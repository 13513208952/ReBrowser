#!/usr/bin/env python3
"""Losslessly optimize an UNSIGNED APK before zipalign/signing.

Already-stored entries stay stored (especially resources.arsc and images).
For DEFLATE entries choose the shortest original, zlib-9, or Zopfli stream.
Never change resource names, decoded bytes, or discard metadata. No ZIP64,
multidisk, encrypted archives, duplicate names, or signed inputs supported.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile
import zlib


def optimize(source, destination, iterations=100):
    import zopfli.zlib

    source, destination = Path(source), Path(destination)
    if source.resolve() == destination.resolve():
        raise ValueError("Use distinct input/output paths")
    original = source.read_bytes()
    records, report = [], []
    with zipfile.ZipFile(source) as archive:
        infos = archive.infolist()
        if len(infos) >= 65535 or len({i.filename for i in infos}) != len(infos):
            raise ValueError("ZIP64 or duplicate entries are unsupported")
        if archive.start_dir >= 24 and original[archive.start_dir - 16:archive.start_dir] == b"APK Sig Block 42":
            raise ValueError("Optimize before signing, not after")
        for info in infos:
            name = info.filename
            if name.upper().startswith("META-INF/") and name.upper().endswith((".RSA", ".DSA", ".EC", ".SF")):
                raise ValueError("Signed APK input")
            if info.flag_bits & 1 or info.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
                raise ValueError("Unsupported encryption or compression")
            if max(info.file_size, info.compress_size, info.header_offset) >= 0xFFFFFFFF:
                raise ValueError("ZIP64 unsupported")
            data = archive.read(info)
            offset = info.header_offset
            if original[offset:offset + 4] != b"PK\x03\x04":
                raise ValueError("Invalid local header")
            name_len, extra_len = struct.unpack_from("<HH", original, offset + 26)
            start = offset + 30 + name_len + extra_len
            compressed = original[start:start + info.compress_size]
            chosen = "stored" if info.compress_type == zipfile.ZIP_STORED else "original"
            if info.compress_type == zipfile.ZIP_DEFLATED:
                compressor = zlib.compressobj(9, zlib.DEFLATED, -15)
                candidates = [("original", compressed), ("zlib-9", compressor.compress(data) + compressor.flush()),
                              ("zopfli", zopfli.zlib.compress(data, numiterations=iterations)[2:-4])]
                chosen, compressed = min(candidates, key=lambda pair: len(pair[1]))
                if zlib.decompress(compressed, -15) != data:
                    raise ValueError("Compression verification failed")
            records.append((info, compressed))
            report.append({"name": name, "method": chosen, "raw_bytes": len(data),
                           "before": info.compress_size, "after": len(compressed),
                           "saved": info.compress_size - len(compressed),
                           "sha256": hashlib.sha256(data).hexdigest()})
        comment = archive.comment

    # Recreate ordinary ZIP records; old offset-dependent padding is removed.
    # zipalign adds the proper alignment after this operation, before signing.
    with destination.open("wb") as output:
        directory = []
        for info, compressed in records:
            offset = output.tell()
            flags = info.flag_bits & 0x800
            name = info.filename.encode("utf-8" if flags else "cp437")
            year, month, day, hour, minute, second = info.date_time
            date = ((year - 1980) << 9) | (month << 5) | day
            time = (hour << 11) | (minute << 5) | (second // 2)
            output.write(struct.pack("<IHHHHHIIIHH", 0x04034B50, 20, flags, info.compress_type,
                                     time, date, info.CRC, len(compressed), info.file_size, len(name), 0))
            output.write(name)
            output.write(compressed)
            directory.append((info, compressed, offset, flags, name, time, date))
        central_start = output.tell()
        for info, compressed, offset, flags, name, time, date in directory:
            output.write(struct.pack("<IHHHHHHIIIHHHHHII", 0x02014B50,
                                     (info.create_system << 8) | 20, 20, flags, info.compress_type,
                                     time, date, info.CRC, len(compressed), info.file_size,
                                     len(name), 0, len(info.comment), 0, info.internal_attr,
                                     info.external_attr, offset))
            output.write(name)
            output.write(info.comment)
        central_size = output.tell() - central_start
        if max(central_start, central_size) >= 0xFFFFFFFF:
            raise ValueError("ZIP64 unsupported")
        output.write(struct.pack("<IHHHHIIH", 0x06054B50, 0, 0, len(records), len(records),
                                 central_size, central_start, len(comment)))
        output.write(comment)
    with zipfile.ZipFile(destination) as check:
        assert check.namelist() == [row["name"] for row in report]
        for row in report:
            assert hashlib.sha256(check.read(row["name"])).hexdigest() == row["sha256"]
    return {"input_bytes": len(original), "output_bytes": destination.stat().st_size,
            "payload_saved": sum(row["saved"] for row in report),
            "iterations": iterations, "entries": report}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--iterations", type=int, default=100)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    if not 1 <= args.iterations <= 1000:
        parser.error("iterations must be 1..1000")
    result = optimize(args.input, args.output, args.iterations)
    args.report.write_text(json.dumps(result, indent=2) + "\n")
    print(f"Payload saved: {result['payload_saved']} bytes; unsigned output: {result['output_bytes']} bytes")


if __name__ == "__main__":
    main()
