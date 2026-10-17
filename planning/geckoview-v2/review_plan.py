#!/usr/bin/env python3
"""Offline planning consistency review only. Does not build, contact devices, generate keys or test Gecko."""
import base64
import csv
import hashlib
import json
from pathlib import Path
import re
from review_runtime_design import review as review_runtime_timeline

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
        effective = min(granted, int(case['proof_granted_ceiling']), int(case['session_ceiling']))
        actual = 'allow' if scoped and effective >= required and case['authenticated'] == 'yes' else 'deny-or-wait'
        assert actual == case['expected'], (case['id'], actual)
    # Immutable high-trust admission and strict disable relationship are NOT the >= capability comparison.
    principal_cases = rows('matrices/principal-policy-cases.csv')
    for case in principal_cases:
        op = case['operation']
        if op == 'add-high':
            actual = 'allow-admission-path' if case['delivery'] == 'signed-package-update' else 'deny'
        elif op == 'disable':
            actual = 'allow' if case['target_role'] != 'root' and int(case['actor_level']) > int(case['target_level']) else 'deny'
        elif op == 'delegate-low':
            actual = 'allow' if int(case['grant_level']) < int(case['high_boundary_H']) and int(case['grant_level']) < int(case['actor_level']) else 'deny'
        elif op == 'mutate-compiled-high':
            actual = 'deny'
        else:
            raise AssertionError('unknown principal example')
        assert actual == case['expected'], (case['id'], actual)
    for case in rows('matrices/root-continuity-cases.csv'):
        assert case['status'] == 'planned-not-run', 'No actual data-destruction test is authorized by a planning model'
    continuity = load('records/root-continuity.json')
    assert continuity['implementation'] == 'not-started'
    assert continuity['confirmed']['destroyOnlyOwnPrivateAppData']
    assert continuity['confirmed']['mismatchRequiresPersonalOwnerPermission']
    confirmation_cases = rows('matrices/confirmation-cases.csv')
    for c in confirmation_cases:
        if c['trusted_same'] == 'yes':
            actual = 'NORMAL_EXISTING'
        elif c['trust_click'] != 'yes':
            actual = 'SOURCE_WARNING_LOCKED'
        elif c['force1_click'] != 'yes':
            actual = 'FORCE1_WARNING_LOCKED'
        elif c['force2_click'] != 'yes':
            actual = 'FORCE2_WARNING_LOCKED'
        elif c['lock_at2'] not in ['secure', 'unset']:
            actual = 'DEVICE_LOCK_STATE_REQUIRED'
        elif c['lock_at2'] == 'secure' and c['auth_at2'] != 'fresh-success':
            actual = 'AUTH2_REQUIRED_LOCKED'
        elif c['force3_click'] != 'yes':
            actual = 'FORCE3_WARNING_LOCKED'
        elif c['lock_at3'] not in ['secure', 'unset']:
            actual = 'DEVICE_LOCK_STATE_REQUIRED'
        elif c['lock_at3'] == 'secure' and c['auth_at3'] != 'fresh-success':
            actual = 'AUTH3_REQUIRED_LOCKED'
        elif c['erasure_verified'] != 'yes':
            actual = 'ERASURE_PENDING_LOCKED'
        elif c['new_epoch_committed'] != 'yes':
            actual = 'COMMIT_NEW_EPOCH_LOCKED'
        else:
            actual = 'UNLOCK_NEW_EMPTY'
        assert actual == c['expected'], (c['id'], actual)
    source_cases = rows('matrices/source-continuity-cases.csv')
    for c in source_cases:
        trusted = (c['trusted_signer'], c['trusted_root'])
        candidate = (c['candidate_signer'], c['candidate_root'])
        # Deliberately never use last_seen as the authority, including in A->B->C recovery.
        if c['platform_update_allowed'] != 'yes':
            actual = 'PLATFORM_REJECT_PRESERVE'
        elif not all(trusted):
            actual = 'LOCK_RECOVERY_REQUIRED' if c['old_epoch_exists'] == 'yes' else 'FIRST_FRESH_SEED'
        elif trusted != candidate:
            actual = 'SOURCE_MISMATCH_LOCKED'
        elif c['old_epoch_destroyed'] == 'yes':
            actual = 'NORMAL_NEW_EMPTY_NO_OLD_RESCUE'
        else:
            actual = 'NORMAL_INHERIT_TRUSTED_EPOCH'
        assert actual == c['expected'], (c['id'], actual)
    assert [s['freshAndroidOwnerAuthentication'] for s in continuity['ownerExactWarningExamples']] == [False, False, True, True]
    assert continuity['confirmed']['businessUnlockOnlyAfterSuccessfulErasure']
    assert continuity['confirmationProtocol']['noUnlockBeforeErase']
    completeness = load('records/completeness.json')
    design_domains = indexed(completeness['domains'])
    for domain in design_domains.values():
        assert set(domain['requirements']) <= requirements.keys()
        assert domain['completionDeliverables'] and len(domain['requiredReviewAxes']) >= 6
        assert not domain['implementationReady'] or domain['designComplete']
        for artifact in domain['currentArtifactRefs']:
            assert (P / artifact).is_file(), (domain['id'], artifact)
    if completeness['overallDesignComplete']:
        assert all(d['designComplete'] for d in design_domains.values())
    backlog = rows('matrices/design-backlog.csv')
    assert {r['id'].removeprefix('DES-') for r in backlog} == design_domains.keys()
    for choice in load('records/engineering-choices.json')['choices']:
        assert (P / choice['artifact']).is_file()
        assert choice['revisitWhen'], 'engineering preference needs a withdrawal condition'
    integration = load('records/gecko-contracts.json')
    contracts = indexed(integration['contracts'])
    facts = indexed(load('evidence/upstream.json')['verifiedApiFacts'])
    for contract in contracts.values():
        assert contract['domain'] in design_domains
        assert set(contract['evidence']) <= facts.keys()
        assert contract['contract'] and contract['failureBoundary']
        assert contract['runtimeValidated'] is False, 'source review is not runtime validation'
    integration_cases = rows('matrices/gecko-contract-cases.csv')
    indexed(integration_cases)
    assert {case['contract'] for case in integration_cases} == contracts.keys()
    assert all(case['state'] == 'planned-not-run' and case['expected'] for case in integration_cases)
    assert integration['productCodeImplemented'] is False
    interfaces = indexed(load('records/domain-interfaces.json')['modules'])
    methods = set()
    for module in interfaces.values():
        assert module['domain'] in design_domains
        assert set(module['geckoContracts']) <= contracts.keys()
        for method in module['methods']:
            name = module['id'] + '.' + method['name']
            assert name not in methods
            methods.add(name)
            assert method['capabilityGroupRef'] in capabilities
            assert method['input'] and method['output'] and method['sideEffectBoundary']
    actions = indexed(load('records/action-semantics.json')['actions'])
    payloads = indexed(load('records/domain-payloads.json')['actions'])
    assert actions.keys() == payloads.keys()
    for action in actions.values():
        assert action['domainMethod'] in methods
        assert action['capabilityGroupRef'] in capabilities
        assert set(action.get('capabilityByTarget', {}).values()) <= capabilities.keys()
        assert action['geckoContract'] in contracts
    for name in ['music.pause', 'music.stop', 'download.cancel']:
        assert actions[name]['lane'] == 'safety'
        assert 'fresh-current' not in actions[name]['siteAuthRequirement']
    journeys = rows('matrices/user-journeys.csv')
    indexed(journeys)
    assert all(set(j['capabilityGroups'].split()) <= capabilities.keys() for j in journeys)
    assert all(j['state'] == 'design-scenario-not-run' for j in journeys)
    runtime_review = review_runtime_timeline()
    report = {
        **runtime_review,
        'domainInterfaceComponents': len(interfaces),
        'domainInterfaceMethodsDesigned': len(methods),
        'actionSemanticSlicesDesignedNotImplemented': len(actions),
        'userJourneysPlannedNotRun': len(journeys),
        'revision': requirement_record['revision'], 'result': 'structural-and-symbolic-review-passed',
        'authorityComparison': '>= (confirmed O13)',
        'ownerMessages': len(messages), 'requirements': len(requirements),
        'geckoIntegrationContractsSourceReviewed': len(contracts),
        'geckoIntegrationCasesPlannedNotRun': len(integration_cases),
        'geckoIntegrationRuntimeValidated': False,
        'designDomainsAudited': len(design_domains),
        'designDomainsComplete': sum(d['designComplete'] for d in design_domains.values()),
        'overallDesignComplete': completeness['overallDesignComplete'],
        'productImplementationReady': completeness['productImplementationReady'],
        'capabilityGroups': len(capabilities), 'acceptanceCasesPlannedNotRun': len(acceptance),
        'authorityExamplesSymbolicallyChecked': len(authority_cases),
        'compiledPrincipalExamplesSymbolicallyChecked': len(principal_cases),
        'rootContinuityCasesPlannedNotRun': len(rows('matrices/root-continuity-cases.csv')),
        'fourStepConfirmationExamplesSymbolicallyChecked': len(confirmation_cases),
        'trustedSourceChainExamplesSymbolicallyChecked': len(source_cases),
        'actualAndroidOwnerAuthenticationPerformed': False,
        'destructionCodeOrExecution': False,
        'mermaidBlocksStructurallyChecked': graph_count,
        'fullMermaidParserOrRenderingValidated': False,
        'productCodeImplementedOrBuilt': False, 'deviceOrPrivateKeyAccess': False,
        'openQuestions': sum(q['state'] != 'resolved' for q in questions.values()),
        'ownerAnswersRequiredNow': sum(q['state'] != 'resolved' and q.get('askOwnerNow', False) for q in questions.values()),
        'resolvedOwnerQuestions': sum(q['state'] == 'resolved' for q in questions.values()),
        'caveat': 'Hashes prevent subsequent edits; they do not independently prove original transcription. Diagram syntax/rendering and unresolved owner semantics require review. Numeric levels remain proposals; >= is owner-confirmed in O13.'
    }
    return report

if __name__ == '__main__':
    print(json.dumps(review(), ensure_ascii=False, indent=2))
