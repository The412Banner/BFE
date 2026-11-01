//! 7z. Every folder (solid block) is a piece. To unpack one, BFE writes a header that lists only
//! that folder after its data and lets the bundled 7-Zip unpack "everything"; then it cuts the
//! folder off and writes a header for the folders that are left, so the leftover is a normal 7z.
//! Folders and empty files have no data and are made at the end. An archive that is one big
//! solid block has one piece: it can't be shrunk while unpacking.

use crate::engine::{Ctx, Format, Piece};
use crate::lzma::{lzma2_decode, lzma_decode};
use crate::ser;
use crate::store::Store;
use crate::util::{crc32, le32, le64, member_rel, Error, Kind, Res};
use crate::zip::walk_files;
use std::fs::{self, File};
use std::process::Stdio;

const SIG: [u8; 6] = [b'7', b'z', 0xBC, 0xAF, 0x27, 0x1C];

#[derive(Clone)]
struct Coder {
    id: Vec<u8>,
    nin: u64,
    nout: u64,
    props: Vec<u8>,
}

#[derive(Clone)]
struct Folder {
    coders: Vec<Coder>,
    binds: Vec<(u64, u64)>,
    packed: Vec<u64>,
    sizes: Vec<u64>,
    crc: Option<u32>,
    /// Pack streams before this folder's first one.
    first_pack: usize,
    substreams: u64,
}

impl Folder {
    fn out_total(&self) -> u64 {
        self.coders.iter().map(|c| c.nout).sum()
    }
    /// The folder's result: the one output stream no bind pair consumes.
    fn unpack_size(&self) -> u64 {
        for (i, s) in self.sizes.iter().enumerate() {
            if !self.binds.iter().any(|&(_, o)| o == i as u64) {
                return *s;
            }
        }
        0
    }
}

#[derive(Clone, Default)]
struct F7 {
    name: String,
    stream: bool,
    empty_file: bool,
    mtime: Option<u64>,
    ctime: Option<u64>,
    atime: Option<u64>,
    attrib: Option<u32>,
    /// For files with data: folder and its size/CRC.
    folder: usize,
    size: u64,
    crc: Option<u32>,
}

pub struct SevenZ {
    pack_pos: u64,
    pack_sizes: Vec<u64>,
    folders: Vec<Folder>,
    files: Vec<F7>,
    pieces: Vec<Piece>,
    piece_folder: Vec<usize>,
    /// Where the original header began (absolute).
    header_at: u64,
}

// ── Reading 7z's numbers and header ──

struct Rd<'a> {
    b: &'a [u8],
    p: usize,
}

fn bad() -> Error {
    Error::new(Kind::Damaged, "7z header is damaged")
}

impl<'a> Rd<'a> {
    fn byte(&mut self) -> Res<u8> {
        let v = *self.b.get(self.p).ok_or_else(bad)?;
        self.p += 1;
        Ok(v)
    }
    fn bytes(&mut self, n: usize) -> Res<&'a [u8]> {
        let s = self.b.get(self.p..self.p + n).ok_or_else(bad)?;
        self.p += n;
        Ok(s)
    }
    fn num(&mut self) -> Res<u64> {
        let first = self.byte()?;
        let mut mask = 0x80u8;
        let mut v: u64 = 0;
        for i in 0..8 {
            if first & mask == 0 {
                let high = (first & mask.wrapping_sub(1)) as u64;
                v |= high << (8 * i);
                return Ok(v);
            }
            v |= (self.byte()? as u64) << (8 * i);
            mask >>= 1;
        }
        Ok(v)
    }
    fn u32(&mut self) -> Res<u32> {
        Ok(le32(self.bytes(4)?, 0))
    }
    fn u64(&mut self) -> Res<u64> {
        Ok(le64(self.bytes(8)?, 0))
    }
    fn bits(&mut self, n: usize) -> Res<Vec<bool>> {
        let mut v = Vec::with_capacity(n);
        let mut cur = 0u8;
        for i in 0..n {
            if i % 8 == 0 {
                cur = self.byte()?;
            }
            v.push(cur & (0x80 >> (i % 8)) != 0);
        }
        Ok(v)
    }
    fn defined(&mut self, n: usize) -> Res<Vec<bool>> {
        if self.byte()? != 0 {
            Ok(vec![true; n])
        } else {
            self.bits(n)
        }
    }
    fn digests(&mut self, n: usize) -> Res<Vec<Option<u32>>> {
        let d = self.defined(n)?;
        d.into_iter().map(|x| if x { self.u32().map(Some) } else { Ok(None) }).collect()
    }
}

