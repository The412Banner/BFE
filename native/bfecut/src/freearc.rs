//! FreeArc (`.arc`, repack `Setup-N.bin`). Every solid data block is a piece. To unpack one, BFE
//! writes a directory listing only that block after it and lets unarc unpack "everything"; then
//! it cuts the block off and writes a directory for the blocks that are left (stored, CRC'd), so
//! the leftover is a normal FreeArc archive. Layout as FreeArc 0.67 Unarc/ArcStructure.h reads it.

use crate::engine::{Ctx, Format, Piece};
use crate::lzma::lzma_decode;
use crate::ser;
use crate::store::Store;
use crate::util::{crc32, le32, member_rel, Error, Kind, Res};
use crate::zip::walk_files;
use std::fs;
use std::path::PathBuf;
use std::process::Stdio;

const SIG: [u8; 4] = [b'A', b'r', b'C', 1];
const HEADER_BLOCK: u64 = 1;
const DIR_BLOCK: u64 = 3;
const FOOTER_BLOCK: u64 = 4;

#[derive(Clone)]
struct Block {
    comp: String,
    pos: u64,
    size: u64,
    first: usize,
    count: usize,
}

#[derive(Clone)]
struct FFile {
    name: String,
    dir: String,
    size: u64,
    time: u32,
    isdir: bool,
    crc: u32,
}

pub struct FreeArc {
    blocks: Vec<Block>,
    files: Vec<FFile>,
    pieces: Vec<Piece>,
    /// The archive's own header block (kept in every footer so unarc sees the same layout).
    header: Option<(String, u64, u64, u64, u32)>,
    unarc: Option<PathBuf>,
    unarc_args: Vec<String>,
}

fn bad() -> Error {
    Error::new(Kind::Damaged, "FreeArc archive structure is damaged")
}

struct Rd<'a> {
    b: &'a [u8],
    p: usize,
}

impl<'a> Rd<'a> {
    fn int(&mut self) -> Res<u64> {
        let first = *self.b.get(self.p).ok_or_else(bad)?;
        let mut n = 1usize;
        while n <= 8 && (first >> (n - 1)) & 1 == 1 {
            n += 1;
        }
        if n == 9 {
            let s = self.b.get(self.p + 1..self.p + 9).ok_or_else(bad)?;
            self.p += 9;
            return Ok(u64::from_le_bytes(s.try_into().unwrap()));
        }
        let s = self.b.get(self.p..self.p + n).ok_or_else(bad)?;
        let mut v = 0u64;
        for (i, &c) in s.iter().enumerate() {
            v |= (c as u64) << (8 * i);
        }
        self.p += n;
        Ok(v >> n)
    }
    fn str(&mut self) -> Res<String> {
        let rest = self.b.get(self.p..).ok_or_else(bad)?;
        let e = rest.iter().position(|&c| c == 0).ok_or_else(bad)?;
        let s = String::from_utf8_lossy(&rest[..e]).to_string();
        self.p += e + 1;
        Ok(s)
    }
    fn u32(&mut self) -> Res<u32> {
        let s = self.b.get(self.p..self.p + 4).ok_or_else(bad)?;
        self.p += 4;
        Ok(le32(s, 0))
    }
    fn u8(&mut self) -> Res<u8> {
        let v = *self.b.get(self.p).ok_or_else(bad)?;
        self.p += 1;
        Ok(v)
    }
}

fn wint(o: &mut Vec<u8>, x: u64) {
    for n in 1..=8u32 {
        if n * 7 >= 64 || x < 1u64 << (7 * n) {
            let v = (x << n) | ((1u64 << (n - 1)) - 1);
            o.extend_from_slice(&v.to_le_bytes()[..n as usize]);
            return;
        }
    }
    o.push(0xFF);
    o.extend_from_slice(&x.to_le_bytes());
}

fn wstr(o: &mut Vec<u8>, s: &str) {
    o.extend_from_slice(s.as_bytes());
    o.push(0);
}

fn control_block(store: &Store, comp: &str, pos: u64, orig: u64, size: u64, crc: u32) -> Res<Vec<u8>> {
    if size > 1 << 28 || orig > 1 << 30 {
        return Err(bad());
    }
    let raw = store.read_vec(pos, size as usize)?;
    let method = comp.split(':').next().unwrap_or("");
    let data = match method {
        "storing" => raw,
        "lzma" => {
            let (mut lc, mut lp, mut pb) = (3u8, 0u8, 2u8);
            for p in comp.split(':').skip(1).map(|p| p.trim_start_matches('*')) {
                if let Some(v) = p.strip_prefix("lc").and_then(|v| v.parse().ok()) {
                    lc = v;
                } else if let Some(v) = p.strip_prefix("lp").and_then(|v| v.parse().ok()) {
                    lp = v;
                } else if let Some(v) = p.strip_prefix("pb").and_then(|v| v.parse().ok()) {
                    pb = v;
                }
            }
            lzma_decode(&raw, (pb * 5 + lp) * 9 + lc, orig as usize)?
        }
        _ => {
            if comp.contains("aes") || comp.contains("serpent") || comp.contains("blowfish") || comp.contains("twofish") {
                return Err(Error::new(Kind::Password, "the FreeArc file list is password-protected"));
            }
            return Err(Error::new(Kind::Unsupported, format!("FreeArc header method {method} isn't supported")));
        }
    };
    if data.len() as u64 != orig || crc32(&data) != crc {
        return Err(bad());
    }
    Ok(data)
}

