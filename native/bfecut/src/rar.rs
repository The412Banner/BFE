//! RAR 4 and RAR 5, single files and volume sets (`name.part1.rar`, … or `name.rar`, `name.r00`, …).
//! The volumes are treated as one long file; every archived file is a piece (with the parts of a
//! file that continue into the next volume). The bundled 7-Zip unpacks one file at a time, chosen
//! by the name 7-Zip itself reports. After a cut, later volumes are deleted, the last one kept is
//! shortened before the removed file and closed with an end-of-archive header, so the leftover is
//! a normal RAR (set) again. Solid archives can't be unpacked file by file from the end.

use crate::engine::{Ctx, Format, Piece};
use crate::ser;
use crate::store::Store;
use crate::util::{crc32, le16, le32, member_rel, Error, Kind, Res};
use crate::zip::walk_files;
use std::fs::{self, File};
use std::io::Read;
use std::path::{Path, PathBuf};
use std::process::Stdio;

const SIG5: [u8; 8] = [0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00];
const SIG4: [u8; 7] = [0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00];

#[derive(Clone)]
struct RFile {
    name: String,
    /// RAR 5 link entry: ('S' symbolic | 'H' hard, target). Made by BFE, not unpacked.
    link: Option<(char, String)>,
    dir: bool,
    size: u64,
    crc: Option<u32>,
    split: bool,
    /// What 7-Zip calls it (used to pick it out).
    tool_name: String,
}

pub struct Rar {
    v5: bool,
    files: Vec<RFile>,
    pieces: Vec<Piece>,
    /// Where the end-of-archive header of the full archive begins (after the last file).
    end: u64,
}

fn bad() -> Error {
    Error::new(Kind::Damaged, "RAR archive structure is damaged")
}

fn vint(b: &[u8], p: &mut usize) -> Res<u64> {
    let mut v = 0u64;
    let mut shift = 0;
    loop {
        let c = *b.get(*p).ok_or_else(bad)?;
        *p += 1;
        v |= ((c & 0x7F) as u64) << shift;
        if c & 0x80 == 0 {
            return Ok(v);
        }
        shift += 7;
        if shift > 63 {
            return Err(bad());
        }
    }
}

/// Every volume of the set `first` belongs to, starting with the first one.
pub fn rar_volumes(path: &Path) -> Vec<PathBuf> {
    let dir = path.parent().map(Path::to_path_buf).unwrap_or_default();
    let name = path.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_default();
    let lower = name.to_lowercase();
    // name.partNN.rar
    if let Some(i) = lower.rfind(".part") {
        let rest = &lower[i + 5..];
        if let Some(num) = rest.strip_suffix(".rar") {
            if !num.is_empty() && num.chars().all(|c| c.is_ascii_digit()) {
                let width = num.len();
                let stem = &name[..i];
                let ext = &name[name.len() - 4..];
                let mut out = Vec::new();
                for n in 1.. {
                    let p = dir.join(format!("{stem}.part{:0width$}{ext}", n, width = width));
                    if !p.is_file() {
                        break;
                    }
                    out.push(p);
                }
                if !out.is_empty() {
                    return out;
                }
            }
        }
    }
    // name.rar, name.r00, name.r01, …
    if lower.ends_with(".rar") || (lower.len() > 4 && lower[lower.len() - 4..].starts_with(".r") && lower[lower.len() - 2..].chars().all(|c| c.is_ascii_digit())) {
        let stem = &name[..name.len() - 4];
        let first = dir.join(format!("{stem}.rar"));
        let mut out = vec![if first.is_file() { first } else { path.to_path_buf() }];
        for n in 0..1000 {
            let p = dir.join(format!("{stem}.r{:02}", n));
            if !p.is_file() {
                break;
            }
            out.push(p);
        }
        return out;
    }
    vec![path.to_path_buf()]
}

impl Rar {
    pub fn looks_like(head: &[u8]) -> bool {
        head.starts_with(&SIG5) || head.starts_with(&SIG4)
    }

