#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.
"""
Renders SCARY's video ad (docs/promo/STORYBOARD.md) from the real CI screenshots.

Frames are drawn with Pillow and piped to ffmpeg; the soundtrack is synthesized here with the same drone as the
app's DroneSynth, so the ad holds no third-party music or footage.

    python3 tools/promo/make_ad.py --lang en --out scary-ad-en.mp4
    python3 tools/promo/make_ad.py --lang he --out scary-ad-he.mp4

Screenshots come from the `screenshots` branch (git fetch origin screenshots), or from --shots DIR.
"""
from __future__ import annotations

import argparse
import math
import subprocess
import tempfile
import wave
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H, FPS = 1920, 1080, 30
ROOT = Path(__file__).resolve().parents[2]
FONTS = ROOT / "app/src/main/res/font"

# ScaryColors (app/src/main/java/.../ui/theme/Theme.kt).
VOID = (10, 11, 11)
BONE = (233, 225, 207)
ASH = (163, 158, 146)
BLOOD = (142, 27, 27)
CANDLE = (224, 169, 74)

TEXT = {
    "en": {
        "open1": "Some home screens…",
        "open2": "should come with a warning.",
        "tagline": "The horror home screen for Android TV",
        "scenes": [
            ("home", "Tonight's pick", "A new horror film every night"),
            ("shiver", "The shiver", "The title trembles in the dark before the movie opens"),
            ("moods", "What do you feel like tonight?", "Six moods, from mind games to no sleep"),
            ("rows", "Endless rows", "Keep scrolling: thousands of films, refreshed every day"),
            ("details", "Where to watch", "Search the film in the apps you already have"),
            ("search", "Search by text or voice", "In the library and across the web"),
            ("family", "Family night", "Only the gentlest films. A PIN to leave."),
        ],
        "languages": "Five languages",
        "end1": "Free · Open source · Android TV",
        "end2": "github.com/diamondrubinny/Scary",
        "notice": "SCARY hosts no video. Movies open in streaming apps installed on your device.",
    },
    "he": {
        "open1": "יש מסכי בית…",
        "open2": "שצריכים לבוא עם אזהרה.",
        "tagline": "מסך הבית של האימה ל־Android TV",
        "scenes": [
            ("home", "הסרט של הלילה", "סרט אימה חדש בכל ערב"),
            ("shiver", "צמרמורת", "השם רועד בחושך רגע לפני שהסרט נפתח"),
            ("moods", "מה בא לך הלילה?", "שישה מצבי רוח, ממשחקי מוח ועד לא לישון"),
            ("rows", "שורות בלי סוף", "ממשיכים לגלול: אלפי סרטים שמתעדכנים כל יום"),
            ("details", "איפה לצפות", "חיפוש הסרט באפליקציות שכבר מותקנות אצלכם"),
            ("search", "חיפוש בהקלדה או בקול", "במאגר וגם ברשת"),
            ("family", "ערב משפחתי", "רק הסרטים העדינים ביותר. היציאה ב־PIN."),
        ],
        "languages": "חמש שפות",
        "end1": "חינם · קוד פתוח · Android TV",
        "end2": "github.com/diamondrubinny/Scary",
        "notice": "SCARY לא מארחת וידאו. סרטים נפתחים באפליקציות הצפייה שמותקנות במכשיר שלכם.",
    },
}

# CI screenshot per scene and language (.github/scripts/screenshots.sh).
SHOTS = {
    "en": {"home": "40-en-home", "shiver": "58-en-shiver", "moods": "55-en-moods", "rows": "43-en-my-list",
           "details": "41-en-details", "search": "42-en-search-web", "family": "56-en-family-night"},
    "he": {"home": "01-home", "shiver": "37-launch-shiver", "moods": "35-moods", "rows": "04-psychological-rows",
           "details": "05-details", "search": "25-search-web", "family": "36-family-night"},
}
LANGUAGE_CUTS = [("40-en-home", "English", "latin"), ("01-home", "עברית", "he"), ("50-ar-home", "العربية", "ar"),
                 ("14-home-pt", "Português", "latin"), ("17-home-ru", "Русский", "latin")]


