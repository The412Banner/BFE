# BFE

**BFE** is a standalone, **sideload-only** Android file manager and archive extractor. It browses your
storage and unpacks archives, disc images and Windows game installers on-device using a bundled native
engine — 7-Zip (`7zz`), `innoextract` (GOG / InnoSetup) and FreeArc `unarc` (FitGirl/DODI-style repacks).

It is lifted from the in-app File Manager + archive-extraction subsystem of the Bannerlator app, itself
built on the Winlator lineage. BFE strips out all of the emulator machinery (Wine containers, the X
server, Steam/GOG store plumbing) and keeps only the browse-and-extract core.

## What it does

- Browse internal storage and SD cards; sort, copy, move, delete, rename, multi-select, favorites.
- **Dual-pane ("commander") split view** (toggle in the toolbar): two independent browsers side by
  side (wide screens) or stacked (portrait), with one-tap **Copy →/Move →** and extract-into-the-
  other-pane between them.
- Grid + compact views, search, Coil thumbnails and PE (`.exe`) icon extraction.
- Extract `.iso`/`.udf`/`.img`, `.7z`/`.zip`/`.rar` (incl. multi-part), split volumes, `.tar.*`, and more.
- One-tap **Fast Extract**, or a full **Unpack Archive** screen with a background foreground-service job,
  progress notification and an app-wide progress pill.
- Unpack **GOG / InnoSetup** installers with `innoextract`, including auto-batching sibling DLC setups.
- Decode **FreeArc / ISDone** game repacks natively with `unarc`.
- **Compress…** any selection into `.zip` / `.7z` (optional AES password) / `.tar` / `.tar.gz` /
  `.tar.xz` with the bundled 7-Zip, or `.tzst` and a **Winlator `.wcp`** content pack (tar.zst with a
  Bannerlator/Winlator-compatible `profile.json`) via streamed Java tar + zstd. Runs as a background
  service job with progress, cancel and a pill; targets this pane or the other one, including SAF/root.
- **APK cloner / editor / signer**: clone any `.apk` or installed app under a new package name with a
  proper rename (ARSCLib rewrites the binary manifest + `resources.arsc`; relative component names are
  made absolute, provider authorities and package-scoped permissions re-prefixed, `sharedUserId`
  dropped, `taskAffinity` followed), edit label / versions / icon / manifest scalars, zip-align and
  sign with apksig (v1+v2+v3) using a built-in persistent test key or your own PKCS12/BKS keystore, and
  optionally install the result. Split/bundle installs are merged first when ARSCLib can.
- Open an archive or a GOG `setup.exe` straight from any app's **Share / "Open with"** sheet.

## Requirements

- **arm64-v8a only.** The native extractors are prebuilt for `arm64-v8a`; there is no other ABI.
- **All-Files-Access (`MANAGE_EXTERNAL_STORAGE`).** The native processes write directly to the
  filesystem and cannot use the Storage Access Framework, so this permission is required for extraction.
  BFE asks for it on first run.
- minSdk 26 (Android 8.0), targetSdk 28.

## Builds & signing

CI (`.github/workflows/build.yml`) produces a single signed release APK artifact. Builds are signed
with a **bundled, publicly-known TEST key** (`keystore/bfe-test.jks`), using APK signature schemes
**v1 + v2 + v3**, so any BFE build installs over-the-top of a previous one without an uninstall. This is
a sideload test app — the test key is intentionally committed to the repo, as are the prebuilt native
libraries under `app/src/main/jniLibs/arm64-v8a/`. **Do not treat the signing key as private.**

## License

BFE is licensed under the **GNU General Public License v3.0** (see [`LICENSE`](LICENSE)), inherited from
its Winlator/Bannerlator lineage. Bundled third-party native components retain their own notices — see
`NOTICE_7ZIP.txt`, `NOTICE_INNOEXTRACT.txt`, `NOTICE_UNARC.txt` and `License_7zip.txt`.
