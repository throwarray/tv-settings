#!/bin/sh
# Rebuilds remapd (Stickman's button daemon, MIT, Mohamed Nofal) from third_party/stickman/native
# and places it where Gradle packages it: app/src/main/jniLibs/armeabi-v7a/libremapd.so.
# Needs the armv7 cross compiler:  sudo apt install gcc-arm-linux-gnueabihf binutils-arm-linux-gnueabihf
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
SRC="$ROOT/third_party/stickman/native"
OUT="$ROOT/app/src/main/jniLibs/armeabi-v7a/libremapd.so"
CC=arm-linux-gnueabihf-gcc
STRIP=arm-linux-gnueabihf-strip

command -v "$CC" >/dev/null 2>&1 || { echo "missing $CC: install gcc-arm-linux-gnueabihf" >&2; exit 1; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# same flags as Stickman's build.sh: static, so it runs on the stick without its libc
"$CC" -O2 -static -w -o "$TMP/remapd" "$SRC/remapd.c" "$SRC/layers.c"
"$STRIP" "$TMP/remapd"

mkdir -p "$(dirname "$OUT")"
cp "$TMP/remapd" "$OUT"
chmod 755 "$OUT"

# sanity check: 32-bit ARM ELF, statically linked
if ! file "$OUT" | grep -q "ARM" ; then
  echo "built file is not an ARM binary" >&2
  exit 1
fi
echo "built $OUT ($(wc -c < "$OUT") bytes)"
file "$OUT"
