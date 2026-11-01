//! The archive bytes: one file, or a split set (`name.001`, `name.002`, …) seen as one long file.
//! Shrinking the logical length deletes whole volumes from the end and shortens the last one kept.

use crate::util::{sync_dir, Error, Kind, Res};
use std::fs::{self, File, OpenOptions};
use std::io::Read;
use std::os::unix::fs::FileExt;
use std::path::{Path, PathBuf};

pub struct Volume {
    pub path: PathBuf,
    file: File,
    pub len: u64,
}

pub struct Store {
    pub vols: Vec<Volume>,
}

impl Store {
    pub fn open(paths: &[PathBuf]) -> Res<Store> {
        let mut vols = Vec::new();
        for p in paths {
            let file = OpenOptions::new().read(true).write(true).open(p)?;
            let len = file.metadata()?.len();
            vols.push(Volume { path: p.clone(), file, len });
        }
        if vols.is_empty() {
            return Err(Error::new(Kind::Usage, "no archive files"));
        }
        Ok(Store { vols })
    }

    /// The archive itself plus, for `name.001`, every following numbered volume that exists.
    pub fn volume_paths(first: &Path) -> Vec<PathBuf> {
        let name = first.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_default();
        if let Some(dot) = name.rfind('.') {
            let ext = &name[dot + 1..];
            if ext.len() >= 3 && ext.chars().all(|c| c.is_ascii_digit()) && ext.trim_start_matches('0') == "1" {
                let width = ext.len();
                let stem = &name[..dot];
                let dir = first.parent().map(Path::to_path_buf).unwrap_or_default();
                let mut out = Vec::new();
                let mut n = 1u64;
                loop {
                    let p = dir.join(format!("{stem}.{:0width$}", n, width = width));
                    if !p.is_file() {
                        break;
                    }
                    out.push(p);
                    n += 1;
                }
                if !out.is_empty() {
                    return out;
                }
            }
        }
        vec![first.to_path_buf()]
    }

    pub fn len(&self) -> u64 {
        self.vols.iter().map(|v| v.len).sum()
    }

    pub fn is_split(&self) -> bool {
        self.vols.len() > 1
    }

    pub fn read_at(&self, mut off: u64, buf: &mut [u8]) -> Res<()> {
        let mut done = 0;
        for v in &self.vols {
            if done == buf.len() {
                break;
            }
            if off >= v.len {
                off -= v.len;
                continue;
            }
            let n = ((v.len - off) as usize).min(buf.len() - done);
            v.file.read_exact_at(&mut buf[done..done + n], off)?;
            done += n;
            off = 0;
        }
        if done != buf.len() {
            return Err(Error::new(Kind::Damaged, "archive ends early"));
        }
        Ok(())
    }

    pub fn read_vec(&self, off: u64, len: usize) -> Res<Vec<u8>> {
        let mut v = vec![0u8; len];
        self.read_at(off, &mut v)?;
        Ok(v)
    }

    /// Writes at a logical offset; bytes past the end extend the last volume.
    pub fn write_at(&mut self, mut off: u64, data: &[u8]) -> Res<()> {
        let mut done = 0;
        let last = self.vols.len() - 1;
        for (i, v) in self.vols.iter_mut().enumerate() {
            if done == data.len() {
                break;
            }
            if off >= v.len && i != last {
                off -= v.len;
                continue;
            }
            let room = if i == last { (data.len() - done) as u64 } else { v.len - off };
            let n = (room as usize).min(data.len() - done);
            v.file.write_all_at(&data[done..done + n], off)?;
            v.len = v.len.max(off + n as u64);
            done += n;
            off = 0;
        }
        Ok(())
    }

    /// Makes the logical archive `len` bytes long: volumes past it are deleted, the one holding
    /// the end is shortened. Every change is flushed before returning.
    pub fn set_len(&mut self, len: u64) -> Res<()> {
        let mut acc = 0u64;
        let mut keep = self.vols.len();
        for (i, v) in self.vols.iter().enumerate() {
            if acc + v.len >= len {
                keep = i + 1;
                break;
            }
            acc += v.len;
        }
        // Shorten the volume holding the new end first, then drop whole volumes after it.
        let i = keep - 1;
        let new_len = len - acc;
        if self.vols[i].len != new_len {
            self.vols[i].file.set_len(new_len)?;
            self.vols[i].file.sync_all()?;
            self.vols[i].len = new_len;
        }
        while self.vols.len() > keep {
            let v = self.vols.pop().unwrap();
            drop(v.file);
            fs::remove_file(&v.path)?;
            if let Some(d) = v.path.parent() {
                sync_dir(d);
            }
        }
        Ok(())
    }

    pub fn sync(&self) -> Res<()> {
        for v in &self.vols {
            v.file.sync_all()?;
        }
        Ok(())
    }

    /// Deletes every volume (the whole archive has been unpacked).
    pub fn delete_all(&mut self) -> Res<()> {
        while let Some(v) = self.vols.pop() {
            drop(v.file);
            fs::remove_file(&v.path)?;
            if let Some(d) = v.path.parent() {
                sync_dir(d);
            }
        }
        Ok(())
    }

    /// The single volume a logical offset falls in, with the offset inside it.
    pub fn locate(&self, mut off: u64) -> Option<(usize, u64)> {
        for (i, v) in self.vols.iter().enumerate() {
            if off < v.len {
                return Some((i, off));
            }
            off -= v.len;
        }
        None
    }

    pub fn volume_file(&self, i: usize) -> &File {
        &self.vols[i].file
    }

    pub fn reader(&self, off: u64, len: u64) -> StoreReader<'_> {
        StoreReader { store: self, pos: off, end: off + len, buf: Vec::new(), bpos: 0 }
    }
}

/// Sequential reads of a byte range, buffered.
pub struct StoreReader<'a> {
    store: &'a Store,
    pub pos: u64,
    end: u64,
    buf: Vec<u8>,
    bpos: usize,
}

impl<'a> Read for StoreReader<'a> {
    fn read(&mut self, out: &mut [u8]) -> std::io::Result<usize> {
        if self.bpos >= self.buf.len() {
            let left = self.end - self.pos;
            if left == 0 {
                return Ok(0);
            }
            let n = left.min(1 << 20) as usize;
            self.buf.resize(n, 0);
            self.store
                .read_at(self.pos, &mut self.buf)
                .map_err(|e| std::io::Error::new(std::io::ErrorKind::UnexpectedEof, e.msg))?;
            self.pos += n as u64;
            self.bpos = 0;
        }
        let n = (self.buf.len() - self.bpos).min(out.len());
        out[..n].copy_from_slice(&self.buf[self.bpos..self.bpos + n]);
        self.bpos += n;
        Ok(n)
    }
}
