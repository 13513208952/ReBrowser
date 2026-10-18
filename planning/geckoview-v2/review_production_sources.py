#!/usr/bin/env python3
"""Verify per-file admission and source-bound HOST test evidence; does not run tests."""
import hashlib
import json
from pathlib import Path

P = Path(__file__).resolve().parent
ROOT = P.parents[1]


def review():
    admission = json.loads((P/'records/implementation-readiness.json').read_text())
    manifest = json.loads((P/'records/application-code-files.json').read_text())
    evidence = json.loads((P/'evidence/production-unit-tests.json').read_text())
    assert {'O41','O42'} <= set(admission['authority'])
    entries = {f['path']:f for f in admission['files']}
    assert len(entries) == len(admission['files'])
    implemented = {f['path'] for f in manifest['files'] if f['state'] != 'planned-not-implemented'}
    assert entries.keys() == implemented and manifest['implementationStarted'] == bool(implemented)
    actual_native = {str(p.relative_to(ROOT)) for p in (ROOT/'app/src/main/java/io/github/rebrowser').rglob('*')
                     if p.is_file() and p.suffix in ['.java','.kt']}
    assert actual_native == {p for p in implemented if p.endswith(('.java','.kt'))}, 'unlisted/mislabelled actual v2 sources'
    for item in entries.values():
        assert item['state'] == 'implemented-host-tested-not-integrated'
        assert item['researchCompleteForScope'] and not item['unresolvedWithinScope']
        assert item['scope'] and item['integrationGate'] and item['notClaimed']
        for ref in item['researchEvidence']:
            assert (P/ref).is_file()
    bound = {f['path'] for f in evidence['sourceFiles']}
    assert bound == implemented | {t for f in entries.values() for t in f['hostTestSources']}
    for source in evidence['sourceFiles']:
        assert hashlib.sha256((ROOT/source['path']).read_bytes()).hexdigest() == source['sha256']
    assert hashlib.sha256((P/'run_production_unit_tests.py').read_bytes()).hexdigest() == evidence['runnerSha256']
    assert evidence['runtimeSourceFilesCompiled'] == len(implemented)
    assert evidence['junitTestsPassed'] > 0 and evidence['temporaryClassDirectoryRemoved']
    assert not any(evidence[k] for k in ['androidRuntimeTested','productApkBuilt','realKeysUsedOrGenerated','strictArgsJsonOrAdmissionComplete'])
    return {'productionSourceFilesImplementedHostTested':len(implemented),
            'productionHostJunitTestsPassed':evidence['junitTestsPassed'],
            'productAndroidIntegrationComplete':False}


if __name__ == '__main__':
    print(json.dumps(review(), ensure_ascii=False, indent=2))
