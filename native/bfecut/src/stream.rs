//! Compressed streams (.gz/.tgz/.tar.gz, multi-block .xz/.txz/.tar.xz): read from the front, and
//! once a restart point is saved, punch the already-read bytes out of the archive (the file keeps
//! its size, the space comes back). Needs a file system that can punch holes (internal storage on
//! Android; not exFAT/FAT SD cards). A tar inside is unpacked member by member as it streams.
//!
//! Restart point = where decoding can continue without the bytes before it: a deflate block start
//! (bit position + last 32 KiB of output, BFE's own inflater) or an xz block start. The checkpoint
//! also holds the tar unpacker's state and the length of the member file being written.

use crate::inflate::{BitIn, Checkpoint, Inflater};
use crate::lzma::lzma2_decode;
use crate::ser;
use crate::store::Store;
use crate::util::{atomic_write, check_stop, crc32, emit, free_bytes, human, jstr, le32, member_rel, punch_hole, sync_dir, Crc32, Error, Kind, Res};
use std::fs::{self, File, OpenOptions};
use std::io::{Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};
use std::time::{Duration, UNIX_EPOCH};

const XZ_MAGIC: [u8; 6] = [0xFD, b'7', b'z', b'X', b'Z', 0];
/// Take a restart point at least this often (input bytes). BFECUT_CP_EVERY overrides it (tests).
fn cp_every() -> u64 {
    std::env::var("BFECUT_CP_EVERY").ok().and_then(|v| v.parse().ok()).unwrap_or(64 << 20)
}

#[derive(Clone, Copy, PartialEq)]
pub enum Codec {
    Gzip,
    Xz,
}

// ── Output: a tar unpacked as it streams, or one file ──

#[derive(Default, Clone)]
struct TarState {
    hdr: Vec<u8>,
    /// 0 header, 1 meta (L/K/x data), 2 file data, 3 padding, 4 end
    st: u8,
    remaining: u64,
    pad: u64,
    meta_typ: u8,
    meta: Vec<u8>,
    long_name: Option<String>,
    long_link: Option<String>,
    pax_path: Option<String>,
    pax_link: Option<String>,
    pax_size: Option<u64>,
    path: String,
    mtime: u64,
    written: u64,
    /// Number of the regular file being written: names its stage file, so a resume never mixes
    /// one member's partial file up with the next one's.
    seq: u64,
}

pub struct Sink {
    tar: bool,
    ts: TarState,
    dest: PathBuf,
    stage: PathBuf,
    state_dir: PathBuf,
    file_name: String,
    cur: Option<File>,
    pub out: u64,
}

fn oct(b: &[u8]) -> u64 {
    if b.first().map(|c| c & 0x80 != 0).unwrap_or(false) {
        let mut v = (b[0] & 0x7F) as u64;
        for &c in &b[1..] {
            v = v.wrapping_mul(256).wrapping_add(c as u64);
        }
        return v;
    }
    let s: String = b.iter().take_while(|&&c| c != 0).map(|&c| c as char).collect();
    u64::from_str_radix(s.trim(), 8).unwrap_or(0)
}

fn cstr(b: &[u8]) -> String {
    let e = b.iter().position(|&c| c == 0).unwrap_or(b.len());
    String::from_utf8_lossy(&b[..e]).to_string()
}

impl Sink {
    fn stage_file(&self) -> PathBuf {
        self.stage.join(format!("m{}.part", self.ts.seq))
    }

    fn open_current(&mut self, keep: u64) -> Res<()> {
        let p = self.stage_file();
        let mut f = OpenOptions::new().create(true).write(true).truncate(false).open(&p)?;
        f.set_len(keep)?;
        f.seek(SeekFrom::Start(keep))?;
        self.cur = Some(f);
        Ok(())
    }

    fn commit_current(&mut self, rel: &Path, mtime: u64) -> Res<()> {
        let f = self.cur.take().ok_or_else(|| Error::new(Kind::Io, "no output open"))?;
        f.sync_all()?;
        let _ = f.set_modified(UNIX_EPOCH + Duration::from_secs(mtime));
        drop(f);
        let dst = self.dest.join(rel);
        if let Some(p) = dst.parent() {
            fs::create_dir_all(p)?;
        }
        fs::rename(self.stage_file(), &dst)?;
        if let Some(p) = dst.parent() {
            sync_dir(p);
        }
        Ok(())
    }

