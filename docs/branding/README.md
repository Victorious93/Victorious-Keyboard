# Branding

* `logo.png`: the project logo, supplied by the repository owner. 720 × 720 px RGB. Flat field is dark navy
  `#010619`. It is the master for the launcher icons and the README header. A lossless recompress of this file
  saves about 10 KB, so there is no separate optimized copy.
* `make_icons.py`: builds all icon outputs from `logo.png`:
  * `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_app_icon_foreground.png`: adaptive-icon foreground (108 dp canvas);
  * `app/src/main/res/mipmap-*/ic_app_icon_monochrome.png`: themed-icon layer for Android 13 and newer;
  * `play_store_icon_512.png`: Play listing icon, 512 × 512, background filled (Play applies its own mask).

  Run `python3 -I docs/branding/make_icons.py` (needs Pillow: `pip install pillow`). Run it again after changing `logo.png`.
* `app/src/main/res/mipmap-anydpi-v26/ic_app_icon_{stable,debug}{,_round}.xml` combine the background colour
  `@color/ic_app_icon_background` (`#010619`, the logo's navy) with those two layers. The round icons are these
  adaptive XML files; `minSdk` is 26, so there is no pre-adaptive bitmap. Stable and debug builds share the artwork;
  debug builds are told apart by the `.debug` package suffix and the "Victorious Keyboard Debug" label.

The master is 720 px, so the density outputs and the 512 px Play icon are downscales, not enlargements. The README
header displays that file at 240 × 240 CSS pixels (at most 720 device pixels on a 3× screen).

The script scales the neon so its farthest pixel is 32 dp from the centre. The adaptive safe zone is a 33 dp radius,
and the script stops if any opaque pixel lands outside it. The Play icon keeps the approved framing instead of that
inset, because Play's mask is a circle of the full 512 px image and the ring already sits inside it.
The `DroidCommand AI` launcher set supplied alongside the request was used only as a *structure* template (adaptive icon
with foreground, monochrome and background, and the same density set); none of its artwork is in this repository.

Checks done: the outputs were composed into launcher-style masks (circle, rounded square) and a monochrome
preview, and inspected by eye. The generator reports the farthest opaque pixel at 31.8–32.3 dp from the centre,
inside the 33 dp safe zone. The debug APK packages the foreground and monochrome layer at every density, the four
adaptive XML icons (stable, debug, and both round icons), and `aapt2` resolves the launcher and splash references
to them. **Not done:** viewing them on a real launcher, with themed (monochrome) icons enabled, or in the Play Console.

## What still says "Floris", and why

Victorious Keyboard is a **derivative of FlorisBoard** (Apache-2.0). It cannot honestly claim to be code-original; what
it can and should do is carry its own name and not present upstream's identity as its own. Status of the built APK
(`aapt2 dump resources` + the bundled theme JSON, checked on the debug build):

| Item | State |
|---|---|
| App label, IME name, settings title, About screen | "Victorious Keyboard" (was already; a stray Albanian override "TastieraFloris" removed) |
| Theme names | "Victorious Neon" and "Victorious Neon Light" are the defaults. Older entries stay selectable as "Victorious Day / Night / Pure Night …". The theme extension id is `com.victorious.keyboard.themes`. Theme component ids such as `floris_day` and `victorious_black` are unchanged. A saved `org.florisboard.themes:…` id still loads |
| "FlorisBoard App Assets" label, extension-store sentence, crash titles in 20+ locales | English text rebranded; the translated copies that said FlorisBoard were deleted so those locales fall back to English until retranslated |
| "Visit Addons Store" button (opened upstream's `beta.addons.florisboard.org`) | **removed**; Extensions screen now only offers import |
| Typing-screen feedback link (upstream discussion thread) | now points to this repository's issue tracker |
| Spell-checker and theme-editor help links | Open the in-app manual (`ui://florisboard/settings/manual`), which is also Settings → Help |
| Kotlin package | `com.victorious.keyboard`. The input method, spell checker, screenshot helper, and launcher alias keep the previous component names so an update does not turn them off |
| Identifiers: preference keys, `floris_user_dictionary` file, `florisboard://` URI scheme, MIME types | **kept on purpose**: renaming them would corrupt stored settings. Not user-visible |
| Built-in extension ids | Settings shows `com.victorious.keyboard.*`. Each pack lists its old `org.florisboard.*` id as an alias, so saved themes, layouts, and subtypes still resolve. Asset folders keep the old names |
| Copyright headers, `LICENSE`, About → licences | **kept, required** by Apache-2.0 §4 |

Credit: Settings → About still has a "Based on FlorisBoard" entry (names The FlorisBoard Contributors, Apache 2.0,
baseline `v0.6.0-alpha02`). It opens the in-app licence screen. English only for now.

Settings → Keyboard extensions, Language pack extensions, and Theme extensions list the eight built-in packs
as Victorious packs, maintained by Victorious93, with homepage and issue links to
https://github.com/Victorious93/Victorious-Keyboard. The Chinese shape pack tells readers, in Chinese and English,
to get more input methods from that repository.

Not yet done: retranslating the removed strings and a pass over inherited screenshots/docs in `docs/upstream/`.
