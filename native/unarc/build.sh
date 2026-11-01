#!/usr/bin/env bash
# Builds FreeArc 0.67's unarc (decompress-only) from the mirror/freearc tree, with BFE's patches applied
# by the caller (native/patches/unarc/series):
#   build.sh <freearc source dir> <output binary>
# Uses $CXX and optional $LDFLAGS (e.g. -static) from the environment.
# Codecs: LZMA, BCJ, Delta, REP, GRZip, Tornado, 4x4, External - the same set as the bundled
# libunarc.so. PPMD/LZP/Dict/MM/TTA are left out: their 0.67 code has known LP64 bugs.
set -euo pipefail
SRC=$(cd "$1" && pwd)
OUT=$2
CXX=${CXX:-c++}
LDFLAGS=${LDFLAGS:-}
SHIM=$(mktemp -d)/shim.h
# CUI.h prompts with gets(), which C11 removed and bionic doesn't declare.
printf '#include <stdio.h>\n#define gets(s) fgets((s), 256, stdin)\n' > "$SHIM"
cd "$SRC/Unarc"
# shellcheck disable=SC2086
"$CXX" -O2 -fno-exceptions -fno-rtti -w -Wno-c++11-narrowing -include "$SHIM" \
  -DFREEARC_UNIX -DFREEARC_INTEL_BYTE_ORDER -DFREEARC_64BIT -DFREEARC_DECOMPRESS_ONLY -D_NO_EXCEPTIONS -DUNARC \
  unarc.cpp ../Environment.cpp ../Compression/CompressionLibrary.cpp ../Compression/Common.cpp \
  ../Compression/LZMA2/C_LZMA.cpp ../Compression/LZMA2/C_BCJ.cpp ../Compression/Delta/C_Delta.cpp \
  ../Compression/REP/C_REP.cpp ../Compression/GRZip/C_GRZip.cpp ../Compression/Tornado/C_Tornado.cpp \
  ../Compression/4x4/C_4x4.cpp ../Compression/External/C_External.cpp \
  $LDFLAGS -o "$OUT"
