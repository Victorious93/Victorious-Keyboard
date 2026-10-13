# Building

Everything in this file was executed in the project's reference environment (Linux x86_64) unless marked
**unverified**.

## Prerequisites

| Tool | Version verified | Notes |
|---|---|---|
| JDK | 21.0.12 (upstream CI uses 17) | Gradle toolchains are not forced; any JDK ≥ 17 should work, only 21 verified |
| Gradle | wrapper (`gradle/wrapper/gradle-wrapper.properties`): 9.2.0 | Use `./gradlew`; downloads itself |
| Android SDK cmdline-tools | `14742923` (sha256 `04453066b540409d975c676d781da1477479dde3761310f1a7eb92a1dfb15af7` verified before use) | Upstream's `tools.versions.toml` at this baseline pins the older `13114758`; the older pin was not tested here |
| SDK packages | `platforms;android-36`, `build-tools;35.0.0` | |

Install SDK packages:

```sh
export ANDROID_HOME=/opt/android-sdk          # any directory
sdkmanager --licenses
sdkmanager "platforms;android-36" "build-tools;35.0.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties   # git-ignored
```

## Commands

```sh
./gradlew assembleDebug         # → app/build/outputs/apk/debug/app-debug.apk   (≈5 min cold)
./gradlew testDebugUnitTest     # JVM unit tests (≈1 min after a build)
```

Debug builds install as `<applicationId>.debug`, so they never replace a release install.

Result of these commands on the unmodified baseline is in `AUDIT.md` §6; results for our changes are in
`TESTING.md` and each phase report.

## Dependency verification

`gradle/verification-metadata.xml` pins SHA-256 checksums of every dependency and plugin. Gradle fails the build if a
downloaded artifact differs. Signature (PGP) verification is **off**; checksums only.

**First real CI run = real cross-check, and it failed (2026-10-12, runs 1-4).** Cause, verified byte-by-byte:
the checksums had been recorded through a Maven Central mirror placed first in the plugin repositories, but real builds
resolve plugin markers from the **Gradle Plugin Portal** first. For five markers that exist on both (Kotlin
`android`/`jvm`/`plugin.compose`/`plugin.serialization`, Kover) the Portal serves its own generated POM (about 680
bytes, same single dependency, no licence/SCM metadata) while Central serves the full POM. The mirror and real Maven
Central were byte-identical (5/5 spot-checked), so this was not a tampered mirror. Those five entries now record the
Portal's checksum and list Central's as `also-trust`. The remaining six markers already matched CI's lookup order.
**Result: CI run 5 (workflow_dispatch on this branch, commit `c92c878`) passed the dependency verification, the
debug build and the unit tests on a GitHub runner with direct downloads** (`BUILD SUCCESSFUL in 10m 10s`, 178 tasks).
The run was still red because the following artifact-upload step failed with "Artifact storage quota has been hit"
(GitHub account storage; it recalculates every 6-12 hours, and old artifacts in any repository of the account count).
Both uploads are now non-fatal. The job stays red only when the debug build or the unit tests fail. While the quota
is exhausted, CI still produces the APK and still does not store it: the "Upload debug APK" step warns and continues.
PR #10 and its merge to `main` (runs `38353170157` and `38357539442`, 2026-10-13) were red for that reason only;
`assembleDebug` and `testDebugUnitTest` succeeded on the runner.

Regenerate after adding/updating a dependency (one task at a time if memory is tight; a combined run was OOM-killed in a
16 GB sandbox). **Do not use `docs/build-env-mirror.gradle.kts` for this** (see its warning), or compare the result
against the Portal:

```sh
./gradlew --write-verification-metadata sha256 assembleDebug testDebugUnitTest
./gradlew --write-verification-metadata sha256 assembleRelease
```

## Known environment issue: HTTP 429 from Maven Central

Some shared networks get `429 Too Many Requests` from `repo.maven.apache.org`. This is not a project problem. A
**local, uncommitted** workaround that worked in the reference environment is a user-level init script
`~/.gradle/init.d/central-mirror.gradle.kts` that prepends a mirror to `pluginManagement` and rewrites the Central URL
(see the script text in `docs/build-env-mirror.gradle.kts`). Do not commit mirror URLs into `settings.gradle.kts`.
Dependency verification (Phase 1) protects against a mirror serving different bytes.

## Not verified

* Installing or running the APK on a device/emulator (no device or KVM in the reference environment).
* Release (`assembleRelease`) builds and signing — Phase 10.
* Windows/macOS hosts.
