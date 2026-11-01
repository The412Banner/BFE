//! LZMA and LZMA2 decoders, written for BFE. Used for the compressed tables of contents of 7z and
//! FreeArc archives, and for xz streams.

use crate::util::{Error, Kind, Res};

const PROB_INIT: u16 = 1024;
const MOVE_BITS: u32 = 5;
const TOP: u32 = 1 << 24;

struct Rc<'a> {
    src: &'a [u8],
    pos: usize,
    range: u32,
    code: u32,
}

impl<'a> Rc<'a> {
    fn new(src: &'a [u8], pos: usize) -> Res<Rc<'a>> {
        if pos + 5 > src.len() || src[pos] != 0 {
            return Err(dmg());
        }
        let mut code = 0u32;
        for k in 1..5 {
            code = (code << 8) | src[pos + k] as u32;
        }
        Ok(Rc { src, pos: pos + 5, range: 0xFFFF_FFFF, code })
    }
    #[inline]
    fn norm(&mut self) -> Res<()> {
        if self.range < TOP {
            let b = *self.src.get(self.pos).ok_or_else(dmg)?;
            self.pos += 1;
            self.range <<= 8;
            self.code = (self.code << 8) | b as u32;
        }
        Ok(())
    }
    #[inline]
    fn bit(&mut self, p: &mut u16) -> Res<u32> {
        let bound = (self.range >> 11) * (*p as u32);
        let b = if self.code < bound {
            self.range = bound;
            *p += (2048 - *p) >> MOVE_BITS;
            0
        } else {
            self.range -= bound;
            self.code -= bound;
            *p -= *p >> MOVE_BITS;
            1
        };
        self.norm()?;
        Ok(b)
    }
    fn direct(&mut self, n: u32) -> Res<u32> {
        let mut r = 0u32;
        for _ in 0..n {
            self.range >>= 1;
            let b = if self.code >= self.range {
                self.code -= self.range;
                1
            } else {
                0
            };
            r = (r << 1) | b;
            self.norm()?;
        }
        Ok(r)
    }
    fn tree(&mut self, probs: &mut [u16], bits: u32) -> Res<u32> {
        let mut m = 1usize;
        for _ in 0..bits {
            m = (m << 1) + self.bit(&mut probs[m])? as usize;
        }
        Ok(m as u32 - (1 << bits))
    }
    fn rtree(&mut self, probs: &mut [u16], bits: u32) -> Res<u32> {
        let mut m = 1usize;
        let mut r = 0u32;
        for i in 0..bits {
            let b = self.bit(&mut probs[m])?;
            m = (m << 1) + b as usize;
            r |= b << i;
        }
        Ok(r)
    }
}

fn dmg() -> Error {
    Error::new(Kind::Damaged, "LZMA data is damaged")
}

struct LenDec {
    choice: u16,
    choice2: u16,
    low: Vec<[u16; 8]>,
    mid: Vec<[u16; 8]>,
    high: [u16; 256],
}

impl LenDec {
    fn new() -> LenDec {
        LenDec { choice: PROB_INIT, choice2: PROB_INIT, low: vec![[PROB_INIT; 8]; 16], mid: vec![[PROB_INIT; 8]; 16], high: [PROB_INIT; 256] }
    }
    fn decode(&mut self, rc: &mut Rc, ps: usize) -> Res<u32> {
        if rc.bit(&mut self.choice)? == 0 {
            return rc.tree(&mut self.low[ps], 3);
        }
        if rc.bit(&mut self.choice2)? == 0 {
            return Ok(8 + rc.tree(&mut self.mid[ps], 3)?);
        }
        Ok(16 + rc.tree(&mut self.high, 8)?)
    }
}

/// The adaptive state of an LZMA decoder (kept across LZMA2 chunks unless they reset it).
pub struct Lzma {
    lc: u32,
    lp: u32,
    pb: u32,
    lit: Vec<u16>,
    is_match: [u16; 192],
    is_rep: [u16; 12],
    is_rep_g0: [u16; 12],
    is_rep_g1: [u16; 12],
    is_rep_g2: [u16; 12],
    is_rep0_long: [u16; 192],
    pos_slot: Vec<[u16; 64]>,
    spec_pos: [u16; 115],
    align: [u16; 16],
    len: LenDec,
    rep_len: LenDec,
    state: usize,
    reps: [u32; 4],
}

impl Lzma {
    pub fn new(props: u8) -> Res<Lzma> {
        let mut p = props as u32;
        if p >= 9 * 5 * 5 {
            return Err(Error::new(Kind::Damaged, "bad LZMA properties"));
        }
        let lc = p % 9;
        p /= 9;
        let lp = p % 5;
        let pb = p / 5;
        let mut z = Lzma {
            lc,
            lp,
            pb,
            lit: Vec::new(),
            is_match: [PROB_INIT; 192],
            is_rep: [PROB_INIT; 12],
            is_rep_g0: [PROB_INIT; 12],
            is_rep_g1: [PROB_INIT; 12],
            is_rep_g2: [PROB_INIT; 12],
            is_rep0_long: [PROB_INIT; 192],
            pos_slot: vec![[PROB_INIT; 64]; 4],
            spec_pos: [PROB_INIT; 115],
            align: [PROB_INIT; 16],
            len: LenDec::new(),
            rep_len: LenDec::new(),
            state: 0,
            reps: [0; 4],
        };
        z.reset();
        Ok(z)
    }

