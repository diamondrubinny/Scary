#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.
"""
Renders SCARY's YouTube explainer: Vesper Static, a haunted TV set, presents the app over the real CI screenshots.

The narration is synthesized with Piper and a public-domain voice (en_GB-cori-high), her sound-wave mouth follows its
loudness, and the
music bed is the same drone as the app's DroneSynth, so the video holds no third-party footage, music or voices.
Writes the video, an .srt file, a thumbnail and the YouTube description with chapters.

    python3 tools/promo/make_explainer.py --piper PATH/TO/piper --voice en_GB-cori-high.onnx --out scary-explainer.mp4

Screenshots come from the `screenshots` branch (git fetch origin screenshots), or from --shots DIR.
"""
from __future__ import annotations

import argparse
import math
import subprocess
import sys
import tempfile
import wave
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
from make_ad import ASH, BLOOD, BONE, CANDLE, FPS, H, VOID, W, Shots, draw_text, ease, finish, font, ken_burns  # noqa: E402

DONATE_URL = "https://www.paypal.com/donate/?hosted_button_id=U8CEQ95ECPP9J"
RATE = 44_100


@dataclass
class Line:
    spoken: str
    shown: str = ""

    def __post_init__(self):
        self.shown = self.shown or self.spoken


@dataclass
class Segment:
    kind: str  # "intro", "screen", "languages", "outro"
    lines: list[Line]
    shot: str = ""
    label: str = ""
    chapter: str = ""
    # Where the slow zoom ends, as a fraction of the screenshot (0.5, 0.5 is the middle).
    focus: tuple[float, float] = (0.5, 0.5)
    zoom: float = 0.08
    start: float = 0.0
    duration: float = 0.0
    timings: list[tuple[float, float]] = field(default_factory=list)


def script() -> list[Segment]:
    return [
        Segment("intro", [Line("Hi. I'm Vesper Static, and I live inside your TV."),
                          Line("Some home screens really should come with a warning."),
                          Line("This one is called SCARY.")], chapter="Meet SCARY"),
        Segment("screen", [Line("SCARY is a free, open source home screen for Android TV, made for people who love to be scared."),
                           Line("Every evening, a brand new film for tonight fills the screen.")],
                shot="40-en-home", label="Tonight's pick", chapter="Tonight's pick and 170 films", focus=(0.3, 0.35)),
        # Synthetic voices say "fear meter" like "fair meter", and blur "hand-picked" in a long sentence: these
        # lines are worded so a listener (and a speech recogniser) hears them right.
        Segment("screen", [Line("Below it, a hundred and seventy horror films and thrillers, all picked by hand.",
                                "Below it: 170 horror films and thrillers, all picked by hand."),
                           Line("Each one is rated from one to five, on how scary it is.",
                                "Each one is rated from 1 to 5 on how scary it is."),
                           Line("And there's a row for every kind of fear: found footage, folk horror, Asian horror, zombies, vampires and haunted houses.")],
                shot="43-en-my-list", label="170 hand-picked films", focus=(0.4, 0.75)),
        Segment("screen", [Line("Not sure what you feel like tonight? Pick a mood, from short and sharp to don't sleep tonight, or pick a decade."),
                           Line("Still can't decide? Press Surprise me, and I'll choose for you.")],
                shot="55-en-moods", label="What do you feel like tonight?", chapter="Moods, decades and Surprise me", focus=(0.3, 0.9)),
        Segment("screen", [Line("Every film gets its own page, with how scary it is, the cast, and a trailer."),
                           Line("Press Watch to open it in Stremio, or search for it in the apps you already have."),
                           Line("Hold O K on any poster to add it to My list.", "Hold OK on any poster to add it to My list.")],
                shot="41-en-details", label="Movie pages", chapter="Movie pages, rent or buy", focus=(0.3, 0.3)),
        Segment("screen", [Line("Not in your apps? Add a free key from The Movie Database, and SCARY shows where to rent or buy it in your country.",
                                "Not in your apps? Add a free key from The Movie Database (TMDB), and SCARY shows where to rent or buy it in your country.")],
                shot="41-en-details", label="Didn't find it? Rent or buy", focus=(0.25, 0.72), zoom=0.22),
        Segment("screen", [Line("And when the film opens, its title shivers in the dark. Just for a moment.")],
                shot="58-en-shiver", label="The shiver", chapter="The shiver, search, family night", focus=(0.5, 0.45)),
        Segment("screen", [Line("You can search by typing, or by voice, in the library and across the web.")],
                shot="42-en-search-web", label="Search", focus=(0.3, 0.3)),
        Segment("screen", [Line("Watching with the kids? Family night shows only the gentlest films, and leaving it takes your pin code.",
                                "Watching with the kids? Family night shows only the gentlest films, and leaving it takes your PIN.")],
                shot="56-en-family-night", label="Family night", focus=(0.3, 0.3)),
        Segment("screen", [Line("When the TV sits idle, the screensaver fills it with horror.")],
                shot="48-en-screensaver", label="Horror screensaver", focus=(0.45, 0.5)),
        Segment("screen", [Line("No account, no ads, no tracking. Your lists stay on your TV."),
                           Line("And to be clear: SCARY doesn't host or stream any video. It opens films in the streaming apps you already have.")],
                shot="47-en-privacy", label="Private by design", chapter="Privacy, languages, download", focus=(0.3, 0.3)),
        Segment("languages", [Line("It speaks English, Hebrew, Arabic, Portuguese and Russian.")], label="Five languages"),
        Segment("outro", [Line("Download it free at scary dot network.", "Download it free at scary.network"),
                          Line("If it gave you goosebumps, scan the code and buy us some popcorn."),
                          Line("Sleep well. If you can.")]),
    ]


