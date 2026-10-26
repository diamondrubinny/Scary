#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.
"""
Renders SCARY's 30-second teaser in two shapes from one soundtrack: 16:9 for YouTube and 9:16 for Shorts, Reels and
TikTok. Vesper Static, the explainer's haunted TV, opens cold out of static; fast cuts with a glitch on each carry
one punchy title and one line each; the end card gives the address.

It reuses the explainer's voice (Piper "norman" through horror_audio.voice_fx), score, presenter and grade, so it
holds no third-party footage, music or voices. Every line is a full sentence that a speech recogniser hears right.

    python3 tools/promo/make_teaser.py --piper PATH/TO/piper --voice en_US-norman-medium.onnx --out scary-teaser
      (writes scary-teaser-16x9.mp4, scary-teaser-9x16.mp4, .srt and a description)
"""
from __future__ import annotations

import argparse
import math
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
from horror_audio import beat_times, score  # noqa: E402
from make_ad import ASH, BONE, CANDLE, FPS, H, VOID, W, Shots, draw_text, ease, font  # noqa: E402
from make_explainer import (EN, RATE, SCREEN, Line, Segment, backdrop, haunt, haunted_dark, is_talking,  # noqa: E402
                            mouth_curve, nearest, place_presenter, screen_layer, stamp, subtitle, synth)

VW, VH = 1080, 1920
RED = (214, 40, 34)
GAP_LINE = 0.18
GAP_SEGMENT = 0.32
LEAD_IN = 0.75  # static first, then the eyes
COLD_OPEN = 0.55


def script() -> list[Segment]:
    # label = the big title of the scene. Each line is one full sentence (the voice garbles fragments).
    return [
        Segment("hook", [Line("Don't look now, but your TV has been watching you."),
                         Line("And tonight, it wants to show you something.")], label="YOUR TV IS WATCHING"),
        Segment("screen", [Line("Every night, it picks a new horror film for you to watch.")],
                shot="40-en-home", label="A NEW NIGHTMARE EVERY NIGHT", focus=(0.3, 0.35), zoom=0.16),
        Segment("screen", [Line("It hides one hundred and seventy horror movies, all picked by hand.",
                                "It hides 170 horror movies, all picked by hand.")],
                shot="43-en-my-list", label="170 HAND-PICKED HORRORS", focus=(0.45, 0.8), zoom=0.18),
        Segment("screen", [Line("Just tell it how scared you want to be, and it finds the film.")],
                shot="55-en-moods", label="PICK YOUR FEAR", focus=(0.35, 0.9), zoom=0.2),
        Segment("screen", [Line("Even the titles shiver in the dark.")],
                shot="58-en-shiver", label="IT SHIVERS", focus=(0.5, 0.45), zoom=0.22),
        Segment("screen", [Line("And when you look away, it keeps watching.")],
                shot="48-en-screensaver", label="IT NEVER SLEEPS", focus=(0.5, 0.5), zoom=0.14),
        Segment("outro", [Line("It's free, and it's called SCARY."),
                          Line("Download it at scary dot network, if you dare.", "Download it at scary.network... if you dare.")]),
    ]


def narrate(segments: list[Segment], piper: str, voice: str, work: Path) -> np.ndarray:
    clips, t = [], LEAD_IN
    for i, seg in enumerate(segments):
        seg.start = t - (LEAD_IN if i == 0 else 0.12)  # each cut lands just before its line
        for j, line in enumerate(seg.lines):
            audio = synth(piper, voice, line.spoken, work / f"teaser-{i}-{j}.wav")
            seg.timings.append((t, t + len(audio) / RATE))
            clips.append((t, audio))
            t += len(audio) / RATE + GAP_LINE
        t += GAP_SEGMENT - GAP_LINE
        if seg.kind == "outro":
            t += 1.8  # the end card stays up after the last word
    for i, seg in enumerate(segments):
        seg.duration = (segments[i + 1].start if i + 1 < len(segments) else t) - seg.start
    track = np.zeros(int(t * RATE) + RATE, dtype=np.float32)
    for at, audio in clips:
        k = int(at * RATE)
        track[k:k + len(audio)] += audio
    return track[: int(t * RATE)]


def current_line(seg: Segment, now: float) -> str:
    for (a, b), line in zip(seg.timings, seg.lines):
        if a - 0.1 <= now <= b + GAP_LINE:
            return line.shown
    return ""


