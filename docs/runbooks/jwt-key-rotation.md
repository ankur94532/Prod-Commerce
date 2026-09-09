# JWT key rotation

Production tokens use RS256 and carry a `kid`. The auth service alone receives the active
private key. Every Java service receives the active public key and, during rotation, one
previous public key. Public keys are also available at
`GET /api/v1/auth/.well-known/jwks.json`.

The maximum overlap is the refresh-token TTL (currently seven days), plus clock skew. Do
not remove the previous key earlier: an access token can be replaced from an older refresh
token throughout that window.

## Prepare a key offline

Use a restricted temporary directory on an encrypted operator workstation:

```bash
umask 077
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out jwt-private.pem
openssl pkcs8 -topk8 -nocrypt -in jwt-private.pem -outform DER | base64 | tr -d '\n'
openssl pkey -in jwt-private.pem -pubout -outform DER | base64 | tr -d '\n'
```

Put the first value in `gocommerce-jwt-signer` as
`SECURITY_JWT_PRIVATE_KEY_BASE64`. Put the second value and a unique, non-secret key ID in
`gocommerce-jwt-verifier`. Never paste the private value into tickets, logs, commits, or
the verifier secret.

## Rotate without logging users out

1. Copy the current active public key and ID into the `PREVIOUS` fields.
2. Replace the active public key, private key, and ID with the newly generated pair.
3. Restart verifier services and verify the JWKS contains both IDs. Restart auth last, so
   no service sees a new token before it can verify it.
4. Confirm a token minted before the rotation and a token minted after it both work.
5. Wait at least the refresh-token TTL plus allowed clock skew. Remove both `PREVIOUS`
   values and restart verifier services.
6. Securely delete the retired private-key file according to the workstation policy.

An unknown `kid`, a missing `kid` after HMAC compatibility is removed, keys under 2048
bits, malformed keys, and mismatched active private/public keys all fail closed. The
legacy `SECURITY_JWT_SECRET` accepts old no-`kid` tokens only when explicitly present; do
not configure it in a fresh environment and remove it after the initial seven-day overlap.

## Emergency compromise

If the active private key may be compromised, skip the overlap for that key: replace it,
remove it from all verifier secrets immediately, restart verifiers before auth, and expect
all tokens signed by the compromised key to become invalid. Record the incident and use
the account/password recovery process rather than retaining a compromised verification
key for convenience.