def font(name: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / name), size, layout_engine=ImageFont.Layout.RAQM)


class Shots:
    """Screenshots from a directory, or straight from the screenshots branch."""

    def __init__(self, directory: Path | None):
        self.directory = directory
        self.cache: dict[str, Image.Image] = {}
        self.tmp = Path(tempfile.mkdtemp(prefix="scary-ad-"))

    def get(self, name: str) -> Image.Image:
        if name not in self.cache:
            path = self.directory / f"{name}.png" if self.directory else self.tmp / f"{name}.png"
            if not self.directory:
                data = subprocess.run(["git", "-C", str(ROOT), "show", f"origin/screenshots:{name}.png"],
                                      check=True, capture_output=True).stdout
                path.write_bytes(data)
            self.cache[name] = Image.open(path).convert("RGB").resize((W, H), Image.LANCZOS)
        return self.cache[name]


# ---------- Effects ----------

def ease(x: float) -> float:
    x = min(max(x, 0.0), 1.0)
    return 1 - (1 - x) ** 3


def vignette() -> np.ndarray:
    y, x = np.ogrid[:H, :W]
    d = np.sqrt(((x - W / 2) / (W / 2)) ** 2 + ((y - H / 2) / (H / 2)) ** 2)
    return np.clip(1.15 - 0.45 * d ** 2, 0.35, 1.0)[..., None].astype(np.float32)


VIGNETTE = vignette()


