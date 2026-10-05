package it.palsoftware.pastiera.inputmethod.ui

import android.content.Context
import android.graphics.Color
import it.palsoftware.pastiera.SettingsManager

/**
 * Individual status LED colours (Settings > Look & sound > Status LED colours). Each LED has
 * its own colour: dim while its modifier is off, the colour itself while it's active, brighter
 * and more saturated while it's locked. Off: every LED uses the theme's three LED colours.
 */
object LedColors {
    enum class Led(val key: String, val defaultColor: Int) {
        // Left to right, the colours run as a rainbow: green, yellow, blue, purple, pink
        SHIFT("shift", Color.rgb(102, 204, 106)),
        ALT("alt", Color.rgb(255, 213, 79)),
        SYM("sym", Color.rgb(79, 164, 247)),
        CTRL("ctrl", Color.rgb(149, 76, 255)),
        EMOJI("emoji", Color.rgb(255, 105, 180))
    }

    enum class Level { OFF, ACTIVE, LOCKED }

    private const val ACTIVE_SATURATION = 0.8f
    private const val ACTIVE_MAX_VALUE = 0.68f

    fun enabled(context: Context): Boolean = SettingsManager.getLedIndividualColorsEnabled(context)

    fun lockedAnimationEnabled(context: Context): Boolean = SettingsManager.getLedLockedAnimationEnabled(context)

    /** A more intense version of [color] for the locked sweep: more saturated and brighter. */
    fun intensify(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return Color.HSVToColor(
            Color.alpha(color),
            floatArrayOf(hsv[0], (hsv[1] * 1.6f + 0.3f).coerceAtMost(1f), 1f)
        )
    }

    /** The low point of the locked sweep: the same colour, clearly darker. */
    fun deepen(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return Color.HSVToColor(Color.alpha(color), floatArrayOf(hsv[0], hsv[1], hsv[2] * 0.45f))
    }

    fun baseColor(context: Context, led: Led): Int = SettingsManager.getLedColor(context, led.key, led.defaultColor)

    /** The colour of [base] at [level]: dimmed when off, boosted when locked. */
    fun shade(base: Int, level: Level): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(base, hsv)
        return when (level) {
            Level.OFF -> Color.HSVToColor(110, floatArrayOf(hsv[0], hsv[1] * 0.45f, hsv[2] * 0.35f))
            // Active leaves room above it: bright colours (the light blue Shift) are calmed down, so
            // locking always shows a clear jump to full colour
            Level.ACTIVE -> Color.HSVToColor(floatArrayOf(hsv[0], hsv[1] * ACTIVE_SATURATION, hsv[2].coerceAtMost(ACTIVE_MAX_VALUE)))
            // Locked: the colour at full saturation and full brightness
            Level.LOCKED -> Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
        }
    }

    internal fun ledFor(state: ModifierLedState): Led = when (state) {
        ModifierLedState.SHIFT -> Led.SHIFT
        ModifierLedState.CTRL -> Led.CTRL
        ModifierLedState.ALT -> Led.ALT
        ModifierLedState.SYM -> Led.SYM
        ModifierLedState.EMOJI -> Led.EMOJI
    }
}
