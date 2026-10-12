#!/usr/bin/env python3
"""No ADB, credentials or real APKs: lossless packaging regression fixtures."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

MODULE = Path(__file__).resolve().parents[1] / "build" / "optimize_apk.py"
spec = importlib.util.spec_from_file_location("optimize_apk", MODULE)
optimizer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(optimizer)


class OptimizeApkTests(unittest.TestCase):
    def test_preserves_entries_stored_resources_and_payloads(self):
        with tempfile.TemporaryDirectory() as root:
            source, output = Path(root) / "input.apk", Path(root) / "output.apk"
            values = {"classes.dex": b"dex fixture repeated" * 2048,
                      "resources.arsc": bytes(range(256)) * 2,
                      "res/raw/photo.webp": b"already encoded photo",
                      "META-INF/library.version": b"1.0"}
            with zipfile.ZipFile(source, "w") as archive:
                for name, data in values.items():
                    mode = zipfile.ZIP_STORED if name in ("resources.arsc", "res/raw/photo.webp") else zipfile.ZIP_DEFLATED
                    archive.writestr(name, data, compress_type=mode, compresslevel=1)
            report = optimizer.optimize(source, output, 5)
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(list(values), archive.namelist())
                for name, data in values.items():
                    self.assertEqual(data, archive.read(name))
                self.assertEqual(zipfile.ZIP_STORED, archive.getinfo("resources.arsc").compress_type)
                self.assertEqual(zipfile.ZIP_STORED, archive.getinfo("res/raw/photo.webp").compress_type)
            self.assertGreater(report["payload_saved"], 0)
            self.assertTrue(all(row["after"] <= row["before"] for row in report["entries"]))

    def test_refuses_signed_or_in_place_inputs(self):
        with tempfile.TemporaryDirectory() as root:
            source, output = Path(root) / "signed.apk", Path(root) / "output.apk"
            with zipfile.ZipFile(source, "w") as archive:
                archive.writestr("META-INF/CERT.RSA", b"fixture")
            with self.assertRaises(ValueError):
                optimizer.optimize(source, output, 1)
            with self.assertRaises(ValueError):
                optimizer.optimize(source, source, 1)

    def test_unicode_filename_and_zip_comment_survive(self):
        with tempfile.TemporaryDirectory() as root:
            source, output = Path(root) / "input.apk", Path(root) / "output.apk"
            with zipfile.ZipFile(source, "w") as archive:
                archive.writestr("目录/图标.txt", b"fixture", compress_type=zipfile.ZIP_DEFLATED)
                archive.comment = b"archive comment"
            optimizer.optimize(source, output, 1)
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(b"fixture", archive.read("目录/图标.txt"))
                self.assertEqual(b"archive comment", archive.comment)


if __name__ == "__main__":
    unittest.main()
