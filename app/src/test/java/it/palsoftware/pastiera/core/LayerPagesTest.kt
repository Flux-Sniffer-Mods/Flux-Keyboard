package it.palsoftware.pastiera.core

import it.palsoftware.pastiera.setPagesStartOnRecents
import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.data.emoji.RecentEmojiManager
import it.palsoftware.pastiera.data.symbols.SymbolSearch
import it.palsoftware.pastiera.inputmethod.AlternateCharacterManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import it.palsoftware.pastiera.saveSymMappings

/** The emoji layer and the symbols page as pages (on by default): Q back, P on, recents first. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LayerPagesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var prefs: SharedPreferences
    private lateinit var controller: SymLayoutController

    @Before
    fun setUp() {
        SettingsManager.getPreferences(context).edit().clear().commit()
        context.getSharedPreferences("recent_emojis_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "kaomoji_recents.txt").delete()
        prefs = context.getSharedPreferences("layer_pages_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        controller = SymLayoutController(context, prefs, AlternateCharacterManager(context.assets, prefs, context))
    }

    private fun press(keyCode: Int): SymLayoutController.SymKeyResult = controller.handleKeyWhenActive(
        keyCode,
        KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, keyCode, 0),
        null,
        ctrlLatchActive = false,
        altLatchActive = false,
        updateStatusBar = {}
    )

    private fun hold(keyCode: Int, repeat: Int): SymLayoutController.SymKeyResult = controller.handleKeyWhenActive(
        keyCode,
        KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, keyCode, repeat),
        null,
        ctrlLatchActive = false,
        altLatchActive = false,
        updateStatusBar = {}
    )

    /**
     * Turns the emoji layer's pages to an emoji with skin tones the test phone's font draws,
     * returning its key and the emoji.
     */
    private fun turnToEmojiWithTones(): Pair<Int, String> {
        repeat(200) {
            keys().entries.firstOrNull { (key, emoji) ->
                key != KeyEvent.KEYCODE_Q && key != KeyEvent.KEYCODE_P &&
                    controller.showEmojiVariants(emoji).also { if (it) controller.closeEmojiVariants() }
            }?.let { return it.key to it.value }
            press(KeyEvent.KEYCODE_P)
        }
        error("No emoji with skin tones on any page")
    }

    private fun keys(): Map<Int, String> = controller.currentSymMappings().orEmpty()

    @Test
    fun theLayerOpensWithArrowsAndSearch() {
        controller.toggleEmojiKeyPage(layer = true)
        val shown = keys()
        // Q leads back to the recents page: it shows the recents icon
        assertEquals(SymLayoutController.RECENTS_KEY_LABEL, shown[KeyEvent.KEYCODE_Q])
        assertEquals(SymLayoutController.KAOMOJI_NEXT_LABEL, shown[KeyEvent.KEYCODE_P])
        assertEquals(SymLayoutController.SEARCH_KEY_LABEL, shown[KeyEvent.KEYCODE_A])
    }

    @Test
    fun opensOnTheDefaultPageWithRecentsOnQ() {
        RecentEmojiManager.addRecentEmoji(context, "😀")
        controller.toggleEmojiKeyPage(layer = true)
        // Developer's pick: the most used emoji first
        assertEquals("😂", keys()[KeyEvent.KEYCODE_W])
        assertEquals(SymLayoutController.SEARCH_KEY_LABEL, keys()[KeyEvent.KEYCODE_A])
        press(KeyEvent.KEYCODE_Q)
        assertEquals("😀", keys()[KeyEvent.KEYCODE_W])
    }

    @Test
    fun recentEmojiOpenFirstWithSearchOnA() {
        SettingsManager.setPagesStartOnRecents(context, "emoji", true)
        RecentEmojiManager.addRecentEmoji(context, "😀")
        controller.toggleEmojiKeyPage(layer = true)
        val shown = keys()
        assertEquals(SymLayoutController.SEARCH_KEY_LABEL, shown[KeyEvent.KEYCODE_A])
        assertEquals("😀", shown[KeyEvent.KEYCODE_W])

        val searches = mutableListOf<SymLayoutController.SearchTarget>()
        controller.onSearchKey = { searches += it }
        assertEquals(SymLayoutController.SymKeyResult.CONSUME, press(KeyEvent.KEYCODE_A))
        assertEquals(listOf(SymLayoutController.SearchTarget.EMOJI_LAYER), searches)
    }

    @Test
    fun qAndPTurnThePagesRoundAndRound() {
        // A recent, so the recents page isn't the first page's starters alone
        RecentEmojiManager.addRecentEmoji(context, "🦄")
        controller.toggleEmojiKeyPage(layer = true)
        val first = keys()
        press(KeyEvent.KEYCODE_P)
        assertNotEquals(first, keys())
        press(KeyEvent.KEYCODE_Q)
        assertEquals(first, keys())
        // Back from the recents page goes round to the last page
        press(KeyEvent.KEYCODE_Q)
        assertNotEquals(first, keys())
    }

    @Test
    fun symbolsEndWithTheKaomojiReachedFromTheRecentsPage() {
        SymbolSearch.addRecent(context, "→")
        assertTrue(controller.openSymbolsPage())
        assertEquals(SymLayoutController.KAOMOJI_KEY_LABEL, keys()[KeyEvent.KEYCODE_L])
        press(KeyEvent.KEYCODE_L)
        assertTrue(SymLayoutController.kaomojiShown)
        // The first kaomoji page has the kaomoji search on A
        val searches = mutableListOf<SymLayoutController.SearchTarget>()
        controller.onSearchKey = { searches += it }
        press(KeyEvent.KEYCODE_A)
        assertEquals(listOf(SymLayoutController.SearchTarget.KAOMOJI), searches)
    }

    @Test
    fun aLetterWithNothingOnItTypesNothing() {
        controller.toggleEmojiKeyPage(layer = true)
        assertEquals(SymLayoutController.SymKeyResult.CONSUME, press(KeyEvent.KEYCODE_A))
    }

    @Test
    fun emojiPagesFollowTheStandardCategories() {
        controller.toggleEmojiKeyPage(layer = true)
        press(KeyEvent.KEYCODE_P)
        // The first category page: smileys (those the phone's font draws), from W along the rows
        val first = keys()[KeyEvent.KEYCODE_W]
        assertTrue(first != null && first.codePointAt(0) in 0x1F600..0x1F64F || first?.codePointAt(0) in 0x1F910..0x1F92F)
    }

    @Test
    fun holdingAnEmojiWithSkinTonesShowsThemOnePerKey() {
        controller.toggleEmojiKeyPage(layer = true)
        val (key, emoji) = turnToEmojiWithTones()
        hold(key, 0)
        hold(key, 1)
        val shown = keys()
        assertEquals(emoji, shown[KeyEvent.KEYCODE_W])
        assertTrue(shown.size > 2)
        assertEquals(SymLayoutController.KAOMOJI_PREVIOUS_LABEL, shown[KeyEvent.KEYCODE_Q])
        // Its release is spent, and Q goes back to the page
        assertTrue(controller.handlePagedKeyUp(key, null) {})
        press(KeyEvent.KEYCODE_Q)
        assertEquals(emoji, keys()[key])
    }

    @Test
    fun aQuickPressOfAnEmojiWithSkinTonesTypesItOnRelease() {
        controller.toggleEmojiKeyPage(layer = true)
        val (key, emoji) = turnToEmojiWithTones()
        hold(key, 0)
        assertTrue(controller.handlePagedKeyUp(key, null) {})
        assertEquals(emoji, RecentEmojiManager.getRecentEmojis(context).first())
    }

    @Test
    fun aTouchHoldShowsTheSkinTonesToo() {
        controller.toggleEmojiKeyPage(layer = true)
        val (_, emoji) = turnToEmojiWithTones()
        assertTrue(controller.showEmojiVariants(emoji))
        assertEquals(emoji, keys()[KeyEvent.KEYCODE_W])
        assertTrue(controller.closeEmojiVariants())
    }
}
