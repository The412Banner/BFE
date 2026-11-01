//! Errors, CRC-32, JSON output and the file-system calls the engine needs (fsync, free space,
//! punching holes) without any crate.

use std::ffi::CString;
use std::fmt;
use std::fs::{self, File, OpenOptions};
use std::io::{self, Write};
use std::os::unix::ffi::OsStrExt;
use std::os::unix::fs::MetadataExt;
use std::os::unix::io::AsRawFd;
use std::path::{Component, Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::OnceLock;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Kind {
    Io,
    Usage,
    NoSpace,
    Damaged,
    Password,
    Unsupported,
    Unsafe,
    Interrupted,
}

impl Kind {
    pub fn code(self) -> &'static str {
        match self {
            Kind::Io => "IO",
            Kind::Usage => "USAGE",
            Kind::NoSpace => "NO_SPACE",
            Kind::Damaged => "DAMAGED",
            Kind::Password => "PASSWORD",
            Kind::Unsupported => "UNSUPPORTED",
            Kind::Unsafe => "UNSAFE",
            Kind::Interrupted => "PAUSED",
        }
    }
    pub fn exit(self) -> i32 {
        match self {
            Kind::Io => 1,
            Kind::Usage => 2,
            Kind::NoSpace => 3,
            Kind::Damaged => 4,
            Kind::Password => 5,
            Kind::Unsupported => 6,
            Kind::Unsafe => 7,
            Kind::Interrupted => 130,
        }
    }
}

#[derive(Debug)]
pub struct Error {
    pub kind: Kind,
    pub msg: String,
}

impl Error {
    pub fn new(kind: Kind, msg: impl Into<String>) -> Error {
        Error { kind, msg: msg.into() }
    }
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "{}: {}", self.kind.code(), self.msg)
    }
}

impl From<io::Error> for Error {
    fn from(e: io::Error) -> Error {
        // ENOSPC / EDQUOT
        if matches!(e.raw_os_error(), Some(28) | Some(122)) {
            return Error::new(Kind::NoSpace, format!("storage full: {e}"));
        }
        Error::new(Kind::Io, e.to_string())
    }
}

pub type Res<T> = Result<T, Error>;

#[macro_export]
macro_rules! fail {
    ($kind:ident, $($arg:tt)*) => {
        return Err($crate::util::Error::new($crate::util::Kind::$kind, format!($($arg)*)))
    };
}

// ── CRC-32 (IEEE, as in ZIP, gzip, 7z, RAR, FreeArc) ──

fn crc_table() -> &'static [u32; 256] {
    static T: OnceLock<[u32; 256]> = OnceLock::new();
    T.get_or_init(|| {
        let mut t = [0u32; 256];
        for (i, e) in t.iter_mut().enumerate() {
            let mut c = i as u32;
            for _ in 0..8 {
                c = if c & 1 != 0 { 0xEDB8_8320 ^ (c >> 1) } else { c >> 1 };
            }
            *e = c;
        }
        t
    })
}

/// Running CRC: start with `Crc32::new()`, feed with `update`, read with `value`.
#[derive(Clone, Copy)]
pub struct Crc32(u32);

impl Crc32 {
    pub fn new() -> Crc32 {
        Crc32(0xFFFF_FFFF)
    }
    pub fn update(&mut self, data: &[u8]) {
        let t = crc_table();
        let mut c = self.0;
        for &b in data {
            c = t[((c ^ b as u32) & 0xFF) as usize] ^ (c >> 8);
        }
        self.0 = c;
    }
    pub fn value(&self) -> u32 {
        self.0 ^ 0xFFFF_FFFF
    }
}

pub fn crc32(data: &[u8]) -> u32 {
    let mut c = Crc32::new();
    c.update(data);
    c.value()
}

// ── JSON lines on stdout (the app reads them) ──

pub fn jstr(s: &str) -> String {
    let mut o = String::with_capacity(s.len() + 2);
    o.push('"');
    for ch in s.chars() {
        match ch {
            '"' => o.push_str("\\\""),
            '\\' => o.push_str("\\\\"),
            '\n' => o.push_str("\\n"),
            '\r' => o.push_str("\\r"),
            '\t' => o.push_str("\\t"),
            c if (c as u32) < 0x20 => o.push_str(&format!("\\u{:04x}", c as u32)),
            c => o.push(c),
        }
    }
    o.push('"');
    o
}

pub fn emit(line: &str) {
    let out = io::stdout();
    let mut l = out.lock();
    let _ = l.write_all(line.as_bytes());
    let _ = l.write_all(b"\n");
    let _ = l.flush();
}

// ── Stop requests (SIGINT / SIGTERM): finish the current step, save, exit 130 ──

static STOP: AtomicBool = AtomicBool::new(false);

extern "C" fn on_signal(_: i32) {
    STOP.store(true, Ordering::SeqCst);
}

