package it.palsoftware.pastiera.gaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameHubLayoutTest {
    @Test
    fun aGameHubProfilesSticksAreItsOnScreenSticks() {
        val laid = GameTouchEditor.withDefaults(GameProfiles.newProfile("A Hat in Time", setOf("gamehub.lite")))
        assertEquals(0.146f, laid.stickZones.getValue(0).first)
        assertEquals(0.683f, laid.stickZones.getValue(1).first)
    }

    @Test
    fun aGameNativeProfilesSticksAreItsBuiltInControllers() {
        val laid = GameTouchEditor.withDefaults(GameProfiles.newProfile("Stray", setOf("app.gamenative")))
        assertEquals(0.216f, laid.stickZones.getValue(0).first)
    }

    @Test
    fun placedSticksAndEmulatorsAreLeftAlone() {
        val emulator = GameProfiles.newProfile("Game", setOf("org.ppsspp.ppsspp"))
        assertTrue(GameTouchEditor.withDefaults(emulator).stickZones.isEmpty())
        val placed = GameProfiles.newProfile("Game", setOf("gamehub.lite")).copy(stickZones = mapOf(0 to Triple(0.5f, 0.5f, 0.1f)))
        assertEquals(placed, GameTouchEditor.withDefaults(placed))
    }
}
