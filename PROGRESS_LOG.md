# BFE — Progress Log

Running engineering log for BFE (`com.the412banner.bfe`). Newest state first, then the timeline, then
lessons and backlog. Companion to the README (what the app *does*) — this is *how it got here and
where it stands*.

---

## In flight: archive browsing (branch `feat/archive-browse`, 2026-10-16)

Open an archive like a folder, ZArchiver / MT Manager style. Branched from `feat/open-with-all-archives`,
rebased onto `main` = 1.0.1 (`ca38ed6`). **Not merged, not released, not device-run as a UI** (see proof).

- **What it does:** "Open as folder" (first Open with action for archives, also in ⋮) shows the archive in
  the same pane through the alt-stack (like SAF/root): breadcrumb `…/folder › Game.zip › inner › dir`,
  Back walks inner dirs then out. Tap a file inside → extracted to `cache/archive-view/<hash>/` → the
  normal Open with sheet (Open as folder for a nested archive, Install for an APK, Extract to…,
  Properties). Multi-select → Extract… / Extract → (other pane) / Copy + paste anywhere = extraction
  with the paste conflict choices. Passwords asked once per archive per session. Compressed tars
  (`.tar.gz/.xz/.bz2/.zst/.tgz/.wcp`) auto-descend via a one-time cached inner tar
  (`cache/archive-tar/<hash>/inner.tar`, 2 kept). Read-only: write actions hidden or refused.
- **Where:** `storage/ArchiveListing.kt` (pure Kotlin: `SltParser`, `ArchiveTree`, `SltOutcome`),
  `storage/ArchiveBackend.kt` (`Loc.ArchiveLoc` backend, `ArchiveIndexCache`, `ArchivePasswords`,
  `ArchiveExtract`, cache housekeeping), `ui/screens/ArchivePane.kt` (pane state, opening indicator,
  password / Extract to… / Properties dialogs, archive ⋮ menu). `FileManagerScreen.kt` only gets hooks.
  `unpack/` (SevenZip.kt, UnpackService.kt) and the manifest are untouched (the decoder branch owns them).
- **Proof level:** parser + tree **JVM-tested** (`ArchiveListingTest`, 21 tests, kotlinc 2.0.21 in the
  sandbox and `testReleaseUnitTest` in CI) on fixtures captured from the bundled 7zz 24.08 on the device
  (zip with/without dir entries, 7z, `-ba`, tar, tar.gz, wcp/zstd, Windows-backslash zip, ZipCrypto,
  7z encrypted files + encrypted headers, not-an-archive, truncated zip) plus a synthetic 100k-entry
  listing; the real 100k-entry listing (29 MB) parses in ~0.5 s on the device's JVM. The 7zz command
  lines BFE runs (`-p`, `-spd`, folder-prefix names, list files, `-so`, wrong-password text) were
  **device-verified by hand**. Everything UI is **compile-only** (CI). Needs an on-device UI pass.

## Current state (2026-10-13)

- **Latest release: v1.0 (versionCode 12), the first stable release.** https://github.com/The412Banner/BFE/releases/tag/v1.0
  Cut by `release.yml`; see the timeline for commits and CI runs.
- **1.0 adds:** in-app updater (update.json on the `manifest` branch, fallback
  `releases/latest/download/update.json`; versionCode compare; Update / Later / Skip; cancelable
  download; SHA-256 check; installer via FileProvider; install-unknown-apps flow; 6 h auto-check
  toggle), `release.yml`, BFE's own **Open with** sheet on file tap (built-in actions + apps + Open as
  chips + per-extension default), grouped ⋮ menu, long-press = multi-select, folder item counts + medium
  dates in List/Compact, clickable breadcrumb, drag-to-resize panes, snackbars, empty/error/no-match
  states, Settings + About (theme mode, dynamic color, default apps, licenses in-app), full Material 3
  colour scheme with themed system bars, monochrome launcher icon, byte sizes without decimals.
- **Signing:** one key since 0.1 (`CN=BFE Test`, cert SHA-256 `4693b885…08fe`). CI now reads it from
  the `BFE_KEYSTORE_B64` secret (same key) with the committed file as fallback, and
  `scripts/check-signing.sh` fails any build signed with a different certificate.
- **Verification level:** 1.0 is CI-built, certificate-checked and version-checked; nothing in 1.0 has
  been run on a device by the maintainer yet. The updater's download + install path can only be
  exercised once a release newer than 1.0 exists. The older device-test backlog (SAF/root, compression,
  cloner, converter, toolbar) still stands.

