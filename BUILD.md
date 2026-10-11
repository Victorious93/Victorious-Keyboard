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

`gradle/verification-metadata.xml` pins SHA-256 checksums of every dependency and plugin (809 components at the time
of writing). Gradle fails the build if a downloaded artifact differs. The checksums were generated
trust-on-first-use **through a mirror** (see below); the first CI run against Maven Central directly is therefore an
independent cross-check — if it fails with a checksum mismatch, treat it as a real supply-chain signal and
investigate before regenerating.

Regenerate after adding/updating a dependency (run the tasks that resolve the relevant classpaths, one at a time if
memory is tight — a combined run was OOM-killed in a 16 GB sandbox):

```sh
./gradlew --write-verification-metadata sha256 assembleDebug testDebugUnitTest
./gradlew --write-verification-metadata sha256 assembleRelease
```

Signature (PGP) verification is **off**; checksums only.

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
