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
| NDK / CMake / Rust | NDK `26.1.10909125`, CMake `4.0.2`, rustup | **Only until `:lib:native` is removed (Phase 1).** |

Install SDK packages:

```sh
export ANDROID_HOME=/opt/android-sdk          # any directory
sdkmanager --licenses
sdkmanager "platforms;android-36" "build-tools;35.0.0" "ndk;26.1.10909125" "cmake;4.0.2"
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
