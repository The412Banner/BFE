# Banners File Explorer (BFE)

**Banners File Explorer** (BFE for short) is a standalone, **sideload-only** Android file manager and explorer built around a set of
bundled native engines. In one app: a dual-pane "commander" layout; **extraction** of archives, disc
images, GOG/InnoSetup installers and FreeArc repacks; **compression** to zip/7z/tar/tzst and Winlator
`.wcp` packs; **browsing storage normal file managers can't reach** — other apps' SAF document
providers and, on a rooted device, **any installed app's private data**; an **APK cloner + signer**;
a **video converter** (game FMVs → MP4) powered by a bundled ffmpeg; **Access from network** — a
local Wi-Fi file server that other devices reach with a web browser or an FTP client; and **cloud storage +
remote connections** (Google Drive, OneDrive, Dropbox, MEGA, S3, SFTP, SMB, WebDAV and more) through rclone.

It is lifted from the in-app File Manager + archive subsystem of the
[Bannerlator](https://github.com/The412Banner/Bannerlator) emulator (Winlator lineage), with all of
the emulator machinery (Wine containers, X server, Steam/GOG store plumbing) stripped out and the
browse / extract / manage core turned into its own app. GPL-3.0.

> **Status: 1.0, the first stable release.** Every release is CI-built and signature-checked, and BFE
> updates itself from this repository's releases (see [Updates](#updates)). Grab the latest APK from
> [**Releases**](https://github.com/The412Banner/BFE/releases).

---

## Features

### Home dashboard
BFE opens on a **Home** screen: a grid of rounded tiles (4 columns in portrait, more in landscape),
each with an icon, a name and a live stats line, measured in the background and refreshed whenever
you come back to it.
- **Main storage** (used / total, as Android's Settings reports it, with a usage bar), one tile per
  **SD card / USB drive** (hidden when none is mounted), **System** (browses `/` — through root when
  BFE has it, otherwise read-only) and **Downloads** (size + item count). Each opens the file manager
  right there.
- **Images, Audio, Videos, Documents** and **New files** (added in the last 7 days) — size + count of
  every matching file on every volume (from Android's media library). Each opens a **category
  browser**: list or grid (photo/video thumbnails), sort by name / size / date, search, and
  multi-select with **Share / Copy to… / Move to… / Delete**. Tapping a file opens BFE's Open with
  sheet (with **Show in folder**); copies never overwrite (a clash gets " (1)"). Documents covers PDF,
  Word/Excel/PowerPoint, OpenDocument, txt, rtf, md, csv/tsv, EPUB and other e-book formats.
- **Apps** — every installed app with icon, version, size and install/update date; **User / System /
  All** filter, search, sort. Tap an app for **Open, App info, Uninstall, Back up APK** (to
  `Download/BFE Backups` or any folder you choose; split installs are saved as one **`.apks`** bundle
  of base + splits), **Share APK**, **Clone…** (the APK cloner below) and, with root, **Browse app
  data**. Full sizes (app + data + cache) need **Usage access**, which the screen offers to open, or
  root; without either it shows each app's APK size.
- **Cloud**, **Remote** and **Access from network** tiles (screens in progress).
- A **Favorites** row under the grid (your starred folders).
- The ⋮ menu has Refresh, Settings, Check for updates and About; the folder icon jumps into the file
  manager where you left it. In the file manager, the toolbar's **Home** button — or Back at a storage
  root — returns here. Prefer the old behaviour? **Settings → Start on → Last folder**.

### Browsing & file management
- Browse internal storage, SD cards and USB drives. **Landscape** shows a collapsible **side rail** of
  locations; **portrait** uses a storage dropdown in the toolbar. Both list your pinned app storages.
- **`.apk` files show their real launcher icon** in list, grid and compact views (parsed off the
  main thread from File, SAF and root locations, like the `.exe` PE icons).
- **New ▾ → Folder / File…**: create an empty file on File, SAF and root locations (root creations
  are chown'd back to the owning app); text-like files offer "Open with…" right away.
- **Clickable breadcrumb** (drive › folder › folder — tap any part to jump there) with free space,
  up/back navigation, sort by **name / size / date / type** (asc/desc), in-folder **search**, show/hide
  hidden files.
- **Rows use their space:** folders show how many items they hold and when they were last changed;
  files show size and date (counted lazily in the background, for File, SAF and root locations).
- **Three view modes**, cycled from the toolbar and remembered: **Grid**, **List**, and a dense
  **Compact** list (~2× more rows on screen).
- **Multi-select** with an action bar: copy, cut, delete, share. Copy/move/delete/rename with real
  progress, cancel, and conflict handling (**overwrite / merge / keep both / skip**). New folder.
- **Favorites**, file **properties** + read-only toggle, image thumbnails (Coil) and **`.exe` icon
  extraction** (PE resources).
- **Tap a file** to get BFE's own **Open with** sheet: what BFE can do with it (extract, install, clone,
  convert, properties) on top, every installed app that can open it below, with **Open as**
  Text / Image / Video / Audio / Archive / Any chips. **Always use for .ext** remembers an app per file
  type (shown first and marked *Default*; manage them in Settings → Default apps). Long-press starts
  multi-select; each row's **⋮** opens the full action menu.
- **Share** one or many files, and **Install** `.apk` files directly.
- Clear **empty, error and no-match states**, and short **snackbar** messages for results.

### Icons
Banners File Explorer draws its own file and folder artwork (original, made for BFE — no icon packs):
**manila folders** with a tab, a sheet of paper peeking out and a shaded front flap; **special
folders** carry a pressed-in emblem (Download, DCIM / Pictures / Screenshots, Music, Movies / Videos,
Documents, Android, Games / Winlator, BFE Backups); **files** are white pages with a folded corner and
a coloured type band (archive, app, Windows program, Winlator package, image, video, audio, document,
spreadsheet, slides, text, code, OTA `payload.bin`); **disc images** are a silver disc. Hidden (dot)
items show the same icon faded. They are used in every listing (list, compact, grid, archives,
categories, cloud/remote, favourites, Open with); real thumbnails, APK icons and EXE icons still win
once loaded. The drawables (`res/drawable/ic_bfe_*.xml`) are generated from the masters in
`scripts/icons/`.

### App icon
BFE's launcher icon is **Glass-Filled Neon** (M5): a frosted-glass folder traced in blue neon with an
amber-lit BFE. **Settings → Appearance → App icon** swaps it for any of the others — the other seven
**Mixes** (Neon Rim Amber, Glass Flap, Neon in Glass, Glass Card Folder, Initials & Spark, Trio Cascade,
Orbit Lens), the twelve **Originals** (Amber Monogram, Glass Panes, Banner Tab, Explorer Compass, Unzip,
Folder B, Layer Stack, Neon Folder, Aurora Glass, Glass Wordmark, Unboxed, Airwave) and **Classic**, the
first blue folder. Tap one for a big preview (and how it looks as an Android 13+ themed icon), then
*Use this icon*.

- **Squircle on every launcher.** The squircle (a superellipse, n = 4) is part of the artwork: each
  adaptive icon has a transparent background, and the picture sits in a squircle 60 dp wide on the
  108 dp canvas — inside the circle a round mask cuts, so circle, squircle, rounded-square and
  no-mask launchers all show the same squircle. Every icon also has a monochrome layer for themed icons.
- **How switching works:** each icon is its own launcher entry (an `<activity-alias>` of the main
  screen, `com.the412banner.bfe.icon.M1` … `O12`, `Classic`); exactly one is enabled. BFE is never closed
  to switch: the step that hides the default entry waits until you leave BFE. Your launcher can take a
  few seconds to update, and some launchers drop the home-screen shortcut when an app's icon entry
  changes — add BFE back from the app drawer. The choice survives updates.
- **Regenerating:** the SVG masters are in `scripts/appicons/src/`, the list in
  `scripts/appicons/icons.json`. `scripts/appicons/build.py` writes the drawables, mipmaps, the Kotlin
  catalog and the manifest aliases; `scripts/appicons/check.py` renders every generated icon next to
  its master, diffs them and writes contact sheets (needs `skia-pathops`, `fonttools`, `pillow`,
  `numpy`, `rsvg-convert`).

### Dual-pane "commander" mode
- Toolbar toggle → **two fully independent panes side by side** — in landscape *and* portrait. **Drag
  the handle between them** to resize (double-tap it for 50/50); the split is remembered.
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
| **lunzip · lzop · lz4** | **lzip** (`.lz`, `.tlz`, `.tar.lz`), **lzop** (`.lzo`, `.tzo`, `.tar.lzo`), **LZ4** (`.lz4`, `.tar.lz4`; frame, concatenated and legacy) — a `.tar` inside is unpacked straight away |
| **unecm** (ecm-tools) | **ECM CD images** (`game.bin.ecm` → `game.bin`, with its `.cue` brought along) |
| **unalz** | **ALZip `.alz`** (Korean CP949 names converted to UTF-8) |
| **EGG** (built in) | **ALZip `.egg`** — Deflate, BZip2, LZMA and **AZO**; solid and split (`.vol1.egg`, `.vol2.egg`, …) archives; CRC-checked |
| **unace 1.2b** | **ACE 1.x** (`.ace`) |
| **lzx** (amiga-lzx) | **Amiga LZX** (`.lzx`) |
| **OTA payload** (built in) | **Android OTA `payload.bin`** (full OTAs) → one `<partition>.img` per partition; pick which partitions; every operation SHA-256-checked |

- One-tap **Fast Extract**, or the full **Unpack Archive** screen (destination picker, options).
- Runs as a **background foreground-service job** with a progress notification, cancel, and an
  **app-wide minimizable progress pill** — survives the app going to the background.
- Extraction targets can be normal storage **or a pinned SAF / root location** (extracted to a temp
  folder, then copied into the target with the ownership fix described below).
- BFE registers as an **"Open with" / Share target** for archives and `setup.exe` files, so other apps
  can hand them straight to the extractor.
- Formats are recognised by their **header**, not just the extension, so a renamed `.lz`, `.egg`,
  `.ace` or `payload.bin` still gets Extract. What an engine can't do is said **before** starting:
  ACE 2.0 compression, password-protected EGG/ALZ/ACE, incremental OTAs.

### Browse archives like folders
- **Open as folder** — the first action in an archive's **Open with** sheet and in its **⋮** menu —
  opens a zip / 7z / rar / iso / tar / apk / xapk / jar / cab / deb / … (anything the bundled 7-Zip
  lists) **in the same pane as a read-only folder**. The breadcrumb reads `…/folder › Game.zip › inner
  › dir`; Back walks up the inner folders, then returns to the archive's folder. The pane's sort, view
  mode, hidden-file toggle, filter and folder item counts all work inside.
- **Compressed tars** (`.tar.gz`, `.tar.xz`, `.tar.bz2`, `.tar.zst`, `.tgz`, Winlator **`.wcp`**) open
  straight into the tar's contents. 7-Zip can't list a tar inside a stream compressor directly, so the
  inner tar is unpacked once into BFE's cache (with progress and Cancel) and reused next time.
- **Tap a file inside** to extract just that file into BFE's cache and get the usual **Open with**
  sheet for it: view it in another app, **install an APK**, or **browse an archive inside the
  archive**. Large entries show progress and can be cancelled.
- **Extract what you picked:** multi-select → **Extract…** (other pane, next to the archive, a new
  folder named after it, or any folder), **Extract →** straight into the other pane, or **Copy** inside
  the archive and **Paste** in any folder. Folders keep everything under them, with the usual
  overwrite / merge / keep both / skip choices; SAF and root destinations work too. **Share** sends
  extracted copies.
- **Password-protected archives** (zip, 7z including encrypted file names, rar): BFE asks once and
  remembers the password until the app closes; a wrong password says so and asks again.
- **Big archives stay responsive:** listing runs in the background with a loading state and is
  cached per archive (path + size + date). A 100,000-entry zip lists in about 1.4 s on a phone; folders
  the archive doesn't store are filled in, and Windows `\` paths show as folders.
- **Read-only for now:** new folder, rename, cut, delete and paste are hidden or refused inside an
  archive ("Archives are read-only in BFE for now").

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

### Access from network — share files over Wi-Fi (browser + FTP)
Open other devices onto this phone's files: a PC, Mac, TV, tablet or another phone on the **same Wi-Fi**,
or connected to **this phone's hotspot**. Everything runs on the phone — no internet, no account, no
cloud. (Until the Home screen lands it lives at ⋮ → **Settings → Access from network**.)
- **Two servers, each with its own big Start / Stop**: a **web server** (HTTP, default port 8080) and an
  **FTP server** (default 2121). The screen shows the address to type (`http://192.168.x.x:8080/`,
  `ftp://192.168.x.x:2121/`) with a copy button and a **QR code**; with Wi-Fi and hotspot both up you
  pick which address. A "devices connected" card lists who's active and the last things they did.
- **What's shared:** the **whole internal storage**, or **only chosen folders** (several at once, each
  shown to clients as its own top-level folder; same-named folders get "(2)"). Add folders with the
  system folder picker, or whole drives (**SD card**, USB) with one tap. Each is **Read-only** or
  **Read & write**. Clients can never step outside what's shared: `..` is refused and symlinks that
  point elsewhere are invisible.
- **Who can connect:** a **user name + password** (a random password is generated and shown; edit it or
  roll a new one), used by both the browser (Basic auth) and FTP — or **no password**, with a warning
  when anything is writable. Changes to shares and the password apply to running servers immediately.
- **In the browser:** a clean page for phones and desktops — folder listing with **sorting** (name,
  size, date), **filter**, show/hide hidden files, **download**, **video / audio streaming** (seeking
  works, via HTTP Range), an **image viewer** (arrows / swipe through the folder), **upload** (several
  files, a whole folder, or **drag & drop**, with per-file progress and a replace prompt), **new folder,
  rename, delete**, and **download a folder or a selection as .zip** — streamed while it's built, so
  nothing is staged on the phone, and files over 4 GB work (ZIP64). Light and dark themes.
- **FTP:** passive mode (data ports from a configurable range, default 50000–50050), active mode,
  UTF-8 names, MLSD listings, resume (REST). Meant for FileZilla, WinSCP, Windows Explorer, Android
  file managers and macOS Finder (read-only there — Finder never writes over FTP). Read-only folders
  refuse every write command.
- **Runs in the background** as a foreground service: the notification shows the address and a
  **Stop** button. BFE holds a Wi-Fi lock while sharing and, with **Keep running with the screen off**
  (on by default), a partial wake lock so transfers don't stall. Options to **start the web and/or FTP
  server when BFE opens**.
- **Wi-Fi only, never mobile data:** the servers bind only to Wi-Fi, hotspot (and Ethernet adapter)
  addresses. BFE re-checks every few seconds: a new address rebinds, and losing Wi-Fi **pauses**
  sharing (the notification says so) until a network is back.
- **Safety details:** wrong passwords lock that device out for a minute after 10 tries; changes from
  the web page need a header other websites can't send, so a page you visit can't use your login;
  shared `.html` / `.svg` files open sandboxed; without a password, requests must use the IP address
  (blocks DNS-rebinding tricks).
- **Engines:** [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) 2.3.1 (BSD-3) for HTTP,
  [Apache FtpServer](https://mina.apache.org/ftpserver-project/) 1.2.1 + Apache MINA 2.2.4
  (Apache-2.0) for FTP, SLF4J (MIT) for its logging, and Nayuki's
  [QR Code generator](https://www.nayuki.io/page/qr-code-generator-library) (MIT). The web page is
  BFE's own HTML/CSS/JS, bundled in the APK.

### Cloud storage & Remote connections (rclone)
- **Cloud** (Home → Cloud): Google Drive, OneDrive, Dropbox, Box, pCloud, Yandex Disk (browser sign-in),
  MEGA, Proton Drive, Nextcloud, ownCloud, Amazon S3, Backblaze B2, Wasabi, Cloudflare R2, MinIO and any
  S3-compatible service (forms). **Remote** (Home → Remote): SFTP, FTP/FTPS, SMB (Windows shares, NAS)
  and WebDAV servers, plus read-only HTTP folders. **More services…** sets up any backend in the build
  with a form generated from rclone's own option list.
- **Accounts:** each shows its service, quota (used / total, where the service reports it) and a menu
  to rename, sign in again or remove. Sign-ins use rclone's built-in OAuth apps by default; every OAuth
  service also takes **your own client ID/secret** (recommended for Google Drive: rclone's shared Drive
  client ID is being retired during 2026).
- **Browser sign-in:** BFE opens the provider's page in your browser (Google blocks embedded WebViews);
  rclone receives the result on `127.0.0.1:53682`, and its "done" page has a **Return to BFE** button.
  Follow-up questions (e.g. OneDrive drive, Drive shared drive) are rclone's own, shown as dialogs, and
  the common ones are answered automatically. Every new account is test-listed before it's called done.
- **Browse a remote:** folders and files with sizes and dates, clickable breadcrumb, sort by
  name / size / date, in-folder search, multi-select. **Open** a file = download to cache, then BFE's
  **Open with** sheet (with *Save to Download* / *Download to…*). **Download** files or whole folders to
  a local folder, **upload** files (system picker, multi-select) or a file/folder chosen in BFE's own
  browser, **new folder**, **rename**, **delete**.
- **Transfers** run in a background service with a notification (progress, speed, ETA, *Cancel all*),
  a pill on the Cloud/Remote screens and a transfers sheet with per-item cancel. Nothing is overwritten:
  an existing name gets " (1)"; folders merge.
- **The engine is downloaded on first use, not bundled.** The first visit explains it and offers a
  one-time **15 MB** download (48 MB installed) of a trimmed rclone v1.75.2 build from this repository's
  release `rclone-v1.75.2-android-arm64`; it is checked against a SHA-256 pinned in the app before it
  runs, and can be removed again from the Cloud/Remote menu. (A copy of the `.gz` in `Download/` is
  offered for offline installs.)
- **How it runs:** one `rclone rcd` (remote-control daemon) per app session on a random `127.0.0.1`
  port behind a random password, driven through its JSON API; stopped after 90 s unused. Accounts,
  tokens and (obscured) passwords live in BFE's private storage (`files/rclone/rclone.conf`); Android
  backups are off, so they never leave the device.

### Settings & About
⋮ → **Settings**: automatic update checks + "Check now", theme (System / Light / Dark, plus
**dynamic color** on Android 12+), the **app icon** (see [App icon](#app-icon)), **Start on** (Home or the last folder), default apps, Access from
network, and About — version, source code link, release notes, BFE's license and every bundled
**open-source license** (7-Zip, innoextract, unarc, FFmpeg, x264, the lzip / lzop / LZ4 / ECM / ALZ /
ACE / LZX tools, the EGG and OTA-payload credits, NanoHTTPD, Apache FtpServer/MINA, rclone and the Java
libraries), readable in-app.

---

## Updates
BFE 1.0 and later **update themselves** from this repository's releases:
- When BFE opens it checks for a newer **stable** release (at most once every 6 hours; turn it off in
  Settings). **Settings → Check now** checks on demand.
- A newer version shows a dialog with its version, size and release notes: **Update**, **Later**, or
  **Skip this version**.
- **Update** downloads the APK with a progress bar (cancelable), checks its **SHA-256**, and opens
  Android's installer. The first time, Android asks you to allow BFE to install apps — BFE explains
  and continues as soon as you come back.
- Offline or GitHub unreachable? The check fails silently.

How it works: each release (made by `.github/workflows/release.yml`) publishes `BFE-X.Y.apk` plus an
`update.json` (versionCode, versionName, notes, apk URL, SHA-256, size, date). The same `update.json`
is written to the `manifest` branch, which the app reads from `raw.githubusercontent.com`; the release
asset `releases/latest/download/update.json` is the fallback. The app compares `versionCode`, and
updates install over the top because every release is signed with the same key.

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
- **Usage access — optional**, only so Home → Apps can show each app's full size (Settings → Usage
  access → BFE). Declaring it grants nothing; the Apps screen opens the right Settings page.
- **Root (Magisk) — optional**, only for the "All apps (root)" features. The first root action triggers
  a Magisk prompt; grant BFE root there. Without root everything else works — the root section simply
  stays unavailable (a one-line notice, no crash).

## Install
1. Download the latest `BFE-x.y.apk` from [Releases](https://github.com/The412Banner/BFE/releases).
2. Sideload it (allow "install unknown apps" for your browser/file manager if prompted).
3. On first launch, grant **All-files access**. Grant **root** in Magisk when the root features ask.

BFE is a separate app (`com.the412banner.bfe`) — it never touches Bannerlator or any other install.

## Signing & integrity
Releases are built by CI (`.github/workflows/release.yml`; every push to `main` is also built by
`build.yml`) and signed with **one fixed key** (`keystore/bfe-test.jks`, or the same key from the
`BFE_KEYSTORE_B64` repository secret) using APK signature schemes **v1 + v2 + v3**, so any BFE build
installs over the previous one without uninstalling. CI fails any build whose signing certificate
isn't that key (SHA-256 `4693b885e7b260043fc35d0565794525302af5734b511a08011888ac772108fe`).

**This repository is public, so that signing key is public too — do not treat it as private.** Anyone
can produce an APK carrying BFE's signature; only install BFE builds you obtained from this repo's
Releases (each release lists its APK SHA-256). This is the normal trade-off for a sideload dev tool;
a private release key can be added via a CI secret if tamper-resistance is ever needed.

## Building from source
```bash
./gradlew assembleRelease     # signed with the committed key
./gradlew testReleaseUnitTest # JVM unit tests (archive-listing parser vs. real 7zz output); CI runs them too
```

### The rclone engine
`.github/workflows/build-rclone.yml` (manual, or on changes to `rclone/`) builds `rclone/main.go`
inside a checkout of rclone at the pinned tag with Go + the NDK (`GOOS=android GOARCH=arm64
CGO_ENABLED=1`, API 26, so it links against bionic; a static Linux build trips Android's seccomp
filter and has no working DNS) and uploads the binary, the `.gz` the app downloads, `SHA256SUMS`, build
info and `rclone-licenses.txt` (every linked module's licence). Builds are reproducible (two runs gave
identical files). To ship a new engine: run it, publish the `.gz` (and `rclone-licenses.txt`) as a
release `rclone-vX.Y.Z-android-arm64`, then update the constants in `cloud/RcloneManager.kt`.

### Cutting a release
```bash
gh workflow run release.yml -f version=1.1 -f release_notes="$(cat notes.txt)"
```
It bumps `versionName`/`versionCode` on `main` if needed, builds and signs the APK, checks the
certificate and version, creates release `v1.1` (latest, not a prerelease) with `BFE-1.1.apk` and
`update.json`, and updates the `manifest` branch. The notes are shown to users in the update dialog.
Toolchain: AGP 8.8, Kotlin 2.0.21, Compose BOM 2024.02, Java 17, NDK 29, compileSdk 34. No Hilt, no
local `.so` build step — the prebuilt native binaries live in `app/src/main/jniLibs/arm64-v8a/` and
are **exec'd** (not `System.loadLibrary`'d) from the app's native-lib dir, which is why the manifest
keeps `android:extractNativeLibs="true"` — without it the executables never land on disk and every
extraction fails. CI uses Gradle build/configuration caching so warm builds are fast.

### Repo map
```
app/src/main/java/com/the412banner/bfe/
├─ unpack/     7-Zip / innoextract / unarc wrappers, ExtraFormats (lzip/lzop/LZ4/ECM/ALZ/ACE/LZX tools +
│              in-app EggArchive / AzoDecoder / OtaPayload), UnpackService (extraction job), FastExtract
├─ pack/       archive creation (7zz, tar+zstd, Winlator .wcp), PackService (compression job)
├─ apk/        APK cloner/editor/signer (ARSCLib rewrite, apksig, keys), ApkJobService
├─ video/      ffmpeg wrapper + ConvertService (video → MP4 job)
├─ server/     Access from network: VirtualFs (shared namespace + sandbox), HttpFileServer + HttpPages
│              (NanoHTTPD), FtpFileServer + FtpVirtualFs (Apache FtpServer), ServerHost, ServerService
│              (foreground service), LanAddresses, ServerSettings — the server core is plain JVM code
├─ storage/    StorageBackend abstraction: File / SAF / Root / Archive (read-only) backends, `7zz l -slt` parser, cross-backend transfer, pins, item counts
├─ update/     UpdateManager (update.json check, download, SHA-256 check, install)
├─ cloud/      rclone: RcClient (rcd API) · RcloneApi · RcloneDaemon/RcdProcess · RcloneManager (first-use download)
│              · CloudSetup (add / sign-in flow) · CloudServices (catalog) · CloudTransferService (queue)
├─ util/       snackbars, default apps, favourites
├─ appicon/    AppIcons (launcher-icon switching via activity-aliases) + the generated AppIconCatalog
├─ ui/screens/ FileManagerScreen (panes, shared toolbar, dual-pane), archive pane (ArchivePane), Open with sheet, Settings, Unpack screen, dialogs
│  ├─ home/       Home dashboard (tiles, stats, navigation, folder picker)
│  ├─ categories/ category browser + MediaStore queries (Images / Audio / Videos / Documents / New files)
│  ├─ apps/       installed apps list, sizes (Usage access / root), APK backup + share
│  ├─ server/     Access from network screen (ServerScreen + QR code)
│  └─ cloud/      CloudScreen / RemoteScreen, accounts, add-account forms, remote browser, transfers UI
├─ ui/icons/   FileIcons: name → BFE icon (special folders, file types), FileTypeIcon / BfeIcon
└─ ui/         progress pills, update dialogs, theme, components
app/src/main/resources/…/server/web/  the Access-from-network web page's app.css + app.js
app/src/main/jniLibs/arm64-v8a/   prebuilt lib7zz / libinnoextract / libunarc / libffmpeg + dependency closure,
                                  liblunzip / liblzop / liblz4 / libunecm / libunalz / libunace / liblzx
native/patches/                   patches applied to those tools by build-extract-tools.yml
rclone/main.go                    the trimmed rclone entry point (which commands + backends are built)
keystore/bfe-test.jks             the (public) BFE signing key
licenses/                         license and notice texts, also shown in Settings
scripts/check-signing.sh          CI guard: the APK must carry the BFE certificate
scripts/icons/                    the file/folder icon masters + generator for res/drawable/ic_bfe_*.xml
scripts/appicons/                 launcher-icon SVG masters, icons.json, build.py (squircle-baked
                                  ic_appicon_* + manifest aliases + catalog) and check.py (renders + diffs)
```

## Known limitations
- SAF only reaches apps that **expose a DocumentsProvider** — most ordinary apps don't, and Android
  blocks SAF from `Android/data`. Use the root mode for those.
- Extracting *from* or browsing an archive that lives on a SAF/root location isn't supported yet
  (archives must be on normal storage; extracting *into* SAF/root works).
- Archive browsing is read-only (no add / delete / rename inside an archive yet). InnoSetup / GOG
  installers and FreeArc repacks can be unpacked but not browsed. Opening a compressed tar needs free
  cache space for its inner tar. Extracting from an archive pane runs in the pane (progress + Cancel),
  not as a background service job, so keep BFE open for very large selections.
- Piped `tar.gz` / `tar.xz` compression reports percent but not per-file names.
- **ACE 2.0** archives can't be unpacked (the bundled unace 1.2b reads ACE 1.x only), nor
  **password-protected** EGG / ALZ / ACE archives. **Incremental OTA** payloads need the phone's
  current images and are refused; full OTAs work.
- Compact rows are ~2× denser than the standard list, not a literal quarter-height (readability floor).
- Video conversion: **Bink 2 (`.bk2`) can't be decoded** (proprietary); encoding is software-only
  (x264), so large/4K sources are slow; output is always H.264 + AAC MP4 (no other output formats yet).
- APK clones of apps that verify their own signature, are Play-licensed, or are bound to
  Google-services/Firebase may not run; that's inherent to cloning.
- No predictive-back animation (targetSdk 28).
- The Home category tiles and browser read Android's media library, so they only see files it has
  indexed: not folders marked `.nomedia`, and on Android 10 and older not files written straight to
  storage (e.g. by an extraction) until the system scans them. Documents and New files skip hidden
  (dot) folders.
- Access from network: plain HTTP and FTP (no HTTPS / FTPS) — meant for a home network or your own
  hotspot, not public Wi-Fi. No WebDAV yet (so no Windows "Map network drive"; Explorer's FTP view and
  the browser work). Browser uploads need JavaScript; a hand-typed URL with a literal `+` in a file
  name must use `%2B` (the page's own links already do).
- Cloud/Remote: remotes aren't yet a location inside the dual-pane file manager (the browser is its own
  screen); files are opened as downloaded copies (edits don't sync back); on object storage (S3, B2…)
  an empty folder disappears until it holds a file. After a browser sign-in you return to BFE yourself
  (the page's *Return to BFE* button, or the app switcher).

## License
BFE is licensed under the **GNU General Public License v3.0** (see [`LICENSE`](LICENSE)), inherited
from its Winlator/Bannerlator lineage. Bundled third-party components keep their own notices, in
[`licenses/`](licenses) and in the app under Settings → Open-source licenses: `NOTICE_7ZIP.txt`,
`License_7zip.txt`, `NOTICE_INNOEXTRACT.txt`, `NOTICE_UNARC.txt`, `NOTICE_FFMPEG.txt` (FFmpeg + x264,
GPL v2+, built with `--enable-gpl --enable-libx264`), `NOTICE_LUNZIP.txt`, `NOTICE_LZOP.txt`,
`NOTICE_LZ4.txt`, `NOTICE_UNECM.txt`, `NOTICE_UNALZ.txt`, `NOTICE_UNACE.txt`, `NOTICE_LZX.txt`,
`NOTICE_EGG.txt`, `NOTICE_OTA_PAYLOAD.txt`, `NOTICE_RCLONE.txt` (rclone, MIT, by Nick
Craig-Wood and contributors, plus the licence of every Go module in the downloaded build) and
`THIRD_PARTY_LIBRARIES.txt`.

### Credits
BFE's extra archive formats stand on these projects (licences and exact versions in the notices):
- **lunzip** by Antonio Diaz Diaz (GPL-2.0+) — lzip.
- **lzop** and **LZO** by Markus F.X.J. Oberhumer (GPL-2.0+) — lzop.
- **LZ4** by Yann Collet (CLI GPL-2.0+, library BSD-2-Clause) — LZ4.
- **ECM** by Neill Corlett, from **ecm-tools** maintained by alucryd (GPL-3.0+) — ECM images.
- **unalz** by kippler (zlib), with Debian's patches by Changwoo Ryu, **GNU libiconv** (LGPL-2.1+)
  and parts of **bzip2** by Julian Seward — ALZ.
- **UNACE 1.2b** by Marcel Lemke (GPL-2.0+), with Debian's patches (Guillem Jover and others) — ACE.
- **amiga-lzx** by Gareth Davidson (WTFPL) and its Rust crate dependencies (MIT / Apache-2.0) — LZX.
- **EggDotNet** by Andrew Kolman (MIT), the reference for BFE's EGG reader, and ESTsoft's published
  EGG format specification; **xunazo** by kippler / Bandisoft (zlib), ported to Kotlin for AZO, whose
  arithmetic coder comes from **Project Nayuki**'s Reference-arithmetic-coding (MIT).
- **AOSP update_engine**'s `update_metadata.proto` (Apache-2.0) — the payload.bin format.
- **XZ for Java** (public domain) and **Apache Commons Compress** (Apache-2.0) — LZMA / xz / bzip2.
- Test archives from EggDotNet, xunazo, patool, acefile-testdata and Sembiance's format samples were
  used to verify the decoders (not shipped).
