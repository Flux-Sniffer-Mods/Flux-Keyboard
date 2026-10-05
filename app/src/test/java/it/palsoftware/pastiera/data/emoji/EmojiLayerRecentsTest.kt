package it.palsoftware.pastiera.data.emoji

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiLayerRecentsTest {
    @Test
    fun recentsGoFirstAndTheLayersOwnMoveAlongWithoutRepeats() {
        val keys = listOf(1, 2, 3, 4)
        val mapped = mapOf(1 to "a", 2 to "b", 3 to "c", 4 to "d")
        // "c" is on the layer and was used: it moves to the front, not shown twice
        val arranged = EmojiLayerRecents.arrange(keys, mapped, recent = listOf("x", "c", "y"), count = 2)
        assertEquals(mapOf(1 to "x", 2 to "c", 3 to "a", 4 to "b"), arranged)
    }
}
