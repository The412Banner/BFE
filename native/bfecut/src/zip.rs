//! ZIP and ZIP64 (also self-extracting .exe ZIPs and `name.zip.001` splits). Every entry is a
//! piece. Stored and deflated entries are unpacked by BFE itself; other methods by the bundled
//! 7-Zip, through a temporary central directory that lists only that entry. After a cut the
//! archive ends with a new central directory for the remaining entries, i.e. a normal ZIP again.

use crate::engine::{Ctx, Format, Piece};
use crate::inflate::inflate_all;
use crate::ser;
use crate::store::Store;
use crate::util::{le16, le32, le64, member_rel, Crc32, Error, Kind, Res};
use std::collections::HashSet;
use std::fs::{self, File};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::time::{Duration, UNIX_EPOCH};

#[derive(Clone)]
struct Entry {
    raw: Vec<u8>,
    name: String,
    flags: u16,
    method: u16,
    crc: u32,
    csize: u64,
    usize_: u64,
    local: u64,
    data: u64,
    made_by: u16,
    ext_attr: u32,
    dos_time: u32,
    piece: usize,
}

pub struct Zip {
    entries: Vec<Entry>, // central directory order
    pieces: Vec<Piece>,
    order: Vec<usize>, // piece index -> entry index
    delta: i64,
    cd_start: u64,
    comment: Vec<u8>,
}

fn dos_to_unix(t: u32) -> u64 {
    let (time, date) = ((t & 0xFFFF) as u64, (t >> 16) as u64);
    let (y, m, d) = (1980 + (date >> 9), ((date >> 5) & 15).max(1), (date & 31).max(1));
    let (hh, mm, ss) = (time >> 11, (time >> 5) & 63, (time & 31) * 2);
    // Days from 1970-01-01 (civil calendar, proleptic Gregorian).
    let (y2, m2) = if m <= 2 { (y - 1, m + 9) } else { (y, m - 3) };
    let era = y2 / 400;
    let yoe = y2 - era * 400;
    let doy = (153 * m2 + 2) / 5 + d - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    let days = era * 146097 + doe - 719468;
    days * 86400 + hh * 3600 + mm * 60 + ss
}

impl Zip {
    pub fn looks_like(head: &[u8], store: &Store) -> bool {
        head.starts_with(b"PK\x03\x04") || Zip::find_eocd(store).is_some()
    }

    fn find_eocd(store: &Store) -> Option<(u64, Vec<u8>)> {
        let len = store.len();
        let n = len.min(65557) as usize;
        if n < 22 {
            return None;
        }
        let tail = store.read_vec(len - n as u64, n).ok()?;
        let mut i = n - 22;
        loop {
            if le32(&tail, i) == 0x0605_4b50 && i + 22 + le16(&tail, i + 20) as usize == n {
                return Some((len - (n - i) as u64, tail[i..].to_vec()));
            }
            if i == 0 {
                return None;
            }
            i -= 1;
        }
    }

