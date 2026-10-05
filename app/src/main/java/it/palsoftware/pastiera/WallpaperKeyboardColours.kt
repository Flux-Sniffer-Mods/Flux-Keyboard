package it.palsoftware.pastiera

import android.content.Context
import android.os.Build

/**
 * Keyboard colours from the wallpaper (Material You): the chosen theme keeps its shapes and
 * sizes, and takes its colours from Android's wallpaper palette, light or dark with the system.
 * Android 12 and later; elsewhere the theme is unchanged.
 */
internal object WallpaperKeyboardColours {

    fun recolour(context: Context, theme: SettingsManager.KeyboardThemeSettings): SettingsManager.KeyboardThemeSettings {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return theme
        return runCatching { recolour(theme, palette(context), SettingsManager.isSystemDarkTheme(context)) }
            .getOrDefault(theme)
    }

    /** Android's tonal palettes: tone (10…1000) to colour, for each of the five palettes. */
    internal class Palette(
        val neutral: (Int) -> Int,
        val neutralVariant: (Int) -> Int,
        val primary: (Int) -> Int,
        val secondary: (Int) -> Int,
        val tertiary: (Int) -> Int
    )

    internal fun recolour(theme: SettingsManager.KeyboardThemeSettings, p: Palette, dark: Boolean): SettingsManager.KeyboardThemeSettings {
        // A see-through theme stays see-through: only the colour of its background changes
        fun keepAlpha(original: Int, colour: Int) = (original and 0xFF000000.toInt()) or (colour and 0x00FFFFFF)
        return if (dark) {
            val accent = p.primary(200)
            theme.copy(
                background = keepAlpha(theme.background, p.neutral(900)),
                divider = p.neutralVariant(700),
                normalKey = p.neutral(800),
                specialKey = p.secondary(700),
                textAndIcons = p.neutral(50),
                ledInactive = p.neutralVariant(600),
                ledActive = accent,
                ledLocked = p.tertiary(200),
                accent = accent,
                cursorSwipe = accent,
                keyPopup = p.secondary(700),
                keyPopupSelected = accent,
                suggestion = p.neutral(800),
                statusBarButton = p.secondary(700)
            )
        } else {
            val accent = p.primary(600)
            theme.copy(
                background = keepAlpha(theme.background, p.neutral(50)),
                divider = p.neutralVariant(300),
                normalKey = p.neutral(10),
                specialKey = p.secondary(100),
                textAndIcons = p.neutral(900),
                ledInactive = p.neutralVariant(300),
                ledActive = accent,
                ledLocked = p.tertiary(600),
                accent = accent,
                cursorSwipe = accent,
                keyPopup = p.secondary(100),
                keyPopupSelected = accent,
                suggestion = p.neutral(10),
                statusBarButton = p.secondary(100)
            )
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun palette(context: Context): Palette {
        val tones = listOf(10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900)
        fun palette(ids: IntArray): (Int) -> Int = { tone -> context.getColor(ids[tones.indexOf(tone).coerceAtLeast(0)]) }
        return Palette(
            neutral = palette(intArrayOf(
                android.R.color.system_neutral1_10, android.R.color.system_neutral1_50, android.R.color.system_neutral1_100,
                android.R.color.system_neutral1_200, android.R.color.system_neutral1_300, android.R.color.system_neutral1_400,
                android.R.color.system_neutral1_500, android.R.color.system_neutral1_600, android.R.color.system_neutral1_700,
                android.R.color.system_neutral1_800, android.R.color.system_neutral1_900)),
            neutralVariant = palette(intArrayOf(
                android.R.color.system_neutral2_10, android.R.color.system_neutral2_50, android.R.color.system_neutral2_100,
                android.R.color.system_neutral2_200, android.R.color.system_neutral2_300, android.R.color.system_neutral2_400,
                android.R.color.system_neutral2_500, android.R.color.system_neutral2_600, android.R.color.system_neutral2_700,
                android.R.color.system_neutral2_800, android.R.color.system_neutral2_900)),
            primary = palette(intArrayOf(
                android.R.color.system_accent1_10, android.R.color.system_accent1_50, android.R.color.system_accent1_100,
                android.R.color.system_accent1_200, android.R.color.system_accent1_300, android.R.color.system_accent1_400,
                android.R.color.system_accent1_500, android.R.color.system_accent1_600, android.R.color.system_accent1_700,
                android.R.color.system_accent1_800, android.R.color.system_accent1_900)),
            secondary = palette(intArrayOf(
                android.R.color.system_accent2_10, android.R.color.system_accent2_50, android.R.color.system_accent2_100,
                android.R.color.system_accent2_200, android.R.color.system_accent2_300, android.R.color.system_accent2_400,
                android.R.color.system_accent2_500, android.R.color.system_accent2_600, android.R.color.system_accent2_700,
                android.R.color.system_accent2_800, android.R.color.system_accent2_900)),
            tertiary = palette(intArrayOf(
                android.R.color.system_accent3_10, android.R.color.system_accent3_50, android.R.color.system_accent3_100,
                android.R.color.system_accent3_200, android.R.color.system_accent3_300, android.R.color.system_accent3_400,
                android.R.color.system_accent3_500, android.R.color.system_accent3_600, android.R.color.system_accent3_700,
                android.R.color.system_accent3_800, android.R.color.system_accent3_900))
        )
    }
}
