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

## Known limitations (as of 1.0)
SAF only reaches DocumentsProvider apps; extracting
*from* a SAF/root-hosted archive unsupported; piped tar.gz/xz report % not per-file; Compact ≈2× (not
¼) density; `.bk2` (Bink 2) undecodable; software-only x264; MP4-only output; clones of
signature-checking / Play-licensed / Google-services apps may not run; no
predictive-back animation (targetSdk 28); imported keystore passwords stored in private prefs as
plain text.

## Backlog / next
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
