#!/usr/bin/env python3
"""Verify per-file admission and source-bound HOST test evidence; does not run tests."""
import hashlib
import json
import subprocess
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
    jvm_entries = {p:f for p,f in entries.items() if f.get('validationMode','jvm') == 'jvm'}
    android_entries = {p:f for p,f in entries.items() if f.get('validationMode','jvm') == 'android-app'}
    assert set(jvm_entries) | set(android_entries) == implemented
    for item in entries.values():
        assert item['state'] in ['implemented-host-tested-not-integrated','source-in-progress-api-compiled']
        if item['state'] == 'source-in-progress-api-compiled':
            assert item['validationMode'] == 'android-app' and not item['fullResponsibilityComplete']
        assert item['researchCompleteForScope'] and not item['unresolvedWithinScope']
        assert item['scope'] and item['integrationGate'] and item['notClaimed']
        for ref in item['researchEvidence']:
            assert (P/ref).is_file()
    bound = {f['path'] for f in evidence['sourceFiles']}
    assert bound == set(jvm_entries) | {t for f in jvm_entries.values() for t in f['hostTestSources']}
    for source in evidence['sourceFiles']:
        assert hashlib.sha256((ROOT/source['path']).read_bytes()).hexdigest() == source['sha256']
    assert hashlib.sha256((P/'run_production_unit_tests.py').read_bytes()).hexdigest() == evidence['runnerSha256']
    assert evidence['runtimeSourceFilesCompiled'] == len(jvm_entries)
    assert evidence['junitTestsPassed'] > 0 and evidence['temporaryClassDirectoryRemoved']
    assert evidence['unverifiedJsonSyntaxSliceCompiled']
    assert not any(evidence[k] for k in ['androidRuntimeTested','productApkBuilt','realKeysUsedOrGenerated','actionSpecificArgsSchemasComplete','proofAdmissionComplete'])
    differential = json.loads((P/'evidence/control-json-differential.json').read_text())
    assert differential['casesPassed'] == 1033
    assert differential['validSyntaxTreesMatched'] == 1004 and differential['malformedCasesRejectedByBoth'] == 29
    for source in differential['sourceFiles']:
        assert hashlib.sha256((ROOT/source['path']).read_bytes()).hexdigest() == source['sha256']
    assert not any(differential[k] for k in ['validCryptographicProofsUsed','actionSpecificSchemasValidated','androidRuntimeExecuted','realKeysOrDeviceDataUsed'])
    object_crypto = json.loads((P/'evidence/object-aead-production-interop.json').read_text())
    assert object_crypto['historicalRfcNistAndFormatChecksRerun'] == 64
    assert object_crypto['bidirectionalAndExactWireChecks'] == 36 and object_crypto['temporaryHarnessRemoved']
    for source in object_crypto['sourceFiles']:
        assert hashlib.sha256((ROOT/source['path']).read_bytes()).hexdigest() == source['sha256']
    assert not any(object_crypto[k] for k in ['realKeyProvisioning','androidProviderValidated','databasePublicationOrSourceAdmissionTested'])
    api = json.loads((P/'evidence/production-android-api-compile.json').read_text())
    assert api['passed'] and api['bootClasspathSdk'] == '37.2'
    assert {s['path'] for s in api['sourceFiles']} == set(jvm_entries)
    for source in api['sourceFiles']:
        assert hashlib.sha256((ROOT/source['path']).read_bytes()).hexdigest() == source['sha256']
    assert all(c['classMajor'] == 52 for c in api['classFiles'])
    assert not any(api[k] for k in ['androidRuntimeExecuted','androidApi30RuntimeValidated','productApkBuilt','keystoreOrRealCredentialsAccessed'])
    app = json.loads((P/'evidence/production-main-app-compile.json').read_text())
    for source in app['sourceFiles']:
        assert hashlib.sha256((ROOT/source['path']).read_bytes()).hexdigest() == source['sha256']
    assert implemented <= {s['path'] for s in app['sourceFiles']}
    assert app['productionJavaSourceFiles'] == len(implemented)
    assert app['mergedManifestPackage'] == 'io.github.rebrowser.development'
    assert app['mergedApplicationClass'] == 'io.github.rebrowser.ReBrowserApplication'
    assert app['adminActivityExported'] == 'false'
    assert app['concreteActionArgumentSchemasImplemented'] == 4 and not app['allActionArgumentSchemasComplete']
    assert 'ActionSchemas.parse(proof)' in (ROOT/'app/src/main/java/io/github/rebrowser/entrypoints/AdminControlActivity.java').read_text()
    assert app['unitTestsPassed'] == evidence['junitTestsPassed'] + sum(app.get(k,0) for k in ['hostAndroidClassPureIdentityTests','hostAndroidClassNativeControlTests','hostAndroidClassNavigationPolicyTests','hostAndroidClassTaskCoordinatorTests','hostAndroidClassResourceBudgetTests'])
    ledger = json.loads((P/'evidence/admin-idempotency-host-sql.json').read_text())
    assert ledger['hostSqlChecks'] == 39 and ledger['temporaryDbAndHarnessRemoved']
    for source in ledger['sourceFiles']:
        assert hashlib.sha256((ROOT/source['path']).read_bytes()).hexdigest() == source['sha256']
    assert not ledger['androidSQLiteExecuted'] and not ledger['actualPolicyAndSqliteAtomicityTested']
    assert not ledger['realDataOrKeysUsed']
    tasks = json.loads((P/'evidence/task-journal-host-sql.json').read_text())
    assert tasks['hostSqlChecks'] == 235 and tasks['validStateTriples'] == 14
    assert tasks['transitionPairsTested'] == 196 and tasks['acceptedTransitionPairs'] == 45
    assert tasks['concurrentHostSqlWriters'] == 2 and tasks['temporaryDbAndHarnessRemoved']
    task_checkpoint = json.loads((P/app['taskJournalHistoricalSqlCheckpointRef']).read_text())
    assert task_checkpoint['sourceCommit'] == 'b87ab85e6ccca5f931b0eb162deb8a5fb3c59dbf' and task_checkpoint['report'] == tasks
    for source in tasks['sourceFiles']:
        historical = subprocess.run(['git','show',task_checkpoint['sourceCommit']+':'+source['path']],cwd=ROOT,capture_output=True,check=True).stdout
        assert hashlib.sha256(historical).hexdigest() == source['sha256']
    assert not any(tasks[k] for k in ['androidSQLiteMethodsExecuted','actualNativeCoordinatorAndPolicyAtomicityTested','realDataOrKeysUsed'])
    assert not app['geckoDelegateCallbacksExecuted']
    assert app['hostAndroidClassNavigationPolicyTests'] == 12 and app['navigationPolicyAndSdkDtoHostExecuted']
    assert not app['navigationDelegateCallbacksAndroidExecuted']
    standalone = json.loads((P/app['currentStandaloneEvidenceRef']).read_text())
    assert standalone['validationExecutor'] == app['validationExecutor'] == 'bounded-standalone-javac-junit'
    assert standalone['unitTestsPassed'] == app['unitTestsPassed'] and standalone['compiledMainClassFiles'] == app['compiledClassFiles']
    assert standalone['jdkJavaBaseNotAndroidBootclasspath'] and not standalone['currentIncrementAgpTasksExecuted']
    assert standalone['temporaryClassDirectoryRemoved'] and standalone['generatedBytesBeforeRemoval'] < 16*1024**2
    assert standalone['runnerSha256'] == hashlib.sha256((P/'run_bounded_main_jvm_checks.py').read_bytes()).hexdigest()
    for source in standalone['sourceFiles']:
        assert hashlib.sha256((ROOT/source['path']).read_bytes()).hexdigest() == source['sha256']
    compact = standalone.get('validationResourceProfile') == 'compact-192m'
    minimum_ram = 2048 if compact else 2304
    assert standalone['minimumMemAvailableMiB'] == minimum_ram
    assert standalone['jvmLimits'][:3] == (['-Xmx192m','-XX:MaxMetaspaceSize=96m','-XX:ReservedCodeCacheSize=48m'] if compact else ['-Xmx256m','-XX:MaxMetaspaceSize=128m','-XX:ReservedCodeCacheSize=64m'])
    for gate in ['resourceBeforeCompile','resourceBeforeTests']:
        assert standalone[gate]['memAvailableBytes'] >= minimum_ram*1024**2
        assert standalone[gate]['freeBytes'] >= 16*1024**2+1024**3
    changed_api = standalone['changedSourcesAndroidBootclasspathCompile']
    assert len(changed_api['sourceFiles']) == 5 and changed_api['classFiles'] and all(c['classMajor'] == 52 for c in changed_api['classFiles'])
    assert not changed_api['androidRuntimeExecuted']
    schema = json.loads((P/'evidence/task-journal-current-schema-equivalence.json').read_text())
    assert schema['actualCurrentCompiledSchemaExtracted'] and not schema['historical235SqlChecksRerun'] and not schema['androidSqliteMethodsExecuted']
    assert schema['currentCompiledDdlAndRecoverySha256'] == standalone['taskJournalCurrentCompiledSchemaSha256'] == tasks['ddlAndRecoverySha256']
    for source in schema['sourceFiles']:
        assert hashlib.sha256((ROOT/source['path']).read_bytes()).hexdigest() == source['sha256']
    assert not standalone['historical235SqlChecksRerun'] and not app['taskJournalNativeFenceAndroidExecuted']
    for name,count,flag in [('TaskCoordinator',37,'actualOwnedExecutorAndWorksetMethodsExecuted'),('ResourceBudgets',10,'actualCounterAndTrimPolicyMethodsExecuted')]:
        ref = 'task-coordinator-host-execution' if name == 'TaskCoordinator' else 'resource-budgets-host-execution'
        host = json.loads((P/('evidence/'+ref+'.json')).read_text())
        assert host['hostTestsPassed'] == count and host[flag] and host['temporaryOwnedClassFilesRemoved']
        assert next(t for t in app['unitTests'] if t['class'] == 'io.github.rebrowser.core.'+name+'Test')['tests'] == count
        for source in host['sourceFiles']:
            assert hashlib.sha256((ROOT/source['path']).read_bytes()).hexdigest() == source['sha256']
    assert not any(app[k] for k in ['taskCoordinatorAndroidSqliteAdapterExecuted','taskCoordinatorCommandDispatcherIntegrated','globalEpochQuiescenceEstablished','resourceBudgetsOwnAndroidMemoryFactoryExecuted'])
    agp_checkpoint = json.loads((P/app['lastFullAgpCheckpointRef']).read_text())
    assert agp_checkpoint['sourceCommit'] == 'b911bb6fc33a4f07a5160fa68d2a64968b795384'
    assert agp_checkpoint['report']['productionJavaSourceFiles'] == 39 and agp_checkpoint['report']['unitTestsPassed'] == 253
    assert agp_checkpoint['report']['mergedManifestSha256'] == app['mergedManifestSha256']
    assert app['responseStreamWorkerHostTests'] == 10 and not app['actualGeckoResponseStreamsExecuted']
    stream = json.loads((P/'records/response-stream-implementation-contract.json').read_text())
    assert not stream['sourceInspection']['productUpstreamModified']
    assert next(t for t in app['unitTests'] if t['class'] == 'io.github.rebrowser.download.ResponseStreamWorkerTest')['tests'] == 10
    assert app['lastGoodSourceHostFilesystemTests'] == 17
    assert next(t for t in app['unitTests'] if t['class'] == 'io.github.rebrowser.continuity.LastGoodSourceStoreTest')['tests'] == 17
    assert not any(app[k] for k in ['lastGoodSourceAndroidFilesystemExecuted','lastGoodSourcePowerLossOrProcessDeathExecuted','lastGoodSourceCoordinatorOrUnlockIntegrated'])
    assert app['continuityReducerHostTests'] == 24
    assert next(t for t in app['unitTests'] if t['class'] == 'io.github.rebrowser.continuity.ContinuityCoordinatorTest')['tests'] == 24
    assert app['continuityNativeOwnerBridgeHostExecuted'] and not app['continuityOwnerPromptOrConsentConsumeAndroidExecuted']
    owner_host = json.loads((P/'evidence/owner-auth-synthetic-platform-host.json').read_text())
    assert owner_host['hostTestsPassed'] == app['hostAndroidClassNativeControlTests'] == 28
    assert len(owner_host['testCases']) == 28 and owner_host['testPlatformInjectionConstructorPrivate']
    for source in owner_host['sourceFiles']:
        assert hashlib.sha256((ROOT/source['path']).read_bytes()).hexdigest() == source['sha256']
    assert owner_host['actualControllerAndPrivateTicketMethodsExecuted'] and owner_host['temporaryFixturesRemoved']
    assert not any(owner_host[k] for k in ['androidPlatformAdapterExecuted','ownAndroidSignerInspectionExecuted','realKeyguardBiometricOrOwnerPromptExecuted','realCredentialsOrDeviceDataUsed','erasureOrBusinessUnlockExecuted'])
    assert app['ownerBiometricPermissionDeclared'] and not app['ownerAuthenticationAndroidPlatformExecuted']
    assert not app['ownerAuthenticationPromptOrTicketConsumeExecuted'] and app['ownerAuthenticationControlFlowHostExecuted'] and app['ownerAuthenticationNativeTicketConsumeHostExecuted']
    assert app['offline'] and not any(app[k] for k in ['nativeMergeDexR8OrApkExecuted','androidRuntimeExecuted','formalKeysProvisioned','formalRootConfigured','commandAdmissionOrEffectsImplemented','oldApplicationOrDataModified'])
    legacy_build = (ROOT/'app/build.gradle.kts').read_text()
    assert 'getByName("main").java.exclude("io/github/rebrowser/**")' in legacy_build
    assert 'getByName("test").java.exclude("io/github/rebrowser/**")' in legacy_build
    return {'productionSourceFilesImplementedHostTested':len(jvm_entries),
            'productionRuntimeSourceFilesStarted':len(implemented),
            'productionAndroidBoundFilesInProgress':len(android_entries),
            'productionAndroidBoundFilesWithSyntheticHostControlTests':sum(f.get('hostControlFlowExecutedWithSyntheticPlatform',False) for f in android_entries.values()),
            'standaloneMainAppCompiled':True,
            'standaloneMainAppValidationExecutor':app['validationExecutor'],
            'currentIncrementAgpTasksExecuted':app['currentIncrementAgpTasksExecuted'],
            'lastFullAgpSourceCommit':agp_checkpoint['sourceCommit'],
            'standaloneMainAppHostTests':app['unitTestsPassed'],
            'concreteTypedActionSchemasImplemented':app['concreteActionArgumentSchemasImplemented'],
            'nativeIdempotencyDdlHostSqlChecks':ledger['hostSqlChecks'],
            'nativeTaskJournalHostSqlChecks':tasks['hostSqlChecks'],
            'nativeTaskTransitionPairs':tasks['transitionPairsTested'],
            'productionSourceAndroid37ApiCompiled':len(api['sourceFiles']),
            'productionHostJunitTestsPassed':evidence['junitTestsPassed'],
            'productionJsonDifferentialCasesPassed':differential['casesPassed'],
            'productionObjectAeadInteropChecksPassed':object_crypto['bidirectionalAndExactWireChecks'],
            'productAndroidIntegrationComplete':False}


if __name__ == '__main__':
    print(json.dumps(review(), ensure_ascii=False, indent=2))
