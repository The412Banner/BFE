//! The format-independent part: plan the space, then unpack pieces from the end of the archive,
//! move each into place and only then shrink the archive. A journal in `<dest>/.bfe-inplace/`
//! makes every step resumable after a crash or a stop.
//!
//! Order of one step (piece `i`, the last one still in the archive):
//!   1. unpack it into the stage folder and verify it (CRC where the format has one),
//!   2. fsync every file, rename it into place,
//!   3. record `done` in the journal (atomic rename),
//!   4. when free space runs short: rewrite the archive so it holds pieces[..=i] only (a normal
//!      archive again) and shrink it. The journal marks the rewrite as in progress until it is
//!      complete, so a crash in the middle is repaired from the saved description on resume.
//! A crash at any point leaves either the finished file or its bytes still in the archive.

use crate::store::Store;
use crate::util::{atomic_write, emit, free_bytes, human, jstr, sync_dir, check_stop, Error, Kind, Res};
use std::collections::HashMap;
use std::fs::{self, File, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};

pub struct Piece {
    /// First byte in the archive that belongs to this piece.
    pub start: u64,
    /// First byte after it: the next piece's start, or where the archive's index begins.
    pub end: u64,
    /// Bytes it unpacks to.
    pub out: u64,
    /// Shown in progress (usually its first file).
    pub label: String,
}

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum OutKind {
    File,
    Dir,
}

pub struct Output {
    pub rel: PathBuf,
    pub kind: OutKind,
}

pub struct Ctx {
    pub dest: PathBuf,
    pub stage: PathBuf,
    pub state_dir: PathBuf,
    pub tool7z: Option<PathBuf>,
    pub unarc: Option<PathBuf>,
    pub outputs: Vec<Output>,
    /// Links are made only after every regular file is out (nothing is written through a link).
    pub links: Vec<(char, String, PathBuf)>,
}

impl Ctx {
    /// Where an output with archive path `rel` is written while its piece is unpacked.
    pub fn stage_path(&self, rel: &Path) -> PathBuf {
        self.stage.join(rel)
    }
    pub fn add_file(&mut self, rel: PathBuf) {
        self.outputs.push(Output { rel, kind: OutKind::File });
    }
    pub fn add_dir(&mut self, rel: PathBuf) {
        self.outputs.push(Output { rel, kind: OutKind::Dir });
    }
    pub fn add_link(&mut self, kind: char, target: String, rel: PathBuf) {
        self.links.push((kind, target, rel));
    }
}

pub trait Format {
    fn kind(&self) -> &'static str;
    fn pieces(&self) -> &[Piece];
    /// Unpacks piece `i` into `ctx.stage` and registers each output; verifies what it can.
    fn extract(&mut self, store: &mut Store, i: usize, ctx: &mut Ctx) -> Res<()>;
    /// Makes the archive hold exactly pieces[..keep] as a normal archive and shrinks it.
    fn rewrite(&self, store: &mut Store, keep: usize) -> Res<()>;
    /// Reads everything once and checks it, before anything is deleted.
    fn verify(&mut self, store: &mut Store, ctx: &mut Ctx) -> Res<()>;
    /// Enough to rebuild this description later (saved once in the journal).
    fn snapshot(&self) -> Vec<u8>;
    /// Work for the very end (folders and empty files listed outside pieces).
    fn finish(&mut self, _ctx: &mut Ctx) -> Res<()> {
        Ok(())
    }
    /// True when extracting piece `i` writes a temporary index after it (the archive is then cut
    /// right after the piece, which writes the real index back).
    fn needs_view(&self, _i: usize) -> bool {
        false
    }
}

// ── Journal ──

pub struct State {
    pub format: String,
    pub start_len: u64,
    pub total: usize,
    /// Pieces unpacked, counted from the end.
    pub done: usize,
    /// Pieces the archive on disk holds after the last completed rewrite.
    pub keep: usize,
    /// A rewrite (or a temporary index) was being written: repair it before going on.
    pub dirty: bool,
    pub verified: bool,
}

impl State {
    fn path(dir: &Path) -> PathBuf {
        dir.join("state")
    }
    pub fn load(dir: &Path) -> Option<State> {
        let text = fs::read_to_string(Self::path(dir)).ok()?;
        let mut m = HashMap::new();
        for line in text.lines() {
            if let Some((k, v)) = line.split_once(' ') {
                m.insert(k.to_string(), v.to_string());
            }
        }
        if m.get("bfecut-state").map(String::as_str) != Some("1") {
            return None;
        }
        Some(State {
            format: m.get("format")?.clone(),
            start_len: m.get("start_len")?.parse().ok()?,
            total: m.get("total")?.parse().ok()?,
            done: m.get("done")?.parse().ok()?,
            keep: m.get("keep")?.parse().ok()?,
            dirty: m.get("dirty").map(|v| v == "1").unwrap_or(false),
            verified: m.get("verified").map(|v| v == "1").unwrap_or(false),
        })
    }
    pub fn save(&self, dir: &Path) -> Res<()> {
        let text = format!(
            "bfecut-state 1\nformat {}\nstart_len {}\ntotal {}\ndone {}\nkeep {}\ndirty {}\nverified {}\n",
            self.format,
            self.start_len,
            self.total,
            self.done,
            self.keep,
            self.dirty as u8,
            self.verified as u8
        );
        atomic_write(&Self::path(dir), text.as_bytes())
    }
}

