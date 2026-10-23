#!/usr/bin/env python3
"""Public provisioning consistency and OpenSSL proof checks, NOT device/OS/authority admission."""
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def decode(value):
    raw = base64.urlsafe_b64decode(value + '=' * (-len(value) % 4))
    if base64.urlsafe_b64encode(raw).rstrip(b'=').decode() != value:
        raise ValueError('Noncanonical base64url')
    return raw


class AdminProvisioningTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.record = json.loads((ROOT / 'planning/geckoview-v2/records/admin-root-public-provisioning.json').read_text())
        cls.device = json.loads((ROOT / 'planning/geckoview-v2/records/device-enrollment-status.json').read_text())

    def test_exact_public_named_curve_key_and_fingerprint(self):
        public = decode(self.record['publicSpkiBase64Url'])
        self.assertEqual(91, len(public))
        self.assertEqual('3059301306072a8648ce3d020106082a8648ce3d03010703420004', public[:27].hex())
        self.assertEqual(hashlib.sha256(public).hexdigest(), self.record['publicSpkiSha256'])
        self.assertEqual('ROOT', self.record['role'])
        self.assertEqual(10, self.record['level'])

    def test_actual_compiled_source_pins_exact_provisioned_public_identity(self):
        source = (ROOT / 'app/src/main/java/io/github/rebrowser/authority/CompiledPrincipals.java').read_text()
        for field in ('keyId', 'publicSpkiBase64Url', 'publicSpkiSha256'):
            self.assertIn(self.record[field], source)
        self.assertNotIn('BEGIN PRIVATE KEY', source)
        self.assertNotIn('BEGIN ENCRYPTED PRIVATE KEY', source)

    def test_public_possession_sample_verifies_and_changed_message_fails(self):
        work = Path(os.environ.get('TMPDIR', '/home/zhao/temp/re_outlook/geckoview-reimplementation/jvm')).resolve()
        self.assertFalse(work.is_relative_to(Path('/tmp')))
        with tempfile.TemporaryDirectory(prefix='root-public-proof-', dir=work) as name:
            path = Path(name)
            public = subprocess.check_output(['openssl', 'pkey', '-pubin', '-inform', 'DER', '-outform', 'PEM'], input=decode(self.record['publicSpkiBase64Url']), stderr=subprocess.PIPE)
            (path / 'public.pem').write_bytes(public)
            sample = self.record['publicProofOfPossession']
            message = decode(sample['challengeBase64Url'])
            self.assertTrue(message.startswith(b'ReBrowser.Root.Provisioning.Verify.1\0'))
            (path / 'signature.der').write_bytes(decode(sample['signatureDerBase64Url']))
            command = ['openssl', 'dgst', '-sha256', '-verify', str(path / 'public.pem'), '-signature', str(path / 'signature.der')]
            result = subprocess.run(command, input=message, capture_output=True)
            self.assertEqual(0, result.returncode)
            changed = message[:-1] + bytes([message[-1] ^ 1])
            self.assertNotEqual(0, subprocess.run(command, input=changed, capture_output=True).returncode)

    def test_generation_is_not_claimed_to_finish_custody_or_source_acceptance(self):
        self.assertEqual(0, self.record['custody']['offlineCopiesVerified'])
        self.assertTrue(self.record['custody']['ownerBackupAndRecoveryHandoffPending'])
        self.assertFalse(self.record['sourceOrBusinessUnlocked'])
        self.assertFalse(self.record['apkSigningKeyGenerated'])
        self.assertFalse(self.record['deviceWhitelistEnrollmentPerformed'])

    def test_connected_device_metadata_never_substitutes_for_installation_key(self):
        self.assertEqual(1, self.device['connectedCandidateCount'])
        self.assertEqual(0, self.device['enrolledCompiledInstallations'])
        self.assertIsNone(self.device['candidateInstallPublicKey'])
        self.assertFalse(self.device['formalV2PackageInstalledOnObservedDevice'])
        for field in ('adbSerial', 'serial', 'model', 'account', 'passphrase'):
            self.assertNotIn(field, self.device)
        self.assertEqual(self.record['keyId'], self.device['candidateListOwnerKeyId'])

    def test_non_whitelisted_route_remains_explicit_without_claiming_android_success(self):
        policy = self.device['nonWhitelistPolicy']
        self.assertFalse(policy['isGlobalDenyList'])
        self.assertFalse(policy['rootExemptFromOwnerAuthenticationOutsideList'])
        self.assertIn(' OR ', policy['highestRiskActions'])
        self.assertIn('integration-pending', policy['implementationStatus'])
        self.assertIn('OS owner authentication prompt', self.device['notPerformed'])


if __name__ == '__main__':
    unittest.main()
