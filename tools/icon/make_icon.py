#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.
"""
SCARY's launcher icon as an adaptive icon: a background that fills the whole shape and the "S" on its own layer.

A home screen cuts an adaptive icon to its own shape (Google TV: a circle). The old icon was one square picture with
see-through corners, which Google TV put on a white plate inside its circle. Android's layers are 108 dp square; only
the middle 72 dp is ever seen, and the middle 66 dp circle is safe in every shape, so the "S" stays inside it.

Run from the repository's root: python3 tools/icon/make_icon.py
Writes app/src/main/res/mipmap-{xhdpi,xxxhdpi}/ic_launcher_{background,foreground,monochrome}.png, which
mipmap-anydpi/ic_launcher.xml puts together. SCARY runs on Android 8 and later, where the layered icon is always the
one used, so no single-picture icon is kept.
"""
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
FONT = RES / "font/rubik_wet_paint.ttf"

BONE = (233, 225, 207, 255)
FEAR = (217, 64, 64, 255)
BLOOD = (142, 27, 27, 255)
CENTRE = (104, 24, 24)
EDGE = (16, 7, 8)

LAYER_DP = 108
SAFE_DP = 66
# Work at four times the largest size written, then scale down: smooth edges at every density.
WORK = 432 * 4
DENSITIES = {"xhdpi": 216, "xxxhdpi": 432}


def background(size: int) -> Image.Image:
    """Dark red in the middle, near black at the edge: the glow behind the letter."""
    small = 256
    image = Image.new("RGB", (small, small))
    pixels = image.load()
    middle = (small - 1) / 2
    reach = middle * 1.05
    for y in range(small):
        for x in range(small):
            t = min(1.0, (((x - middle) ** 2 + (y - middle * 0.92) ** 2) ** 0.5) / reach)
            t = t * t * (3 - 2 * t)
            pixels[x, y] = tuple(round(c + (e - c) * t) for c, e in zip(CENTRE, EDGE))
    return image.resize((size, size), Image.LANCZOS)


def letter_mask() -> Image.Image:
    """The "S" (with the font's drips), centred, its height a little under the safe circle."""
    safe = WORK * SAFE_DP / LAYER_DP
    font_size = 100
    font = ImageFont.truetype(str(FONT), font_size)
    left, top, right, bottom = font.getbbox("S")
    font = ImageFont.truetype(str(FONT), round(font_size * safe * 0.74 / (bottom - top)))
    left, top, right, bottom = font.getbbox("S")
    mask = Image.new("L", (WORK, WORK), 0)
    x = (WORK - (right - left)) / 2 - left
    y = (WORK - (bottom - top)) / 2 - top
    ImageDraw.Draw(mask).text((x, y), "S", font=font, fill=255)
    return mask


def foreground() -> Image.Image:
    """The bone "S" over its red shadow, pushed down and to the left like the letter in the app."""
    mask = letter_mask()
    step = WORK // 108
    layer = Image.new("RGBA", (WORK, WORK), (0, 0, 0, 0))
    # A soft dark halo first, so the letter stands off the glow.
    halo = ImageChops.offset(mask, -step * 2, step * 3).filter(ImageFilter.GaussianBlur(step * 3))
    layer.paste((0, 0, 0, 150), mask=halo)
    for i in range(4, 0, -1):
        colour = BLOOD if i > 2 else FEAR
        layer.paste(colour, mask=ImageChops.offset(mask, -step * i // 2 - step // 2, step * i // 2 + step // 2))
    layer.paste(BONE, mask=mask)
    return layer


def main() -> None:
    letter = foreground()
    mono = Image.new("RGBA", (WORK, WORK), (255, 255, 255, 0))
    mono.putalpha(letter_mask())
    for density, size in DENSITIES.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        background(size).save(folder / "ic_launcher_background.png", optimize=True)
        letter.resize((size, size), Image.LANCZOS).save(folder / "ic_launcher_foreground.png", optimize=True)
        mono.resize((size, size), Image.LANCZOS).save(folder / "ic_launcher_monochrome.png", optimize=True)


if __name__ == "__main__":
    main()
