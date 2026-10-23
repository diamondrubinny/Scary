// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates a quiet, endless horror ambience: two slightly detuned low tones that beat against each other,
 * a slow swell, and filtered "wind" noise. Everything is synthesized, so there are no audio files or rights to clear.
 */
class DroneSynth(private val sampleRate: Int = 22_050, seed: Int = 666) {
    private val random = Random(seed)
    private var frame = 0L
    private var brown = 0.0

    /** Fills [out] with 16-bit mono PCM. Peak level stays below a quarter of full scale. */
    fun render(out: ShortArray) {
        for (i in out.indices) {
            val t = frame.toDouble() / sampleRate
            val swell = 0.55 + 0.45 * sin(2 * PI * t / SWELL_PERIOD_S)
            val tone = 0.5 * sin(2 * PI * 55.0 * t) + 0.5 * sin(2 * PI * 55.35 * t) + 0.2 * sin(2 * PI * 82.4 * t)
            brown = (brown + (random.nextDouble() * 2 - 1) * 0.02).coerceIn(-1.0, 1.0)
            val gust = 0.5 + 0.5 * sin(2 * PI * t / GUST_PERIOD_S + 1.3)
            val sample = (tone * swell * 0.55 + brown * gust * 0.6) * PEAK
            out[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(-Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            frame++
        }
    }

    private companion object {
        const val PEAK = 0.18
        const val SWELL_PERIOD_S = 11.0
        const val GUST_PERIOD_S = 17.0
    }
}
