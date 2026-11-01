//! tar (ustar, GNU long names, pax). Every member is a piece; members are stored as is, so BFE
//! copies them out itself. After a cut the archive is the remaining members plus the two zero
//! blocks that end a tar, i.e. a normal tar again.

use crate::engine::{Ctx, Format, Piece};
use crate::ser;
use crate::store::Store;
use crate::util::{member_rel, Crc32, Error, Kind, Res};
use std::collections::HashSet;
use std::fs::{self, File};
use std::io::{Read, Write};
use std::time::{Duration, UNIX_EPOCH};

#[derive(Clone)]
pub struct Member {
    pub typ: u8,
    pub path: String,
    pub link: String,
    pub size: u64,
    pub data: u64,
    pub mtime: u64,
    /// A later member has the same path: tar's rule is that the later one wins.
    pub superseded: bool,
}

pub struct Tar {
    pieces: Vec<Piece>,
    members: Vec<Member>,
    /// Where the end marker starts (after the last member).
    end: u64,
}

fn octal(b: &[u8]) -> Option<u64> {
    if b.first().map(|c| c & 0x80 != 0).unwrap_or(false) {
        // GNU base-256 for big numbers.
        let mut v: u64 = (b[0] & 0x7F) as u64;
        for &c in &b[1..] {
            v = v.checked_mul(256)?.checked_add(c as u64)?;
        }
        return Some(v);
    }
    let s: String = b.iter().take_while(|&&c| c != 0).map(|&c| c as char).collect();
    let s = s.trim();
    if s.is_empty() {
        return Some(0);
    }
    u64::from_str_radix(s, 8).ok()
}

fn cstr(b: &[u8]) -> String {
    let end = b.iter().position(|&c| c == 0).unwrap_or(b.len());
    String::from_utf8_lossy(&b[..end]).to_string()
}

pub fn header_ok(h: &[u8]) -> bool {
    if h.len() < 512 {
        return false;
    }
    let want = match octal(&h[148..156]) {
        Some(v) => v,
        None => return false,
    };
    let mut sum: u64 = 0;
    let mut ssum: i64 = 0;
    for (i, &c) in h[..512].iter().enumerate() {
        let c = if (148..156).contains(&i) { b' ' } else { c };
        sum += c as u64;
        ssum += c as i8 as i64;
    }
    sum == want || ssum == want as i64
}

fn pax(data: &[u8], path: &mut Option<String>, link: &mut Option<String>, size: &mut Option<u64>) {
    let mut p = 0;
    while p < data.len() {
        let sp = match data[p..].iter().position(|&c| c == b' ') {
            Some(x) => p + x,
            None => break,
        };
        let len: usize = match std::str::from_utf8(&data[p..sp]).ok().and_then(|s| s.parse().ok()) {
            Some(l) if l > 0 && p + l <= data.len() => l,
            _ => break,
        };
        let rec = &data[sp + 1..p + len];
        let rec = rec.strip_suffix(b"\n").unwrap_or(rec);
        if let Some(eq) = rec.iter().position(|&c| c == b'=') {
            let k = &rec[..eq];
            let v = String::from_utf8_lossy(&rec[eq + 1..]).to_string();
            match k {
                b"path" => *path = Some(v),
                b"linkpath" => *link = Some(v),
                b"size" => *size = v.parse().ok(),
                _ => {}
            }
        }
        p += len;
    }
}

impl Tar {
    pub fn looks_like(head: &[u8]) -> bool {
        head.len() >= 512 && header_ok(&head[..512]) && head[..512].iter().any(|&c| c != 0)
    }

