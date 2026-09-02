# BFE

**BFE** is a standalone, **sideload-only** Android file manager and explorer built around a set of
bundled native engines. In one app: a dual-pane "commander" layout; **extraction** of archives, disc
images, GOG/InnoSetup installers and FreeArc repacks; **compression** to zip/7z/tar/tzst and Winlator
`.wcp` packs; **browsing storage normal file managers can't reach** — other apps' SAF document
providers and, on a rooted device, **any installed app's private data**; an **APK cloner + signer**;
and a **video converter** (game FMVs → MP4) powered by a bundled ffmpeg.

It is lifted from the in-app File Manager + archive subsystem of the
[Bannerlator](https://github.com/The412Banner/Bannerlator) emulator (Winlator lineage), with all of
the emulator machinery (Wine containers, X server, Steam/GOG store plumbing) stripped out and the
browse / extract / manage core turned into its own app. GPL-3.0.

> **Status:** early, fast-moving 0.x builds. Every release is CI-built and signature-verified, but the
> project is not yet broadly device-tested. Grab the latest APK from
> [**Releases**](https://github.com/The412Banner/BFE/releases).

---

## Features

### Browsing & file management
- Browse internal storage, SD cards and USB drives. **Landscape** shows a collapsible **side rail** of
  locations; **portrait** uses a storage dropdown in the toolbar. Both list your pinned app storages.
- **`.apk` files show their real launcher icon** in list, grid and compact views (parsed off the
  main thread from File, SAF and root locations, like the `.exe` PE icons).
- **New ▾ → Folder / File…**: create an empty file on File, SAF and root locations (root creations
  are chown'd back to the owning app); text-like files offer "Open with…" right away.
- Path / free-space bar, up/back navigation, sort by **name / size / date / type** (asc/desc), in-folder
  **search**, show/hide hidden files.
- **Three view modes**, cycled from the toolbar and remembered: **Grid**, **List**, and a dense
  **Compact** list (~2× more rows on screen).
- **Multi-select** with an action bar: copy, cut, delete, share. Copy/move/delete/rename with real
  progress, cancel, and conflict handling (**overwrite / merge / keep both / skip**). New folder.
- **Favorites**, file **properties** + read-only toggle, image thumbnails (Coil) and **`.exe` icon
  extraction** (PE resources).
- **Open with** any file in another app, **Share** one or many files, and **Install** `.apk` files
  directly.

### Dual-pane "commander" mode
- Toolbar toggle → **two fully independent panes side by side** — in landscape *and* portrait
  (fixed 50/50 split).
- **One shared toolbar** controls the **active** pane (tap a pane to focus it); each pane keeps its own
  folder, selection, scroll, **view mode and sort**, so the two sides can differ (e.g. left Compact,
  right Grid).
- One-tap **Copy → / Move →** from the active pane into the other pane's folder.
- **Extract into the other pane:** extracting an archive defaults its destination to the other pane's
  folder — "extract here → into there" in one tap.

### Extraction (bundled native engines, all on-device)
| Engine | Handles |
|---|---|
| **7-Zip** (`7zz`) | `.7z`, `.zip`, `.rar` (incl. multi-part), split volumes (`.001`, `.bin`), `.tar` / `.tar.gz` / `.tar.xz`, disc images `.iso` / `.udf` / `.img`, `.wcp` / `.tzst`, and more |
| **innoextract** | **InnoSetup / GOG offline installers** — unpacks the game files directly, including **automatic DLC batch**: sibling `setup_*.exe` DLC installers in the same folder are detected and extracted into one merged game folder |
| **unarc** (FreeArc) | **FreeArc / ISDone game repacks** (FitGirl / DODI-style `Setup-N.bin` volumes) decoded natively |

- One-tap **Fast Extract**, or the full **Unpack Archive** screen (destination picker, options).
- Runs as a **background foreground-service job** with a progress notification, cancel, and an
  **app-wide minimizable progress pill** — survives the app going to the background.
- Extraction targets can be normal storage **or a pinned SAF / root location** (extracted to a temp
  folder, then copied into the target with the ownership fix described below).
- BFE registers as an **"Open with" / Share target** for archives and `setup.exe` files, so other apps
  can hand them straight to the extractor.

### Compression — create archives
Select files/folders → **Compress…** → format, name, level, optional password, destination (this pane
or the other). Same background job / pill / cancel as extraction.
| Format | Engine |
|---|---|
| **zip** (optional AES-256 password) · **7z** (optional password, encrypted headers) · **tar** | bundled 7-Zip |
| **tar.gz** · **tar.xz** | two piped 7-Zip processes — no on-disk `.tar` staging, so a large folder never doubles its footprint |
| **tzst** (`tar.zst`) | streamed Java tar + Zstandard (`commons-compress` + `zstd-jni`) |
| **Winlator `.wcp`** | a `tar.zst` content pack with a `profile.json` manifest |

The **Create Winlator pack** form collects the pack **type** (Wine / Proton / DXVK / D7VK / VKD3D /
Box64 / WOWBox64 / FEXCore / VEGAS), versionName, versionCode, description (and the `wine` paths for
Wine/Proton packs) and writes the exact `profile.json` schema Bannerlator/Winlator parse — a pack you
build in BFE installs straight into Bannerlator.

### App storage — pin other apps' storage as locations
- **Storage apps (SAF):** the *Add app storage* picker lists installed apps that expose a
  **DocumentsProvider** (SD/USB, Downloads, cloud apps like Drive/Dropbox, and opt-in apps) by app
  label + package name. Pick one, confirm the folder in Android's picker (it opens at the app's root —
  a single "Use this folder" tap), and it's **pinned** as a location you can browse and manage: new
  folder, rename, delete, copy/move in and out (including cross-pane), share, open-with, multi-select.
- **All apps (root):** on a rooted device, the same picker lists **every installed app**. Pick one and
  BFE **drops you straight into its private `/data/data/<pkg>`** (and its `Android/data/<pkg>` if
  present) — **no folder-picker step**. Full management there: browse, copy/move in and out, rename,
  delete, new folder, share/open-with/install (staged through BFE's cache, since other apps can't read
  `/data/data`).
- **Ownership safety:** anything BFE writes into an app's sandbox as root is automatically **chowned
  back to that app's uid** (with `chmod 770/660` and `restorecon`), so the target app keeps working —
  no root-owned leftovers that make an app crash with permission errors.
- Pinned locations appear in the side rail / storage dropdown with a × to unpin.

### APK Cloner + Signer
- **Clone** any `.apk` file (⋮ → *Clone APK…*) or **any installed app** (*Clone installed app…* — split/
  bundle installs are merged into a single APK first) with a new **package name, label, versionCode,
  versionName and icon**, plus an Advanced section (minSdk/targetSdk, debuggable, allowBackup,
  extractNativeLibs).
- The rename is done *properly* so clones actually install and run beside the original: relative
  component class names are made absolute (code keeps resolving), every **ContentProvider authority**
  moves to the new package (no provider conflict), package-scoped **permissions** are renamed,
  `sharedUserId` is dropped, `taskAffinity` is rewritten, and the manifest + `resources.arsc` package
  are renamed together. The result lists every fixup applied. **Clone & Install** in one tap.
- **Sign APK…** on any `.apk` — re-signs with APK signature schemes **v1 + v2 + v3** (per-sign
  toggles), zipaligned (16 KB page-aligned native libs when `extractNativeLibs=false`), then verified.
- **Keys:** a built-in **BFE Test Key** (generated once, private to the app) is the default so all your
  clones share one signature and update in place; or import your own **PKCS12 / BKS** keystore, or
  generate a new key in-app. (JKS isn't supported by Android's crypto stack — convert to PKCS12.)
- Verified on a real APK: the pipeline cloned BFE's own release to a new package with v1/v2/v3 all
  verifying and both provider authorities renamed.
- *Inherent limits, shown in-app:* apps that verify their own signature, Play-licensed apps,
  Google-services/Firebase-bound apps, and some split/bundle apps may not run as clones; code that
  builds a FileProvider authority from `BuildConfig.APPLICATION_ID` will mismatch after a rename.

### Video converter — "Convert to MP4…"
- ⋮ on any video (or multi-select → *Convert*) transcodes to **H.264 + AAC MP4** with the bundled
  **ffmpeg** (a static aarch64/bionic GPL build with x264, exec'd like the other native engines).
  Made for game FMV: **Bink 1 (`.bik`)**, **Smacker (`.smk`)**, WMV/ASF, AVI, MKV/WebM, MOV/MP4/M4V,
  MPEG-PS/TS, OGV, FLV, 3GP, VOB.
- **Quality** Fast / Balanced / High (x264 veryfast / medium / slow, CRF 26 / 23 / 20), **resolution**
  Keep / 1080p / 720p / 480p (aspect kept, even dims), **audio** keep (AAC 160k) or none, output to this
  pane or the other one (SAF/root via temp + copy). Batch conversions run one after another as one
  background job with live percent / speed / ETA in the pill and notification; Cancel kills ffmpeg
  and removes the partial file.
- **Engine:** a single static **ffmpeg 7.1.5 + x264** binary (~6.8 MB, arm64, built reproducibly by
  the `build-ffmpeg.yml` workflow and shipped as `libffmpeg.so`). Codec coverage — **video:** H.264,
  HEVC, VP8, VP9, MPEG-1/2/4, MS-MPEG4 v1–3, WMV1/2/3, VC-1, Theora, VP3, MJPEG, Cinepak, Indeo 3/4/5,
  H.263, Bink video, Smacker video; **audio:** AAC, MP3, Vorbis, Opus, WMA v1/v2/Pro, AC-3, Bink audio,
  Smacker audio, PCM, ADPCM (IMA/MS/QT/Yamaha). **Output:** MP4 (`+faststart`, yuv420p) with H.264 +
  AAC, so it plays on anything.
- **Bink 2 (`.bk2`) is proprietary** — there's no open decoder, so BFE says so up front instead of
  failing. Encoding is software x264 (no hardware encoder): quick for 480p/720p cutscenes, slow for 4K.

---

## How storage access works
BFE has a pluggable storage layer with three backends behind one file-manager UI:
- **File** — direct filesystem access via All-Files-Access (the default; what makes native extraction
  possible).
- **SAF** — `DocumentsContract` on a persisted tree URI for pinned document providers.
- **Root** — `libsu` (Magisk) for `/data/data` and anything else, with the chown-back safety above.

Copy/move between any two backends streams through, so you can e.g. copy from a cloud provider into an
app's private folder in one operation.

---

## Requirements
- **Android 8.0+** (minSdk 26, targetSdk 28).
- **arm64-v8a only** — the native extractors are prebuilt for arm64; there is no other ABI.
- **All-Files-Access** (`MANAGE_EXTERNAL_STORAGE`) — requested on first run. The native engines write
  real files and can't go through the scoped-storage picker, so this is required for extraction and
  compression.
- **Root (Magisk) — optional**, only for the "All apps (root)" features. The first root action triggers
  a Magisk prompt; grant BFE root there. Without root everything else works — the root section simply
  stays unavailable (a one-line notice, no crash).

## Install
1. Download the latest `BFE-x.y.apk` from [Releases](https://github.com/The412Banner/BFE/releases).
2. Sideload it (allow "install unknown apps" for your browser/file manager if prompted).
3. On first launch, grant **All-files access**. Grant **root** in Magisk when the root features ask.

BFE is a separate app (`com.the412banner.bfe`) — it never touches Bannerlator or any other install.

## Signing & integrity
Releases are built by CI (`.github/workflows/build.yml`) and signed with a **bundled, publicly-known
TEST key** (`keystore/bfe-test.jks`) using APK signature schemes **v1 + v2 + v3**, so any BFE build
installs over the previous one without uninstalling.

**This repository is public, so that signing key is public too — do not treat it as private.** Anyone
can produce an APK carrying BFE's signature; only install BFE builds you obtained from this repo's
Releases (each release lists its APK SHA-256). This is the normal trade-off for a sideload dev tool;
a private release key can be added via a CI secret if tamper-resistance is ever needed.

## Building from source
```bash
./gradlew assembleRelease     # signed with the committed test key
```
Toolchain: AGP 8.8, Kotlin 2.0.21, Compose BOM 2024.02, Java 17, NDK 29, compileSdk 34. No Hilt, no
local `.so` build step — the prebuilt native binaries live in `app/src/main/jniLibs/arm64-v8a/` and
are **exec'd** (not `System.loadLibrary`'d) from the app's native-lib dir, which is why the manifest
keeps `android:extractNativeLibs="true"` — without it the executables never land on disk and every
extraction fails. CI uses Gradle build/configuration caching so warm builds are fast.

### Repo map
```
app/src/main/java/com/the412banner/bfe/
├─ unpack/     7-Zip / innoextract / unarc wrappers, UnpackService (extraction job), FastExtract
├─ pack/       archive creation (7zz, tar+zstd, Winlator .wcp), PackService (compression job)
├─ apk/        APK cloner/editor/signer (ARSCLib rewrite, apksig, keys), ApkJobService
├─ video/      ffmpeg wrapper + ConvertService (video → MP4 job)
├─ storage/    StorageBackend abstraction: File / SAF / Root backends, cross-backend transfer, pins
├─ ui/screens/ FileManagerScreen (panes, shared toolbar, dual-pane), Unpack screen, Compress dialog
└─ ui/         progress pills, theme, components
app/src/main/jniLibs/arm64-v8a/   prebuilt lib7zz / libinnoextract / libunarc / libffmpeg + dependency closure
keystore/bfe-test.jks             public test signing key
```

## Known limitations
- Dual-pane split is a fixed 50/50 (no drag-to-resize yet). Path bar isn't a clickable breadcrumb yet.
- SAF only reaches apps that **expose a DocumentsProvider** — most ordinary apps don't, and Android
  blocks SAF from `Android/data`. Use the root mode for those.
- Extracting *from* an archive that lives on a SAF/root location isn't supported yet (archives must be
  on normal storage; extracting *into* SAF/root works).
- Piped `tar.gz` / `tar.xz` compression reports percent but not per-file names.
- Compact rows are ~2× denser than the standard list, not a literal quarter-height (readability floor).
- Video conversion: **Bink 2 (`.bk2`) can't be decoded** (proprietary); encoding is software-only
  (x264), so large/4K sources are slow; output is always H.264 + AAC MP4 (no other output formats yet).
- APK clones of apps that verify their own signature, are Play-licensed, or are bound to
  Google-services/Firebase may not run; that's inherent to cloning.
- Errors surface as toasts; no predictive-back animation (targetSdk 28).

## License
BFE is licensed under the **GNU General Public License v3.0** (see [`LICENSE`](LICENSE)), inherited
from its Winlator/Bannerlator lineage. Bundled third-party components keep their own notices — see
`NOTICE_7ZIP.txt`, `NOTICE_INNOEXTRACT.txt`, `NOTICE_UNARC.txt`, `NOTICE_FFMPEG.txt` (FFmpeg + x264,
GPL v2+, built with `--enable-gpl --enable-libx264`) and `License_7zip.txt`.