struct Streams {
    pack_pos: u64,
    pack_sizes: Vec<u64>,
    folders: Vec<Folder>,
    sub_sizes: Vec<u64>,
    sub_crcs: Vec<Option<u32>>,
}

fn read_folder(r: &mut Rd) -> Res<Folder> {
    let nc = r.num()? as usize;
    if nc == 0 || nc > 64 {
        return Err(bad());
    }
    let mut coders = Vec::new();
    for _ in 0..nc {
        let flags = r.byte()?;
        if flags & 0x80 != 0 {
            return Err(Error::new(Kind::Unsupported, "7z alternative coders aren't supported"));
        }
        let id = r.bytes((flags & 0x0F) as usize)?.to_vec();
        let (nin, nout) = if flags & 0x10 != 0 { (r.num()?, r.num()?) } else { (1, 1) };
        let props = if flags & 0x20 != 0 {
            let n = r.num()? as usize;
            r.bytes(n)?.to_vec()
        } else {
            Vec::new()
        };
        coders.push(Coder { id, nin, nout, props });
    }
    let tout: u64 = coders.iter().map(|c| c.nout).sum();
    let tin: u64 = coders.iter().map(|c| c.nin).sum();
    let mut binds = Vec::new();
    for _ in 0..tout.saturating_sub(1) {
        binds.push((r.num()?, r.num()?));
    }
    let np = tin - binds.len() as u64;
    let packed = if np == 1 {
        let free = (0..tin).find(|i| !binds.iter().any(|&(bi, _)| bi == *i)).ok_or_else(bad)?;
        vec![free]
    } else {
        (0..np).map(|_| r.num()).collect::<Res<Vec<_>>>()?
    };
    Ok(Folder { coders, binds, packed, sizes: Vec::new(), crc: None, first_pack: 0, substreams: 1 })
}

fn read_streams(r: &mut Rd) -> Res<Streams> {
    let mut s = Streams { pack_pos: 0, pack_sizes: Vec::new(), folders: Vec::new(), sub_sizes: Vec::new(), sub_crcs: Vec::new() };
    let mut have_sub = false;
    loop {
        match r.byte()? {
            0x00 => break,
            0x06 => {
                s.pack_pos = r.num()?;
                let n = r.num()? as usize;
                loop {
                    match r.byte()? {
                        0x00 => break,
                        0x09 => s.pack_sizes = (0..n).map(|_| r.num()).collect::<Res<_>>()?,
                        0x0A => {
                            r.digests(n)?;
                        }
                        _ => return Err(bad()),
                    }
                }
            }
            0x07 => {
                if r.byte()? != 0x0B {
                    return Err(bad());
                }
                let nf = r.num()? as usize;
                if r.byte()? != 0 {
                    return Err(Error::new(Kind::Unsupported, "external 7z folders aren't supported"));
                }
                for _ in 0..nf {
                    s.folders.push(read_folder(r)?);
                }
                if r.byte()? != 0x0C {
                    return Err(bad());
                }
                for f in s.folders.iter_mut() {
                    f.sizes = (0..f.out_total()).map(|_| r.num()).collect::<Res<_>>()?;
                }
                loop {
                    match r.byte()? {
                        0x00 => break,
                        0x0A => {
                            let d = r.digests(nf)?;
                            for (f, c) in s.folders.iter_mut().zip(d) {
                                f.crc = c;
                            }
                        }
                        _ => return Err(bad()),
                    }
                }
            }
            0x08 => {
                have_sub = true;
                let mut sizes_read = false;
                loop {
                    match r.byte()? {
                        0x00 => break,
                        0x0D => {
                            for f in s.folders.iter_mut() {
                                f.substreams = r.num()?;
                            }
                        }
                        0x09 => {
                            sizes_read = true;
                            for f in &s.folders {
                                let mut sum = 0;
                                for _ in 1..f.substreams.max(1) {
                                    let v = r.num()?;
                                    s.sub_sizes.push(v);
                                    sum += v;
                                }
                                if f.substreams > 0 {
                                    s.sub_sizes.push(f.unpack_size().checked_sub(sum).ok_or_else(bad)?);
                                }
                            }
                        }
                        0x0A => {
                            let n: u64 = s.folders.iter().map(|f| if f.substreams == 1 && f.crc.is_some() { 0 } else { f.substreams }).sum();
                            let d = r.digests(n as usize)?;
                            let mut it = d.into_iter();
                            for f in &s.folders {
                                if f.substreams == 1 && f.crc.is_some() {
                                    s.sub_crcs.push(f.crc);
                                } else {
                                    for _ in 0..f.substreams {
                                        s.sub_crcs.push(it.next().flatten());
                                    }
                                }
                            }
                        }
                        _ => return Err(bad()),
                    }
                }
                if !sizes_read {
                    for f in &s.folders {
                        if f.substreams == 1 {
                            s.sub_sizes.push(f.unpack_size());
                        } else if f.substreams > 1 {
                            return Err(bad());
                        }
                    }
                }
            }
            _ => return Err(bad()),
        }
    }
    if !have_sub {
        for f in &s.folders {
            s.sub_sizes.push(f.unpack_size());
            s.sub_crcs.push(f.crc);
        }
    }
    let nsub: u64 = s.folders.iter().map(|f| f.substreams).sum();
    if s.sub_crcs.is_empty() {
        let mut v = Vec::new();
        for f in &s.folders {
            for _ in 0..f.substreams {
                v.push(if f.substreams == 1 { f.crc } else { None });
            }
        }
        s.sub_crcs = v;
    }
    if s.sub_sizes.len() as u64 != nsub || s.sub_crcs.len() as u64 != nsub {
        return Err(bad());
    }
    let mut fp = 0;
    for f in s.folders.iter_mut() {
        f.first_pack = fp;
        fp += f.packed.len();
    }
    if fp != s.pack_sizes.len() {
        return Err(bad());
    }
    Ok(s)
}

