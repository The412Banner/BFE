# Banners File Explorer (BFE)

**A full-featured, sideload-only file manager for Android** — dual-pane browsing, on-device extraction of
archives, disc images and game installers, a VLC-powered media player, cloud and network access, and a
Wi-Fi file server, in one app with a modern **Aurora Glass** interface.

- **Latest release:** [1.1.0](https://github.com/The412Banner/BFE/releases/latest) · Android 8.0+ · arm64
- **License:** GPL-3.0 · **Package:** `com.the412banner.bfe`

Banners File Explorer grew out of the file manager and archive subsystem of the
[Bannerlator](https://github.com/The412Banner/Bannerlator) emulator (Winlator lineage). The emulator
machinery is gone; the browse / extract / manage core is now its own app.

---

## Contents
- [Features](#features)
- [Supported formats](#supported-formats)
- [Requirements & permissions](#requirements--permissions)
- [Install & updates](#install--updates)
- [Building from source](#building-from-source)
- [Project structure](#project-structure)
- [Known limitations](#known-limitations)
- [License](#license)
- [Credits](#credits)

---

## Features

### Home dashboard
- Wide tiles with live stats: **Main storage** (used / total with a usage bar), **SD card / USB** drives,
  **System** (`/`, read-only without root), **Downloads**, **Images**, **Audio**, **Videos**,
  **Documents**, **New files** (last 7 days), **Apps**, **Cloud**, **Remote** and **Access from network**.
- **App storage tiles** — pin another app's storage (via Android's document access, or its private data
  with root) with **+**; each appears as its own small tile with the app's icon. Long-press to remove.
- **Category browsers** for Images, Audio, Videos, Documents and New files — list or grid with
  thumbnails, sort, search, multi-select Share / Copy to / Move to / Delete, and *Show in folder*.
- **Apps** — every installed app with size, version and install / update time: Open, App info,
  Uninstall, **Back up APK** (split installs as one `.apks`), Share, **Clone**, and *Browse app data*
  (root). Full sizes need Usage access or root.
- **Favorites** row, and *Settings → Start on* to open on Home or the last folder.

### File management
- **Dual-pane "commander" mode** in portrait and landscape: two independent panes split down the middle,
  a draggable divider (snaps to ⅓ · ½ · ⅔, double-tap for 50/50), one-tap **Copy → / Move →** into
  the other pane, and extraction straight into the other pane's folder.
- **Grid, List and Compact** views per pane; clickable breadcrumb with free space; search; hidden files.
- **Sorting** with separate orders for folders and files — default **folders A→Z, then newest files
  first** — with natural name order ("Disc 2" before "Disc 10").
- Rows show item counts or sizes and **date + time** of the last change (date over time in list cards).
- **Thumbnails** for photos and **videos** (a frame from ~1 s in), real **APK icons** and **`.exe`
  icons**; BFE's own folder and file artwork everywhere else.
- **Multi-select** with copy, cut, paste, delete, share, compress and extract; real progress, cancel, and
  overwrite / merge / keep both / skip conflict handling. New folder / new file, rename, properties
  (with a separate *Created* time when the filesystem records one) and a read-only toggle.
- **Open with sheet** on every file tap: BFE's own actions first (View, Play, View page, Open as folder,
  Extract, Install, Clone, Convert, Properties), then every app that can open it, *Open as* type chips,
  and *Always use for .ext* defaults (managed in Settings).
- Lists **refresh in place** when you come back to the app — no scrolling to the top to see changes.

### Looks & personalisation
- **Aurora Glass** (default): frosted-glass panels over a soft drifting backdrop, a floating pill toolbar
  in portrait and a pill rail in landscape, a rising selection pill, glass sheets, menus and dialogs,
  and purposeful motion (direction-aware folder transitions, staggered lists, spring sheets). Real
  backdrop blur on Android 12L+; a dense frosted fill on older versions. Respects *Remove animations*.
- **Classic**: the original BFE interface. Switch instantly under **Settings → Look**.
- **Palettes**: Neutral (default), Aurora, Ocean, Ember, Forest and Rose — light and dark — plus
  Android 12+ dynamic colour.
- **App icon picker**: the default *Glass-Filled Neon* plus 20 alternatives (squircle on every launcher,
  with themed-icon variants) under **Settings → App icon**.
- The **Banners File Explorer** wordmark, with the B, F and E initials highlighted.

### Archives
- **Extraction** of 40+ formats with bundled on-device engines (see [Supported formats](#supported-formats)),
  including **GOG / InnoSetup installers** (with automatic DLC batches) and **FreeArc game repacks**.
- **Browse archives like folders** — read-only, in the pane, with breadcrumbs; compressed tars and
  Winlator `.wcp` open straight into their contents; tap a file inside to open it; multi-select →
  **Extract…**; password prompts for zip / 7z / rar.
- **Background extraction** as a foreground service: progress, speed and time left in the notification
  with **Pause / Resume / Cancel / Cancel all**, a **queue**, and an **Open folder** button when done.
- **System "Open with" integration**: BFE appears for archives, disc images and installers in other
  apps — as **BFE** (extract screen) and as a one-tap **Extract to ./<Archive name>/** that runs entirely
  in the background. Files shared from other apps are opened in place (or copied in when the sender hides
  their location).
- **Compression** to zip (AES-256 optional), 7z (optional password, encrypted headers), tar, tar.gz,
  tar.xz, tzst and **Winlator `.wcp`** content packs with a correct `profile.json`.

### Media
- **Video & music player (libVLC)** — plays virtually every container and codec (HEVC, AV1, VP9, VC-1,
  WMV, DivX, MPEG-2, AC-3, DTS, TrueHD, FLAC, Opus…) with embedded, ASS/SSA, SRT and PGS subtitles.
  Gestures for seek, brightness and volume (boost to 200 %), double-tap ±10 s, hold for 2×,
  pinch-zoom, resume position and picture-in-picture. An effects sheet with **Picture** (brightness,
  contrast, saturation, hue, gamma, looks, aspect, crop, zoom, rotate / mirror), **Effects**
  (deinterlace, denoise, banding removal, deblocking, sharpen, film grain), **Audio** (10-band
  equalizer with presets, boost, A/V sync, speed with keep-pitch) and **Subtitles** (tracks, delay,
  size, colour). Music plays with cover art and the folder as a playlist.
- **Photo viewer** — swipe through a folder or category, pinch and double-tap zoom with tiled
  sub-sampling for large photos, GIF / animated WebP, EXIF orientation, details, lossless JPEG
  rotation, slideshow, share, set as, delete.
- **HTML viewer** — renders local `.html` / `.htm` / `.xhtml` / `.svg` with scripts, relative assets,
  find in page, view source and open in browser.
- **Video converter** — any video to H.264 + AAC MP4 with a bundled FFmpeg; made for game FMVs (Bink 1,
  Smacker, WMV, AVI…); quality, resolution and audio options; batch jobs.
- BFE Viewer, Player and HTML Viewer are offered to other apps' *Open with* lists.

### Cloud, remote & network
- **Cloud**: Google Drive, OneDrive, Dropbox, Box, pCloud, Yandex Disk, MEGA, Proton Drive, Nextcloud,
  ownCloud, Amazon S3, Backblaze B2, Wasabi, Cloudflare R2, MinIO and more. **Remote**: SFTP,
  FTP/FTPS, SMB and WebDAV. Browse, open, download, upload, rename and delete, with background
  transfers. Powered by **rclone**, downloaded once on first use (15 MB, SHA-256 pinned).
- **Access from network** — share the whole storage or chosen folders (read-only or read & write) over
  Wi-Fi or your hotspot, with a password. A **web server** (browse, stream with seeking, upload by
  drag & drop, download folders as zip, image viewer) and an **FTP server** (passive / active, UTF-8,
  resume). Address + QR code, background service with a Stop button; never listens on mobile data.

### Storage access & APK tools
- **App storage**: browse any app that exposes its files through Android's document provider, and —
  with root — any app's private `/data/data/<pkg>` with full management; files written as root are
  handed back to the owning app automatically.
- **APK cloner & signer**: clone an APK or an installed app with a new package name, label, version and
  icon (providers, permissions and components renamed properly), and sign with v1 + v2 + v3 schemes
  using the built-in key or your own PKCS12 / BKS key.

### Updates
BFE checks this repository's releases on launch (at most every 6 hours) or on demand, shows the release
notes, downloads the new APK with a progress bar, verifies its SHA-256 and hands it to Android's
installer.

---

## Supported formats

| Category | Extract | Engine |
|---|---|---|
| Common archives | 7z, zip, zipx, rar / rar5 (incl. multi-part), cab, arj, lzh / lha, cpio, xar, chm, wim / esd, msi | 7-Zip |
| Tar & compressors | tar, gz / tgz, bz2 / tbz, xz / txz, zst / tzst, lzma, Z, Winlator `.wcp` | 7-Zip |
| Packages | apk, xapk, apks, apkm, jar, deb, rpm, mtz | 7-Zip |
| Disc & disk images | iso, udf, img, bin / cue, cso, dmg, vhd / vhdx, vmdk, vdi, qcow, squashfs, cramfs | 7-Zip |
| Split volumes | .001, .z01, .r00 | 7-Zip |
| Game installers | InnoSetup / GOG `setup_*.exe` (+ DLC batches) | innoextract |
| Game repacks | FreeArc / ISDone `Setup-N.bin` | unarc |
| lzip · lzop · LZ4 | `.lz` / `.tlz`, `.lzo` / `.tzo`, `.lz4` | lunzip · lzop · lz4 |
| CD images | `.ecm` | unecm |
| Korean archives | `.alz`, `.egg` (incl. AZO, split, solid) | unalz · built-in EGG reader |
| Legacy | ACE 1.x `.ace`, Amiga `.lzx` | unace · amiga-lzx |
| Android OTA | `payload.bin` (full OTAs → partition images) | built-in |

**Create:** zip, 7z, tar, tar.gz, tar.xz, tzst, Winlator `.wcp`.

---

## Requirements & permissions
- **Android 8.0 or newer**, **arm64-v8a** devices only (the native engines are prebuilt for arm64).
- **All files access** — requested on first launch; the native engines read and write real files.
- **Optional:** *Usage access* (full app sizes on the Apps screen), **root via Magisk** (other apps'
  private data, System), and notification permission (job progress and controls).
- Network access is used only for update checks, cloud / remote connections and the local file server.

## Install & updates
1. Download `BFE-x.y.z.apk` from [Releases](https://github.com/The412Banner/BFE/releases) and open it
   (allow installs from your browser or file manager if asked).
2. Grant **All files access** on first launch.
3. Future versions install from inside the app: **Settings → Check now** (or the automatic check).

Every release is built and signed by CI with the same key, so updates install over the previous
version. **The signing key is committed to this public repository** — only install builds from this
repository's Releases; each release lists its APK SHA-256.

---

## Building from source
```bash
./gradlew assembleRelease        # signed with the repository key
./gradlew testReleaseUnitTest    # JVM unit tests (archive listing, media logic, …)
```
Toolchain: AGP 8.8, Kotlin 2.0.21, Compose BOM 2024.02, Java 17, compileSdk 34, minSdk 26,
targetSdk 28. The native engines are prebuilt binaries in `app/src/main/jniLibs/arm64-v8a/` and are
executed (not loaded) from the app's native-library directory, which is why the manifest keeps
`android:extractNativeLibs="true"`.

| Workflow | Purpose |
|---|---|
| `build.yml` | Builds, tests and signature-checks every push to `main` (and on demand) |
| `release.yml` | `gh workflow run release.yml -f version=X.Y.Z -f release_notes="…"` — bumps the version, builds and signs, publishes the release with `update.json`, and updates the `manifest` branch the in-app updater reads |
| `build-ffmpeg.yml` | Reproducible static FFmpeg + x264 build for the video converter |
| `build-extract-tools.yml` | lunzip, lzop, lz4, unecm, unalz, unace and lzx for Android (patches in `native/patches/`) |
| `build-rclone.yml` | The trimmed rclone engine (`rclone/main.go`), published as release `rclone-vX.Y.Z-android-arm64` |

Icons are generated from SVG masters: `scripts/icons/` (file and folder icons) and `scripts/appicons/`
(launcher icons, aliases and catalog, with a render-and-diff checker).

## Project structure
```
app/src/main/java/com/the412banner/bfe/
├─ unpack/      extraction: 7-Zip / innoextract / unarc wrappers, extra formats, EGG / AZO / OTA
│               readers, UnpackService (background queue, notification controls), QuickExtract
├─ pack/        compression (7-Zip, tar + Zstandard, Winlator .wcp) and PackService
├─ storage/     File / SAF / Root / Archive backends, transfers, pins, archive listing
├─ media/       player and viewer logic (geometry, colour, settings, libVLC options, resume)
├─ cloud/       rclone engine manager, rc API client, account setup, transfer service
├─ server/      Wi-Fi HTTP (NanoHTTPD) and FTP (Apache FtpServer) servers and service
├─ apk/         APK cloner, editor and signer
├─ video/       FFmpeg converter and ConvertService
├─ update/      in-app updater
├─ appicon/     launcher-icon switching
├─ util/        shared helpers (incoming files, dates, defaults, favourites, …)
└─ ui/          Compose UI — screens (file manager, Home, categories, apps, cloud, server, settings),
                media (photo viewer, video player), theme (Aurora Glass, Classic, palettes, motion),
                icons and components
```

---

## Known limitations
- **Archives:** browsing is read-only; InnoSetup / FreeArc installers can be extracted but not browsed;
  archives on SAF / root locations can't be extracted or browsed yet (extracting *into* them works);
  ACE 2.0, password-protected EGG / ALZ / ACE and incremental OTAs aren't supported.
- **Video converter:** Bink 2 (`.bk2`) is proprietary and can't be decoded; encoding is software-only.
- **Player:** libVLC for Android omits some desktop VLC filters (posterize, mirror, psychedelic…);
  sharpen needs Android 13+; CPU filters and crop / zoom / 90° turns briefly re-open the file; music
  has no background notification yet.
- **Cloud / Remote:** remotes open in their own browser rather than as file-manager panes; files open as
  downloaded copies. rclone's shared Google Drive key is being retired in 2026 — use your own client ID.
- **Network server:** plain HTTP and FTP (no HTTPS / FTPS or WebDAV) — intended for home networks and
  your own hotspot.
- **Storage:** apps without a document provider (and `Android/data`) need root to browse; the category
  tiles only see files Android's media library has indexed.
- **APK clones** of apps that verify their own signature or rely on Google services may not run.

---

## License
Banners File Explorer is free software under the **GNU General Public License v3.0** — see
[`LICENSE`](LICENSE). Bundled components keep their own licences; the full notices are in
[`licenses/`](licenses) and in the app under **Settings → Open-source licenses**.

## Credits
BFE stands on the work of these projects and their authors.

**Lineage**

| Project | Author | License |
|---|---|---|
| [Winlator](https://github.com/brunodev85/winlator) | brunodev85 | GPL-3.0 |
| [Bannerlator](https://github.com/The412Banner/Bannerlator) | The412Banner | GPL-3.0 |

**Native engines**

| Component | Author | License | Used for |
|---|---|---|---|
| [7-Zip](https://www.7-zip.org) 24.08 | Igor Pavlov | LGPL-2.1 + unRAR restriction | Extraction, compression, archive browsing |
| [innoextract](https://github.com/dscharrer/innoextract) 1.9 | Daniel Scharrer | zlib | InnoSetup / GOG installers |
| Boost, zlib, bzip2, liblzma (XZ Utils), GNU libiconv | Boost, Jean-loup Gailly & Mark Adler, Julian Seward, Lasse Collin, GNU | BSL-1.0, zlib, bzip2, 0BSD / public domain, LGPL-2.1+ | innoextract / unalz dependencies |
| [FreeArc unarc](https://github.com/mirror/freearc) 0.67 | Bulat Ziganshin | GPL | FreeArc / ISDone repacks |
| [FFmpeg](https://ffmpeg.org) 7.1.5 + [x264](https://www.videolan.org/developers/x264.html) | FFmpeg developers, VideoLAN | GPL-2.0+ | Video converter |
| [libVLC for Android](https://code.videolan.org/videolan/vlc-android) 3.6.5 | VideoLAN and the VLC authors | LGPL-2.1+ | Video & music player |
| [rclone](https://rclone.org) 1.75.2 | Nick Craig-Wood and contributors | MIT | Cloud & remote (downloaded on first use) |
| [lunzip](https://www.nongnu.org/lzip/lunzip.html) 1.15 | Antonio Diaz Diaz | GPL-2.0+ | lzip |
| [lzop](https://www.lzop.org) 1.04 + LZO 2.10 | Markus F.X.J. Oberhumer | GPL-2.0+ | lzop |
| [LZ4](https://github.com/lz4/lz4) 1.10.0 | Yann Collet | GPL-2.0+ (CLI), BSD-2 (library) | LZ4 |
| [ecm-tools](https://github.com/alucryd/ecm-tools) 1.0.3 | Neill Corlett, alucryd | GPL-3.0+ | ECM CD images |
| [unalz](https://github.com/kippler/unalz) 0.65 | kippler (+ Debian patches) | zlib | ALZ |
| UNACE 1.2b | Marcel Lemke (+ Debian patches) | GPL-2.0+ | ACE 1.x |
| [amiga-lzx](https://crates.io/crates/amiga-lzx) 0.1.5 | Gareth Davidson | WTFPL | Amiga LZX |

**Libraries**

| Library | Author | License |
|---|---|---|
| Android Jetpack / AndroidX (Compose, Activity, Lifecycle, Navigation, Core, AppCompat, Preference, WebKit, ExifInterface) | Google | Apache-2.0 |
| Material Components for Android, Material icons | Google | Apache-2.0 |
| Kotlin, kotlinx.coroutines | JetBrains | Apache-2.0 |
| [Coil](https://github.com/coil-kt/coil) (+ video, gif) | Coil contributors | Apache-2.0 |
| [telephoto](https://github.com/saket/telephoto) | Saket Narayan | Apache-2.0 |
| [Haze](https://github.com/chrisbanes/haze) | Chris Banes | Apache-2.0 |
| [libsu](https://github.com/topjohnwu/libsu) | topjohnwu | Apache-2.0 |
| [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) | Apache Software Foundation | Apache-2.0 |
| [XZ for Java](https://tukaani.org/xz/java.html) | Lasse Collin and contributors | Public domain |
| [zstd-jni](https://github.com/luben/zstd-jni) (Zstandard) | Luben Karavelov, Meta | BSD-2 / BSD |
| [ARSCLib](https://github.com/REAndroid/ARSCLib) | REAndroid | Apache-2.0 |
| [apksig](https://android.googlesource.com/platform/tools/apksig) | The Android Open Source Project | Apache-2.0 |
| [Bouncy Castle](https://www.bouncycastle.org) | Legion of the Bouncy Castle | MIT-style |
| [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) | NanoHTTPD contributors | BSD-3 |
| [Apache FtpServer](https://mina.apache.org/ftpserver-project/) + Apache MINA | Apache Software Foundation | Apache-2.0 |
| [SLF4J](https://www.slf4j.org) | QOS.ch | MIT |
| [QR Code generator](https://www.nayuki.io/page/qr-code-generator-library) | Project Nayuki | MIT |

**Format references**
- [EggDotNet](https://www.nuget.org/packages/EggDotNet) by Andrew Kolman (MIT) and ESTsoft's EGG
  specification — the reference for BFE's EGG reader; **xunazo** by kippler / Bandisoft (zlib), ported
  for AZO, with Project Nayuki's Reference-arithmetic-coding (MIT).
- AOSP update_engine `update_metadata.proto` (Apache-2.0) — the OTA `payload.bin` format.
- Test archives from EggDotNet, xunazo, patool, acefile-testdata and Sembiance's format samples were used
  to verify the decoders (not shipped).
