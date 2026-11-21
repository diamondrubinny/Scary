// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary

import android.view.KeyEvent
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isSelected
import io.github.diamondrubinny.scary.core.player.LanguagePrefs
import io.github.diamondrubinny.scary.core.player.OnlineSubtitles
import io.github.diamondrubinny.scary.core.player.SubtitleFiles
import io.github.diamondrubinny.scary.core.realdebrid.RealDebrid
import io.github.diamondrubinny.scary.ui.player.PLAYER_SUBTITLES_OFF_TAG
import io.github.diamondrubinny.scary.ui.player.PLAYER_TRACKS_BUTTON_TAG
import io.github.diamondrubinny.scary.ui.player.PLAYER_TRACKS_TAG
import io.github.diamondrubinny.scary.ui.player.trackTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.diamondrubinny.scary.data.RealDebridRepository
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import io.github.diamondrubinny.scary.ui.home.CONTINUE_MENU_TAG
import io.github.diamondrubinny.scary.ui.home.CONTINUE_MENU_REMOVE_TAG
import io.github.diamondrubinny.scary.ui.home.CONTINUE_MENU_WATCHED_TAG
import io.github.diamondrubinny.scary.ui.home.HOME_ROWS_TAG
import io.github.diamondrubinny.scary.ui.home.HomeViewModel
import io.github.diamondrubinny.scary.ui.home.OnlineSubtitleSource
import io.github.diamondrubinny.scary.ui.home.PlayerSession
import io.github.diamondrubinny.scary.ui.player.ONLINE_SEARCH_TAG
import io.github.diamondrubinny.scary.ui.player.ONLINE_TOGGLE_TAG
import io.github.diamondrubinny.scary.ui.player.languageNameIn
import io.github.diamondrubinny.scary.ui.player.onlineLanguageTag
import io.github.diamondrubinny.scary.ui.player.LOOK_COLOR_TAG
import io.github.diamondrubinny.scary.ui.player.LOOK_BACKGROUND_TAG
import io.github.diamondrubinny.scary.ui.player.LOOK_POSITION_TAG
import io.github.diamondrubinny.scary.ui.player.PLAYER_TAG
import io.github.diamondrubinny.scary.ui.player.PLAYER_TOAST_TAG
import io.github.diamondrubinny.scary.ui.player.PLAYER_SPEED_TAG
import io.github.diamondrubinny.scary.ui.player.PLAYER_SEEKBAR_TAG
import io.github.diamondrubinny.scary.ui.player.PLAYER_MARK_TAG
import io.github.diamondrubinny.scary.ui.player.PLAYER_BOOKMARK_LIST_TAG
import io.github.diamondrubinny.scary.ui.player.PLAYER_BOOKMARKS_TAG
import io.github.diamondrubinny.scary.ui.player.CONTINUE_CARD_TAG
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.onNodeWithTag
import io.github.diamondrubinny.scary.ui.components.REAL_DEBRID_NAV_TAG
import io.github.diamondrubinny.scary.ui.realdebrid.REAL_DEBRID_TAG
import io.github.diamondrubinny.scary.ui.settings.REAL_DEBRID_ROW_TAG
import io.github.diamondrubinny.scary.ui.stremio.STREMIO_TAG
import io.github.diamondrubinny.scary.ui.settings.STREMIO_ROW_TAG
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The internal player and the Real-Debrid screen. The player plays a short local clip (no account, no network): the
 * same player a Real-Debrid file opens in. The Real-Debrid screen is checked signed out, without connecting.
 */
