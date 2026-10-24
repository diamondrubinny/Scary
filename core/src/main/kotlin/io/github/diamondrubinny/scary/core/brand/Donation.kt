// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.brand

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/**
 * Donations to SCARY, through PayPal. A TV has no browser worth the name, so the settings show [URL] as a QR code to
 * scan with a phone. SCARY itself never opens the link or sends anything to PayPal.
 */
object Donation {
    const val URL = "https://www.paypal.com/donate/?hosted_button_id=U8CEQ95ECPP9J"

    /** What the screen prints under the code, for anyone who would rather type it. */
    const val SHORT_URL = "paypal.com/donate"

    /** The code for [text], built once per call: cheap enough for a settings screen, so callers just remember it. */
    fun qr(text: String = URL): QrCode {
        val code = Encoder.encode(text, ErrorCorrectionLevel.M, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
        val matrix = code.matrix
        val size = matrix.width
        return QrCode(size, BooleanArray(size * size) { i -> matrix.get(i % size, i / size).toInt() == 1 })
    }
}

/** A QR code's modules, without the quiet zone: whoever draws it adds four light modules around it. */
class QrCode(val size: Int, private val dark: BooleanArray) {
    fun isDark(x: Int, y: Int): Boolean = dark[y * size + x]

    companion object {
        /** Light modules the QR specification asks for around the code, on every side. */
        const val QUIET_ZONE = 4
    }
}
