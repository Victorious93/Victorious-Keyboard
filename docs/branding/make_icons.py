#!/usr/bin/env python3
# Copyright (C) 2026 Victorious Keyboard Contributors
# Licensed under the Apache License, Version 2.0 (see /LICENSE).
"""
Builds the Victorious Keyboard launcher icons from the master logo, docs/branding/logo.png.

Writes, relative to the repository root:

  app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_app_icon_foreground.png
      Adaptive-icon foreground: 108dp canvas, transparent where the logo is flat navy.
  app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_app_icon_monochrome.png
      Themed-icon layer (Android 13+): white, alpha taken from the artwork's brightness.
  docs/branding/play_store_icon_512.png
      Play listing icon, 512x512. The approved composition, navy included (Play applies its own mask).

How it works:
  * logo.png is the 720x720 approved mark. Its flat field is dark navy #010619, the same value as
    @color/ic_app_icon_background. Those pixels are cleared on the adaptive foreground so the
    launcher background shows through and the ring has no square edge.
  * The neon (ring, figure, keyboard, prompt, V) is scaled so its farthest pixel sits 32dp from
    the canvas centre. The adaptive safe zone is a 33dp radius; the script refuses to write a
    layer that sticks out past that.

Usage (Pillow required):  python3 docs/branding/make_icons.py
"""
import os
import sys

from PIL import Image, ImageChops

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
MASTER = os.path.join(ROOT, "docs", "branding", "logo.png")
RES = os.path.join(ROOT, "app", "src", "main", "res")
PLAY_OUT = os.path.join(ROOT, "docs", "branding", "play_store_icon_512.png")

# Flat navy of logo.png. Equals @color/ic_app_icon_background.
NAVY = (0x01, 0x06, 0x19)
NAVY_TOLERANCE = 12  # max per-channel difference that still counts as the flat field
# Farthest artwork pixel, as a fraction of the 108dp canvas. Safe zone radius is 33dp.
EMBLEM_RADIUS = 32 / 108
SAFE_RADIUS = 33 / 108
OPAQUE = 128

DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def strip_navy(master):
    """Return an RGBA image. Flat navy (and any existing transparency) becomes alpha 0."""
    out = master.convert("RGBA")
    px = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            near = max(abs(r - NAVY[0]), abs(g - NAVY[1]), abs(b - NAVY[2])) <= NAVY_TOLERANCE
            if a == 0 or near:
                px[x, y] = (0, 0, 0, 0)
    return out


def artwork_centre_and_radius(art):
    """Centre of the artwork's bounding box, and the distance from it to the farthest artwork pixel."""
    w, h = art.size
    px = art.load()
    points = [(x, y) for y in range(h) for x in range(w) if px[x, y][3] >= OPAQUE]
    if not points:
        sys.exit("make_icons: no artwork left after removing the navy field; check docs/branding/logo.png")
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    cx = (min(xs) + max(xs) + 1) / 2
    cy = (min(ys) + max(ys) + 1) / 2
    radius = max(((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5 for x, y in points)
    return cx, cy, radius


def fit_layer(art, size):
    """Transparent square layer of `size` px. Artwork is scaled so it stays inside the safe zone."""
    cx, cy, radius = artwork_centre_and_radius(art)
    scale = EMBLEM_RADIUS * size / radius
    scaled_w = max(1, round(art.width * scale))
    scaled_h = max(1, round(art.height * scale))
    scaled = art.resize((scaled_w, scaled_h), Image.LANCZOS)
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    layer.paste(scaled, (round(size / 2 - cx * scale), round(size / 2 - cy * scale)), scaled)
    _assert_inside_safe_zone(layer, size)
    return layer


def _assert_inside_safe_zone(layer, size):
    px = layer.load()
    limit = SAFE_RADIUS * size + 0.6  # half a pixel of resampling slack
    cx = cy = size / 2
    farthest = 0.0
    for y in range(size):
        for x in range(size):
            if px[x, y][3] < OPAQUE:
                continue
            farthest = max(farthest, ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5)
    dp = farthest / size * 108
    if farthest > limit:
        sys.exit(f"make_icons: artwork reaches {dp:.1f}dp from centre; safe zone is 33dp")
    print(f"  farthest opaque pixel: {dp:.1f}dp from centre (safe zone 33dp)")


def monochrome(layer):
    """White silhouette whose alpha follows the artwork's brightness (the neon edges stay visible)."""
    r, g, b, a = layer.split()
    brightest = ImageChops.lighter(ImageChops.lighter(r, g), b)
    ramp = [round(255 * min(1.0, max(0.0, (v / 255 - 0.12) / 0.45))) for v in range(256)]
    alpha = ImageChops.multiply(brightest.point(ramp), a)
    white = Image.new("L", layer.size, 255)
    return Image.merge("RGBA", (white, white, white, alpha))


def main():
    if not os.path.isfile(MASTER):
        sys.exit(f"make_icons: master logo missing: {MASTER}")
    master = Image.open(MASTER)
    if master.size[0] != master.size[1]:
        sys.exit(f"make_icons: logo.png must be square, got {master.size}")
    art = strip_navy(master)

    for name, size in DENSITIES.items():
        out_dir = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(out_dir, exist_ok=True)
        foreground = fit_layer(art, size)
        foreground.save(os.path.join(out_dir, "ic_app_icon_foreground.png"), optimize=True)
        monochrome(foreground).save(os.path.join(out_dir, "ic_app_icon_monochrome.png"), optimize=True)
        print(f"mipmap-{name}: {size}x{size}")

    # Play listing keeps the approved framing: the ring sits inside the square, navy fills the rest.
    play = master.convert("RGB").resize((512, 512), Image.LANCZOS)
    play.save(PLAY_OUT, optimize=True)
    print(f"play store icon: {PLAY_OUT}")


if __name__ == "__main__":
    main()
