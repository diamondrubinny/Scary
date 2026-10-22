// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import io.github.diamondrubinny.scary.core.audio.DroneSynth
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Plays the synthesized ambience while the launcher is on screen. Started in onStart and stopped in onStop,
 * so it never plays over Stremio or any other app.
 */
class AmbientPlayer {
    /** One flag per playback thread, so a quick stop-then-start never leaves two threads playing. */
    private var active: AtomicBoolean? = null

    fun start() {
        if (active != null) return
        val flag = AtomicBoolean(true)
        active = flag
        thread(name = "scary-ambience", isDaemon = true, priority = Thread.MIN_PRIORITY) { play(flag) }
    }

    fun stop() {
        active?.set(false)
        active = null
    }

    private fun play(running: AtomicBoolean) {
        val track = try {
            val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuffer, CHUNK_FRAMES * 2 * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Ambient audio unavailable: ${e.javaClass.simpleName}")
            return
        }

        val synth = DroneSynth(SAMPLE_RATE)
        val chunk = ShortArray(CHUNK_FRAMES)
        var written = 0L
        try {
            track.play()
            while (running.get()) {
                synth.render(chunk)
                // Two-second fade-in so the sound never starts abruptly.
                for (i in chunk.indices) {
                    val gain = ((written + i).toDouble() / FADE_IN_FRAMES).coerceAtMost(1.0)
                    chunk[i] = (chunk[i] * gain).toInt().toShort()
                }
                written += chunk.size
                if (track.write(chunk, 0, chunk.size) < 0) break
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Ambient audio stopped: ${e.message}")
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private companion object {
        const val TAG = "AmbientPlayer"
        const val SAMPLE_RATE = 22_050
        const val CHUNK_FRAMES = 2_048
        const val FADE_IN_FRAMES = SAMPLE_RATE * 2
    }
}