## In flight: Home dashboard (`feat/home-dashboard`, 2026-10-16)
Owner ask (File Manager+ home grid as reference): the first screen shows the same options plus cloud
management. Branch off `main` `ca38ed6` (1.0.1); not merged, no release.
- **What:** BFE now opens on a Home grid (4 cols portrait, 5-10 landscape) of tiles with live stats:
  Main storage (StorageStatsManager total/free = Settings' numbers, decimal units, usage bar), one tile
  per removable volume (StorageRoots + StatFs), System (`/` via RootBackend when root is granted or
  granted on tap, else File read-only), Downloads (walkFileTree: size + top-level item count), Images /
  Audio / Videos / Documents / New files (MediaStore "external" = all volumes; size + count), Apps,
  Cloud / Remote / Access from network (placeholders), then a Favorites row. ⋮ = Refresh, Settings,
  Check for updates, About.
- **Category browser** (`ui/screens/categories/`): MediaStore list → list/grid (Coil for images,
  `loadThumbnail` for video/audio), sort name/size/date, search, multi-select Share / Copy to… / Move
  to… (BFE's own folder picker = FileManagerScreen pick-dir mode, so Download/ and roots are pickable)
  / Delete (+ MediaScanner rescan); tap = Open with sheet with Show in folder / Install / Extract to….
- **Apps** (`ui/screens/apps/`): PackageManager list, User/System/All, search, sort; sizes via
  StorageStatsManager (Usage access) or one root `du` pass, else APK size + grant banner; sheet with
  Open, App info, Uninstall, Back up APK (`<label>_<version>.apk`, split → `.apks` zip of base+splits,
  default `Download/BFE Backups`, folder choosable), Share APK (named copy in cache), Clone… (the
  existing ApkEditorDialog + ApkJobService, output in the backup folder), Browse app data (root).
- **Navigation:** `AppRoute` in MainActivity's AppRoot. FileManagerScreen stays composed under every
  other screen (panes keep folder, selection, clipboard); while hidden its BackHandlers are pointed at a
  detached OnBackPressedDispatcher (`BackScope`) so Back on Home exits instead of climbing an invisible
  pane. FileManagerScreen got 3 optional params (`openRequest`, `onHome`, `onBackAtRoot`), a toolbar
  Home button, a Back-at-root handler and a last-folder pref write — nothing else in it changed.
- **Settings → Start on:** Home (default) / Last folder (active pane's File path, saved on navigation).
- **Intent:** `MainActivity.EXTRA_OPEN_PATH` (String, absolute folder path) skips Home and opens the
  file manager there (onCreate + onNewIntent); for the extraction notification's "Open folder".
- **Permissions:** `PACKAGE_USAGE_STATS` (lists BFE under Usage access; user-granted) and
  `REQUEST_DELETE_PACKAGES` (required at targetSdk 28 to hand a package to the uninstall dialog).
- **Verification:** CI compile only (no device run, no JVM tests). `build.yml` on the branch:
  run 38977484101 green on `8eab222` (feature); follow-up fixes (backup dialog steps aside for its
  folder picker; comparator) re-built on the next commit.
- **Owner to confirm:** decimal units on the storage tiles only (matches Settings / the box; file
  sizes elsewhere stay binary); the Cloud / Remote / Network stats lines ("Cloud storage",
  "SFTP · SMB · WebDAV", "HTTP · FTP"); Documents + New files skip dot-folders; New files = last 7 days
  by date added; copies in the category browser never overwrite (" (1)"); clones from Apps land in the
  backup folder; Back at a storage root goes Home (also after "Show in folder").

## In flight: `feat/local-server` — Access from network (2026-10-16)
Not merged, not released. Branch off `ca38ed6` (1.0.1). Owner ask: host a local HTTP or FTP file
server so devices on the same Wi-Fi can reach the phone's files (or chosen folders), offline.
- **Entry point:** `ui/screens/server/ServerScreen(onBack)` — for the Home dashboard's "Access from
  network" tile. Until Home lands: ⋮ → Settings → Files → **Access from network** (SettingsScreen
  `page = "server"`, which hands the whole page to ServerScreen and its own app bar).
- **Libraries:** NanoHTTPD 2.3.1 (BSD-3), Apache FtpServer 1.2.1 + MINA 2.2.4 (Apache-2.0),
  slf4j-android 1.7.36 (MIT), Nayuki qrcodegen 1.8.0 (MIT; chosen over zxing: 38 KB vs ~550 KB). No
  native code. Notices: `licenses/NOTICE_NANOHTTPD.txt`, `NOTICE_APACHE_FTPSERVER.txt`, SLF4J + QR MIT
  texts appended to `THIRD_PARTY_LIBRARIES.txt`; all listed in Settings → Open-source licenses.
- **Manifest:** `ServerService` (dataSync FGS), `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`.
  `MainActivity.onCreate` gained one line (`ServerService.startOnLaunch`).
- **Proof level (2026-10-16):** the server core (`server/` minus the Android files) was compiled
  with kotlinc 2.0.21 and run on the JVM (aarch64 sandbox, 127.0.0.1) against a fixture tree: RW
  share, RO share, a duplicate share name, UTF-8 / space / `+` names, dotfiles, a symlink escaping the
  share (dir + file), a symlink inside it, and a 5 GB sparse file.
  - HTTP, 78/78 curl checks: auth 401/200, Basic challenge, 10-failure lockout (429), listing JSON +
    HTML, gzip pages, slash redirect, full download md5, Range 100-199 / suffix / past 4 GB / 416,
    HEAD without body, ETag 304, If-Range, UTF-8 + `+` + space names, Content-Disposition `filename*`,
    `..` / `%2e%2e` / `..%2f` → 400, symlink escapes → 404 and hidden from listings, PUT upload
    (md5, mtime kept, no temp left, `mkdirs`, 403 without the CSRF header, 403 in RO, keep-alive
    intact after a refused upload), mkdir / rename / delete incl. RO and share-root refusals, delete
    never follows a link out, folder / selection (GET and POST form) / top-level zips verified with
    Python `zipfile`, and a **ZIP64** download of the 5 GB entry streamed and CRC-verified, nosniff,
    `CSP: sandbox` on shared HTML.
  - FTP, 43/43 Python `ftplib` + 11/11 curl checks: anonymous and wrong password refused, FEAT
    (UTF8, MLST, no AUTH TLS), OPTS UTF8, EPSV + forced PASV + active PORT, MLSD (sizes, types, escapes
    hidden), CWD `..` clamped at `/`, backslash paths, RETR md5, REST resume incl. past 4 GB, SIZE of
    5 GB, STOR / APPE / UTF-8 STOR / MKD / RNFR+RNTO / DELE / RMD, and RO refusals for STOR, APPE, STOU,
    DELE, MKD, RNTO-into, RNFR-out-of, MFMT, plus top-level STOR and share-root RMD.
  - Modes, 13/13: whole storage at `/` (no escape to siblings, root undeletable), anonymous HTTP +
    FTP (any user/password), DNS-rebinding Host check (IP / `.local` allowed, names refused).
  - Web page, 28 checks in headless Chromium over CDP: sort, filter, hidden toggle, upload via the
    file picker (bytes verified) with replace prompt, **folder drag & drop**, new folder / rename /
    delete through the native dialogs, multi-select delete, selection zip download (valid, UTF-8
    names), image viewer + Escape, navigation, RO page hides write actions; screenshots at desktop,
    412 px phone and dark mode.
  - Android layer (ServerService, LanAddresses, ServerSettings, ServerScreen, QR): **compile-only
    (CI)**. Not run on a device: Wi-Fi / hotspot detection, the notification, locks, Windows Explorer /
    FileZilla / Finder / Android FTP clients and phone browsers are the device-test list below.

## Cloud + Remote via rclone (branch `feat/cloud-rclone`, 2026-10-16, not merged)

Owner ask: "cloud management for various cloud storage services" + Remote connections, reached from the
new Home screen's **Cloud** and **Remote** tiles (`CloudScreen(onBack)` / `RemoteScreen(onBack)` in
`ui/screens/cloud/CloudScreens.kt`; the Home branch wires them).

- **Engine = rclone v1.75.2, trimmed** (`rclone/main.go`: rcd, lsjson, copy/copyto/move/moveto,
  delete/deletefile/purge/mkdir/rmdir, about, config, authorize, version, rc, obscure, … + backends
  drive, onedrive, dropbox, box, pcloud, mega, yandex, protondrive, webdav, s3, b2, sftp, ftp, smb,
  http, local, crypt, combine, union). Built by `build-rclone.yml`: Go 1.26.9 + NDK r29, android/arm64,
  cgo/bionic, API 26. **47,616,352 B binary / 14,998,988 B .gz** (upstream full android build:
  88,817,560 B / 27,940,918 B gz). Runs 38976010135 and 38976867583 produced **bit-identical** files.
- **Not bundled.** `RcloneManager` downloads `rclone-v1.75.2-android-arm64.gz` from release tag
  `rclone-v1.75.2-android-arm64` on first open (explains it + size first), checks the pinned SHA-256
  (gz `259868b5…d34b`, binary `19015133…c6c2`), gunzips to `filesDir/bin/rclone`, runs `rclone version`
  once. A copy of the .gz in `Download/` can be installed offline. The release is published by the
  maintainer, not by CI.
- **Runtime:** `RcloneDaemon` keeps one `rclone rcd` (127.0.0.1, random port, random basic-auth password
  via env, `--rc-serve`, cwd `/`) shared by screens + the transfer service, idle-stopped after 90 s.
  Config `filesDir/rclone/rclone.conf` (app-private; backups off). `RcClient`/`RcloneApi` are plain JVM.
- **Accounts:** add via `config/create` with `nonInteractive`, then answer rclone's own questions
  (`CloudSetup`): auto-answers per service + single-choice lists; the rest as dialogs. **OAuth:** answer
  `config_is_local=true` as an async job, read `config/oauthstatus` (new in 1.7x) for the
  `127.0.0.1:53682/auth` URL, open it in the browser, `config/oauthstop` cancels; `config_template`
  swaps rclone's done page for one with a `bfe-auth://done` "Return to BFE" link (`AuthReturnActivity`).
  New accounts are test-listed. Rename = edit the INI section header (rc has no rename; rcd reloads the
  file when its mtime/size change). Own client ID/secret per OAuth service.
- **Browser + transfers:** see README. Transfers = rclone jobs (`operations/copyfile`, `sync/copy`)
  polled via `job/status` + `core/stats group=job/<id>`, cancel = `job/stop` (rclone removes the partial).
- **Proof so far:** device (Pocket FIT, Android 14) as root via the bridge: `rclone version`, local
  `lsjson`, `lsjson :http: --http-url https://downloads.rclone.org` (DNS + TLS OK), rcd + `curl rc/noop`
  (device curl and from the sandbox; 401 without auth), Drive OAuth up to Google's consent page (307
  from the auth URL), custom done page renders, a job's stats + stop (partial file removed). JVM test of
  RcClient/RcloneApi/RcdProcess/RcloneConfigFile against the CI binary in the PRoot sandbox: 29/29.
  **Not yet run inside the app on a device**; no real account signed in.