    pub fn open(store: &Store) -> Res<Tar> {
        let len = store.len();
        let mut off = 0u64;
        let mut members = Vec::new();
        let mut pieces = Vec::new();
        let mut group_start = 0u64;
        let (mut lname, mut llink): (Option<String>, Option<String>) = (None, None);
        let (mut ppath, mut plink, mut psize): (Option<String>, Option<String>, Option<u64>) = (None, None, None);
        let mut end: Option<u64> = None;
        while off + 512 <= len {
            let h = store.read_vec(off, 512)?;
            if h.iter().all(|&c| c == 0) {
                end = Some(off);
                break;
            }
            if !header_ok(&h) {
                if off == 0 {
                    return Err(Error::new(Kind::Unsupported, "not a tar archive"));
                }
                return Err(Error::new(Kind::Damaged, format!("tar header at byte {off} is damaged")));
            }
            let typ = h[156];
            let hsize = octal(&h[124..136]).ok_or_else(|| Error::new(Kind::Damaged, "bad tar size"))?;
            let data = off + 512;
            let size = match typ {
                b'x' | b'g' | b'L' | b'K' => hsize,
                _ => psize.unwrap_or(hsize),
            };
            let next = data + size.div_ceil(512) * 512;
            if next > len {
                return Err(Error::new(Kind::Damaged, "tar archive ends inside a member"));
            }
            match typ {
                b'L' | b'K' => {
                    let v = store.read_vec(data, size.min(1 << 20) as usize)?;
                    let s = cstr(&v);
                    if typ == b'L' { lname = Some(s) } else { llink = Some(s) }
                }
                b'x' => {
                    let v = store.read_vec(data, size.min(1 << 20) as usize)?;
                    pax(&v, &mut ppath, &mut plink, &mut psize);
                }
                b'g' => {}
                b'S' => return Err(Error::new(Kind::Unsupported, "sparse tar members aren't supported")),
                _ => {
                    let mut path = cstr(&h[0..100]);
                    if &h[257..262] == b"ustar" {
                        let prefix = cstr(&h[345..500]);
                        if !prefix.is_empty() {
                            path = format!("{prefix}/{path}");
                        }
                    }
                    let path = ppath.take().or(lname.take()).unwrap_or(path);
                    let link = plink.take().or(llink.take()).unwrap_or_else(|| cstr(&h[157..257]));
                    let mtime = octal(&h[136..148]).unwrap_or(0);
                    let size = if matches!(typ, b'0' | 0 | b'7') { size } else { 0 };
                    members.push(Member { typ, path: path.clone(), link, size, data, mtime, superseded: false });
                    pieces.push(Piece { start: group_start, end: next, out: size, label: path });
                    psize = None;
                    group_start = next;
                }
            }
            off = next;
        }
        // A tar without its closing zero blocks ends where the last member does.
        let end = end.unwrap_or(off);
        let mut t = Tar { pieces, members, end };
        t.mark_superseded()?;
        Ok(t)
    }

    fn mark_superseded(&mut self) -> Res<()> {
        let mut seen = HashSet::new();
        for (i, m) in self.members.iter_mut().enumerate().rev() {
            let Some(rel) = member_rel(&m.path)? else {
                // The archive's own top folder ("./"): nothing to unpack.
                m.superseded = true;
                continue;
            };
            if !seen.insert(rel) {
                m.superseded = true;
                self.pieces[i].out = 0;
            }
        }
        Ok(())
    }

    pub fn from_snapshot(b: &[u8]) -> Res<Tar> {
        let mut r = ser::R::new(b);
        let end = r.u64()?;
        let n = r.u64()? as usize;
        let mut members = Vec::with_capacity(n);
        let mut pieces = Vec::with_capacity(n);
        for _ in 0..n {
            let typ = r.u8()?;
            let path = r.str()?;
            let link = r.str()?;
            let size = r.u64()?;
            let data = r.u64()?;
            let mtime = r.u64()?;
            let superseded = r.u8()? == 1;
            let start = r.u64()?;
            let pend = r.u64()?;
            let out = r.u64()?;
            pieces.push(Piece { start, end: pend, out, label: path.clone() });
            members.push(Member { typ, path, link, size, data, mtime, superseded });
        }
        Ok(Tar { pieces, members, end })
    }

