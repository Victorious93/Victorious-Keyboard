#!/usr/bin/env python3
# Copyright (C) 2026 Victorious Keyboard Contributors
# Licensed under the Apache License, Version 2.0 (see /LICENSE).
"""
Builds the Victorious Keyboard launcher icons from the master logo, docs/branding/logo.png.

Writes, relative to the repository root:

  app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_app_icon_foreground.png
      Adaptive-icon foreground: 108dp canvas, transparent outside the artwork.
  app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_app_icon_monochrome.png
      Themed-icon layer (Android 13+): white, alpha taken from the artwork's brightness.
  docs/branding/play_store_icon_512.png
      Play listing icon, 512x512, filled with the background colour (Play applies its own mask).

How it works:
  * The disc in logo.png is exactly #11151C, which is also @color/ic_app_icon_background. The disc is removed from
    the foreground (set transparent), so the launcher's own background shows through and no disc edge can appear.
  * The artwork is centred on its own pixels (the figure sits above the disc centre) and scaled so that its farthest
    pixel is 31dp from the canvas centre. The adaptive safe zone is a 33dp radius; the 31dp margin is the same one
    used by the reference icon set this structure follows.

The source is only 117x117 px, so the xxxhdpi/Play outputs are upscaled. A larger master (1024 px or an SVG) gives
sharper icons: replace docs/branding/logo.png and run this script again.

Usage (Pillow required, pure Pillow otherwise):  python3 -I docs/branding/make_icons.py
"""
import os
import sys

from PIL import Image, ImageChops

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
MASTER = os.path.join(ROOT, "docs", "branding", "logo.png")
RES = os.path.join(ROOT, "app", "src", "main", "res")
PLAY_OUT = os.path.join(ROOT, "docs", "branding", "play_store_icon_512.png")

DISC = (0x11, 0x15, 0x1C)  # disc colour of logo.png; equals @color/ic_app_icon_background
DISC_TOLERANCE = 10  # max per-channel difference that still counts as disc
EMBLEM_RADIUS = 31 / 108  # farthest artwork pixel, as a fraction of the canvas size
OPAQUE = 128  # alpha threshold for "artwork pixel" when measuring the radius

DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def remove_disc(master):
    """Return an RGBA image where the transparent area and the disc colour are alpha 0."""
    out = master.copy()
    px = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            near_disc = max(abs(r - DISC[0]), abs(g - DISC[1]), abs(b - DISC[2])) <= DISC_TOLERANCE
            if a == 0 or near_disc:
                px[x, y] = (0, 0, 0, 0)
    return out


def artwork_centre_and_radius(art):
    """Centre of the artwork's bounding box, and the distance from it to the farthest artwork pixel."""
    w, h = art.size
    px = art.load()
    points = [(x, y) for y in range(h) for x in range(w) if px[x, y][3] >= OPAQUE]
    if not points:
        sys.exit("make_icons: no artwork left after removing the disc; check docs/branding/logo.png")
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    cx = (min(xs) + max(xs) + 1) / 2
    cy = (min(ys) + max(ys) + 1) / 2
    radius = max(((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5 for x, y in points)
    return cx, cy, radius


def fit_layer(art, size):
    """Transparent square layer of `size` px with the artwork scaled and centred on its own bounds."""
    cx, cy, radius = artwork_centre_and_radius(art)
    scale = EMBLEM_RADIUS * size / radius
    scaled_w = max(1, round(art.width * scale))
    scaled_h = max(1, round(art.height * scale))
    scaled = art.resize((scaled_w, scaled_h), Image.LANCZOS)
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    # Single layer over a transparent canvas: a plain paste replaces the transparent pixels exactly.
    layer.paste(scaled, (round(size / 2 - cx * scale), round(size / 2 - cy * scale)))
    return layer


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
    master = Image.open(MASTER).convert("RGBA")
    art = remove_disc(master)

    for name, size in DENSITIES.items():
        out_dir = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(out_dir, exist_ok=True)
        foreground = fit_layer(art, size)
        foreground.save(os.path.join(out_dir, "ic_app_icon_foreground.png"), optimize=True)
        monochrome(foreground).save(os.path.join(out_dir, "ic_app_icon_monochrome.png"), optimize=True)
        print(f"mipmap-{name}: {size}x{size}")

    play = Image.new("RGBA", (512, 512), DISC + (255,))
    play.alpha_composite(fit_layer(art, 512))
    play.convert("RGB").save(PLAY_OUT, optimize=True)
    print(f"play store icon: {PLAY_OUT}")


if __name__ == "__main__":
    main()
