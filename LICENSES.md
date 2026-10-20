# Licensing and attribution

This is not legal advice. It records what the repository does to comply with the licences it knows about.

## 1. What this project is

Victorious Keyboard is a **modified distribution of FlorisBoard**, derived from upstream tag `v0.6.0-alpha02`
(commit `0ea0481c40616b0fd6eb4cd09c57521181b9a4b6`, <https://github.com/florisboard/florisboard>). It is **not** a
clean-room implementation. Upstream's full Git history is part of this repository's history, so per-file authorship
is preserved.

## 2. Project licence

[Apache License 2.0](LICENSE) (unchanged upstream text). New files added by this project carry the same licence.

Apache-2.0 §4 obligations and how they are met:

| Obligation | How |
|---|---|
| Give recipients a copy of the licence | `LICENSE` at the repository root; copied into the APK assets by the existing build (`app/src/main/assets/license/project_license.txt`) |
| Retain copyright/attribution notices in source | Upstream per-file headers (`Copyright (C) … The FlorisBoard Contributors`) are left intact |
| Mark modified files | Git history is the record. New files use the header `Copyright (C) 2026 Victorious Keyboard Contributors`. Significant modifications to upstream files add a line, they never remove upstream's |
| Preserve `NOTICE` content | Upstream has no `NOTICE` file at this commit |

## 3. Names, logos, trademarks

* Application id: `com.victorious.keyboard` (debug `.debug`, bench `.bench`). The Kotlin source namespace is
  `com.victorious.keyboard`. It is an internal code identifier, not a product name. Copyright notices still name
  FlorisBoard. The input-method component name stays `dev.patrickgold.florisboard.FlorisImeService`.
* The FlorisBoard name and icon belong to upstream. Apache-2.0 §6 grants no trademark rights. User-visible names and
  the launcher icon are replaced in Phase 1; the README states the derivation factually ("based on FlorisBoard").
* This project is not affiliated with or endorsed by the FlorisBoard project or by Google.

## 4. Upstream AI policy

Upstream's `AI_POLICY.md` (kept at `docs/upstream/AI_POLICY.md`, licence CC-BY-4.0) governs contributions *to
FlorisBoard*. It forbids AI-generated code and messages there. It is **not** adopted here, but consequently: **code
developed in this fork with AI assistance must not be submitted to upstream.** Upstream-originated fixes may be
cherry-picked into this fork under Apache-2.0 with attribution preserved.

## 5. Gboard

No Gboard source, assets, layouts-as-files, dictionaries or models are used. "Gboard-inspired" means original
implementations of generally known behaviours (e.g. suggestion strip, swipe typing) using public Android APIs.

## 6. Third-party components

Runtime libraries are declared in `gradle/libs.versions.toml`; the app generates its in-app open-source list with the
`aboutlibraries` plugin (`app/src/main/config`). Notable non-Apache items found during the audit:

| Component | Where | Licence | Note |
|---|---|---|---|
| ICU4C data/notices | `app/src/main/config/licenses/icu4c.json` | Unicode / ICU licence (+ BSD-style notices for bundled dictionaries) | Notice text is bundled and must keep shipping |
| Material icons | `app/src/main/config/libraries/google-material-icons.json` | Apache-2.0 | |
| Pictogrammers Material icons | `…/pictogrammers-material-icons.json` | Apache-2.0 | |
| Emoji data (`assets/ime/media/emoji/*.txt`), emoticons | upstream assets | Not individually verified in this audit | **Open item:** confirm provenance/licence of each emoji annotation file before a public release |
| Han shape-based pack (`han.sqlite3`) | `assets/ime/languagepack/…` | Not individually verified | **Open item:** confirm licence before a public release |
| Keyboard layouts / localisation | `assets/ime/keyboard/…` | Upstream (Apache-2.0) | |
| llama.cpp `b11624` and ggml | fetched at build time into the native library; notice in `assets/licenses/llama.cpp.MIT` | MIT | CPU-only JNI. No model weights are included. nlohmann json notice is `assets/licenses/nlohmann-json.MIT` in case that header is linked. |

New runtime dependencies added by this project will be listed here when they are added, with their licences.
Candidates under consideration for Phase 6 must be permissively licensed (Apache-2.0/MIT/BSD); GPL/AGPL/LGPL
dependencies are not acceptable in the APK.