    fn link(&self, k: char, target: &str, rel: &Path) -> Res<()> {
        let mut f = OpenOptions::new().create(true).append(true).open(self.state_dir.join("links"))?;
        writeln!(f, "{k}\t{}\t{}", target.replace(['\t', '\n'], " "), rel.display())?;
        f.sync_all()?;
        Ok(())
    }

    fn write(&mut self, mut data: &[u8]) -> Res<()> {
        self.out += data.len() as u64;
        if !self.tar {
            self.cur.as_mut().unwrap().write_all(data)?;
            return Ok(());
        }
        while !data.is_empty() {
            match self.ts.st {
                0 => {
                    let n = (512 - self.ts.hdr.len()).min(data.len());
                    self.ts.hdr.extend_from_slice(&data[..n]);
                    data = &data[n..];
                    if self.ts.hdr.len() == 512 {
                        let h = std::mem::take(&mut self.ts.hdr);
                        self.header(&h)?;
                    }
                }
                1 => {
                    let n = (self.ts.remaining as usize).min(data.len());
                    if self.ts.meta.len() < 1 << 20 {
                        self.ts.meta.extend_from_slice(&data[..n]);
                    }
                    self.ts.remaining -= n as u64;
                    data = &data[n..];
                    if self.ts.remaining == 0 {
                        let m = std::mem::take(&mut self.ts.meta);
                        match self.ts.meta_typ {
                            b'L' => self.ts.long_name = Some(cstr(&m)),
                            b'K' => self.ts.long_link = Some(cstr(&m)),
                            b'x' => self.pax(&m),
                            _ => {}
                        }
                        self.ts.st = if self.ts.pad > 0 { 3 } else { 0 };
                    }
                }
                2 => {
                    let n = (self.ts.remaining as usize).min(data.len());
                    self.cur.as_mut().unwrap().write_all(&data[..n])?;
                    self.ts.written += n as u64;
                    self.ts.remaining -= n as u64;
                    data = &data[n..];
                    if self.ts.remaining == 0 {
                        if let Some(rel) = member_rel(&self.ts.path)? {
                            let mt = self.ts.mtime;
                            self.commit_current(&rel, mt)?;
                        } else {
                            self.cur = None;
                        }
                        self.ts.st = if self.ts.pad > 0 { 3 } else { 0 };
                    }
                }
                3 => {
                    let n = (self.ts.pad as usize).min(data.len());
                    self.ts.pad -= n as u64;
                    data = &data[n..];
                    if self.ts.pad == 0 {
                        self.ts.st = 0;
                    }
                }
                _ => return Ok(()), // after the end marker
            }
        }
        Ok(())
    }

    fn pax(&mut self, data: &[u8]) {
        let mut p = 0;
        while p < data.len() {
            let Some(sp) = data[p..].iter().position(|&c| c == b' ').map(|x| p + x) else { break };
            let Some(len) = std::str::from_utf8(&data[p..sp]).ok().and_then(|s| s.parse::<usize>().ok()) else { break };
            if len == 0 || p + len > data.len() {
                break;
            }
            let rec = &data[sp + 1..p + len];
            let rec = rec.strip_suffix(b"\n").unwrap_or(rec);
            if let Some(eq) = rec.iter().position(|&c| c == b'=') {
                let v = String::from_utf8_lossy(&rec[eq + 1..]).to_string();
                match &rec[..eq] {
                    b"path" => self.ts.pax_path = Some(v),
                    b"linkpath" => self.ts.pax_link = Some(v),
                    b"size" => self.ts.pax_size = v.parse().ok(),
                    _ => {}
                }
            }
            p += len;
        }
    }

