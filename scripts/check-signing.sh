#!/usr/bin/env bash
# Fails unless the APK carries BFE's rotated signature:
#   - v2 (what Android 8.x checks): the original key, CN=BFE Test;
#   - v3 for API 28+: the release key, CN=Banners File Explorer, with a lineage original → release.
# Installing over every earlier BFE and the in-app updater both depend on this.
set -euo pipefail
ORIGINAL="4693b885e7b260043fc35d0565794525302af5734b511a08011888ac772108fe"
RELEASE="3e5dceac8ce1730359d407354f6318f4b67f09d15093650b6dfc73566a682cfc"
APK="$1"
APKSIGNER=$(ls "${ANDROID_SDK_ROOT:-$ANDROID_HOME}"/build-tools/*/apksigner | sort -V | tail -1)
"$APKSIGNER" verify --min-sdk-version 26 "$APK"

# apksigner's --print-certs shows one summary signer, so read the signature blocks directly.
BLOCKS=$(python3 -I "$(dirname "$0")/sigblock.py" "$APK")
echo "$BLOCKS"
V2=$(printf '%s\n' "$BLOCKS" | awk '$1=="v2"{print $2}')
V3=$(printf '%s\n' "$BLOCKS" | awk '$1=="v3"{print $2" "$3}')
[ "$V2" = "$ORIGINAL" ] || { echo "::error::v2 signer is '$V2', expected the original key $ORIGINAL"; exit 1; }
[ "$V3" = "$RELEASE 28" ] || { echo "::error::v3 signer is '$V3', expected the release key $RELEASE from API 28"; exit 1; }

# The lineage inside the APK must hand the original key over to the release key.
LINEAGE=$("$APKSIGNER" lineage --in "$APK" --print-certs 2>/dev/null | grep -iE 'certificate SHA-256 digest' | awk '{print $NF}' | tr '\n' ' ')
[ "$LINEAGE" = "$ORIGINAL $RELEASE " ] || { echo "::error::unexpected signing lineage: $LINEAGE"; exit 1; }
echo "signature OK: v2 original key, v3 release key (API 28+), lineage original → release"
