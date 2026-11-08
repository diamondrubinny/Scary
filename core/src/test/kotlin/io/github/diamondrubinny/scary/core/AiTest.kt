// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.core

import io.github.diamondrubinny.scary.core.ai.AiAnswer
import io.github.diamondrubinny.scary.core.ai.AiCandidates
import io.github.diamondrubinny.scary.core.ai.AiMessage
import io.github.diamondrubinny.scary.core.ai.AiPrompts
import io.github.diamondrubinny.scary.core.ai.AiProvider
import io.github.diamondrubinny.scary.core.ai.AiWire
import io.github.diamondrubinny.scary.core.ai.ModelPicker
import io.github.diamondrubinny.scary.core.catalog.Personalize
import io.github.diamondrubinny.scary.core.model.Genre
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.core.model.Subgenre
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** AI in SCARY: the right addresses, only the films SCARY sent come back, and only what may be sent is sent. */
class AiTest {
    private fun film(id: String, title: String, vararg subs: Subgenre, synopsis: String = "", rating: Double? = 7.0, director: String = "") =
        Movie(id = id, imdbId = "tt0000001", title = title, year = 2010, genre = Genre.HORROR, subgenres = subs.toList(), synopsisEn = synopsis, imdbRating = rating, director = director)

    private val films = listOf(
        film("a", "Doll House", Subgenre.GHOST, synopsis = "A cursed doll waits in the attic."),
        film("b", "Camp Blood", Subgenre.SLASHER, synopsis = "A masked killer stalks a summer camp."),
        film("c", "The Village", Subgenre.FOLK, synopsis = "A pagan ritual in a remote village."),
    )

    @Test
    fun `every provider talks HTTPS to its own host, and the free one needs no key`() {
        AiProvider.entries.filter { it != AiProvider.CUSTOM }.forEach { provider ->
            assertTrue(provider.chatUrl, provider.chatUrl.startsWith("https://"))
            assertNotNull(provider.host)
            provider.modelsUrl?.let { assertTrue(it.startsWith("https://${provider.host}/")) }
        }
        assertFalse(AiProvider.POLLINATIONS_FREE.needsKey)
        assertEquals("openai", AiProvider.POLLINATIONS_FREE.fixedModel)
        assertTrue(AiProvider.OPENAI.needsKey && AiProvider.ANTHROPIC.needsKey && AiProvider.GEMINI.needsKey)
        assertEquals(AiProvider.Format.ANTHROPIC, AiProvider.ANTHROPIC.format)
        assertEquals(AiProvider.POLLINATIONS_FREE, AiProvider.DEFAULT)
    }

    @Test
    fun `a custom address must be plain HTTPS`() {
        assertEquals("https://ai.example.com/v1/chat/completions", AiProvider.customChatUrl("https://ai.example.com/v1/"))
        assertEquals("https://ai.example.com/v1/chat/completions", AiProvider.customChatUrl("https://ai.example.com/v1/chat/completions"))
        assertNull(AiProvider.customChatUrl("http://192.168.1.5:11434/v1"))
        assertNull(AiProvider.customChatUrl("https://user:pw@ai.example.com/v1"))
        assertNull(AiProvider.customChatUrl("https://ai.example.com/v1?key=secret"))
        assertNull(AiProvider.customChatUrl("javascript:alert(1)"))
        assertNull(AiProvider.customChatUrl("https://ai example.com"))
        assertEquals("https://ai.example.com/v1/models", AiProvider.customModelsUrl("https://ai.example.com/v1/chat/completions"))
    }

    @Test
    fun `a quick chat model is picked from the service's own list`() {
        val openai = listOf("text-embedding-4", "dall-e-4", "gpt-6.1", "gpt-6.1-mini-2026-09-01", "gpt-6.1-mini", "whisper-2", "omni-moderation")
        assertEquals("gpt-6.1-mini", ModelPicker.pick(AiProvider.OPENAI, openai))
        assertEquals("claude-haiku-5-5", ModelPicker.pick(AiProvider.ANTHROPIC, listOf("claude-opus-5-5", "claude-haiku-5-5", "claude-sonnet-5-5")))
        assertEquals("gemini-4-flash-lite", ModelPicker.pick(AiProvider.GEMINI, listOf("gemini-4-pro", "gemini-4-flash", "gemini-4-flash-lite", "text-embedding-005")))
        assertEquals("a/b:free", ModelPicker.pick(AiProvider.OPENROUTER, listOf("x/paid", "a/b:free", "c/longer-name:free")))
        assertEquals("openai", ModelPicker.pick(AiProvider.POLLINATIONS_FREE, emptyList()))
        assertNull(ModelPicker.pick(AiProvider.OPENAI, listOf("text-embedding-4", "tts-2")))
        assertEquals("some-model", ModelPicker.pick(AiProvider.CUSTOM, listOf("some-model")))
    }

    @Test
    fun `the AI chooses only among the films SCARY sent, each once, by number`() {
        val reply = """Sure! ```json
            {"picks":[{"n":2,"why":"Masked killer, **summer camp** https://evil.example/x"},{"n":999,"why":"made up"},
            {"n":0,"why":"zero"},{"n":"3","why":"Folk ‮ritual"},{"n":2,"why":"again"}],"reply":"Two for tonight."}
            ```"""
        val picks = AiAnswer.parsePicks(reply, films, max = 8)!!
        assertEquals(listOf("b", "c"), picks.picks.map { it.movie.id })
        assertEquals("Masked killer, summer camp", picks.picks[0].why)
        assertFalse(picks.picks[1].why.contains('‮'))
        assertEquals("Two for tonight.", picks.reply)
        assertNull(AiAnswer.parsePicks("I think you would like Hereditary!", films, max = 8))
        assertEquals(0, AiAnswer.parsePicks("""{"picks":"nope"}""", films, 8)!!.picks.size)
        val many = """{"picks":[{"n":1},{"n":2},{"n":3}]}"""
        assertEquals(2, AiAnswer.parsePicks(many, films, max = 2)!!.picks.size)
    }

