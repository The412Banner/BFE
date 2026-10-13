#!/usr/bin/env bash
# Fails unless the APK is signed with the one BFE key (CN=BFE Test, the key every release since 0.1
# has used). Installing over an existing BFE and the in-app updater both depend on this.
set -euo pipefail
EXPECTED="4693b885e7b260043fc35d0565794525302af5734b511a08011888ac772108fe"
APK="$1"
APKSIGNER=$(ls "${ANDROID_SDK_ROOT:-$ANDROID_HOME}"/build-tools/*/apksigner | sort -V | tail -1)
"$APKSIGNER" verify --min-sdk-version 21 "$APK"
# The label differs between apksigner versions ("Signer #1 certificate …" vs "Signer (minSdkVersion=…)
# certificate …"), so match any certificate SHA-256 line and take its last field.
CERTS=$("$APKSIGNER" verify --min-sdk-version 21 --print-certs "$APK" 2>/dev/null || true)
ACTUAL=$(printf '%s\n' "$CERTS" | grep -iE 'certificate SHA-256 digest' | head -1 | awk '{print $NF}')
# Every signer block (v1/v2/v3 and any rotation entries) must carry the same certificate.
OTHERS=$(printf '%s\n' "$CERTS" | grep -iE 'certificate SHA-256 digest' | awk '{print $NF}' | sort -u | grep -vx "$EXPECTED" || true)
[ -z "$OTHERS" ] || { echo "::error::unexpected certificate(s): $OTHERS"; exit 1; }
echo "signing certificate SHA-256: $ACTUAL"
[ -n "$ACTUAL" ] || { printf '%s\n' "$CERTS" | grep -v 'not protected by signature'; }
if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "::error::APK is signed with the wrong key (expected $EXPECTED)"
  exit 1
fi
echo "signing certificate matches the BFE key"