fn decode_folder(store: &Store, s: &Streams, f: &Folder) -> Res<Vec<u8>> {
    // Only the simple shapes used for headers: one coder (LZMA, LZMA2 or Copy), one pack stream.
    if f.coders.len() != 1 || f.packed.len() != 1 {
        return Err(Error::new(Kind::Unsupported, "this 7z header compression isn't supported"));
    }
    let off = 32 + s.pack_pos + s.pack_sizes[..f.first_pack].iter().sum::<u64>();
    let src = store.read_vec(off, s.pack_sizes[f.first_pack] as usize)?;
    let size = f.unpack_size() as usize;
    let c = &f.coders[0];
    let out = match c.id.as_slice() {
        [0x00] => src,
        [0x03, 0x01, 0x01] if c.props.len() >= 1 => lzma_decode(&src, c.props[0], size)?,
        [0x21] => lzma2_decode(&src, size + 1)?,
        [0x06, 0xF1, 0x07, 0x01] => return Err(Error::new(Kind::Password, "the 7z file list is password-protected")),
        _ => return Err(Error::new(Kind::Unsupported, "this 7z header compression isn't supported")),
    };
    if out.len() != size || f.crc.map(|c| c != crc32(&out)).unwrap_or(false) {
        return Err(bad());
    }
    Ok(out)
}

fn utf16z(b: &[u8]) -> Res<(Vec<String>, usize)> {
    let mut names = Vec::new();
    let mut cur = Vec::new();
    let mut p = 0;
    while p + 1 < b.len() {
        let u = u16::from_le_bytes([b[p], b[p + 1]]);
        p += 2;
        if u == 0 {
            names.push(String::from_utf16_lossy(&cur));
            cur.clear();
        } else {
            cur.push(u);
        }
    }
    Ok((names, p))
}

impl SevenZ {
    pub fn looks_like(head: &[u8]) -> bool {
        head.starts_with(&SIG)
    }

