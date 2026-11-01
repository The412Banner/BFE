//! bfecut — BFE's space-saving unpacker.
//!
//!   bfecut plan    <archive> --dest <dir> [--keep-free BYTES]
//!   bfecut run     <archive> --dest <dir> [--keep-free BYTES] [--no-verify] [--eager] [--7z PATH] [--unarc PATH]
//!                  [--unarc-arg ARG]…
//!   bfecut abandon <archive> --dest <dir>
//!
//! Output: one JSON object per line on stdout. Exit codes: 0 done, 1 I/O, 2 usage, 3 not enough
//! space (progress saved), 4 damaged, 5 password, 6 unsupported, 7 unsafe archive, 130 paused.

#[macro_use]
mod util;
mod engine;
mod freearc;
mod inflate;
mod lzma;
mod rar;
mod ser;
mod sevenz;
mod store;
mod tar;
mod zip;

use engine::{Ctx, Format, State};
use std::fs;
use std::path::{Path, PathBuf};
use store::Store;
use util::{emit, jstr, Error, Kind, Res};

struct Args {
    cmd: String,
    archive: PathBuf,
    dest: PathBuf,
    keep_free: u64,
    verify: bool,
    eager: bool,
    tool7z: Option<PathBuf>,
    unarc: Option<PathBuf>,
    unarc_args: Vec<String>,
}

fn parse_args() -> Res<Args> {
    let mut it = std::env::args().skip(1);
    let cmd = it.next().ok_or_else(|| Error::new(Kind::Usage, "usage: bfecut plan|run|abandon <archive> --dest <dir>"))?;
    let mut a = Args {
        cmd,
        archive: PathBuf::new(),
        dest: PathBuf::new(),
        keep_free: 2_000_000_000,
        verify: true,
        eager: false,
        tool7z: None,
        unarc: None,
        unarc_args: Vec::new(),
    };
    while let Some(x) = it.next() {
        match x.as_str() {
            "--dest" => a.dest = it.next().map(PathBuf::from).unwrap_or_default(),
            "--keep-free" => a.keep_free = it.next().and_then(|v| v.parse().ok()).ok_or_else(|| Error::new(Kind::Usage, "bad --keep-free"))?,
            "--no-verify" => a.verify = false,
            "--eager" => a.eager = true,
            "--7z" => a.tool7z = it.next().map(PathBuf::from),
            "--unarc" => a.unarc = it.next().map(PathBuf::from),
            // Extra arguments for unarc (e.g. -cfg<arc.ini> -w<temp> for archives with external layers).
            "--unarc-arg" => a.unarc_args.extend(it.next()),
            s if a.archive.as_os_str().is_empty() && !s.starts_with("--") => a.archive = PathBuf::from(s),
            s => return Err(Error::new(Kind::Usage, format!("unknown argument {s}"))),
        }
    }
    if a.archive.as_os_str().is_empty() || a.dest.as_os_str().is_empty() {
        return Err(Error::new(Kind::Usage, "archive and --dest are required"));
    }
    Ok(a)
}

/// Opens the archive's description: from the journal when a run is unfinished, else from the archive.
fn open_format(store: &Store, saved: Option<(&str, &[u8])>, a: &Args) -> Res<Box<dyn Format>> {
    if let Some((kind, snap)) = saved {
        return Ok(match kind {
            "zip" => Box::new(zip::Zip::from_snapshot(snap)?),
            "tar" => Box::new(tar::Tar::from_snapshot(snap)?),
            "7z" => Box::new(sevenz::SevenZ::from_snapshot(snap)?),
            "freearc" => Box::new(freearc::FreeArc::from_snapshot(snap, a.unarc.clone(), a.unarc_args.clone())?),
            "rar" => Box::new(rar::Rar::from_snapshot(snap)?),
            k => return Err(Error::new(Kind::Io, format!("journal names an unknown format {k}"))),
        });
    }
    let head = store.read_vec(0, store.len().min(4096) as usize)?;
    if sevenz::SevenZ::looks_like(&head) {
        return Ok(Box::new(sevenz::SevenZ::open(store)?));
    }
    if rar::Rar::looks_like(&head) {
        return Ok(Box::new(rar::Rar::open(store, a.tool7z.as_deref())?));
    }
    if head.starts_with(b"PK\x03\x04") {
        return Ok(Box::new(zip::Zip::open(store)?));
    }
    if tar::Tar::looks_like(&head) {
        return Ok(Box::new(tar::Tar::open(store)?));
    }
    if zip::Zip::looks_like(&head, store) {
        return Ok(Box::new(zip::Zip::open(store)?));
    }
    if !store.is_split() && freearc::FreeArc::looks_like(store) {
        return Ok(Box::new(freearc::FreeArc::open(store, a.unarc.clone(), a.unarc_args.clone())?));
    }
    Err(Error::new(Kind::Unsupported, "this archive type can't be unpacked while deleting it"))
}

/// The archive's files: a RAR set by its own naming, else `name.001`-style splits.
fn volumes_for(path: &Path) -> Vec<PathBuf> {
    let mut head = [0u8; 8];
    let n = fs::File::open(path).and_then(|mut f| std::io::Read::read(&mut f, &mut head)).unwrap_or(0);
    if rar::Rar::looks_like(&head[..n]) {
        return rar::rar_volumes(path);
    }
    Store::volume_paths(path)
}

