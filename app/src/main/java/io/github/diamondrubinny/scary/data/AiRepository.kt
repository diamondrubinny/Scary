// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.os.SystemClock
import android.util.Log
import io.github.diamondrubinny.scary.core.ai.AiPrompt
import io.github.diamondrubinny.scary.core.ai.AiProvider
import io.github.diamondrubinny.scary.core.ai.AiWire
import io.github.diamondrubinny.scary.core.ai.ModelPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Talks to the AI service the viewer chose. Each request goes to that service's own address only (a custom one: the
 * HTTPS address the viewer gave), never follows a redirect elsewhere, and is cut off when too slow or too large. Keys
 * are stored with the Android Keystore (SecretStore), never logged, and sent only to their own service. The free
 * service allows one request every 30 seconds per IP address: SCARY keeps to that and says how long to wait.
 */
class AiRepository(
    private val secrets: SecretStore,
    private val settings: SettingsRepository,
) {
    sealed interface Result {
        data class Ok(val text: String) : Result
        data class Wait(val seconds: Int) : Result
        data object NeedsKey : Result
        data object BadKey : Result
        data object NoCredit : Result
        data object NoModel : Result
        data object Unreachable : Result
        data class Failed(val code: Int) : Result
    }

    enum class KeyResult { SAVED, INVALID, REJECTED, UNREACHABLE, STORAGE }

    /** One request at a time: the free service allows nothing else, and a TV has no use for more. */
    private val busy = Mutex()

    @Volatile
    private var freeReadyAt = 0L

    /** Seconds until the free service takes another request (0 when it would now). */
    fun freeWaitSeconds(): Int = ((freeReadyAt - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)

    fun hasKey(provider: AiProvider): Boolean = keyOf(provider) != null

    /** "sk-…a1b2": enough to recognize a key, never enough to use it. */
    fun keyMask(provider: AiProvider): String? = keyOf(provider)?.let { key ->
        if (key.length <= 8) "••••" else key.take(3) + "…" + key.takeLast(4)
    }

    private fun keyOf(provider: AiProvider): String? = secrets.read(secretName(provider))?.takeIf { it.isNotBlank() }

    /** Checks the key with the service (its models list) before keeping it; the custom address keeps it as given. */
    suspend fun saveKey(provider: AiProvider, input: String): KeyResult {
        val key = input.trim()
        if (!validKey(key) || provider == AiProvider.POLLINATIONS_FREE) return KeyResult.INVALID
        val chatUrl = chatUrl(provider) ?: return KeyResult.INVALID
        val check = withContext(Dispatchers.IO) { listModels(provider, chatUrl, key) }
        when (check) {
            is Listed.Ids -> Unit
            Listed.Refused -> return KeyResult.REJECTED
            Listed.Unreachable -> if (provider != AiProvider.CUSTOM) return KeyResult.UNREACHABLE
            Listed.Unsupported -> Unit
        }
        if (!secrets.write(secretName(provider), key)) return KeyResult.STORAGE
        settings.setAiModel(provider, null)
        return KeyResult.SAVED
    }

    fun deleteKey(provider: AiProvider) = secrets.remove(secretName(provider))

    fun deleteAllKeys() = AiProvider.entries.forEach(::deleteKey)

    /** The service's chat models, for the viewer to choose from; empty when it cannot list them. */
    suspend fun models(provider: AiProvider): List<String> {
        provider.fixedModel?.let { return listOf(it) }
        val chatUrl = chatUrl(provider) ?: return emptyList()
        return when (val listed = withContext(Dispatchers.IO) { listModels(provider, chatUrl, keyOf(provider)) }) {
            is Listed.Ids -> listed.ids
            else -> emptyList()
        }
    }

    /** Sends [prompt] to [provider] and returns its text, or why not. */
    suspend fun complete(provider: AiProvider, prompt: AiPrompt): Result = busy.withLock {
        if (provider.free) freeWaitSeconds().takeIf { it > 0 }?.let { return@withLock Result.Wait(it) }
        val key = keyOf(provider)
        if (provider.needsKey && key == null) return@withLock Result.NeedsKey
        val chatUrl = chatUrl(provider) ?: return@withLock Result.NeedsKey
        val model = model(provider, chatUrl, key) ?: return@withLock Result.NoModel
        val body = AiWire.requestBody(provider.format, model, prompt, provider.reasoningEffort)
        val result = withContext(Dispatchers.IO) { post(provider, chatUrl, key, body) }
        if (provider.free) {
            val wait = (result as? Result.Wait)?.seconds ?: FREE_GAP_SECONDS
            freeReadyAt = SystemClock.elapsedRealtime() + wait * 1000L
        }
        // A model the service dropped: forget it, so the next request picks a current one.
        if (result is Result.Failed && result.code == HttpURLConnection.HTTP_NOT_FOUND) settings.setAiModel(provider, null)
        result
    }

    /** The model to use: the one saved for [provider], or one picked from its list (and saved). */
    private suspend fun model(provider: AiProvider, chatUrl: String, key: String?): String? {
        provider.fixedModel?.let { return it }
        settings.prefs.first().aiModels[provider.key]?.takeIf(AiWire::validModel)?.let { return it }
        val listed = withContext(Dispatchers.IO) { listModels(provider, chatUrl, key) }
        val picked = (listed as? Listed.Ids)?.ids?.let { ModelPicker.pick(provider, it) } ?: return null
        settings.setAiModel(provider, picked)
        return picked
    }

    private suspend fun chatUrl(provider: AiProvider): String? =
        if (provider == AiProvider.CUSTOM) settings.prefs.first().aiCustomUrl.takeIf { it.isNotEmpty() }?.let(AiProvider::customChatUrl)
        else provider.chatUrl

    private sealed interface Listed {
        data class Ids(val ids: List<String>) : Listed
        data object Refused : Listed
        data object Unreachable : Listed
        data object Unsupported : Listed
    }

    private fun listModels(provider: AiProvider, chatUrl: String, key: String?): Listed {
        val url = if (provider == AiProvider.CUSTOM) AiProvider.customModelsUrl(chatUrl) else provider.modelsUrl ?: return Listed.Unsupported
        val connection = open(url, provider, chatUrl, key, method = "GET") ?: return Listed.Unreachable
        return try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> Listed.Ids(AiWire.modelIds(readLimited(connection.inputStream, MAX_MODELS_BYTES) ?: ""))
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> Listed.Refused
                else -> if (code in 300..399) Listed.Unreachable else Listed.Unsupported
            }
        } catch (e: IOException) {
            Log.i(TAG, "AI models list failed: ${e.javaClass.simpleName}")
            Listed.Unreachable
        } finally {
            connection.disconnect()
        }
    }

    private fun post(provider: AiProvider, chatUrl: String, key: String?, body: String): Result {
        val connection = open(chatUrl, provider, chatUrl, key, method = "POST") ?: return Result.Unreachable
        return try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            when {
                code == HttpURLConnection.HTTP_OK -> {
                    val text = readLimited(connection.inputStream, MAX_REPLY_BYTES)?.let { AiWire.replyText(provider.format, it) }
                    if (text != null) Result.Ok(text) else Result.Failed(code)
                }
                code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN -> Result.BadKey
                // The free service answers 402 past its limit; paid ones mean no credit left.
                code == PAYMENT_REQUIRED && provider.free -> Result.Wait(retryAfter(connection))
                code == PAYMENT_REQUIRED -> Result.NoCredit
                code == TOO_MANY_REQUESTS -> Result.Wait(retryAfter(connection))
                else -> Result.Failed(code)
            }
        } catch (e: IOException) {
            Log.i(TAG, "AI request failed: ${e.javaClass.simpleName}")
            Result.Unreachable
        } finally {
            connection.disconnect()
        }
    }

    /**
     * A connection to [url], only on HTTPS and only to the host of [provider]'s own chat address ([chatUrl]): the key
     * can go nowhere else. Redirects are not followed.
     */
    private fun open(url: String, provider: AiProvider, chatUrl: String, key: String?, method: String): HttpsURLConnection? {
        val target = runCatching { URL(url) }.getOrNull() ?: return null
        val home = runCatching { URL(chatUrl) }.getOrNull() ?: return null
        if (target.protocol != "https" || home.protocol != "https" || !target.host.equals(home.host, ignoreCase = true)) return null
        val connection = try {
            target.openConnection() as? HttpsURLConnection ?: return null
        } catch (e: IOException) {
            return null
        }
        connection.instanceFollowRedirects = false
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "SCARY-launcher")
        if (key != null) when (provider.auth) {
            AiProvider.Auth.BEARER -> connection.setRequestProperty("Authorization", "Bearer $key")
            AiProvider.Auth.ANTHROPIC -> {
                connection.setRequestProperty("x-api-key", key)
                connection.setRequestProperty("anthropic-version", AiWire.ANTHROPIC_VERSION)
            }
            AiProvider.Auth.NONE -> Unit
        } else if (provider.auth == AiProvider.Auth.ANTHROPIC) {
            connection.setRequestProperty("anthropic-version", AiWire.ANTHROPIC_VERSION)
        }
        return connection
    }

    private fun retryAfter(connection: HttpURLConnection): Int =
        connection.getHeaderField("Retry-After")?.trim()?.toIntOrNull()?.coerceIn(1, MAX_WAIT_SECONDS) ?: FREE_GAP_SECONDS

    private fun readLimited(input: InputStream, limit: Int): String? = input.use {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = it.read(buffer)
            if (read < 0) break
            if (out.size() + read > limit) return null
            out.write(buffer, 0, read)
        }
        out.toString(Charsets.UTF_8.name())
    }

    companion object {
        private const val TAG = "ScaryAi"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 90_000
        private const val MAX_REPLY_BYTES = 256 * 1024
        private const val MAX_MODELS_BYTES = 2 * 1024 * 1024
        private const val PAYMENT_REQUIRED = 402
        private const val TOO_MANY_REQUESTS = 429
        /** The free service's gap between answers: 30 s in its documentation, 45 s in its replies on 8 Nov 2026. */
        const val FREE_GAP_SECONDS = 45
        private const val MAX_WAIT_SECONDS = 600

        fun secretName(provider: AiProvider): String = "ai_key_${provider.key}"

        /** A key as services issue them: printable ASCII, no spaces, 8 to 400 characters. */
        fun validKey(key: String): Boolean = key.length in 8..400 && key.all { it in '!'..'~' }
    }
}