    pub fn open(store: &Store, tool7z: Option<&Path>) -> Res<Rar> {
        let mut files: Vec<RFile> = Vec::new();
        let mut pieces: Vec<Piece> = Vec::new();
        let mut base = 0u64;
        let mut v5 = false;
        let mut end = store.len();
        for (vi, vol) in store.vols.iter().enumerate() {
            let len = vol.len;
            let head = store.read_vec(base, (len.min(8)) as usize)?;
            let mut p;
            if head.starts_with(&SIG5) {
                v5 = true;
                p = 8u64;
            } else if head.starts_with(&SIG4) {
                p = 7u64;
            } else {
                return Err(Error::new(Kind::Unsupported, format!("volume {} isn't a RAR volume", vi + 1)));
            }
            while p < len {
                let at = base + p;
                if v5 {
                    let pre = store.read_vec(at, (len - p).min(16) as usize)?;
                    if pre.len() < 6 {
                        break;
                    }
                    let mut q = 4usize;
                    let hsize = vint(&pre, &mut q)?;
                    let hstart = q;
                    if hsize > 2 << 20 {
                        return Err(bad());
                    }
                    let hdr = store.read_vec(at, hstart + hsize as usize)?;
                    if crc32(&hdr[4..]) != le32(&hdr, 0) {
                        return Err(Error::new(Kind::Damaged, format!("RAR header at byte {at} failed its CRC check")));
                    }
                    let mut q = hstart;
                    let typ = vint(&hdr, &mut q)?;
                    let hflags = vint(&hdr, &mut q)?;
                    let extra = if hflags & 1 != 0 { vint(&hdr, &mut q)? } else { 0 };
                    let data = if hflags & 2 != 0 { vint(&hdr, &mut q)? } else { 0 };
                    let total = hstart as u64 + hsize + data;
                    match typ {
                        1 => {
                            let aflags = vint(&hdr, &mut q)?;
                            if aflags & 4 != 0 {
                                return Err(Error::new(Kind::Unsupported, "solid RAR archives can't be unpacked while deleting them"));
                            }
                        }
                        4 => return Err(Error::new(Kind::Password, "the RAR file list is password-protected")),
                        2 => {
                            let fflags = vint(&hdr, &mut q)?;
                            let usize_ = vint(&hdr, &mut q)?;
                            let _attr = vint(&hdr, &mut q)?;
                            if fflags & 2 != 0 {
                                q += 4;
                            }
                            let crc = if fflags & 4 != 0 { let c = le32(&hdr, q); q += 4; Some(c) } else { None };
                            let comp = vint(&hdr, &mut q)?;
                            let _os = vint(&hdr, &mut q)?;
                            let nl = vint(&hdr, &mut q)? as usize;
                            let name = String::from_utf8_lossy(hdr.get(q..q + nl).ok_or_else(bad)?).to_string();
                            q += nl;
                            // Extra records: type 1 = encryption, type 5 = link (redirection).
                            let mut link = None;
                            if extra > 0 {
                                let ex_end = hdr.len();
                                let mut x = ex_end - extra as usize;
                                while x < ex_end {
                                    let rs = vint(&hdr, &mut x)? as usize;
                                    let rstart = x;
                                    let rt = vint(&hdr, &mut x)?;
                                    if rt == 1 {
                                        return Err(Error::new(Kind::Password, "password-protected RAR: unpack it normally"));
                                    }
                                    if rt == 5 {
                                        let kind = vint(&hdr, &mut x)?;
                                        let _flags = vint(&hdr, &mut x)?;
                                        let tl = vint(&hdr, &mut x)? as usize;
                                        let target = String::from_utf8_lossy(hdr.get(x..x + tl).ok_or_else(bad)?).to_string();
                                        link = match kind {
                                            1 | 2 | 3 => Some(('S', target)),
                                            4 => Some(('H', target)),
                                            _ => None, // file copy: 7-Zip handles it
                                        };
                                    }
                                    x = rstart + rs;
                                }
                            }
                            if comp & 0x40 != 0 {
                                return Err(Error::new(Kind::Unsupported, "solid RAR archives can't be unpacked while deleting them"));
                            }
                            let cont = hflags & 0x08 != 0;
                            let splits_on = hflags & 0x10 != 0;
                            Rar::add(&mut files, &mut pieces, at, at + total, cont, splits_on, name, fflags & 1 != 0, usize_, crc)?;
                            if !cont {
                                if let Some(f) = files.last_mut() {
                                    f.link = link;
                                }
                            }
                        }
                        5 => {
                            end = at.min(end);
                            if vi + 1 < store.vols.len() {
                                end = store.len();
                            }
                            break;
                        }
                        _ => {} // service headers (comment, quick open, recovery): dropped on rewrite
                    }
                    p += total;
                } else {
                    let h = store.read_vec(at, (len - p).min(7) as usize)?;
                    if h.len() < 7 {
                        break;
                    }
                    let typ = h[2];
                    let flags = le16(&h, 3);
                    let hsize = le16(&h, 5) as u64;
                    if hsize < 7 {
                        return Err(bad());
                    }
                    let hdr = store.read_vec(at, hsize as usize)?;
                    if (crc32(&hdr[2..]) & 0xFFFF) as u16 != le16(&hdr, 0) && typ != 0x73 {
                        return Err(Error::new(Kind::Damaged, format!("RAR header at byte {at} failed its CRC check")));
                    }
                    let add = if flags & 0x8000 != 0 || typ == 0x74 || typ == 0x7A { le32(&hdr, 7) as u64 } else { 0 };
                    match typ {
                        0x73 => {
                            if flags & 0x0008 != 0 {
                                return Err(Error::new(Kind::Unsupported, "solid RAR archives can't be unpacked while deleting them"));
                            }
                            if flags & 0x0080 != 0 {
                                return Err(Error::new(Kind::Password, "the RAR file list is password-protected"));
                            }
                        }
                        0x74 => {
                            let mut pack = add;
                            let mut usize_ = le32(&hdr, 11) as u64;
                            let crc = le32(&hdr, 16);
                            let nl = le16(&hdr, 26) as usize;
                            let mut q = 32;
                            if flags & 0x100 != 0 {
                                pack |= (le32(&hdr, 32) as u64) << 32;
                                usize_ |= (le32(&hdr, 36) as u64) << 32;
                                q = 40;
                            }
                            let nb = hdr.get(q..q + nl).ok_or_else(bad)?;
                            let name = if flags & 0x200 != 0 {
                                // Unicode names: an OEM part, a zero, then the encoded part. 7-Zip's name is used to
                                // pick the file; the OEM part is enough for labels and path checks.
                                let z = nb.iter().position(|&c| c == 0).unwrap_or(nb.len());
                                String::from_utf8_lossy(&nb[..z]).to_string()
                            } else {
                                nb.iter().map(|&c| c as char).collect()
                            };
                            if flags & 0x04 != 0 {
                                return Err(Error::new(Kind::Password, "password-protected RAR: unpack it normally"));
                            }
                            if flags & 0x10 != 0 {
                                return Err(Error::new(Kind::Unsupported, "solid RAR archives can't be unpacked while deleting them"));
                            }
                            let dir = flags & 0xE0 == 0xE0;
                            let total = hsize + pack;
                            Rar::add(&mut files, &mut pieces, at, at + total, flags & 1 != 0, flags & 2 != 0, name.replace('\\', "/"), dir, usize_, Some(crc))?;
                            p += hsize + pack;
                            continue;
                        }
                        0x7B => {
                            end = at.min(end);
                            if vi + 1 < store.vols.len() {
                                end = store.len();
                            }
                            break;
                        }
                        _ => {}
                    }
                    p += hsize + add;
                }
            }
            base += len;
        }
        if let Some(last) = pieces.last() {
            end = end.max(last.end);
        }
        let mut r = Rar { v5, files, pieces, end };
        r.name_from_tool(store, tool7z)?;
        Ok(r)
    }

