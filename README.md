## `scala-pgp-bootstrap`

Automation tool for generating PGP key for setting up `sbt-ci-release`.

### How to run

Use Coursier.

```sh
% cs launch dev.capslock::scala-pgp-bootstrap:<VERSION>
```

### What it does

- Generate PGP key into local keyring
   - EDDSA(ed25519) sign-only key (no auth/encryption)
   - no expiration
- Publish key into keyservers (`hkps://keys.openpgp.org`, `hkps://keyserver.ubuntu.com`)
- Set up PGP key and passphrase as GitHub Secrets
  - `PGP_PASSPHRASE` -- passphrase for PGP secret key
  - `PGP_SECRET` -- base64-encoded PGP secret key