@RunWith(AndroidJUnit4::class)
class PlayerTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun likeATv() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun text(id: Int): String = rule.activity.getString(id)

    private fun waitFor(matcher: SemanticsMatcher, timeoutMillis: Long = TIMEOUT_MS) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun viewModel(): HomeViewModel {
        val container = (rule.activity.application as ScaryApplication).container
        return ViewModelProvider(rule.activity, HomeViewModel.factory(container))[HomeViewModel::class.java]
    }

    private fun session(uri: String, transcoded: String? = null) = PlayerSession(
        key = "test:${uri.hashCode()}",
        playable = RealDebridRepository.Playable(uri, "Test clip") { transcoded },
        movieId = null,
        startAtMs = 0L,
    )

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    @Test
    fun aClipPlaysAndBackLeavesThePlayer() {
        waitFor(hasTestTag(HOME_ROWS_TAG))
        // The clip ships in the test APK; the player reads files from the app's own storage.
        val clip = File(rule.activity.cacheDir, "test-clip.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("test-clip.mp4").use { input -> clip.outputStream().use(input::copyTo) }
        rule.runOnUiThread { viewModel().playForTest(session(clip.toURI().toString())) }
        waitFor(hasTestTag(PLAYER_TAG))
        // Playing: the main button offers to pause.
        waitFor(hasText(text(R.string.player_pause)))
        assertTrue(rule.onAllNodes(hasText(text(R.string.player_error))).fetchSemanticsNodes().isEmpty())
        // A few quiet seconds later the controls hide; the remote's OK still reaches the player and pauses it.
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasText(text(R.string.player_pause))).fetchSemanticsNodes().isEmpty() }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor(hasText(text(R.string.player_play)))
        pressBack()
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasTestTag(PLAYER_TAG)).fetchSemanticsNodes().isEmpty() }
        assertTrue("The player session ends with the screen", viewModel().player.value == null)
    }

    /**
     * The upgraded player: a spot is marked (a message says so), the marked spots open in a list that Back closes
     * without leaving the film, the speed changes, and once the player is closed the file waits on the home screen
     * under "Continue watching".
     */
    @Test
    fun aSpotMarkedInThePlayerWaitsUnderContinueWatching() {
        waitFor(hasTestTag(HOME_ROWS_TAG))
        val clip = File(rule.activity.cacheDir, "test-clip.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("test-clip.mp4").use { input -> clip.outputStream().use(input::copyTo) }
        val vm = viewModel()
        // A key as Real-Debrid makes them, so the file goes into the watch history.
        val session = PlayerSession(
            key = "d:TESTCLIP",
            playable = RealDebridRepository.Playable(clip.toURI().toString(), "Test clip") { null },
            movieId = null,
            startAtMs = 0L,
        )
        try {
            rule.runOnUiThread { vm.playForTest(session) }
            waitFor(hasTestTag(PLAYER_TAG))
            // Paused, the controls stay up.
            waitFor(hasText(text(R.string.player_pause)))
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE)
            waitFor(hasText(text(R.string.player_play)))
            rule.onNode(hasTestTag(PLAYER_SEEKBAR_TAG)).assertExists()

            rule.onNode(hasTestTag(PLAYER_MARK_TAG)).performClick()
            waitFor(hasTestTag(PLAYER_TOAST_TAG))
            waitFor(hasTestTag(PLAYER_BOOKMARKS_TAG))
            rule.onNode(hasTestTag(PLAYER_BOOKMARKS_TAG)).performClick()
            waitFor(hasTestTag(PLAYER_BOOKMARK_LIST_TAG))
            // Back closes the list, not the film.
            rule.waitForIdle()
            pressBack()
            rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasTestTag(PLAYER_BOOKMARK_LIST_TAG)).fetchSemanticsNodes().isEmpty() }
            rule.onNode(hasTestTag(PLAYER_TAG)).assertExists()

            // The speed goes from normal to the next one.
            waitFor(hasText(rule.activity.getString(R.string.player_speed, "1×")))
            rule.onNode(hasTestTag(PLAYER_SPEED_TAG)).performClick()
            waitFor(hasText(rule.activity.getString(R.string.player_speed, "1.25×")))

            pressBack()
            rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasTestTag(PLAYER_TAG)).fetchSemanticsNodes().isEmpty() }
            assertTrue(vm.watchHistory.value.single { it.key == "d:TESTCLIP" }.bookmarks.size == 1)
            // Home: the file waits under "Continue watching".
            waitFor(hasTestTag(HOME_ROWS_TAG))
            rule.waitUntil(TIMEOUT_MS) { runCatching { rule.onNodeWithTag(HOME_ROWS_TAG).performScrollToKey("continue") }.isSuccess }
            waitFor(hasTestTag(CONTINUE_CARD_TAG))
            // Holding the card opens its menu. This file is no film SCARY knows, so there is nothing to mark as watched:
            // "Only remove from this row" takes it out, and says so.
            rule.onNode(hasTestTag(CONTINUE_CARD_TAG)).performTouchInput { longClick() }
            waitFor(hasTestTag(CONTINUE_MENU_TAG))
            assertTrue(rule.onAllNodes(hasTestTag(CONTINUE_MENU_WATCHED_TAG)).fetchSemanticsNodes().isEmpty())
            rule.onNode(hasText(text(R.string.continue_menu_note_file))).assertExists()
            rule.onNode(hasTestTag(CONTINUE_MENU_REMOVE_TAG)).performClick()
            rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasTestTag(CONTINUE_CARD_TAG)).fetchSemanticsNodes().isEmpty() }
            waitFor(hasText(text(R.string.continue_removed)))
            val store = (rule.activity.application as ScaryApplication).container.watchStore
            assertTrue("The file's saved spot is forgotten", store.entry("d:TESTCLIP") == null)
        } finally {
            rule.runOnUiThread { vm.clearWatchHistory() }
        }
    }

    /**
     * "Audio and subtitles": an MKV with English and Hebrew audio and subtitles plays English by itself (the default
     * languages); the window lists every track, switches to the Hebrew dub, adds the torrent's Hebrew subtitle file
     * (Windows-1255, read as Hebrew) and turns subtitles off; Back closes the window, not the film; the choice is kept
     * for the file.
     */
    @Test
    fun theTrackWindowSwitchesAudioAndSubtitlesAndRemembersThem() {
        waitFor(hasTestTag(HOME_ROWS_TAG))
        val context = InstrumentationRegistry.getInstrumentation().context
        val clip = File(rule.activity.cacheDir, "tracks.mkv")
        context.assets.open("tracks.mkv").use { input -> clip.outputStream().use(input::copyTo) }
        val subtitleBytes = context.assets.open("tracks-he-1255.srt").use { it.readBytes() }
        val container = (rule.activity.application as ScaryApplication).container
        container.watchStore.setLanguages(LanguagePrefs())
        val vm = viewModel()
        val session = PlayerSession(
            key = "d:TESTTRACKS",
            playable = RealDebridRepository.Playable(clip.toURI().toString(), "Tracks") { null },
            movieId = null,
            startAtMs = 0L,
            subtitles = listOf(RealDebrid.RdSubtitle("Tracks.heb.srt", subtitleBytes.size.toLong(), "https://real-debrid.com/d/TESTSUB")),
            // As the repository does: the file read in its own encoding, kept as UTF-8.
            fetchSubtitle = { subtitle ->
                SubtitleFiles.decode(subtitleBytes, SubtitleFiles.mimeType(subtitle.name), "he")?.let { text ->
                    File(rule.activity.cacheDir, "tracks-he-utf8.srt").apply { writeText(text) }
                }
            },
        )
        fun row(text: String) = hasContentDescription(text, substring = true) and hasAnyAncestor(hasTestTag(PLAYER_TRACKS_TAG))
        try {
            rule.runOnUiThread { vm.playForTest(session) }
            waitFor(hasTestTag(PLAYER_TAG))
            waitFor(hasText(text(R.string.player_pause)))
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE)
            waitFor(hasText(text(R.string.player_play)))
            waitFor(hasTestTag(PLAYER_TRACKS_BUTTON_TAG))
            rule.onNode(hasTestTag(PLAYER_TRACKS_BUTTON_TAG)).performClick()
            waitFor(hasTestTag(PLAYER_TRACKS_TAG))

            // English audio (stereo) and English subtitles, by themselves.
            waitFor(row(text(R.string.track_stereo)) and isSelected())
            waitFor(row("Hebrew dub"))
            rule.onNode(hasTestTag(trackTag("x0"))).assertExists()
            // The Hebrew dub, 5.1.
            rule.onNode(row("Hebrew dub")).performScrollTo().performClick()
            waitFor(row("Hebrew dub") and row("5.1") and isSelected())
            // The torrent's subtitle file: fetched, read as Hebrew, and shown.
            rule.onNode(hasTestTag(trackTag("x0"))).performScrollTo().performClick()
            waitFor(hasTestTag(trackTag("x0")) and isSelected(), LONG_TIMEOUT_MS)
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PLAY)
            waitFor(hasText("שלום מקובץ הכתוביות"), LONG_TIMEOUT_MS)
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE)
            // Off.
            rule.onNode(hasTestTag(PLAYER_SUBTITLES_OFF_TAG)).performScrollTo().performClick()
            waitFor(hasTestTag(PLAYER_SUBTITLES_OFF_TAG) and isSelected())
            // How subtitles look: a press changes the colour, and it is kept for every film.
            rule.onNode(hasTestTag(LOOK_COLOR_TAG)).performScrollTo().performClick()
            waitFor(hasText(rule.activity.getString(R.string.tracks_look_color, text(R.string.tracks_look_yellow))))
            assertTrue("yellow subtitles are kept", container.watchStore.subtitleColor == 1)
            rule.onNode(hasTestTag(LOOK_BACKGROUND_TAG)).performScrollTo().assertExists()
            rule.onNode(hasTestTag(LOOK_POSITION_TAG)).performScrollTo().assertExists()

            // Back closes the window, not the film.
            rule.waitForIdle()
            pressBack()
            rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasTestTag(PLAYER_TRACKS_TAG)).fetchSemanticsNodes().isEmpty() }
            rule.onNode(hasTestTag(PLAYER_TAG)).assertExists()
            val memory = container.watchStore.trackMemory("d:TESTTRACKS")
            assertTrue("Hebrew audio kept for the file: $memory", memory?.audio?.contains("|he|") == true)
            assertTrue("Subtitles off kept for the file: $memory", memory?.subtitlesOff == true)
            pressBack()
            rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasTestTag(PLAYER_TAG)).fetchSemanticsNodes().isEmpty() }
        } finally {
            rule.runOnUiThread { vm.clearWatchHistory() }
            container.watchStore.setLanguages(LanguagePrefs())
            container.watchStore.subtitleColor = 0
        }
    }

    private fun onlineEntry(id: String, language: String, release: String) = OnlineSubtitles.Entry(
        id = id,
        url = "https://subs5.strem.io/test/$id",
        language = language,
        fileName = "Tracks.$language.srt",
        release = release,
        format = null,
        fps = null,
        hearingImpaired = false,
        order = id.toInt(),
    )

    /** A fake service: what it lists, the subtitle text for each language, and how often it was asked. */
    private class FakeService(val entries: List<OnlineSubtitles.Entry>?, val texts: Map<String, String>, val cacheDir: File) {
        var searches = 0
        var fetched = mutableListOf<String>()
        val source = OnlineSubtitleSource(
            search = {
                searches++
                entries
            },
            fetch = { entry ->
                fetched += entry.language
                texts[entry.language]?.let { text -> File(cacheDir, "online-${entry.id}.srt").apply { writeText("1\n00:00:00,500 --> 00:00:05,500\n$text\n\n", Charsets.UTF_8) } }
            },
        )
    }

    private fun frenchAndHebrew() = listOf(
        onlineEntry("1", "fr", "Tracks.720p.WEB"),
        onlineEntry("2", "he", "Tracks.720p.WEB"),
        onlineEntry("3", "he", "Tracks.1080p.BluRay"),
    )

    private fun playClipWith(service: FakeService, key: String): PlayerSession {
        val clip = File(rule.activity.cacheDir, "tracks.mkv")
        InstrumentationRegistry.getInstrumentation().context.assets.open("tracks.mkv").use { input -> clip.outputStream().use(input::copyTo) }
        return PlayerSession(
            key = key,
            playable = RealDebridRepository.Playable(clip.toURI().toString(), "Tracks.2013.720p.WEB.mkv") { null },
            movieId = null,
            startAtMs = 0L,
            online = service.source,
        )
    }

    /**
     * Subtitles found online by the film's name: the viewer wants French subtitles, the file has none, so the player
     * searches by itself and says what it found; the window then lists the languages the service has and the viewer
     * adds Hebrew with one press; both are remembered for the file.
     */
    @Test
    fun subtitlesAreFoundOnlineByThemselvesWhenTheFileHasNoneInTheViewersLanguage() {
        waitFor(hasTestTag(HOME_ROWS_TAG))
        val container = (rule.activity.application as ScaryApplication).container
        val service = FakeService(frenchAndHebrew(), mapOf("fr" to "Bonjour du service", "he" to "שלום מהשירות"), rule.activity.cacheDir)
        container.watchStore.setOnlineSubtitles(true)
        // French: neither in the file nor among the torrent's files.
        container.watchStore.setLanguages(LanguagePrefs(audio = null, subtitles = "fr"))
        val vm = viewModel()
        val locale = rule.activity.resources.configuration.locales[0]
        try {
            rule.runOnUiThread { vm.playForTest(playClipWith(service, "d:TESTONLINE")) }
            waitFor(hasTestTag(PLAYER_TAG))
            waitFor(hasText(text(R.string.player_pause)))
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE)
            // Said, by itself: "Subtitles found: French".
            waitFor(hasText(rule.activity.getString(R.string.player_online_found, languageNameIn("fr", locale))), LONG_TIMEOUT_MS)
            assertTrue("one search, and only the French subtitle fetched: ${service.fetched}", service.searches == 1 && service.fetched == listOf("fr"))
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PLAY)
            waitFor(hasText("Bonjour du service"), LONG_TIMEOUT_MS)
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE)

            // The window: the languages the service has, the viewer's own first, French playing.
            waitFor(hasTestTag(PLAYER_TRACKS_BUTTON_TAG))
            rule.onNode(hasTestTag(PLAYER_TRACKS_BUTTON_TAG)).performClick()
            waitFor(hasTestTag(PLAYER_TRACKS_TAG))
            waitFor(hasTestTag(onlineLanguageTag("fr")) and isSelected())
            rule.onNode(hasTestTag(onlineLanguageTag("he"))).assertExists()
            // Hebrew with one press: the best of its two subtitles (the one made for this WEB release).
            rule.onNode(hasTestTag(onlineLanguageTag("he"))).performScrollTo().performClick()
            waitFor(hasTestTag(onlineLanguageTag("he")) and isSelected(), LONG_TIMEOUT_MS)
            assertTrue("Hebrew fetched once: ${service.fetched}", service.fetched == listOf("fr", "he"))
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PLAY)
            waitFor(hasText("שלום מהשירות"), LONG_TIMEOUT_MS)
            val memory = container.watchStore.trackMemory("d:TESTONLINE")
            assertTrue("The viewer's choice is kept for the file: $memory", memory?.subtitle?.contains("|o|he|") == true)
            // The search ran once for the whole film, not again when the window opened.
            assertTrue("one search: ${service.searches}", service.searches == 1)
            pressBack()
            rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasTestTag(PLAYER_TRACKS_TAG)).fetchSemanticsNodes().isEmpty() }
            pressBack()
            rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasTestTag(PLAYER_TAG)).fetchSemanticsNodes().isEmpty() }
        } finally {
            rule.runOnUiThread { vm.clearWatchHistory() }
            container.watchStore.setLanguages(LanguagePrefs())
            container.watchStore.setOnlineSubtitles(true)
        }
    }

    /** With "Find subtitles online" off nothing is searched, by itself or in the window, until the viewer turns it on. */
    @Test
    fun nothingIsSearchedOnlineWhileTheSettingIsOff() {
        waitFor(hasTestTag(HOME_ROWS_TAG))
        val container = (rule.activity.application as ScaryApplication).container
        val service = FakeService(frenchAndHebrew(), mapOf("fr" to "Bonjour du service"), rule.activity.cacheDir)
        container.watchStore.setOnlineSubtitles(false)
        container.watchStore.setLanguages(LanguagePrefs(audio = null, subtitles = "fr"))
        val vm = viewModel()
        try {
            rule.runOnUiThread { vm.playForTest(playClipWith(service, "d:TESTONLINEOFF")) }
            waitFor(hasTestTag(PLAYER_TAG))
            waitFor(hasText(text(R.string.player_pause)))
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE)
            waitFor(hasText(text(R.string.player_play)))
            waitFor(hasTestTag(PLAYER_TRACKS_BUTTON_TAG))
            rule.onNode(hasTestTag(PLAYER_TRACKS_BUTTON_TAG)).performClick()
            waitFor(hasTestTag(PLAYER_TRACKS_TAG))
            waitFor(hasTestTag(ONLINE_TOGGLE_TAG))
            rule.onNode(hasText(text(R.string.tracks_online_off))).assertExists()
            rule.onAllNodes(hasTestTag(onlineLanguageTag("fr"))).fetchSemanticsNodes().let { assertTrue("no language rows while off", it.isEmpty()) }
            assertTrue("nothing was asked while off: ${service.searches}", service.searches == 0 && service.fetched.isEmpty())
            // Turned on in the window: the search runs and the languages appear (nothing is picked by itself).
            rule.onNode(hasTestTag(ONLINE_TOGGLE_TAG)).performScrollTo().performClick()
            waitFor(hasTestTag(onlineLanguageTag("fr")), LONG_TIMEOUT_MS)
            assertTrue("one search after turning it on: ${service.searches}", service.searches == 1 && service.fetched.isEmpty())
            rule.onNode(hasTestTag(ONLINE_TOGGLE_TAG)).assertExists()
            pressBack()
            rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasTestTag(PLAYER_TRACKS_TAG)).fetchSemanticsNodes().isEmpty() }
            pressBack()
            rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasTestTag(PLAYER_TAG)).fetchSemanticsNodes().isEmpty() }
        } finally {
            rule.runOnUiThread { vm.clearWatchHistory() }
            container.watchStore.setLanguages(LanguagePrefs())
            container.watchStore.setOnlineSubtitles(true)
        }
    }

    /** A service that cannot be reached is said so in the window, with "Search again"; the film plays on. */
    @Test
    fun anUnreachableSubtitleServiceIsSaidAndRetried() {
        waitFor(hasTestTag(HOME_ROWS_TAG))
        val container = (rule.activity.application as ScaryApplication).container
        val service = FakeService(null, emptyMap(), rule.activity.cacheDir)
        container.watchStore.setOnlineSubtitles(true)
        container.watchStore.setLanguages(LanguagePrefs(audio = null, subtitles = "fr"))
        val vm = viewModel()
        try {
            rule.runOnUiThread { vm.playForTest(playClipWith(service, "d:TESTONLINEDOWN")) }
            waitFor(hasTestTag(PLAYER_TAG))
            waitFor(hasText(text(R.string.player_pause)))
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE)
            waitFor(hasTestTag(PLAYER_TRACKS_BUTTON_TAG))
            rule.onNode(hasTestTag(PLAYER_TRACKS_BUTTON_TAG)).performClick()
            waitFor(hasTestTag(PLAYER_TRACKS_TAG))
            waitFor(hasText(text(R.string.tracks_online_unreachable)), LONG_TIMEOUT_MS)
            val before = service.searches
            rule.onNode(hasTestTag(ONLINE_SEARCH_TAG)).performScrollTo().performClick()
            rule.waitUntil(LONG_TIMEOUT_MS) { service.searches > before }
            pressBack()
            rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasTestTag(PLAYER_TRACKS_TAG)).fetchSemanticsNodes().isEmpty() }
            rule.onNode(hasTestTag(PLAYER_TAG)).assertExists()
            pressBack()
            rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasTestTag(PLAYER_TAG)).fetchSemanticsNodes().isEmpty() }
        } finally {
            rule.runOnUiThread { vm.clearWatchHistory() }
            container.watchStore.setLanguages(LanguagePrefs())
        }
    }

    @Test
    fun aFileThatCannotPlaySaysSoAndCloses() {
        waitFor(hasTestTag(HOME_ROWS_TAG))
        val missing = File(rule.activity.cacheDir, "missing-file.mkv").also { it.delete() }
        rule.runOnUiThread { viewModel().playForTest(session(missing.toURI().toString())) }
        waitFor(hasText(text(R.string.player_error)))
        rule.onNode(hasText(text(R.string.player_close))).performClick()
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasTestTag(PLAYER_TAG)).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun theTopBarOpensRealDebridInOnePress() {
        waitFor(hasTestTag(HOME_ROWS_TAG))
        rule.onNode(hasTestTag(REAL_DEBRID_NAV_TAG)).performClick()
        waitFor(hasTestTag(REAL_DEBRID_TAG))
        pressBack()
        waitFor(hasTestTag(HOME_ROWS_TAG))
    }

    @Test
    fun theRealDebridScreenExplainsAndOffersToConnect() {
        waitFor(hasTestTag(HOME_ROWS_TAG))
        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        waitFor(hasTestTag(REAL_DEBRID_ROW_TAG))
        rule.onNode(hasTestTag(REAL_DEBRID_ROW_TAG)).performScrollTo().performClick()
        waitFor(hasTestTag(REAL_DEBRID_TAG))
        // A build with the sources feature says what that build does.
        val sources = rule.activity.resources.getBoolean(R.bool.rd_sources_enabled)
        rule.onNode(hasText(text(if (sources) R.string.rd_what_sources else R.string.rd_what))).assertExists()
        rule.onNode(hasText(text(R.string.rd_sign_in))).assertExists()
        rule.onNode(hasText(text(R.string.rd_privacy))).assertExists()
        pressBack()
    }


    /** Settings → Stremio account: what connecting does (and never does), and "Connect"; nothing is sent until then. */
    @Test
    fun theStremioScreenExplainsAndOffersToConnect() {
        waitFor(hasTestTag(HOME_ROWS_TAG))
        rule.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        waitFor(hasTestTag(STREMIO_ROW_TAG))
        rule.onNode(hasTestTag(STREMIO_ROW_TAG)).performScrollTo().performClick()
        waitFor(hasTestTag(STREMIO_TAG))
        rule.onNode(hasText(text(R.string.stremio_what))).assertExists()
        rule.onNode(hasText(text(R.string.stremio_sign_in))).assertExists()
        rule.onNode(hasText(text(R.string.stremio_privacy))).assertExists()
        pressBack()
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasTestTag(STREMIO_TAG)).fetchSemanticsNodes().isEmpty() }
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val LONG_TIMEOUT_MS = 40_000L
    }
}
