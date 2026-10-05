package it.palsoftware.pastiera.adb

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display
import it.palsoftware.pastiera.R

/**
 * Screen size presets through Shizuku (wm density, as the ADB shell): Default at 300 dpi, and
 * Tablet and Desktop at the density that makes the screen's smallest width 601 and 860 dp (about 200 dpi on the Titan 2 Elite), as
 * Developer options' "Smallest width" would. Worked out from this phone's own screen.
 */
object ScreenDensity {
    enum class Preset(val action: String, val titleRes: Int, val smallestWidthDp: Int?) {
        DEFAULT("density_default", R.string.density_default_title, null),
        TABLET("density_tablet", R.string.density_tablet_title, 601),
        DESKTOP("density_desktop", R.string.density_desktop_title, 860);

        companion object {
            fun byAction(action: String): Preset? = entries.firstOrNull { it.action == action }
        }
    }

    private const val DEFAULT_DPI = 300

    /** The screen's shorter side in pixels (its real size, not the app's window). */
    private fun shortestSidePx(context: Context): Int? = runCatching {
        val display = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display.mode
        minOf(mode.physicalWidth, mode.physicalHeight).takeIf { it > 0 }
            ?: Point().also { @Suppress("DEPRECATION") display.getRealSize(it) }.let { minOf(it.x, it.y) }
    }.getOrNull()

    /**
     * The density a preset sets: 300 for Default; for the others, the highest density at which the
     * smallest width still reaches its dp (smallest width = shorter side in px × 160 / density).
     */
    fun dpiFor(context: Context, preset: Preset): Int? {
        val width = preset.smallestWidthDp ?: return DEFAULT_DPI
        val px = shortestSidePx(context) ?: return null
        return (px * 160 / width).coerceAtLeast(72)
    }

    /** Sets a preset's density; false when it couldn't be worked out or Shizuku isn't there. */
    fun apply(context: Context, preset: Preset): Boolean {
        val dpi = dpiFor(context, preset) ?: return false
        if (!AdbShell.available()) return false
        AdbShell.runAsync("wm density $dpi")
        // The phone's density from now on, for apps without one of their own
        PerAppDensity.setBase(context, preset.action)
        return true
    }

    /** Back to the phone's own density: the density alone (wm density reset), nothing else. */
    fun reset(context: Context): Boolean {
        if (!AdbShell.available()) return false
        AdbShell.runAsync("wm density reset")
        PerAppDensity.setBase(context, PerAppDensity.BASE_RESET)
        return true
    }
}
