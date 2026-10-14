package it.palsoftware.pastiera.gaming

import android.view.KeyEvent
import it.palsoftware.pastiera.gaming.FluxLayout.Control
import it.palsoftware.pastiera.gaming.FluxLayout.Layer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadDrawingTest {
    private val base: (Int, Layer) -> Control? = { code, layer -> FluxLayout.defaultControlFor(code, FluxLayout.modifiersOf(layer)) }
    private val keys = (KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z).toList() + KeyEvent.KEYCODE_SPACE

    @Test
    fun aButtonGivenAKeyIsPressedByItAlone() {
        val changed = GamepadDrawing.assign(emptyMap(), base, keys, Control.A, Layer.NONE, KeyEvent.KEYCODE_R)
        assertEquals(listOf(Layer.NONE to KeyEvent.KEYCODE_R), GamepadDrawing.keysFor(changed, base, keys, Control.A))
        // M (A by default) now presses nothing
        assertEquals(null, GamepadDrawing.effective(changed, base, Layer.NONE, KeyEvent.KEYCODE_M))
        // Back to how it was
        assertEquals(emptyMap<Any, Any>(), GamepadDrawing.restore(changed, base, Control.A))
    }

    @Test
    fun buttonsDontOverlapOnAPhone() {
        val width = 336f
        val height = width / GamepadDrawing.ASPECT
        val spots = GamepadDrawing.SPOTS
        assertEquals(spots.size, spots.map { it.control }.toSet().size)
        for (i in spots.indices) for (j in i + 1 until spots.size) {
            val a = spots[i]; val b = spots[j]
            val apart = kotlin.math.abs(a.x - b.x) * width >= 40f || kotlin.math.abs(a.y - b.y) * height >= 30f
            assertTrue("${a.label} and ${b.label} overlap", apart)
        }
    }
}
