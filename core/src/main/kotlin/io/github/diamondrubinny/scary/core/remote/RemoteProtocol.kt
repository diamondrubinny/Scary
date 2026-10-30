// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The phone remote's wire protocol, between the page a phone opens (assets/remote/remote.js) and SCARY on the TV.
 *
 * The secret is in the QR code only, in the part of the address after "#", which a browser never sends: it never
 * crosses the network. Every command is signed with it (HMAC-SHA256) and carries a counter that must keep rising, so
 * a command cannot be forged or played again; text (a search, the TMDB key) is encrypted with a keystream from the
 * same secret, then signed (encrypt-then-MAC). The page is served over plain HTTP on the home network, where browsers
 * offer no Web Crypto, so remote.js computes the same with its own SHA-256; RemoteProtocolTest checks both agree.
 */
object RemoteProtocol {
    const val SECRET_BYTES = 32

    /** Longest text a command may carry: a TMDB read access token is a little over 200 characters. */
    const val MAX_TEXT = 600

    /** Longest request body accepted. */
    const val MAX_BODY = 4096

    enum class Command(val wire: String, val takesText: Boolean = false) {
        UP("up"), DOWN("down"), LEFT("left"), RIGHT("right"), OK("ok"), BACK("back"), HOME("home"),
        PLAY_PAUSE("play"), VOLUME_UP("vol_up"), VOLUME_DOWN("vol_down"), MUTE("mute"),
        SEARCH("search", takesText = true), OPEN("open", takesText = true), TMDB_KEY("tmdb", takesText = true),

        /** The page asks whether it is paired; it changes nothing. */
        PING("ping");

        companion object {
            fun fromWire(value: String): Command? = entries.firstOrNull { it.wire == value }
        }
    }

    data class Request(val counter: Long, val command: Command, val text: String)

    sealed interface Verdict {
        data class Accepted(val request: Request) : Verdict
        /** Not valid JSON, an unknown command, or text where none belongs. */
        data object Malformed : Verdict
        /** Signed with another secret, or changed on the way. */
        data object BadSignature : Verdict
        /** A counter not above the last one: a command played again. */
        data object Replayed : Verdict
    }

    /** The signature of a command: over its counter, its name and its encrypted text. */
    fun tag(secret: ByteArray, counter: Long, command: Command, cipher: ByteArray): ByteArray =
        hmac(secret, "mac".toByteArray() + be64(counter) + command.wire.toByteArray() + byteArrayOf(0) + cipher)

    /** XORs [data] with the keystream for [counter]: blocks HMAC(secret, "ks" | counter | block number). */
    fun crypt(secret: ByteArray, counter: Long, data: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        var block = 0
        var offset = 0
        while (offset < data.size) {
            val stream = hmac(secret, "ks".toByteArray() + be64(counter) + be32(block))
            for (i in stream.indices) {
                if (offset + i >= data.size) break
                out[offset + i] = (data[offset + i].toInt() xor stream[i].toInt()).toByte()
            }
            offset += stream.size
            block++
        }
        return out
    }

    /** What the phone sends, as JSON: the counter, the command, the encrypted text (base64) and the signature (hex). */
    fun seal(secret: ByteArray, counter: Long, command: Command, text: String = ""): String {
        val cipher = crypt(secret, counter, text.toByteArray(Charsets.UTF_8))
        val tag = tag(secret, counter, command, cipher)
        return """{"n":$counter,"c":"${command.wire}","x":"${Base64.getEncoder().encodeToString(cipher)}","t":"${hex(tag)}"}"""
    }

    /** One pairing: its secret, and the last counter it accepted. */
    class Session(private val secret: ByteArray) {
        private var last = 0L

        init {
            require(secret.size == SECRET_BYTES) { "secret must be $SECRET_BYTES bytes" }
        }

        @Synchronized
        fun open(body: String): Verdict {
            if (body.length > MAX_BODY) return Verdict.Malformed
            val o = try {
                json.parseToJsonElement(body).jsonObject
            } catch (e: IllegalArgumentException) {
                return Verdict.Malformed
            }
            val counter = (o["n"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: return Verdict.Malformed
            val command = o.string("c")?.let(Command::fromWire) ?: return Verdict.Malformed
            val cipher = try {
                Base64.getDecoder().decode(o.string("x").orEmpty())
            } catch (e: IllegalArgumentException) {
                return Verdict.Malformed
            }
            val tag = o.string("t")?.let(::unhex) ?: return Verdict.Malformed
            // The signature first, in constant time: nothing about the request is trusted before it.
            if (!MessageDigest.isEqual(tag, tag(secret, counter, command, cipher))) return Verdict.BadSignature
            if (counter <= last) return Verdict.Replayed
            if (cipher.size > MAX_TEXT * 4 || (!command.takesText && cipher.isNotEmpty())) return Verdict.Malformed
            val text = String(crypt(secret, counter, cipher), Charsets.UTF_8)
            if (text.length > MAX_TEXT || text.any { it < ' ' }) return Verdict.Malformed
            last = counter
            return Verdict.Accepted(Request(counter, command, text))
        }
    }

    /** The secret as it goes in the QR code's "#" part: URL-safe base64, no padding. */
    fun encodeSecret(secret: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(secret)

    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    private fun be64(value: Long) = ByteArray(8) { i -> (value shr (56 - 8 * i)).toByte() }

    private fun be32(value: Int) = ByteArray(4) { i -> (value shr (24 - 8 * i)).toByte() }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun unhex(text: String): ByteArray? {
        if (text.length != 64 || text.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
        return ByteArray(32) { i -> text.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
    }
}
