#!/usr/bin/env bash
# Re-signs an APK (in place) for BFE's signing-key rotation (APK Signature Scheme v3):
#   - v1 + v2 with the ORIGINAL key (CN=BFE Test, keystore/bfe-test.jks) — what Android 8.x checks;
#   - v3 with the RELEASE key (CN=Banners File Explorer) plus keystore/bfe-lineage.bin, for Android 9+
#     (--rotation-min-sdk-version 28). The lineage proves the original key handed over to the release
#     key, so installs signed with the original key update in place, and afterwards accept only the
#     release key (the original key has no rollback capability).
# The release key comes from repository secrets; without them the build fails on purpose, so an
# original-key-only APK can never be published after the rotation.
set -euo pipefail
APK="$1"
: "${BFE_RELEASE_KEYSTORE_B64:?missing secret BFE_RELEASE_KEYSTORE_B64 (release signing key)}"
: "${BFE_RELEASE_KEYSTORE_PASSWORD:?missing secret BFE_RELEASE_KEYSTORE_PASSWORD}"
ALIAS="${BFE_RELEASE_KEY_ALIAS:-bfe-release}"
export BFE_RELEASE_KEY_PASSWORD="${BFE_RELEASE_KEY_PASSWORD:-$BFE_RELEASE_KEYSTORE_PASSWORD}"
export BFE_OLD_STORE_PASS="${BFE_KEYSTORE_PASSWORD:-bfetest}"
export BFE_OLD_KEY_PASS="${BFE_KEY_PASSWORD:-bfetest}"
OLD_KS="${BFE_KEYSTORE:-keystore/bfe-test.jks}"
OLD_ALIAS="${BFE_KEY_ALIAS:-bfetest}"

# A fresh private temp file (never a fixed name, so nothing else can be overwritten or removed).
NEW_KS=$(mktemp "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/bfe-release-XXXXXX.p12")
trap 'rm -f "$NEW_KS"' EXIT
( umask 077; printf '%s' "$BFE_RELEASE_KEYSTORE_B64" | base64 -d > "$NEW_KS" )
APKSIGNER=$(ls "${ANDROID_SDK_ROOT:-$ANDROID_HOME}"/build-tools/*/apksigner | sort -V | tail -1)

"$APKSIGNER" sign \
  --ks "$OLD_KS" --ks-key-alias "$OLD_ALIAS" --ks-pass env:BFE_OLD_STORE_PASS --key-pass env:BFE_OLD_KEY_PASS \
  --next-signer \
  --ks "$NEW_KS" --ks-key-alias "$ALIAS" --ks-pass env:BFE_RELEASE_KEYSTORE_PASSWORD --key-pass env:BFE_RELEASE_KEY_PASSWORD \
  --lineage keystore/bfe-lineage.bin \
  --rotation-min-sdk-version 28 \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
  --out "$APK.rotated" "$APK"
mv "$APK.rotated" "$APK"
echo "re-signed with key rotation: $APK"