    fn reset(&mut self) {
        self.lit = vec![PROB_INIT; 0x300 << (self.lc + self.lp)];
        self.is_match = [PROB_INIT; 192];
        self.is_rep = [PROB_INIT; 12];
        self.is_rep_g0 = [PROB_INIT; 12];
        self.is_rep_g1 = [PROB_INIT; 12];
        self.is_rep_g2 = [PROB_INIT; 12];
        self.is_rep0_long = [PROB_INIT; 192];
        self.pos_slot = vec![[PROB_INIT; 64]; 4];
        self.spec_pos = [PROB_INIT; 115];
        self.align = [PROB_INIT; 16];
        self.len = LenDec::new();
        self.rep_len = LenDec::new();
        self.state = 0;
        self.reps = [0; 4];
    }

    /// Decodes from `src[pos..]` appending to `out` until `out` has grown by `want` bytes (or an
    /// end marker, when `want` is None). `out` doubles as the dictionary. Returns the input end.
    fn run(&mut self, src: &[u8], pos: usize, out: &mut Vec<u8>, want: Option<usize>, dict_start: usize) -> Res<usize> {
        let mut rc = Rc::new(src, pos)?;
        let target = want.map(|w| out.len() + w);
        let pb_mask = (1usize << self.pb) - 1;
        let lp_mask = (1usize << self.lp) - 1;
        loop {
            if let Some(t) = target {
                if out.len() >= t {
                    break;
                }
            }
            let opos = out.len();
            // Positions count from the last dictionary reset (LZMA2), i.e. from the stream start.
            let rel = opos - dict_start;
            let ps = rel & pb_mask;
            if rc.bit(&mut self.is_match[(self.state << 4) + ps])? == 0 {
                let prev = if opos > dict_start { out[opos - 1] as usize } else { 0 };
                let base = 0x300 * (((rel & lp_mask) << self.lc) + (prev >> (8 - self.lc)));
                let probs = &mut self.lit[base..base + 0x300];
                let mut sym = 1usize;
                if self.state >= 7 {
                    let back = self.reps[0] as usize + 1;
                    if back > opos - dict_start {
                        return Err(dmg());
                    }
                    let mut mb = out[opos - back] as usize;
                    while sym < 0x100 {
                        let mbit = (mb >> 7) & 1;
                        mb <<= 1;
                        let b = rc.bit(&mut probs[((1 + mbit) << 8) + sym])? as usize;
                        sym = (sym << 1) | b;
                        if mbit != b {
                            break;
                        }
                    }
                }
                while sym < 0x100 {
                    sym = (sym << 1) | rc.bit(&mut probs[sym])? as usize;
                }
                out.push(sym as u8);
                self.state = if self.state < 4 { 0 } else if self.state < 10 { self.state - 3 } else { self.state - 6 };
                continue;
            }
            let len;
            if rc.bit(&mut self.is_rep[self.state])? == 1 {
                if opos == dict_start {
                    return Err(dmg());
                }
                if rc.bit(&mut self.is_rep_g0[self.state])? == 0 {
                    if rc.bit(&mut self.is_rep0_long[(self.state << 4) + ps])? == 0 {
                        self.state = if self.state < 7 { 9 } else { 11 };
                        let back = self.reps[0] as usize + 1;
                        if back > opos - dict_start {
                            return Err(dmg());
                        }
                        let b = out[opos - back];
                        out.push(b);
                        continue;
                    }
                } else {
                    let d;
                    if rc.bit(&mut self.is_rep_g1[self.state])? == 0 {
                        d = self.reps[1];
                    } else {
                        if rc.bit(&mut self.is_rep_g2[self.state])? == 0 {
                            d = self.reps[2];
                        } else {
                            d = self.reps[3];
                            self.reps[3] = self.reps[2];
                        }
                        self.reps[2] = self.reps[1];
                    }
                    self.reps[1] = self.reps[0];
                    self.reps[0] = d;
                }
                len = self.rep_len.decode(&mut rc, ps)?;
                self.state = if self.state < 7 { 8 } else { 11 };
            } else {
                self.reps[3] = self.reps[2];
                self.reps[2] = self.reps[1];
                self.reps[1] = self.reps[0];
                len = self.len.decode(&mut rc, ps)?;
                self.state = if self.state < 7 { 7 } else { 10 };
                let lps = (len.min(3)) as usize;
                let slot = rc.tree(&mut self.pos_slot[lps], 6)?;
                let dist = if slot < 4 {
                    slot
                } else {
                    let nd = (slot >> 1) - 1;
                    let mut d = (2 | (slot & 1)) << nd;
                    if slot < 14 {
                        let base = (d - slot) as usize;
                        // Reverse bit tree over spec_pos[base..]; index from 1 like the others.
                        let mut m = 1usize;
                        let mut r = 0u32;
                        for i in 0..nd {
                            let b = rc.bit(&mut self.spec_pos[base + m])?;
                            m = (m << 1) + b as usize;
                            r |= b << i;
                        }
                        d += r;
                    } else {
                        d += rc.direct(nd - 4)? << 4;
                        d += rc.rtree(&mut self.align, 4)?;
                    }
                    d
                };
                if dist == 0xFFFF_FFFF {
                    // End marker.
                    break;
                }
                self.reps[0] = dist;
            }
            let n = len as usize + 2;
            let back = self.reps[0] as usize + 1;
            if back > opos - dict_start {
                return Err(dmg());
            }
            if let Some(t) = target {
                if opos + n > t {
                    return Err(dmg());
                }
            }
            let from = opos - back;
            for k in 0..n {
                let b = out[from + k];
                out.push(b);
            }
        }
        Ok(rc.pos)
    }
}

/// Raw LZMA (properties byte + dictionary size given separately, as in 7z and FreeArc headers).
pub fn lzma_decode(src: &[u8], props: u8, out_size: usize) -> Res<Vec<u8>> {
    let mut z = Lzma::new(props)?;
    let mut out = Vec::with_capacity(out_size);
    z.run(src, 0, &mut out, Some(out_size), 0)?;
    Ok(out)
}

/// LZMA2 (7z method 21, xz). `out_limit` guards against runaway sizes.
pub fn lzma2_decode(src: &[u8], out_limit: usize) -> Res<Vec<u8>> {
    let mut out = Vec::new();
    let mut p = 0usize;
    let mut z: Option<Lzma> = None;
    let mut dict_start = 0usize;
    loop {
        let c = *src.get(p).ok_or_else(dmg)?;
        p += 1;
        if c == 0 {
            break;
        }
        if c == 1 || c == 2 {
            if p + 2 > src.len() {
                return Err(dmg());
            }
            let n = ((src[p] as usize) << 8 | src[p + 1] as usize) + 1;
            p += 2;
            if c == 1 {
                dict_start = out.len();
            }
            out.extend_from_slice(src.get(p..p + n).ok_or_else(dmg)?);
            p += n;
        } else if c >= 0x80 {
            if p + 4 > src.len() {
                return Err(dmg());
            }
            let usize_ = (((c & 0x1F) as usize) << 16 | (src[p] as usize) << 8 | src[p + 1] as usize) + 1;
            let psize = ((src[p + 2] as usize) << 8 | src[p + 3] as usize) + 1;
            p += 4;
            let mode = (c >> 5) & 3;
            if mode == 3 {
                dict_start = out.len();
            }
            if mode >= 2 {
                let props = *src.get(p).ok_or_else(dmg)?;
                p += 1;
                z = Some(Lzma::new(props)?);
            } else if mode == 1 {
                z.as_mut().ok_or_else(dmg)?.reset();
            }
            let dec = z.as_mut().ok_or_else(dmg)?;
            let chunk = src.get(p..p + psize).ok_or_else(dmg)?;
            dec.run(chunk, 0, &mut out, Some(usize_), dict_start)?;
            p += psize;
        } else {
            return Err(dmg());
        }
        if out.len() > out_limit {
            return Err(dmg());
        }
    }
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;
    const PLAIN: &[u8] = include_bytes!("../testdata/plain.bin");

    fn props(lc: u8, lp: u8, pb: u8) -> u8 {
        (pb * 5 + lp) * 9 + lc
    }

    #[test]
    fn lzma1_variants() {
        for (f, lc, lp, pb) in [("l1_332", 3, 0, 2), ("l1_020", 0, 2, 0), ("l1_131", 1, 3, 1)] {
            let src = std::fs::read(format!("{}/testdata/{f}.lzma", env!("CARGO_MANIFEST_DIR"))).unwrap();
            let out = lzma_decode(&src, props(lc, lp, pb), PLAIN.len()).unwrap();
            assert!(out == PLAIN, "{f} differs");
        }
    }

    #[test]
    fn lzma2() {
        let src = std::fs::read(format!("{}/testdata/l2.lzma2", env!("CARGO_MANIFEST_DIR"))).unwrap();
        let out = lzma2_decode(&src, 1 << 30).unwrap();
        assert!(out == PLAIN);
    }
}
