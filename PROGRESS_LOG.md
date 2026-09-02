# BFE — Progress Log

Running engineering log for BFE (`com.the412banner.bfe`). Newest state first, then the timeline, then
lessons and backlog. Companion to the README (what the app *does*) — this is *how it got here and
where it stands*.

---

## Current state (2026-09-02)

- **Latest release: v0.10** — https://github.com/The412Banner/BFE/releases/tag/v0.10
  `main` @ `dc32a89` (README) / build commit `606c1fc`, CI run 33617124598, versionCode 11.
- **Shipped feature set:** dual-pane commander (shared active-pane toolbar, per-pane Grid/List/Compact +
  sort, wrapping toolbar); extraction (7-Zip, innoextract w/ GOG DLC batch, FreeArc unarc, into
  SAF/root); compression (zip/7z/tar/tar.gz/tar.xz/tzst/Winlator `.wcp`); pinned SAF app storage;
  root any-app `/data/data` browsing with chown-to-app-uid safety; APK cloner + signer (ARSCLib +
  apksig, v1/v2/v3, built-in test key + key manager); video converter (static ffmpeg 7.1.5 + x264 →
  H.264/AAC MP4); `.apk` icons; New file; Open-with / Share / Install-APK targets.
- **Verification level:** every release is **CI-green + `apksigner` v1/v2/v3-verified + version-checked**.
  The cloner pipeline is **JVM-proven on a real APK**; the shipped ffmpeg binary was **run from the APK**
  (aarch64 PRoot) and converted a test clip. **Nothing from 0.5 onward has been run on a device yet**
  by the maintainer — device testing is the open gate for: SAF/root browsing, the root chown path,
  compression, cloner UI + split-merge, converter UI, toolbar wrapping, the 0.9.1 progress fix.
- **Repo:** public since 2026-09-01. The committed test key (`keystore/bfe-test.jks`, pw `bfetest`)
  is therefore public — accepted for a sideload dev tool; a private release key via CI secret is the
  upgrade path if tamper-resistance is ever wanted.

## Release pipeline (how every version is cut)
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
- The build agent and coordinator share one checkout: always check `git rev-list origin/main..HEAD`
  before pushing docs so you don't ship half-done work early.

## Known limitations (as of 0.10)
Fixed 50/50 split; path bar not clickable-segmented; SAF only reaches DocumentsProvider apps; extracting
*from* a SAF/root-hosted archive unsupported; piped tar.gz/xz report % not per-file; Compact ≈2× (not
¼) density; `.bk2` (Bink 2) undecodable; software-only x264; MP4-only output; clones of
signature-checking / Play-licensed / Google-services apps may not run; toasts not snackbars; no
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
- Nice-to-haves: drag-to-resize divider; clickable breadcrumb; snackbars; extract-from-SAF/root
  archives; more converter outputs; private release key via CI secret.