    fn header(&mut self, h: &[u8]) -> Res<()> {
        if h.iter().all(|&c| c == 0) {
            self.ts.st = 4;
            return Ok(());
        }
        if !crate::tar::header_ok(h) {
            return Err(Error::new(Kind::Damaged, "tar header inside the stream is damaged"));
        }
        let typ = h[156];
        let size = oct(&h[124..136]);
        let pad = |s: u64| (512 - s % 512) % 512;
        match typ {
            b'L' | b'K' | b'x' | b'g' => {
                self.ts.st = if size > 0 { 1 } else { 0 };
                self.ts.meta_typ = typ;
                self.ts.remaining = size;
                self.ts.pad = pad(size);
                self.ts.meta.clear();
                if size == 0 {
                    self.ts.st = 0;
                }
                return Ok(());
            }
            b'S' => return Err(Error::new(Kind::Unsupported, "sparse tar members aren't supported")),
            _ => {}
        }
        let mut path = cstr(&h[0..100]);
        if &h[257..262] == b"ustar" {
            let pre = cstr(&h[345..500]);
            if !pre.is_empty() {
                path = format!("{pre}/{path}");
            }
        }
        let path = self.ts.pax_path.take().or(self.ts.long_name.take()).unwrap_or(path);
        let link = self.ts.pax_link.take().or(self.ts.long_link.take()).unwrap_or_else(|| cstr(&h[157..257]));
        let size = self.ts.pax_size.take().unwrap_or(size);
        let mtime = oct(&h[136..148]);
        let rel = member_rel(&path)?;
        match typ {
            b'0' | 0 | b'7' => {
                self.ts.seq += 1;
                self.ts.path = path;
                self.ts.mtime = mtime;
                self.ts.written = 0;
                self.ts.remaining = size;
                self.ts.pad = pad(size);
                self.open_current(0)?;
                if size == 0 {
                    if let Some(rel) = rel {
                        self.commit_current(&rel, mtime)?;
                    } else {
                        self.cur = None;
                    }
                    self.ts.st = 0;
                } else {
                    self.ts.st = 2;
                }
            }
            _ => {
                if let Some(rel) = rel {
                    match typ {
                        b'5' => fs::create_dir_all(self.dest.join(&rel))?,
                        b'2' => self.link('S', &link, &rel)?,
                        b'1' => self.link('H', &link, &rel)?,
                        _ => {}
                    }
                }
                self.ts.remaining = 0;
                self.ts.pad = pad(size) + if matches!(typ, b'5' | b'2' | b'1') { 0 } else { size };
                self.ts.st = if self.ts.pad > 0 { 3 } else { 0 };
            }
        }
        Ok(())
    }

    /// Makes the current output durable (a restart point is about to be saved).
    fn sync(&mut self) -> Res<()> {
        if let Some(f) = &self.cur {
            f.sync_data()?;
        }
        Ok(())
    }

    fn save(&self, w: &mut ser::W) {
        let t = &self.ts;
        w.u64(self.out).bytes(&t.hdr).u8(t.st).u64(t.remaining).u64(t.pad).u8(t.meta_typ).bytes(&t.meta);
        for o in [&t.long_name, &t.long_link, &t.pax_path, &t.pax_link] {
            match o {
                Some(s) => w.u8(1).str(s),
                None => w.u8(0).str(""),
            };
        }
        w.u8(t.pax_size.is_some() as u8).u64(t.pax_size.unwrap_or(0));
        w.str(&t.path).u64(t.mtime).u64(if self.tar { t.written } else { self.out }).u64(t.seq);
    }

