#!/usr/bin/env python3
"""Auxiliary tree-generator tests; never part of application-code tree leaves."""
import copy
import json
from pathlib import Path
import unittest
from render_application_tree import render

P = Path(__file__).resolve().parent


class ApplicationTreeTests(unittest.TestCase):
    def setUp(self):
        self.manifest = json.loads((P / 'records/application-code-files.json').read_text())

    def rejects_path(self, path):
        self.manifest['files'][0]['path'] = path
        with self.assertRaises(AssertionError):
            render(self.manifest)

    def test_all_application_leaves_rendered(self):
        text, facts = render(self.manifest)
        self.assertEqual(facts['applicationRuntimeCodeLeaves'], len(self.manifest['files']))
        self.assertIn('## ↪ 测试需求', text)
        code_tree = text.split('```text\n', 1)[1].split('```', 1)[0]
        self.assertNotIn('ContractsTest.java', code_tree)
        self.assertNotIn('RuntimeTest.java', code_tree)
        for item in self.manifest['files']:
            self.assertIn(Path(item['path']).name + ' [', code_tree)

    def test_low_delegation_matches_exact_task_inventory(self):
        tasks = json.loads((P/'records/low-cost-agent-assignments.json').read_text())['tasks']
        assigned = {f['path']:f for f in self.manifest['files'] if f.get('delegation')}
        self.assertEqual(set(assigned), {t['runtimePath'] for t in tasks})
        self.assertEqual(len({t['id'] for t in tasks}), len(tasks))
        text, facts = render(self.manifest)
        self.assertEqual(facts['delegatedRuntimeTargetsIncludingPriorDelivery'], 48)
        self.assertEqual(facts['newD02DelegatedRuntimeFiles'], 42)
        for task in tasks:
            f = assigned[task['runtimePath']]
            if task['round']=='D03':
                d=f['activeDelegation'];self.assertEqual(d['taskId'],task['id']);self.assertEqual(d['status'],'reviewed-scoped-host-api');self.assertEqual(d['scope'],'slice')
                self.assertTrue(task['existingFileExtension']);self.assertFalse(task['countedAsNewRuntimeFile']);self.assertTrue(task['requiredBehavior'] and task['acceptance']);self.assertIn('[D03:'+task['id']+']',text)
                continue
            self.assertEqual(f['delegation']['taskId'], task['id'])
            if task['round']=='D02':
                self.assertEqual(task['status'],'review-held-layout-unapproved' if task['id'] in ['L47','L48'] else 'reviewed-scoped-host-api' if task['runtimePath'].endswith('.java') else 'reviewed-static-source-inactive')
                self.assertEqual(f['state']=='planned-not-implemented',task['id'] in ['L47','L48'])
            else:self.assertEqual(task['status'],'reviewed-scoped-host-api')
            d = f['delegation']; risk = f['riskAssessment']['fullFileRisk']
            tag = 'LOW-SLICE' if d['scope']=='slice' else 'LOW' if risk=='low' else 'LOW-EFFECT'
            self.assertIn('['+tag+':'+task['id']+']', text)
            self.assertTrue(task['scope'] and task['requiredBehavior'] and task['acceptance'])
            if task['testPath']:
                self.assertTrue(task['testPath'].startswith('app/src/test/'))
                group = next(g for g in self.manifest['groups'] if g['id']==f['testReferenceGroup'])
                self.assertIn(task['testPath'], [t['path'] for t in group['testReferences']])

    def test_delegation_is_not_implementation_progress(self):
        text, facts = render(self.manifest)
        for f in self.manifest['files']:
            if f.get('delegation'):
                self.assertIn(Path(f['path']).name+' [', text)
                if f['delegation']['round']=='D02':self.assertEqual(f['state']=='planned-not-implemented', f['delegation']['status']=='review-held-layout-unapproved')
        without = copy.deepcopy(self.manifest)
        for f in without['files']:
            f.pop('delegation', None)
        _, unchanged = render(without)
        for key in ['applicationRuntimeCodeLeaves','runtimeSourceFilesCreated','implementedRuntimeCodeFiles','apiCompiledRuntimeSourcesInProgress']:
            self.assertEqual(facts[key], unchanged[key])

    def test_unknown_delegation_difficulty_rejected(self):
        f = next(f for f in self.manifest['files'] if f.get('delegation'))
        f['delegation']['difficulty'] = 'already-complete'
        with self.assertRaises(AssertionError):
            render(self.manifest)

    def test_delegation_cannot_claim_runtime_passed(self):
        f = next(f for f in self.manifest['files'] if f.get('delegation'))
        f['delegation']['status'] = 'product-runtime-complete'
        with self.assertRaises(AssertionError):
            render(self.manifest)

    def test_entire_remaining_tree_has_full_responsibility_risk(self):
        remaining = {f['path']:f for f in self.manifest['files'] if f['state']=='planned-not-implemented'}
        record = json.loads((P/'records/remaining-file-risk-assessment.json').read_text())
        self.assertEqual(len(remaining),61)
        self.assertEqual(set(remaining), {f['path'] for f in record['files'] if f['state']=='planned-not-implemented'})
        self.assertEqual(len(record['files']),126) # preserve original allocation survey, don't silently rewrite history
        for f in record['files']:
            if f['path'] in remaining:self.assertEqual(f['fullFileRisk'],remaining[f['path']]['riskAssessment']['fullFileRisk'])
            self.assertTrue(f['riskReason'])
        self.assertEqual(record['coverage']['mainOnlyUnassignedFiles'],59)

    def test_new_round_42_does_not_recount_original_six(self):
        tasks = json.loads((P/'records/low-cost-agent-assignments.json').read_text())['tasks']
        old = {t['runtimePath'] for t in tasks if t['round']=='D01'}
        new = {t['runtimePath'] for t in tasks if t['round']=='D02'}
        self.assertEqual(len(old),6);self.assertEqual(len(new),42);self.assertFalse(old & new)
        self.assertEqual(sum(t['round']=='D02' and t['runtimePath'].endswith('.java') for t in tasks),36)
        self.assertEqual(sum(t['round']=='D02' and t['delegationScope']=='slice' for t in tasks),28)

    def test_high_risk_file_cannot_get_whole_low_risk_marker(self):
        text,_ = render(self.manifest)
        for f in self.manifest['files']:
            d=f.get('delegation')
            if d and f['riskAssessment']['fullFileRisk']=='high':
                self.assertEqual(d['scope'],'slice')
                self.assertIn('[LOW-SLICE:'+d['taskId']+']',text)
                self.assertNotIn('[LOW:'+d['taskId']+']',text)

    def test_d03_real_code_extensions_not_twenty_five_new_stubs(self):
        import hashlib
        record=json.loads((P/'records/low-cost-agent-assignments.json').read_text());tasks=[t for t in record['tasks'] if t['round']=='D03']
        self.assertEqual(len(tasks),25);self.assertEqual(len({t['runtimePath'] for t in tasks}),25)
        self.assertEqual(record['currentBatchNewRuntimeFiles'],0)
        self.assertEqual(record['currentBatchTaskIds'],[t['id'] for t in tasks])
        for t in tasks:
            raw=(P.parents[1]/t['runtimePath']).read_bytes();self.assertEqual(hashlib.sha256(raw).hexdigest(),t['mainReviewedRuntimeSha256'])
            import subprocess
            original=subprocess.check_output(['git','show',t['inputBaseCommit']+':'+t['runtimePath']],cwd=P.parents[1]);self.assertEqual(hashlib.sha256(original).hexdigest(),t['inputRuntimeSha256'])
            self.assertTrue(t['runtimePath'].endswith('.java'));self.assertFalse(t['fullResponsibilityCompleteClaim'])
        self.assertTrue(record['D03Rules']['existingD02ResultUntouched'])
        self.assertEqual(render(self.manifest)[1]['activeD03ExistingCodeExtensions'],0)
        self.assertEqual(render(self.manifest)[1]['reviewedD03ExistingCodeExtensions'],25)

    def test_main_core_not_delegated_for_count(self):
        for suffix in ['ActorContext.java','AuthorityPolicy.java','DataKeyVault.java','PrivateRecordSealer.java','PrivateRecordStore.java','EpochErasureCoordinator.java','ControlDispatcher.java','PermissionCoordinator.java','BrowserStore.java','BrowserCatalogCodec.java','BrowserStoreCoordinator.java','MailDatabase.java','SourceWarningActivity.java']:
            f=next(f for f in self.manifest['files'] if f['path'].endswith('/'+suffix))
            self.assertNotIn('delegation',f)
            self.assertEqual(f['riskAssessment']['owner'],'main')

    def test_original_d02_sha_bound_inputs_survive_runtime_admission(self):
        import base64,hashlib,zlib
        original=json.loads((P/'evidence/delegation-D02-static-checks.json').read_text())
        saved=json.loads((P/'evidence/delegation-D02-original-input-snapshots.json').read_text())
        snapshots={s['path']:s for s in saved['snapshots']}
        for source in original['sourceFiles']:
            snap=snapshots[source['path']];raw=zlib.decompress(base64.b64decode(snap['zlibBase64']))
            self.assertEqual(hashlib.sha256(raw).hexdigest(),source['sha256'])
            self.assertEqual(len(raw),snap['utf8Bytes'])

    def test_missing_risk_assessment_for_remaining_target_rejected(self):
        f=next(f for f in self.manifest['files'] if f['state']=='planned-not-implemented')
        f.pop('riskAssessment')
        with self.assertRaises(AssertionError):render(self.manifest)

    def test_host_marker_matches_implemented_count(self):
        text, facts = render(self.manifest)
        expected = sum(f['state'] == 'implemented-host-tested-not-integrated' for f in self.manifest['files'])
        self.assertEqual(facts['implementedRuntimeCodeFiles'], expected)
        self.assertEqual(text.count('[HOST][?]'), expected)
        wip = sum(f['state'] == 'source-in-progress-api-compiled' for f in self.manifest['files'])
        self.assertEqual(text.count('[API/WIP][?]'), wip)
        asset=sum(f['state']=='source-in-progress-static-tested' for f in self.manifest['files'])
        self.assertEqual(text.count('[ASSET/WIP][?]'),asset)
        self.assertEqual(facts['runtimeSourceFilesCreated'], expected + wip+asset)

    def test_reject_unknown_implementation_state(self):
        self.manifest['files'][0]['state'] = 'complete-Android-passed'
        with self.assertRaises(AssertionError):
            render(self.manifest)

    def test_reject_implemented_file_without_admission(self):
        item = next(f for f in self.manifest['files'] if f['state'] != 'planned-not-implemented')
        del item['implementationReadinessRef']
        with self.assertRaises(AssertionError):
            render(self.manifest)

    def test_reject_test_source_in_main_tree(self):
        self.rejects_path('app/src/test/java/io/github/rebrowser/Fixture.java')

    def test_reject_instrumentation_in_main_tree(self):
        self.rejects_path('app/src/androidTest/java/io/github/rebrowser/Fixture.java')

    def test_reject_tool_code_in_main_tree(self):
        self.rejects_path('tools/admin/helper.py')

    def test_reject_build_file_as_code_leaf(self):
        self.rejects_path('app/src/main/build.gradle')

    def test_reject_parent_traversal(self):
        self.rejects_path('app/src/main/../test/Fixture.java')

    def test_reject_noncanonical_double_slash(self):
        self.rejects_path('app/src/main//java/Fixture.java')

    def test_reject_wildcard_as_files(self):
        self.rejects_path('app/src/main/java/*.java')

    def test_reject_ellipsis_as_files(self):
        self.rejects_path('app/src/main/java/....java')

    def test_reject_duplicate_target(self):
        self.manifest['files'].append(copy.deepcopy(self.manifest['files'][0]))
        with self.assertRaises(AssertionError):
            render(self.manifest)

    def test_reject_auxiliary_role(self):
        self.manifest['files'][0]['kind'] = 'auxiliary-test-code'
        with self.assertRaises(AssertionError):
            render(self.manifest)

    def test_reject_missing_test_reference_group(self):
        self.manifest['files'][0]['testReferenceGroup'] = 'not-present'
        with self.assertRaises(AssertionError):
            render(self.manifest)

    def test_runtime_scripts_styles_html_sql_are_not_omitted(self):
        paths = [f['path'] for f in self.manifest['files']]
        for suffix in ['.js', '.css', '.html', '.sql']:
            self.assertTrue(any(x.endswith(suffix) for x in paths))

    def test_deferred_subapp_storage_not_current_runtime_dependency(self):
        paths = [f['path'] for f in self.manifest['files']]
        self.assertFalse(any('StorageEstimate' in x or 'SubappCleanup' in x for x in paths))
        self.assertTrue(any('EpochErasureCoordinator' in x for x in paths))
        self.assertTrue(any('MailReferenceReconciler' in x for x in paths))


if __name__ == '__main__':
    unittest.main()
