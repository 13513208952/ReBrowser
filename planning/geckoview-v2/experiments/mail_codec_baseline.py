#!/usr/bin/env python3
"""Bounded SYNTHETIC HOST codec evidence, never real mail or Android performance.

Standard library Deflate/GZIP; optional existing HOST libzstd via ctypes. This
neither installs a library nor recommends shipping that native library in APKs.
Creates a NEW output directory, never removes existing files or reads mail.
"""
import argparse
import ctypes as C
import ctypes.util
import hashlib
import json
import platform
from pathlib import Path
import random
import resource
import sqlite3
import sys
import time
import zlib


class HostZstd:
    def __init__(self):
        name = ctypes.util.find_library('zstd')
        if not name:
            raise RuntimeError('host libzstd unavailable; optional comparison skipped')
        self.lib = C.CDLL(name)
        declarations = {
            'ZSTD_createCCtx': ([], C.c_void_p),
            'ZSTD_freeCCtx': ([C.c_void_p], C.c_size_t),
            'ZSTD_sizeof_CCtx': ([C.c_void_p], C.c_size_t),
            'ZSTD_compressBound': ([C.c_size_t], C.c_size_t),
            'ZSTD_compressCCtx': ([C.c_void_p, C.c_void_p, C.c_size_t, C.c_void_p, C.c_size_t, C.c_int], C.c_size_t),
            'ZSTD_decompress': ([C.c_void_p, C.c_size_t, C.c_void_p, C.c_size_t], C.c_size_t),
            'ZSTD_isError': ([C.c_size_t], C.c_uint),
            'ZSTD_getErrorName': ([C.c_size_t], C.c_char_p),
            'ZSTD_versionString': ([], C.c_char_p),
        }
        for name, (args, result) in declarations.items():
            fn = getattr(self.lib, name)
            fn.argtypes, fn.restype = args, result
        self.ctx = self.lib.ZSTD_createCCtx()
        if not self.ctx:
            raise MemoryError('host Zstd context allocation failed')
        self.workspace_peak = 0
        self.version = self.lib.ZSTD_versionString().decode('ascii')

    def checked(self, n):
        if self.lib.ZSTD_isError(n):
            raise RuntimeError(self.lib.ZSTD_getErrorName(n).decode('ascii'))
        return n

    def compress(self, data, level):
        src = C.create_string_buffer(data)
        out = C.create_string_buffer(self.lib.ZSTD_compressBound(len(data)))
        n = self.checked(self.lib.ZSTD_compressCCtx(self.ctx, out, len(out), src, len(data), level))
        self.workspace_peak = max(self.workspace_peak, self.lib.ZSTD_sizeof_CCtx(self.ctx))
        return out.raw[:n]

    def decompress(self, data, original_size):
        # Size comes from our own bounded synthetic source, never a remote header.
        assert original_size <= 512 * 1024
        src = C.create_string_buffer(data)
        out = C.create_string_buffer(original_size)
        n = self.checked(self.lib.ZSTD_decompress(out, original_size, src, len(data)))
        assert n == original_size
        return out.raw[:n]

    def close(self):
        if self.ctx:
            self.lib.ZSTD_freeCCtx(self.ctx)
            self.ctx = None


def corpus():
    rng = random.Random(20261018)
    words = ['会议', '附件', '项目', '通知', '安排', '材料', '结果', '说明', '进度', '计划',
             '邮件', '问题', '确认', '更新', 'document', 'review', 'schedule', 'report']
    def paragraph(n):
        return '<p>' + ' '.join(rng.choice(words) + str(rng.randrange(1000)) for _ in range(n)) + '</p>'
    base = '<html><head><style>p{line-height:1.5}blockquote{border-left:2px solid #aaa}</style></head><body>'
    samples = {}
    samples['rich-text-varied'] = [
        (base + ''.join(paragraph(100) for _ in range(12 + i % 12)) + '</body></html>').encode()
        for i in range(96)]
    samples['quoted-conversations'] = []
    thread = ''
    for i in range(96):
        thread = paragraph(70) + '<blockquote>' + thread + '</blockquote>'
        if len(thread.encode()) > 220 * 1024:
            thread = paragraph(70)
        samples['quoted-conversations'].append((base + thread + '</body></html>').encode())
    samples['low-repetition-html'] = [
        (base + ''.join('<p data-id="' + rng.randbytes(24).hex() + '">' + paragraph(20) + '</p>'
                        for _ in range(180)) + '</body></html>').encode()
        for _ in range(48)]
    samples['small-messages'] = [
        ('<html><body><p>示例通知 ' + str(i) + ': ' + ' '.join(rng.sample(words, 6)) +
         ' reference=' + rng.randbytes(12).hex() + '</p></body></html>').encode()
        for i in range(2000)]
    samples['incompressible-attachment-surrogate'] = [rng.randbytes(128 * 1024) for _ in range(16)]
    # Exact duplicates are explicit, not disguised as compression gains.
    samples['exact-duplicates'] = samples['rich-text-varied'][:24] * 4
    assert max(len(b) for records in samples.values() for b in records) <= 512 * 1024
    return samples