- **Finding:** rclone's shared Google Drive client ID "is being retired and will stop working during
  2026" (rclone asks `config_shared_client_id`). The Drive form says so and links rclone's guide to a
  personal client ID; BFE answers "continue with the shared one" only when no ID was given.
- Licences: `licenses/NOTICE_RCLONE.txt` (MIT text + all 142 linked modules: 59 Apache-2.0, 44 MIT,
  31 BSD, 5 MPL-2.0, ISC, CC0, and `cloudsoda/sddl` LGPL-3.0 via the SMB backend); full texts are the
  workflow's `rclone-licenses.txt`, to be attached to the release.

## In flight: extra archive formats (branch `feat/extra-decoders`, 2026-10-16, not merged)
The formats ZArchiver / MT Manager open that the bundled 7-Zip can't, behind one service engine
`"extra"` (`unpack/ExtraFormats.kt`). Built on `feat/quick-extract` (874ce64) so its background
Extract-to queue, Pause/Resume and QuickExtract planner cover them too.

| Format | Engine | Proof |
|---|---|---|
| lzip `.lz` `.tlz` `.tar.lz` | lunzip 1.15 (`liblunzip.so`, static) | device-run, hash-exact (single, tar, multi-member, truncated → rc 2) |
| lzop `.lzo` `.tzo` `.tar.lzo` | lzop 1.04 + LZO 2.10 (`liblzop.so`, static) | device-run, hash-exact (truncated → rc 1) |
| LZ4 `.lz4` `.tar.lz4` | lz4 1.10.0 CLI (`liblz4.so`, static) | device-run, hash-exact (frame, legacy, block-dependent, concatenated) |
| ECM `.ecm` | ecm-tools 1.0.3, decode-only patch (`libunecm.so`, static) | device-run, hash-exact (literal + Mode 1 + Mode 2 Form 1/2 sectors) |
| ALZ `.alz` | unalz 0.65 + Debian patches + static GNU libiconv (`libunalz.so`) | device-run, hash-exact vs EggDotNet's zip twin; listing parser checked on real output |
| EGG `.egg` | in-app `EggArchive` + `AzoDecoder` (xunazo port) | JVM + ART on device (d8 + `app_process`): all 34 EggDotNet test archives, 6 hash-exact vs reference zip, CRC-32 per block, 13 encrypted ones refused |
| ACE 1.x `.ace` | unace 1.2b + Debian 1.2b-27 patches + 2 BFE fixes (`libunace.so`) | device-run, hash-exact vs acefile (LZ77 + stored); ACE 2.0 / password refused up front |
| Amiga LZX `.lzx` | amiga-lzx 0.1.5 `lzx` (`liblzx.so`, Rust PIE) | device-run, hash-exact (own archive + 2 real Aminet samples) |
| OTA `payload.bin` | in-app `OtaPayload` | JVM + ART on device: synthetic full / mixed / incremental payloads; schema checked with `protoc --decode` against AOSP's proto |

