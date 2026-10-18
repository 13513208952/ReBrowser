#!/usr/bin/env python3
"""Host synthetic gate fixtures only; never archive real code or touch the root ZIP."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import review_legacy_archive as gate


class LegacyArchiveGateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='rbv-archive-gate-')
        self.root = Path(self.temp.name)
        self.plan = self.root / 'planning/geckoview-v2'
        (self.plan / 'records').mkdir(parents=True)
        self.path = 'app/src/main/java/io/github/reoutlook/Synthetic.java'
        f = self.root / self.path
        f.parent.mkdir(parents=True)
        self.data = b'// synthetic old-app fixture, not real source\n'
        f.write_bytes(self.data)
        self.entry = {'path': self.path, 'sha256': hashlib.sha256(self.data).hexdigest(), 'bytes': len(self.data), 'state': 'queued-not-semantically-reviewed', 'archiveApproved': False, 'refactorValueExhausted': False, 'evidenceRefs': [], 'reason': 'Not reviewed', 'reviewedAt': None}
        self.ledger = {'entries': [self.entry], 'archiveTarget': 'old_version.zip', 'filesMoved': 0, 'archiveCreated': False}
        self.addCleanup(self.temp.cleanup)

    def check(self):
        (self.plan / 'records/legacy-archive-review.json').write_text(json.dumps(self.ledger))
        with patch.object(gate, 'P', self.plan), patch.object(gate, 'ROOT', self.root):
            return gate.review()

    def synthetic_archive(self, name=None):
        self.entry.update(state='archived', archiveApproved=True, refactorValueExhausted=True, evidenceRefs=['synthetic-evidence'], reason='Synthetic completed review', reviewedAt='synthetic-time')
        (self.root / self.path).unlink()
        self.ledger.update(filesMoved=1, archiveCreated=True)
        with zipfile.ZipFile(self.root / 'old_version.zip', 'w') as z:
            z.writestr(name or self.path, self.data)

    def test_unreviewed_is_retained_not_approved(self):
        self.assertEqual(self.check()['legacyFilesArchived'], 0)
        self.assertTrue((self.root / self.path).is_file())

    def test_original_structure_and_bytes_checked(self):
        self.synthetic_archive()
        self.assertEqual(self.check()['legacyFilesArchived'], 1)

    def test_bad_original_hash_fails(self):
        self.entry['sha256'] = '0' * 64
        with self.assertRaises(AssertionError): self.check()

    def test_duplicate_record_fails(self):
        self.ledger['entries'].append(copy.deepcopy(self.entry))
        with self.assertRaises(AssertionError): self.check()

    def test_temporary_test_code_not_subject(self):
        self.entry['path'] = 'app/src/test/java/io/github/reoutlook/TemporaryTest.java'
        with self.assertRaises(AssertionError): self.check()

    def test_parent_traversal_fails(self):
        self.entry['path'] = 'app/src/main/../test/Temporary.java'
        with self.assertRaises(AssertionError): self.check()

    def test_no_unreviewed_archive(self):
        self.synthetic_archive()
        self.entry['archiveApproved'] = False
        with self.assertRaises(AssertionError): self.check()

    def test_no_archive_before_value_exhausted(self):
        self.synthetic_archive()
        self.entry['refactorValueExhausted'] = False
        with self.assertRaises(AssertionError): self.check()

    def test_no_flattening_of_archive_paths(self):
        self.synthetic_archive('Synthetic.java')
        with self.assertRaises(AssertionError): self.check()

    def test_no_unsafe_extra_archive_path(self):
        self.synthetic_archive()
        with zipfile.ZipFile(self.root / 'old_version.zip', 'a') as z:
            z.writestr('../outside.java', b'synthetic')
        with self.assertRaises(AssertionError): self.check()


if __name__ == '__main__':
    unittest.main()
