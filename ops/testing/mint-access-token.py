"""Mints an RS256 access token the services will accept.

The chaos drills need a real token: cart-service authenticates every request, so a drill
that could not produce one would be reduced to testing /health, which touches none of the
state the drill is about.

Signing is done by shelling out to openssl rather than a JWT library, so this needs nothing
that is not already installed. The claims mirror what JwtIssuer emits and JwtVerifier
requires: a "kid" header naming the active key, and a "type" claim, without which the
verifier refuses the token because access and refresh tokens must be distinguishable.

Usage: mint-access-token.py <private-key.pem> <key-id> <subject> [role] [ttl-seconds]
"""
from __future__ import annotations

import base64
import json
import subprocess
import sys
import tempfile
import time


def b64url(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b'=').decode()


def sign(signing_input: bytes, private_key_pem: str) -> bytes:
    with tempfile.NamedTemporaryFile() as payload:
        payload.write(signing_input)
        payload.flush()
        return subprocess.run(
            ['openssl', 'dgst', '-sha256', '-sign', private_key_pem, payload.name],
            check=True, capture_output=True).stdout


def main(argv: list[str]) -> int:
    private_key_pem, key_id, subject = argv[1], argv[2], argv[3]
    role = argv[4] if len(argv) > 4 else 'USER'
    ttl = int(argv[5]) if len(argv) > 5 else 3600

    now = int(time.time())
    header = {'alg': 'RS256', 'typ': 'JWT', 'kid': key_id}
    claims = {
        'sub': subject,
        'email': f'{subject}@example.invalid',
        'fullName': 'Chaos Drill',
        'role': role,
        # Without this the verifier rejects the token outright.
        'type': 'access',
        'iat': now,
        'exp': now + ttl,
    }
    encode = lambda part: b64url(json.dumps(part, separators=(',', ':')).encode())
    signing_input = f'{encode(header)}.{encode(claims)}'.encode()
    print(f"{signing_input.decode()}.{b64url(sign(signing_input, private_key_pem))}")
    return 0


if __name__ == '__main__':
    raise SystemExit(main(sys.argv))
