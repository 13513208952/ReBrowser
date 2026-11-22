#!/usr/bin/env python3
"""Reproduce a ReBrowser administrator key from an offline recovery passphrase."""

import argparse
import base64
import getpass
import hashlib
import os
import sys
import warnings
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec

SALT = b"ReBrowser administrator root v2 | ECDSA P-256"
P256_ORDER = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551


def derive(passphrase: str) -> ec.EllipticCurvePrivateKey:
    if not passphrase:
        raise ValueError("The recovery passphrase must not be empty")
    seed = hashlib.scrypt(passphrase.encode("utf-8"), salt=SALT,
                          n=2**17, r=8, p=1, dklen=64, maxmem=256 * 1024**2)
    scalar = 1 + int.from_bytes(seed, "big") % (P256_ORDER - 1)
    return ec.derive_private_key(scalar, ec.SECP256R1())


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--private-out", type=Path, metavar="FILE",
                        help="also save a PKCS#8 private PEM (new file only, mode 0600)")
    args = parser.parse_args()
    if not sys.stdin.isatty():
        parser.error("Enter the recovery passphrase in a local terminal, not through stdin")
    with warnings.catch_warnings():
        warnings.simplefilter("error", getpass.GetPassWarning)
        try:
            phrase = getpass.getpass("Recovery passphrase: ")
            confirmation = getpass.getpass("Repeat passphrase: ")
        except getpass.GetPassWarning:
            parser.exit(1, "Terminal cannot hide passphrase input.\n")
    if not phrase or phrase != confirmation:
        parser.exit(1, "Passphrase is blank or the two entries differ.\n")
    key = derive(phrase)
    if args.private_out is not None:
        pem = key.private_bytes(serialization.Encoding.PEM,
                                serialization.PrivateFormat.PKCS8,
                                serialization.NoEncryption())
        try:
            fd = os.open(args.private_out.expanduser(), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, "wb") as output:
                output.write(pem)
        except OSError as error:
            parser.exit(1, f"Cannot save private key: {error}\n")
    public_der = key.public_key().public_bytes(serialization.Encoding.DER,
                                               serialization.PublicFormat.SubjectPublicKeyInfo)
    print("Public key (SPKI DER, Base64):", base64.b64encode(public_der).decode("ascii"))
    print("Public key SHA-256:", hashlib.sha256(public_der).hexdigest())


if __name__ == "__main__":
    main()
