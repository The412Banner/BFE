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
├─ ui/screens/ FileManagerScreen (PaneState, BrowserPane, SharedToolbar, dual-pane) · UnpackArchiveScreen · CompressDialog · ConvertDialog · ApkToolsDialogs
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
