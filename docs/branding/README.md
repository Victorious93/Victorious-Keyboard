# Branding

* `logo.png`: the project logo, supplied by the repository owner. 117 × 117 px RGBA, transparent outside a near-black
  disc of exactly `#11151C`. It is the master for the launcher icons and the README header.
* `make_icons.py`: builds all icon outputs from `logo.png`:
  * `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_app_icon_foreground.png`: adaptive-icon foreground (108 dp canvas);
  * `app/src/main/res/mipmap-*/ic_app_icon_monochrome.png`: themed-icon layer for Android 13 and newer;
  * `play_store_icon_512.png`: Play listing icon, 512 × 512, background filled (Play applies its own mask).

  Run `python3 -I docs/branding/make_icons.py` (needs Pillow: `pip install pillow`). Run it again after changing `logo.png`.
* `app/src/main/res/mipmap-anydpi-v26/ic_app_icon_{stable,debug}{,_round}.xml` combine the background colour
  `@color/ic_app_icon_background` (`#11151C`, the same as the disc) with those two layers. Stable and debug builds share
  the artwork; debug builds are told apart by the `.debug` package suffix and the "Victorious Keyboard Debug" label.

The artwork is 117 px, so the density outputs are upscaled and will look soft at xxxhdpi and on the Play listing. For
sharper icons, replace `logo.png` with a square PNG of at least 1024 px and run the script again.

The script scales the artwork so its farthest pixel is 31 dp from the centre (the adaptive safe zone is 33 dp).
The `DroidCommand AI` launcher set supplied alongside the request was used only as a *structure* template (adaptive icon
with foreground, monochrome and background, and the same density set); none of its artwork is in this repository.

Checks done: the outputs were composed into launcher-style masks (circle, rounded square, square) and a monochrome
preview, and inspected by eye. The debug APK packages all ten layers, and `aapt2` resolves the launcher and splash
references to them. **Not done:** viewing them on a real launcher, with themed (monochrome) icons enabled, or in the
Play Console.

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
