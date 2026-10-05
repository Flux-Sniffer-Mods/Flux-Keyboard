package it.palsoftware.pastiera.inputmethod.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifierLedLayoutTest {
    @Test
    fun titan2EliteMirrorsCoupledShiftAtBothOuterBottomPositions() {
        val shifts = ModifierLedLayouts.TITAN_2_ELITE.segments
            .filter { it.state == ModifierLedState.SHIFT }

        assertEquals(2, shifts.size)
        assertTrue(shifts.all { it.y > 0.5f })
        assertTrue(shifts.minOf { it.x } < 0.02f)
        assertTrue(shifts.maxOf { it.x + it.width } > 0.98f)
    }

    @Test
    fun titan2ElitePlacesAltAndSymAboveShiftAndCtrlAtFnPosition() {
        val segments = ModifierLedLayouts.TITAN_2_ELITE.segments
        val alt = segments.single { it.state == ModifierLedState.ALT }
        val sym = segments.single { it.state == ModifierLedState.SYM }
        val ctrl = segments.single { it.state == ModifierLedState.CTRL }
        val rightShift = segments.filter { it.state == ModifierLedState.SHIFT }.maxBy { it.x }

        assertTrue(alt.x < 0.02f)
        assertTrue(alt.y < 0.5f)
        assertTrue(sym.x > 0.7f)
        assertTrue(sym.y < 0.5f)
        assertTrue(ctrl.y > 0.5f)
        assertTrue(ctrl.x < rightShift.x)
        assertTrue(ctrl.x + ctrl.width <= rightShift.x)
    }

    @Test
    fun explicitPhysicalProfileControlsPresetAndAutoUsesDetection() {
        assertSame(
            ModifierLedLayouts.TITAN_2_ELITE_SPLIT,
            ModifierLedLayouts.resolve("titan2elite_qwerty", titan2EliteAutoDetected = false)
        )
        assertSame(
            ModifierLedLayouts.TITAN_2_ELITE_SPLIT,
            ModifierLedLayouts.resolve("auto", titan2EliteAutoDetected = true)
        )
        assertSame(
            ModifierLedLayouts.DEFAULT,
            ModifierLedLayouts.resolve("titan2", titan2EliteAutoDetected = true)
        )
    }

    @Test
    fun liftedStatusBarUsesOneLedPerModifierInPhysicalOrder() {
        val segments = ModifierLedLayouts.TITAN_2_ELITE_SPLIT.segments.sortedBy { it.x }

        assertEquals(
            listOf(ModifierLedState.SHIFT, ModifierLedState.ALT, ModifierLedState.SYM, ModifierLedState.CTRL),
            segments.map { it.state }
        )
        segments.zipWithNext().forEach { (left, right) ->
            assertTrue("${left.state} overlaps ${right.state}", left.x + left.width < right.x)
        }
    }

    @Test
    fun splitLayoutKeepsShiftAwayFromTheSymLed() {
        val segments = ModifierLedLayouts.TITAN_2_ELITE_SPLIT.segments
        val shift = segments.single { it.state == ModifierLedState.SHIFT }
        val sym = segments.single { it.state == ModifierLedState.SYM }

        assertTrue(shift.x + shift.width < 0.5f)
        assertTrue(sym.x > 0.5f)
    }

    @Test
    fun titan2EliteUsesTheFourLedLayoutOnEveryScreen() {
        assertSame(
            ModifierLedLayouts.TITAN_2_ELITE_SPLIT,
            ModifierLedLayouts.resolve("auto", titan2EliteAutoDetected = true)
        )
        assertSame(
            ModifierLedLayouts.DEFAULT,
            ModifierLedLayouts.resolve("titan2", titan2EliteAutoDetected = true)
        )
    }

    @Test
    fun emojiLedMakesFiveEqualLedsOnEveryLayout() {
        listOf(
            ModifierLedLayouts.resolve("titan2elite_qwerty", false, emojiLed = true),
            ModifierLedLayouts.resolve(null, false, emojiLed = true)
        ).forEach { layout ->
            assertEquals(5, layout.segments.size)
            assertEquals(1, layout.segments.count { it.state == ModifierLedState.EMOJI })
            val widths = layout.segments.map { it.width }.distinct()
            assertEquals(1, widths.size)
        }
        assertEquals(
            ModifierLedLayouts.TITAN_2_ELITE_SPLIT,
            ModifierLedLayouts.resolve("titan2elite_qwerty", false, emojiLed = false)
        )
    }
}
