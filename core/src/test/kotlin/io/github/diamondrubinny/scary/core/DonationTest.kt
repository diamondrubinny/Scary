// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.RGBLuminanceSource
import io.github.diamondrubinny.scary.core.brand.Donation
import io.github.diamondrubinny.scary.core.brand.QrCode
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DonationTest {
    @Test
    fun `the donation link is PayPal over HTTPS`() {
        val uri = URI(Donation.URL)
        assertEquals("https", uri.scheme)
        assertEquals("www.paypal.com", uri.host)
        assertEquals("/donate/", uri.path)
        assertTrue(Donation.URL.contains(Donation.SHORT_URL))
    }

    @Test
    fun `the code on screen scans back to exactly the donation link`() {
        // Drawn the way the app draws it: dark modules on light, four light modules around, a few pixels per module.
        assertEquals(Donation.URL, decode(Donation.qr(), pixelsPerModule = 6))
    }

    @Test
    fun `any text round-trips, so the code always follows the link`() {
        val text = "https://example.org/donate?x=1"
        assertEquals(text, decode(Donation.qr(text), pixelsPerModule = 4))
    }

    private fun decode(code: QrCode, pixelsPerModule: Int): String {
        val side = (code.size + 2 * QrCode.QUIET_ZONE) * pixelsPerModule
        val pixels = IntArray(side * side) { i ->
            val x = (i % side) / pixelsPerModule - QrCode.QUIET_ZONE
            val y = (i / side) / pixelsPerModule - QrCode.QUIET_ZONE
            val dark = x in 0 until code.size && y in 0 until code.size && code.isDark(x, y)
            if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        return QRCodeReader().decode(bitmap, mapOf(DecodeHintType.TRY_HARDER to true)).text
    }

}