    pub fn open(store: &Store) -> Res<Zip> {
        let (eocd_pos, eocd) = Zip::find_eocd(store).ok_or_else(|| Error::new(Kind::Unsupported, "no ZIP central directory"))?;
        let mut disk = le16(&eocd, 4) as u32;
        let mut cd_disk = le16(&eocd, 6) as u32;
        let mut count = le16(&eocd, 10) as u64;
        let mut cd_size = le32(&eocd, 12) as u64;
        let mut cd_off = le32(&eocd, 16) as u64;
        let comment = eocd[22..].to_vec();
        let mut cd_end = eocd_pos;
        // ZIP64 locator right before the EOCD.
        if eocd_pos >= 20 {
            let loc = store.read_vec(eocd_pos - 20, 20)?;
            if le32(&loc, 0) == 0x0706_4b50 {
                let rec_off = le64(&loc, 8);
                // The record normally sits right before the locator; find it there (SFX shifts offsets).
                let guess = eocd_pos - 20 - 56;
                let rec = store.read_vec(guess, 56)?;
                let rec = if le32(&rec, 0) == 0x0606_4b50 { rec } else { store.read_vec(rec_off, 56)? };
                if le32(&rec, 0) != 0x0606_4b50 {
                    return Err(Error::new(Kind::Damaged, "ZIP64 end record missing"));
                }
                disk = le32(&rec, 16);
                cd_disk = le32(&rec, 20);
                count = le64(&rec, 32);
                cd_size = le64(&rec, 40);
                cd_off = le64(&rec, 48);
                cd_end = guess;
            }
        }
        if disk != 0 || cd_disk != 0 {
            return Err(Error::new(Kind::Unsupported, "spanned ZIP sets (.z01, .z02, …) can't be shrunk; unpack it normally"));
        }
        let cd_start = cd_end.checked_sub(cd_size).ok_or_else(|| Error::new(Kind::Damaged, "bad central directory size"))?;
        let delta = cd_start as i64 - cd_off as i64;
        let cd = store.read_vec(cd_start, cd_size as usize)?;
        let mut entries = Vec::new();
        let mut p = 0usize;
        for _ in 0..count {
            if p + 46 > cd.len() || le32(&cd, p) != 0x0201_4b50 {
                return Err(Error::new(Kind::Damaged, "central directory is damaged"));
            }
            let (nl, xl, cl) = (le16(&cd, p + 28) as usize, le16(&cd, p + 30) as usize, le16(&cd, p + 32) as usize);
            let rec_len = 46 + nl + xl + cl;
            if p + rec_len > cd.len() {
                return Err(Error::new(Kind::Damaged, "central directory is damaged"));
            }
            let raw = cd[p..p + rec_len].to_vec();
            let mut csize = le32(&cd, p + 20) as u64;
            let mut usize_ = le32(&cd, p + 24) as u64;
            let mut local = le32(&cd, p + 42) as u64;
            let start_disk = le16(&cd, p + 34);
            // ZIP64 extra: present fields in order usize, csize, local, disk.
            let extra = &cd[p + 46 + nl..p + 46 + nl + xl];
            let mut x = 0;
            while x + 4 <= extra.len() {
                let (id, sz) = (le16(extra, x), le16(extra, x + 2) as usize);
                if x + 4 + sz > extra.len() {
                    break;
                }
                if id == 0x0001 {
                    let mut q = x + 4;
                    if usize_ == 0xFFFF_FFFF && q + 8 <= x + 4 + sz {
                        usize_ = le64(extra, q);
                        q += 8;
                    }
                    if csize == 0xFFFF_FFFF && q + 8 <= x + 4 + sz {
                        csize = le64(extra, q);
                        q += 8;
                    }
                    if local == 0xFFFF_FFFF && q + 8 <= x + 4 + sz {
                        local = le64(extra, q);
                    }
                }
                x += 4 + sz;
            }
            if start_disk != 0 && start_disk != 0xFFFF {
                return Err(Error::new(Kind::Unsupported, "spanned ZIP sets can't be shrunk"));
            }
            let flags = le16(&cd, p + 8);
            let name_bytes = &cd[p + 46..p + 46 + nl];
            let name = if flags & 0x800 != 0 {
                String::from_utf8_lossy(name_bytes).to_string()
            } else {
                // CP437 for the ASCII range is ASCII; anything else is shown best effort.
                name_bytes.iter().map(|&c| if c < 0x80 { c as char } else { char::from_u32(c as u32).unwrap_or('?') }).collect()
            };
            entries.push(Entry {
                raw,
                name,
                flags,
                method: le16(&cd, p + 10),
                crc: le32(&cd, p + 16),
                csize,
                usize_,
                local: (local as i64 + delta) as u64,
                data: 0,
                made_by: le16(&cd, p + 4),
                ext_attr: le32(&cd, p + 38),
                dos_time: le32(&cd, p + 12),
                piece: 0,
            });
            p += rec_len;
        }
        // Local headers: where each entry's data begins.
        for e in entries.iter_mut() {
            let lh = store.read_vec(e.local, 30)?;
            if le32(&lh, 0) != 0x0403_4b50 {
                return Err(Error::new(Kind::Damaged, format!("local header of {} is missing", e.name)));
            }
            e.data = e.local + 30 + le16(&lh, 26) as u64 + le16(&lh, 28) as u64;
        }
        let mut z = Zip { entries, pieces: Vec::new(), order: Vec::new(), delta, cd_start, comment };
        z.build_pieces()?;
        Ok(z)
    }