    pub fn open(store: &Store) -> Res<SevenZ> {
        let sh = store.read_vec(0, 32)?;
        if sh[..6] != SIG {
            return Err(Error::new(Kind::Unsupported, "not a 7z archive"));
        }
        if crc32(&sh[12..32]) != le32(&sh, 8) {
            return Err(Error::new(Kind::Damaged, "7z start header is damaged"));
        }
        let (noff, nsize, ncrc) = (le64(&sh, 12), le64(&sh, 20), le32(&sh, 28));
        let header_at = 32 + noff;
        if nsize == 0 || nsize > 1 << 30 || header_at + nsize > store.len() {
            return Err(Error::new(Kind::Damaged, "7z header is missing (incomplete download?)"));
        }
        let mut hdr = store.read_vec(header_at, nsize as usize)?;
        if crc32(&hdr) != ncrc {
            return Err(Error::new(Kind::Damaged, "7z header failed its CRC check"));
        }
        // An encoded header: decode it first (it may take a few rounds).
        let mut guard = 0;
        while hdr.first() == Some(&0x17) {
            guard += 1;
            if guard > 4 {
                return Err(bad());
            }
            let mut r = Rd { b: &hdr, p: 1 };
            let s = read_streams(&mut r)?;
            if s.folders.len() != 1 {
                return Err(bad());
            }
            hdr = decode_folder(store, &s, &s.folders[0])?;
        }
        let mut r = Rd { b: &hdr, p: 0 };
        if r.byte()? != 0x01 {
            return Err(bad());
        }
        let mut streams: Option<Streams> = None;
        let mut files: Vec<F7> = Vec::new();
        loop {
            match r.byte()? {
                0x00 => break,
                0x02 => loop {
                    let t = r.byte()?;
                    if t == 0 {
                        break;
                    }
                    let n = r.num()? as usize;
                    r.bytes(n)?;
                },
                0x03 => return Err(Error::new(Kind::Unsupported, "7z additional streams aren't supported")),
                0x04 => streams = Some(read_streams(&mut r)?),
                0x05 => files = SevenZ::read_files(&mut r)?,
                _ => return Err(bad()),
            }
        }
        let s = streams.unwrap_or(Streams { pack_pos: 0, pack_sizes: Vec::new(), folders: Vec::new(), sub_sizes: Vec::new(), sub_crcs: Vec::new() });
        for f in &s.folders {
            if f.coders.iter().any(|c| c.id == [0x06, 0xF1, 0x07, 0x01]) {
                return Err(Error::new(Kind::Password, "password-protected 7z: unpack it normally"));
            }
        }
        // Files with data take the substreams in order.
        let mut sub = 0usize;
        let mut fi = 0usize;
        let mut left = s.folders.first().map(|f| f.substreams).unwrap_or(0);
        for f in files.iter_mut().filter(|f| f.stream) {
            while left == 0 {
                fi += 1;
                if fi >= s.folders.len() {
                    return Err(bad());
                }
                left = s.folders[fi].substreams;
            }
            f.folder = fi;
            f.size = s.sub_sizes[sub];
            f.crc = s.sub_crcs[sub];
            sub += 1;
            left -= 1;
        }
        let mut z = SevenZ { pack_pos: s.pack_pos, pack_sizes: s.pack_sizes, folders: s.folders, files, pieces: Vec::new(), piece_folder: Vec::new(), header_at };
        z.build_pieces()?;
        Ok(z)
    }

    fn read_files(r: &mut Rd) -> Res<Vec<F7>> {
        let n = r.num()? as usize;
        let mut files = vec![F7 { stream: true, ..Default::default() }; n];
        let mut empty_idx: Vec<usize> = Vec::new();
        loop {
            let t = r.num()?;
            if t == 0 {
                break;
            }
            let size = r.num()? as usize;
            let data = r.bytes(size)?;
            let mut d = Rd { b: data, p: 0 };
            match t {
                0x0E => {
                    let v = d.bits(n)?;
                    empty_idx.clear();
                    for (i, e) in v.into_iter().enumerate() {
                        files[i].stream = !e;
                        if e {
                            empty_idx.push(i);
                        }
                    }
                }
                0x0F => {
                    let v = d.bits(empty_idx.len())?;
                    for (k, e) in v.into_iter().enumerate() {
                        files[empty_idx[k]].empty_file = e;
                    }
                }
                0x10 => {
                    if d.bits(empty_idx.len())?.into_iter().any(|x| x) {
                        return Err(Error::new(Kind::Unsupported, "7z update archives (anti items) aren't supported"));
                    }
                }
                0x11 => {
                    if d.byte()? != 0 {
                        return Err(Error::new(Kind::Unsupported, "external 7z names aren't supported"));
                    }
                    let (names, _) = utf16z(&data[1..])?;
                    if names.len() < n {
                        return Err(bad());
                    }
                    for (f, nm) in files.iter_mut().zip(names) {
                        f.name = nm;
                    }
                }
                0x12 | 0x13 | 0x14 => {
                    let def = d.defined(n)?;
                    if d.byte()? != 0 {
                        return Err(Error::new(Kind::Unsupported, "external 7z times aren't supported"));
                    }
                    for (i, x) in def.into_iter().enumerate() {
                        if x {
                            let v = d.u64()?;
                            match t {
                                0x12 => files[i].ctime = Some(v),
                                0x13 => files[i].atime = Some(v),
                                _ => files[i].mtime = Some(v),
                            }
                        }
                    }
                }
                0x15 => {
                    let def = d.defined(n)?;
                    if d.byte()? != 0 {
                        return Err(Error::new(Kind::Unsupported, "external 7z attributes aren't supported"));
                    }
                    for (i, x) in def.into_iter().enumerate() {
                        if x {
                            files[i].attrib = Some(d.u32()?);
                        }
                    }
                }
                _ => {} // dummy, start position, comment: not needed
            }
        }
        Ok(files)
    }

