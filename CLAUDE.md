# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`scala-pgp-bootstrap` is an interactive CLI tool that automates PGP key setup for `sbt-ci-release`: it generates an ed25519 sign-only PGP key into a local keyring (`./keyring.gpg`), optionally publishes it to keyservers (`hkps://keys.openpgp.org`, `hkps://keyserver.ubuntu.com`), and stores `PGP_PASSPHRASE` / `PGP_SECRET` (base64-encoded secret key) as GitHub Secrets via the `gh` CLI.

## Commands

- Build/compile: `sbt compile`
- Run locally: `sbt run` (requires `gpg` and `gh` on PATH, and must be run inside a GitHub repo checkout)
- Test: `sbt test` (munit; no test sources exist yet)
- Run a single test: `sbt "testOnly <fully.qualified.TestName>"`
- Format: `scalafmt` config is `.scalafmt.conf` (scalafmt 3.10.0, maxColumn 120, trailingCommas always); run via `sbt scalafmtAll` if needed
- Local fat-jar for testing: `sbt assembly` (sbt-assembly is included for this purpose); output goes to `target/out/jvm/scala-<scala-version>/scala-pgp-bootstrap/`

This build uses sbt 2.x. Multiple commands on the CLI must be joined with semicolons in a single argument (`sbt "compile; test"`) — passing them as separate arguments fails to parse. `build.sbt` uses sbt 2 bare settings (top-level settings apply to all subprojects), which replace the sbt 1 `inThisBuild(...)` pattern — keep new common settings at the top level rather than reintroducing `ThisBuild` scoping.

Runtime requires Java 21+ (the Ox library uses virtual threads). The release workflow (`.github/workflows/release.yml`) publishes to Maven Central via `sbt ci-release` on push to main or tags.

## Architecture

Scala 3 (version pinned in `build.sbt`), single sbt module, package `dev.capslock.scalapgpbootstrap`. Concurrency is direct-style via **Ox** (`ox.OxApp`, `supervised` scopes, `ox.par`), not effect systems. External commands (`gpg`, `gh`, `rm`) are invoked via **os-lib** (`os.proc`); interactive prompts use **cue4s** (`Prompts.sync`); logging uses **scribe**; JSON parsing of `gh` output uses **circe**.

- `Main.scala` — orchestration. Nested "ensure/with" combinators from `GitHub` wrap the flow (gh available → inside repo → token has `user` scope → email selected), then a `supervised` scope manages temporary files (batch file, passphrase file) as `Closeable` resources via `ox.useCloseableInScope`, and runs keyserver publishing / secret-setting in parallel with `ox.par`. Each side-effecting step (publish, set secrets, wipe keyring) is gated by a user confirmation prompt.
- `GitHub.scala` — all `gh` CLI interaction. The `with…`/`ensure…` functions take the continuation body as a parameter (`body: => Ox ?=> ExitCode`), forming the nested control flow in `Main`. Parses `gh auth status` text and `gh api /user/emails` JSON.
- `Gpg.scala` — all `gpg` interaction against a dedicated keyring `./keyring.gpg` (never the user's default keyring). `KeyId` is an opaque type. Key ID is extracted by parsing `--with-colons` output (`fpr:` line, 10th field).
- `Util.scala` — passphrase generation with `SecureRandom` (charset deliberately excludes backslash to avoid escaping issues) and `TemporaryFile`, a `Closeable` wrapper so temp files holding secrets are deleted when the scope exits.

## Security-relevant conventions

- The passphrase and the GPG batch file (which embeds the passphrase) are written only to temporary files that are deleted via `Closeable` scoping — preserve this pattern when changing secret handling.
- All gpg operations must keep using `--no-default-keyring --keyring ./keyring.gpg` so the user's real keyring is untouched.