extern "C" {
    fn signal(sig: i32, handler: extern "C" fn(i32)) -> usize;
    fn statvfs(path: *const std::os::raw::c_char, buf: *mut u64) -> i32;
    fn fallocate(fd: i32, mode: i32, offset: i64, len: i64) -> i32;
}

pub fn install_signals() {
    unsafe {
        signal(2, on_signal); // SIGINT
        signal(15, on_signal); // SIGTERM
    }
}

pub fn stop_requested() -> bool {
    STOP.load(Ordering::SeqCst)
}

pub fn check_stop() -> Res<()> {
    if stop_requested() {
        fail!(Interrupted, "paused; run again to continue");
    }
    Ok(())
}

// ── File system ──

/// Bytes free for an unprivileged writer on the file system holding `path`.
pub fn free_bytes(path: &Path) -> Res<u64> {
    let c = CString::new(path.as_os_str().as_bytes()).map_err(|_| Error::new(Kind::Usage, "bad path"))?;
    // struct statvfs is eleven 64-bit fields plus spare on arm64 (bionic and glibc); a roomy buffer.
    let mut buf = [0u64; 32];
    let r = unsafe { statvfs(c.as_ptr(), buf.as_mut_ptr()) };
    if r != 0 {
        return Err(io::Error::last_os_error().into());
    }
    let frsize = if buf[1] != 0 { buf[1] } else { buf[0] };
    Ok(buf[4].saturating_mul(frsize))
}

/// The device a path's file system lives on (for "same storage?" checks).
pub fn device_of(path: &Path) -> Res<u64> {
    Ok(fs::metadata(path)?.dev())
}

/// Frees `len` bytes at `off` inside `file` without changing its size. False when the file
/// system can't (exFAT, FAT, some FUSE setups).
pub fn punch_hole(file: &File, off: u64, len: u64) -> bool {
    if len == 0 {
        return true;
    }
    // FALLOC_FL_KEEP_SIZE | FALLOC_FL_PUNCH_HOLE
    unsafe { fallocate(file.as_raw_fd(), 0x01 | 0x02, off as i64, len as i64) == 0 }
}

pub fn sync_dir(dir: &Path) {
    // Not every file system lets a directory be fsynced (FUSE may refuse); data files are synced anyway.
    if let Ok(d) = File::open(dir) {
        let _ = d.sync_all();
    }
}

/// Writes `data` to `path` so that a crash leaves either the old or the new content.
pub fn atomic_write(path: &Path, data: &[u8]) -> Res<()> {
    let tmp = path.with_extension("tmp");
    {
        let mut f = OpenOptions::new().write(true).create(true).truncate(true).open(&tmp)?;
        f.write_all(data)?;
        f.sync_all()?;
    }
    fs::rename(&tmp, path)?;
    if let Some(p) = path.parent() {
        sync_dir(p);
    }
    Ok(())
}

/// An archive member name turned into a safe relative path, or None when it would escape the
/// destination (absolute, `..`, empty). Backslashes count as separators (archives made on Windows).
pub fn safe_rel(name: &str) -> Option<PathBuf> {
    let norm = name.replace('\\', "/");
    let mut out = PathBuf::new();
    for part in norm.split('/') {
        match part {
            "" | "." => continue,
            ".." => return None,
            p => {
                if p.contains('\0') {
                    return None;
                }
                out.push(p);
            }
        }
    }
    if out.as_os_str().is_empty() {
        return None;
    }
    // Belt and braces: every component must be a normal one.
    if out.components().any(|c| !matches!(c, Component::Normal(_))) {
        return None;
    }
    Some(out)
}

/// Like [safe_rel], but a name that is only the archive's own top folder (".", "./") is Ok(None):
/// nothing to unpack. Names that would escape are an error.
pub fn member_rel(name: &str) -> Res<Option<PathBuf>> {
    let norm = name.replace('\\', "/");
    if norm.split('/').all(|p| p.is_empty() || p == ".") {
        return Ok(None);
    }
    match safe_rel(name) {
        Some(r) => Ok(Some(r)),
        None => Err(Error::new(Kind::Unsafe, format!("unsafe path in archive: {name}"))),
    }
}

pub fn human(n: u64) -> String {
    const U: [&str; 5] = ["B", "KB", "MB", "GB", "TB"];
    let mut v = n as f64;
    let mut i = 0;
    while v >= 1000.0 && i < U.len() - 1 {
        v /= 1000.0;
        i += 1;
    }
    if i == 0 {
        format!("{n} B")
    } else {
        format!("{v:.1} {}", U[i])
    }
}

pub fn le16(b: &[u8], p: usize) -> u16 {
    u16::from_le_bytes([b[p], b[p + 1]])
}
pub fn le32(b: &[u8], p: usize) -> u32 {
    u32::from_le_bytes([b[p], b[p + 1], b[p + 2], b[p + 3]])
}
pub fn le64(b: &[u8], p: usize) -> u64 {
    let mut a = [0u8; 8];
    a.copy_from_slice(&b[p..p + 8]);
    u64::from_le_bytes(a)
}
