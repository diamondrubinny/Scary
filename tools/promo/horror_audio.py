# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.
"""The horror sound for SCARY's promo videos, all generated here (no samples, nothing to license).

- voice_fx: a synthetic voice made darker: lower pitch and formants, a whispered double, a cold echo.
- score: a sub drone with a beating minor second, a high string cluster that creeps in, a heartbeat that quickens
  before each cut, a detuned music box lullaby, wind, and a riser into a stinger at every cut.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

import numpy as np

RATE = 44_100

# ffmpeg's documented "whisperization" (random phase per FFT bin): breath without pitch.
_WHISPER = ("afftfilt=real='hypot(re,im)*cos((random(0)*2-1)*2*3.14)':"
            "imag='hypot(re,im)*sin((random(1)*2-1)*2*3.14)':win_size=512:overlap=0.75")


def voice_fx(src: Path, dst: Path, depth: float = 0.91) -> None:
    """[src] (any rate) to [dst] (44.1 kHz mono): pitch and formants down by [depth], a whisper under it, an echo."""
    rate = int(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "stream=sample_rate", "-of", "csv=p=0", str(src)],
                              capture_output=True, text=True, check=True).stdout.strip())
    graph = (f"[0]asetrate={rate}*{depth},aresample={RATE},atempo={1 / depth:.5f},asplit=2[a][b];"
             f"[b]{_WHISPER},highpass=f=1500,volume=0.24,adelay=25|25[w];"
             "[a][w]amix=inputs=2:weights=1 1:normalize=0,"
             "aecho=0.8:0.6:70|140|260:0.24|0.14|0.08,lowpass=f=9000,"
             "acompressor=threshold=-20dB:ratio=3:attack=5:release=120")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(src), "-filter_complex", graph, "-ac", "1", "-ar", str(RATE),
                    str(dst)], check=True)


def _env(n: int, attack: float, release: float) -> np.ndarray:
    t = np.arange(n) / RATE
    return np.clip(t / max(attack, 1e-4), 0, 1) * np.exp(-t / max(release, 1e-4))


def _lowpass(x: np.ndarray, width: int) -> np.ndarray:
    return np.convolve(x, np.ones(width) / width, mode="same")


def _place(out: np.ndarray, sound: np.ndarray, at: float, gain: float = 1.0) -> None:
    i = int(at * RATE)
    if i >= out.size or i + sound.size <= 0:
        return
    s = sound[max(0, -i):]
    i = max(0, i)
    n = min(s.size, out.size - i)
    out[i:i + n] += s[:n] * gain


def heartbeat() -> np.ndarray:
    """Lub-dub: two low thumps."""
    def thump(freq: float, length: float) -> np.ndarray:
        t = np.arange(int(length * RATE)) / RATE
        return np.sin(2 * np.pi * freq * t * (1 - 0.35 * t / length)) * _env(t.size, 0.004, length / 4)
    beat = np.zeros(int(0.6 * RATE))
    _place(beat, thump(58, 0.22), 0.0, 1.0)
    _place(beat, thump(52, 0.2), 0.17, 0.75)
    return beat


def stinger(rng: np.random.Generator) -> np.ndarray:
    """A hit: a low boom, a metallic screech and a burst of noise, all dying out over two seconds."""
    n = int(2.2 * RATE)
    t = np.arange(n) / RATE
    boom = np.sin(2 * np.pi * (45 - 15 * t) * t) * _env(n, 0.003, 0.7)
    screech = sum(np.sin(2 * np.pi * f * t + 3 * np.sin(2 * np.pi * 7 * t)) for f in (1661.0, 1760.0, 2489.0)) / 3
    screech *= _env(n, 0.01, 0.5) * 0.35
    noise = _lowpass(rng.uniform(-1, 1, n), 6) * _env(n, 0.002, 0.25) * 0.5
    return (boom + screech + noise) * 0.9


def riser(rng: np.random.Generator, length: float = 1.6) -> np.ndarray:
    """Reversed-cymbal swell into a cut: noise that rises and brightens, then stops dead."""
    n = int(length * RATE)
    t = np.arange(n) / RATE
    noise = rng.uniform(-1, 1, n)
    dark, bright = _lowpass(noise, 40), noise - _lowpass(noise, 6)
    mix = (t / length)
    return (dark * (1 - mix) + bright * mix) * (t / length) ** 2.5 * 0.55


def music_box(start: float, length: float) -> tuple[np.ndarray, float]:
    """A slow lullaby in A minor on a slightly out-of-tune music box; returns the sound and when it starts."""
    notes = [76, 72, 69, 71, 72, 71, 68, 64, 76, 72, 69, 71, 72, 74, 75, 76]  # MIDI; the D# (75) is the wrong note
    step = 0.62
    out = np.zeros(int(length * RATE))
    for k in range(int(length / step)):
        midi = notes[k % len(notes)]
        f = 440 * 2 ** ((midi - 69) / 12) * (1 + 0.006 * np.sin(k * 1.7))  # drifting, like a tired spring
        n = int(1.6 * RATE)
        t = np.arange(n) / RATE
        tone = (np.sin(2 * np.pi * f * t) + 0.4 * np.sin(2 * np.pi * 2.76 * f * t) + 0.2 * np.sin(2 * np.pi * 5.4 * f * t))
        _place(out, tone * _env(n, 0.002, 0.45) * 0.22, k * step * (1 + 0.04 * np.sin(k)))
    return out, start


def beat_times(total: float, cuts: list[float]) -> list[float]:
    """When the heartbeat thumps: slow throughout, quickening over the three seconds before every cut."""
    times, at = [], 1.0
    while at < total - 1:
        times.append(at)
        to_cut = min((c - at for c in cuts if c > at), default=99.0)
        at += 1.05 if to_cut > 3 else max(0.42, 1.05 * to_cut / 3)
    return times


def score(total: float, voice_track: np.ndarray, cuts: list[float], box_spans: list[tuple[float, float]],
          stings: list[float]) -> np.ndarray:
    """The whole score: [cuts] get a riser and a quickening heartbeat, [stings] a hit, [box_spans] the music box."""
    rng = np.random.default_rng(13)
    n = int(total * RATE)
    t = np.arange(n) / RATE

    # Sub drone: D1 with a minor second above it, beating slowly, breathing in and out.
    breath = 0.6 + 0.4 * np.sin(2 * np.pi * t / 9.0)
    drone = (np.sin(2 * np.pi * 36.7 * t) + 0.6 * np.sin(2 * np.pi * 73.4 * t) + 0.45 * np.sin(2 * np.pi * 77.9 * t)
             + 0.25 * np.sin(2 * np.pi * 110.2 * t + 0.5 * np.sin(2 * np.pi * 0.13 * t))) * breath * 0.32

    # High strings: a tight cluster with tremolo that creeps in and out, never resolving.
    creep = np.clip(0.5 + 0.5 * np.sin(2 * np.pi * t / 23.0 - 1.2), 0, 1) ** 2
    trem = 0.6 + 0.4 * np.sin(2 * np.pi * 6.3 * t)
    strings = sum(np.sin(2 * np.pi * f * t + 0.8 * np.sin(2 * np.pi * 5.1 * t + f)) for f in (1174.7, 1244.5, 1661.2)) / 3
    strings *= creep * trem * 0.07

    # Wind: dark noise with gusts.
    wind = _lowpass(_lowpass(rng.uniform(-1, 1, n), 30), 30) * 6
    wind *= (0.4 + 0.6 * np.clip(np.sin(2 * np.pi * t / 13.0 + 2.0), 0, 1)) * 0.08

    bed = drone + strings + wind
    hits = np.zeros(n)

    beat = heartbeat()
    for at in beat_times(total, cuts):
        _place(hits, beat, at, 0.55)

    for c in cuts:
        r = riser(rng)
        _place(hits, r, c - r.size / RATE, 0.9)
    for s in stings:
        _place(hits, stinger(rng), s, 1.0)
    for start, end in box_spans:
        box, _ = music_box(start, end - start)
        fade = np.clip(np.arange(box.size) / RATE / 1.5, 0, 1) * np.clip((end - start - np.arange(box.size) / RATE) / 1.5, 0, 1)
        _place(bed, box * fade, start, 1.0)

    mix = bed + hits
    mix *= np.clip(t / 1.5, 0, 1) * np.clip((total - t) / 2.5, 0, 1)
    # The score steps back while the narrator talks, and comes up in the gaps.
    env = np.convolve(np.abs(voice_track[:n]), np.ones(4410) / 4410, mode="same") if voice_track.size else np.zeros(n)
    if env.size < n:
        env = np.pad(env, (0, n - env.size))
    duck = 1 - 0.7 * np.clip(env / (env.max() * 0.25 + 1e-9), 0, 1)
    return np.tanh(mix * duck * 0.9) * 0.42
