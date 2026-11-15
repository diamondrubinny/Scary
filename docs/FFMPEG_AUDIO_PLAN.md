# DTS / TrueHD audio in software (FFmpeg): an exploration plan, no code (15.11.2026)

**The request:** "Yes, explore it after this upgrade" (DTS/TrueHD in software). This document is an exploration and cost estimate. **There is no code here**, and nothing will be built before approval.

## The problem
- Many MKV files (Blu-ray remuxes) carry only DTS, DTS-HD MA or Dolby TrueHD audio.
- Most Android TVs **don't decode** these formats themselves. They can only pass them to an amplifier or soundbar (passthrough).
- Without an amplifier the film plays without sound. Since version 0.5.7, SCARY reports this and offers Real-Debrid's converted version.

## What was checked (15.11.2026)
- **Media3's FFmpeg extension (`decoder_ffmpeg`) is not published as a ready library.** Checked against Google's Maven repository: only the base `media3-decoder` is published, and `media3-decoder-ffmpeg` returns 404.
- So the extension has to be **built from source**, per the Media3 guide:
  - build FFmpeg with the NDK for each processor architecture (arm64-v8a, armeabi-v7a, x86_64 for the emulators);
  - link it with the extension's JNI code.
- **Requires verification against the Media3 documentation:** the exact steps and the supported FFmpeg version for Media3 1.11.

## Options

### A. FFmpeg built for audio only (recommended if we go ahead)
- **Build:** only the DTS (`dca`), TrueHD (`truehd`), and optionally AC-3 / E-AC-3 decoders (for TVs that lack them).
- **Size (estimate, requires measurement):** about 1–3 MB per architecture. With two ARM architectures, roughly +3–5 MB on top of the current ~4.4 MB APK.
- **Build in CI:**
  - a job that builds FFmpeg (cached between runs) or keeps a ready `.so` in the repository with its checksum and build script;
  - roughly 10–20 minutes for the first build.
- **In the player:**
  - `DefaultRenderersFactory` with `EXTENSION_RENDERER_MODE_PREFER` only for the formats the TV lacks;
  - DTS tracks would then be "supported" and stop appearing grayed out.

### B. Not building, relying on Real-Debrid's converted version (today's state)
- **Advantages:** no size, licensing or maintenance cost.
- **Disadvantages:**
  - lower quality (the conversion lowers the bitrate, and often turns 5.1 into stereo);
  - it depends on Real-Debrid generating the conversion;
  - embedded subtitles are lost in the conversion.

### C. Using another engine (libVLC / mpv)
- **Very large** (tens of MB), a different API, and a rewrite of the whole player.
- **Not recommended.**

## Licensing and legal (requires legal review)
- **FFmpeg licensing:**
  - FFmpeg is under LGPL 2.1+ when built without the GPL parts (`--enable-gpl` isn't needed for these decoders). **Requires verification** against the `configure` of the chosen version.
  - LGPL with a dynamically linked library (`.so`): you have to publish FFmpeg's source and the build flags, allow the library to be replaced, and include the license notice. SCARY is open source under Apache 2.0, so this is generally compatible. **Requires legal review.**
- **Patents:**
  - DTS and Dolby (TrueHD, AC-3) are technologies with patents and licensing terms.
  - Distributing a software decoder can require a license in some countries. **Requires legal review before publishing.** This is the main reason many apps don't ship these decoders.
- **NOTICE / third-party licenses:** FFmpeg would be added to the NOTICE file and to the in-app licenses screen.

## Security
- An FFmpeg decoder is native code (C) that parses files from the internet, so it is an attack surface (security bugs in decoders are common).
- **Required:**
  - a supported FFmpeg version, updated regularly, with a CI alert on new releases;
  - building only the needed decoders, which reduces the surface;
  - a check that FFmpeg's crash doesn't take SCARY down. Media3 runs decoders in the app's process, so a native crash would bring down the app. **A real risk** for a home screen launcher.

## Recommendation
1. **For now:** stay with B (the converted version plus the clear message from 0.5.7).
2. **If DTS is really common in your files:** do a short trial of option A on a separate branch:
   - building in CI;
   - measuring the real size;
   - testing on your TV with a DTS file.
   - Then decide whether to publish, **after a legal review** of licensing and patents.

## Decision questions (for you)
- How common are DTS/TrueHD files in your library? Is there an amplifier or soundbar connected to the TV (with one, passthrough already works)?
- Is +3–5 MB to the app acceptable?
- Is a legal review of FFmpeg licensing and patents possible before publishing?
