#!/usr/bin/env python3
"""Offline planning consistency review only. Does not build, contact devices, generate keys or test Gecko."""
import base64
import csv
import hashlib
import json
from pathlib import Path
import re
from review_runtime_design import review as review_runtime_timeline
from review_authority_design import review as review_authority_policy
from review_mail_schema import review as review_mail_sql
from render_application_tree import render as render_application_tree
from review_legacy_archive import review as review_legacy_archive
from review_control_proof import review as review_control_proof
from review_platform_actions import review as review_platform_actions
from review_permission_design import review as review_permission_design
from review_production_sources import review as review_production_sources

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
        assert item['implementation'] in ['not-started', 'partial-source'], 'No product completion claim'
        if item['implementation'] == 'partial-source':
            assert item.get('implementationEvidence') == 'records/implementation-readiness.json'
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
    phases = indexed(plan['phases'] + plan.get('parallelTracks', []))
    assert plan['activePhase'] in ['P0', 'V0', 'T0']
    assert plan['codingAuthorizedByPlan'] and not plan['productCodingStillUnauthorized']
    assert {'O41','O42'} <= set(plan['codingAuthority'])
    production_review = review_production_sources()
    if plan['activePhase'] in ['V0', 'T0']:
        assert plan['isolatedValidationCodeAuthorized'] and load('records/validation-scope.json')['authority'] == 'O26'
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
    codec_evidence = load('evidence/mail-codec-host-baseline.json')
    codec_design = load('records/mail-storage-design.json')
    assert codec_evidence['kind'] == 'synthetic-host-baseline-not-Android-benchmark'
    assert codec_evidence['experimentScriptSha256'] == hashlib.sha256((P / codec_evidence['experimentScript']).read_bytes()).hexdigest()
    assert not codec_evidence['newLibraryInstalled'] and not codec_evidence['productDependencyAdded']
    assert not codec_evidence['realMailboxOrDeviceRead'] and not codec_evidence['AndroidRuntimeTested']
    assert not codec_design['productImplemented'] and not codec_design['AndroidValidated']
    assert codec_design['objectPlacement']['smallStoredPayloadMaxBytes'] == 65536
    assert all(m['roundTripExact'] for m in codec_evidence['measurements'])
    assert {m['codec'] for m in codec_evidence['measurements']} >= {'deflate-6', 'deflate-9', 'gzip-9'}
    authority_review = review_authority_policy()
    sql_review = review_mail_sql()
    tree_text, tree_review = render_application_tree()
    assert (P / 'APPLICATION_CODE_TREE.md').read_text() == tree_text
    validation = load('records/technology-validation.json')
    for source in validation['sourceFiles']:
        assert hashlib.sha256((P / source['path']).read_bytes()).hexdigest() == source['sha256']
    package = load('evidence/validation-release-package-verification.json')
    assert package['applicationId'] == 'io.github.rebrowser.validation'
    assert not package['realKeysOrSigningUsed'] and not package['apkInstalled'] and not package['AndroidRuntimeExecuted']
    feature_map = indexed(load('records/feature-action-map.json')['features'])
    action_policy = indexed(load('records/action-registry.json')['actions'])
    app_paths = {f['path'] for f in load('records/application-code-files.json')['files']}
    routes = load('records/application-action-routing.json')['routes']
    assert {r['action'] for r in routes} == action_policy.keys()
    for route in routes:
        held = action_policy[route['action']].get('scheduling') == 'deferred-O26'
        assert (route['targetCodePath'] is None) == held
        if not held:
            assert route['targetCodePath'] in app_paths and route['entryPath'] in app_paths
    assert feature_map.keys() == indexed(rows('matrices/feature-parity.csv')).keys()
    for feature in feature_map.values():
        assert feature['actions'] and set(feature['actions']) <= action_policy.keys()
    android_path = P / 'evidence/validation-android-local-synthetic.json'
    android_executed = android_path.exists()
    if android_executed:
        android = load('evidence/validation-android-local-synthetic.json')
        assert android['authorization'] == 'O29'
        assert android['environment']['package'] == 'io.github.rebrowser.validation'
        assert android['artifact']['disposableSigner'] and not android['artifact']['formalOrLegacyKeysUsed']
        assert android['runtimeResults']['syntheticPass']
        assert not android['preservation']['credentialsEntered'] and not android['preservation']['playbackStarted']
        assert not android['preservation']['legacyDataAccessedOrCleared']
        assert android['preservation']['legacyPackageVersionUnchanged']
    abi_report = {}
    abi_path = P / 'evidence/validation-arm64-apk-static-check.json'
    if abi_path.exists():
        abi = load('evidence/validation-arm64-apk-static-check.json')
        policy = load('records/abi-packaging-policy.json')
        assert set(policy['formalRelease']['abis']) == {'arm64-v8a', 'armeabi-v7a', 'x86_64'}
        assert policy['formalRelease']['standaloneApkPerAbi'] and not policy['formalRelease']['universalApk']
        assert policy['phoneValidation']['abi'] == abi['abi'] == 'arm64-v8a'
        assert abi['onlyExpectedAbi'] and abi['nativeElfMatchesAbi'] and abi['nativeZipAlignment16KiB']
        assert abi['sha256'] == android['artifact']['sha256'] and abi['bytes'] == android['artifact']['bytes']
        abi_report = {'phoneTestApkVerifiedSingleArm64': True, 'formalThreeAbiSplitPolicyOnlyNotBuilt': True}
    platform_report = review_platform_actions()
    permission_report = review_permission_design()
    platform_contract = load('records/platform-action-contracts.json')
    for action in platform_contract['actions']:
        assert action['id'] in action_policy and action['domainMethod'] in methods
        assert action['targetCodePath'] in app_paths
        assert next(r['targetCodePath'] for r in routes if r['action'] == action['id']) == action['targetCodePath']
    proof_report = review_control_proof()
    cross = load('evidence/control-proof-cross-implementation.json')
    assert cross['independentOpenSslVerifiedThisRun'] and not cross['realKeysReadOrGenerated']
    for source in cross['sourceFiles']:
        assert hashlib.sha256((P / source['path']).read_bytes()).hexdigest() == source['sha256']
    early = load('evidence/arm64-early-native-filter.json')
    assert not early['officialAarModified'] and not early['APKRebuiltOrReinstalled']
    for stage in early['stages']:
        assert stage['abis'] == ['arm64-v8a'] and stage['files'] == 13
        assert stage['eachLibraryMatchesOfficialAar']
        assert sum(lib['bytes'] for lib in stage['libraries']) == stage['bytes']
    archive_review = review_legacy_archive()
    retirement_report = {}
    if (P / 'records/noncode-retirement-review.json').exists():
        retirement = load('records/noncode-retirement-review.json')
        assert retirement['authority'] == 'O36'
        assert not any(retirement[k] for k in ['legacyApplicationCodeTrashed', 'importantCredentialsTrashed', 'trashEmptied', 'sourceOrReleaseDocsDeleted'])
        assert {e['originalWorkspaceFile'] for e in retirement['entries']} == {'create_plan_records.py', 'create_plan_matrices.py'}
        for e in retirement['entries']:
            assert e['state'] == 'system-trash-verified-restorable' and len(e['sha256']) == 64
            assert e['restore'] and e['reason'] and e['bytes'] > 0
        for doc in retirement['expiredDocsReviewedRetained']:
            assert (P.parents[1] / doc['path']).is_file() and doc['reason']
        retirement_report = {'obsoleteNonApplicationUtilitiesInSystemTrash': len(retirement['entries']), 'oldDocumentsReviewedRetained': len(retirement['expiredDocsReviewedRetained']), 'trashEmptiedByAgent': False}
    storage_path = P / 'evidence/temporary-storage-recovery.json'
    storage_report = {}
    if storage_path.exists():
        storage = load('evidence/temporary-storage-recovery.json')
        assert storage['authority'] == 'O33'
        assert not storage['deviceActions'] and not storage['unrelatedCachesOrSourceRemoved']
        assert {r['path'] for r in storage['removed']} == {
            'validation/first-3abi-unsigned-release.apk',
            'validation/project/app/build/intermediates/merged_native_libs',
            'validation/project/app/build/intermediates/stripped_native_libs',
        }
        assert storage['reclaimedFreeBytes'] == storage['freeBytesAfter'] - storage['freeBytesBefore'] > 0
        storage_report = {'ownTemporaryFreeBytesRecovered': storage['reclaimedFreeBytes'], 'temporaryStorageAuthority': 'O33'}
    report = {
        **storage_report,
        **abi_report,
        **proof_report,
        **platform_report,
        **permission_report,
        'historicalIndependentOpenSslProofVerificationPassed': True,
        'phoneOnlyNativeMergeStripEarlyFilterChecked': True,
        **retirement_report,
        **runtime_review,
        **archive_review,
        **authority_review,
        **sql_review,
        **tree_review,
        **production_review,
        'isolatedUnsignedReleasePackageBuilt': True,
        'AndroidGeckoRuntimeExecuted': android_executed,
        'AndroidScope': 'one authorized local synthetic run; official sites/media/owner-auth not tested' if android_executed else 'not executed',
        'subapplicationStorageFeatureDeferredByOwner': True,
        'actionToApplicationFileRoutesChecked': len(routes),
        'deferredActionRoutes': sum(r['targetCodePath'] is None for r in routes),
        'hostSyntheticCodecMeasurements': len(codec_evidence['measurements']),
        'hostSyntheticSourceBytes': codec_evidence['totalSyntheticSourceBytes'],
        'androidCodecPerformanceTested': False,
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
        'productSourceImplementationStarted': bool(production_review['productionSourceFilesImplementedHostTested']),
        'productApkBuilt': False, 'deviceOrPrivateKeyAccess': False,
        'openQuestions': sum(not q['state'].startswith(('resolved', 'deferred')) for q in questions.values()),
        'ownerAnswersRequiredNow': sum(not q['state'].startswith(('resolved', 'deferred')) and q.get('askOwnerNow', False) for q in questions.values()),
        'resolvedOwnerQuestions': sum(q['state'] == 'resolved' for q in questions.values()),
        'resolvedByEngineeringDesign': sum(q['state'] == 'resolved-by-engineering-design' for q in questions.values()),
        'deferredQuestions': sum(q['state'].startswith('deferred') for q in questions.values()),
        'caveat': 'Owner >=/source-neutral/static-high/source-continuity rules remain confirmed. Numeric scale is delegated engineering design, not a new owner instruction. Host/compile/unsigned R8 package evidence is not Android runtime/site validation. R11 adds actual isolated-package local Native/ports/MAIN/context checks only; not product/site/media validation. Archive inventory is not semantic exhaustion approval. Main-tree PLAN files remain targets; HOST-marked admitted source files have source-bound JVM evidence only, not Android integration. Full Mermaid rendering not validated.'
    }
    return report

if __name__ == '__main__':
    print(json.dumps(review(), ensure_ascii=False, indent=2))