    @Test
    fun `free text is plain and short`() {
        val text = AiAnswer.text("# Title\n\n\n\n*Bold* see https://x.example and <script>alert(1)</script>‏", max = 40)
        assertFalse(text.contains("https://"))
        assertFalse(text.contains('<') || text.contains('*') || text.contains('#'))
        assertTrue(text.length <= 41)
    }

    @Test
    fun `requests carry the request and public film facts, and taste only as a summary when given`() {
        val prompt = AiPrompts.recommend("x".repeat(1000), films, Language.HE, tasteSummary = null)
        assertTrue(prompt.system.contains("1. Doll House (2010)"))
        assertTrue(prompt.system.contains("Hebrew"))
        assertFalse(prompt.system.contains("taste"))
        assertTrue(prompt.messages.single().text.length <= AiPrompts.MAX_QUERY_CHARS + 1)
        val withTaste = AiPrompts.recommend("ghosts", films, Language.EN, tasteSummary = "likes ghost, folk")
        assertTrue(withTaste.system.contains("likes ghost, folk"))
    }

    @Test
    fun `a taste summary names traits, never films or people`() {
        val watched = listOf(
            film("w1", "Secret Ghost Film", Subgenre.GHOST, director = "Jane Director"),
            film("w2", "Another Ghost", Subgenre.GHOST, Subgenre.FOLK, director = "Jane Director"),
            film("w3", "Third One", Subgenre.FOLK, director = "Jane Director"),
        )
        val byId = watched.associateBy { it.id }
        val taste = Personalize.taste(byId::get, watched.map { it.id }, emptyList(), emptyList())
        val summary = AiPrompts.tasteSummary(taste)!!
        assertTrue(summary, summary.contains("ghost") && summary.contains("folk"))
        listOf("Secret", "Another", "Third", "Jane", "w1").forEach { assertFalse(summary, summary.contains(it)) }
        assertNull(AiPrompts.tasteSummary(Personalize.Taste.NONE))
    }

    @Test
    fun `the chat keeps the last turns, starting with the viewer`() {
        val turns = (1..20).map { AiMessage(if (it % 2 == 1) AiMessage.Role.USER else AiMessage.Role.ASSISTANT, "turn $it " + "y".repeat(900)) }
        val prompt = AiPrompts.chat(turns, Language.EN, null)
        assertTrue(prompt.messages.size <= AiPrompts.MAX_CHAT_TURNS)
        assertEquals(AiMessage.Role.USER, prompt.messages.first().role)
        assertTrue(prompt.messages.all { it.text.length <= AiPrompts.MAX_CHAT_CHARS + 1 })
    }

    @Test
    fun `candidates put matching films first and stay within the limit`() {
        val many = films + (1..200).map { film("x$it", "Other $it", Subgenre.CREATURE, rating = 5.0) }
        val picked = AiCandidates.select(many, "a doll in the attic", Personalize.Taste.NONE, watched = emptySet(), limit = 50)
        assertEquals("a", picked.first().id)
        assertEquals(50, picked.size)
        assertEquals(picked.size, picked.map { it.id }.toSet().size)
        assertTrue(AiCandidates.select(emptyList(), "x", Personalize.Taste.NONE, emptySet()).isEmpty())
    }

    @Test
    fun `request bodies are built as JSON for both shapes, and replies read back`() {
        val prompt = AiPrompts.chat(listOf(AiMessage(AiMessage.Role.USER, "hi \"quoted\" }")), Language.EN, null)
        val openai = Json.parseToJsonElement(AiWire.requestBody(AiProvider.Format.OPENAI, "m", prompt)).jsonObject
        assertEquals("system", openai["messages"]!!.jsonArray[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("hi \"quoted\" }", openai["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content)
        val anthropic = Json.parseToJsonElement(AiWire.requestBody(AiProvider.Format.ANTHROPIC, "m", prompt)).jsonObject
        assertTrue(anthropic.containsKey("system") && anthropic.containsKey("max_tokens"))
        assertEquals("user", anthropic["messages"]!!.jsonArray[0].jsonObject["role"]!!.jsonPrimitive.content)

        assertEquals("Hello", AiWire.replyText(AiProvider.Format.OPENAI, """{"choices":[{"message":{"role":"assistant","content":"Hello"}}]}"""))
        assertEquals("Hi\nthere", AiWire.replyText(AiProvider.Format.ANTHROPIC, """{"content":[{"type":"text","text":"Hi"},{"type":"text","text":"there"}]}"""))
        assertNull(AiWire.replyText(AiProvider.Format.OPENAI, "<html>error</html>"))
        assertNull(AiWire.replyText(AiProvider.Format.OPENAI, """{"choices":[]}"""))

        assertEquals(listOf("gpt-x", "gpt-y"), AiWire.modelIds("""{"data":[{"id":"gpt-x"},{"id":"gpt-y"}]}"""))
        assertEquals(listOf("gemini-x"), AiWire.modelIds("""{"models":[{"name":"models/gemini-x"}]}"""))
        assertTrue(AiWire.validModel("anthropic/claude-haiku-5.5:fast"))
        assertFalse(AiWire.validModel("x\"}],\"evil\":1"))
        assertFalse(AiWire.validModel(""))
    }
}
