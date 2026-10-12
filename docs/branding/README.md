# Branding

* `logo.svg`: master art (stable build, cyan accent); `logo_debug.svg`: debug build (amber accent).
* `play_store_icon_512.png`: 512×512 raster of `logo.svg` (rendered with headless Chromium; Play applies its own mask).
* `make_logo.py`: the single source of the geometry. It writes the four Android vector drawables
  (`app/src/main/res/drawable/ic_app_icon_{stable,debug}_{foreground,monochrome}.xml`) and the two SVGs:
  `python3 -I docs/branding/make_logo.py`. Do not hand-edit the generated XML.

The mark is original artwork (a hooded figure, neon-edged face with a `>_` prompt for eyes, a red-glowing laptop lid
carrying the "V"). It is inspired by the mood of a reference wallpaper; none of that image is reused.
The `DroidCommand AI` launcher set supplied alongside the request was used only as a *structure* template (adaptive icon
with foreground/monochrome/background); none of its artwork is in this repository.

Checks done: all outline control points inside the 33dp safe zone (asserted by the script); the debug APK built with
the new vectors. **Not done:** viewing it on a real launcher or with themed (monochrome) icons enabled.

## What still says "Floris", and why

Victorious Keyboard is a **derivative of FlorisBoard** (Apache-2.0). It cannot honestly claim to be code-original; what
it can and should do is carry its own name and not present upstream's identity as its own. Status of the built APK
(`aapt2 dump resources` + the bundled theme JSON, checked on the debug build):

| Item | State |
|---|---|
| App label, IME name, settings title, About screen | "Victorious Keyboard" (was already; a stray Albanian override "TastieraFloris" removed) |
| Theme names | now "Victorious Day / Night / Pure Night …"; theme *ids* (`org.florisboard.themes`, `floris_day`) unchanged |
| "FlorisBoard App Assets" label, extension-store sentence, crash titles in 20+ locales | English text rebranded; the translated copies that said FlorisBoard were deleted so those locales fall back to English until retranslated |
| "Visit Addons Store" button (opened upstream's `beta.addons.florisboard.org`) | **removed**; Extensions screen now only offers import |
| Typing-screen feedback link (upstream discussion thread) | now points to this repository's issue tracker |
| Two help links to upstream wiki pages (spell-checker service, theme editor) | **kept**: they document inherited features; replace when we have our own docs |
| Identifiers: Kotlin package `dev.patrickgold.florisboard`, extension ids `org.florisboard.*`, preference keys, `floris_user_dictionary` file, `florisboard://` URI scheme, MIME types | **kept on purpose**: renaming would corrupt stored settings/extensions and make upstream merges unmanageable (`CLAUDE.md`). Not user-visible |
| Copyright headers, `LICENSE`, About → licences | **kept, required** by Apache-2.0 §4 |

Credit: Settings → About now has a "Based on FlorisBoard" entry (names The FlorisBoard Contributors, Apache 2.0,
baseline `v0.6.0-alpha02`, opens upstream's repository). English only for now.

Not yet done: retranslating the removed strings and a pass over inherited screenshots/docs in `docs/upstream/`.
