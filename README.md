# Victorious Keyboard

An open-source Android keyboard for everyday typing **and** for developers, Linux/Termux users and people who work
with AI assistants. It is a **modified distribution of [FlorisBoard](https://github.com/florisboard/florisboard)**
(Apache-2.0), based on upstream tag `v0.6.0-alpha02`. It is not affiliated with the FlorisBoard project.

> **Status: early development (Phase 0 complete).** The repository currently builds the upstream keyboard unchanged.
> None of the Victorious-specific features below exist yet, and the app has **not yet been run on an Android
> device** by this project. Do not use it as a daily driver. See [ROADMAP.md](ROADMAP.md) for the real status.

## Planned (not yet implemented)

* Persistent clipboard history (500 items) with search, pinning, encrypted backup/restore
* Developer mode (Esc/Tab/Ctrl/arrows rows, bracket pairing) and Terminal mode for Termux/SSH
* Snippets with text expansion; prompt library for AI tools (offline, opt-in network only)
* Offline word suggestions, glide typing, themes, one-handed/floating layouts

Privacy stance: no analytics, no keylogging, no background network, no sensitive data in logs.

## Documentation

| Doc | Purpose |
|---|---|
| [AUDIT.md](AUDIT.md) | What was inspected, which upstream base and why, baseline results, risks |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Code structure and decisions |
| [BUILD.md](BUILD.md) | How to build and test |
| [ROADMAP.md](ROADMAP.md) | Phases and honest status |
| [LICENSES.md](LICENSES.md) | Licensing and attribution |
| [CLAUDE.md](CLAUDE.md) | Working notes for AI coding agents on this repo |
| [docs/upstream/](docs/upstream/) | Upstream's README, roadmap, contributing guide and AI policy, preserved verbatim |

## Build

```sh
./gradlew assembleDebug testDebugUnitTest
```

Prerequisites and caveats: [BUILD.md](BUILD.md).

## Licence

Apache License 2.0 — see [LICENSE](LICENSE) and [LICENSES.md](LICENSES.md).