pub fn state_dir(dest: &Path) -> PathBuf {
    dest.join(".bfe-inplace")
}

// ── Space plan ──

pub struct Plan {
    pub total_out: u64,
    pub free: u64,
    pub need_normal: u64,
    /// Extra bytes still missing for the space-saving unpack (0 = it fits).
    pub short: u64,
    pub largest: u64,
}

/// Replays the unpack from the end: cut only when the next piece wouldn't fit.
pub fn simulate(pieces: &[Piece], archive_len: u64, free: u64, keep_free: u64, done: usize) -> Plan {
    let todo = pieces.len() - done;
    let total_out: u64 = pieces[..todo].iter().map(|p| p.out).sum();
    let mut avail = free.saturating_sub(keep_free) as i128;
    // Bytes after the last piece still to unpack (index, already unpacked pieces) go at the first cut.
    let mut pending: i128 = if todo > 0 { archive_len.saturating_sub(pieces[todo - 1].end) as i128 } else { 0 };
    let mut short: i128 = 0;
    let slack: i128 = 1 << 20;
    for p in pieces[..todo].iter().rev() {
        let need = p.out as i128 + slack;
        if avail < need {
            avail += pending;
            pending = 0;
        }
        if avail < need {
            short = short.max(need - avail);
        }
        avail -= p.out as i128;
        pending += (p.end - p.start) as i128;
    }
    Plan {
        total_out,
        free,
        need_normal: total_out + keep_free,
        short: short.max(0) as u64,
        largest: pieces[..todo].iter().map(|p| p.out.max(p.end - p.start)).max().unwrap_or(0),
    }
}

// ── Running ──

pub struct RunOpts {
    pub keep_free: u64,
    pub verify_first: bool,
    /// Shrink after every piece instead of only when space runs short (tests, or by choice).
    pub eager: bool,
}

fn commit(ctx: &mut Ctx) -> Res<()> {
    let outputs = std::mem::take(&mut ctx.outputs);
    for o in &outputs {
        let dst = ctx.dest.join(&o.rel);
        match o.kind {
            OutKind::Dir => {
                fs::create_dir_all(&dst)?;
            }
            OutKind::File => {
                let src = ctx.stage.join(&o.rel);
                if let Some(p) = dst.parent() {
                    fs::create_dir_all(p)?;
                }
                File::open(&src)?.sync_all()?;
                if let Ok(m) = fs::symlink_metadata(&dst) {
                    if m.is_dir() {
                        return Err(Error::new(Kind::Unsafe, format!("a folder is in the way of {}", o.rel.display())));
                    }
                }
                fs::rename(&src, &dst)?;
                if let Some(p) = dst.parent() {
                    sync_dir(p);
                }
            }
        }
    }
    if !ctx.links.is_empty() {
        let mut f = OpenOptions::new().create(true).append(true).open(ctx.state_dir.join("links"))?;
        for (k, target, rel) in ctx.links.drain(..) {
            writeln!(f, "{k}\t{}\t{}", target.replace(['\t', '\n'], " "), rel.display())?;
        }
        f.sync_all()?;
    }
    clear_stage(&ctx.stage)?;
    Ok(())
}

fn clear_stage(stage: &Path) -> Res<()> {
    if stage.exists() {
        fs::remove_dir_all(stage)?;
    }
    fs::create_dir_all(stage)?;
    Ok(())
}

fn make_links(ctx: &Ctx) -> Res<usize> {
    let p = ctx.state_dir.join("links");
    let Ok(text) = fs::read_to_string(&p) else { return Ok(0) };
    let mut skipped = 0;
    for line in text.lines() {
        let mut it = line.splitn(3, '\t');
        let (Some(k), Some(target), Some(rel)) = (it.next(), it.next(), it.next()) else { continue };
        let dst = ctx.dest.join(rel);
        if let Some(parent) = dst.parent() {
            let _ = fs::create_dir_all(parent);
        }
        let _ = fs::remove_file(&dst);
        let ok = match k {
            "S" => std::os::unix::fs::symlink(target, &dst).is_ok(),
            // Hard link: the target is another member, already unpacked. Shared storage has no hard
            // links, so fall back to a copy.
            _ => {
                let src = match crate::util::safe_rel(target) {
                    Some(r) => ctx.dest.join(r),
                    None => {
                        skipped += 1;
                        continue;
                    }
                };
                fs::hard_link(&src, &dst).is_ok() || fs::copy(&src, &dst).is_ok()
            }
        };
        if !ok {
            skipped += 1;
        }
    }
    Ok(skipped)
}

