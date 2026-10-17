#!/bin/sh
# Rebuilds the document-viewer test fixtures in app/src/test/resources/docs from sample.md:
# pandoc writes the .docx / .odt / .epub / .rtf / .pptx, make_office.py the rest (and adds merged
# cells, colours and page breaks to pandoc's .docx / .odt). Needs pandoc 3 + python3.
#   scripts/docfixtures/make_fixtures.sh [<real LibreOffice .odt to scrub into libreoffice-interview.odt>]
set -eu
here=$(cd "$(dirname "$0")" && pwd)
out="$here/../../app/src/test/resources/docs"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$out"

python3 -I -c '
import struct, sys, zlib
raw = b"".join(b"\x00" + bytes((220, 30, 30)) * 8 for _ in range(8))
c = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
open(sys.argv[1], "wb").write(b"\x89PNG\r\n\x1a\n" + c(b"IHDR", struct.pack(">IIBBBBB", 8, 8, 8, 2, 0, 0, 0)) + c(b"IDAT", zlib.compress(raw)) + c(b"IEND", b""))
' "$work/red.png"
cp "$here/sample.md" "$work/sample.md"
cp "$here/sample.md" "$out/sample.md"
(
  cd "$work"
  pandoc sample.md -o pandoc.docx
  pandoc sample.md -o pandoc.odt
  pandoc sample.md -s -o "$out/sample.rtf"
  pandoc sample.md -t epub3 --split-level=1 -o "$out/sample.epub"
  pandoc sample.md -o "$out/sample.pptx"
)
python3 -I "$here/make_office.py" "$work" "$out" "$@"

# Hand-written RTF: code page 1252 hex escapes, \u Unicode with a fallback, \b0, \page, \line.
python3 -I -c '
import sys
open(sys.argv[1], "w", newline="\n").write(
    r"{\rtf1\ansi\ansicpg1252\deff0{\fonttbl{\f0 Arial;}}{\colortbl;\red192\green0\blue0;}" "\n"
    r"\pard\qc\b Caf\'"'"'e9 Title\b0\par" "\n"
    r"\pard Price: 5 \u8364? euro, {\cf1 red text}, {\i italic} and \b bold\b0 again.\line Second line.\par" "\n"
    r"\page\pard After the break.\par}" "\n")
' "$out/extras.rtf"