def sqlite_size(path, rows):
    with sqlite3.connect(path) as db:
        db.execute('PRAGMA page_size=4096')
        db.execute('CREATE TABLE objects (id INTEGER PRIMARY KEY, digest BLOB UNIQUE, codec INTEGER, original_length INTEGER, payload BLOB)')
        db.executemany('INSERT INTO objects VALUES (?,?,?,?,?)', rows)
        db.commit()
        count = db.execute('SELECT COUNT(*) FROM objects').fetchone()[0]
    return {'databaseBytes': path.stat().st_size, 'rows': count, 'codecTag': 'integer',
            'meaning': 'HOST SQLite minimal content rows only; not Android DB, headers/search/encryption/WAL cost'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-dir', required=True, type=Path)
    args = parser.parse_args()
    assert args.output_dir.is_absolute() and not args.output_dir.is_relative_to('/tmp')
    resource.setrlimit(resource.RLIMIT_AS, (384 * 1024 * 1024, 384 * 1024 * 1024))
    resource.setrlimit(resource.RLIMIT_CPU, (60, 60))
    args.output_dir.mkdir(parents=True, exist_ok=False)
    samples = corpus()
    codecs = [(f'deflate-{n}', lambda b, n=n: zlib.compress(b, n), zlib.decompress) for n in [6, 9]]
    codecs.append(('gzip-9', lambda b: zlib.compress(b, 9, wbits=31), lambda b: zlib.decompress(b, wbits=31)))
    zs = None
    optional_status = None
    try:
        zs = HostZstd()
        codecs += [(f'host-zstd-{n}', lambda b, n=n: zs.compress(b, n), None) for n in [3, 9, 19]]
    except (OSError, RuntimeError, AttributeError) as exc:
        optional_status = str(exc)
    results = []
    try:
        for name, encode, decode in codecs:
            for category, records in samples.items():
                source_bytes = sum(map(len, records))
                stored = []; write_s = read_s = 0.0
                for data in records:
                    start = time.perf_counter(); encoded = encode(data); write_s += time.perf_counter() - start
                    start = time.perf_counter()
                    recovered = zs.decompress(encoded, len(data)) if decode is None else decode(encoded)
                    read_s += time.perf_counter() - start
                    assert recovered == data
                    stored.append(encoded)
                payload = sum(map(len, stored))
                choose_min = sum(min(len(raw), len(comp)) for raw, comp in zip(records, stored))
                unique = {hashlib.sha256(raw).hexdigest(): comp for raw, comp in zip(records, stored)}
                results.append({'codec': name, 'category': category, 'records': len(records),
                                'sourceBytes': source_bytes, 'compressedPayloadBytes': payload,
                                'compressedFraction': round(payload / source_bytes, 6),
                                'chooseRawIfLargerPayloadBytes': choose_min,
                                'uniqueRecords': len(unique), 'deduplicatedCompressedPayloadBytes': sum(map(len, unique.values())),
                                'hostEncodeSeconds': round(write_s, 6), 'hostDecodeSeconds': round(read_s, 6),
                                'roundTripExact': True})
        small = samples['small-messages']
        def rows(compressed):
            for i, raw in enumerate(small):
                data = zlib.compress(raw, 9) if compressed else raw
                yield (i, hashlib.sha256(raw).digest(), 1 if compressed else 0, len(raw), data)
        packing = {'rawInline': sqlite_size(args.output_dir / 'synthetic-small-raw.sqlite', rows(False)),
                   'deflateInline': sqlite_size(args.output_dir / 'synthetic-small-deflate.sqlite', rows(True)),
                   'oneFilePerMessage4KiBModelBytes': len(small) * 4096,
                   'fileAllocationCaveat': '4KiB-per-small-file is a model, not measured Android filesystem allocation'}
        report = {'kind': 'synthetic-host-baseline-not-Android-benchmark', 'seed': 20261018,
                  'hostArchitecture': platform.machine(), 'python': platform.python_version(),
                  'zlib': zlib.ZLIB_RUNTIME_VERSION, 'optionalExistingHostZstd': zs.version if zs else optional_status,
                  'newLibraryInstalled': False, 'productDependencyAdded': False,
                  'realMailboxOrDeviceRead': False, 'AndroidRuntimeTested': False,
                  'sourceCorpusSha256': hashlib.sha256(b''.join(hashlib.sha256(b).digest() for r in samples.values() for b in r)).hexdigest(),
                  'totalSyntheticSourceBytes': sum(len(b) for r in samples.values() for b in r),
                  'measurements': results, 'smallObjectPacking': packing,
                  'hostZstdContextWorkspacePeakBytes': zs.workspace_peak if zs else None,
                  'processPeakRssKiB': resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,
                  'scope': 'Payload ratio+single host timing, one pass; not index/encryption/recovery/per-ABI performance or Android dependency approval'}
        (args.output_dir / 'results.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({k: report[k] for k in ['kind', 'totalSyntheticSourceBytes', 'optionalExistingHostZstd', 'smallObjectPacking', 'hostZstdContextWorkspacePeakBytes', 'processPeakRssKiB']}, ensure_ascii=False, indent=2))
    finally:
        if zs:
            zs.close()


if __name__ == '__main__':
    main()
