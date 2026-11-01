//! Deflate decoder (RFC 1951), written for BFE. Besides plain decoding it can stop at any block
//! boundary and later continue from there: the position (in bits) and the last 32 KiB of output
//! are all that's needed. The gzip path uses that to resume a `.tar.gz` whose start is gone.

use crate::util::{Crc32, Error, Kind, Res};
use std::io::{Read, Write};

const WSIZE: usize = 32768;

// ── Bits, least significant first ──

pub struct BitIn<R: Read> {
    r: R,
    buf: Box<[u8]>,
    pos: usize,
    len: usize,
    bitbuf: u64,
    bitcnt: u32,
    /// Bytes moved from `buf` into `bitbuf` so far (relative to where reading began).
    taken: u64,
    /// Bit offset of where reading began (resume support).
    base_bits: u64,
    eof: bool,
}

impl<R: Read> BitIn<R> {
    pub fn new(r: R) -> BitIn<R> {
        BitIn { r, buf: vec![0u8; 1 << 16].into_boxed_slice(), pos: 0, len: 0, bitbuf: 0, bitcnt: 0, taken: 0, base_bits: 0, eof: false }
    }

    /// Starts reading at bit `bit` of a stream whose byte `bit / 8` is the first one `r` returns.
    pub fn at_bit(r: R, bit: u64) -> Res<BitIn<R>> {
        let mut b = BitIn::new(r);
        b.base_bits = bit & !7;
        let skip = (bit & 7) as u32;
        if skip > 0 {
            b.need(skip)?;
            b.drop(skip);
        }
        Ok(b)
    }

    fn fill(&mut self) -> Res<()> {
        while self.bitcnt <= 56 {
            if self.pos == self.len {
                if self.eof {
                    return Ok(());
                }
                self.len = loop {
                    match self.r.read(&mut self.buf) {
                        Ok(n) => break n,
                        Err(e) if e.kind() == std::io::ErrorKind::Interrupted => continue,
                        Err(e) => return Err(Error::new(Kind::Io, e.to_string())),
                    }
                };
                self.pos = 0;
                if self.len == 0 {
                    self.eof = true;
                    return Ok(());
                }
            }
            self.bitbuf |= (self.buf[self.pos] as u64) << self.bitcnt;
            self.pos += 1;
            self.taken += 1;
            self.bitcnt += 8;
        }
        Ok(())
    }

    #[inline]
    fn need(&mut self, n: u32) -> Res<()> {
        if self.bitcnt < n {
            self.fill()?;
            if self.bitcnt < n {
                return Err(Error::new(Kind::Damaged, "compressed data ends early"));
            }
        }
        Ok(())
    }

    #[inline]
    fn drop(&mut self, n: u32) {
        self.bitbuf >>= n;
        self.bitcnt -= n;
    }

    #[inline]
    pub fn bits(&mut self, n: u32) -> Res<u32> {
        if n == 0 {
            return Ok(0);
        }
        self.need(n)?;
        let v = (self.bitbuf & ((1u64 << n) - 1)) as u32;
        self.drop(n);
        Ok(v)
    }

    pub fn align(&mut self) {
        let r = self.bitcnt & 7;
        self.drop(r);
    }

    /// Absolute bit position of the next unread bit.
    pub fn bit_pos(&self) -> u64 {
        self.base_bits + self.taken * 8 - self.bitcnt as u64
    }

    /// Reads whole bytes after `align` (gzip trailer, headers).
    pub fn byte(&mut self) -> Res<u8> {
        Ok(self.bits(8)? as u8)
    }

    pub fn at_end(&mut self) -> Res<bool> {
        self.fill()?;
        Ok(self.bitcnt == 0)
    }
}

// ── Huffman tables: index = next 15 bits (LSB first), entry = (length << 9) | symbol ──

struct Huff {
    table: Vec<u16>,
    max: u32,
}

