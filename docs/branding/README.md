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