impl FreeArc {
    pub fn looks_like(store: &Store) -> bool {
        let len = store.len();
        let n = len.min(4096) as usize;
        store.read_vec(len - n as u64, n).map(|t| t.windows(4).any(|w| w == SIG)).unwrap_or(false)
    }

    pub fn open(store: &Store, unarc: Option<PathBuf>, unarc_args: Vec<String>) -> Res<FreeArc> {
        let len = store.len();
        let n = len.min(4096) as usize;
        let tail = store.read_vec(len - n as u64, n)?;
        let at = (0..=n.saturating_sub(4)).rev().find(|&i| tail[i..i + 4] == SIG).ok_or_else(|| Error::new(Kind::Unsupported, "not a FreeArc archive"))?;
        let d = &tail[at..];
        if d.len() < 8 || crc32(&d[..d.len() - 4]) != le32(d, d.len() - 4) {
            return Err(Error::new(Kind::Unsupported, "not a FreeArc archive"));
        }
        let dpos = len - n as u64 + at as u64;
        let mut r = Rd { b: d, p: 4 };
        let (t, comp, orig, size, crc) = (r.int()?, r.str()?, r.int()?, r.int()?, r.u32()?);
        if t != FOOTER_BLOCK || size > dpos {
            return Err(bad());
        }
        let fpos = dpos - size;
        let footer = control_block(store, &comp, fpos, orig, size, crc)?;
        let mut r = Rd { b: &footer, p: 0 };
        let count = r.int()? as usize;
        let mut header = None;
        let mut blocks = Vec::new();
        let mut files = Vec::new();
        for _ in 0..count {
            let (t, comp, rel, orig, size, crc) = (r.int()?, r.str()?, r.int()?, r.int()?, r.int()?, r.u32()?);
            let pos = fpos.checked_sub(rel).ok_or_else(bad)?;
            match t {
                HEADER_BLOCK => header = Some((comp, pos, orig, size, crc)),
                DIR_BLOCK => {
                    let dir = control_block(store, &comp, pos, orig, size, crc)?;
                    FreeArc::read_dir(&dir, pos, &mut blocks, &mut files)?;
                }
                _ => {} // recovery data: dropped when the archive is rewritten
            }
        }
        blocks.sort_by_key(|b| b.pos);
        let mut f = FreeArc { blocks, files, pieces: Vec::new(), header, unarc, unarc_args };
        f.build_pieces()?;
        Ok(f)
    }

    fn read_dir(dir: &[u8], dpos: u64, blocks: &mut Vec<Block>, files: &mut Vec<FFile>) -> Res<()> {
        let mut r = Rd { b: dir, p: 0 };
        let nb = r.int()? as usize;
        let counts = (0..nb).map(|_| r.int().map(|v| v as usize)).collect::<Res<Vec<_>>>()?;
        let comps = (0..nb).map(|_| r.str()).collect::<Res<Vec<_>>>()?;
        let offs = (0..nb).map(|_| r.int()).collect::<Res<Vec<_>>>()?;
        let sizes = (0..nb).map(|_| r.int()).collect::<Res<Vec<_>>>()?;
        let nd = r.int()? as usize;
        let dirs = (0..nd).map(|_| r.str()).collect::<Res<Vec<_>>>()?;
        let total: usize = counts.iter().sum();
        let names = (0..total).map(|_| r.str()).collect::<Res<Vec<_>>>()?;
        let dnums = (0..total).map(|_| r.int().map(|v| v as usize)).collect::<Res<Vec<_>>>()?;
        let fsizes = (0..total).map(|_| r.int()).collect::<Res<Vec<_>>>()?;
        let times = (0..total).map(|_| r.u32()).collect::<Res<Vec<_>>>()?;
        let isdir = (0..total).map(|_| r.u8()).collect::<Res<Vec<_>>>()?;
        let crcs = (0..total).map(|_| r.u32()).collect::<Res<Vec<_>>>()?;
        let mut first = files.len();
        for k in 0..nb {
            let pos = dpos.checked_sub(offs[k]).ok_or_else(bad)?;
            if comps[k].contains("+aes") || comps[k].contains("+serpent") || comps[k].contains("+blowfish") || comps[k].contains("+twofish") {
                return Err(Error::new(Kind::Password, "password-protected FreeArc: unpack it normally"));
            }
            blocks.push(Block { comp: comps[k].clone(), pos, size: sizes[k], first, count: counts[k] });
            first += counts[k];
        }
        for i in 0..total {
            let dir = dirs.get(dnums[i]).cloned().ok_or_else(bad)?;
            files.push(FFile { name: names[i].clone(), dir, size: fsizes[i], time: times[i], isdir: isdir[i] != 0, crc: crcs[i] });
        }
        Ok(())
    }