    fn build_pieces(&mut self) -> Res<()> {
        let mut idx: Vec<usize> = (0..self.entries.len()).collect();
        idx.sort_by_key(|&i| self.entries[i].local);
        let mut seen = HashSet::new();
        let mut pieces = Vec::with_capacity(idx.len());
        for (k, &ei) in idx.iter().enumerate() {
            let e = &self.entries[ei];
            let end = if k + 1 < idx.len() { self.entries[idx[k + 1]].local } else { self.cd_start };
            if e.data + e.csize > end {
                return Err(Error::new(Kind::Unsupported, format!("entries overlap ({}); unpack it normally", e.name)));
            }
            if let Some(rel) = member_rel(&e.name)? {
                if !seen.insert(rel) {
                    return Err(Error::new(Kind::Unsupported, format!("the archive lists {} twice; unpack it normally", e.name)));
                }
            }
            if e.flags & 1 != 0 {
                return Err(Error::new(Kind::Password, "password-protected ZIP: unpack it normally"));
            }
            // Methods BFE doesn't decode itself go through a one-entry copy: count that copy too.
            let extra = if Zip::own_method(e) || Zip::is_dir(e) { 0 } else { end - e.local + 4096 };
            pieces.push(Piece { start: e.local, end, out: e.usize_ + extra, label: e.name.clone() });
        }
        for (k, &ei) in idx.iter().enumerate() {
            self.entries[ei].piece = k;
        }
        self.order = idx;
        self.pieces = pieces;
        Ok(())
    }

    pub fn from_snapshot(b: &[u8]) -> Res<Zip> {
        let mut r = ser::R::new(b);
        let delta = r.u64()? as i64;
        let cd_start = r.u64()?;
        let comment = r.bytes()?;
        let n = r.u64()? as usize;
        let mut entries = Vec::with_capacity(n);
        for _ in 0..n {
            let raw = r.bytes()?;
            let name = r.str()?;
            let local = r.u64()?;
            let data = r.u64()?;
            let csize = r.u64()?;
            let usize_ = r.u64()?;
            entries.push(Entry {
                flags: le16(&raw, 8),
                method: le16(&raw, 10),
                crc: le32(&raw, 16),
                made_by: le16(&raw, 4),
                ext_attr: le32(&raw, 38),
                dos_time: le32(&raw, 12),
                raw,
                name,
                csize,
                usize_,
                local,
                data,
                piece: 0,
            });
        }
        let mut z = Zip { entries, pieces: Vec::new(), order: Vec::new(), delta, cd_start, comment };
        z.build_pieces()?;
        Ok(z)
    }

    fn is_dir(e: &Entry) -> bool {
        e.name.ends_with('/') || e.name.ends_with('\\') || (e.ext_attr & 0x10 != 0 && e.usize_ == 0)
    }

    fn is_symlink(e: &Entry) -> bool {
        (e.made_by >> 8) == 3 && ((e.ext_attr >> 16) & 0o170000) == 0o120000
    }

    fn own_method(e: &Entry) -> bool {
        e.method == 0 || e.method == 8
    }

    /// Unpacks a stored/deflated entry to `w`, checking size and CRC.
    fn unpack_own<W: Write>(store: &Store, e: &Entry, w: &mut W) -> Res<()> {
        let r = store.reader(e.data, e.csize);
        let (n, crc) = if e.method == 0 {
            let mut c = Crc32::new();
            let mut rr = r;
            let mut buf = vec![0u8; 1 << 20];
            let mut n = 0u64;
            loop {
                let k = rr.read(&mut buf).map_err(|x| Error::new(Kind::Damaged, x.to_string()))?;
                if k == 0 {
                    break;
                }
                c.update(&buf[..k]);
                w.write_all(&buf[..k])?;
                n += k as u64;
            }
            (n, c.value())
        } else {
            inflate_all(r, w)?
        };
        if n != e.usize_ || crc != e.crc {
            return Err(Error::new(Kind::Damaged, format!("{} failed its CRC check", e.name)));
        }
        Ok(())
    }

