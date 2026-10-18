#!/usr/bin/env python3
"""HOST-only DTO and own-window lifecycle examples; never invokes Android/installer."""
import json
from pathlib import Path
import re

P = Path(__file__).resolve().parent


def validate(spec, action, args):
    definition = next(x for x in spec['actions'] if x['id'] == action)
    assert type(args) is dict
    assert set(definition['required']) <= args.keys()
    assert args.keys() <= set(definition['required']) | set(definition['optional'])
    for key, value in args.items():
        kind = (definition['required'] | definition['optional'])[key]
        if kind == 'safeInteger': assert type(value) is int and 0 <= value <= spec['safeInteger']['max']
        elif kind == 'permissionPurpose': assert type(value) is str and value in definition['permissionPurpose']
        else: assert type(value) is str and re.fullmatch(spec['refs'][kind], value)
    if action == 'host.openInstaller':
        assert ('apkSourceRef' in args) == ('expectedSourceRevision' in args)
    return definition


def review():
    spec = json.loads((P/'records/platform-action-contracts.json').read_text())
    window = 'win_' + 'a'*32; source = 'apk_' + 'b'*32
    count = 0
    for definition in spec['actions']:
        action = definition['id']; args = {'windowRef': window, 'expectedPresentationRevision': 2}
        if action == 'host.openPermissionSettings': args['purpose'] = 'location'
        validate(spec, action, args); count += 1
        for bad in [args | {'unexpected': True}, args | {'windowRef': '../private'},
                    args | {'expectedPresentationRevision': True}, args | {'expectedPresentationRevision': 2.0},
                    args | {'expectedPresentationRevision': -1}, args | {'expectedPresentationRevision': 2**53},
                    {k:v for k,v in args.items() if k!='windowRef'}]:
            try: validate(spec, action, bad)
            except AssertionError: count += 1
            else: raise AssertionError('invalid DTO accepted')
    good = {'windowRef':window, 'expectedPresentationRevision':2, 'apkSourceRef':source, 'expectedSourceRevision':1}
    validate(spec, 'host.openInstaller', good); count += 1
    for bad in [good | {'uri':'content://arbitrary'}, {k:v for k,v in good.items() if k!='expectedSourceRevision'},
                good | {'apkSourceRef':'/data/private'}, good | {'component':'other.app.Activity'}]:
        try: validate(spec, 'host.openInstaller', bad)
        except AssertionError: count += 1
        else: raise AssertionError('unsafe install DTO accepted')
    # Symbolic idempotency/presentation fence: not actual UI automation.
    revision = 2; jobs = {}; back_count = 0
    def back(request, expected):
        nonlocal revision, back_count
        if request in jobs: return jobs[request]
        if expected != revision: return 'STALE'
        back_count += 1; revision += 1;jobs[request] = 'ONE_LAYER';return 'ONE_LAYER'
    assert back('r1',2) == 'ONE_LAYER'; count += 1
    assert back('r1',2) == 'ONE_LAYER' and back_count == 1; count += 1
    assert back('r2',2) == 'STALE' and back_count == 1; count += 1
    assert back('r2',3) == 'ONE_LAYER' and back_count == 2; count += 1
    assert all(x not in ['installed','permission-granted','default-changed'] for x in spec['PlatformFlowReceipt']['stage']);count += 1
    return {'concretePlatformActionContracts':len(spec['actions']), 'platformDtoAndLifecycleHostChecks':count,
            'AndroidPlatformFlowsExecuted':False}


if __name__ == '__main__':
    print(json.dumps(review(),indent=2))