    fn build_pieces(&mut self) -> Res<()> {
        let mut pieces = Vec::new();
        let mut pf = Vec::new();
        let base = 32 + self.pack_pos;
        let total_pack: u64 = self.pack_sizes.iter().sum();
        for (k, f) in self.folders.iter().enumerate() {
            if f.packed.is_empty() {
                continue;
            }
            let start = base + self.pack_sizes[..f.first_pack].iter().sum::<u64>();
            let label = self.files.iter().find(|x| x.stream && x.folder == k).map(|x| x.name.clone()).unwrap_or_default();
            pieces.push(Piece { start, end: 0, out: f.unpack_size(), label });
            pf.push(k);
        }
        for i in 0..pieces.len() {
            pieces[i].end = if i + 1 < pieces.len() { pieces[i + 1].start } else { base + total_pack };
        }
        for f in &self.files {
            if member_rel(&f.name)?.is_none() && f.stream {
                return Err(Error::new(Kind::Unsafe, "a 7z entry has no name"));
            }
        }
        self.pieces = pieces;
        self.piece_folder = pf;
        Ok(())
    }

    // ── Writing ──

    fn wnum(o: &mut Vec<u8>, v: u64) {
        // 7z NUMBER: the first byte's leading ones say how many little-endian bytes follow.
        let mut n = 0;
        while n < 8 && v >= 1u64 << (7 * (n + 1)) {
            n += 1;
        }
        if n == 8 {
            o.push(0xFF);
            o.extend_from_slice(&v.to_le_bytes());
            return;
        }
        let high = (v >> (8 * n)) as u8;
        let mask: u8 = !(0xFFu8 >> n);
        o.push(mask | high);
        for i in 0..n {
            o.push((v >> (8 * i)) as u8);
        }
    }

    fn wbits(o: &mut Vec<u8>, v: &[bool]) {
        let mut cur = 0u8;
        for (i, &b) in v.iter().enumerate() {
            if b {
                cur |= 0x80 >> (i % 8);
            }
            if i % 8 == 7 {
                o.push(cur);
                cur = 0;
            }
        }
        if v.len() % 8 != 0 {
            o.push(cur);
        }
    }

    fn wdigests(o: &mut Vec<u8>, d: &[Option<u32>]) {
        if d.iter().all(|x| x.is_some()) {
            o.push(1);
        } else {
            o.push(0);
            let bits: Vec<bool> = d.iter().map(|x| x.is_some()).collect();
            SevenZ::wbits(o, &bits);
        }
        for c in d.iter().flatten() {
            o.extend_from_slice(&c.to_le_bytes());
        }
    }

    fn wfolder(o: &mut Vec<u8>, f: &Folder) {
        SevenZ::wnum(o, f.coders.len() as u64);
        for c in &f.coders {
            let complex = c.nin != 1 || c.nout != 1;
            let mut flags = c.id.len() as u8;
            if complex {
                flags |= 0x10;
            }
            if !c.props.is_empty() {
                flags |= 0x20;
            }
            o.push(flags);
            o.extend_from_slice(&c.id);
            if complex {
                SevenZ::wnum(o, c.nin);
                SevenZ::wnum(o, c.nout);
            }
            if !c.props.is_empty() {
                SevenZ::wnum(o, c.props.len() as u64);
                o.extend_from_slice(&c.props);
            }
        }
        for &(i, j) in &f.binds {
            SevenZ::wnum(o, i);
            SevenZ::wnum(o, j);
        }
        if f.packed.len() > 1 {
            for &p in &f.packed {
                SevenZ::wnum(o, p);
            }
        }
    }