    fn load(&mut self, r: &mut ser::R) -> Res<()> {
        self.out = r.u64()?;
        let mut t = TarState { hdr: r.bytes()?, st: r.u8()?, remaining: r.u64()?, pad: r.u64()?, meta_typ: r.u8()?, meta: r.bytes()?, ..Default::default() };
        let mut opt = |r: &mut ser::R| -> Res<Option<String>> { let f = r.u8()?; let s = r.str()?; Ok(if f == 1 { Some(s) } else { None }) };
        t.long_name = opt(r)?;
        t.long_link = opt(r)?;
        t.pax_path = opt(r)?;
        t.pax_link = opt(r)?;
        let ps = r.u8()?;
        let pv = r.u64()?;
        t.pax_size = if ps == 1 { Some(pv) } else { None };
        t.path = r.str()?;
        t.mtime = r.u64()?;
        let written = r.u64()?;
        t.written = written;
        t.seq = r.u64()?;
        let open = !self.tar || t.st == 2;
        let rel = if self.tar { member_rel(&t.path)? } else { member_rel(&self.file_name)? };
        self.ts = t;
        // Stage files of members started after this restart point are redone from scratch.
        let keep = self.stage_file();
        if let Ok(rd) = fs::read_dir(&self.stage) {
            for e in rd.flatten() {
                if e.path() != keep {
                    let _ = fs::remove_file(e.path());
                }
            }
        }
        if open {
            // The member may have been finished and moved into place after this restart point:
            // take it back and continue it from the saved length.
            let part = self.stage_file();
            if !part.exists() {
                if let Some(rel) = rel {
                    let done = self.dest.join(rel);
                    if done.is_file() && fs::metadata(&done)?.len() >= written {
                        fs::rename(&done, &part)?;
                    }
                }
            }
            if part.exists() && fs::metadata(&part)?.len() < written {
                return Err(Error::new(Kind::Io, "the partly written file is shorter than the saved progress"));
            }
            if !part.exists() && written > 0 {
                return Err(Error::new(Kind::Io, "the partly written file is missing"));
            }
            self.open_current(written)?;
        }
        Ok(())
    }

    fn finish(&mut self) -> Res<()> {
        if !self.tar {
            let rel = member_rel(&self.file_name)?.ok_or_else(|| Error::new(Kind::Unsafe, "no output name"))?;
            self.commit_current(&rel, 0)?;
        } else if self.ts.st == 2 {
            return Err(Error::new(Kind::Damaged, "the stream ends inside a tar member"));
        }
        Ok(())
    }
}

// ── The run ──

pub struct Stream {
    pub codec: Codec,
    pub tar: bool,
    pub out_name: String,
    /// xz: (block start, block total size, uncompressed size) from the index.
    blocks: Vec<(u64, u64, u64)>,
    check_kind: u8,
}

pub fn looks_like(head: &[u8]) -> Option<Codec> {
    if head.len() >= 3 && head[0] == 0x1F && head[1] == 0x8B && head[2] == 8 {
        Some(Codec::Gzip)
    } else if head.starts_with(&XZ_MAGIC) {
        Some(Codec::Xz)
    } else {
        None
    }
}

fn vli(b: &[u8], p: &mut usize) -> Res<u64> {
    let mut v = 0u64;
    for i in 0..9 {
        let c = *b.get(*p).ok_or_else(|| Error::new(Kind::Damaged, "xz index is damaged"))?;
        *p += 1;
        v |= ((c & 0x7F) as u64) << (7 * i);
        if c & 0x80 == 0 {
            return Ok(v);
        }
    }
    Err(Error::new(Kind::Damaged, "xz index is damaged"))
}

fn crc64(data: &[u8]) -> u64 {
    let mut c: u64 = !0;
    for &b in data {
        c ^= b as u64;
        for _ in 0..8 {
            c = if c & 1 != 0 { (c >> 1) ^ 0xC96C_5795_D787_0F42 } else { c >> 1 };
        }
    }
    !c
}

impl Stream {
    pub fn open(store: &Store, archive: &Path, codec: Codec) -> Res<Stream> {
        if store.is_split() {
            return Err(Error::new(Kind::Unsupported, "split compressed streams can't be shrunk while unpacking"));
        }
        let name = archive.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_default();
        let lower = name.to_lowercase();
        let (base, tar_ext) = if let Some(b) = lower.strip_suffix(".tgz").or(lower.strip_suffix(".txz")) {
            (name[..b.len()].to_string() + ".tar", true)
        } else if let Some(b) = lower.strip_suffix(".gz").or(lower.strip_suffix(".xz")) {
            (name[..b.len()].to_string(), b.ends_with(".tar"))
        } else {
            (name.clone() + ".out", false)
        };
        let mut s = Stream { codec, tar: tar_ext, out_name: base, blocks: Vec::new(), check_kind: 0 };
        if codec == Codec::Xz {
            s.read_xz_index(store)?;
            if s.blocks.len() < 2 {
                return Err(Error::new(Kind::Unsupported, "this .xz is one block: it can't be shrunk while unpacking"));
            }
        }
        Ok(s)
    }