impl Huff {
    fn new(lens: &[u8]) -> Res<Huff> {
        let mut count = [0u16; 16];
        for &l in lens {
            count[l as usize] += 1;
        }
        count[0] = 0;
        let mut max = 0;
        for l in (1..16).rev() {
            if count[l] != 0 {
                max = l as u32;
                break;
            }
        }
        // Over-subscribed sets are invalid; incomplete ones are allowed (single-code distance trees).
        let mut left: i32 = 1;
        for l in 1..16 {
            left <<= 1;
            left -= count[l] as i32;
            if left < 0 {
                return Err(Error::new(Kind::Damaged, "bad Huffman table"));
            }
        }
        let mut next = [0u32; 16];
        let mut code = 0u32;
        for l in 1..16 {
            code = (code + count[l - 1] as u32) << 1;
            next[l] = code;
        }
        let max = max.max(1);
        let mut table = vec![0u16; 1 << max];
        for (sym, &l) in lens.iter().enumerate() {
            if l == 0 {
                continue;
            }
            let l32 = l as u32;
            let c = next[l as usize];
            next[l as usize] += 1;
            // Reverse the code: deflate stores Huffman codes most significant bit first.
            let mut rev = 0u32;
            for b in 0..l32 {
                if c & (1 << b) != 0 {
                    rev |= 1 << (l32 - 1 - b);
                }
            }
            let entry = ((l as u16) << 9) | sym as u16;
            let mut i = rev;
            while i < (1 << max) {
                table[i as usize] = entry;
                i += 1 << l32;
            }
        }
        Ok(Huff { table, max })
    }

    #[inline]
    fn decode<R: Read>(&self, b: &mut BitIn<R>) -> Res<u16> {
        if b.bitcnt < self.max {
            b.fill()?;
        }
        let e = self.table[(b.bitbuf & ((1u64 << self.max) - 1)) as usize];
        let l = (e >> 9) as u32;
        if l == 0 || l > b.bitcnt {
            return Err(Error::new(Kind::Damaged, "bad compressed data"));
        }
        b.drop(l);
        Ok(e & 0x1FF)
    }
}

const LBASE: [u16; 29] = [3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258];
const LEXT: [u8; 29] = [0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0];
const DBASE: [u16; 30] = [1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577];
const DEXT: [u8; 30] = [0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13];
const CLORDER: [usize; 19] = [16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15];

/// Where decoding can continue from: taken at the start of a block.
#[derive(Clone)]
pub struct Checkpoint {
    pub bit: u64,
    pub out: u64,
    /// The last min(out, 32 KiB) bytes of output.
    pub window: Vec<u8>,
}

pub struct Inflater {
    win: Vec<u8>,
    wpos: usize,
    /// Total bytes produced (including any restored from a checkpoint).
    pub out: u64,
    pending: Vec<u8>,
    pub crc: Crc32,
    pub done: bool,
}

impl Inflater {
    pub fn new() -> Inflater {
        Inflater { win: vec![0u8; WSIZE], wpos: 0, out: 0, pending: Vec::with_capacity(1 << 17), crc: Crc32::new(), done: false }
    }

    /// Continues from a checkpoint (the bit reader must already be at `cp.bit`).
    pub fn from_checkpoint(cp: &Checkpoint) -> Inflater {
        let mut f = Inflater::new();
        for &b in &cp.window {
            f.win[f.wpos] = b;
            f.wpos = (f.wpos + 1) & (WSIZE - 1);
        }
        f.out = cp.out;
        f
    }

    pub fn checkpoint(&self, bit: u64) -> Checkpoint {
        let n = (self.out.min(WSIZE as u64)) as usize;
        let mut window = Vec::with_capacity(n);
        let start = (self.wpos + WSIZE - n) & (WSIZE - 1);
        for k in 0..n {
            window.push(self.win[(start + k) & (WSIZE - 1)]);
        }
        Checkpoint { bit, out: self.out, window }
    }

    #[inline]
    fn put(&mut self, b: u8) {
        self.win[self.wpos] = b;
        self.wpos = (self.wpos + 1) & (WSIZE - 1);
        self.pending.push(b);
    }

    fn flush<W: Write>(&mut self, w: &mut W) -> Res<()> {
        if !self.pending.is_empty() {
            self.crc.update(&self.pending);
            w.write_all(&self.pending).map_err(Error::from)?;
            self.out += self.pending.len() as u64;
            self.pending.clear();
        }
        Ok(())
    }

