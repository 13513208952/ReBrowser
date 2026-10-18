#!/usr/bin/env python3
"""Run production streaming AEAD against pinned R9 public dummy-key reference only."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

P=Path(__file__).resolve().parents[1]
ROOT=P.parents[1]


def run(args):
    work=args.work_dir.resolve()
    assert work.is_dir() and not work.is_relative_to(ROOT) and not work.is_relative_to(Path('/tmp'))
    assert shutil.disk_usage(work).free>=16*1024**2+1024**3
    assert int(next(l.split()[1] for l in Path('/proc/meminfo').read_text().splitlines() if l.startswith('MemAvailable:')))*1024>=3*1024**3
    sources=[ROOT/'app/src/main/java/io/github/rebrowser/crypto/ObjectAead.java',
             P/'experiments/MailObjectFormatVectors.java',P/'experiments/ObjectAeadInterop.java']
    with tempfile.TemporaryDirectory(prefix='object-aead-interop-',dir=work) as out:
        common=dict(check=True,text=True,capture_output=True,timeout=45)
        subprocess.run([str(args.java_home/'bin/javac'),'-J-Xmx256m','-J-Djava.io.tmpdir='+str(work),
                        '--release','8','-encoding','UTF-8','-d',out,*map(str,sources)],**common)
        result=subprocess.run([str(args.java_home/'bin/java'),'-Xmx256m','-Djava.io.tmpdir='+str(work),
                               '-cp',out,'ObjectAeadInterop'],**common)
    baseline,cross=map(json.loads,result.stdout.splitlines())
    assert baseline['hostFixedCryptoAndFormatChecks']==64 and cross['oldAndProductionInteropChecks']==36
    return {'scope':'HOST R9 independent implementation versus actual production streaming AEAD; fixed PUBLIC dummy DEK only, same JCA provider, not Android crypto/real custody',
            'historicalRfcNistAndFormatChecksRerun':64,'bidirectionalAndExactWireChecks':36,
            'codecs':['RAW','ZLIB-packed'],'lengths':[0,1,32768,32769,65537,262144],
            'sourceFiles':[{'path':str(f.relative_to(ROOT)),'sha256':hashlib.sha256(f.read_bytes()).hexdigest()} for f in sources+[Path(__file__)]],
            'temporaryHarnessRemoved':True,'realKeyProvisioning':False,'androidProviderValidated':False,
            'databasePublicationOrSourceAdmissionTested':False,'uniqueRandomObjectIdsAreNotKeys':True}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home',type=Path,required=True)
    parser.add_argument('--work-dir',type=Path,required=True)
    print(json.dumps(run(parser.parse_args()),ensure_ascii=False,indent=2))
