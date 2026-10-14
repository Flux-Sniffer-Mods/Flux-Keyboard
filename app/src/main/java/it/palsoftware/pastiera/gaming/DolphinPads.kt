package it.palsoftware.pastiera.gaming

import it.palsoftware.pastiera.gaming.FluxLayout.Layer
import it.palsoftware.pastiera.gaming.FluxLayout.WiiPad

/**
 * Dolphin's controllers as one game can have its own keys for them: each thing on a controller a
 * key can press, where it sits on the controller (for drawing it), and Dolphin's names for it.
 * A game's changed keys are written as a Dolphin profile of its own, loaded as the game starts.
 */
object DolphinPads {
    /**
     * One button, stick direction or movement: [label] drawn at [x], [y] (shares of the drawing,
     * as the controller is held), written as Dolphin's [keys] (a shake moves all three ways).
     */
    data class Input(val id: String, val label: String, val x: Float, val y: Float, val keys: List<String> = listOf(id))

    /** A key and the modifier held with it. */
    data class Binding(val layer: Layer, val key: Int)

    /** How wide each controller is drawn, for its height. */
    fun aspect(pad: WiiPad): Float = when (pad) {
        WiiPad.GAMECUBE, WiiPad.CLASSIC -> 1.6f
        WiiPad.SIDEWAYS -> 2.2f
        WiiPad.NUNCHUK -> 1.1f
    }

    /** A stick's (or d-pad's) four ways around ([x], [y]), [reach] across, as far up and down (the drawing is [aspect] wide). */
    private fun stick(prefix: String, name: String, x: Float, y: Float, reach: Float, aspect: Float) = listOf(
        Input("$prefix/Up", "$name ↑", x, y - reach * aspect),
        Input("$prefix/Down", "$name ↓", x, y + reach * aspect),
        Input("$prefix/Left", "$name ←", x - reach, y),
        Input("$prefix/Right", "$name →", x + reach, y)
    )

    /** The Wii Remote's movements, in a row (a shake moves it all three ways at once). */
    private fun motions(y: Float, from: Float, to: Float): List<Input> {
        val list = listOf(
            Input("Shake", "Shake", 0f, y, listOf("Shake/X", "Shake/Y", "Shake/Z")),
            Input("Tilt/Forward", "Tilt ↑", 0f, y), Input("Tilt/Backward", "Tilt ↓", 0f, y),
            Input("Tilt/Left", "Tilt ←", 0f, y), Input("Tilt/Right", "Tilt →", 0f, y),
            Input("Swing/Up", "Swing ↑", 0f, y), Input("Swing/Down", "Swing ↓", 0f, y)
        )
        return list.mapIndexed { i, input -> input.copy(x = from + (to - from) * i / (list.size - 1)) }
    }