fn existing_ancestor(p: &Path) -> PathBuf {
    let mut cur = p.to_path_buf();
    loop {
        if cur.exists() {
            return cur;
        }
        match cur.parent() {
            Some(x) if !x.as_os_str().is_empty() => cur = x.to_path_buf(),
            _ => return PathBuf::from("."),
        }
    }
}

fn load_saved(dest: &Path) -> Option<(State, Vec<u8>)> {
    let dir = engine::state_dir(dest);
    let st = State::load(&dir)?;
    let snap = fs::read(dir.join("index.bin")).ok()?;
    Some((st, snap))
}

fn cmd_plan(a: &Args) -> Res<()> {
    let paths = volumes_for(&a.archive);
    let store = Store::open(&paths)?;
    let saved = load_saved(&a.dest);
    let f = match open_format(&store, saved.as_ref().map(|(s, b)| (s.format.as_str(), b.as_slice())), a) {
        Ok(f) => f,
        Err(e) => {
            emit(&format!("{{\"ev\":\"plan\",\"supported\":false,\"code\":{},\"reason\":{}}}", jstr(e.kind.code()), jstr(&e.msg)));
            return Ok(());
        }
    };
    let anc = existing_ancestor(&a.dest);
    let same = util::device_of(&anc)? == util::device_of(&a.archive)?;
    let free = util::free_bytes(&anc)?;
    let done = saved.as_ref().map(|(s, _)| s.done).unwrap_or(0);
    let p = engine::simulate(f.pieces(), store.len(), free, a.keep_free, done);
    emit(&format!(
        "{{\"ev\":\"plan\",\"supported\":{},\"format\":{},\"pieces\":{},\"volumes\":{},\"archive\":{},\"total_out\":{},\"free\":{},\"keep_free\":{},\"need_normal\":{},\"fits_normal\":{},\"short\":{},\"fits\":{},\"largest\":{},\"same_storage\":{},\"resume\":{},\"done\":{}}}",
        same,
        jstr(f.kind()),
        f.pieces().len(),
        store.vols.len(),
        store.len(),
        p.total_out,
        p.free,
        a.keep_free,
        p.need_normal,
        p.free >= p.need_normal,
        p.short,
        p.short == 0,
        p.largest,
        same,
        saved.is_some(),
        done
    ));
    Ok(())
}

fn cmd_run(a: &Args, abandon: bool) -> Res<()> {
    // Stopped in the last moment of a finished run (archive already deleted)? Then it's done.
    if !a.archive.exists() {
        if let Some((st, _)) = load_saved(&a.dest) {
            if st.done == st.total {
                let _ = fs::remove_dir_all(engine::state_dir(&a.dest));
                emit(&format!("{{\"ev\":\"done\",\"total_out\":0,\"freed\":{},\"links_skipped\":0,\"note\":\"finished earlier\"}}", st.start_len));
                return Ok(());
            }
        }
        fail!(Usage, "{} doesn't exist", a.archive.display());
    }
    let paths = volumes_for(&a.archive);
    let mut store = Store::open(&paths)?;
    fs::create_dir_all(&a.dest)?;
    let sdir = engine::state_dir(&a.dest);
    let saved = load_saved(&a.dest);
    let mut f = open_format(&store, saved.as_ref().map(|(s, b)| (s.format.as_str(), b.as_slice())), a)?;
    if util::device_of(&a.dest)? != util::device_of(&a.archive)? {
        fail!(Unsupported, "the destination is on different storage than the archive; deleting the archive wouldn't make room there");
    }
    let mut st = match saved {
        Some((st, _)) => {
            if st.format != f.kind() || st.total != f.pieces().len() {
                fail!(Io, "the saved progress belongs to a different archive");
            }
            st
        }
        None => {
            if abandon {
                emit("{\"ev\":\"abandoned\",\"note\":\"nothing to abandon\"}");
                return Ok(());
            }
            fs::create_dir_all(&sdir)?;
            util::atomic_write(&sdir.join("index.bin"), &f.snapshot())?;
            let st = State {
                format: f.kind().to_string(),
                start_len: store.len(),
                total: f.pieces().len(),
                done: 0,
                keep: f.pieces().len(),
                dirty: false,
                verified: false,
            };
            st.save(&sdir)?;
            st
        }
    };
    let mut ctx = Ctx {
        dest: a.dest.clone(),
        stage: sdir.join("stage"),
        state_dir: sdir.clone(),
        tool7z: a.tool7z.clone(),
        unarc: a.unarc.clone(),
        outputs: Vec::new(),
        links: Vec::new(),
    };
    if abandon {
        return engine::abandon(f.as_mut(), &mut store, &ctx, &st);
    }
    let o = engine::RunOpts { keep_free: a.keep_free, verify_first: a.verify, eager: a.eager };
    engine::run(f.as_mut(), &mut store, &mut ctx, &mut st, &o)
}

fn main() {
    util::install_signals();
    let r = parse_args().and_then(|a| match a.cmd.as_str() {
        "plan" => cmd_plan(&a),
        "run" => cmd_run(&a, false),
        "abandon" => cmd_run(&a, true),
        c => Err(Error::new(Kind::Usage, format!("unknown command {c}"))),
    });
    if let Err(e) = r {
        emit(&format!("{{\"ev\":\"error\",\"code\":{},\"msg\":{}}}", jstr(e.kind.code()), jstr(&e.msg)));
        std::process::exit(e.kind.exit());
    }
}