LANGUAGE_CUTS = [("40-en-home", "English"), ("01-home", "עברית"), ("50-ar-home", "العربية"), ("14-home-pt", "Português"),
                 ("17-home-ru", "Русский")]

GAP_LINE = 0.28
GAP_SEGMENT = 0.75
LEAD_IN = 0.6


# ---------- Voice ----------

def synth(piper: str, voice: str, text: str, path: Path) -> np.ndarray:
    subprocess.run([piper, "-m", voice, "-f", str(path), "--length-scale", "1.04", "--sentence-silence", "0.1"],
                   input=text.encode(), check=True, capture_output=True)
    with wave.open(str(path)) as w:
        rate = w.getframerate()
        data = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float32) / 32768
    # To the video's sample rate; plenty for speech.
    n = int(len(data) * RATE / rate)
    return np.interp(np.linspace(0, len(data) - 1, n), np.arange(len(data)), data).astype(np.float32)


def narrate(segments: list[Segment], piper: str, voice: str, work: Path) -> np.ndarray:
    clips, t = [], LEAD_IN
    for i, seg in enumerate(segments):
        seg.start = t if i else 0.0
        for j, line in enumerate(seg.lines):
            audio = synth(piper, voice, line.spoken, work / f"line-{i}-{j}.wav")
            seg.timings.append((t, t + len(audio) / RATE))
            clips.append((t, audio))
            t += len(audio) / RATE + GAP_LINE
        t += GAP_SEGMENT - GAP_LINE
        if seg.kind == "outro":
            t += 2.0  # the end card stays up after the last word
        seg.duration = t - seg.start
    total = t
    voice_track = np.zeros(int(total * RATE) + RATE, dtype=np.float32)
    for at, audio in clips:
        i = int(at * RATE)
        voice_track[i:i + len(audio)] += audio
    return voice_track[: int(total * RATE)]


def mouth_curve(voice_track: np.ndarray, frames: int) -> np.ndarray:
    """How loud her sound-wave mouth is in each frame (0..1): the voice's loudness, with a quick open and a softer close."""
    hop = RATE // FPS
    rms = np.array([np.sqrt(np.mean(voice_track[k * hop:(k + 1) * hop] ** 2) + 1e-12) for k in range(frames)])
    peak = np.percentile(rms[rms > 1e-4], 95) if np.any(rms > 1e-4) else 1.0
    target = np.clip(rms / peak * 1.25 - 0.08, 0, 1) ** 0.8
    out, level = np.zeros(frames), 0.0
    for k, v in enumerate(target):
        level += (v - level) * (0.75 if v > level else 0.45)
        out[k] = level
    return out


# ---------- Music ----------