    fn read_xz_index(&mut self, store: &Store) -> Res<()> {
        let len = store.len();
        let sh = store.read_vec(0, 12)?;
        if crc32(&sh[6..8]) != le32(&sh, 8) {
            return Err(Error::new(Kind::Damaged, "xz header is damaged"));
        }
        self.check_kind = sh[7] & 0x0F;
        let ft = store.read_vec(len - 12, 12)?;
        if &ft[10..12] != b"YZ" || crc32(&ft[4..10]) != le32(&ft, 0) {
            return Err(Error::new(Kind::Unsupported, "xz files with several streams or padding aren't supported"));
        }
        let back = (le32(&ft, 4) as u64 + 1) * 4;
        let ipos = len - 12 - back;
        let idx = store.read_vec(ipos, back as usize)?;
        if idx[0] != 0 || crc32(&idx[..idx.len() - 4]) != le32(&idx, idx.len() - 4) {
            return Err(Error::new(Kind::Damaged, "xz index is damaged"));
        }
        let mut p = 1;
        let n = vli(&idx, &mut p)?;
        let mut off = 12u64;
        for _ in 0..n {
            let unpadded = vli(&idx, &mut p)?;
            let usize_ = vli(&idx, &mut p)?;
            let total = unpadded.div_ceil(4) * 4;
            self.blocks.push((off, total, usize_));
            off += total;
        }
        if off != ipos {
            return Err(Error::new(Kind::Damaged, "xz index doesn't match the blocks"));
        }
        Ok(())
    }

    /// Saved before anything is punched: once the start is freed, the archive can't describe itself.
    pub fn save_meta(&self, state_dir: &Path, total: Option<u64>) -> Res<()> {
        let mut w = ser::W::default();
        w.u8(if self.codec == Codec::Gzip { 0 } else { 1 }).u8(self.tar as u8).str(&self.out_name).u8(self.check_kind);
        w.u8(total.is_some() as u8).u64(total.unwrap_or(0));
        w.u64(self.blocks.len() as u64);
        for &(a, b, c) in &self.blocks {
            w.u64(a).u64(b).u64(c);
        }
        fs::create_dir_all(state_dir)?;
        atomic_write(&state_dir.join("stream_meta"), &w.0)
    }

    pub fn load_meta(state_dir: &Path) -> Option<(Stream, Option<u64>)> {
        let b = fs::read(state_dir.join("stream_meta")).ok()?;
        let mut r = ser::R::new(&b);
        let codec = if r.u8().ok()? == 0 { Codec::Gzip } else { Codec::Xz };
        let tar = r.u8().ok()? == 1;
        let out_name = r.str().ok()?;
        let check_kind = r.u8().ok()?;
        let has_total = r.u8().ok()? == 1;
        let total = r.u64().ok()?;
        let n = r.u64().ok()? as usize;
        let mut blocks = Vec::with_capacity(n);
        for _ in 0..n {
            blocks.push((r.u64().ok()?, r.u64().ok()?, r.u64().ok()?));
        }
        Some((Stream { codec, tar, out_name, blocks, check_kind }, if has_total { Some(total) } else { None }))
    }

    pub fn total_out(&self, store: &Store) -> Option<u64> {
        match self.codec {
            Codec::Xz => Some(self.blocks.iter().map(|b| b.2).sum()),
            Codec::Gzip => {
                // ISIZE of the last member (exact below 4 GiB for one member).
                let t = store.read_vec(store.len() - 4, 4).ok()?;
                let v = le32(&t, 0) as u64;
                (store.len() < 1 << 30).then_some(v)
            }
        }
    }