- **Native build:** `.github/workflows/build-extract-tools.yml` (push-triggered on itself / `native/**`,
  plus dispatch). Green run **38979886818** @ `466b879` → artifact `extract-tools-arm64`, committed to
  jniLibs; hashes in each `licenses/NOTICE_*.txt`. Patches in `native/patches/`.
- **App build:** `build.yml` (dispatched on the branch) green on the integration commit (run
  38978472804 @ `12de52c`) and again on the branch tip with the binaries + notices committed.
- **Not done in-app:** the screen / service / QuickExtract wiring is CI-compiled only — no APK was
  installed on the device (per the brief). ACE 2.0 is not decodable (see backlog).
- Skipped **unlzx.c**: no licence statement anywhere (distros list it as unknown) — amiga-lzx (WTFPL)
  replaces it.

## Release pipeline (how every version is cut)
**From 1.0:** `gh workflow run release.yml -f version=X.Y -f release_notes="..."` does steps 1-3 below
in one job (version bump/validation, build, certificate + aapt check, release `vX.Y` with
`BFE-X.Y.apk` + `update.json`, manifest branch). The notes are user-facing (shown in the update
dialog), never a placeholder; the workflow rejects obvious ones. Push docs first so the tag lands on
the final commit. Then verify both update.json URLs, the asset's version + certificate, and stage.

