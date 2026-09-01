#!/usr/bin/env python3
"""Regenerate the BrightSteps launcher mark: four ascending bars, a day's steps climbing.

Emits the adaptive-icon vector foreground and the legacy PNG mipmaps, white on black, in the
108 canvas / 18..90 safe zone of the unified Bright* set. Run from the repo root:

    python3 scripts/generate_icon.py
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"

# (x0, y0, x1, y1) in the 108 viewport; four bars rising left to right within the safe zone.
BARS = [(22, 64, 34, 84), (39, 52, 51, 84), (56, 40, 68, 84), (73, 28, 85, 84)]
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def write_vector() -> None:
    paths = "\n".join(
        f'    <path android:fillColor="#FFFFFF" '
        f'android:pathData="M{x0},{y0} H{x1} V{y1} H{x0} Z" />'
        for (x0, y0, x1, y1) in BARS
    )
    (RES / "drawable/ic_launcher_foreground.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!--\n"
        "  BrightSteps launcher mark: four ascending bars, a day's steps climbing. One of the\n"
        "  unified Bright* set: 108 canvas, 18..90 safe zone, white on black, no colour.\n"
        "-->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp" android:height="108dp"\n'
        '    android:viewportWidth="108" android:viewportHeight="108">\n'
        f"{paths}\n"
        "</vector>\n"
    )


def write_pngs() -> None:
    for dens, size in DENSITIES.items():
        k = size / 108.0
        for rnd in (False, True):
            img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
            d = ImageDraw.Draw(img)
            if rnd:
                d.ellipse([0, 0, size - 1, size - 1], fill=(0, 0, 0, 255))
            else:
                d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.14), fill=(0, 0, 0, 255))
            for (x0, y0, x1, y1) in BARS:
                d.rectangle([x0 * k, y0 * k, x1 * k, y1 * k], fill=(255, 255, 255, 255))
            name = "ic_launcher_round.png" if rnd else "ic_launcher.png"
            img.save(RES / f"mipmap-{dens}/{name}")


if __name__ == "__main__":
    write_vector()
    write_pngs()
    print("BrightSteps icon regenerated")