    /// A plain header for folders `sel` (in order) and their files, plus files without data when
    /// `with_empty`.
    fn header(&self, sel: &[usize], with_empty: bool) -> Vec<u8> {
        let mut o = vec![0x01u8];
        if !sel.is_empty() {
            o.push(0x04);
            // Pack info: positions are relative to the end of the start header.
            let first = &self.folders[sel[0]];
            let pack_pos = self.pack_pos + self.pack_sizes[..first.first_pack].iter().sum::<u64>();
            o.push(0x06);
            SevenZ::wnum(&mut o, pack_pos);
            let sizes: Vec<u64> = sel.iter().flat_map(|&k| {
                let f = &self.folders[k];
                self.pack_sizes[f.first_pack..f.first_pack + f.packed.len()].to_vec()
            }).collect();
            SevenZ::wnum(&mut o, sizes.len() as u64);
            o.push(0x09);
            for s in &sizes {
                SevenZ::wnum(&mut o, *s);
            }
            o.push(0x00);
            // Folders.
            o.push(0x07);
            o.push(0x0B);
            SevenZ::wnum(&mut o, sel.len() as u64);
            o.push(0);
            for &k in sel {
                SevenZ::wfolder(&mut o, &self.folders[k]);
            }
            o.push(0x0C);
            for &k in sel {
                for s in &self.folders[k].sizes {
                    SevenZ::wnum(&mut o, *s);
                }
            }
            let fcrc: Vec<Option<u32>> = sel.iter().map(|&k| self.folders[k].crc).collect();
            if fcrc.iter().any(|c| c.is_some()) {
                o.push(0x0A);
                SevenZ::wdigests(&mut o, &fcrc);
            }
            o.push(0x00);
            // Substreams.
            o.push(0x08);
            if sel.iter().any(|&k| self.folders[k].substreams != 1) {
                o.push(0x0D);
                for &k in sel {
                    SevenZ::wnum(&mut o, self.folders[k].substreams);
                }
            }
            let mut sizes = Vec::new();
            let mut digests = Vec::new();
            for &k in sel {
                let f = &self.folders[k];
                let fs: Vec<&F7> = self.files.iter().filter(|x| x.stream && x.folder == k).collect();
                for (n, x) in fs.iter().enumerate() {
                    if n + 1 < fs.len() {
                        sizes.push(x.size);
                    }
                    if !(f.substreams == 1 && f.crc.is_some()) {
                        digests.push(x.crc);
                    }
                }
            }
            if !sizes.is_empty() {
                o.push(0x09);
                for s in sizes {
                    SevenZ::wnum(&mut o, s);
                }
            }
            if !digests.is_empty() {
                o.push(0x0A);
                SevenZ::wdigests(&mut o, &digests);
            }
            o.push(0x00);
            o.push(0x00);
        }
        let files: Vec<&F7> = self.files.iter().filter(|x| if x.stream { sel.contains(&x.folder) } else { with_empty }).collect();
        if !files.is_empty() {
            o.push(0x05);
            SevenZ::wnum(&mut o, files.len() as u64);
            let empty: Vec<bool> = files.iter().map(|x| !x.stream).collect();
            if empty.iter().any(|&e| e) {
                let mut v = Vec::new();
                SevenZ::wbits(&mut v, &empty);
                o.push(0x0E);
                SevenZ::wnum(&mut o, v.len() as u64);
                o.extend_from_slice(&v);
                let ef: Vec<bool> = files.iter().filter(|x| !x.stream).map(|x| x.empty_file).collect();
                if ef.iter().any(|&e| e) {
                    let mut v = Vec::new();
                    SevenZ::wbits(&mut v, &ef);
                    o.push(0x0F);
                    SevenZ::wnum(&mut o, v.len() as u64);
                    o.extend_from_slice(&v);
                }
            }
            let mut names = vec![0u8];
            for x in &files {
                for u in x.name.encode_utf16() {
                    names.extend_from_slice(&u.to_le_bytes());
                }
                names.extend_from_slice(&[0, 0]);
            }
            o.push(0x11);
            SevenZ::wnum(&mut o, names.len() as u64);
            o.extend_from_slice(&names);
            for (t, get) in [(0x12u8, 0u8), (0x13, 1), (0x14, 2)] {
                let vals: Vec<Option<u64>> = files.iter().map(|x| match get { 0 => x.ctime, 1 => x.atime, _ => x.mtime }).collect();
                if vals.iter().any(|v| v.is_some()) {
                    let mut v = Vec::new();
                    SevenZ::wdefined(&mut v, &vals.iter().map(|x| x.is_some()).collect::<Vec<_>>());
                    v.push(0);
                    for t in vals.iter().flatten() {
                        v.extend_from_slice(&t.to_le_bytes());
                    }
                    o.push(t);
                    SevenZ::wnum(&mut o, v.len() as u64);
                    o.extend_from_slice(&v);
                }
            }
            let attrs: Vec<Option<u32>> = files.iter().map(|x| x.attrib).collect();
            if attrs.iter().any(|a| a.is_some()) {
                let mut v = Vec::new();
                SevenZ::wdefined(&mut v, &attrs.iter().map(|x| x.is_some()).collect::<Vec<_>>());
                v.push(0);
                for a in attrs.iter().flatten() {
                    v.extend_from_slice(&a.to_le_bytes());
                }
                o.push(0x15);
                SevenZ::wnum(&mut o, v.len() as u64);
                o.extend_from_slice(&v);
            }
            o.push(0x00);
        }
        o.push(0x00);
        o
    }