Before 1.0 (manual):
1. Feature work on `main` (agent-built), pushed → `build.yml` runs `assembleRelease` (signed with the
   test key, v1+v2+v3) → artifact `bfe-release-apk`.
2. Verify: `gh run view <id> --json conclusion,jobs` (never trust `gh run watch`), download the
   artifact, `aapt dump badging` (package/versionCode/versionName/ABI), and
   **`apksigner verify --min-sdk-version 21 -v`** — the `--min-sdk-version 21` is required to make
   apksigner *report* v1; at the APK's real minSdk 26 it prints `v1: false` even though the v1
   signature is present and valid.
3. Cut a GitHub Release `vX.Y` **targeting the full 40-char build SHA** (a short SHA fails with
   "target_commitish is invalid"), attach `BFE-X.Y.apk`, notes list the APK SHA-256.
4. Stage to the maintainer's device: `cp` to `/sdcard/Download/BFE-X.Y.apk`, checksum-match.
5. Ping.

## Timeline

| Ver | Date | Build commit(s) | CI run | Shipped |
|---|---|---|---|---|
| 0.1 | 2026-09-01 | `59c5bb0` P0 · `fcf0502` P1 · `556f3c7` · `1e62b6a` | 33543765362 | Full file manager lifted from Bannerlator + de-emulator'd; extraction stack; All-Files gate; Open-with/Install-APK/Share; VIEW/SEND filters. Staged only (no release tag). |
| 0.2 | 2026-09-01 | `2562b28` · `d9f30a0` | 33548724435 | Dual-pane commander: PaneState hoist, Copy→/Move→, extract-into-other-pane. |
| 0.3 | 2026-09-01 | `2a02dec` | 33553763658 | Portrait split left/right; width-adaptive header; **Compact** view mode. |
| 0.4 | 2026-09-01 | `cea7b58` · `936cc49` | 33559517872 | **Single shared toolbar** bound to the active pane; view + sort per-pane. |
| 0.5 | 2026-09-01 | `a9b9f3e` · `b5dea04` · `5fcefa0` | 33568769613 | Storage-backend layer (File/SAF); **pinned SAF app storage** picker (DocumentsProviders); landscape rail back; "+" in portrait dropdown. |
| 0.6 | 2026-09-01 | `e34ab30` | 33572398649 | **Root backend** (libsu): any-app `/data/data` one-tap; chown-to-app-uid on writes; extract into SAF/root; SAF one-tap-at-root. |
| 0.7 | 2026-09-02 | `7e07b60` · `4a3c281` · `456587e` | 33575176216 | **Compress…** zip/7z/tar/tar.gz/tar.xz (7zz) + tzst/`.wcp` (Java tar+zstd); `.wcp` schema = Bannerlator `ContentProfile`. First run 33574881036 failed (Material3 opt-in). |
| 0.8 | 2026-09-02 | `8107713` · `7c6b590` (fix) | 33577251015 | **APK cloner + signer**: ARSCLib proper rename, apksig v1/v2/v3, built-in test key + key manager, clone installed apps w/ split merge, Clone & Install. First run 33576830590 failed (unclosed block comments). |
| 0.9 | 2026-09-02 | `d09b3b8` | 33610094584 | **Toolbar never clips**: measured-width layout, FlowRow wrap, narrow icon collapse; selection bar + dialog chips wrap. |
| 0.9.1 | 2026-09-02 | `3ce888c` | 33613357237 | **Fix:** extraction progress pinned at 100% — dest-size pollers now baseline the destination. |
| 0.10 | 2026-09-02 | `2c20196` · `606c1fc` (+ ffmpeg CI 33615215915 / 33615584735) | 33617124598 | **Convert to MP4…** (static ffmpeg 7.1.5 + x264 `libffmpeg.so`, `build-ffmpeg.yml`); `.apk` icons; **New file**. |
| 1.0 | 2026-10-13 | `73b9eb2f` feature · `10334042` CI fix · `41ac248d` compile fix · `f8516b95` cert-check fix + docs | 38440434618 · main 38441935303 · release.yml 38441955085 (tag `v1.0` = `f75f3ea0`) | **First stable.** In-app updater + `release.yml` + manifest branch; Open with sheet; Settings/About + licenses; item counts; breadcrumb; resizable panes; snackbars; M3 polish; signing from secret + cert guard. APK `BFE-1.0.apk` 27,822,882 B, sha256 `7b33ea00…8a79145f`, cert same as 0.10. Runs 38439402215 (setup-android `tools` package gone), 38439521683 (missing `detectTapGestures` import) and 38439927296 (cert digest parse) failed. |
| 1.0 (docs) | 2026-10-13 | `236f76bb` | — | release.yml body names the Settings button "Check now" (published v1.0 notes edited to match). |
| (branch) | 2026-10-16 | `feat/archive-browse`: `61cab355` feature + a follow-up (IO-dispatcher placement, quieter warnings, clone destination outside archives) | 38976084785 (green, unit tests included) | **Archive browsing**, read-only (see "In flight"). On top of 1.0.1 `ca38ed6`. Not merged, not released, not device-run. |