    /// Copies member `m`'s data to a file, returns its CRC (for logging only: tar has no checksum).
    fn copy_out(store: &Store, m: &Member, dst: &std::path::Path) -> Res<u32> {
        if let Some(p) = dst.parent() {
            fs::create_dir_all(p)?;
        }
        let mut f = File::create(dst)?;
        let mut r = store.reader(m.data, m.size);
        let mut buf = vec![0u8; 1 << 20];
        let mut crc = Crc32::new();
        loop {
            let n = r.read(&mut buf).map_err(|e| Error::new(Kind::Damaged, e.to_string()))?;
            if n == 0 {
                break;
            }
            crc.update(&buf[..n]);
            f.write_all(&buf[..n])?;
        }
        f.flush()?;
        let _ = f.set_modified(UNIX_EPOCH + Duration::from_secs(m.mtime));
        Ok(crc.value())
    }
}

/// Extracts one tar member into the stage (shared with the gzip/xz stream path, which feeds
/// members through the same code).
pub fn emit_member(m: &Member, ctx: &mut Ctx, data: Option<&mut dyn Read>) -> Res<()> {
    if m.superseded {
        if let Some(r) = data {
            std::io::copy(r, &mut std::io::sink()).map_err(Error::from)?;
        }
        return Ok(());
    }
    let Some(rel) = member_rel(&m.path)? else { return Ok(()) };
    match m.typ {
        b'5' => ctx.add_dir(rel),
        b'2' => ctx.add_link('S', m.link.clone(), rel),
        b'1' => ctx.add_link('H', m.link.clone(), rel),
        b'0' | 0 | b'7' => {
            if let Some(r) = data {
                let dst = ctx.stage_path(&rel);
                if let Some(p) = dst.parent() {
                    fs::create_dir_all(p)?;
                }
                let mut f = File::create(&dst)?;
                let n = std::io::copy(r, &mut f).map_err(Error::from)?;
                if n != m.size {
                    return Err(Error::new(Kind::Damaged, format!("{} is shorter than its header says", m.path)));
                }
                let _ = f.set_modified(UNIX_EPOCH + Duration::from_secs(m.mtime));
            }
            ctx.add_file(rel);
        }
        _ => {} // devices, fifos: not on Android storage
    }
    Ok(())
}

impl Format for Tar {
    fn kind(&self) -> &'static str {
        "tar"
    }
    fn pieces(&self) -> &[Piece] {
        &self.pieces
    }

    fn extract(&mut self, store: &mut Store, i: usize, ctx: &mut Ctx) -> Res<()> {
        let m = self.members[i].clone();
        if !m.superseded && matches!(m.typ, b'0' | 0 | b'7') {
            let Some(rel) = member_rel(&m.path)? else { return Ok(()) };
            Tar::copy_out(store, &m, &ctx.stage_path(&rel))?;
            ctx.add_file(rel);
            return Ok(());
        }
        emit_member(&m, ctx, None)
    }

    fn rewrite(&self, store: &mut Store, keep: usize) -> Res<()> {
        let at = if keep < self.pieces.len() { self.pieces[keep].start } else { self.end };
        store.write_at(at, &[0u8; 1024])?;
        store.set_len(at + 1024)?;
        store.sync()?;
        Ok(())
    }

    fn verify(&mut self, store: &mut Store, _ctx: &mut Ctx) -> Res<()> {
        // tar keeps no checksum of file data: read every byte once so a short or unreadable download
        // shows up before anything is deleted (headers were checked when the archive was opened).
        let mut buf = vec![0u8; 1 << 20];
        let mut r = store.reader(0, self.end);
        loop {
            let n = r.read(&mut buf).map_err(|e| Error::new(Kind::Damaged, e.to_string()))?;
            if n == 0 {
                break;
            }
            crate::util::check_stop()?;
        }
        Ok(())
    }

    fn snapshot(&self) -> Vec<u8> {
        let mut w = ser::W::default();
        w.u64(self.end).u64(self.members.len() as u64);
        for (m, p) in self.members.iter().zip(&self.pieces) {
            w.u8(m.typ).str(&m.path).str(&m.link).u64(m.size).u64(m.data).u64(m.mtime).u8(m.superseded as u8);
            w.u64(p.start).u64(p.end).u64(p.out);
        }
        w.0
    }
}
