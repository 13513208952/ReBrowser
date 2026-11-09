#!/usr/bin/env python3
"""Resource-safety regressions; no Android device or dependency downloads."""
import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock

MODULE = Path(__file__).resolve().parents[1] / "build/revideo_native.py"
spec = importlib.util.spec_from_file_location("revideo_native", MODULE)
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)


class NativeBuildSafetyTests(unittest.TestCase):
    def test_live_measurement_tolerates_only_vanished_entries(self):
        vanished = subprocess.CompletedProcess([], 1, "1234\t/work\n",
                                                "du: cannot access '/work/transient': No such file or directory\n")
        with mock.patch.object(builder.subprocess, "run", return_value=vanished):
            self.assertEqual(1234, builder.size_bytes(Path("/work")))
        denied = subprocess.CompletedProcess([], 1, "1234\t/work\n",
                                              "du: cannot read directory '/work/private': Permission denied\n")
        with mock.patch.object(builder.subprocess, "run", return_value=denied):
            with self.assertRaises(subprocess.CalledProcessError):
                builder.size_bytes(Path("/work"))

    def test_low_system_space_never_launches_a_command(self):
        with tempfile.TemporaryDirectory() as directory:
            free = shutil.disk_usage(directory)
            low = type(free)(free.total, free.used, 2 * builder.GIB)
            ample = type(free)(free.total, free.used, 20 * builder.GIB)
            with mock.patch.object(builder.shutil, "disk_usage", side_effect=[low, ample]), \
                 mock.patch.object(builder, "size_bytes", return_value=0), \
                 mock.patch.object(builder.subprocess, "Popen") as launch:
                with self.assertRaises(builder.SpaceLimit):
                    builder.run_guarded([sys.executable, "-c", "raise SystemExit(0)"],
                                        cwd=directory, env=os.environ.copy(), work=Path(directory))
                launch.assert_not_called()

    def test_running_child_is_terminated_when_reserve_is_reached(self):
        children = []
        real_popen = subprocess.Popen

        def spawn(*args, **kwargs):
            child = real_popen(*args, **kwargs)
            children.append(child)
            return child

        with tempfile.TemporaryDirectory() as directory, \
             mock.patch.object(builder, "size_bytes", return_value=0), \
             mock.patch.object(builder, "check_space", side_effect=[None, builder.SpaceLimit("reserve reached")]), \
             mock.patch.object(builder.subprocess, "Popen", side_effect=spawn):
            with self.assertRaises(builder.SpaceLimit):
                builder.run_guarded([sys.executable, "-c", "import time; time.sleep(30)"],
                                    cwd=directory, env=os.environ.copy(), work=Path(directory))
        self.assertEqual(1, len(children))
        self.assertIsNotNone(children[0].returncode)
        self.assertLess(children[0].returncode, 0)

    def test_cleanup_preserves_artifacts_libraries_and_unrelated_files(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            builder.ensure_owned(work)
            files = {
                "builds/flutter/intermediate": b"regenerate",
                "gradle-caches/9.1.0/transforms/temporary": b"regenerate",
                "artifacts/apk/last-good.apk": b"retained package",
                "native-libs/pinned.jar": b"verified native library",
                "gradle-caches/modules-2/dependency": b"retained dependency",
                "user-file": b"not build-owned",
            }
            for relative, contents in files.items():
                path = work / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(contents)
            builder.cleanup(work)
            self.assertFalse((work / "builds").exists())
            self.assertFalse((work / "gradle-caches/9.1.0/transforms").exists())
            for relative in ("artifacts/apk/last-good.apk", "native-libs/pinned.jar",
                             "gradle-caches/modules-2/dependency", "user-file"):
                self.assertEqual(files[relative], (work / relative).read_bytes())

    def test_existing_user_directory_is_not_adopted(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            file = work / "user-file"
            file.write_bytes(b"keep")
            with self.assertRaises(ValueError):
                builder.ensure_owned(work)
            self.assertEqual(b"keep", file.read_bytes())
            self.assertFalse((work / builder.MARKER).exists())

    def test_cleanup_cannot_escape_via_path_or_symlink(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            work, outside = root / "owned", root / "outside"
            outside.mkdir()
            important = outside / "important"
            important.write_bytes(b"keep")
            builder.ensure_owned(work)
            (work / "builds").symlink_to(outside, target_is_directory=True)
            for relative in ("builds", "../outside", "."):
                with self.assertRaises(ValueError):
                    builder.remove_owned(work, relative)
            self.assertEqual(b"keep", important.read_bytes())

    def test_preparation_rejects_nested_scratch_link_escape(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            work, outside = root / "owned", root / "outside"
            outside.mkdir()
            builder.ensure_owned(work)
            (work / "gradle-caches").mkdir()
            (work / "gradle-caches/9.1.0").symlink_to(outside, target_is_directory=True)
            with self.assertRaisesRegex(ValueError, "escapes owned"):
                builder.ensure_owned(work)
            self.assertEqual([], list(outside.iterdir()))

    def test_existing_wrapper_survives_removed_custom_cache_environment(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repo, module, work, cache = (root / name for name in ("repo", "module", "work", "cache"))
            for base, version in ((repo, "8.11.1"), (module / ".android", "9.1.0")):
                declaration = base / "gradle/wrapper/gradle-wrapper.properties"
                declaration.parent.mkdir(parents=True)
                declaration.write_text(f"distributionUrl=https://services.gradle.org/distributions/gradle-{version}-bin.zip")
                (cache / f"wrapper/dists/gradle-{version}-bin").mkdir(parents=True)
            with mock.patch.object(builder, "REPO", repo), \
                 mock.patch.object(builder, "MODULE", module):
                builder.ensure_owned(work)
                with mock.patch.dict(os.environ, {"GRADLE_USER_HOME": str(cache)}):
                    home = builder.prepare(work)
                with mock.patch.dict(os.environ, {}, clear=True):
                    builder.prepare(work)
                for version in ("8.11.1", "9.1.0"):
                    name = f"gradle-{version}-bin"
                    self.assertEqual(cache / "wrapper/dists" / name,
                                     (home / "wrapper/dists" / name).resolve())

    def test_stop_group_kills_grandchild_ignoring_term_after_parent_exits(self):
        with tempfile.TemporaryDirectory() as directory:
            pidfile = Path(directory) / "grandchild"
            script = (
                "import os, signal, time; from pathlib import Path\n"
                "if os.fork() == 0:\n"
                " signal.signal(signal.SIGTERM, signal.SIG_IGN)\n"
                f" Path({str(pidfile)!r}).write_text(str(os.getpid()))\n"
                " time.sleep(60)\n"
                "else:\n"
                " time.sleep(60)\n"
            )
            process = subprocess.Popen([sys.executable, "-c", script], start_new_session=True)
            try:
                deadline = time.monotonic() + 5
                while not pidfile.exists() and time.monotonic() < deadline:
                    time.sleep(0.01)
                self.assertTrue(pidfile.exists(), "Grandchild failed to start")
                pid = int(pidfile.read_text())
                builder.stop_group(process)
                status = Path(f"/proc/{pid}/stat")
                deadline = time.monotonic() + 2
                while status.exists() and status.read_text().split()[2] != "Z" and time.monotonic() < deadline:
                    time.sleep(0.01)
                self.assertTrue(not status.exists() or status.read_text().split()[2] == "Z",
                                "Grandchild survived after its parent exited")
            finally:
                builder.stop_group(process)
                if pidfile.exists():
                    try:
                        os.kill(int(pidfile.read_text()), 9)
                    except ProcessLookupError:
                        pass


if __name__ == "__main__":
    unittest.main()
