package it.palsoftware.pastiera.core

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.SymPagesConfig
import it.palsoftware.pastiera.data.emoji.RecentEmojiManager
import it.palsoftware.pastiera.inputmethod.AlternateCharacterManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EmojiKeyScreensTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var prefs: SharedPreferences
    private lateinit var controller: SymLayoutController

    @Before
    fun setUp() {
        SettingsManager.getPreferences(context).edit().clear().commit()
        // The layers on one page each, as before pages (LayerPagesTest has the pages)
        SettingsManager.setEmojiLayerPages(context, false)
        SettingsManager.setSymbolsPages(context, false)
        context.getSharedPreferences("recent_emojis_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = context.getSharedPreferences("emoji_key_screens_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        controller = SymLayoutController(context, prefs, AlternateCharacterManager(context.assets, prefs, context))
    }

    @Test
    fun emojiKeyTogglesThePickerOrTheLayer() {
        assertTrue(controller.toggleEmojiKeyPage(layer = false))
        assertEquals(4, controller.currentSymPage())
        assertTrue(controller.openedByEmojiKey)

        assertFalse(controller.toggleEmojiKeyPage(layer = false))
        assertEquals(0, controller.currentSymPage())
        assertFalse(controller.openedByEmojiKey)

        assertTrue(controller.toggleEmojiKeyPage(layer = true))
        assertEquals(1, controller.currentSymPage())
    }

    @Test
    fun layerOpenedByTheEmojiKeyStaysWhenItIsNotInTheSymCycle() {
        SettingsManager.setSymPagesConfig(context, SymPagesConfig(emojiEnabled = false))

        controller.toggleEmojiKeyPage(layer = true)

        assertEquals(1, controller.currentSymPage())
    }

    @Test
    fun symCycleClearsTheEmojiKeyFlag() {
        controller.toggleEmojiKeyPage(layer = true)

        controller.toggleSymPage()

        assertFalse(controller.openedByEmojiKey)
    }

    @Test
    fun recentsKeyShowsRecentEmojiOnTheOtherKeys() {
        SettingsManager.setEmojiLayerRecentsKey(context, KeyEvent.KEYCODE_A)
        RecentEmojiManager.addRecentEmoji(context, "🙂")
        RecentEmojiManager.addRecentEmoji(context, "🎉")
        controller.toggleEmojiKeyPage(layer = true)
        assertEquals(SymLayoutController.RECENTS_KEY_LABEL, controller.currentSymMappings()!![KeyEvent.KEYCODE_A])

        assertTrue(controller.toggleEmojiLayerRecents())

        val shown = controller.currentSymMappings()!!
        assertEquals(SymLayoutController.RECENTS_BACK_LABEL, shown[KeyEvent.KEYCODE_A])
        // Q keeps the search key; the recents start on W, then the layer's own emoji
        assertEquals(SymLayoutController.SEARCH_KEY_LABEL, shown[KeyEvent.KEYCODE_Q])
        assertEquals("🎉", shown[KeyEvent.KEYCODE_W])
        assertEquals("🙂", shown[KeyEvent.KEYCODE_E])
    }

    @Test
    fun pressingTheRecentsKeyTogglesInsteadOfTyping() {
        SettingsManager.setEmojiLayerRecentsKey(context, KeyEvent.KEYCODE_A)
        controller.toggleEmojiKeyPage(layer = true)
        var updates = 0

        val result = controller.handleKeyWhenActive(
            KeyEvent.KEYCODE_A,
            KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0),
            null,
            ctrlLatchActive = false,
            altLatchActive = false,
            updateStatusBar = { updates++ }
        )

        assertEquals(SymLayoutController.SymKeyResult.CONSUME, result)
        assertTrue(controller.emojiLayerShowsRecents)
        assertEquals(1, updates)
    }

    @Test
    fun recentsKeyIsAByDefaultAndMustBeAnEmojiLayerKey() {
        assertEquals(KeyEvent.KEYCODE_A, SettingsManager.getEmojiLayerRecentsKey(context))
        assertFalse(SettingsManager.setEmojiLayerRecentsKey(context, KeyEvent.KEYCODE_SPACE))
        assertEquals(KeyEvent.KEYCODE_A, SettingsManager.getEmojiLayerRecentsKey(context))
        assertTrue(SettingsManager.setEmojiLayerRecentsKey(context, KeyEvent.KEYCODE_M))
        assertEquals(KeyEvent.KEYCODE_M, SettingsManager.getEmojiLayerRecentsKey(context))
    }

    @Test
    fun turningTheRecentsKeyOffSticks() {
        assertTrue(SettingsManager.setEmojiLayerRecentsKey(context, KeyEvent.KEYCODE_UNKNOWN))
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, SettingsManager.getEmojiLayerRecentsKey(context))
    }

    @Test
    fun emojiScreensUseTheirOwnAutoCloseOnlyWithAnEmojiKey() {
        SettingsManager.setSymAutoClose(context, true)
        SettingsManager.setSymAutoCloseOnTouch(context, true)
        SettingsManager.setEmojiKeyAutoClose(context, false)
        SettingsManager.setEmojiPickerKey(context, KeyEvent.KEYCODE_SHIFT_RIGHT)

        // Emoji key set: the picker and the emoji key's layer follow the emoji key setting
        assertFalse(SettingsManager.emojiScreenClosesAfterInput(context, isPicker = true, openedByEmojiKey = false, byTouch = true))
        assertFalse(SettingsManager.emojiScreenClosesAfterInput(context, isPicker = false, openedByEmojiKey = true, byTouch = false))
        // ...while the layer reached through Sym keeps SYM auto-close
        assertTrue(SettingsManager.emojiScreenClosesAfterInput(context, isPicker = false, openedByEmojiKey = false, byTouch = true))

        // No emoji key: everything follows SYM auto-close, as before
        SettingsManager.setEmojiPickerKey(context, KeyEvent.KEYCODE_UNKNOWN)
        assertTrue(SettingsManager.emojiScreenClosesAfterInput(context, isPicker = true, openedByEmojiKey = false, byTouch = true))
    }

    @Test
    fun gifKeyIsPAndShowsOnlyWhileGifSearchIsOn() {
        assertEquals(KeyEvent.KEYCODE_P, SettingsManager.getEmojiLayerGifKey(context))
        controller.toggleEmojiKeyPage(layer = true)
        assertTrue(controller.currentSymMappings()!![KeyEvent.KEYCODE_P] != SymLayoutController.GIF_KEY_LABEL)

        SettingsManager.setGifsEnabled(context, true)

        assertEquals(SymLayoutController.GIF_KEY_LABEL, controller.currentSymMappings()!![KeyEvent.KEYCODE_P])
    }

    @Test
    fun pressingTheGifKeyAsksForGifSearch() {
        SettingsManager.setGifsEnabled(context, true)
        var gifRequests = 0
        controller.onEmojiLayerGifKey = { gifRequests++ }
        controller.toggleEmojiKeyPage(layer = true)

        val result = controller.handleKeyWhenActive(
            KeyEvent.KEYCODE_P,
            KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_P, 0),
            null,
            ctrlLatchActive = false,
            altLatchActive = false,
            updateStatusBar = {}
        )

        assertEquals(SymLayoutController.SymKeyResult.CONSUME, result)
        assertEquals(1, gifRequests)
    }

    @Test
    fun recentsAndGifKeysNeverShareALetter() {
        SettingsManager.setGifsEnabled(context, true)
        // Defaults: Recents Q, GIF P
        assertFalse(SettingsManager.setEmojiLayerRecentsKey(context, KeyEvent.KEYCODE_P))
        assertFalse(SettingsManager.setEmojiLayerGifKey(context, KeyEvent.KEYCODE_A))
        assertTrue(SettingsManager.setEmojiLayerGifKey(context, KeyEvent.KEYCODE_L))
        assertEquals(KeyEvent.KEYCODE_L, SettingsManager.getEmojiLayerGifKey(context))
    }

    @Test
    fun aLetterOnTheEmojiLayerStartsASearchWhenTypeToSearchIsOn() {
        SettingsManager.setEmojiLayerTypeToSearch(context, true)
        val searches = mutableListOf<Pair<Boolean, String>>()
        controller.onTypeToSearch = { emoji, text -> searches += emoji to text }
        controller.toggleEmojiKeyPage(layer = true)

        val result = controller.handleKeyWhenActive(
            KeyEvent.KEYCODE_S,
            KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_S, 0),
            null,
            ctrlLatchActive = false,
            altLatchActive = false,
            updateStatusBar = {}
        )

        assertEquals(SymLayoutController.SymKeyResult.CONSUME, result)
        assertEquals(listOf(true to "s"), searches)
    }

    @Test
    fun lettersTypeTheirEmojiWhenTypeToSearchIsOff() {
        SettingsManager.setEmojiLayerTypeToSearch(context, false)
        val searches = mutableListOf<Pair<Boolean, String>>()
        controller.onTypeToSearch = { emoji, text -> searches += emoji to text }
        controller.toggleEmojiKeyPage(layer = true)

        controller.handleKeyWhenActive(
            KeyEvent.KEYCODE_S,
            KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_S, 0),
            null,
            ctrlLatchActive = false,
            altLatchActive = false,
            updateStatusBar = {}
        )

        assertTrue(searches.isEmpty())
    }

    @Test
    fun withRecentsShownTheGifKeyKeepsItsJob() {
        SettingsManager.setGifsEnabled(context, true)
        SettingsManager.setEmojiLayerRecentsKey(context, KeyEvent.KEYCODE_A)
        val recent = listOf("😀", "😂", "😍", "😎", "👍", "🙏", "🎉", "🔥", "❤", "😢")
        recent.forEach { RecentEmojiManager.addRecentEmoji(context, it) }
        var gifRequests = 0
        controller.onEmojiLayerGifKey = { gifRequests++ }
        controller.toggleEmojiKeyPage(layer = true)
        assertEquals(SymLayoutController.GIF_KEY_LABEL, controller.currentSymMappings()!![KeyEvent.KEYCODE_P])

        assertTrue(controller.toggleEmojiLayerRecents())

        // P stays GIF, the recents running round it
        assertEquals(SymLayoutController.GIF_KEY_LABEL, controller.currentSymMappings()!![KeyEvent.KEYCODE_P])
        controller.handleKeyWhenActive(
            KeyEvent.KEYCODE_P,
            KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_P, 0),
            null,
            ctrlLatchActive = false,
            altLatchActive = false,
            updateStatusBar = {}
        )
        assertEquals(1, gifRequests)
    }

    @Test
    fun searchKeyIsQAndShowsItsLabelOnTheLayer() {
        assertEquals(KeyEvent.KEYCODE_Q, SettingsManager.getSearchKey(context))
        controller.toggleEmojiKeyPage(layer = true)

        assertEquals(SymLayoutController.SEARCH_KEY_LABEL, controller.currentSymMappings()!![KeyEvent.KEYCODE_Q])
    }

    @Test
    fun pressingTheSearchKeyOnTheLayerAsksForEmojiSearch() {
        val targets = mutableListOf<SymLayoutController.SearchTarget>()
        controller.onSearchKey = { targets += it }
        controller.toggleEmojiKeyPage(layer = true)

        val result = controller.handleKeyWhenActive(
            KeyEvent.KEYCODE_Q,
            KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Q, 0),
            null,
            ctrlLatchActive = false,
            altLatchActive = false,
            updateStatusBar = {}
        )

        assertEquals(SymLayoutController.SymKeyResult.CONSUME, result)
        assertEquals(listOf(SymLayoutController.SearchTarget.EMOJI_LAYER), targets)
    }

    @Test
    fun searchKeyNeverSharesALetterWithRecentsOrGif() {
        SettingsManager.setGifsEnabled(context, true)
        // Defaults: Recents A, GIF P, search Q
        assertFalse(SettingsManager.setSearchKey(context, KeyEvent.KEYCODE_A))
        assertFalse(SettingsManager.setSearchKey(context, KeyEvent.KEYCODE_P))
        assertFalse(SettingsManager.setEmojiLayerRecentsKey(context, KeyEvent.KEYCODE_Q))
        assertFalse(SettingsManager.setEmojiLayerGifKey(context, KeyEvent.KEYCODE_Q))
        assertTrue(SettingsManager.setSearchKey(context, KeyEvent.KEYCODE_S))
        assertEquals(KeyEvent.KEYCODE_S, SettingsManager.getSearchKey(context))
    }
}