## Design decisions (owner, 2026-10-13)
Made from the owner's screenshots during the 1.0 round. Keep these when changing the browser UI:
- **Rows use the free space:** folders show "N items" and the last-modified date, files show size
  and date. Counts are computed lazily per visible row, off the main thread. Byte sizes have no decimals ("34 B").
- **Tapping a file opens BFE's own Open with sheet, never Android's chooser.** BFE's actions are at
  the top (extract, install, clone, convert, properties) and installed apps that can open it are
  below, with "Open as" type chips. A remembered "Always use for .ext" app is listed first and
  marked, but is **never launched automatically**; every tap shows the sheet.
- **Tapping a file does not open the ⋮ action menu.** This was tried and rejected; the ⋮ button keeps the menu.
- Tapping a folder navigates; long-press starts multi-select; the ⋮ menu is grouped, with Delete in the error color.

## Architecture (where things live)
```
app/src/main/java/com/the412banner/bfe/
├─ unpack/     SevenZip / Innoextract / Unarc wrappers · UnpackService (extract job, baselined dest poller) · FastExtract
├─ pack/       PackModels (incl. .wcp profile.json) · SevenZipPack · TarZstPack · PackService (compress job)
├─ storage/    StorageBackend (Loc = FileLoc|SafLoc|RootLoc) · RootBackend (libsu, fixOwnership) · StorageTransfer · AppStorage (pins)
├─ apk/        ApkRewriter (ARSCLib rename+fixups) · ApkSigning (apksig, zipalign) · SigningKeys · ApkJobService · ApkModels
├─ video/      Ffmpeg (exec libffmpeg.so, -progress parsing) · ConvertService · ConvertModels
├─ server/     Access from network. Pure JVM core: VirtualFs (namespace + sandbox) · HttpFileServer/HttpPages
│              (NanoHTTPD + page) · FtpFileServer/FtpVirtualFs (Apache FtpServer) · ServerHost · ServerActivity
│              · MimeTypes. Android: ServerService (FGS, locks, 3 s network re-check) · LanAddresses · ServerSettings
├─ ui/screens/ FileManagerScreen (PaneState, BrowserPane, SharedToolbar, dual-pane) · UnpackArchiveScreen · CompressDialog · ConvertDialog · ApkToolsDialogs · server/ServerScreen + QrCodeImage

├─ cloud/      RcClient · RcloneApi · RcdProcess · RcloneDaemon · RcloneManager · CloudSetup · CloudServices · CloudTransfers/Service
├─ ui/screens/ FileManagerScreen (PaneState, BrowserPane, SharedToolbar, dual-pane) · UnpackArchiveScreen · CompressDialog · ConvertDialog · ApkToolsDialogs
└─ ui/         UnpackProgressPill (+ pack/convert pills) · theme · CollapsibleRail
app/src/main/jniLibs/arm64-v8a/  lib7zz · libinnoextract (+boost/lzma/iconv/z/bz2 closure) · libunarc · libc++_shared · libffmpeg
.github/workflows/  build.yml (signed release APK, gradle caching) · build-ffmpeg.yml (static NDK ffmpeg+x264) · build-rclone.yml (trimmed rclone, cgo/bionic)
```
Native engines are **exec'd** from `nativeLibraryDir` (not `System.loadLibrary`), which is why
`android:extractNativeLibs="true"` is mandatory.

## Lessons learned (don't repeat these)
- **A `/*` inside a Kotlin block comment cannot be escaped** — the glob `lib/**\/*.so` in a KDoc opened
  nested comments that swallowed the rest of two files, producing a cascade of bogus "unresolved
  reference" errors. Diagnose "Unclosed comment" *first*; check `/\*` vs `\*/` balance per file.