    fn decode_xz_block(&self, store: &Store, i: usize) -> Res<Vec<u8>> {
        let (off, total, usize_) = self.blocks[i];
        let blk = store.read_vec(off, total as usize)?;
        let hs = (blk[0] as usize + 1) * 4;
        if crc32(&blk[..hs - 4]) != le32(&blk, hs - 4) {
            return Err(Error::new(Kind::Damaged, "xz block header is damaged"));
        }
        let flags = blk[1];
        let nfilt = (flags & 3) as usize + 1;
        let mut p = 2;
        if flags & 0x40 != 0 {
            vli(&blk, &mut p)?;
        }
        if flags & 0x80 != 0 {
            vli(&blk, &mut p)?;
        }
        for k in 0..nfilt {
            let id = vli(&blk, &mut p)?;
            let psz = vli(&blk, &mut p)? as usize;
            p += psz;
            if id != 0x21 || k + 1 != nfilt || nfilt != 1 {
                return Err(Error::new(Kind::Unsupported, "this .xz uses filters BFE can't decode in this mode"));
            }
        }
        let csize = match self.check_kind {
            0 => 0,
            1 => 4,
            4 => 8,
            10 => 32,
            _ => return Err(Error::new(Kind::Unsupported, "unknown xz check")),
        };
        let body = &blk[hs..blk.len() - csize];
        let out = lzma2_decode(body, usize_ as usize + 1)?;
        if out.len() as u64 != usize_ {
            return Err(Error::new(Kind::Damaged, "xz block has the wrong size"));
        }
        let ck = &blk[blk.len() - csize..];
        let ok = match self.check_kind {
            1 => crc32(&out) == le32(ck, 0),
            4 => crc64(&out).to_le_bytes() == ck[..8],
            _ => true,
        };
        if !ok {
            return Err(Error::new(Kind::Damaged, "xz block failed its check"));
        }
        Ok(out)
    }
}

pub struct StreamOpts {
    pub keep_free: u64,
    pub verify_first: bool,
}

/// Probes whether the archive's file system can punch holes (writes and removes a small file next to it).
pub fn can_punch(dir: &Path) -> bool {
    let p = dir.join(".bfe-punch-test");
    let ok = (|| -> Res<bool> {
        let mut f = File::create(&p)?;
        f.write_all(&vec![7u8; 1 << 20])?;
        f.sync_all()?;
        Ok(punch_hole(&f, 0, 1 << 19))
    })()
    .unwrap_or(false);
    let _ = fs::remove_file(&p);
    ok
}

struct Cp {
    in_bit: u64,
    punched: u64,
    member_crc: u32,
    member_out: u64,
    /// gzip: 0 member header next, 1 inside deflate (window below), 2 trailer next. xz: block index.
    phase: u64,
    window: Vec<u8>,
    sink: Vec<u8>,
    verified: bool,
}

fn save_cp(dir: &Path, c: &Cp) -> Res<()> {
    let mut w = ser::W::default();
    w.u64(c.in_bit).u64(c.punched).u64(c.member_crc as u64).u64(c.member_out).u64(c.phase).bytes(&c.window).bytes(&c.sink).u8(c.verified as u8);
    atomic_write(&dir.join("stream"), &w.0)
}

fn load_cp(dir: &Path) -> Option<Cp> {
    let b = fs::read(dir.join("stream")).ok()?;
    let mut r = ser::R::new(&b);
    Some(Cp {
        in_bit: r.u64().ok()?,
        punched: r.u64().ok()?,
        member_crc: r.u64().ok()? as u32,
        member_out: r.u64().ok()?,
        phase: r.u64().ok()?,
        window: r.bytes().ok()?,
        sink: r.bytes().ok()?,
        verified: r.u8().ok()? == 1,
    })
}

fn gz_header<R: Read>(b: &mut BitIn<R>) -> Res<()> {
    if b.byte()? != 0x1F || b.byte()? != 0x8B || b.byte()? != 8 {
        return Err(Error::new(Kind::Damaged, "bad gzip member header"));
    }
    let flg = b.byte()?;
    for _ in 0..6 {
        b.byte()?;
    }
    if flg & 4 != 0 {
        let xl = b.byte()? as usize | (b.byte()? as usize) << 8;
        for _ in 0..xl {
            b.byte()?;
        }
    }
    for bit in [8u8, 16] {
        if flg & bit != 0 {
            while b.byte()? != 0 {}
        }
    }
    if flg & 2 != 0 {
        b.byte()?;
        b.byte()?;
    }
    Ok(())
}

