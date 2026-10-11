# Security policy

## Reporting a vulnerability

Please **do not open a public issue** for security problems. Use GitHub's private vulnerability reporting for this
repository (Security → Report a vulnerability). If that is not available, open an issue titled "Security contact
request" *without details* and a maintainer will arrange a private channel.

There is no bug bounty and no guaranteed response time; this is a volunteer project.

## Scope and threat model (summary)

A keyboard sees everything typed. Highest-value assets: typed text, clipboard contents, saved snippets/prompts, backup
files, and (planned) AI provider credentials. In scope: leaks of those, unintended network access, weak or misused
cryptography, unsafe handling of imported files, and exported components that let other apps read private data.
Out of scope: attacks requiring a rooted/compromised device or a malicious input method already selected by the user.

## Invariants the code must keep

See `CLAUDE.md` ("Security invariants"). In short: no logging of content, no network without explicit opt-in, no
destructive migrations of user data, backups use standard authenticated encryption, imports are treated as hostile.

## Supported versions

Pre-release (alpha). Only the latest commit on `main` is supported.

## Status of security work

Phase 9 of `ROADMAP.md` covers the full review. Until then, nothing here should be read as an audit result.