def music(total: float, voice_track: np.ndarray) -> np.ndarray:
    t = np.arange(int(total * RATE)) / RATE
    rng = np.random.default_rng(13)
    swell = 0.55 + 0.45 * np.sin(2 * np.pi * t / 11.0)
    tone = 0.5 * np.sin(2 * np.pi * 55.0 * t) + 0.5 * np.sin(2 * np.pi * 55.35 * t) + 0.2 * np.sin(2 * np.pi * 82.4 * t)
    brown = np.cumsum(rng.uniform(-1, 1, t.size) * 0.02)
    brown = np.clip(brown - np.convolve(brown, np.ones(4410) / 4410, mode="same"), -1, 1)
    gust = 0.5 + 0.5 * np.sin(2 * np.pi * t / 17.0 + 1.3)
    bed = (tone * swell * 0.55 + brown * gust * 0.6) * 0.5
    bed *= np.clip(t / 2.0, 0, 1) * np.clip((total - t) / 2.0, 0, 1)
    # The music steps back while Vesper talks.
    env = np.convolve(np.abs(voice_track[: t.size]), np.ones(4410) / 4410, mode="same")
    duck = 1 - 0.72 * np.clip(env / (env.max() * 0.25 + 1e-9), 0, 1)
    return bed * duck * 0.22


# ---------- Vesper Static ----------

PW, PH = 640, 760
SS = 2  # drawn at twice the size, then scaled down: smooth edges
PHOSPHOR = (190, 255, 215)