    fn wdefined(o: &mut Vec<u8>, d: &[bool]) {
        if d.iter().all(|&x| x) {
            o.push(1);
        } else {
            o.push(0);
            SevenZ::wbits(o, d);
        }
    }

    /// Writes `hdr` at `at`, ends the archive there and points the start header at it.
    fn put_header(store: &mut Store, at: u64, hdr: &[u8]) -> Res<()> {
        store.write_at(at, hdr)?;
        store.set_len(at + hdr.len() as u64)?;
        store.sync()?;
        let mut sh = Vec::with_capacity(32);
        sh.extend_from_slice(&SIG);
        sh.extend_from_slice(&[0, 4]);
        let mut rest = Vec::with_capacity(20);
        rest.extend_from_slice(&(at - 32).to_le_bytes());
        rest.extend_from_slice(&(hdr.len() as u64).to_le_bytes());
        rest.extend_from_slice(&crc32(hdr).to_le_bytes());
        sh.extend_from_slice(&crc32(&rest).to_le_bytes());
        sh.extend_from_slice(&rest);
        store.write_at(0, &sh)?;
        store.sync()?;
        Ok(())
    }

    fn run_7z(ctx: &Ctx, archive: &std::path::Path, test: bool) -> Res<()> {
        let tool = ctx.tool7z.as_ref().ok_or_else(|| Error::new(Kind::Unsupported, "7-Zip is needed for 7z archives"))?;
        let mut c = crate::util::tool(tool);
        if test {
            c.arg("t");
        } else {
            c.arg("x").arg(format!("-o{}", ctx.stage.display()));
        }
        let out = c.arg("-y").arg("-bd").arg("-p-").arg(archive).stdin(Stdio::null()).output().map_err(Error::from)?;
        if !out.status.success() {
            let msg = String::from_utf8_lossy(&out.stderr).lines().filter(|l| !l.trim().is_empty()).last().unwrap_or("").to_string();
            return Err(Error::new(Kind::Damaged, format!("7-Zip: {msg}")));
        }
        Ok(())
    }

    pub fn from_snapshot(b: &[u8]) -> Res<SevenZ> {
        let mut r = ser::R::new(b);
        let pack_pos = r.u64()?;
        let header_at = r.u64()?;
        let np = r.u64()? as usize;
        let pack_sizes = (0..np).map(|_| r.u64()).collect::<Res<Vec<_>>>()?;
        let nf = r.u64()? as usize;
        let mut folders = Vec::with_capacity(nf);
        for _ in 0..nf {
            let nc = r.u64()? as usize;
            let mut coders = Vec::new();
            for _ in 0..nc {
                coders.push(Coder { id: r.bytes()?, nin: r.u64()?, nout: r.u64()?, props: r.bytes()? });
            }
            let nb = r.u64()? as usize;
            let binds = (0..nb).map(|_| Ok((r.u64()?, r.u64()?))).collect::<Res<Vec<_>>>()?;
            let npk = r.u64()? as usize;
            let packed = (0..npk).map(|_| r.u64()).collect::<Res<Vec<_>>>()?;
            let ns = r.u64()? as usize;
            let sizes = (0..ns).map(|_| r.u64()).collect::<Res<Vec<_>>>()?;
            let crc = if r.u8()? == 1 { Some(r.u64()? as u32) } else { r.u64().map(|_| None)? };
            let first_pack = r.u64()? as usize;
            let substreams = r.u64()?;
            folders.push(Folder { coders, binds, packed, sizes, crc, first_pack, substreams });
        }
        let nfi = r.u64()? as usize;
        let mut files = Vec::with_capacity(nfi);
        for _ in 0..nfi {
            let name = r.str()?;
            let flags = r.u8()?;
            let opt = |r: &mut ser::R, set: bool| -> Res<Option<u64>> { let v = r.u64()?; Ok(if set { Some(v) } else { None }) };
            let mtime = opt(&mut r, flags & 4 != 0)?;
            let ctime = opt(&mut r, flags & 8 != 0)?;
            let atime = opt(&mut r, flags & 16 != 0)?;
            let attrib = opt(&mut r, flags & 32 != 0)?.map(|v| v as u32);
            let folder = r.u64()? as usize;
            let size = r.u64()?;
            let crc = opt(&mut r, flags & 64 != 0)?.map(|v| v as u32);
            files.push(F7 { name, stream: flags & 1 != 0, empty_file: flags & 2 != 0, mtime, ctime, atime, attrib, folder, size, crc });
        }
        let mut z = SevenZ { pack_pos, pack_sizes, folders, files, pieces: Vec::new(), piece_folder: Vec::new(), header_at };
        z.build_pieces()?;
        Ok(z)
    }
}

