#!/usr/bin/env python3
"""Compile/run ONLY the admitted v2 pure-Java slice with existing local JUnit jars.

No Gradle, download, Android/device access, APK assembly, key generation or release.
Supply existing JDK/JUnit/Hamcrest paths and an external non-tmpfs work directory.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

P = Path(__file__).resolve().parent
ROOT = P.parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(args):
    work = args.work_dir.resolve()
    assert work.is_dir() and not work.is_relative_to(ROOT)
    assert not work.is_relative_to(Path('/tmp')), 'use owned disk-backed temporary storage'
    free = shutil.disk_usage(work).free
    mem = int(next(line.split()[1] for line in Path('/proc/meminfo').read_text().splitlines()
                   if line.startswith('MemAvailable:'))) * 1024
    assert free >= 16 * 1024**2 + 1024**3 and mem >= 3 * 1024**3
    java, javac = (args.java_home / 'bin' / name for name in ['java', 'javac'])
    assert all(p.is_file() for p in [java, javac, args.junit, args.hamcrest])
    slices = json.loads((P / 'records/implementation-readiness.json').read_text())['files']
    sources = [ROOT / s['path'] for s in slices]
    tests = sorted({ROOT / test for s in slices for test in s['hostTestSources']})
    classes = sorted({name for s in slices for name in s['junitClasses']})
    classpath = os.pathsep.join(str(p.resolve()) for p in [args.junit, args.hamcrest])
    with tempfile.TemporaryDirectory(prefix='v2-admitted-java-', dir=work) as target:
        common = dict(cwd=ROOT, text=True, capture_output=True, timeout=45, check=True)
        compile_result = subprocess.run([str(javac), '-J-Xmx256m', '-J-Djava.io.tmpdir='+str(work),
            '--release', '8', '-encoding', 'UTF-8', '-cp', classpath, '-d', target,
            *map(str, sources + tests)], **common)
        result = subprocess.run([str(java), '-Xmx256m', '-Djava.io.tmpdir='+str(work),
            '-cp', target+os.pathsep+classpath, 'org.junit.runner.JUnitCore', *classes], **common)
    matched = re.search(r'^OK \((\d+) tests?\)$', result.stdout, re.M)
    assert matched, result.stdout
    report = {
        'scope': 'production v2 source, Java8 API surface compile and HOST JUnit only; not Android/runtime/admission implementation',
        'runtimeSourceFilesCompiled': len(sources), 'junitTestsPassed': int(matched.group(1)),
        'junitOutput': result.stdout, 'compilerDiagnostics': compile_result.stderr,
        'sourceFiles': [{'path': str(f.relative_to(ROOT)), 'sha256': digest(f)} for f in sources+tests],
        'runnerSha256': digest(Path(__file__)),
        'localDependencySha256': {'junit':digest(args.junit), 'hamcrest':digest(args.hamcrest)},
        'temporaryNewPeakBudgetBytes': 16 * 1024**2, 'freeBytesBefore': free, 'memAvailableBefore':mem,
        'temporaryClassDirectoryRemoved': True,
        'androidRuntimeTested': False, 'productApkBuilt': False, 'realKeysUsedOrGenerated':False,
        'strictArgsJsonOrAdmissionComplete': False
    }
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--junit', type=Path, required=True)
    parser.add_argument('--hamcrest', type=Path, required=True)
    parser.add_argument('--work-dir', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(run(args), ensure_ascii=False, indent=2))