pub fn run(s: &Stream, store: &mut Store, dest: &Path, state_dir: &Path, o: &StreamOpts, known_total: Option<u64>) -> Res<()> {
    fs::create_dir_all(state_dir)?;
    let stage = state_dir.join("stage");
    fs::create_dir_all(&stage)?;
    let start_len = store.len();
    let resumed = load_cp(state_dir);
    if resumed.is_none() {
        let dir = store.vols[0].path.parent().map(Path::to_path_buf).unwrap_or_else(|| PathBuf::from("."));
        if !can_punch(&dir) {
            let _ = fs::remove_dir_all(state_dir);
            fail!(Unsupported, "this storage can't free space while reading (SD cards); unpack it normally");
        }
        s.save_meta(state_dir, known_total.or_else(|| s.total_out(store)))?;
    }
    let mut sink = Sink { tar: s.tar, ts: TarState::default(), dest: dest.to_path_buf(), stage, state_dir: state_dir.to_path_buf(), file_name: s.out_name.clone(), cur: None, out: 0 };
    let mut cp = match resumed {
        Some(c) => c,
        None => Cp { in_bit: 0, punched: 0, member_crc: 0, member_out: 0, phase: 0, window: Vec::new(), sink: Vec::new(), verified: false },
    };
    if o.verify_first && !cp.verified {
        emit("{\"ev\":\"verify\"}");
        let n = verify(s, store)?;
        emit(&format!("{{\"ev\":\"verified\",\"total_out\":{n}}}"));
        cp.verified = true;
        save_cp(state_dir, &cp)?;
    }
    if !cp.sink.is_empty() {
        let mut r = ser::R::new(&cp.sink);
        sink.load(&mut r)?;
    } else if !s.tar {
        sink.open_current(0)?;
    }
    let total_out = Stream::load_meta(state_dir).and_then(|(_, t)| t).unwrap_or(0);
    let file = store.volume_file(0).try_clone()?;
    let mut last_report = 0u64;
    let every = cp_every();
    let mut take_cp = |sink: &mut Sink, cp: &mut Cp, in_byte: u64, force: bool| -> Res<()> {
        let free = free_bytes(dest)?.saturating_sub(o.keep_free);
        if !force && in_byte < cp.punched + every && free > (256 << 20) {
            return Ok(());
        }
        sink.sync()?;
        let mut w = ser::W::default();
        sink.save(&mut w);
        cp.sink = w.0;
        save_cp(state_dir, cp)?;
        let upto = in_byte & !4095;
        if upto > cp.punched {
            if !punch_hole(&file, cp.punched, upto - cp.punched) {
                return Err(Error::new(Kind::Unsupported, "this storage can't free space while reading"));
            }
            cp.punched = upto;
            save_cp(state_dir, cp)?;
        }
        let free = free_bytes(dest)?.saturating_sub(o.keep_free);
        if free < (64 << 20) {
            fail!(NoSpace, "storage is almost full ({} left above the reserve); progress saved", human(free));
        }
        Ok(())
    };
    match s.codec {
        Codec::Xz => {
            let start = cp.phase as usize;
            for i in start..s.blocks.len() {
                check_stop()?;
                let out = s.decode_xz_block(store, i)?;
                sink.write(&out)?;
                cp.phase = i as u64 + 1;
                let next = if i + 1 < s.blocks.len() { s.blocks[i + 1].0 } else { s.blocks[i].0 + s.blocks[i].1 };
                cp.in_bit = next * 8;
                take_cp(&mut sink, &mut cp, next, false)?;
                if sink.out - last_report >= 16 << 20 {
                    last_report = sink.out;
                    emit(&format!("{{\"ev\":\"progress\",\"out\":{},\"total_out\":{},\"freed\":{},\"file\":{}}}", sink.out, total_out, cp.punched, jstr(&sink.ts.path)));
                }
            }
        }
        Codec::Gzip => {
            let src = store.reader(cp.in_bit / 8, store.len() - cp.in_bit / 8);
            let mut b = BitIn::at_bit(src, cp.in_bit)?;
            let mut inf: Option<Inflater> = None;
            if cp.phase == 1 {
                let mut f = Inflater::from_checkpoint(&Checkpoint { bit: cp.in_bit, out: cp.member_out, window: cp.window.clone() });
                f.crc = Crc32::from_raw(cp.member_crc);
                inf = Some(f);
            }
            loop {
                check_stop()?;
                match cp.phase {
                    0 => {
                        if b.at_end()? {
                            break;
                        }
                        gz_header(&mut b)?;
                        inf = Some(Inflater::new());
                        cp.phase = 1;
                    }
                    1 => {
                        let f = inf.as_mut().unwrap();
                        let mut tee = TeeSink { sink: &mut sink };
                        let last = f.block(&mut b, &mut tee)?;
                        if last {
                            b.align();
                            cp.phase = 2;
                        } else {
                            let pos = b.bit_pos();
                            let k = f.checkpoint(pos);
                            cp.in_bit = pos;
                            cp.member_out = k.out;
                            cp.member_crc = f.crc.raw();
                            cp.window = k.window;
                            take_cp(&mut sink, &mut cp, pos / 8, false)?;
                        }
                        if sink.out - last_report >= 16 << 20 {
                            last_report = sink.out;
                            emit(&format!("{{\"ev\":\"progress\",\"out\":{},\"total_out\":{},\"freed\":{},\"file\":{}}}", sink.out, total_out, cp.punched, jstr(&sink.ts.path)));
                        }
                    }
                    _ => {
                        let f = inf.take().unwrap();
                        let mut t = [0u8; 8];
                        for x in t.iter_mut() {
                            *x = b.byte()?;
                        }
                        if le32(&t, 0) != f.crc.value() || le32(&t, 4) != (f.out & 0xFFFF_FFFF) as u32 {
                            return Err(Error::new(Kind::Damaged, "gzip member failed its CRC check"));
                        }
                        cp.phase = 0;
                        cp.window.clear();
                        cp.in_bit = b.bit_pos();
                        if b.at_end()? {
                            break;
                        }
                        // Another member, or trailing zeros some tools leave.
                        let peek = store.read_vec(b.bit_pos() / 8, 2).unwrap_or_default();
                        if peek != [0x1F, 0x8B] {
                            break;
                        }
                    }
                }
            }
        }
    }
    sink.finish()?;
    let skipped = crate::engine::make_links_at(dest, state_dir)?;
    store.delete_all()?;
    let _ = fs::remove_dir_all(state_dir);
    emit(&format!("{{\"ev\":\"done\",\"total_out\":{},\"freed\":{},\"links_skipped\":{}}}", sink.out, start_len, skipped));
    Ok(())
}