    #[allow(clippy::too_many_arguments)]
    fn add(files: &mut Vec<RFile>, pieces: &mut Vec<Piece>, start: u64, stop: u64, cont: bool, splits_on: bool, name: String, dir: bool, size: u64, crc: Option<u32>) -> Res<()> {
        if cont {
            // The rest of the previous file, in the next volume.
            let p = pieces.last_mut().ok_or_else(bad)?;
            p.end = stop;
            if let Some(f) = files.last_mut() {
                f.split = true;
                if !splits_on {
                    f.crc = crc.or(f.crc);
                }
            }
            return Ok(());
        }
        member_rel(&name)?;
        pieces.push(Piece { start, end: stop, out: if dir { 0 } else { size }, label: name.clone() });
        files.push(RFile { name, link: None, dir, size, crc: if splits_on { None } else { crc }, split: splits_on, tool_name: String::new() });
        Ok(())
    }

    /// Asks 7-Zip for its names of the files, in archive order (it decodes RAR 4 Unicode names).
    fn name_from_tool(&mut self, store: &Store, tool7z: Option<&Path>) -> Res<()> {
        let tool = tool7z.ok_or_else(|| Error::new(Kind::Unsupported, "7-Zip is needed for RAR archives"))?;
        let out = crate::util::tool(tool)
            .arg("l")
            .arg("-slt")
            .arg("-ba")
            .arg("-sccUTF-8")
            .arg("-p-")
            .arg(&store.vols[0].path)
            .stdin(Stdio::null())
            .output()
            .map_err(Error::from)?;
        let text = String::from_utf8_lossy(&out.stdout);
        let names: Vec<String> = text.lines().filter_map(|l| l.strip_prefix("Path = ").map(str::to_string)).collect();
        if names.len() != self.files.len() {
            return Err(Error::new(Kind::Unsupported, format!("7-Zip lists {} entries, BFE read {}; unpack it normally", names.len(), self.files.len())));
        }
        for (f, n) in self.files.iter_mut().zip(names) {
            f.tool_name = n;
        }
        Ok(())
    }