fn progress(st: &State, f: &dyn Format, out_done: u64, total_out: u64, start_len: u64, store: &Store, label: &str) {
    let freed = start_len.saturating_sub(store.len());
    emit(&format!(
        "{{\"ev\":\"progress\",\"done\":{},\"total\":{},\"out\":{},\"total_out\":{},\"freed\":{},\"archive\":{},\"file\":{}}}",
        st.done,
        st.total,
        out_done,
        total_out,
        freed,
        store.len(),
        jstr(label)
    ));
    let _ = f;
}

pub fn run(f: &mut dyn Format, store: &mut Store, ctx: &mut Ctx, st: &mut State, o: &RunOpts) -> Res<()> {
    let total = f.pieces().len();
    if st.dirty {
        // A rewrite or a temporary index was interrupted. Rebuild the index for the pieces not yet
        // unpacked only: bytes of finished pieces may already be gone.
        let keep = total - st.done;
        f.rewrite(store, keep)?;
        st.keep = keep;
        st.dirty = false;
        st.save(&ctx.state_dir)?;
    }
    clear_stage(&ctx.stage)?;
    if o.verify_first && !st.verified {
        emit("{\"ev\":\"verify\"}");
        f.verify(store, ctx)?;
        clear_stage(&ctx.stage)?;
        st.verified = true;
        st.save(&ctx.state_dir)?;
    }
    let total_out: u64 = f.pieces().iter().map(|p| p.out).sum();
    let mut out_done: u64 = f.pieces()[total - st.done..].iter().map(|p| p.out).sum();
    while st.done < total {
        check_stop()?;
        let i = total - st.done - 1;
        let need = f.pieces()[i].out + (1 << 20);
        let mut free = free_bytes(&ctx.dest)?.saturating_sub(o.keep_free);
        if free < need && st.keep > i + 1 {
            cut(f, store, ctx, st, i + 1)?;
            free = free_bytes(&ctx.dest)?.saturating_sub(o.keep_free);
        }
        if free < need {
            fail!(
                NoSpace,
                "{} more needed to unpack {} (keeping {} free); progress saved",
                human(need - free),
                f.pieces()[i].label,
                human(o.keep_free)
            );
        }
        let view = f.needs_view(i);
        if view {
            st.dirty = true;
            st.save(&ctx.state_dir)?;
        }
        f.extract(store, i, ctx)?;
        commit(ctx)?;
        st.done += 1;
        if view || o.eager {
            // The piece is out: cut it off (this also replaces a temporary index with the real one).
            cut(f, store, ctx, st, i)?;
        } else {
            st.save(&ctx.state_dir)?;
        }
        out_done += f.pieces()[i].out;
        let label = f.pieces()[i].label.clone();
        progress(st, f, out_done, total_out, st.start_len, store, &label);
    }
    f.finish(ctx)?;
    commit(ctx)?;
    let skipped = make_links(ctx)?;
    store.delete_all()?;
    let _ = fs::remove_dir_all(&ctx.state_dir);
    emit(&format!(
        "{{\"ev\":\"done\",\"total_out\":{},\"freed\":{},\"links_skipped\":{}}}",
        total_out, st.start_len, skipped
    ));
    Ok(())
}

/// Rewrites the archive to hold pieces[..keep] (keep never covers an unpacked piece) and shrinks it.
fn cut(f: &mut dyn Format, store: &mut Store, ctx: &Ctx, st: &mut State, keep: usize) -> Res<()> {
    debug_assert!(keep <= f.pieces().len() - st.done);
    st.dirty = true;
    st.save(&ctx.state_dir)?;
    f.rewrite(store, keep)?;
    st.keep = keep;
    st.dirty = false;
    st.save(&ctx.state_dir)?;
    Ok(())
}

/// Gives up on an unfinished run: the archive gets a correct index for what's left (pieces not yet
/// unpacked) and the journal goes. Files already unpacked stay.
pub fn abandon(f: &mut dyn Format, store: &mut Store, ctx: &Ctx, st: &State) -> Res<()> {
    let keep = st.total - st.done;
    if keep < st.total || st.dirty {
        f.rewrite(store, keep)?;
    }
    let _ = fs::remove_dir_all(&ctx.state_dir);
    emit(&format!("{{\"ev\":\"abandoned\",\"archive\":{},\"pieces_left\":{}}}", store.len(), keep));
    Ok(())
}
