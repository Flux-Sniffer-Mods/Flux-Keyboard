package it.palsoftware.pastiera.gaming

import it.palsoftware.pastiera.gaming.FluxLayout.Control
import it.palsoftware.pastiera.gaming.FluxLayout.Layer

/**
 * The layout seen from the controller's side: a gamepad drawn as it's held, each button where it
 * sits, showing the keys that press it. Picking a key for a button changes the same keys the
 * keyboard drawing does, the other way round.
 */
object GamepadDrawing {
    /** A button drawn at [x], [y] (shares of the drawing). */
    data class Spot(val control: Control, val label: String, val x: Float, val y: Float)

    /** How wide the controller is drawn, for its height. */
    const val ASPECT = 1.6f

    private fun stick(up: Control, down: Control, left: Control, right: Control, name: String, x: Float, y: Float, reach: Float) = listOf(
        Spot(up, "$name ↑", x, y - reach * ASPECT), Spot(down, "$name ↓", x, y + reach * ASPECT),
        Spot(left, "$name ←", x - reach, y), Spot(right, "$name →", x + reach, y)
    )

    val SPOTS: List<Spot> = listOf(
        Spot(Control.L2, "LT", 0.12f, 0.06f), Spot(Control.L1, "LB", 0.27f, 0.1f),
        Spot(Control.R2, "RT", 0.88f, 0.06f), Spot(Control.R1, "RB", 0.73f, 0.1f),
        Spot(Control.HOME, "Home", 0.5f, 0.12f),
        Spot(Control.SELECT, "Select", 0.44f, 0.3f), Spot(Control.START, "Start", 0.56f, 0.3f),
        Spot(Control.Y, "Y", 0.8f, 0.25f), Spot(Control.X, "X", 0.68f, 0.44f),
        Spot(Control.B, "B", 0.92f, 0.44f), Spot(Control.A, "A", 0.8f, 0.63f),
        Spot(Control.L3, "L3", 0.2f, 0.44f), Spot(Control.R3, "R3", 0.68f, 0.8f)
    ) + stick(Control.LS_UP, Control.LS_DOWN, Control.LS_LEFT, Control.LS_RIGHT, "L", 0.2f, 0.44f, 0.12f) +
        stick(Control.UP, Control.DOWN, Control.LEFT, Control.RIGHT, "D", 0.32f, 0.8f, 0.1f) +
        stick(Control.RS_UP, Control.RS_DOWN, Control.RS_LEFT, Control.RS_RIGHT, "R", 0.68f, 0.8f, 0.12f)

    /** What a key makes with [overrides] on top of [base]. */
    fun effective(overrides: Map<Pair<Layer, Int>, Control?>, base: (Int, Layer) -> Control?, layer: Layer, key: Int): Control? =
        (layer to key).let { if (it in overrides) overrides[it] else base(key, layer) }

    /** The keys (with their modifier) among [keys] that press [control]. */
    fun keysFor(overrides: Map<Pair<Layer, Int>, Control?>, base: (Int, Layer) -> Control?, keys: List<Int>, control: Control) =
        Layer.entries.flatMap { layer -> keys.filter { effective(overrides, base, layer, it) == control }.map { layer to it } }

    /**
     * [control] pressed by [key] with [layer]'s modifier alone: the keys that pressed it before
     * press nothing. Changes that come back to [base] are dropped.
     */
    fun assign(
        overrides: Map<Pair<Layer, Int>, Control?>, base: (Int, Layer) -> Control?, keys: List<Int>,
        control: Control, layer: Layer, key: Int
    ): Map<Pair<Layer, Int>, Control?> {
        val out = overrides.toMutableMap()
        keysFor(overrides, base, keys, control).filter { it != layer to key }.forEach { out[it] = null }
        out[layer to key] = control
        return out.filter { (k, v) -> v != base(k.second, k.first) }
    }

    /** [control] back on the keys [base] has for it, and no other. */
    fun restore(overrides: Map<Pair<Layer, Int>, Control?>, base: (Int, Layer) -> Control?, control: Control) =
        overrides.filter { (k, v) -> v != control && !(v == null && base(k.second, k.first) == control) }
}
