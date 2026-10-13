package it.palsoftware.pastiera.gaming

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyGridTest {
    @Test
    fun lettersHaveTheirRowAndColumn() {
        assertEquals(8f to 0, GameMode.keyGridPlace(KeyEvent.KEYCODE_O))
        assertEquals(8f to 1, GameMode.keyGridPlace(KeyEvent.KEYCODE_L))
        assertEquals(7f to 2, GameMode.keyGridPlace(KeyEvent.KEYCODE_M))
    }

    @Test
    fun otherKeysHaveNone() {
        assertNull(GameMode.keyGridPlace(KeyEvent.KEYCODE_SPACE))
        assertNull(GameMode.keyGridPlace(KeyEvent.KEYCODE_VOLUME_UP))
    }
}