    fn cd_bytes(&self, keep_pieces: &dyn Fn(usize) -> bool, at: u64) -> Vec<u8> {
        let mut cd = Vec::new();
        let mut n = 0u64;
        for e in &self.entries {
            if keep_pieces(e.piece) {
                cd.extend_from_slice(&e.raw);
                n += 1;
            }
        }
        let cd_size = cd.len() as u64;
        let cd_off = (at as i64 - self.delta) as u64;
        let need64 = n >= 0xFFFF || cd_size >= 0xFFFF_FFFF || cd_off >= 0xFFFF_FFFF;
        let mut out = cd;
        if need64 {
            let rec_pos = (at as i64 + cd_size as i64 - self.delta) as u64;
            out.extend_from_slice(&0x0606_4b50u32.to_le_bytes());
            out.extend_from_slice(&44u64.to_le_bytes());
            out.extend_from_slice(&45u16.to_le_bytes());
            out.extend_from_slice(&45u16.to_le_bytes());
            out.extend_from_slice(&0u32.to_le_bytes());
            out.extend_from_slice(&0u32.to_le_bytes());
            out.extend_from_slice(&n.to_le_bytes());
            out.extend_from_slice(&n.to_le_bytes());
            out.extend_from_slice(&cd_size.to_le_bytes());
            out.extend_from_slice(&cd_off.to_le_bytes());
            out.extend_from_slice(&0x0706_4b50u32.to_le_bytes());
            out.extend_from_slice(&0u32.to_le_bytes());
            out.extend_from_slice(&rec_pos.to_le_bytes());
            out.extend_from_slice(&1u32.to_le_bytes());
        }
        out.extend_from_slice(&0x0605_4b50u32.to_le_bytes());
        out.extend_from_slice(&0u16.to_le_bytes());
        out.extend_from_slice(&0u16.to_le_bytes());
        let n16 = if need64 { 0xFFFF } else { n as u16 };
        out.extend_from_slice(&n16.to_le_bytes());
        out.extend_from_slice(&n16.to_le_bytes());
        out.extend_from_slice(&(if need64 { 0xFFFF_FFFF } else { cd_size as u32 }).to_le_bytes());
        out.extend_from_slice(&(if need64 { 0xFFFF_FFFF } else { cd_off as u32 }).to_le_bytes());
        out.extend_from_slice(&(self.comment.len() as u16).to_le_bytes());
        out.extend_from_slice(&self.comment);
        out
    }

    fn write_index(&self, store: &mut Store, at: u64, keep: &dyn Fn(usize) -> bool) -> Res<()> {
        let bytes = self.cd_bytes(keep, at);
        store.write_at(at, &bytes)?;
        store.set_len(at + bytes.len() as u64)?;
        store.sync()?;
        Ok(())
    }

    fn run_7z(ctx: &Ctx, archive: &Path, test: bool) -> Res<()> {
        let tool = ctx.tool7z.as_ref().ok_or_else(|| Error::new(Kind::Unsupported, "7-Zip is needed for this entry's method"))?;
        let mut c = crate::util::tool(tool);
        if test {
            c.arg("t");
        } else {
            c.arg("x").arg(format!("-o{}", ctx.stage.display()));
        }
        let out = c.arg("-y").arg("-bd").arg("-p-").arg(archive).stdin(Stdio::null()).output().map_err(Error::from)?;
        if !out.status.success() {
            let msg = String::from_utf8_lossy(&out.stderr).lines().last().unwrap_or("").to_string();
            return Err(Error::new(Kind::Damaged, format!("7-Zip: {msg}")));
        }
        Ok(())
    }
}

/// Sets a central directory record's local header offset (the ZIP64 field too, if it is used).
fn set_local_offset(rec: &mut [u8], off: u64) {
    let lf = le32(rec, 42);
    rec[42..46].copy_from_slice(&(off as u32).to_le_bytes());
    if lf != 0xFFFF_FFFF {
        return;
    }
    rec[42..46].copy_from_slice(&0xFFFF_FFFFu32.to_le_bytes());
    let (nl, xl) = (le16(rec, 28) as usize, le16(rec, 30) as usize);
    let (usz, csz) = (le32(rec, 24), le32(rec, 20));
    let mut x = 46 + nl;
    let end = 46 + nl + xl;
    while x + 4 <= end {
        let (id, sz) = (le16(rec, x), le16(rec, x + 2) as usize);
        if id == 0x0001 {
            let mut q = x + 4;
            if usz == 0xFFFF_FFFF {
                q += 8;
            }
            if csz == 0xFFFF_FFFF {
                q += 8;
            }
            if q + 8 <= x + 4 + sz {
                rec[q..q + 8].copy_from_slice(&off.to_le_bytes());
            }
            return;
        }
        x += 4 + sz;
    }
}

/// Every regular file under `root`, relative.
pub fn walk_files(root: &Path) -> Res<Vec<PathBuf>> {
    let mut out = Vec::new();
    let mut stack = vec![PathBuf::new()];
    while let Some(rel) = stack.pop() {
        for e in fs::read_dir(root.join(&rel))? {
            let e = e?;
            let r = rel.join(e.file_name());
            let t = e.file_type()?;
            if t.is_dir() {
                stack.push(r);
            } else if t.is_file() {
                out.push(r);
            }
        }
    }
    Ok(out)
}

