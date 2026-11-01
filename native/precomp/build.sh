#!/usr/bin/env bash
# Builds one precomp release from its source tree, decompress side included (precomp has one binary).
#   build.sh <precomp source dir> <output binary>
# Uses $CC / $CXX (and optional $LDFLAGS, e.g. -static) from the environment, so the same recipe
# builds with the Android NDK in CI and with a host compiler for local tests.
# v0.4.4 .. v0.4.6 ship plain Makefiles that hard-code gcc/g++ and -m64; their object lists are read
# from the top Makefile and each source is compiled here instead. Newer trees use CMake.
set -euo pipefail
SRC=$(cd "$1" && pwd)
OUT=$2
CC=${CC:-cc}
CXX=${CXX:-c++}
LDFLAGS=${LDFLAGS:-}
JOBS=${JOBS:-$(nproc)}
# Old C (implicit int/function declarations, assert without <assert.h>) needs these with modern clang/gcc.
# bionic lacks the legacy S_IREAD/S_IWRITE (owner read/write bits).
OLDC="-DS_IREAD=S_IRUSR -DS_IWRITE=S_IWUSR -include assert.h -Wno-implicit-function-declaration -Wno-int-conversion -Wno-implicit-int -w"

if [ -f "$SRC/CMakeLists.txt" ]; then
  # The CMakeLists overwrites CMAKE_C_FLAGS/CMAKE_CXX_FLAGS; keep the caller's flags in front.
  sed -i 's|set(CMAKE_C_FLAGS "-DLINUX|set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DLINUX|; s|set(CMAKE_CXX_FLAGS "-DUNIX")|set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DUNIX")|' "$SRC/CMakeLists.txt"
  B="$SRC/build-bfe"
  rm -rf "$B"
  cmake -S "$SRC" -B "$B" -DCMAKE_BUILD_TYPE=Release -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
    -DCMAKE_C_COMPILER="$CC" -DCMAKE_CXX_COMPILER="$CXX" \
    -DCMAKE_C_FLAGS="$OLDC" -DCMAKE_EXE_LINKER_FLAGS="$LDFLAGS" ${CMAKE_EXTRA:-}
  cmake --build "$B" -j"$JOBS"
  install -m 755 "$B/precomp" "$OUT"
  exit 0
fi

M="$SRC/Makefile"
# Make variable value (one line), e.g. var BZIP2_OBJ.
var() { sed -n "s/^$1[[:space:]]*=[[:space:]]*//p" "$M" | head -1; }
OBJDIR="$SRC/build-bfe"
rm -rf "$OBJDIR"; mkdir -p "$OBJDIR"
objs=()
compile_list() {
  local list=$1
  for o in $list; do
    local dir base src
    dir=$(dirname "$o"); base=$(basename "$o" .o)
    src=$(find "$SRC/$dir" -name "$base.c" -o -name "$base.cpp" | head -1)
    [ -n "$src" ] || { echo "no source for $o" >&2; exit 1; }
    local obj="$OBJDIR/${dir//\//_}_$base.o"
    case "$src" in
      *.cpp) echo "$CXX -c -O2 -DUNIX -DBUILD_LIB -D_FILE_OFFSET_BITS=64 -w $src -o $obj" ;;
      *liblzma*) echo "$CC -c -O2 -std=gnu99 -DHAVE__BOOL -DUNIX -D_FILE_OFFSET_BITS=64 $OLDC $(find "$SRC/contrib/liblzma" -mindepth 1 -maxdepth 1 -type d -printf '-I%p ') $src -o $obj" ;;
      *) echo "$CC -c -O2 -DLINUX -DUNIX -D_FILE_OFFSET_BITS=64 $OLDC $src -o $obj" ;;
    esac
    objs+=("$obj")
  done
}
: > "$OBJDIR/cmds"
# v0.4.5+: BZIP2_OBJ …; v0.4.4: OBJ_ZLIB … plus SRC_PACKJPG (sources, not objects).
for v in BZIP2_OBJ GIFLIB_OBJ PACKJPG_OBJ PACKMP3_OBJ ZLIB_OBJ LIBLZMA_OBJ OBJ_ZLIB OBJ_BZIP2 OBJ_GIFLIB; do
  l=$(var "$v"); [ -n "$l" ] && compile_list "$l" >> "$OBJDIR/cmds"
done
l=$(var SRC_PACKJPG); [ -n "$l" ] && compile_list "$(echo "$l" | sed 's/\.cpp/.o/g')" >> "$OBJDIR/cmds"
xargs -P "$JOBS" -I{} sh -c '{}' < "$OBJDIR/cmds"
extra=$(var LIBLZMA_CPP)
inc=""; [ -d "$SRC/contrib/liblzma" ] && inc=$(find "$SRC/contrib/liblzma" -mindepth 1 -maxdepth 1 -type d -printf '-I%p ')
# shellcheck disable=SC2086
# -DLINUX: v0.4.4 picks its Unix code with LINUX, later versions with UNIX.
"$CXX" -std=c++11 -DUNIX -DLINUX -DBIT64 -D_FILE_OFFSET_BITS=64 -O2 -pthread -w $inc \
  "${objs[@]}" ${extra:+$(for f in $extra; do echo "$SRC/$f"; done)} "$SRC/precomp.cpp" $LDFLAGS -o "$OUT"
