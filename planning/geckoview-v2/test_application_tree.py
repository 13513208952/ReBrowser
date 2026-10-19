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

    def test_host_marker_matches_implemented_count(self):
        text, facts = render(self.manifest)
        expected = sum(f['state'] == 'implemented-host-tested-not-integrated' for f in self.manifest['files'])
        self.assertEqual(facts['implementedRuntimeCodeFiles'], expected)
        self.assertEqual(text.count('[HOST][?]'), expected)
        wip = sum(f['state'] == 'source-in-progress-api-compiled' for f in self.manifest['files'])
        self.assertEqual(text.count('[API/WIP][?]'), wip)
        self.assertEqual(facts['runtimeSourceFilesCreated'], expected + wip)

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
