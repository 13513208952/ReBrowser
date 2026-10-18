#!/usr/bin/env python3
"""Offline adopted-policy model, NOT credential validation or product authorization."""
import json
from pathlib import Path
P = Path(__file__).resolve().parent


def load(name):
    return json.loads((P / name).read_text())


def decide(action, actor, *, grant=False, list_owner=None, channel='SIGNED_IPC',
           target_level=0, target_root=False, issuer_id=None, issuer_level=0):
    scale = load('records/authority-scale.json')
    if not actor.get('proof', True) or actor.get('disabled', False):
        return 'IDENTITY_REJECTED'
    level = actor['level']
    if level >= scale['highTrustBoundary'] and not actor.get('compiled', False):
        return 'HIGH_IDENTITY_NOT_COMPILED'
    list_object = 'whitelist' in action['targetSchema'] or action['targetSchema'] in ['own-highest-domain-only', 'root-exclusive-domain']
    if list_object and list_owner is not None and actor['role'] != 'root' and actor['id'] != list_owner:
        return 'SCOPE_DENIED'
    base = action['baseLevel']
    if base == 'grant.level+1':
        if not 0 <= target_level < scale['highTrustBoundary']:
            return 'HIGH_DELEGATION_REJECTED'
        base = target_level + 1
    elif base == 'target.level+1':
        if target_root:
            return 'ROOT_NOT_DISABLEABLE'
        base = target_level + 1
    elif isinstance(base, str):
        base = target_level + 1 if actor['id'] == issuer_id else issuer_level + 1
    required = max(base, scale['channelFloors'][channel], scale['interventionFloors'][action['intervention']])
    # Whitelist owner is applicable only after the ownership check above. It
    # stands for a verified target possession match, not a public-key string.
    exemption = list_owner is not None and (actor['role'] == 'root' or
                 (actor['role'] == 'highest' and actor['id'] == list_owner))
    effective = level
    if action['risk'] == 'highest' and not (exemption or grant):
        effective = min(effective, required - 1)
    return 'ALLOW' if effective >= required else ('WAIT_OWNER_OR_LEVEL' if action['risk'] == 'highest' else 'LEVEL_INSUFFICIENT')


def review():
    scale = load('records/authority-scale.json')
    actions = load('records/action-registry.json')['actions']
    assert scale['highTrustBoundary'] == 5 and scale['dynamicMaximum'] == 4
    assert scale['rootLevel'] == 10 > scale['highestLevel'] == 9
    assert len({a['id'] for a in actions}) == len(actions)
    by_id = {a['id']: a for a in actions}
    monotone = 0
    for action in actions:
        if isinstance(action['baseLevel'], int):
            if action['returnPrivacy'] not in ['non-sensitive']:
                assert action['baseLevel'] >= 5, action['id']
            allowed = False
            for level in range(11):
                actor = {'id': 'test', 'level': level, 'compiled': level >= 5,
                         'role': 'root' if level == 10 else 'highest' if level == 9 else 'admin'}
                result = decide(action, actor, grant=True)
                if allowed:
                    assert result == 'ALLOW', ('nonmonotone', action['id'], level)
                if result == 'ALLOW':
                    allowed = True
                monotone += 1
            assert allowed, ('Root cannot execute ordinary action with valid grant/scope', action['id'])
    root = {'id': 'root', 'level': 10, 'compiled': True, 'role': 'root'}
    a = {'id': 'A', 'level': 9, 'compiled': True, 'role': 'highest'}
    b = {'id': 'B', 'level': 9, 'compiled': True, 'role': 'highest'}
    act = by_id['engine.executeRendererScript']
    examples = [
        (decide(act, root), 'WAIT_OWNER_OR_LEVEL'),
        (decide(act, root, grant=True), 'ALLOW'),
        (decide(act, root, list_owner='A'), 'ALLOW'),
        (decide(act, a, list_owner='A'), 'ALLOW'),
        (decide(act, b, list_owner='A'), 'WAIT_OWNER_OR_LEVEL'),
        (decide(act, b, list_owner='A', grant=True), 'ALLOW'),
        (decide(by_id['admin.ownWhitelist.query'], b, list_owner='A', grant=True), 'SCOPE_DENIED'),
        (decide(by_id['admin.ownWhitelist.query'], a, list_owner='root'), 'SCOPE_DENIED'),
        (decide(by_id['admin.disable'], root, target_level=10, target_root=True), 'ROOT_NOT_DISABLEABLE'),
        (decide(by_id['admin.disable'], a, target_level=9), 'LEVEL_INSUFFICIENT'),
        (decide(by_id['admin.disable'], root, target_level=9), 'ALLOW'),
        (decide(by_id['admin.delegateLow'], root, target_level=5), 'HIGH_DELEGATION_REJECTED'),
        (decide(by_id['admin.delegateLow'], {'id':'low4','level':4,'compiled':False,'role':'admin'}, target_level=3), 'ALLOW'),
        (decide(by_id['browser.page.readDom'], {'id':'fake5','level':5,'compiled':False,'role':'admin'}), 'HIGH_IDENTITY_NOT_COMPILED'),
        (decide(by_id['admin.restore'], {'id':'issuer','level':8,'compiled':True,'role':'admin'}, target_level=6,issuer_id='issuer',issuer_level=8), 'ALLOW'),
        (decide(by_id['admin.restore'], {'id':'peer','level':8,'compiled':True,'role':'admin'}, target_level=6,issuer_id='issuer',issuer_level=8), 'LEVEL_INSUFFICIENT'),
        (decide(by_id['admin.restore'], a,target_level=6,issuer_id='issuer',issuer_level=8), 'ALLOW'),
    ]
    for actual, expected in examples:
        assert actual == expected, (actual, expected)
    # Caller program is intentionally not an input to decide().
    assert decide(by_id['music.pause'], {'id':'shared','level':3,'compiled':False,'role':'admin'}) == 'ALLOW'
    return {'adoptedActionPoliciesDesigned': len(actions),
            'adoptedScaleMonotonicComparisonsSymbolicallyChecked': monotone,
            'adoptedAuthorityBoundaryExamplesSymbolicallyChecked': len(examples) + 1,
            'credentialOrDeviceProofActuallyVerified': False}


if __name__ == '__main__':
    print(json.dumps(review(), ensure_ascii=False, indent=2))