# ---------- Shared pieces ----------

def cover(img: Image.Image, size: tuple[int, int]) -> Image.Image:
    """Scaled to fill [size], cropped in the middle."""
    w, h = size
    s = max(w / img.width, h / img.height)
    big = img.resize((math.ceil(img.width * s), math.ceil(img.height * s)), Image.BILINEAR)
    x, y = (big.width - w) // 2, (big.height - h) // 2
    return big.crop((x, y, x + w, y + h))


def static_frame(k: int, size: tuple[int, int]) -> Image.Image:
    """TV snow: the cold open, before the eyes."""
    w, h = size
    noise = np.random.default_rng(1000 + k).integers(0, 200, (h // 4, w // 4), dtype=np.uint8)
    return Image.fromarray(noise).resize((w, h), Image.NEAREST).convert("RGBA")


def zoomed(shot: Image.Image, p: float, focus: tuple[float, float], zoom: float, size: tuple[int, int]) -> Image.Image:
    """The screenshot pushed in towards [focus], fast at first and settling (the teaser's cuts are short)."""
    q = 1 - (1 - min(max(p, 0), 1)) ** 2
    s = 1 + zoom * q
    cw, ch = W / s, H / s
    fx, fy = focus
    x0 = min(max(fx * W - cw / 2, 0), W - cw) * q + (W - cw) / 2 * (1 - q)
    y0 = min(max(fy * H - ch / 2, 0), H - ch) * q + (H - ch) / 2 * (1 - q)
    return shot.resize(size, Image.BILINEAR, box=(x0, y0, x0 + cw, y0 + ch))


def wrap(text: str, f, width: int) -> list[str]:
    lines, cur = [], ""
    for word in text.split():
        trial = (cur + " " + word).strip()
        if f.getlength(trial) > width and cur:
            lines.append(cur)
            cur = word
        else:
            cur = trial
    return lines + [cur]


def title(img: Image.Image, text: str, x: float, y: float, width: int, size: int, alpha: float, anchor: str = "la") -> None:
    """A scene's big title: tall condensed capitals, the first line in red, slamming in."""
    if alpha <= 0:
        return
    f = font("oswald_semibold.ttf", size)
    for k, line in enumerate(wrap(text, f, width)):
        draw_text(img, (x, y + k * size * 1.08), line, f, RED if k == 0 else BONE, alpha, anchor, EN, shadow=(0, 0, 0))


def caption(img: Image.Image, text: str, cx: float, y: float, width: int, size: int) -> None:
    """Big captions for phones: most people watch Shorts without sound."""
    if not text:
        return
    f = font("assistant_bold.ttf", size)
    lines = wrap(text, f, width - 60)
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    line_h = int(size * 1.3)
    d.rounded_rectangle((cx - width / 2, y, cx + width / 2, y + line_h * len(lines) + size * 0.7), 18, fill=(8, 4, 4, 205))
    for k, line in enumerate(lines):
        d.text((cx, y + size * 0.35 + k * line_h), line, font=f, fill=BONE + (255,), anchor="ma", **EN)
    img.alpha_composite(layer)


def slam(local: float) -> float:
    """A title's entrance: a hard cut in at 0.1 s, as if it were always there and you only just noticed."""
    return 1.0 if local > 0.1 else 0.0


# ---------- 16:9 ----------

def frame_wide(seg: Segment, k: int, now: float, shots: Shots, mouth: float) -> Image.Image:
    if now < COLD_OPEN:
        return static_frame(k, (W, H))
    local = now - seg.start
    p = local / max(seg.duration, 0.01)
    talking = is_talking(seg, now)
    text = current_line(seg, now)
    if seg.kind == "hook":
        img = haunted_dark(now)
        place_presenter(img, now, mouth, talking, W / 2, 900, 0.9, look=0.0)
        title(img, seg.label, W / 2, 70, 1700, 120, slam(now - seg.timings[0][1]), anchor="ma")
        subtitle(img, text, 1.0, box_w=1300, x=(W - 1300) // 2, y=920)
        return img
    if seg.kind == "outro":
        img = haunted_dark(now, centre_x=480)
        place_presenter(img, now, mouth, talking, 480, 960, 0.95, look=1.0)
        a = slam(local)
        draw_text(img, (1280, 330), "SCARY", font("rubik_wet_paint.ttf", 230), (196, 30, 28), a, "ms", EN, shadow=(0, 0, 0))
        draw_text(img, (1280, 420), "Free · Open source · Android TV", font("assistant_regular.ttf", 44), ASH, a, "ms", EN)
        b = slam(now - seg.timings[-1][0])
        draw_text(img, (1280, 600), "scary.network", font("oswald_semibold.ttf", 130), CANDLE, b, "ms", EN, shadow=(0, 0, 0))
        draw_text(img, (W / 2, 1050), "SCARY hosts no video. Movies open in streaming apps installed on your device.",
                  font("assistant_regular.ttf", 26), ASH, a, "ms", EN)
        subtitle(img, text, 1.0, box_w=900, x=830, y=760)
        return img
    shot = shots.get(seg.shot)
    img = backdrop(shot)
    screen_layer(img, shot, min(p * 1.6, 1.0), seg.focus, seg.zoom)
    title(img, seg.label, SCREEN[0] + SCREEN[2] + 40, 110, W - SCREEN[0] - SCREEN[2] - 80, 84, slam(local))
    place_presenter(img, now, mouth, talking, 1630, 1040, 0.55)
    subtitle(img, text, 1.0)
    return img


# ---------- 9:16 ----------

def frame_tall(seg: Segment, k: int, now: float, shots: Shots, mouth: float) -> Image.Image:
    size = (VW, VH)
    if now < COLD_OPEN:
        return static_frame(k, size)
    local = now - seg.start
    p = local / max(seg.duration, 0.01)
    talking = is_talking(seg, now)
    text = current_line(seg, now)
    if seg.kind == "hook":
        img = cover(haunted_dark(now), size)
        place_presenter(img, now, mouth, talking, VW / 2, 1360, 1.15, look=0.0)
        title(img, seg.label, VW / 2, 250, 980, 132, slam(now - seg.timings[0][1]), anchor="ma")
        caption(img, text, VW / 2, 1430, 980, 60)
        return img
    if seg.kind == "outro":
        img = cover(haunted_dark(now), size)
        a = slam(local)
        draw_text(img, (VW / 2, 470), "SCARY", font("rubik_wet_paint.ttf", 230), (196, 30, 28), a, "ms", EN, shadow=(0, 0, 0))
        draw_text(img, (VW / 2, 560), "Free · Android TV", font("assistant_regular.ttf", 50), ASH, a, "ms", EN)
        place_presenter(img, now, mouth, talking, VW / 2, 1220, 0.95, look=0.0)
        b = slam(now - seg.timings[-1][0])
        draw_text(img, (VW / 2, 1360), "scary.network", font("oswald_semibold.ttf", 128), CANDLE, b, "ms", EN, shadow=(0, 0, 0))
        caption(img, text, VW / 2, 1420, 980, 58)
        return img
    shot = shots.get(seg.shot)
    bg = cover(shot.resize((W // 6, H // 6)).filter(ImageFilter.GaussianBlur(4)), size)
    img = Image.blend(Image.new("RGB", size, VOID), bg, 0.3).convert("RGBA")
    title(img, seg.label, 60, 230, 960, 118, slam(local))
    box = zoomed(shot, min(p * 1.6, 1.0), seg.focus, seg.zoom, (1000, 562))
    mask = Image.new("L", box.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, box.width - 1, box.height - 1), 22, fill=255)
    shadow = Image.new("RGBA", size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((30, 650, 1050, 1240), 30, fill=(0, 0, 0, 190))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(24)))
    img.paste(box, (40, 660), mask)
    caption(img, text, VW / 2, 1290, 980, 58)
    place_presenter(img, now, mouth, talking, VW / 2, 1890, 0.6)
    return img


def write_srt(segments: list[Segment], path: Path) -> None:
    n, out = 1, []
    for seg in segments:
        for (a, b), line in zip(seg.timings, seg.lines):
            out.append(f"{n}\n{stamp(a, True)} --> {stamp(b + 0.15, True)}\n{line.shown}\n")
            n += 1
    path.write_text("\n".join(out), encoding="utf-8")


DESCRIPTION = """Your TV has been watching you. 👁️

SCARY is a free, open-source horror home screen for Android TV: a new film every night, 170 hand-picked horror films with a fear meter, moods, the shiver, and a horror screensaver.

Download: https://scary.network
Source code: https://github.com/diamondrubinny/Scary

SCARY does not host, stream or link to video files; it opens movies in the streaming apps already installed on your device. Voice: synthetic (Piper, public-domain voice "norman", processed). Music and sound: generated.

#shorts #horror #AndroidTV #GoogleTV #HorrorMovies
"""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--piper", required=True)
    parser.add_argument("--voice", required=True, help="the .onnx voice (en_US-norman-medium, public domain)")
    parser.add_argument("--out", type=Path, required=True, help="output stem: <stem>-16x9.mp4, <stem>-9x16.mp4 ...")
    parser.add_argument("--shots", type=Path, help="directory with the CI screenshots (PNG); default: git branch")
    args = parser.parse_args()

    work = Path(tempfile.mkdtemp(prefix="scary-teaser-"))
    segments = script()
    voice_track = narrate(segments, args.piper, args.voice, work)
    total = len(voice_track) / RATE
    frames = int(total * FPS)
    mouths = mouth_curve(voice_track, frames)
    cuts = [seg.start for seg in segments[1:]]
    hook, outro = segments[0], segments[-1]
    shiver = next(seg.start for seg in segments if seg.shot == "58-en-shiver")
    stings = [COLD_OPEN, hook.timings[0][1] + 0.1, shiver, outro.timings[-1][1] + 0.2]
    boxes = [(COLD_OPEN, segments[1].start), (outro.start, total)]
    bed = score(total, voice_track, cuts, boxes, stings)
    bed = np.pad(bed, (0, max(0, voice_track.size - bed.size)))[: voice_track.size]
    # The cold open is static noise on the soundtrack too.
    hiss = np.random.default_rng(5).uniform(-1, 1, int(COLD_OPEN * RATE)).astype(np.float32) * 0.25
    bed[: hiss.size] += hiss
    mix = voice_track * 0.95 + bed
    mix /= max(np.abs(mix).max(), 1e-6) / 0.9
    beats = beat_times(total, cuts)
    audio = work / "mix.wav"
    with wave.open(str(audio), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes((mix * 32767).astype("<i2").tobytes())

    def encoder(path: Path, size: tuple[int, int]) -> subprocess.Popen:
        return subprocess.Popen(
            ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{size[0]}x{size[1]}",
             "-r", str(FPS), "-i", "-", "-i", str(audio), "-c:v", "libx264", "-preset", "slow", "-crf", "19",
             "-maxrate", "10M", "-bufsize", "20M", "-pix_fmt", "yuv420p", "-g", "30",
             "-af", "loudnorm=I=-14:TP=-1.5:LRA=9", "-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-ac", "2",
             "-movflags", "+faststart", "-shortest", "-metadata", "title=SCARY: your TV has been watching you",
             "-metadata", "comment=Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary", str(path)],
            stdin=subprocess.PIPE)

    stem = args.out
    wide_path, tall_path = Path(f"{stem}-16x9.mp4"), Path(f"{stem}-9x16.mp4")
    wide, tall = encoder(wide_path, (W, H)), encoder(tall_path, (VW, VH))
    assert wide.stdin and tall.stdin
    shots = Shots(args.shots)
    seg_index = 0
    for k in range(frames):
        now = k / FPS
        while seg_index + 1 < len(segments) and now >= segments[seg_index + 1].start:
            seg_index += 1
        seg = segments[seg_index]
        glitch = max(0.0, 1 - min(nearest(cuts, now), nearest(stings, now) * 0.8) / 0.3)
        since_beat = min((now - b for b in beats if b <= now), default=9.0)
        pulse = math.exp(-since_beat / 0.18) if since_beat < 1 else 0.0
        fade = min(1.0, (total - now) / 0.6)  # the end goes to black
        mouth = float(mouths[k])
        wide.stdin.write(haunt(frame_wide(seg, k, now, shots, mouth), k, now, fade, glitch, pulse))
        tall.stdin.write(haunt(frame_tall(seg, k, now, shots, mouth), k, now, fade, glitch, pulse))
    for enc in (wide, tall):
        enc.stdin.close()
        if enc.wait() != 0:
            raise SystemExit("ffmpeg failed")
    write_srt(segments, Path(f"{stem}.srt"))
    Path(f"{stem}-description.txt").write_text(DESCRIPTION, encoding="utf-8")
    print(f"{wide_path}, {tall_path}: {total:.1f} s, {frames} frames")


if __name__ == "__main__":
    main()