struct TeeSink<'a> {
    sink: &'a mut Sink,
}

impl<'a> Write for TeeSink<'a> {
    fn write(&mut self, b: &[u8]) -> std::io::Result<usize> {
        self.sink.write(b).map_err(|e| std::io::Error::other(e.msg))?;
        Ok(b.len())
    }
    fn flush(&mut self) -> std::io::Result<()> {
        Ok(())
    }
}

/// A full decode pass (checks every CRC) before anything is punched. Returns the unpacked size.
pub fn verify(s: &Stream, store: &Store) -> Res<u64> {
    match s.codec {
        Codec::Xz => {
            let mut n = 0;
            for i in 0..s.blocks.len() {
                check_stop()?;
                n += s.decode_xz_block(store, i)?.len() as u64;
            }
            Ok(n)
        }
        Codec::Gzip => {
            let mut b = BitIn::new(store.reader(0, store.len()));
            let mut total = 0u64;
            loop {
                if b.at_end()? {
                    break;
                }
                gz_header(&mut b)?;
                let mut f = Inflater::new();
                let mut sink = std::io::sink();
                while !f.block(&mut b, &mut sink)? {
                    check_stop()?;
                }
                b.align();
                let mut t = [0u8; 8];
                for x in t.iter_mut() {
                    *x = b.byte()?;
                }
                if le32(&t, 0) != f.crc.value() || le32(&t, 4) != (f.out & 0xFFFF_FFFF) as u32 {
                    return Err(Error::new(Kind::Damaged, "gzip member failed its CRC check"));
                }
                total += f.out;
                if b.at_end()? {
                    break;
                }
                let peek = store.read_vec(b.bit_pos() / 8, 2).unwrap_or_default();
                if peek != [0x1F, 0x8B] {
                    break;
                }
            }
            Ok(total)
        }
    }
}

