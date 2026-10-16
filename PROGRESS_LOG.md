# BFE — Progress Log

Running engineering log for BFE (`com.the412banner.bfe`). Newest state first, then the timeline, then
lessons and backlog. Companion to the README (what the app *does*) — this is *how it got here and
where it stands*.

---

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
└─ ui/         UnpackProgressPill (+ pack/convert pills) · theme · CollapsibleRail
app/src/main/jniLibs/arm64-v8a/  lib7zz · libinnoextract (+boost/lzma/iconv/z/bz2 closure) · libunarc · libc++_shared · libffmpeg
.github/workflows/  build.yml (signed release APK, gradle caching) · build-ffmpeg.yml (static NDK ffmpeg+x264)
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

## Known limitations (as of 1.0)
SAF only reaches DocumentsProvider apps; extracting
*from* a SAF/root-hosted archive unsupported; piped tar.gz/xz report % not per-file; Compact ≈2× (not
¼) density; `.bk2` (Bink 2) undecodable; software-only x264; MP4-only output; clones of
signature-checking / Play-licensed / Google-services apps may not run; no
predictive-back animation (targetSdk 28); imported keystore passwords stored in private prefs as
plain text.

## Backlog / next
- **Access from network device test** (feat/local-server): Start HTTP + FTP on Wi-Fi and on the
  phone's hotspot (check the hotspot address shows and binds); open from a PC browser (list, stream a
  big video with seeking, drag-drop upload of a folder, zip a folder > 4 GB) and a phone browser; FTP
  from FileZilla, Windows Explorer (`ftp://ip:2121`), macOS Finder (read-only) and an Android FTP
  client; RO folder refuses writes; screen off with "keep running" on (transfer continues); Wi-Fi off →
  "paused" notification → back on → resumes on the new address; notification Stop; "start when BFE
  opens"; process kill → service restarts what was running. Confirm nothing listens on rmnet/cellular.
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
- Nice-to-haves: extract-from-SAF/root archives; more converter outputs; a built-in text/image viewer
  for the Open with sheet; a private (non-public) release key, which would need key rotation (APK v3
  signing lineage) so existing installs keep updating.