- Material3 `SegmentedButton` / `ExposedDropdownMenu` / `PullToRefresh` need `@OptIn(ExperimentalMaterial3Api)`;
  a refactor can silently drop a function-level opt-in — prefer file-level.
- `apksigner verify` reports `v1: false` at minSdk ≥ 24 even when v1 is present/valid — use
  `--min-sdk-version 21` to see the truth.
- `gh release create --target` needs the **full** SHA.
- The dest-size progress pollers (innoextract/FreeArc) must **baseline** the destination; summing
  everything already there pins the bar at 100% on a re-extract. (Bannerlator still has this bug.)
- The bundled 7-Zip 24.08 is **zstd decode-only** (`-tzstd` → E_NOTIMPL) — tzst/.wcp creation uses
  commons-compress tar + zstd-jni.
- Build agents can die from a mid-session model switch (transcript lost) or a network drop (API
  connection lost) — resume via the agent id when it exists, else respawn with the file-map brief
  above. Work on disk is never lost.
- `android-actions/setup-android@v3` installs the retired `tools` package by default, which current
  sdkmanager rejects ("Failed to find package 'tools'"). Pass `packages: 'platform-tools'`.
- A Compose gesture *extension* (`detectTapGestures`) can't be called by its fully qualified name;
  import it.
- The updater depends on signature continuity: CI checks the certificate digest on every build so a
  wrong key can never reach a release.
- rclone's `local` backend resolves a remote's relative paths against the process working directory;
  rcd runs with cwd `/` so they match the `/` fs used for local paths.
- `gh workflow run build-rclone.yml --ref feat/cloud-rclone` works even while the workflow file exists
  only on that branch (run 38979307335); build-rclone.yml also runs on pushes that touch its inputs.
- The build agent and coordinator share one checkout: always check `git rev-list origin/main..HEAD`
  before pushing docs so you don't ship half-done work early.
- **Kotlin `apply {}` scope shadows outer properties.** `ListenerFactory().apply { setPort(port) }`
  read the factory's own `getPort()` (21), not the class field — the FTP server quietly bound port 21
  (EACCES on a phone). Use plain locals / explicit assignments when a builder has a same-named getter.
  Caught only because the server core runs on the JVM; keep it Android-free so that stays possible.
- NanoHTTPD 2.3.1: HEAD on a fixed-length response sends the body (answer HEAD with an empty stream +
  the real length); an unread request body corrupts the next keep-alive request (close the
  connection); it gzips every `text/...` response, which breaks Range on text files (gzip only
  generated pages); URL decoding turns `+` into a space (links encode `+` as `%2B`).
- CSS grid + `display:none` children: a hidden grid item leaves the flow and every later cell shifts
  a column. Give list cells explicit `grid-column`s.

- **7zz facts learned for archive browsing (bundled 7-Zip 24.08, device-verified 2026-10-16):**
  without `-p` an encrypted listing waits on "Enter password:" (stdin closed → "Break signaled",
  exit 255) — always pass `-p<pw>` (empty is fine); `-ttar.gzip` (tar nested in a stream compressor)
  is E_NOTIMPL and `-tgzip.tar` is "not a tar", so compressed tars need the inner tar on disk; a folder
  name extracts its whole subtree even when the archive has no entry for that folder; `-spd` turns off
  wildcard matching; the zstd stream's only entry has NO `Path` line; a FAT-host zip lists
  `Win\Dir\a.txt` and 7zz on Android extracts it as one file with backslashes in its name; 7z marks
  folders only via `Attributes = D …`, tar via `Folder = +` / `Mode = d…`; 7-Zip's list-file reader
  trims spaces/quotes per line (names with edge spaces must go on argv after `--`).
- **On-device shell:** toybox `env` treats any argument containing `=` as an assignment, and app lib
  paths look like `/data/app/~~xyz==/…` — run `LD_LIBRARY_PATH=$d $d/lib7zz.so`, never `env … $d/lib7zz.so`.

- **Debian's unace 1.2b-24..27 extracts nothing:** its CVE-2015-2063 bounds check counts the PSIZE
  field twice, so every file header is rejected (rc 0, zero files). BFE's patch 010 fixes it; patch 011
  closes a 1-byte overflow in read_wd() that AddressSanitizer found on a corrupt archive.
- **bionic headers leak `PATH_MAX` (4096) through stdio/fcntl**; old code that falls back to its own
  `PATH_MAX` in some files gets two sizes for one global array. Build such code with `-include limits.h`.
- Static links against bionic `libc.a` collide with any global named like a syscall wrapper (lzop's
  `sysinfo()`): rename with `-D`.
- `workflow_dispatch` alone can't be dispatched from a branch until the workflow exists on the default
  branch — give a new workflow a `push: paths:` trigger. Bound every download (`curl --max-time`,
  `timeout sdkmanager`, `timeout-minutes`): two runs hung for 20 min on a stalled mirror.
