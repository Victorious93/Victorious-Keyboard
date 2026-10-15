#!/usr/bin/env python3
"""Layout renders for the suggestion strip, tools panel, and slash commands. Not device photos."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
BG = (5, 5, 5)
KEY = (22, 22, 22)
LEGEND = (232, 232, 232)
MUTED = (154, 154, 154)
ENTER = (58, 58, 58)
LINE = (232, 232, 232)


def font(size):
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ):
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def label(draw, xy, text, fill=MUTED, size=18):
    draw.text(xy, text, fill=fill, font=font(size))


def keyboard(draw, top, words):
    rows = ["qwertyuiop", "asdfghjkl", "zxcvbnm"]
    y = top
    for row in rows:
        x = 24
        for ch in row:
            draw.rounded_rectangle((x, y, x + 48, y + 54), 5, fill=KEY)
            label(draw, (x + 16, y + 16), ch, LEGEND, 20)
            x += 54
        y += 62
    draw.rounded_rectangle((24, y, 520, y + 54), 5, fill=ENTER)
    label(draw, (230, y + 16), "space", LEGEND, 18)


def strip(path):
    img = Image.new("RGB", (560, 520), BG)
    draw = ImageDraw.Draw(img)
    label(draw, (16, 8), "Layout render, not a device photo", MUTED, 14)
    draw.rounded_rectangle((8, 36, 56, 92), 6, fill=KEY)
    label(draw, (18, 52), "▦", LEGEND, 22)
    chips = [("github", True), ("ghost", False), ("giving", False), ("girl", False)]
    x = 64
    for text, primary in chips:
        draw.rounded_rectangle((x, 36, x + 110, 92), 8, fill=(40, 40, 40) if primary else KEY)
        label(draw, (x + 12, 52), text, LEGEND, 18)
        if primary:
            draw.rectangle((x + 16, 84, x + 94, 88), fill=LINE)
        x += 118
    keyboard(draw, 110, None)
    img.save(path)


def tools(path):
    img = Image.new("RGB", (560, 640), BG)
    draw = ImageDraw.Draw(img)
    label(draw, (16, 8), "Layout render, not a device photo", MUTED, 14)
    draw.rounded_rectangle((8, 36, 56, 84), 6, fill=KEY)
    label(draw, (16, 48), "▦", LEGEND, 20)
    label(draw, (70, 50), "github", LEGEND, 18)
    y = 100
    groups = [
        ("Clipboard", ["Clips", "Paste", "Copy", "Cut"]),
        ("Clip editor", ["Edit"]),
        ("Vault", ["Vault"]),
        ("AI", ["Rewrite", "Grammar", "Brief", "Ask"]),
        ("Terminal keys", ["Ctrl", "Alt", "Esc", "Tab"]),
        ("Privacy", ["Incog", "Learn", "Theme", "Settings"]),
    ]
    for title, tiles in groups:
        label(draw, (16, y), title, MUTED, 14)
        y += 22
        x = 16
        for tile in tiles:
            draw.rounded_rectangle((x, y, x + 120, y + 44), 6, fill=KEY)
            label(draw, (x + 10, y + 12), tile, LEGEND, 16)
            x += 128
        y += 56
    label(draw, (420, 48), "Keyboard", LEGEND, 16)
    img.save(path)


def suggestions(path):
    img = Image.new("RGB", (420, 640), (12, 12, 14))
    draw = ImageDraw.Draw(img)
    label(draw, (16, 12), "Layout render, not a device photo", MUTED, 14)
    label(draw, (16, 48), "My suggestions", LEGEND, 28)
    label(draw, (16, 96), "Add a suggestion", LINE, 18)
    rows = [("github.com/me", "shortcut: gh"), ("hello there", "matches the phrase")]
    y = 150
    for title, summary in rows:
        draw.rounded_rectangle((16, y, 400, y + 72), 8, fill=KEY)
        label(draw, (28, y + 12), title, LEGEND, 18)
        label(draw, (28, y + 40), summary, MUTED, 14)
        y += 84
    img.save(path)


def slash(path):
    img = Image.new("RGB", (560, 420), BG)
    draw = ImageDraw.Draw(img)
    label(draw, (16, 8), "Layout render, not a device photo", MUTED, 14)
    label(draw, (16, 40), "field:  /sh", MUTED, 16)
    chips = ["/shell …", "/snip", "/sum"]
    x = 16
    for i, text in enumerate(chips):
        draw.rounded_rectangle((x, 72, x + 150, 128), 8, fill=(40, 40, 40) if i == 0 else KEY)
        label(draw, (x + 12, 90), text, LEGEND, 18)
        if i == 0:
            draw.rectangle((x + 16, 118, x + 130, 122), fill=LINE)
        x += 160
    label(draw, (16, 160), "/shell inserts a command. It is not run.", MUTED, 16)
    keyboard(draw, 200, None)
    img.save(path)


if __name__ == "__main__":
    strip(OUT / "suggestion-strip.png")
    tools(OUT / "tools-panel.png")
    suggestions(OUT / "my-suggestions.png")
    slash(OUT / "slash-commands.png")
    print("wrote", OUT)