impl Format for SevenZ {
    fn kind(&self) -> &'static str {
        "7z"
    }
    fn pieces(&self) -> &[Piece] {
        &self.pieces
    }
    fn needs_view(&self, _i: usize) -> bool {
        true
    }

    fn extract(&mut self, store: &mut Store, i: usize, ctx: &mut Ctx) -> Res<()> {
        let k = self.piece_folder[i];
        let hdr = self.header(&[k], false);
        SevenZ::put_header(store, self.pieces[i].end, &hdr)?;
        let first = store.vols[0].path.clone();
        SevenZ::run_7z(ctx, &first, false)?;
        let mut total = 0u64;
        for rel in walk_files(&ctx.stage)? {
            total += fs::metadata(ctx.stage.join(&rel))?.len();
            ctx.add_file(rel);
        }
        let want: u64 = self.files.iter().filter(|x| x.stream && x.folder == k).map(|x| x.size).sum();
        if total != want {
            return Err(Error::new(Kind::Damaged, format!("7-Zip unpacked {total} bytes of {want} for {}", self.pieces[i].label)));
        }
        Ok(())
    }

    fn rewrite(&self, store: &mut Store, keep: usize) -> Res<()> {
        let sel: Vec<usize> = self.piece_folder[..keep].to_vec();
        let at = if keep < self.pieces.len() {
            self.pieces[keep].start
        } else {
            self.pieces.last().map(|p| p.end).unwrap_or(self.header_at.min(32 + self.pack_pos))
        };
        let hdr = self.header(&sel, true);
        SevenZ::put_header(store, at, &hdr)
    }

    fn verify(&mut self, store: &mut Store, ctx: &mut Ctx) -> Res<()> {
        let first = store.vols[0].path.clone();
        SevenZ::run_7z(ctx, &first, true)
    }

    fn finish(&mut self, ctx: &mut Ctx) -> Res<()> {
        for f in self.files.iter().filter(|x| !x.stream) {
            let Some(rel) = member_rel(&f.name)? else { continue };
            if f.empty_file {
                let p = ctx.stage_path(&rel);
                if let Some(d) = p.parent() {
                    fs::create_dir_all(d)?;
                }
                File::create(&p)?;
                ctx.add_file(rel);
            } else {
                ctx.add_dir(rel);
            }
        }
        Ok(())
    }

    fn snapshot(&self) -> Vec<u8> {
        let mut w = ser::W::default();
        w.u64(self.pack_pos).u64(self.header_at).u64(self.pack_sizes.len() as u64);
        for s in &self.pack_sizes {
            w.u64(*s);
        }
        w.u64(self.folders.len() as u64);
        for f in &self.folders {
            w.u64(f.coders.len() as u64);
            for c in &f.coders {
                w.bytes(&c.id).u64(c.nin).u64(c.nout).bytes(&c.props);
            }
            w.u64(f.binds.len() as u64);
            for &(a, b) in &f.binds {
                w.u64(a).u64(b);
            }
            w.u64(f.packed.len() as u64);
            for &p in &f.packed {
                w.u64(p);
            }
            w.u64(f.sizes.len() as u64);
            for &s in &f.sizes {
                w.u64(s);
            }
            match f.crc {
                Some(c) => w.u8(1).u64(c as u64),
                None => w.u8(0).u64(0),
            };
            w.u64(f.first_pack as u64).u64(f.substreams);
        }
        w.u64(self.files.len() as u64);
        for f in &self.files {
            let flags = (f.stream as u8)
                | (f.empty_file as u8) << 1
                | (f.mtime.is_some() as u8) << 2
                | (f.ctime.is_some() as u8) << 3
                | (f.atime.is_some() as u8) << 4
                | (f.attrib.is_some() as u8) << 5
                | (f.crc.is_some() as u8) << 6;
            w.str(&f.name).u8(flags);
            w.u64(f.mtime.unwrap_or(0)).u64(f.ctime.unwrap_or(0)).u64(f.atime.unwrap_or(0)).u64(f.attrib.unwrap_or(0) as u64);
            w.u64(f.folder as u64).u64(f.size).u64(f.crc.unwrap_or(0) as u64);
        }
        w.0
    }
}