def presenter(t: float, mouth: float, talking: bool, look: float = -1.0) -> Image.Image:
    """Vesper Static, a haunted TV set: a face of light on a screen of static, a sound-wave mouth, twitching antennas."""
    s = SS
    img = Image.new("RGBA", (PW * s, PH * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    u = lambda *v: tuple(x * s for x in v)  # noqa: E731

    # Antennas, twitching more while she talks.
    twitch = 9 if talking else 4
    for base_angle, speed, phase in ((-122, 2.3, 0.0), (-58, 2.9, 1.0)):
        angle = math.radians(base_angle + twitch * math.sin(t * speed + phase))
        x0, y0 = 320, 214
        x1, y1 = x0 + 175 * math.cos(angle), y0 + 175 * math.sin(angle)
        d.line(u(x0, y0, x1, y1), fill=(150, 150, 162, 255), width=7 * s)
        d.ellipse(u(x1 - 11, y1 - 11, x1 + 11, y1 + 11), fill=(176, 48, 48, 255))
    d.ellipse(u(286, 196, 354, 230), fill=(34, 24, 20, 255))
    # Legs.
    for x0, x1 in ((205, 178), (435, 462)):
        d.line(u(x0, 640, x1, 728), fill=(40, 26, 20, 255), width=20 * s)
    # The cabinet, the bezel and the side panel.
    d.rounded_rectangle(u(90, 214, 550, 652), 48 * s, fill=(78, 48, 33, 255), outline=(46, 28, 20, 255), width=6 * s)
    d.line(u(130, 228, 510, 228), fill=(120, 80, 56, 255), width=4 * s)
    d.rounded_rectangle(u(122, 248, 448, 618), 60 * s, fill=(20, 20, 22, 255))
    for k, cy in enumerate((330, 412)):
        d.ellipse(u(470, cy - 26, 522, cy + 26), fill=(30, 20, 16, 255), outline=(150, 110, 80, 255), width=3 * s)
        turn = math.radians(t * (40 if k else -25))
        d.line(u(496, cy, 496 + 20 * math.cos(turn), cy + 20 * math.sin(turn)), fill=(200, 170, 120, 255), width=4 * s)
    for gy in range(470, 600, 14):
        d.line(u(472, gy, 520, gy), fill=(36, 24, 18, 255), width=5 * s)

    # The screen: dark phosphor, live static, scanlines.
    sx0, sy0, sx1, sy1 = 140, 266, 430, 600
    sw, sh = (sx1 - sx0) * s, (sy1 - sy0) * s
    frame_seed = int(t * FPS)
    noise = np.random.default_rng(frame_seed).integers(0, 255, (sh // 6, sw // 6), dtype=np.uint8)
    static = Image.fromarray(noise).resize((sw, sh), Image.NEAREST)
    screen = Image.new("RGBA", (sw, sh), (14, 28, 24, 255))
    screen.alpha_composite(Image.merge("RGBA", (static, static, static, static.point(lambda v: v // 7))))
    lines = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
    ld = ImageDraw.Draw(lines)
    for y in range(0, sh, 6 * s):
        ld.line((0, y, sw, y), fill=(0, 0, 0, 60), width=2 * s)
    screen.alpha_composite(lines)

    face = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
    fd = ImageDraw.Draw(face)
    fx = lambda x: (x - sx0) * s  # noqa: E731
    fy = lambda y: (y - sy0) * s  # noqa: E731
    blink = (t % 4.1) < 0.14
    gaze = 10 * look
    for ex in (238, 332):
        ry = 5 if blink else 40
        fd.ellipse((fx(ex - 30), fy(388 - ry), fx(ex + 30), fy(388 + ry)), fill=PHOSPHOR + (255,))
        if not blink:
            fd.ellipse((fx(ex - 12 + gaze), fy(390), fx(ex + 12 + gaze), fy(414)), fill=(10, 30, 24, 255))
    # The mouth is a sound wave: flat when quiet, a wave as loud as her voice.
    amp = 3 + 34 * mouth
    points = []
    for k in range(61):
        x = 205 + 160 * k / 60
        env = math.sin(math.pi * k / 60)
        wave_y = 505 + (amp * env * (0.7 * math.sin(k * 0.55 + t * 18) + 0.3 * math.sin(k * 1.3 - t * 11)) if mouth > 0.05
                        else 505 + 6 * math.sin(math.pi * k / 60))
        points.append((fx(x), fy(wave_y)))
    fd.line(points, fill=PHOSPHOR + (255,), width=6 * s, joint="curve")
    glow = face.filter(ImageFilter.GaussianBlur(9 * s))
    screen.alpha_composite(glow)
    screen.alpha_composite(face)
    # Now and then the picture tears.
    if (t % 4.7) < 0.12:
        band_y = int(sh * (0.3 + 0.4 * ((t * 7) % 1)))
        band = screen.crop((0, band_y, sw, band_y + 40 * s))
        screen.paste(band, (int(14 * s * math.sin(t * 50)), band_y))
    mask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, sw - 1, sh - 1), 52 * s, fill=255)
    halo = Image.new("RGBA", img.size, (120, 255, 190, 0))
    halo_mask = Image.new("L", img.size, 0)
    halo_mask.paste(mask, (sx0 * s, sy0 * s))
    halo.putalpha(halo_mask.filter(ImageFilter.GaussianBlur(26 * s)).point(lambda v: int(v * 0.35)))
    img.alpha_composite(halo)
    img.paste(screen, (sx0 * s, sy0 * s), mask)
    return img.resize((PW, PH), Image.LANCZOS)


def place_presenter(img: Image.Image, t: float, mouth: float, talking: bool, cx: float, base_y: float, scale: float,
                    look: float = -1.0, alpha: float = 1.0) -> None:
    g = presenter(t, mouth, talking, look)
    hop = abs(math.sin(t * 2.2)) * (7 if talking else 3)
    tilt = math.sin(t * 1.1) * 1.6
    g = g.rotate(tilt, resample=Image.BICUBIC, expand=False)
    w, h = int(PW * scale), int(PH * scale)
    g = g.resize((w, h), Image.LANCZOS)
    if alpha < 1:
        g.putalpha(g.getchannel("A").point(lambda v: int(v * alpha)))
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sw = w * 0.62
    ImageDraw.Draw(shadow).ellipse((cx - sw / 2, base_y - 14, cx + sw / 2, base_y + 14), fill=(0, 0, 0, int(140 * alpha)))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(10)))
    img.alpha_composite(g, (int(cx - w / 2), int(base_y - h + 8 - hop)))


# ---------- Frames ----------

SCREEN = (64, 112, 1280, 720)  # x, y, w, h
EN = {"language": "en"}


def screen_layer(img: Image.Image, shot: Image.Image, p: float, focus: tuple[float, float], zoom: float) -> None:
    x, y, w, h = SCREEN
    fx, fy = focus
    s = 1 + zoom * ease(p)
    cw, ch = W / s, H / s
    x0 = min(max(fx * W - cw / 2, 0), W - cw) * ease(p) + (W - cw) / 2 * (1 - ease(p))
    y0 = min(max(fy * H - ch / 2, 0), H - ch) * ease(p) + (H - ch) / 2 * (1 - ease(p))
    view = shot.resize((w, h), Image.BILINEAR, box=(x0, y0, x0 + cw, y0 + ch))
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((x - 8, y + 6, x + w + 8, y + h + 26), 26, fill=(0, 0, 0, 190))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(24)))
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), 18, fill=255)
    img.paste(view, (x, y), mask)
    ImageDraw.Draw(img).rounded_rectangle((x, y, x + w - 1, y + h - 1), 18, outline=BONE + (50,), width=2)


