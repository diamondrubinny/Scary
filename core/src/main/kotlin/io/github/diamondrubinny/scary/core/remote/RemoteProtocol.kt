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
 * same secret, then signed (encrypt-then-MAC). An answer that carries the viewer's backup goes back the same way,
 * with its own keystream and signature. The page is served over plain HTTP on the home network, where browsers
 * offer no Web Crypto, so remote.js computes the same with its own SHA-256; RemoteProtocolTest checks both agree.
 */
object RemoteProtocol {
    const val SECRET_BYTES = 32

    /** Longest text a command may carry: a TMDB read access token is a little over 200 characters. */
    const val MAX_TEXT = 600

    /** Longest request body accepted. */
    const val MAX_BODY = 4096

    /** Longest text of a large command (a backup file to restore): Backup.MAX_CHARS. */
    const val MAX_LARGE_TEXT = 120_000

    /** Longest body of a large command: its text encrypted, in base64, with the counter and signature around it. */
    const val MAX_LARGE_BODY = 170_000

    enum class Command(val wire: String, val takesText: Boolean = false, val large: Boolean = false) {
        UP("up"), DOWN("down"), LEFT("left"), RIGHT("right"), OK("ok"), BACK("back"), HOME("home"),
        PLAY_PAUSE("play"), VOLUME_UP("vol_up"), VOLUME_DOWN("vol_down"), MUTE("mute"),
        SEARCH("search", takesText = true), OPEN("open", takesText = true), TMDB_KEY("tmdb", takesText = true),

        /** An AI service's key, as "service\nkey" (see AiProvider.key). */
        AI_KEY("ai_key", takesText = true),

        /** The phone asks for the viewer's backup; the answer carries it, encrypted and signed ([Session.sealAnswer]). */
        BACKUP("backup"),

        /** A backup file's text, to merge into the TV's lists. Large: taken only where a large body is expected. */
        RESTORE("restore", takesText = true, large = true),

        /**
         * A WireGuard settings file for the VPN in Settings, in base64 (a file has line breaks, and a command's text
         * may not). Large, like a backup. It is only kept on the TV: connecting is done at the TV, never from a phone.
         */
        VPN_CONFIG("vpn", takesText = true, large = true),
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

    /** An answer's signature: over the counter of the command it answers, and its encrypted text. */
    fun answerTag(secret: ByteArray, counter: Long, cipher: ByteArray): ByteArray =
        hmac(secret, "amac".toByteArray() + be64(counter) + cipher)

    /** An answer's text, encrypted or decrypted: its own keystream ("ka"), never the one the command's text used. */
    fun cryptAnswer(secret: ByteArray, counter: Long, data: ByteArray): ByteArray = crypt(secret, counter, data, "ka")

    /** An answer's text as the page reads it back, or null when it was not signed with [secret] for [counter]. */
    fun openAnswer(secret: ByteArray, counter: Long, answer: SealedAnswer): String? {
        val cipher = try {
            Base64.getDecoder().decode(answer.cipher)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val tag = unhex(answer.tag) ?: return null
        if (!MessageDigest.isEqual(tag, answerTag(secret, counter, cipher))) return null
        return String(cryptAnswer(secret, counter, cipher), Charsets.UTF_8)
    }

    /** An answer's encrypted text (base64) and its signature (hex). */
    data class SealedAnswer(val cipher: String, val tag: String)

    /** XORs [data] with the keystream for [counter]: blocks HMAC(secret, [label] | counter | block number). */
    fun crypt(secret: ByteArray, counter: Long, data: ByteArray, label: String = "ks"): ByteArray {
        val out = ByteArray(data.size)
        var block = 0
        var offset = 0
        while (offset < data.size) {
            val stream = hmac(secret, label.toByteArray() + be64(counter) + be32(block))
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

        /**
         * [large] is true where a large body is expected (the restore address): only a large command is taken there,
         * and none anywhere else, so the usual small limits hold for everything but a backup file.
         */
        @Synchronized
        fun open(body: String, large: Boolean = false): Verdict {
            if (body.length > (if (large) MAX_LARGE_BODY else MAX_BODY)) return Verdict.Malformed
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
            if (command.large != large) return Verdict.Malformed
            val maxText = if (large) MAX_LARGE_TEXT else MAX_TEXT
            // A large text is plain ASCII (a backup), so it is as many bytes as characters.
            val maxCipher = if (large) MAX_LARGE_TEXT else MAX_TEXT * 4
            if (cipher.size > maxCipher || (!command.takesText && cipher.isNotEmpty())) return Verdict.Malformed
            val text = String(crypt(secret, counter, cipher), Charsets.UTF_8)
            if (text.length > maxText || !cleanText(command, text)) return Verdict.Malformed
            last = counter
            return Verdict.Accepted(Request(counter, command, text))
        }

        /** [text] for the phone that sent command [counter]: encrypted and signed with this pairing's secret. */
        fun sealAnswer(counter: Long, text: String): SealedAnswer {
            val cipher = cryptAnswer(secret, counter, text.toByteArray(Charsets.UTF_8))
            return SealedAnswer(Base64.getEncoder().encodeToString(cipher), hex(answerTag(secret, counter, cipher)))
        }
    }

    /**
     * No control characters, with one exception: [Command.AI_KEY] carries "service\nkey", so it may hold that one
     * line break (and only one, between two non-empty parts).
     */
    private fun cleanText(command: Command, text: String): Boolean {
        if (command != Command.AI_KEY) return text.none { it < ' ' }
        val parts = text.split('\n')
        return parts.size == 2 && parts.all { part -> part.isNotEmpty() && part.none { it < ' ' } }
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
