#!/usr/bin/env python3
"""Read-only O64 minimum-work check. Code quantity never establishes runtime completion."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

P=Path(__file__).resolve().parent
ROOT=P.parents[1]

def review(base=None,contract=None):
    batch=json.loads((contract or P/'records/native-browser-window-batch-contract.json').read_text())
    args=['git','diff','--name-only','--diff-filter=ACMR']+([base] if base else ['--cached'])+['--','app/src/main/java/io/github/rebrowser']
    changed=set(subprocess.run(args,cwd=ROOT,text=True,capture_output=True,check=True).stdout.splitlines())
    runtime={s for s in changed if s.endswith(('.java','.kt')) and (ROOT/s).is_file()}
    minimum=batch['commitMinimum']['minimumMeaningfulRuntimeCodeFiles']
    assert minimum>=10 and len(runtime)>=minimum, 'O64: require >=10 meaningful runtime code files; tests/docs not counted'
    assert set(batch['runtimeCodeBatch'])<=runtime, 'missing reviewed source-batch member'
    manifest=json.loads((P/'records/application-code-files.json').read_text())
    assert runtime<={f['path'] for f in manifest['files']}, 'unlisted source must enter authoritative master tree'
    if base is None:
        for path in runtime:
            staged=subprocess.run(['git','show',':'+path],cwd=ROOT,capture_output=True,check=True).stdout
            assert staged==(ROOT/path).read_bytes(), 'staged source differs from reviewed working source: '+path
    return {'authority':'O64','minimumRuntimeCodeFiles':minimum,'runtimeCodeFilesChanged':len(runtime),'maximumFiles':None,'scope':'source workload only, not full responsibility/runtime completion','sourceFiles':[{'path':s,'sha256':hashlib.sha256((ROOT/s).read_bytes()).hexdigest()} for s in sorted(runtime)]}

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--base',help='review committed/un-staged delta from an existing base instead of staged files');parser.add_argument('--contract',type=Path,help='review a different native source-batch contract')
    args=parser.parse_args();print(json.dumps(review(args.base,args.contract),ensure_ascii=False,indent=2))