def backdrop(shot: Image.Image | None) -> Image.Image:
    if shot is None:
        base = Image.new("RGB", (W, H), VOID)
    else:
        small = shot.resize((W // 6, H // 6), Image.BILINEAR).filter(ImageFilter.GaussianBlur(6))
        base = Image.blend(Image.new("RGB", small.size, VOID), small, 0.28).resize((W, H), Image.BILINEAR)
    return base.convert("RGBA")


def subtitle(img: Image.Image, text: str, alpha: float, box_w: int = SCREEN[2], x: int = SCREEN[0], y: int = 878) -> None:
    if not text or alpha <= 0:
        return
    f = font("assistant_bold.ttf", 44)
    words, lines, cur = text.split(), [], ""
    for word in words:
        trial = (cur + " " + word).strip()
        if f.getlength(trial) > box_w - 60 and cur:
            lines.append(cur)
            cur = word
        else:
            cur = trial
    lines.append(cur)
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    height = 58 * len(lines) + 28
    d.rounded_rectangle((x, y, x + box_w, y + height), 14, fill=(8, 6, 6, int(200 * alpha)))
    for k, line in enumerate(lines):
        d.text((x + box_w / 2, y + 18 + 58 * k), line, font=f, fill=BONE + (int(255 * alpha),), anchor="ma", **EN)
    img.alpha_composite(layer)


def eyebrow(img: Image.Image, text: str, alpha: float) -> None:
    if text:
        draw_text(img, (SCREEN[0] + 4, 74), text.upper(), font("assistant_bold.ttf", 32), CANDLE, alpha, "ls", EN)


def current_line(seg: Segment, now: float) -> str:
    for (a, b), line in zip(seg.timings, seg.lines):
        if a - 0.15 <= now <= b + GAP_LINE:
            return line.shown
    return ""


def is_talking(seg: Segment, now: float) -> bool:
    return any(a <= now <= b for a, b in seg.timings)


def render_frame(seg: Segment, now: float, shots: Shots, mouth: float, qr_img: Image.Image) -> Image.Image:
    local = now - seg.start
    p = local / max(seg.duration, 0.01)
    talking = is_talking(seg, now)
    text = current_line(seg, now)
    if seg.kind == "intro":
        img = backdrop(None)
        logo_at = seg.timings[-1][0] + 0.9
        a = ease((now - logo_at) / 0.8)
        if a > 0:
            draw_text(img, (W / 2, 190), "SCARY", font("rubik_wet_paint.ttf", 190), BLOOD, a, "ms", EN, shadow=(0, 0, 0))
            draw_text(img, (W / 2, 250), "The horror home screen for Android TV", font("assistant_regular.ttf", 40), ASH, a, "ms", EN)
        place_presenter(img, now, mouth, talking, W / 2, 870, 0.7, look=0.0)
        subtitle(img, text, 1.0, box_w=1300, x=(W - 1300) // 2, y=900)
        return img.convert("RGB")
    if seg.kind == "outro":
        img = backdrop(None)
        place_presenter(img, now, mouth, talking, 470, 900, 0.9, look=1.0)
        a = ease(local / 0.8)
        draw_text(img, (1250, 250), "SCARY", font("rubik_wet_paint.ttf", 170), BLOOD, a, "ms", EN, shadow=(0, 0, 0))
        draw_text(img, (1250, 320), "Free · Open source · Android TV", font("assistant_regular.ttf", 40), ASH, a, "ms", EN)
        draw_text(img, (1250, 430), "scary.network", font("oswald_semibold.ttf", 96), CANDLE, a, "ms", EN)
        qa = ease((now - seg.timings[1][0] + 0.2) / 0.6) if len(seg.timings) > 1 else 0
        if qa > 0:
            q = qr_img.copy()
            q.putalpha(int(255 * qa))
            img.alpha_composite(q, (1250 - q.width // 2, 490))
            draw_text(img, (1250, 490 + q.height + 44), "Scan to donate", font("assistant_bold.ttf", 34), BONE, qa, "ms", EN)
        draw_text(img, (W / 2, 1050), "SCARY hosts no video. Movies open in streaming apps installed on your device.",
                  font("assistant_regular.ttf", 26), ASH, a, "ms", EN)
        subtitle(img, text, 1.0, box_w=760, x=90, y=40)
        return img.convert("RGB")
    if seg.kind == "languages":
        n = len(LANGUAGE_CUTS)
        k = min(int(p * n), n - 1)
        name, label = LANGUAGE_CUTS[k]
        shot = shots.get(name)
        img = backdrop(shot)
        screen_layer(img, shot, (p * n) % 1, (0.5, 0.4), 0.05)
        eyebrow(img, seg.label, 1.0)
        kw = {"direction": "rtl", "language": "he"} if label == "עברית" else ({"direction": "rtl", "language": "ar"} if label == "العربية" else EN)
        face = "cairo_bold.ttf" if label == "العربية" else ("assistant_bold.ttf" if label == "עברית" else "oswald_semibold.ttf")
        draw_text(img, (SCREEN[0] + SCREEN[2], 80), label, font(face, 56), BONE, 1.0, "rs", kw)
        place_presenter(img, now, mouth, talking, 1630, 860, 0.62)
        subtitle(img, text, 1.0)
        return img.convert("RGB")
    shot = shots.get(seg.shot)
    img = backdrop(shot)
    screen_layer(img, shot, p, seg.focus, seg.zoom)
    eyebrow(img, seg.label, ease(local / 0.5))
    place_presenter(img, now, mouth, talking, 1630, 860, 0.62)
    subtitle(img, text, 1.0)
    return img.convert("RGB")


def qr_image(size: int = 250) -> Image.Image:
    root = Path(__file__).resolve().parents[2]
    q = Image.open(root / "docs/donate-qr.png").convert("RGBA").resize((size, size), Image.NEAREST)
    card = Image.new("RGBA", (size + 28, size + 28), (255, 255, 255, 255))
    card.alpha_composite(q, (14, 14))
    return card


# ---------- Outputs ----------

def stamp(seconds: float, srt: bool = False) -> str:
    h, rem = divmod(seconds, 3600)
    m, s = divmod(rem, 60)
    if srt:
        return f"{int(h):02d}:{int(m):02d}:{int(s):02d},{int((s % 1) * 1000):03d}"
    return f"{int(m)}:{int(s):02d}"


def write_srt(segments: list[Segment], path: Path) -> None:
    rows, n = [], 1
    for seg in segments:
        for (a, b), line in zip(seg.timings, seg.lines):
            rows.append(f"{n}\n{stamp(a, True)} --> {stamp(b + 0.2, True)}\n{line.shown}\n")
            n += 1
    path.write_text("\n".join(rows), encoding="utf-8")


def write_description(segments: list[Segment], path: Path, total: float) -> None:
    chapters = [f"{stamp(seg.start)} {seg.chapter}" for seg in segments if seg.chapter]
    text = f"""SCARY: the horror home screen for Android TV (free and open source)

Vesper Static, a haunted TV set, shows you around SCARY, a free, open-source home screen (launcher) for Android TV, made for people who love to be scared: a new film for tonight every evening, 170 hand-picked horror films and thrillers with a fear meter, moods and decades, family night, a horror screensaver, and all your streaming apps in one place. No account, no ads, no tracking.

Download: https://scary.network
Source code: https://github.com/diamondrubinny/Scary
Support SCARY (PayPal): {DONATE_URL}

Chapters
{chr(10).join(chapters)}

Good to know: SCARY does not host, stream or link to video files. It shows public movie information and opens movies in the streaming apps already installed on your device. Movie information and images come from TMDB and Cinemeta and belong to their owners; this product uses the TMDB API but is not endorsed or certified by TMDB.

Made with real screenshots of the app. Vesper's voice is synthetic (Piper, public-domain voice "cori"); the music is generated.

#AndroidTV #HorrorMovies #GoogleTV #Launcher #OpenSource
"""
    path.write_text(text, encoding="utf-8")


def thumbnail(shots: Shots, path: Path) -> None:
    img = backdrop(shots.get("40-en-home"))
    # The screenshot starts right of the headline, so the two never touch.
    shot = shots.get("40-en-home").resize((1010, 568), Image.LANCZOS)
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, shot.width - 1, shot.height - 1), 22, fill=255)
    img.paste(shot, (W - 1010 - 60, 400), mask)
    draw_text(img, (90, 300), "SCARY", font("rubik_wet_paint.ttf", 230), BLOOD, 1.0, "ls", EN, shadow=(0, 0, 0), shadow_offset=(0, 8))
    draw_text(img, (100, 420), "Horror home screen", font("oswald_semibold.ttf", 92), BONE, 1.0, "ls", EN, shadow=(0, 0, 0))
    draw_text(img, (100, 520), "for Android TV", font("oswald_semibold.ttf", 92), CANDLE, 1.0, "ls", EN, shadow=(0, 0, 0))
    place_presenter(img, 0.7, 0.6, True, 420, 1060, 0.62, look=1.0)
    img.convert("RGB").resize((1280, 720), Image.LANCZOS).save(path, quality=90)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--piper", required=True, help="the piper executable")
    parser.add_argument("--voice", required=True, help="the .onnx voice (en_GB-cori-high, public domain)")
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--shots", type=Path, help="directory with the CI screenshots (PNG); default: git branch")
    args = parser.parse_args()

    work = Path(tempfile.mkdtemp(prefix="scary-explainer-"))
    segments = script()
    voice_track = narrate(segments, args.piper, args.voice, work)
    total = len(voice_track) / RATE
    frames = int(total * FPS)
    mouths = mouth_curve(voice_track, frames)
    mix = voice_track * 0.9 + music(total, voice_track)
    mix /= max(np.abs(mix).max(), 1e-6) / 0.9
    audio = work / "mix.wav"
    with wave.open(str(audio), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes((mix * 32767).astype("<i2").tobytes())

    shots = Shots(args.shots)
    qr_img = qr_image()
    ffmpeg = subprocess.Popen(
        ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS),
         "-i", "-", "-i", str(audio), "-c:v", "libx264", "-preset", "slow", "-crf", "19", "-maxrate", "8M", "-bufsize", "16M",
         "-pix_fmt", "yuv420p", "-g", "60", "-af", "loudnorm=I=-14:TP=-1.5:LRA=11", "-c:a", "aac", "-b:a", "192k",
         "-ar", "48000", "-ac", "2", "-movflags", "+faststart", "-shortest",
         "-metadata", "title=SCARY: the horror home screen for Android TV",
         "-metadata", "comment=Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary",
         str(args.out)],
        stdin=subprocess.PIPE,
    )
    assert ffmpeg.stdin
    seg_index = 0
    for k in range(frames):
        now = k / FPS
        while seg_index + 1 < len(segments) and now >= segments[seg_index + 1].start:
            seg_index += 1
        seg = segments[seg_index]
        local = now - seg.start
        # A short dip to dark between segments, never a flash.
        dip = min(1.0, local / 0.25, (seg.duration - local) / 0.25) if seg_index else min(1.0, local / 0.8)
        frame = render_frame(seg, now, shots, float(mouths[k]), qr_img)
        ffmpeg.stdin.write(finish(frame, k, 0.55 + 0.45 * max(dip, 0.0)))
    ffmpeg.stdin.close()
    if ffmpeg.wait() != 0:
        raise SystemExit("ffmpeg failed")
    stem = args.out.with_suffix("")
    write_srt(segments, stem.with_suffix(".srt"))
    write_description(segments, Path(f"{stem}-youtube.txt"), total)
    thumbnail(shots, Path(f"{stem}-thumbnail.jpg"))
    print(f"{args.out}: {total:.1f} s, {frames} frames")


if __name__ == "__main__":
    main()