impl Format for Zip {
    fn kind(&self) -> &'static str {
        "zip"
    }
    fn pieces(&self) -> &[Piece] {
        &self.pieces
    }


    fn extract(&mut self, store: &mut Store, i: usize, ctx: &mut Ctx) -> Res<()> {
        let e = self.entries[self.order[i]].clone();
        let Some(rel) = member_rel(&e.name)? else { return Ok(()) };
        if Zip::is_dir(&e) {
            ctx.add_dir(rel);
            return Ok(());
        }
        if Zip::is_symlink(&e) && Zip::own_method(&e) {
            let mut target = Vec::new();
            Zip::unpack_own(store, &e, &mut target)?;
            ctx.add_link('S', String::from_utf8_lossy(&target).to_string(), rel);
            return Ok(());
        }
        let dst = ctx.stage_path(&rel);
        if let Some(p) = dst.parent() {
            fs::create_dir_all(p)?;
        }
        if Zip::own_method(&e) {
            let mut f = File::create(&dst)?;
            Zip::unpack_own(store, &e, &mut f)?;
            f.flush()?;
            let _ = f.set_modified(UNIX_EPOCH + Duration::from_secs(dos_to_unix(e.dos_time)));
            ctx.add_file(rel);
            return Ok(());
        }
        // Another method: copy just this entry into a one-entry ZIP and let 7-Zip unpack that.
        let one = ctx.state_dir.join("one.zip");
        let (start, end) = (self.pieces[i].start, self.pieces[i].end);
        {
            let mut f = File::create(&one)?;
            let mut r = store.reader(start, end - start);
            std::io::copy(&mut r, &mut f).map_err(Error::from)?;
            let mut rec = e.raw.clone();
            set_local_offset(&mut rec, 0);
            let cd_size = rec.len() as u32;
            f.write_all(&rec)?;
            let mut eocd = Vec::new();
            eocd.extend_from_slice(&0x0605_4b50u32.to_le_bytes());
            eocd.extend_from_slice(&[0, 0, 0, 0, 1, 0, 1, 0]);
            eocd.extend_from_slice(&cd_size.to_le_bytes());
            eocd.extend_from_slice(&((end - start) as u32).to_le_bytes());
            eocd.extend_from_slice(&[0, 0]);
            f.write_all(&eocd)?;
        }
        let r7 = Zip::run_7z(ctx, &one, false);
        let _ = fs::remove_file(&one);
        r7?;
        let found = walk_files(&ctx.stage)?;
        if found.len() != 1 {
            return Err(Error::new(Kind::Damaged, format!("7-Zip produced {} files for {}", found.len(), e.name)));
        }
        let mut f = File::open(ctx.stage.join(&found[0]))?;
        let mut c = Crc32::new();
        let mut buf = vec![0u8; 1 << 20];
        let mut n = 0u64;
        loop {
            let k = f.read(&mut buf)?;
            if k == 0 {
                break;
            }
            c.update(&buf[..k]);
            n += k as u64;
        }
        if n != e.usize_ || c.value() != e.crc {
            return Err(Error::new(Kind::Damaged, format!("{} failed its CRC check", e.name)));
        }
        if found[0] != rel {
            if let Some(p) = ctx.stage_path(&rel).parent() {
                fs::create_dir_all(p)?;
            }
            fs::rename(ctx.stage.join(&found[0]), ctx.stage_path(&rel))?;
        }
        ctx.add_file(rel);
        Ok(())
    }

    fn rewrite(&self, store: &mut Store, keep: usize) -> Res<()> {
        let at = if keep < self.pieces.len() { self.pieces[keep].start } else { self.cd_start };
        self.write_index(store, at, &|p| p < keep)
    }

    fn verify(&mut self, store: &mut Store, ctx: &mut Ctx) -> Res<()> {
        let mut need7 = false;
        for e in &self.entries {
            crate::util::check_stop()?;
            if Zip::is_dir(e) {
                continue;
            }
            if Zip::own_method(e) {
                Zip::unpack_own(store, e, &mut std::io::sink())?;
            } else {
                need7 = true;
            }
        }
        if need7 {
            let first = store.vols[0].path.clone();
            Zip::run_7z(ctx, &first, true)?;
        }
        Ok(())
    }

    fn snapshot(&self) -> Vec<u8> {
        let mut w = ser::W::default();
        w.u64(self.delta as u64).u64(self.cd_start).bytes(&self.comment).u64(self.entries.len() as u64);
        for e in &self.entries {
            w.bytes(&e.raw).str(&e.name).u64(e.local).u64(e.data).u64(e.csize).u64(e.usize_);
        }
        w.0
    }
}