    /// Decodes one block. Returns true after the final block.
    pub fn block<R: Read, W: Write>(&mut self, b: &mut BitIn<R>, w: &mut W) -> Res<bool> {
        let last = b.bits(1)? == 1;
        match b.bits(2)? {
            0 => {
                b.align();
                let len = b.bits(16)? as u16;
                let nlen = b.bits(16)? as u16;
                if len != !nlen {
                    return Err(Error::new(Kind::Damaged, "bad stored block"));
                }
                for _ in 0..len {
                    let v = b.bits(8)? as u8;
                    self.put(v);
                }
            }
            1 => {
                let mut l = [0u8; 288];
                l[..144].fill(8);
                l[144..256].fill(9);
                l[256..280].fill(7);
                l[280..].fill(8);
                let lit = Huff::new(&l)?;
                let dist = Huff::new(&[5u8; 30])?;
                self.codes(b, w, &lit, &dist)?;
            }
            2 => {
                let hlit = b.bits(5)? as usize + 257;
                let hdist = b.bits(5)? as usize + 1;
                let hclen = b.bits(4)? as usize + 4;
                let mut cl = [0u8; 19];
                for &k in CLORDER.iter().take(hclen) {
                    cl[k] = b.bits(3)? as u8;
                }
                let clh = Huff::new(&cl)?;
                let mut lens = vec![0u8; hlit + hdist];
                let mut i = 0;
                while i < hlit + hdist {
                    let sym = clh.decode(b)?;
                    match sym {
                        0..=15 => {
                            lens[i] = sym as u8;
                            i += 1;
                        }
                        16 => {
                            if i == 0 {
                                return Err(Error::new(Kind::Damaged, "bad code lengths"));
                            }
                            let prev = lens[i - 1];
                            let n = 3 + b.bits(2)? as usize;
                            if i + n > lens.len() {
                                return Err(Error::new(Kind::Damaged, "bad code lengths"));
                            }
                            lens[i..i + n].fill(prev);
                            i += n;
                        }
                        17 | 18 => {
                            let n = if sym == 17 { 3 + b.bits(3)? as usize } else { 11 + b.bits(7)? as usize };
                            if i + n > lens.len() {
                                return Err(Error::new(Kind::Damaged, "bad code lengths"));
                            }
                            i += n;
                        }
                        _ => return Err(Error::new(Kind::Damaged, "bad code lengths")),
                    }
                }
                if lens[256] == 0 {
                    return Err(Error::new(Kind::Damaged, "no end-of-block code"));
                }
                let lit = Huff::new(&lens[..hlit])?;
                let dist = Huff::new(&lens[hlit..])?;
                self.codes(b, w, &lit, &dist)?;
            }
            _ => return Err(Error::new(Kind::Damaged, "bad block type")),
        }
        self.flush(w)?;
        if last {
            self.done = true;
        }
        Ok(last)
    }

    fn codes<R: Read, W: Write>(&mut self, b: &mut BitIn<R>, w: &mut W, lit: &Huff, dist: &Huff) -> Res<()> {
        loop {
            let sym = lit.decode(b)?;
            if sym < 256 {
                self.put(sym as u8);
            } else if sym == 256 {
                return Ok(());
            } else {
                let s = (sym - 257) as usize;
                if s >= 29 {
                    return Err(Error::new(Kind::Damaged, "bad length code"));
                }
                let len = LBASE[s] as usize + b.bits(LEXT[s] as u32)? as usize;
                let ds = dist.decode(b)? as usize;
                if ds >= 30 {
                    return Err(Error::new(Kind::Damaged, "bad distance code"));
                }
                let d = DBASE[ds] as usize + b.bits(DEXT[ds] as u32)? as usize;
                if d as u64 > self.out + self.pending.len() as u64 {
                    return Err(Error::new(Kind::Damaged, "distance too far back"));
                }
                let mut from = (self.wpos + WSIZE - d) & (WSIZE - 1);
                for _ in 0..len {
                    let v = self.win[from];
                    from = (from + 1) & (WSIZE - 1);
                    self.put(v);
                }
            }
            if self.pending.len() >= 1 << 17 {
                self.flush(w)?;
            }
        }
    }
}

/// Inflates one whole raw deflate stream from `r` into `w`. Returns (bytes out, CRC-32).
pub fn inflate_all<R: Read, W: Write>(r: R, w: &mut W) -> Res<(u64, u32)> {
    let mut b = BitIn::new(r);
    let mut f = Inflater::new();
    while !f.block(&mut b, w)? {}
    Ok((f.out, f.crc.value()))
}