- Kotlin `pos += f()` reads `pos` *before* calling `f()`; if `f()` advances `pos`, the update is lost.
- unalz exits 0 on every failure — judge it by its output (`done.` without `failed.` / `err code(`).
- update_engine has **no REPLACE_ZSTD** (the enum ends at LZ4DIFF_PUFFDIFF = 13); zstd in OTAs is the
  on-device VABC COW compression, not a payload operation.
- In-app Kotlin decoders can be proven under ART without installing anything: compile the decoders +
  a test main, `d8 --min-api 26`, push the dex, and run it as root with
  `app_process -Djava.class.path=<dex> <dir> <MainKt> …`.

## Known limitations (as of 1.0)
SAF only reaches DocumentsProvider apps; extracting
*from* a SAF/root-hosted archive unsupported; piped tar.gz/xz report % not per-file; Compact ≈2× (not
¼) density; `.bk2` (Bink 2) undecodable; software-only x264; MP4-only output; clones of
signature-checking / Play-licensed / Google-services apps may not run; no
predictive-back animation (targetSdk 28); imported keystore passwords stored in private prefs as
plain text.

Archive browsing (branch above): read-only; no browsing of SAF/root-hosted archives, InnoSetup/GOG or
FreeArc installers; archive-pane extraction is a pane job (progress + Cancel), not a foreground service;
icons/thumbnails inside archives are generic; a compressed tar needs cache space for its inner tar.

## Backlog / next
- **Access from network device test** (feat/local-server): Start HTTP + FTP on Wi-Fi and on the
  phone's hotspot (check the hotspot address shows and binds); open from a PC browser (list, stream a
  big video with seeking, drag-drop upload of a folder, zip a folder > 4 GB) and a phone browser; FTP
  from FileZilla, Windows Explorer (`ftp://ip:2121`), macOS Finder (read-only) and an Android FTP
  client; RO folder refuses writes; screen off with "keep running" on (transfer continues); Wi-Fi off →
  "paused" notification → back on → resumes on the new address; notification Stop; "start when BFE
  opens"; process kill → service restarts what was running. Confirm nothing listens on rmnet/cellular.

- **Extra formats (feat/extra-decoders):** device-test the in-app flow once an APK with them is installed
  (Open with → Extract for `.lz`/`.egg`/`.ace`/`payload.bin`, Pause/Resume on a tool, the payload
  partition picker); a real full-OTA `payload.bin` and a real `.alz` with Korean names. Possible
  follow-ups: ACE 2.0 via a Kotlin port of droe/acefile (BSD-2, decodes ACE 2.0 incl. its
  EXE/DELTA/SOUND/PIC filters), password support for EGG (ZIP 2.0 / AES / LEA) and ALZ.
- **Device-test pass** (the real gate): SAF pin + browse; root pin, copy *into* an emulator app's
  sandbox and confirm the app still reads it; Create WCP from a DXVK folder and install it in
  Bannerlator; Clone & Install a simple app, then an installed split app; convert a `.bik`/`.wmv`
  cutscene; confirm the 0.9 toolbar on a narrow phone; re-extract over an existing folder for 0.9.1.
- **Port back into Bannerlator** — assessed 2026-09-02 as feasible: `storage/`, `pack/`, `apk/`,
  `video/`, the progress-fix, APK icons, New file, intent filters all drop in nearly as-is (Bannerlator
  has most deps); the real work is merging the dual-pane/shared-toolbar `FileManagerScreen` refactor
  back **while keeping** Bannerlator's container/Wine/shortcut hooks. Do it in phases, on a feature
  branch, behind device tests, under the vc-77 freeze. First and cheapest: the 0.9.1 progress-bar fix.
- **First updater proof:** with 1.0 installed from the release, cut the next release (e.g. 1.0.1) and
  confirm 1.0 offers it, downloads, verifies and installs over itself.
- Device-check the 1.0 UI: Open with sheet (chips, default app, empty state), item counts on SAF/root,
  breadcrumb, pane divider, Settings theme/dynamic color, license viewer.
- **Archive browsing follow-ups:** device UI pass (open zip/7z/rar/iso/apk/wcp, nested archive, password
  prompt, Extract to all four destinations, SAF/root destination, 100k-entry zip scroll, cancel a big
  inner-tar unpack); write support via `7zz a` / `d` / `rn` in `ArchiveBackend`; `innoextract --list`
  for Inno/GOG installers; thumbnails for cached image entries; browse SAF/root archives by staging.
- Nice-to-haves: extract-from-SAF/root archives; more converter outputs; a built-in text/image viewer
  for the Open with sheet; a private (non-public) release key, which would need key rotation (APK v3
  signing lineage) so existing installs keep updating.