def grain(seed: int) -> np.ndarray:
    """Film grain at a third of the resolution, scaled up: it reads as grain and still compresses well."""
    small = np.random.default_rng(seed).normal(128, 30, (H // 3, W // 3)).clip(0, 255).astype(np.uint8)
    big = np.asarray(Image.fromarray(small).resize((W, H), Image.BILINEAR), dtype=np.float32)
    return ((big - 128) * 0.16)[..., None]


GRAIN = [grain(i) for i in range(6)]


def finish(img: Image.Image, frame: int, brightness: float = 1.0) -> bytes:
    """Vignette, film grain and an overall brightness (for the dips between scenes)."""
    a = np.asarray(img, dtype=np.float32) * VIGNETTE * brightness + GRAIN[frame % len(GRAIN)]
    return np.clip(a, 0, 255).astype(np.uint8).tobytes()


def ken_burns(img: Image.Image, p: float, zoom: float = 0.07, pan: float = 0.025, rtl: bool = False,
              dx: float = 0, dy: float = 0, size: tuple[int, int] = (W, H)) -> Image.Image:
    """A slow zoom with a drift away from the text side; dx/dy shake it (the shiver). Never leaves the picture."""
    s = 1 + zoom * (0.25 + 0.75 * p)
    cw, ch = W / s, H / s
    x0 = (W - cw) / 2 + (-1 if rtl else 1) * pan * W * (p - 0.5) + dx
    y0 = (H - ch) / 2 + dy
    x0 = min(max(x0, 0.0), W - cw)
    y0 = min(max(y0, 0.0), H - ch)
    return img.resize(size, Image.BILINEAR, box=(x0, y0, x0 + cw, y0 + ch))


def shiver_offsets(t: float, seed: int, amplitude: float = 9.0, duration: float = 1.1) -> tuple[float, float]:
    if t >= duration:
        return 0.0, 0.0
    rng = np.random.default_rng(seed + int(t / 0.045))
    strength = amplitude * (1 - t / duration)
    return float(rng.uniform(-1, 1) * strength), float(rng.uniform(-1, 1) * strength * 0.5)


def chill_alpha(t: float) -> float:
    """The app's Chill: a 1.4 s reveal, then two dips (to 55% and 70%), never to black."""
    alpha = ease(t / 1.4)
    for start, low in ((1.45, 0.55), (1.95, 0.70)):
        if start <= t < start + 0.07:
            alpha = min(alpha, low)
        elif start + 0.07 <= t < start + 0.19:
            alpha = min(alpha, low + (1 - low) * (t - start - 0.07) / 0.12)
    return alpha


# ---------- Text ----------

class Typo:
    def __init__(self, lang: str):
        self.lang = lang
        self.rtl = lang == "he"
        self.title = font("karantina_bold.ttf", 112) if self.rtl else font("oswald_semibold.ttf", 80)
        self.body = font("assistant_regular.ttf", 42)
        self.body_bold = font("assistant_bold.ttf", 50)
        self.small = font("assistant_regular.ttf", 30)
        self.logo = font("rubik_wet_paint.ttf", 250)
        self.open = font("karantina_bold.ttf", 120) if self.rtl else font("oswald_semibold.ttf", 84)
        # The red bar sits a little above the tallest letter of the title, whatever the font.
        self.bar_y = TITLE_Y + self.title.getbbox("Aל", anchor="ls")[1] - 26

    def kw(self) -> dict:
        return {"direction": "rtl", "language": "he"} if self.rtl else {"language": "en"}


def draw_text(img: Image.Image, xy: tuple[float, float], text: str, f: ImageFont.FreeTypeFont, fill, alpha: float,
              anchor: str, kw: dict, shadow: tuple | None = None, shadow_offset=(0, 4)) -> None:
    if alpha <= 0:
        return
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    a = int(255 * min(alpha, 1.0))
    if shadow:
        d.text((xy[0] + shadow_offset[0], xy[1] + shadow_offset[1]), text, font=f, fill=shadow + (a,), anchor=anchor, **kw)
    d.text(xy, text, font=f, fill=fill + (a,), anchor=anchor, **kw)
    img.paste(layer, (0, 0), layer)


# The screenshot sits in a "screen" over a blurred, darkened copy of itself, with the caption below it.
FW, FH = 1440, 810
FX, FY = (W - FW) // 2, 44
TITLE_Y, SUB_Y = 972, 1036


def rounded_mask(radius: int = 18) -> Image.Image:
    mask = Image.new("L", (FW, FH), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, FW - 1, FH - 1), radius, fill=255)
    return mask


MASK = rounded_mask()


def frame_shadow() -> Image.Image:
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle((FX - 6, FY + 4, FX + FW + 6, FY + FH + 22), 26, fill=(0, 0, 0, 200))
    return layer.filter(ImageFilter.GaussianBlur(28))


SHADOW = None
BACKDROPS: dict[int, Image.Image] = {}


def framed(source: Image.Image, p: float, rtl: bool, zoom: float = 0.06, dx: float = 0, dy: float = 0) -> Image.Image:
    global SHADOW
    if SHADOW is None:
        SHADOW = frame_shadow()
    key = id(source)
    if key not in BACKDROPS:
        blurred = source.resize((W // 4, H // 4), Image.BILINEAR).filter(ImageFilter.GaussianBlur(10))
        BACKDROPS[key] = Image.blend(Image.new("RGB", (W // 4, H // 4), VOID), blurred, 0.32).resize((W, H), Image.BILINEAR)
    img = BACKDROPS[key].copy().convert("RGBA")
    img.alpha_composite(SHADOW)
    screen = ken_burns(source, p, zoom=zoom, rtl=rtl, dx=dx, dy=dy, size=(FW, FH))
    img.paste(screen, (FX, FY), MASK)
    border = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(border).rounded_rectangle((FX, FY, FX + FW - 1, FY + FH - 1), 18, outline=BONE + (46,), width=2)
    img.alpha_composite(border)
    return img.convert("RGB")


def red_bar(img: Image.Image, a: float, y: int) -> None:
    w = int(110 * a)
    if w > 0:
        ImageDraw.Draw(img).rectangle(((W - w) // 2, y, (W + w) // 2, y + 6), fill=BLOOD)


# ---------- Scenes ----------

@dataclass
class Scene:
    duration: float
    draw: Callable[[float], Image.Image]
    fade_in: float = 0.2
    fade_out: float = 0.15


def build(lang: str, shots: Shots) -> list[Scene]:
    tx, ty = TEXT[lang], Typo(lang)
    rtl = ty.rtl
    scenes: list[Scene] = []

    def cold_open(t: float) -> Image.Image:
        img = Image.new("RGB", (W, H), VOID)
        draw_text(img, (W / 2, H / 2 - 30), tx["open1"], ty.open, BONE, ease((t - 0.3) / 0.8), "ms", ty.kw())
        a2 = ease((t - 1.5) / 0.6) * (0.6 if 2.3 < t < 2.37 else 1.0)  # one soft flicker, never a flash
        draw_text(img, (W / 2, H / 2 + 110), tx["open2"], ty.open, CANDLE, a2, "ms", ty.kw())
        return img

    scenes.append(Scene(3.5, cold_open, fade_in=0.4))

    def logo(t: float) -> Image.Image:
        img = Image.new("RGB", (W, H), VOID)
        dx, dy = shiver_offsets(t, 7, amplitude=12)
        scale = 0.96 + 0.04 * ease(t / 1.4)
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        draw_text(layer, (W / 2 + dx, H / 2 + 40 + dy), "SCARY", ty.logo, BONE, chill_alpha(t), "ms", {"language": "en"},
                  shadow=BLOOD, shadow_offset=(0, 18))
        if scale != 1:
            layer = layer.resize((int(W * scale), int(H * scale)), Image.BILINEAR)
            ox, oy = (W - layer.width) // 2, (H - layer.height) // 2
            img.paste(layer, (ox, oy), layer)
        else:
            img.paste(layer, (0, 0), layer)
        draw_text(img, (W / 2, H / 2 + 150), tx["tagline"], ty.body_bold, BONE, ease((t - 1.9) / 0.6), "ms", ty.kw())
        draw_text(img, (W / 2, H / 2 + 210), "Powered by SCARY · Open source", ty.small, ASH, ease((t - 2.3) / 0.6), "ms",
                  {"language": "en"})
        return img

    scenes.append(Scene(3.5, logo, fade_in=0.05))

    for i, (key, title, sub) in enumerate(tx["scenes"]):
        def feature(t: float, key=key, title=title, sub=sub, i=i, d=2.8) -> Image.Image:
            dx = dy = 0.0
            if key == "shiver":
                dx, dy = shiver_offsets(t, 31 + i, amplitude=10)
            img = framed(shots.get(SHOTS[lang][key]), t / d, rtl, dx=dx, dy=dy)
            a = ease((t - 0.15) / 0.35)
            rise = (1 - a) * 20
            red_bar(img, a, ty.bar_y)
            draw_text(img, (W / 2, TITLE_Y + rise), title, ty.title, BONE, a, "ms", ty.kw())
            draw_text(img, (W / 2, SUB_Y + rise), sub, ty.body, ASH, ease((t - 0.45) / 0.35), "ms", ty.kw())
            if key == "shiver":
                # The app's two dips, on the whole frame this time (dims only, never a flash).
                ca = chill_alpha(t + 1.15) if t > 0.25 else 1.0
                img = Image.blend(Image.new("RGB", (W, H), VOID), img, max(ca, 0.55))
            return img

        scenes.append(Scene(2.8, feature))

    names_font = {"latin": font("rubik_bold.ttf", 36), "he": font("rubik_bold.ttf", 36), "ar": font("cairo_bold.ttf", 36)}
    names_kw = {"latin": {"language": "en"}, "he": {"direction": "rtl", "language": "he"},
                "ar": {"direction": "rtl", "language": "ar"}}

    def languages(t: float) -> Image.Image:
        index = min(int(t / 0.6), len(LANGUAGE_CUTS) - 1)
        current = LANGUAGE_CUTS[index]
        img = framed(shots.get(current[0]), (t % 0.6) / 0.6, rtl, zoom=0.03)
        red_bar(img, 1.0, ty.bar_y - 14)
        draw_text(img, (W / 2, TITLE_Y - 14), tx["languages"], ty.title, BONE, 1.0, "ms", ty.kw())
        order = list(reversed(LANGUAGE_CUTS)) if rtl else LANGUAGE_CUTS
        d = ImageDraw.Draw(img)
        widths = [d.textlength(n, font=names_font[sc], **names_kw[sc]) for _, n, sc in order]
        x = (W - sum(widths) - 56 * (len(order) - 1)) / 2
        for item, w in zip(order, widths):
            draw_text(img, (x, SUB_Y + 4), item[1], names_font[item[2]], CANDLE if item == current else ASH, 1.0, "ls",
                      names_kw[item[2]])
            x += w + 56
        return img

    scenes.append(Scene(3.0, languages))

    def end_card(t: float) -> Image.Image:
        img = Image.new("RGB", (W, H), VOID)
        after = t - 3.2
        dx, dy = shiver_offsets(after, 99, amplitude=5, duration=0.35) if after > 0 else (0.0, 0.0)
        draw_text(img, (W / 2 + dx, H / 2 - 40 + dy), "SCARY", ty.logo, BONE, chill_alpha(t), "ms", {"language": "en"},
                  shadow=BLOOD, shadow_offset=(0, 18))
        draw_text(img, (W / 2, H / 2 + 70), tx["end1"], ty.body_bold, BONE, ease((t - 1.2) / 0.5), "ms", ty.kw())
        draw_text(img, (W / 2, H / 2 + 150), tx["end2"], font("assistant_bold.ttf", 58), CANDLE, ease((t - 1.7) / 0.5),
                  "ms", {"language": "en"})
        draw_text(img, (W / 2, H - 70), tx["notice"], ty.small, ASH, ease((t - 2.2) / 0.5), "ms", ty.kw())
        return img

    scenes.append(Scene(7.4, end_card, fade_out=0.6))
    return scenes


def frames(scenes: list[Scene]):
    n = 0
    for scene in scenes:
        count = round(scene.duration * FPS)
        for k in range(count):
            t = k / FPS
            b = min(1.0, t / scene.fade_in) if scene.fade_in else 1.0
            b = min(b, (scene.duration - t) / scene.fade_out) if scene.fade_out else b
            yield finish(scene.draw(t), n, max(b, 0.0))
            n += 1


# ---------- Sound ----------

def soundtrack(scenes: list[Scene], path: Path, rate: int = 44_100) -> None:
    total = sum(s.duration for s in scenes)
    t = np.arange(int(total * rate)) / rate
    rng = np.random.default_rng(666)
    # DroneSynth: two detuned low tones that beat, a fifth above, a slow swell, and brown-noise wind.
    swell = 0.55 + 0.45 * np.sin(2 * np.pi * t / 11.0)
    tone = 0.5 * np.sin(2 * np.pi * 55.0 * t) + 0.5 * np.sin(2 * np.pi * 55.35 * t) + 0.2 * np.sin(2 * np.pi * 82.4 * t)
    brown = np.cumsum(rng.uniform(-1, 1, t.size) * 0.02)
    brown = brown - np.convolve(brown, np.ones(4410) / 4410, mode="same")  # keep it centred
    brown = np.clip(brown, -1, 1)
    gust = 0.5 + 0.5 * np.sin(2 * np.pi * t / 17.0 + 1.3)
    mix = (tone * swell * 0.55 + brown * gust * 0.6) * 0.5
    mix *= np.clip(t / 3.0, 0, 1)  # the drone rises out of silence

    starts = np.cumsum([0] + [s.duration for s in scenes])

    def add(at: float, sound: np.ndarray, gain: float) -> None:
        i = int(at * rate)
        j = min(i + sound.size, mix.size)
        if i < mix.size:
            mix[i:j] += sound[: j - i] * gain

    def boom(length=2.2) -> np.ndarray:
        x = np.arange(int(length * rate)) / rate
        freq = 32 + 48 * np.exp(-x * 5)
        body = np.sin(2 * np.pi * np.cumsum(freq) / rate) * np.exp(-x * 2.2)
        hiss = np.convolve(rng.normal(0, 1, x.size), np.ones(60) / 60, mode="same") * np.exp(-x * 9) * 0.6
        return body + hiss

    def thump() -> np.ndarray:
        x = np.arange(int(0.25 * rate)) / rate
        return np.sin(2 * np.pi * (45 + 40 * np.exp(-x * 30)) * x) * np.exp(-x * 18)

    def whoosh(length=0.4) -> np.ndarray:
        x = np.arange(int(length * rate)) / rate
        env = np.sin(np.pi * x / length) ** 2
        return np.convolve(rng.normal(0, 1, x.size), np.ones(25) / 25, mode="same") * env

    add(starts[1], boom(), 0.9)  # the logo
    for k in range(2, len(scenes) - 1):
        add(starts[k] - 0.15, whoosh(), 0.35)
    shiver_at = starts[3]
    for beat in range(3):
        add(shiver_at + 0.1 + beat * 0.8, thump(), 0.8)
        add(shiver_at + 0.35 + beat * 0.8, thump(), 0.55)
    # A rising whine into the end card, then the last hit.
    end = starts[-2]
    x = np.arange(int(1.6 * rate)) / rate
    riser = np.sin(2 * np.pi * np.cumsum(220 + 500 * (x / 1.6) ** 2) / rate) * (x / 1.6) ** 2 * 0.25
    riser += np.convolve(rng.normal(0, 1, x.size), np.ones(8) / 8, mode="same") * (x / 1.6) ** 3 * 0.3
    add(end - 1.6, riser, 0.6)
    add(end, boom(3.0), 1.0)
    add(end + 3.2, thump(), 0.6)
    fade = np.clip((total - t) / 1.2, 0, 1)
    mix *= fade
    mix /= max(np.abs(mix).max(), 1e-6) / 0.85
    pcm = (mix * 32767).astype("<i2")
    stereo = np.repeat(pcm[:, None], 2, axis=1)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(stereo.tobytes())


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--lang", choices=sorted(TEXT), default="en")
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--shots", type=Path, help="directory with the CI screenshots (PNG); default: git branch")
    parser.add_argument("--poster", type=Path, help="also save a still from the end card")
    args = parser.parse_args()

    scenes = build(args.lang, Shots(args.shots))
    audio = Path(tempfile.mkdtemp(prefix="scary-ad-audio-")) / "sound.wav"
    soundtrack(scenes, audio)
    ffmpeg = subprocess.Popen(
        ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS),
         "-i", "-", "-i", str(audio), "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-maxrate", "5M", "-bufsize", "10M",
         "-pix_fmt", "yuv420p", "-g", "60",
         "-af", "loudnorm=I=-16:TP=-1.5:LRA=11", "-c:a", "aac", "-b:a", "192k", "-ar", "48000",
         "-movflags", "+faststart", "-shortest",
         "-metadata", "title=SCARY: the horror home screen for Android TV",
         "-metadata", "comment=Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary",
         str(args.out)],
        stdin=subprocess.PIPE,
    )
    assert ffmpeg.stdin
    for frame in frames(scenes):
        ffmpeg.stdin.write(frame)
    ffmpeg.stdin.close()
    if ffmpeg.wait() != 0:
        raise SystemExit("ffmpeg failed")
    if args.poster:
        end = scenes[-1]
        Image.frombytes("RGB", (W, H), finish(end.draw(3.0), 0)).save(args.poster, quality=90)


if __name__ == "__main__":
    main()