    fun inputs(pad: WiiPad): List<Input> {
        val aspect = aspect(pad)
        return when (pad) {
            WiiPad.GAMECUBE -> listOf(
                Input("Triggers/L", "L", 0.16f, 0.06f, listOf("Triggers/L", "Triggers/L-Analog")),
                Input("Triggers/R", "R", 0.84f, 0.06f, listOf("Triggers/R", "Triggers/R-Analog")),
                Input("Buttons/Z", "Z", 0.93f, 0.21f),
                Input("Buttons/Start", "Start", 0.5f, 0.4f),
                Input("Buttons/A", "A", 0.78f, 0.45f), Input("Buttons/B", "B", 0.64f, 0.5f),
                Input("Buttons/X", "X", 0.91f, 0.36f), Input("Buttons/Y", "Y", 0.74f, 0.27f)
            ) + stick("Main Stick", "Stick", 0.2f, 0.42f, 0.09f, aspect) + stick("D-Pad", "D-pad", 0.36f, 0.8f, 0.09f, aspect) +
                stick("C-Stick", "C", 0.68f, 0.82f, 0.09f, aspect)
            WiiPad.CLASSIC -> listOf(
                Input("Classic/Triggers/L", "L", 0.12f, 0.06f), Input("Classic/Buttons/ZL", "ZL", 0.28f, 0.1f),
                Input("Classic/Triggers/R", "R", 0.88f, 0.06f), Input("Classic/Buttons/ZR", "ZR", 0.72f, 0.1f),
                Input("Classic/Buttons/-", "−", 0.38f, 0.42f), Input("Classic/Buttons/Home", "Home", 0.5f, 0.42f),
                Input("Classic/Buttons/+", "+", 0.62f, 0.42f),
                Input("Classic/Buttons/A", "a", 0.94f, 0.42f), Input("Classic/Buttons/B", "b", 0.84f, 0.57f),
                Input("Classic/Buttons/X", "x", 0.84f, 0.27f), Input("Classic/Buttons/Y", "y", 0.74f, 0.42f)
            ) + stick("Classic/D-Pad", "D-pad", 0.17f, 0.42f, 0.09f, aspect) +
                stick("Classic/Left Stick", "L stick", 0.34f, 0.8f, 0.09f, aspect) +
                stick("Classic/Right Stick", "R stick", 0.66f, 0.8f, 0.09f, aspect)
            // The Nunchuk on the left, the Wii Remote upright on the right, its movements below
            WiiPad.NUNCHUK -> stick("Nunchuk/Stick", "Stick", 0.22f, 0.24f, 0.1f, aspect) + listOf(
                Input("Nunchuk/Buttons/C", "C", 0.22f, 0.48f), Input("Nunchuk/Buttons/Z", "Z", 0.22f, 0.6f),
                Input("Nunchuk/Shake", "Shake", 0.22f, 0.72f, listOf("Nunchuk/Shake/X", "Nunchuk/Shake/Y", "Nunchuk/Shake/Z")),
                Input("Buttons/A", "A", 0.66f, 0.35f), Input("Buttons/B", "B", 0.88f, 0.3f),
                Input("Buttons/-", "−", 0.53f, 0.48f), Input("Buttons/Home", "Home", 0.66f, 0.48f), Input("Buttons/+", "+", 0.79f, 0.48f),
                Input("Buttons/1", "1", 0.66f, 0.61f), Input("Buttons/2", "2", 0.66f, 0.72f)
            ) + stick("D-Pad", "D-pad", 0.66f, 0.14f, 0.09f, aspect) + motions(y = 0.89f, from = 0.07f, to = 0.93f)
            // Held sideways: the d-pad under the left thumb, 1 and 2 under the right
            WiiPad.SIDEWAYS -> stick("D-Pad", "D-pad", 0.13f, 0.42f, 0.09f, aspect) + listOf(
                Input("Buttons/A", "A", 0.35f, 0.42f), Input("Buttons/B", "B", 0.35f, 0.68f),
                Input("Buttons/-", "−", 0.47f, 0.42f), Input("Buttons/Home", "Home", 0.59f, 0.42f), Input("Buttons/+", "+", 0.71f, 0.42f),
                Input("Buttons/1", "1", 0.83f, 0.42f), Input("Buttons/2", "2", 0.95f, 0.42f)
            ) + motions(y = 0.9f, from = 0.07f, to = 0.93f)
        }
    }

    /** The Dolphin names [inputs] writes, for one input's [id]. */
    fun keysOf(pad: WiiPad, id: String): List<String> = inputs(pad).firstOrNull { it.id == id }?.keys ?: listOf(id)

    /** "id<TAB>LAYER:code,LAYER:code" lines: an input's keys (none after the tab: nothing presses it). */
    fun format(map: Map<String, List<Binding>>): String =
        map.entries.joinToString("\n") { (id, keys) -> id + "\t" + keys.joinToString(",") { "${it.layer}:${it.key}" } }

    fun parse(text: String?): Map<String, List<Binding>> = text.orEmpty().lines().filter { '\t' in it }.associate { line ->
        line.substringBefore('\t') to line.substringAfter('\t').split(',').mapNotNull { part ->
            val layer = runCatching { Layer.valueOf(part.substringBefore(':')) }.getOrNull() ?: return@mapNotNull null
            part.substringAfter(':').toIntOrNull()?.let { Binding(layer, it) }
        }
    }
}
