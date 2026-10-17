#!/usr/bin/env python3
"""Print the certificate SHA-256 of every signer in an APK's v2 / v3 / v3.1 signature blocks.

apksigner's `verify --print-certs` reports one summary signer (the newest scheme's), which hides what
an older Android version actually checks. This reads the APK Signing Block directly, one line per
signer: `v2 <sha256>`, `v3 <sha256> <minSdk> <maxSdk>`.
"""
import hashlib
import struct
import sys

SCHEMES = {0x7109871A: "v2", 0xF05368C0: "v3", 0x1B93AD61: "v3.1"}


def length_prefixed(buf, off):
    n = struct.unpack_from("<I", buf, off)[0]
    return buf[off + 4: off + 4 + n], off + 4 + n


def signers(apk_path):
    data = open(apk_path, "rb").read()
    eocd = data.rfind(b"PK\x05\x06")
    cd_offset = struct.unpack_from("<I", data, eocd + 16)[0]
    if data[cd_offset - 16: cd_offset] != b"APK Sig Block 42":
        raise SystemExit("no APK Signing Block")
    block_size = struct.unpack_from("<Q", data, cd_offset - 24)[0]
    pos = cd_offset - (block_size + 8) + 8
    end = cd_offset - 24
    while pos < end:
        pair_len = struct.unpack_from("<Q", data, pos)[0]
        pair_id = struct.unpack_from("<I", data, pos + 8)[0]
        value = data[pos + 12: pos + 8 + pair_len]
        pos += 8 + pair_len
        scheme = SCHEMES.get(pair_id)
        if scheme is None:
            continue
        signer_seq, _ = length_prefixed(value, 0)
        off = 0
        while off < len(signer_seq):
            signer, off = length_prefixed(signer_seq, off)
            signed_data, _ = length_prefixed(signer, 0)
            _digests, d = length_prefixed(signed_data, 0)
            certs, d = length_prefixed(signed_data, d)
            cert, _ = length_prefixed(certs, 0)
            digest = hashlib.sha256(cert).hexdigest()
            if scheme == "v2":
                yield scheme, digest, None, None
            else:
                min_sdk, max_sdk = struct.unpack_from("<II", signed_data, d)
                yield scheme, digest, min_sdk, max_sdk


if __name__ == "__main__":
    for scheme, digest, min_sdk, max_sdk in signers(sys.argv[1]):
        print(scheme, digest, *(x for x in (min_sdk, max_sdk) if x is not None))
