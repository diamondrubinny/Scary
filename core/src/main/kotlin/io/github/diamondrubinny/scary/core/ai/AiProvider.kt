// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.ai

import java.net.URI

/**
 * The AI services SCARY can talk to. Every address below was checked against the service itself on 8 Nov 2026
 * (each answers a request without a key with its own "key required" error). Model names change too often to fix in
 * code, so SCARY asks each service for its list ([modelsUrl]) and picks one ([ModelPicker]); the viewer can change it.
 */
enum class AiProvider(
    val key: String,
    val displayName: String,
    /** The chat address; for [CUSTOM] it comes from the viewer. */
    val chatUrl: String,
    val modelsUrl: String?,
    val auth: Auth,
    val format: Format,
    /** Where to get a key, said in settings and on the phone. */
    val keyPage: String?,
    /** Model used without asking the service, when there is one that never changes. */
    val fixedModel: String? = null,
) {
    /** Free, no key, no sign-up: one request every 30 seconds per IP address (Pollinations' Classic API). */
    POLLINATIONS_FREE(
        "pollinations_free", "Pollinations (free)", "https://text.pollinations.ai/openai", null,
        Auth.NONE, Format.OPENAI, null, fixedModel = "openai",
    ),
    POLLINATIONS(
        "pollinations", "Pollinations", "https://gen.pollinations.ai/v1/chat/completions", "https://gen.pollinations.ai/v1/models",
        Auth.BEARER, Format.OPENAI, "enter.pollinations.ai",
    ),
    OPENAI(
        "openai", "OpenAI", "https://api.openai.com/v1/chat/completions", "https://api.openai.com/v1/models",
        Auth.BEARER, Format.OPENAI, "platform.openai.com/api-keys",
    ),
    ANTHROPIC(
        "anthropic", "Anthropic (Claude)", "https://api.anthropic.com/v1/messages", "https://api.anthropic.com/v1/models",
        Auth.ANTHROPIC, Format.ANTHROPIC, "console.anthropic.com/settings/keys",
    ),
    GEMINI(
        "gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
        "https://generativelanguage.googleapis.com/v1beta/openai/models", Auth.BEARER, Format.OPENAI, "aistudio.google.com/apikey",
    ),
    GROQ(
        "groq", "Groq", "https://api.groq.com/openai/v1/chat/completions", "https://api.groq.com/openai/v1/models",
        Auth.BEARER, Format.OPENAI, "console.groq.com/keys",
    ),
    OPENROUTER(
        "openrouter", "OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "https://openrouter.ai/api/v1/models",
        Auth.BEARER, Format.OPENAI, "openrouter.ai/keys",
    ),
    MISTRAL(
        "mistral", "Mistral", "https://api.mistral.ai/v1/chat/completions", "https://api.mistral.ai/v1/models",
        Auth.BEARER, Format.OPENAI, "console.mistral.ai/api-keys",
    ),
    DEEPSEEK(
        "deepseek", "DeepSeek", "https://api.deepseek.com/chat/completions", "https://api.deepseek.com/models",
        Auth.BEARER, Format.OPENAI, "platform.deepseek.com/api_keys",
    ),

    /** Any OpenAI-compatible service at an HTTPS address the viewer gives; the key is optional. */
    CUSTOM("custom", "OpenAI-compatible address", "", null, Auth.BEARER, Format.OPENAI, null);

    enum class Auth { NONE, BEARER, ANTHROPIC }
    enum class Format { OPENAI, ANTHROPIC }

    val needsKey: Boolean get() = auth != Auth.NONE && this != CUSTOM
    val free: Boolean get() = this == POLLINATIONS_FREE

    /** The only host a request (and so the key) may go to. */
    val host: String? get() = chatUrl.takeIf { it.isNotEmpty() }?.let { runCatching { URI(it).host }.getOrNull() }

    companion object {
        val DEFAULT = POLLINATIONS_FREE
        fun fromKey(key: String?): AiProvider? = entries.firstOrNull { it.key == key }

        /** A custom address: HTTPS, a host, no user name in it, at most 300 characters. Null when not acceptable. */
        fun customChatUrl(input: String): String? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.length > MAX_URL || trimmed.any { it.isWhitespace() || it.isISOControl() }) return null
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrEmpty() || uri.rawUserInfo != null) return null
            if (uri.rawQuery != null || uri.rawFragment != null) return null
            // "https://host/v1" or the full ".../chat/completions" both work.
            return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
        }

        /** The models list next to a custom chat address (".../chat/completions" → ".../models"). */
        fun customModelsUrl(chatUrl: String): String = chatUrl.removeSuffix("/chat/completions") + "/models"

        private const val MAX_URL = 300
    }
}

/**
 * Picks a model from a service's own list: a quick, inexpensive one that answers chat, by name. Lists change, so this
 * prefers patterns rather than exact names, skips models that do not chat (embeddings, images, speech, moderation),
 * and falls back to the first that is left.
 */
object ModelPicker {
    private val notChat = Regex(
        "embed|whisper|tts|speech|audio|image|vision-preview|dall-e|moderation|transcri|realtime|search|rerank|guard|ocr|video|sora|computer-use|codex",
        RegexOption.IGNORE_CASE,
    )

    private val preferences: Map<AiProvider, List<String>> = mapOf(
        AiProvider.OPENAI to listOf("mini", "nano", "gpt"),
        AiProvider.ANTHROPIC to listOf("haiku", "sonnet", "claude"),
        AiProvider.GEMINI to listOf("flash-lite", "flash", "gemini"),
        AiProvider.GROQ to listOf("llama", "qwen", "gpt-oss", "mixtral"),
        AiProvider.OPENROUTER to listOf(":free"),
        AiProvider.MISTRAL to listOf("mistral-small", "small", "mistral"),
        AiProvider.DEEPSEEK to listOf("deepseek-chat", "chat"),
        AiProvider.POLLINATIONS to listOf("openai", "mini", "nano", "flash"),
    )

    fun pick(provider: AiProvider, ids: List<String>): String? {
        provider.fixedModel?.let { return it }
        val chat = ids.map(String::trim).filter { it.isNotEmpty() && !notChat.containsMatchIn(it) }.distinct()
        if (chat.isEmpty()) return null
        for (pattern in preferences[provider].orEmpty()) {
            // Among the matches, the shortest name is usually the plain current one ("gpt-x-mini", not a dated copy).
            chat.filter { it.contains(pattern, ignoreCase = true) }.minByOrNull { it.length }?.let { return it }
        }
        return chat.first()
    }
}
