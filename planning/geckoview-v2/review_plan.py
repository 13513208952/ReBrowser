#!/usr/bin/env python3
"""Offline planning consistency review only. Does not build, contact devices, generate keys or test Gecko."""
import base64
import csv
import hashlib
import json
from pathlib import Path
import re

P = Path(__file__).resolve().parent

def load(name):
    return json.loads((P / name).read_text(encoding='utf-8'))

def rows(name):
    with (P / name).open(encoding='utf-8', newline='') as f:
        return list(csv.DictReader(f))

def indexed(items):
    result = {item['id']: item for item in items}
    assert len(result) == len(items), 'duplicate ids'
    return result

def review():
    manifest = load('owner-verbatim/manifest.json')
    messages = indexed(manifest['messages'])
    for message in messages.values():
        p = P / message['file']
        assert p.resolve().is_relative_to(P.resolve()) and not p.is_symlink()
        data = p.read_bytes()
        assert hashlib.sha256(data).hexdigest() == message['sha256'], message['id'] + ': changed owner quote'
        assert len(data) == message['utf8Bytes']
        assert data.decode('utf-8') and not data.endswith(b'\n'), 'unexpected quote wrapping/newline'
    assert {str(p.relative_to(P)) for p in (P / 'owner-verbatim').glob('O*.txt')} == {m['file'] for m in messages.values()}
    requirement_record = load('records/requirements.json')
    requirements = indexed(requirement_record['requirements'])
    for item in requirements.values():
        assert item['ownerSources'] and set(item['ownerSources']) <= messages.keys(), item['id']
        assert item['implementation'] == 'not-started', 'Planning must not pretend product implementation'
    decisions = indexed(load('records/decisions.json')['decisions'])
    questions = indexed(load('records/questions.json')['questions'])
    for group in [decisions, questions]:
        for item in group.values():
            assert set(item['requirements']) <= requirements.keys(), item['id']
    capabilities = indexed(rows('matrices/capabilities.csv'))
    acceptance = indexed(rows('matrices/acceptance.csv'))
    covered = set()
    for file in ['matrices/capabilities.csv', 'matrices/acceptance.csv', 'matrices/feature-parity.csv', 'matrices/risks.csv', 'matrices/transports.csv']:
        for item in rows(file):
            refs = set(item['requirements'].split())
            assert refs <= requirements.keys(), (file, item['id'])
            if file.endswith('acceptance.csv'):
                covered.update(refs)
                assert item['state'] == 'planned-not-run'
                assert not item['capability'] or item['capability'] in capabilities
    assert covered == requirements.keys(), 'requirements without acceptance coverage'
    for item in capabilities.values():
        assert item['coverage'] in acceptance and acceptance[item['coverage']]['capability'] == item['id']
        assert item['implementation'] == 'not-started'
    plan = load('records/work-plan.json')
    phases = indexed(plan['phases'])
    assert plan['activePhase'] == 'P0' and plan['codingAuthorizedByPlan'] is False
    done = set()
    def visit(name, active):
        assert name in phases and name not in active, 'unknown/cyclic phase'
        if name in done:
            return
        for parent in phases[name]['depends']:
            visit(parent, active | {name})
        done.add(name)
    for name in phases:
        visit(name, set())
    for item in acceptance.values():
        assert item['phase'] in phases
    for root in load('evidence/legacy-public-trust.json')['roots']:
        assert hashlib.sha256(base64.b64decode(root['publicSpkiBase64'])).hexdigest() == root['sha256Spki']
        assert root['privateCustody'].startswith('UNVERIFIED')
    graph_count = 0
    for p in P.rglob('*.md'):
        text = p.read_text(encoding='utf-8')
        assert text.count('```') % 2 == 0, str(p)
        blocks = re.findall(r'```mermaid\n(.*?)```', text, re.S)
        for block in blocks:
            assert block.lstrip().startswith(('flowchart ', 'mindmap\n', 'stateDiagram-v2\n', 'sequenceDiagram\n'))
        graph_count += len(blocks)
    assert graph_count >= 10, 'missing visual planning views'
    # Symbolic authority examples: NOT an implementation test or final approved level scale.
    authority_cases = rows('matrices/authority-cases.csv')
    for case in authority_cases:
        actor, list_owner = case['actor'], case['target_list_owner']
        own = actor == list_owner
        root = actor == 'root'
        scoped = case['kind'] == 'application' or root or own
        exempt = list_owner != 'none' and (root or own)
        consent_ok = case['risk'] != 'highest' or exempt or case['owner_grant'] == 'yes'
        identity = int(case['identity_ceiling'])
        required = int(case['required_threshold'])
        assert case['risk'] != 'highest' or required > 0, 'highest-risk threshold must permit an insufficient grant ceiling'
        granted = identity if consent_ok else min(identity, required - 1)
        effective = min(granted, int(case['credential_ceiling']), int(case['session_ceiling']))
        actual = 'allow' if scoped and effective >= required and case['authenticated'] == 'yes' else 'deny-or-wait'
        assert actual == case['expected'], (case['id'], actual)
    report = {
        'revision': requirement_record['revision'], 'result': 'structural-and-symbolic-review-passed',
        'authorityComparison': '>= (confirmed O13)',
        'ownerMessages': len(messages), 'requirements': len(requirements),
        'capabilityGroups': len(capabilities), 'acceptanceCasesPlannedNotRun': len(acceptance),
        'authorityExamplesSymbolicallyChecked': len(authority_cases),
        'mermaidBlocksStructurallyChecked': graph_count,
        'fullMermaidParserOrRenderingValidated': False,
        'productCodeImplementedOrBuilt': False, 'deviceOrPrivateKeyAccess': False,
        'openQuestions': sum(q['state'] != 'resolved' for q in questions.values()),
        'resolvedOwnerQuestions': sum(q['state'] == 'resolved' for q in questions.values()),
        'caveat': 'Hashes prevent subsequent edits; they do not independently prove original transcription. Diagram syntax/rendering and unresolved owner semantics require review. Numeric levels remain proposals; >= is owner-confirmed in O13.'
    }
    return report

if __name__ == '__main__':
    print(json.dumps(review(), ensure_ascii=False, indent=2))
