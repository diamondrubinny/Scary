// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * The two shapes AI services speak: OpenAI's chat completions (OpenAI, Gemini, Groq, OpenRouter, Mistral, DeepSeek,
 * Pollinations, custom addresses) and Anthropic's Messages API. Request bodies are built with a JSON builder (never by
 * pasting strings), and replies are read defensively.
 */
object AiWire {
    /** Sent with every Anthropic request, as its API requires. */
    const val ANTHROPIC_VERSION = "2023-06-01"

    private val json = Json { ignoreUnknownKeys = true }

    fun requestBody(format: AiProvider.Format, model: String, prompt: AiPrompt): String = when (format) {
        AiProvider.Format.OPENAI -> buildJsonObject {
            put("model", model)
            put(
                "messages",
                buildJsonArray {
                    add(buildJsonObject { put("role", "system"); put("content", prompt.system) })
                    prompt.messages.forEach { add(message(it)) }
                },
            )
            put("stream", false)
        }
        AiProvider.Format.ANTHROPIC -> buildJsonObject {
            put("model", model)
            put("max_tokens", prompt.maxTokens)
            put("system", prompt.system)
            put("messages", buildJsonArray { prompt.messages.forEach { add(message(it)) } })
        }
    }.toString()

    private fun message(message: AiMessage) = buildJsonObject {
        put("role", if (message.role == AiMessage.Role.USER) "user" else "assistant")
        put("content", message.text)
    }

    /** The answer's text, or null when the body is not a reply SCARY understands. */
    fun replyText(format: AiProvider.Format, body: String): String? {
        val root = parse(body) ?: return null
        return when (format) {
            AiProvider.Format.OPENAI -> {
                val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
                val message = choice?.get("message") as? JsonObject
                (message?.get("content") as? JsonPrimitive)?.takeIf { it.isString }?.content
            }
            AiProvider.Format.ANTHROPIC -> (root["content"] as? JsonArray)
                ?.mapNotNull { block -> (block as? JsonObject)?.takeIf { (it["type"] as? JsonPrimitive)?.content == "text" } }
                ?.mapNotNull { (it["text"] as? JsonPrimitive)?.content }
                ?.joinToString("\n")
        }?.takeIf { it.isNotBlank() }
    }

    /** Model ids from a models list: OpenAI's {"data":[{"id":…}]}, Anthropic's the same, or {"models":[{"name":"models/…"}]}. */
    fun modelIds(body: String): List<String> {
        val root = parse(body) ?: return emptyList()
        val data = (root["data"] as? JsonArray ?: root["models"] as? JsonArray).orEmpty()
        return data.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            ((item["id"] ?: item["name"]) as? JsonPrimitive)?.content?.removePrefix("models/")
        }.filter { it.isNotBlank() && it.length <= MAX_MODEL_ID }.distinct().take(MAX_MODELS)
    }

    /** A model name the viewer typed or a service listed: what is safe to put in a request. */
    fun validModel(model: String): Boolean = model.length in 1..MAX_MODEL_ID && model.all { it.isLetterOrDigit() || it in "-_.:/@+" }

    private fun parse(body: String) = try {
        json.parseToJsonElement(body).jsonObject
    } catch (e: IllegalArgumentException) {
        null
    }

    private const val MAX_MODEL_ID = 120
    private const val MAX_MODELS = 400
}
