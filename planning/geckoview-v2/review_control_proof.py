#!/usr/bin/env python3
"""Independent HOST encoder/boundary model. Optional existing OpenSSL verification.
No keys generated, no IPC/device access; public scalar1 fixture is not a credential.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile

P = Path(__file__).resolve().parent
ORDER = int('ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551', 16)


def b64decode(text):
    assert all(c in 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_' for c in text)
    raw = base64.urlsafe_b64decode(text + '=' * (-len(text) % 4))
    assert base64.urlsafe_b64encode(raw).decode().rstrip('=') == text
    return raw


def encode(spec, fields):
    result = spec['magicAscii'].encode('ascii') + struct.pack('>H', spec['formatVersionU16BE'])
    assert set(fields) == {f['name'] for f in spec['fields']}
    for f in spec['fields']:
        value = fields[f['name']]
        assert value or f.get('emptyAllowed')
        assert len(value) <= f.get('max', f.get('exact', 0))
        if 'exact' in f: assert len(value) == f['exact']
        if 'fixed' in f: assert value == f['fixed'].encode('ascii')
        if f['kind'] == 'ascii': assert all(33 <= b <= 126 for b in value)
        if f['kind'] == 'utf8': value.decode('utf-8', errors='strict')
        if f['kind'] == 'u63be': assert struct.unpack('>Q', value)[0] < 2**63
        result += struct.pack('>I', len(value)) + value
    assert len(result) <= spec['maxBytes']
    return result


def der(sig):
    assert 8 <= len(sig) <= 72 and sig[0] == 0x30 and sig[1] == len(sig) - 2
    at = 2; values = []
    for _ in range(2):
        assert at + 2 <= len(sig) and sig[at] == 2
        n = sig[at + 1]; at += 2
        assert 1 <= n <= 33 and at + n <= len(sig)
        v = sig[at:at+n]; at += n
        assert v[0] < 128 and not (n > 1 and v[0] == 0 and v[1] < 128)
        number = int.from_bytes(v, 'big'); assert 0 < number < ORDER
        values.append(number)
    assert at == len(sig)
    return values


def strict_json(text):
    def pairs(items):
        out = {}
        for k, v in items:
            assert k not in out, 'duplicate JSON key'
            out[k] = v
        return out
    result = json.loads(text, object_pairs_hook=pairs, parse_constant=lambda _: (_ for _ in ()).throw(AssertionError('nonfinite')))
    def unicode_check(value):
        if isinstance(value, str): value.encode('utf-8', errors='strict')
        elif isinstance(value, dict):
            for k, v in value.items(): unicode_check(k); unicode_check(v)
        elif isinstance(value, list):
            for v in value: unicode_check(v)
    unicode_check(result)
    return result


def review(openssl=False):
    spec = json.loads((P / 'records/control-proof-format.json').read_text())
    vector = json.loads((P / 'evidence/control-proof-host-vectors.json').read_text())
    fields = {k: bytes.fromhex(v) for k, v in vector['fieldHex'].items()}
    frame = encode(spec['frame'], fields)
    assert frame == b64decode(vector['frameB64'])
    assert hashlib.sha256(frame).hexdigest() == vector['frameSha256']
    a = der(b64decode(vector['signatureB64'])); b = der(b64decode(vector['alternateValidSignatureB64']))
    assert a[0] == b[0] and a[1] + b[1] == ORDER
    assert strict_json(fields['argsBytes'].decode()) == {'query': '合成', 'limit': 2}
    rejected = 0
    for malformed in ['{"x":1,"x":2}', '{"nested":{"x":1,"x":2}}', '{"n":NaN}', '{"n":Infinity}', '{"x":"\\ud800"}']:
        try: strict_json(malformed)
        except (AssertionError, ValueError): rejected += 1
    assert rejected == 5
    # Admission/idempotency are symbolic state, not implemented native authorization.
    nonce_used = set(); jobs = {}; checks = 0
    def admit(nonce, request, digest, valid=True):
        nonlocal checks
        if not valid: return 'BAD_PROOF'
        if nonce in nonce_used: return 'REPLAY'
        nonce_used.add(nonce)
        if request in jobs: return 'EXISTING_JOB' if jobs[request] == digest else 'ID_CONFLICT'
        jobs[request] = digest; return 'ONE_EXECUTION'
    assert admit('n1', 'r1', 'digest', False) == 'BAD_PROOF'; checks += 1
    assert not nonce_used; checks += 1
    assert admit('n1', 'r1', 'digest') == 'ONE_EXECUTION'; checks += 1
    assert admit('n1', 'r1', 'digest') == 'REPLAY'; checks += 1
    assert admit('n2', 'r1', 'digest') == 'EXISTING_JOB'; checks += 1
    assert admit('n3', 'r1', 'other') == 'ID_CONFLICT'; checks += 1
    assert len(jobs) == 1; checks += 1
    openssl_pass = False
    if openssl:
        executable = shutil.which('openssl'); assert executable, 'use existing OpenSSL, never auto-install'
        with tempfile.TemporaryDirectory(prefix='rbv-proof-vector-') as folder:
            w = Path(folder)
            spki = b64decode(vector['publicSpkiB64'])
            pem = b'-----BEGIN PUBLIC KEY-----\n' + base64.encodebytes(spki) + b'-----END PUBLIC KEY-----\n'
            (w/'public.pem').write_bytes(pem); (w/'frame.bin').write_bytes(frame)
            for key in ['signatureB64', 'alternateValidSignatureB64']:
                (w/'signature.der').write_bytes(b64decode(vector[key]))
                result = subprocess.run([executable, 'dgst', '-sha256', '-verify', str(w/'public.pem'), '-signature', str(w/'signature.der'), str(w/'frame.bin')], capture_output=True, text=True, timeout=10)
                assert result.returncode == 0 and 'Verified OK' in result.stdout
            (w/'frame.bin').write_bytes(frame[:-1] + bytes([frame[-1] ^ 1]))
            result = subprocess.run([executable, 'dgst', '-sha256', '-verify', str(w/'public.pem'), '-signature', str(w/'signature.der'), str(w/'frame.bin')], capture_output=True, text=True, timeout=10)
            assert result.returncode != 0
        openssl_pass = True
    return {'controlProofSignedFields': len(fields), 'controlProofHostJcaChecks': vector['checks'],
            'independentPythonFrameBytesMatch': True, 'strictJsonHostNegativeCases': rejected,
            'controlProofAdmissionSymbolicChecks': checks, 'independentOpenSslVerifiedThisRun': openssl_pass,
            'AndroidControlProofImplemented': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--openssl', action='store_true')
    print(json.dumps(review(parser.parse_args().openssl), indent=2))
