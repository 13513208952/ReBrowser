"""Offline maintenance checks against disposable fixtures; never modify application data."""
import contextlib
import hashlib
import importlib.util
import io
import tempfile
import unittest
from pathlib import Path
from unittest import mock

MODULE = Path(__file__).resolve().parents[1] / 'maintenance/check_repository.py'
spec = importlib.util.spec_from_file_location('repository_check', MODULE)
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)
clean_spec = importlib.util.spec_from_file_location('repository_clean', MODULE.with_name('clean_generated.py'))
cleaner = importlib.util.module_from_spec(clean_spec)
clean_spec.loader.exec_module(cleaner)


class RepositoryMaintenanceTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix='rebrowser-maintenance-')
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def write(self, name, text=''):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')
        return path

    def test_local_links_and_unicode_duplicate_anchors(self):
        self.write('README.md', '[page](docs/page%20one.md#中文标题-1)\n[folder](docs/)')
        self.write('docs/page one.md', '# 中文标题\n# 中文标题\n')
        self.assertEqual(checker.check_links(self.root, ['README.md']), [])

    def test_broken_path_and_anchor_are_errors(self):
        self.write('README.md', '[missing](absent.md)\n[heading](docs/page.md#absent)')
        self.write('docs/page.md', '# Present')
        errors = checker.check_links(self.root, ['README.md'])
        self.assertEqual(len(errors), 2)
        self.assertTrue(any('missing link' in e for e in errors))
        self.assertTrue(any('missing heading' in e for e in errors))

    def test_fenced_examples_inline_examples_and_external_links_ignored(self):
        self.write('README.md', '```md\n[example](missing.md)\n```\n'
                   '~~~markdown\n[example](missing.md)\n~~~\n'
                   '`[example](missing.md)`\n[remote](https://example.invalid/missing)')
        self.assertEqual(checker.check_links(self.root, ['README.md']), [])

    def test_angle_path_and_link_title(self):
        self.write('README.md', '[one](<docs/with space.md>)\n[two](docs/a.md "title")')
        self.write('docs/with space.md')
        self.write('docs/a.md')
        self.assertEqual(checker.check_links(self.root, ['README.md']), [])

    def test_outside_repository_and_symlink_target_rejected(self):
        self.write('README.md', '[outside](../private.md)\n[symlink](outside.md)')
        (self.root / 'outside.md').symlink_to(self.root.parent / 'private.md')
        errors = checker.check_links(self.root, ['README.md'])
        self.assertEqual(len(errors), 2)
        self.assertTrue(all('outside repository' in e for e in errors))
        self.assertTrue(checker.check_paths(self.root, ['outside.md']))

    def test_private_generated_files_rejected_but_build_tool_sources_allowed(self):
        bad = ['app/build/output.txt', '.gradle/cache', 'secret.pem', 'release.apk', 'mail.db-wal']
        self.assertEqual(len(checker.check_paths(self.root, bad)), len(bad))
        good = ['tools/build/optimize_apk.py', 'tools/build/bounded-tests.gradle',
                'gradle/wrapper/gradle-wrapper.jar', 'app/src/main/res/raw/photo.webp']
        self.assertEqual(checker.check_paths(self.root, good), [])

    def test_reference_integrity_detects_changed_or_missing_bytes(self):
        path = self.write('reference.md', 'do not rewrite\n')
        expected = {'reference.md': hashlib.sha256(path.read_bytes()).hexdigest()}
        self.assertEqual(checker.check_immutable(self.root, expected), [])
        path.write_bytes(b'do not rewrite')
        self.assertTrue(checker.check_immutable(self.root, expected))
        path.unlink()
        self.assertTrue(checker.check_immutable(self.root, expected))

    def test_release_link_and_record_must_match_stable_build(self):
        self.write('app/build.gradle.kts', 'versionName = "1.2.3"')
        self.write('docs/releases/README.md', '**当前稳定版：[v1.2.3](v1.2.3.md)**。')
        self.write('docs/releases/v1.2.3.md')
        self.assertEqual(checker.check_release(self.root), [])
        self.write('docs/releases/README.md', '**当前稳定版：[v1.0.0](v1.0.0.md)**。')
        self.assertTrue(checker.check_release(self.root))

    def test_owner_readme_stays_unchanged_independently_of_release_version(self):
        self.write('app/build.gradle.kts', 'versionName = "1.2.3"')
        self.write('README.md', '机主原话。当前正式版本：v1.0.0')
        self.write('docs/releases/README.md', '**当前稳定版：[v1.2.3](v1.2.3.md)**。')
        self.write('docs/releases/v1.2.3.md')
        before = (self.root / 'README.md').read_bytes()
        self.assertEqual(checker.check_release(self.root), [])
        self.assertEqual(before, (self.root / 'README.md').read_bytes())

    def test_missing_release_index_or_record_is_still_rejected(self):
        self.write('app/build.gradle.kts', 'versionName = "1.2.3"')
        self.assertEqual(len(checker.check_release(self.root)), 2)
        self.write('docs/releases/README.md', '**当前稳定版：[v1.2.3](v1.2.3.md)**。')
        self.assertTrue(checker.check_release(self.root))

    def test_development_version_does_not_require_release_record(self):
        self.write('app/build.gradle.kts', 'versionName = "1.2.4-dev"')
        self.assertEqual(checker.check_release(self.root), [])

    def test_old_shared_music_implementation_detected(self):
        self.write('app/src/main/java/io/github/reoutlook/ReBrowserMusicRuntime.java')
        errors = checker.check_sources(self.root)
        self.assertTrue(any('wrong-source-set' in e for e in errors))

    def test_cleanup_accepts_only_fixed_generated_targets(self):
        cleaner.validate_targets(self.root, ['app/build', 'build', '.gradle'],
                                 ['app/build.gradle.kts', 'tools/build/optimize_apk.py'])
        for name in ['tools/build', '../private', 'app/src', '.git', '/tmp']:
            with self.assertRaises(ValueError):
                cleaner.validate_targets(self.root, [name], [])

    def test_cleanup_refuses_tracked_files(self):
        with self.assertRaises(ValueError):
            cleaner.validate_targets(self.root, ['app/build'], ['app/build/important.txt'])

    def test_cleanup_refuses_symlink_target_or_parent(self):
        (self.root / 'build').symlink_to(self.root.parent, target_is_directory=True)
        with self.assertRaises(ValueError):
            cleaner.validate_targets(self.root, ['build'], [])
        (self.root / 'actual').mkdir()
        (self.root / 'app').symlink_to(self.root / 'actual', target_is_directory=True)
        with self.assertRaises(ValueError):
            cleaner.validate_targets(self.root, ['app/build'], [])

    def test_cleanup_refuses_unexpected_file(self):
        self.write('build', 'not a generated directory')
        with self.assertRaises(ValueError):
            cleaner.validate_targets(self.root, ['build'], [])

    def run_cleanup_fixture(self, *arguments, busy=(), wrong_root=False):
        def fake_git(command, **options):
            if command[-1] == '--show-toplevel':
                return str(self.root.parent if wrong_root else self.root)
            return b'README.md\0'
        with mock.patch.object(cleaner, 'ROOT', self.root), \
                mock.patch('sys.argv', ['clean_generated.py', *arguments]), \
                mock.patch.object(cleaner.subprocess, 'check_output', side_effect=fake_git), \
                mock.patch.object(cleaner, 'gradle_processes', return_value=busy), \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return cleaner.main()

    def test_cleanup_refuses_wrong_worktree_root(self):
        path = self.write('app/build/output.txt', 'not the tool worktree')
        self.assertEqual(self.run_cleanup_fixture('--apply', wrong_root=True), 1)
        self.assertTrue(path.exists())

    def test_cleanup_default_cli_is_nondestructive(self):
        path = self.write('app/build/output.txt', 'keep until explicitly applied')
        self.assertEqual(self.run_cleanup_fixture(), 0)
        self.assertTrue(path.exists())

    def test_cleanup_refuses_busy_build_before_any_removal(self):
        path = self.write('app/build/output.txt', 'build still active')
        self.assertEqual(self.run_cleanup_fixture('--apply', busy=['12345']), 1)
        self.assertTrue(path.exists())

    def test_cleanup_apply_affects_only_disposable_selected_directories(self):
        self.write('app/build/output.txt')
        self.write('build/report.txt')
        cache = self.write('.gradle/cache.txt')
        source = self.write('app/src/main/keep.txt', 'never delete source')
        self.assertEqual(self.run_cleanup_fixture('--apply'), 0)
        self.assertFalse((self.root / 'app/build').exists())
        self.assertFalse((self.root / 'build').exists())
        self.assertTrue(cache.exists())
        self.assertTrue(source.exists())
        self.assertEqual(self.run_cleanup_fixture('--apply', '--include-local-gradle-cache'), 0)
        self.assertFalse(cache.exists())
        self.assertTrue(source.exists())


if __name__ == '__main__':
    unittest.main()