    fn end_header(&self) -> Vec<u8> {
        if self.v5 {
            let body = [0x03u8, 0x05, 0x00, 0x00];
            let mut v = crc32(&body).to_le_bytes().to_vec();
            v.extend_from_slice(&body);
            v
        } else {
            let body = [0x7Bu8, 0x00, 0x40, 0x07, 0x00];
            let mut v = ((crc32(&body) & 0xFFFF) as u16).to_le_bytes().to_vec();
            v.extend_from_slice(&body);
            v
        }
    }

    pub fn from_snapshot(b: &[u8]) -> Res<Rar> {
        let mut r = ser::R::new(b);
        let v5 = r.u8()? == 1;
        let end = r.u64()?;
        let n = r.u64()? as usize;
        let mut files = Vec::with_capacity(n);
        let mut pieces = Vec::with_capacity(n);
        for _ in 0..n {
            let name = r.str()?;
            let tool_name = r.str()?;
            let dir = r.u8()? == 1;
            let size = r.u64()?;
            let has_crc = r.u8()? == 1;
            let crc = r.u64()? as u32;
            let split = r.u8()? == 1;
            let link = match r.u8()? {
                0 => { r.str()?; None }
                k => Some((k as char, r.str()?)),
            };
            let start = r.u64()?;
            let pend = r.u64()?;
            pieces.push(Piece { start, end: pend, out: if dir { 0 } else { size }, label: name.clone() });
            files.push(RFile { name, link, dir, size, crc: if has_crc { Some(crc) } else { None }, split, tool_name });
        }
        Ok(Rar { v5, files, pieces, end })
    }
}

