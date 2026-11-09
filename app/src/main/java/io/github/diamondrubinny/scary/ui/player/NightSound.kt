// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.ui.player

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * "Night sound": quiet explosions and screams, louder speech, so a film can be watched late without waking the house.
 * Android's own dynamics processor on the player's audio session (Android 9 and later): one compressor band over the
 * whole range, a make-up gain, and a limiter so nothing clips. Audio sent untouched to an amplifier (passthrough of
 * Dolby or DTS) does not go through it; on such a setup it changes nothing.
 */
class NightSound private constructor(private val effect: DynamicsProcessing) {
    fun release() {
        runCatching { effect.release() }
    }

    companion object {
        /** Whether this TV can do it at all. */
        val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        /** On for [audioSessionId], or null when the TV refuses (no effect, or the session is gone). */
        fun start(audioSessionId: Int): NightSound? {
            if (!supported || audioSessionId <= 0) return null
            return try {
                NightSound(create(audioSessionId))
            } catch (e: RuntimeException) {
                // UnsupportedOperationException, IllegalStateException, IllegalArgumentException: no night sound here.
                Log.w("ScaryPlayer", "Night sound unavailable: ${e.javaClass.simpleName}")
                null
            }
        }

        @RequiresApi(Build.VERSION_CODES.P)
        private fun create(audioSessionId: Int): DynamicsProcessing {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                CHANNELS,
                false, 0,
                true, 1,
                false, 0,
                true,
            ).build()
            val effect = DynamicsProcessing(0, audioSessionId, config)
            val band = DynamicsProcessing.MbcBand(
                true,
                20_000f, // cutoff: the whole range
                5f, // attack, ms
                120f, // release, ms
                3.5f, // ratio
                -30f, // threshold, dB
                8f, // knee width, dB
                -90f, // noise gate threshold, dB
                1f, // expander ratio
                0f, // pre-gain, dB
                9f, // post-gain, dB: speech comes up
            )
            effect.setMbcBandAllChannelsTo(0, band)
            effect.setLimiterAllChannelsTo(
                DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -2f, 0f),
            )
            effect.enabled = true
            return effect
        }

        private const val CHANNELS = 2
    }
}