    fn full(f: &FFile) -> String {
        if f.dir.is_empty() {
            f.name.clone()
        } else {
            format!("{}/{}", f.dir, f.name)
        }
    }

    fn build_pieces(&mut self) -> Res<()> {
        let mut pieces = Vec::new();
        for (k, b) in self.blocks.iter().enumerate() {
            let fs = &self.files[b.first..b.first + b.count];
            for f in fs {
                member_rel(&FreeArc::full(f))?;
            }
            let end = if k + 1 < self.blocks.len() { self.blocks[k + 1].pos } else { b.pos + b.size };
            let label = fs.iter().find(|f| !f.isdir).map(FreeArc::full).unwrap_or_default();
            pieces.push(Piece { start: b.pos, end, out: fs.iter().map(|f| f.size).sum(), label });
        }
        self.pieces = pieces;
        Ok(())
    }

    /// Directory + footer for blocks `sel`, written at `at` (the archive then ends there).
    fn index(&self, sel: &[usize], at: u64) -> Vec<u8> {
        let mut dir = Vec::new();
        let files: Vec<&FFile> = sel.iter().flat_map(|&k| &self.files[self.blocks[k].first..self.blocks[k].first + self.blocks[k].count]).collect();
        wint(&mut dir, sel.len() as u64);
        for &k in sel {
            wint(&mut dir, self.blocks[k].count as u64);
        }
        for &k in sel {
            wstr(&mut dir, &self.blocks[k].comp);
        }
        for &k in sel {
            wint(&mut dir, at - self.blocks[k].pos);
        }
        for &k in sel {
            wint(&mut dir, self.blocks[k].size);
        }
        let mut dirs: Vec<String> = Vec::new();
        let mut dnum = Vec::new();
        for f in &files {
            let i = match dirs.iter().position(|d| *d == f.dir) {
                Some(i) => i,
                None => {
                    dirs.push(f.dir.clone());
                    dirs.len() - 1
                }
            };
            dnum.push(i);
        }
        wint(&mut dir, dirs.len() as u64);
        for d in &dirs {
            wstr(&mut dir, d);
        }
        for f in &files {
            wstr(&mut dir, &f.name);
        }
        for &d in &dnum {
            wint(&mut dir, d as u64);
        }
        for f in &files {
            wint(&mut dir, f.size);
        }
        for f in &files {
            dir.extend_from_slice(&f.time.to_le_bytes());
        }
        for f in &files {
            dir.push(f.isdir as u8);
        }
        for f in &files {
            dir.extend_from_slice(&f.crc.to_le_bytes());
        }
        let fpos = at + dir.len() as u64;
        let mut footer = Vec::new();
        let n = 1 + self.header.is_some() as u64;
        wint(&mut footer, n);
        if let Some((comp, pos, orig, size, crc)) = &self.header {
            wint(&mut footer, HEADER_BLOCK);
            wstr(&mut footer, comp);
            wint(&mut footer, fpos - pos);
            wint(&mut footer, *orig);
            wint(&mut footer, *size);
            footer.extend_from_slice(&crc.to_le_bytes());
        }
        wint(&mut footer, DIR_BLOCK);
        wstr(&mut footer, "storing");
        wint(&mut footer, fpos - at);
        wint(&mut footer, dir.len() as u64);
        wint(&mut footer, dir.len() as u64);
        footer.extend_from_slice(&crc32(&dir).to_le_bytes());
        footer.push(0); // not locked
        wint(&mut footer, 0); // no comment
        let mut desc = SIG.to_vec();
        wint(&mut desc, FOOTER_BLOCK);
        wstr(&mut desc, "storing");
        wint(&mut desc, footer.len() as u64);
        wint(&mut desc, footer.len() as u64);
        desc.extend_from_slice(&crc32(&footer).to_le_bytes());
        let dc = crc32(&desc);
        desc.extend_from_slice(&dc.to_le_bytes());
        let mut out = dir;
        out.extend_from_slice(&footer);
        out.extend_from_slice(&desc);
        out
    }

    fn put_index(&self, store: &mut Store, sel: &[usize], at: u64) -> Res<()> {
        let b = self.index(sel, at);
        store.write_at(at, &b)?;
        store.set_len(at + b.len() as u64)?;
        store.sync()?;
        Ok(())
    }

