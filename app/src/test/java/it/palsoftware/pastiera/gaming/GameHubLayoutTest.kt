package it.palsoftware.pastiera.gaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameHubLayoutTest {
    @Test
    fun aGameHubProfilePressesItsButtonsWithoutPlacing() {
        val profile = GameProfiles.newProfile("A Hat in Time", GameStyle.GAMEPAD, setOf("gamehub.lite"))
        val laid = GameTouchEditor.withDefaults(profile)
        val a = profile.keys.entries.first { it.value == GameAction.BUTTON_A }.key
        assertEquals(0.873f to 0.831f, laid.taps[a])
        assertTrue(laid.stickZones.containsKey(0))
    }

    @Test
    fun placedSpotsAndOtherAppsAreLeftAlone() {
        val other = GameProfiles.newProfile("Game", GameStyle.GAMEPAD, setOf("org.ppsspp.ppsspp"))
        assertTrue(GameTouchEditor.withDefaults(other).taps.isEmpty())
        val placed = GameProfiles.newProfile("Game", GameStyle.GAMEPAD, setOf("gamehub.lite")).copy(taps = mapOf(1 to (0.5f to 0.5f)))
        assertEquals(placed, GameTouchEditor.withDefaults(placed))
    }
}
