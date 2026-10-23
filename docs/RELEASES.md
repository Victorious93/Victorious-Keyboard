# Releases

Status: **policy only. No release workflow in this repository creates releases; the debug releases that exist were published by hand or by an automation outside the repo. Nothing here has been run.**

## What the Releases page is for

The in-app "Search for Updates" button and the "Get more" entries for themes and layouts open this repository's Releases page (`ProjectUrls.RELEASES`). A person who lands there wants to know which file to install. By 2026-10-23 the page held 34 pre-releases named `v0.1.0-debug-<commit>`, one per build, and no real release, so the answer was not visible.

## Policy

1. **A release is a version someone is meant to install.** Tag it `v<versionName>` (for example `v0.1.0`), mark it as the latest release, and attach the signed APK, its SHA-256 and a short note of what was and was not tested.
2. **Debug builds do not get a release each.** There is one rolling pre-release, tagged `debug-latest`. Publishing a new debug build replaces the APK on that release and rewrites its note (commit, `versionName`, `versionCode`, SHA-256, tests run, "not run on a device"). Older debug builds are not kept; the commit is the record, and CI can rebuild it.
3. **Never reuse a `versionCode`.** The rolling release does not change that rule: each debug build still carries a higher `versionCode` than the one before (security invariant 8).
4. **Pack files are separate.** Dictionary packs, themes and the like are release assets under tags named `packs-<id>-<version>` (see `docs/PACKS.md`). They are not app releases and are never marked as the latest release.
5. **Every release note says what was not run.** A debug build is "not installed on a device" unless the owner says otherwise.

## Cleaning up the existing debug releases

Deleting a release or tag cannot be undone, and the AI sessions that work on this repository cannot do it. The owner can, with `gh` on their own machine. List first and check the output before deleting anything:

```sh
# Show every debug release except the newest one (these are the candidates):
gh release list --repo Victorious93/Victorious-Keyboard --limit 100 \
  --json tagName,publishedAt \
  --jq 'map(select(.tagName | startswith("v0.1.0-debug-"))) | sort_by(.publishedAt) | reverse | .[1:] | .[].tagName'
```

When the list is right, run the same command through a loop that deletes each tag and its release:

```sh
gh release list --repo Victorious93/Victorious-Keyboard --limit 100 \
  --json tagName,publishedAt \
  --jq 'map(select(.tagName | startswith("v0.1.0-debug-"))) | sort_by(.publishedAt) | reverse | .[1:] | .[].tagName' \
| while read -r tag; do
    gh release delete "$tag" --repo Victorious93/Victorious-Keyboard --cleanup-tag --yes
  done
```

To keep the newest three instead of one, change `.[1:]` to `.[3:]`. Releases that are not debug builds are never matched.

## Not done

* No workflow publishes the rolling release; today it would be done by hand or by the outside automation. A workflow is a separate change and needs the owner's decision on who may publish.
* The 34 existing debug releases are still there until the owner deletes them.