impl Format for Rar {
    fn kind(&self) -> &'static str {
        "rar"
    }
    fn pieces(&self) -> &[Piece] {
        &self.pieces
    }

    fn extract(&mut self, store: &mut Store, i: usize, ctx: &mut Ctx) -> Res<()> {
        let f = self.files[i].clone();
        // 7-Zip's name is the real one (RAR 4 Unicode names, Windows separators already decoded).
        let shown = if f.tool_name.is_empty() { &f.name } else { &f.tool_name };
        let Some(rel) = member_rel(shown)? else { return Ok(()) };
        if f.dir {
            ctx.add_dir(rel);
            return Ok(());
        }
        if let Some((k, target)) = &f.link {
            ctx.add_link(*k, target.clone(), rel);
            return Ok(());
        }
        let list = ctx.state_dir.join("names.txt");
        fs::write(&list, format!("{}\n", f.tool_name))?;
        let tool = ctx.tool7z.as_ref().ok_or_else(|| Error::new(Kind::Unsupported, "7-Zip is needed for RAR archives"))?;
        let out = crate::util::tool(tool)
            .arg("x")
            .arg(format!("-o{}", ctx.stage.display()))
            .arg("-y")
            .arg("-bd")
            .arg("-p-")
            .arg("-spd")
            .arg("-scsUTF-8")
            .arg(&store.vols[0].path)
            .arg(format!("@{}", list.display()))
            .stdin(Stdio::null())
            .output()
            .map_err(Error::from)?;
        let _ = fs::remove_file(&list);
        if !out.status.success() {
            let msg = String::from_utf8_lossy(&out.stderr).lines().filter(|l| !l.trim().is_empty()).last().unwrap_or("").to_string();
            return Err(Error::new(Kind::Damaged, format!("7-Zip: {msg}")));
        }
        let found = walk_files(&ctx.stage)?;
        if found.is_empty() {
            // A link entry: 7-Zip made a symbolic link, not a file.
            if let Ok(target) = fs::read_link(ctx.stage_path(&rel)) {
                ctx.add_link('S', target.to_string_lossy().to_string(), rel);
                return Ok(());
            }
        }
        if found.len() != 1 {
            return Err(Error::new(Kind::Damaged, format!("7-Zip produced {} files for {}", found.len(), f.name)));
        }
        let path = ctx.stage.join(&found[0]);
        let mut fh = File::open(&path)?;
        let mut c = crate::util::Crc32::new();
        let mut buf = vec![0u8; 1 << 20];
        let mut n = 0u64;
        loop {
            let k = fh.read(&mut buf)?;
            if k == 0 {
                break;
            }
            c.update(&buf[..k]);
            n += k as u64;
        }
        if n != f.size || f.crc.map(|x| x != c.value()).unwrap_or(false) {
            return Err(Error::new(Kind::Damaged, format!("{} failed its size/CRC check", f.name)));
        }
        if found[0] != rel {
            if let Some(p) = ctx.stage_path(&rel).parent() {
                fs::create_dir_all(p)?;
            }
            fs::rename(&path, ctx.stage_path(&rel))?;
        }
        ctx.add_file(rel);
        Ok(())
    }

    fn rewrite(&self, store: &mut Store, keep: usize) -> Res<()> {
        let at = if keep < self.pieces.len() { self.pieces[keep].start } else { self.end };
        let e = self.end_header();
        // Shorten first (later volumes go), then close the volume that now ends the set.
        store.set_len(at)?;
        store.write_at(at, &e)?;
        store.set_len(at + e.len() as u64)?;
        store.sync()?;
        Ok(())
    }

    fn verify(&mut self, store: &mut Store, ctx: &mut Ctx) -> Res<()> {
        let tool = ctx.tool7z.as_ref().ok_or_else(|| Error::new(Kind::Unsupported, "7-Zip is needed for RAR archives"))?;
        let out = crate::util::tool(tool).arg("t").arg("-bd").arg("-p-").arg(&store.vols[0].path).stdin(Stdio::null()).output().map_err(Error::from)?;
        if !out.status.success() {
            let msg = String::from_utf8_lossy(&out.stderr).lines().filter(|l| !l.trim().is_empty()).last().unwrap_or("").to_string();
            return Err(Error::new(Kind::Damaged, format!("7-Zip test: {msg}")));
        }
        Ok(())
    }

    fn snapshot(&self) -> Vec<u8> {
        let mut w = ser::W::default();
        w.u8(self.v5 as u8).u64(self.end).u64(self.files.len() as u64);
        for (f, p) in self.files.iter().zip(&self.pieces) {
            w.str(&f.name).str(&f.tool_name).u8(f.dir as u8).u64(f.size).u8(f.crc.is_some() as u8).u64(f.crc.unwrap_or(0) as u64).u8(f.split as u8);
            match &f.link {
                Some((k, t)) => w.u8(*k as u8).str(t),
                None => w.u8(0).str(""),
            };
            w.u64(p.start).u64(p.end);
        }
        w.0
    }
}