    fn run_unarc(&self, ctx: &Ctx, archive: &std::path::Path, test: bool) -> Res<()> {
        let tool = self.unarc.as_ref().ok_or_else(|| Error::new(Kind::Unsupported, "unarc is needed for FreeArc archives"))?;
        let mut c = crate::util::tool(tool);
        c.arg(if test { "t" } else { "x" }).arg("-o+").arg("-ld-");
        for a in &self.unarc_args {
            c.arg(a);
        }
        if !test {
            c.arg(format!("-dp{}", ctx.stage.display()));
        }
        let out = c.arg(archive).stdin(Stdio::null()).output().map_err(Error::from)?;
        if !out.status.success() {
            let text = String::from_utf8_lossy(&out.stdout).to_string() + &String::from_utf8_lossy(&out.stderr);
            let msg = text.lines().filter(|l| l.contains("ERROR") || l.contains("BFE-EXT")).last().unwrap_or("unarc failed").to_string();
            return Err(Error::new(Kind::Damaged, format!("unarc: {msg}")));
        }
        Ok(())
    }

    pub fn from_snapshot(b: &[u8], unarc: Option<PathBuf>, unarc_args: Vec<String>) -> Res<FreeArc> {
        let mut r = ser::R::new(b);
        let header = if r.u8()? == 1 { Some((r.str()?, r.u64()?, r.u64()?, r.u64()?, r.u64()? as u32)) } else { None };
        let nb = r.u64()? as usize;
        let mut blocks = Vec::with_capacity(nb);
        for _ in 0..nb {
            blocks.push(Block { comp: r.str()?, pos: r.u64()?, size: r.u64()?, first: r.u64()? as usize, count: r.u64()? as usize });
        }
        let nf = r.u64()? as usize;
        let mut files = Vec::with_capacity(nf);
        for _ in 0..nf {
            files.push(FFile { name: r.str()?, dir: r.str()?, size: r.u64()?, time: r.u64()? as u32, isdir: r.u8()? == 1, crc: r.u64()? as u32 });
        }
        let mut f = FreeArc { blocks, files, pieces: Vec::new(), header, unarc, unarc_args };
        f.build_pieces()?;
        Ok(f)
    }
}

impl Format for FreeArc {
    fn kind(&self) -> &'static str {
        "freearc"
    }
    fn pieces(&self) -> &[Piece] {
        &self.pieces
    }
    fn needs_view(&self, _i: usize) -> bool {
        true
    }

    fn extract(&mut self, store: &mut Store, i: usize, ctx: &mut Ctx) -> Res<()> {
        self.put_index(store, &[i], self.pieces[i].end)?;
        let path = store.vols[0].path.clone();
        self.run_unarc(ctx, &path, false)?;
        let b = self.blocks[i].clone();
        let mut total = 0u64;
        for rel in walk_files(&ctx.stage)? {
            total += fs::metadata(ctx.stage.join(&rel))?.len();
            ctx.add_file(rel);
        }
        for f in &self.files[b.first..b.first + b.count] {
            if f.isdir {
                if let Some(rel) = member_rel(&FreeArc::full(f))? {
                    ctx.add_dir(rel);
                }
            }
        }
        if total != self.pieces[i].out {
            return Err(Error::new(Kind::Damaged, format!("unarc unpacked {total} bytes of {}", self.pieces[i].out)));
        }
        Ok(())
    }

    fn rewrite(&self, store: &mut Store, keep: usize) -> Res<()> {
        let at = if keep < self.pieces.len() { self.pieces[keep].start } else { self.pieces.last().map(|p| p.end).unwrap_or(0) };
        let sel: Vec<usize> = (0..keep).collect();
        self.put_index(store, &sel, at)
    }

    fn verify(&mut self, store: &mut Store, ctx: &mut Ctx) -> Res<()> {
        let path = store.vols[0].path.clone();
        self.run_unarc(ctx, &path, true)
    }

    fn snapshot(&self) -> Vec<u8> {
        let mut w = ser::W::default();
        match &self.header {
            Some((c, p, o, s, k)) => {
                w.u8(1).str(c).u64(*p).u64(*o).u64(*s).u64(*k as u64);
            }
            None => {
                w.u8(0);
            }
        }
        w.u64(self.blocks.len() as u64);
        for b in &self.blocks {
            w.str(&b.comp).u64(b.pos).u64(b.size).u64(b.first as u64).u64(b.count as u64);
        }
        w.u64(self.files.len() as u64);
        for f in &self.files {
            w.str(&f.name).str(&f.dir).u64(f.size).u64(f.time as u64).u8(f.isdir as u8).u64(f.crc as u64);
        }
        w.0
    }
}
