#!/usr/bin/env python3
"""Verify the published PEM evidence against a pinned, publicly obtained Yubico root.

Requires Python 3 and OpenSSL 3. No token, PIN, private files or Python packages.
"""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile
import urllib.request

ROOT_URL = 'https://developers.yubico.com/PKI/yubico-ca-1.pem'
ROOT_SHA256 = '62760c6a6ef91679f454c8902b80fd009825b3f25da90f1fbace2ec6586cd5a8'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--openssl', default=os.environ.get('OPENSSL', 'openssl'))
    parser.add_argument('--cache-dir', type=Path,
                        default=Path.home()/'.cache/pkb-signing-attestations')
    parser.add_argument('documents', nargs='*', type=Path)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parent.parent
    documents = args.documents or [repo/'docs'/f'{c}-signing-key-attestation.md'
                                    for c in ('stable', 'nightly')]

    def openssl(*command, data=None):
        return subprocess.check_output([args.openssl, *command], input=data)

    def der(pem):
        return openssl('x509', '-outform', 'DER', data=pem)

    def root_valid(pem):
        return hashlib.sha256(der(pem)).hexdigest() == ROOT_SHA256

    if not openssl('version').decode().startswith('OpenSSL 3.'):
        raise RuntimeError('OpenSSL 3 required; use --openssl /path/to/openssl')
    args.cache_dir.mkdir(parents=True, exist_ok=True)
    cached = args.cache_dir/'yubico-ca-1.pem'
    if cached.exists():
        root = cached.read_bytes()
        if not root_valid(root):
            raise RuntimeError(f'Cached root fingerprint mismatch: {cached}')
    else:
        with urllib.request.urlopen(ROOT_URL, timeout=30) as response:
            root = response.read(65536)
        if not root_valid(root):
            raise RuntimeError('Downloaded root fingerprint mismatch')
        # Atomic complete write; do not overwrite a concurrent cache entry.
        with tempfile.NamedTemporaryFile(dir=args.cache_dir, delete=False) as f:
            temp = Path(f.name)
            f.write(root)
        try:
            try:
                os.link(temp, cached)
            except FileExistsError:
                if not root_valid(cached.read_bytes()):
                    raise RuntimeError('Concurrent cached root mismatch')
        finally:
            temp.unlink()
    print('Trusted root source:', ROOT_URL)
    print('Trusted root DER SHA-256:', ROOT_SHA256)

    for document in documents:
        source = document.read_text()
        blocks = re.findall(
            r'\\begin\{Verbatim\}\[fontsize=\\certfont\]\n(.*?)\n\\end\{Verbatim\}',
            source, re.S)
        qr = re.findall(r'\\qrcode\[height=2.1in\]\{(.*?)\}', source)
        if len(blocks) != 12 or len(qr) != 12:
            raise RuntimeError(f'Expected 12 certificate/QR pairs: {document}')
        for printed, payload in zip(blocks, qr):
            if ''.join(printed.split()) != ''.join(payload.split()):
                raise RuntimeError('QR payload differs from printed PEM')
        with tempfile.TemporaryDirectory(prefix='pkb-attestation-') as work:
            work = Path(work)
            paths = []
            for i, block in enumerate(blocks):
                p = work/f'certificate-{i}.pem'
                p.write_text(block+'\n')
                paths.append(p)
            if der(paths[11].read_bytes()) != der(root):
                raise RuntimeError('Document root differs from public trusted root')
            trust = work/'trusted-root.pem'
            trust.write_bytes(root)
            for i in range(3):
                signer, slot, f9 = paths[i], paths[3+i], paths[6+i]
                # The document intermediates remain untrusted until chaining succeeds.
                chain = work/f'chain-{i}.pem'
                chain.write_bytes(f9.read_bytes()+paths[9].read_bytes()+paths[10].read_bytes())
                result = openssl('verify', '-show_chain', '-CAfile', str(trust),
                                 '-untrusted', str(chain), str(slot))
                print(document.name, 'signer', 'ABC'[i])
                print(result.decode().strip())
                public = lambda p: openssl('x509', '-in', str(p), '-pubkey', '-noout')
                if public(signer) != public(slot):
                    raise RuntimeError('Signer public key differs from attestation')
                openssl('verify', '-check_ss_sig', '-CAfile', str(signer), str(signer))
                fingerprint = hashlib.sha256(der(signer.read_bytes())).hexdigest()
                if fingerprint not in source:
                    raise RuntimeError('Signer fingerprint missing from declaration')
            print('PASS: root, manufacturer chains, public-key matches, self-signatures, QR payloads')
    print('This verifies certificate evidence, not QES status or Android installation behaviour.')


if __name__ == '__main__':
    main()
