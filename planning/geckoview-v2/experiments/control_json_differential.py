#!/usr/bin/env python3
"""Independent Python JSON/BE framing versus production Java decoder; synthetic only."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import random
import shutil
import struct
import subprocess
import tempfile

P = Path(__file__).resolve().parents[1]
ROOT = P.parents[1]
SOURCES = [ROOT/'app/src/main/java/io/github/rebrowser'/f for f in [
    'control/ControlEnvelope.java', 'crypto/CryptoEncoding.java', 'control/ControlDecoder.java']]


class ObjectPairs(list):
    pass


class NumberToken(str):
    pass


def obj(pairs):
    if len({k for k, _ in pairs}) != len(pairs):
        raise ValueError('duplicate JSON key')
    return ObjectPairs(pairs)


def b64(value):
    return base64.urlsafe_b64encode(value).rstrip(b'=')


def events(value):
    if isinstance(value, ObjectPairs):
        return f'O {len(value)}\n' + ''.join('K '+b64(k.encode('utf-8',errors='strict')).decode()+'\n'+events(v) for k,v in value)
    if isinstance(value, list):
        return f'A {len(value)}\n' + ''.join(events(v) for v in value)
    if isinstance(value, NumberToken):
        return 'N '+value+'\n'
    if isinstance(value, str):
        return 'S '+b64(value.encode('utf-8',errors='strict')).decode()+'\n'
    if value is True:
        return 'T\n'
    if value is False:
        return 'F\n'
    assert value is None
    return 'Z\n'


def expected(raw):
    def nonfinite(_):
        raise ValueError('non-JSON number')
    try:
        parsed = json.loads(raw.decode('utf-8',errors='strict'), object_pairs_hook=obj,
                            parse_int=NumberToken, parse_float=NumberToken, parse_constant=nonfinite)
        if not isinstance(parsed,ObjectPairs):
            return 'REJECT'
        return b64(events(parsed).encode()).decode()
    except (ValueError, UnicodeError):
        return 'REJECT'


def wire(raw_args):
    # Independent Python encoding, not Java ControlEnvelope serialization.
    fields = [b'rebrowser.control/1',b'io.github.rebrowser',b'public-parser-dummy',
              bytes(32),bytes(16),bytes(32),bytes(32),struct.pack('>Q',0),bytes(16),
              bytes(32),struct.pack('>Q',0),bytes(16),b'mail.query',b'account_synthetic',
              b'',bytes(32),raw_args]
    frame = b'ReBrowser.Control.Proof\0'+struct.pack('>H',1)+b''.join(struct.pack('>I',len(v))+v for v in fields)
    assert len(raw_args)<=32768 and len(frame)<=35000
    envelope = json.dumps({'protocol':'rebrowser.control/1','frameB64':b64(frame).decode(),
                           'signatureB64':b64(bytes.fromhex('3006020101020101')).decode()},separators=(',',':')).encode()
    assert len(envelope)<=65536
    return b64(envelope).decode()


def cases():
    rng=random.Random(0x524232304A)
    keys=['a','b','合成','😀','é','é','\0','\\','"']
    leaves=[None,True,False,'','合成😀','\0\n\t','"\\/','é','é']
    def generate(depth):
        kind=rng.randrange(7) if depth<4 else rng.randrange(4)
        if kind==0:return rng.choice(leaves)
        if kind==1:return rng.randint(-(2**70),2**70)
        if kind==2:return rng.uniform(-1e20,1e20)
        if kind==3:return ''.join(rng.choice('abc合成😀é\\\n') for _ in range(rng.randrange(15)))
        if kind==4:return [generate(depth+1) for _ in range(rng.randrange(4))]
        return {k:generate(depth+1) for k in rng.sample(keys,rng.randrange(4))}
    result=[]
    for i in range(1000):
        value={k:generate(0) for k in rng.sample(keys,rng.randrange(5))}
        result.append(json.dumps(value,ensure_ascii=i%2==0,separators=(',',':')).encode('utf-8'))
    result += [s.encode() for s in [' { "n":-0,"e":1E+30,"f":1.00 } ', '{"n":1e999999999999999}',
        '{"q":"\\ud83d\\ude00"}', '{"q":"\\u0000"}',
        '{}{}','{"x":null,"x":1}','{"a":1,"\\u0061":2}', '{"x":[{"a":0,"a":1}]}',
        '{"q":"\\ud800"}','{"q":"\\udc00"}','{"q":"\\ud800\\u0041"}',
        '{"x":NaN}','{"x":Infinity}','{"x":01}','{"x":+1}','{"x":1.}','{"x":.1}',
        '{"x":1e}','{"x":1e+}','{"x":truefalse}','{"x":1,}','{"x":[1,]}','{/*x*/}',
        '[]','null','false','1','"object"','\ufeff{}','\u00a0{}']]
    result += [b'{"q":"\xff"}',b'{"q":"\xc0\xaf"}',b'{"q":"\xed\xa0\x80"}']
    return result


def run(args):
    work=args.work_dir.resolve()
    assert work.is_dir() and not work.is_relative_to(ROOT) and not work.is_relative_to(Path('/tmp'))
    assert shutil.disk_usage(work).free>=16*1024**2+1024**3
    assert int(next(l.split()[1] for l in Path('/proc/meminfo').read_text().splitlines() if l.startswith('MemAvailable:')))*1024>=3*1024**3
    raw=cases();want=[expected(r) for r in raw]
    with tempfile.TemporaryDirectory(prefix='control-json-cross-',dir=work) as out:
        common=dict(check=True,capture_output=True,text=True,timeout=45)
        subprocess.run([str(args.java_home/'bin/javac'),'-J-Xmx256m','-J-Djava.io.tmpdir='+str(work),
                        '--release','8','-encoding','UTF-8','-d',out,*map(str,SOURCES),
                        str(P/'experiments/ControlJsonDifferential.java')],**common)
        result=subprocess.run([str(args.java_home/'bin/java'),'-Xmx256m','-Djava.io.tmpdir='+str(work),
                               '-cp',out,'ControlJsonDifferential'],input='\n'.join(wire(r) for r in raw)+'\n',**common)
    actual=result.stdout.splitlines()
    assert len(actual)==len(want)
    for i,(a,b) in enumerate(zip(actual,want)):
        assert a==b, f'differential failure at synthetic case {i}'
    bound=SOURCES+[P/'experiments/ControlJsonDifferential.java',Path(__file__)]
    return {'scope':'independent Python JSON parsing and binary framing versus actual production Java decoder; synthetic syntax only, not valid signature/authority/Android',
            'casesPassed':len(want),'validSyntaxTreesMatched':sum(s!='REJECT' for s in want),
            'malformedCasesRejectedByBoth':sum(s=='REJECT' for s in want),
            'sourceFiles':[{'path':str(f.relative_to(ROOT)),'sha256':hashlib.sha256(f.read_bytes()).hexdigest()} for f in bound],
            'caseCorpusSha256':hashlib.sha256(b'\0'.join(raw)).hexdigest(),
            'temporaryHarnessRemoved':True,'validCryptographicProofsUsed':False,
            'actionSpecificSchemasValidated':False,'androidRuntimeExecuted':False,'realKeysOrDeviceDataUsed':False}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home',type=Path,required=True)
    parser.add_argument('--work-dir',type=Path,required=True)
    print(json.dumps(run(parser.parse_args()),ensure_ascii=False,indent=2))
