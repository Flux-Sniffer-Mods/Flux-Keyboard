package it.palsoftware.pastiera.data.mappings

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EmojiLayerAroundButtonsTest {
    @Test
    fun emojiMoveOffButtonKeysAndTheLastDropOff() {
        val profile = EmojiLayerProfiles.BUILT_IN.first { it.id == "chatting" }
        val buttons = setOf(KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_A)
        val layer = EmojiLayerProfiles.aroundButtons(profile.mappings, buttons)

        buttons.forEach { assertFalse(layer.containsKey(it)) }
        // The most used emoji keeps first place, on the first free key
        assertEquals(profile.mappings[KeyEvent.KEYCODE_Q], layer[KeyEvent.KEYCODE_W])
        assertEquals(EmojiLayerProfiles.KEYS.size - buttons.size, layer.size)
    }

    @Test
    fun noButtonsLeavesTheLayerAsIs() {
        val profile = EmojiLayerProfiles.BUILT_IN.first()
        assertEquals(profile.mappings, EmojiLayerProfiles.aroundButtons(profile.mappings, emptySet()))
    }
}
