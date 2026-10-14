package it.palsoftware.pastiera.gaming

import android.content.Context
import it.palsoftware.pastiera.adb.AdbShell

/**
 * A DS or 3DS game's two screens: which shows, which is bigger, which comes first. Written into
 * the emulator's own settings where they're in a file Flux Keyboard can reach (Azahar and the
 * Citra builds: config/config.ini, read as each game starts; NooDS: noods.ini, read as it opens).
 * melonDS, DraStic, Panda3DS and Cemu keep theirs in their private storage: set there.
 */
object DualScreens {
    /** A layout, and whether it suits a landscape screen (else portrait, one above the other). */
    enum class Layout(val landscape: Boolean) {
        STACKED(false), STACKED_BOTTOM_FIRST(false),
        TOP_LARGE(true), BOTTOM_LARGE(true), SIDE_BY_SIDE(true),
        TOP_ONLY(true), BOTTOM_ONLY(true);

        /** The screen choice that suits it: Landscape or Portrait. */
        val screen: ScreenMode get() = if (landscape) ScreenMode.SIDEWAYS else ScreenMode.UPRIGHT
    }

    private val CITRA = setOf("Azahar", "Mandarine")

    /** The layouts Flux Keyboard can set for [pkg]'s emulator (empty: none, set in the emulator). */
    fun layouts(pkg: String): List<Layout> = when (GameApps.emulator(pkg)) {
        in CITRA -> Layout.entries
        // NooDS: no swap in its file, so no bottom screen first or alone
        "NooDS" -> listOf(Layout.STACKED, Layout.TOP_LARGE, Layout.BOTTOM_LARGE, Layout.SIDE_BY_SIDE, Layout.TOP_ONLY)
        else -> emptyList()
    }

    /** NooDS reads its file only as it opens; Azahar as each game starts. */
    fun appliesAtNextStart(pkg: String): Boolean = GameApps.emulator(pkg) == "NooDS"

    fun layout(name: String?): Layout? = Layout.entries.firstOrNull { it.name == name }

    /**
     * [layout] written into [pkg]'s settings for a screen [window] pixels across and down.
     * Blocking: through the shell. False when its settings file wasn't found.
     */
    fun apply(pkg: String, layout: Layout, window: Pair<Int, Int>): Boolean = when (GameApps.emulator(pkg)) {
        in CITRA -> citraFiles(pkg).map { path ->
            val ini = FluxLayout.read(path)
            val changed = citraValues(layout, window).entries.fold(ini) { text, (key, value) -> FluxLayout.withKey(text, "Layout", key, value) }
            FluxLayout.writeIfChanged(path, changed)
        }.let { it.isNotEmpty() && it.all { ok -> ok } }
        "NooDS" -> {
            val path = "/storage/emulated/0/Android/data/$pkg/files/noods.ini"
            val ini = FluxLayout.read(path)
            ini.isNotEmpty() && FluxLayout.writeIfChanged(path, noodsValues(layout).entries.fold(ini) { text, (key, value) -> withNoodsKey(text, key, value) })
        }
        else -> false
    }

    /**
     * The window a game gets on this phone: landscape (Flux Keyboard's landscape-shaped size,
     * at the game's resolution) or portrait (the phone's own, below the camera). Blocking.
     */
    fun window(context: Context, landscape: Boolean, height: Int?): Pair<Int, Int>? {
        val physical = Regex("Physical size: (\\d+)x(\\d+)").find(AdbShell.run("wm size", 3_000).orEmpty())
            ?.destructured?.toList()?.map { it.toInt() } ?: return null
        val (w, h) = minOf(physical[0], physical[1]) to maxOf(physical[0], physical[1])
        val cutout = GameMode.cutoutTop(context)
        return if (landscape) GameMode.scaled(GameMode.sidewaysSize(w, h, cutout), height) else w to (h - cutout)
    }

    // ---- Azahar and the Citra builds: config.ini's [Layout] ----

    /** Their config.ini files: in the folder picked for them (on the phone's storage) or their own. */
    private fun citraFiles(pkg: String): List<String> = AdbShell.run(
        "for f in /storage/emulated/0/*/config/config.ini \"/storage/emulated/0/Android/data/$pkg/files/config/config.ini\"; do " +
            "[ -f \"\$f\" ] && grep -q '^portrait_layout_option' \"\$f\" && echo \"\$f\"; done; true", 5_000
    )?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    // Their layout options (Settings::LayoutOption, PortraitLayoutOption)
    private const val SINGLE = "1"
    private const val LARGE = "2"
    private const val SIDE = "3"
    private const val CUSTOM = "5"
    private const val PORTRAIT_CUSTOM = "1"

    /** A 3DS's screens: the top 400 x 240, the bottom 320 x 240. */
    private const val TOP_W = 400
    private const val BOTTOM_W = 320
    private const val SCREEN_H = 240

    /** Room kept round the edges, so a slightly smaller window than worked out still holds them. */
    private const val FIT = 0.97

    internal fun citraValues(layout: Layout, window: Pair<Int, Int>): Map<String, String> {
        val (w, h) = window
        return when (layout) {
            // One above the other, the screen that comes first at the top; both at one scale
            Layout.STACKED, Layout.STACKED_BOTTOM_FIRST -> {
                val k = minOf(w.toDouble() / TOP_W, h.toDouble() / (2 * SCREEN_H)) * FIT
                val sh = (SCREEN_H * k).toInt()
                val first = layout == Layout.STACKED
                val top = Rect(((w - TOP_W * k) / 2).toInt(), if (first) 0 else sh, (TOP_W * k).toInt(), sh)
                val bottom = Rect(((w - BOTTOM_W * k) / 2).toInt(), if (first) sh else 0, (BOTTOM_W * k).toInt(), sh)
                custom("custom_portrait_", top, bottom) +
                    mapOf("portrait_layout_option" to PORTRAIT_CUSTOM)
            }
            Layout.TOP_LARGE -> mapOf("layout_option" to LARGE, "small_screen_position" to "1")
            // The bottom screen big, the top one beside it a third its size
            Layout.BOTTOM_LARGE -> {
                val small = 1.0 / 3
                val k = minOf(w.toDouble() / (BOTTOM_W + TOP_W * small), h.toDouble() / SCREEN_H) * FIT
                val bottomW = (BOTTOM_W * k).toInt()
                val x = ((w - (BOTTOM_W + TOP_W * small) * k) / 2).toInt()
                val y = ((h - SCREEN_H * k) / 2).toInt()
                custom("custom_", top = Rect(x + bottomW, y + ((SCREEN_H * k * (1 - small)) / 2).toInt(), (TOP_W * k * small).toInt(), (SCREEN_H * k * small).toInt()),
                    bottom = Rect(x, y, bottomW, (SCREEN_H * k).toInt())) + mapOf("layout_option" to CUSTOM)
            }
            Layout.SIDE_BY_SIDE -> mapOf("layout_option" to SIDE)
            Layout.TOP_ONLY -> mapOf("layout_option" to SINGLE)
            // The bottom screen alone: the top one a single pixel in the corner
            Layout.BOTTOM_ONLY -> {
                val k = minOf(w.toDouble() / BOTTOM_W, h.toDouble() / SCREEN_H) * FIT
                custom("custom_", top = Rect(0, 0, 1, 1),
                    bottom = Rect(((w - BOTTOM_W * k) / 2).toInt(), ((h - SCREEN_H * k) / 2).toInt(), (BOTTOM_W * k).toInt(), (SCREEN_H * k).toInt())) +
                    mapOf("layout_option" to CUSTOM)
            }
        }
    }

    /**
     * Where the bottom screen is (left, top, right, bottom, shares of a [w] x [h] screen) when
     * Flux Keyboard set [profile]'s layout and knows where it puts it; null otherwise.
     */
    fun bottomArea(profile: GameProfile?, w: Float, h: Float): List<Float>? {
        val layout = layout(profile?.screens) ?: return null
        if (w <= 0f || h <= 0f || profile?.packages?.none { GameApps.emulator(it) in CITRA } != false) return null
        val rect = when (layout) {
            Layout.SIDE_BY_SIDE -> {
                val k = minOf(w / (TOP_W + BOTTOM_W), h / SCREEN_H)
                Rect(((w - (TOP_W + BOTTOM_W) * k) / 2 + TOP_W * k).toInt(), ((h - SCREEN_H * k) / 2).toInt(), (BOTTOM_W * k).toInt(), (SCREEN_H * k).toInt())
            }
            Layout.TOP_LARGE, Layout.TOP_ONLY -> return null
            else -> {
                val v = citraValues(layout, w.toInt() to h.toInt())
                val prefix = if (layout.landscape) "custom_" else "custom_portrait_"
                fun get(key: String) = v["$prefix$key"]?.toIntOrNull() ?: 0
                Rect(get("bottom_x"), get("bottom_y"), get("bottom_width"), get("bottom_height"))
            }
        }
        return listOf(rect.x / w, rect.y / h, (rect.x + rect.w) / w, (rect.y + rect.h) / h)
    }

    internal data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

    private fun custom(prefix: String, top: Rect, bottom: Rect): Map<String, String> = mapOf(
        "${prefix}top_x" to top.x, "${prefix}top_y" to top.y, "${prefix}top_width" to top.w, "${prefix}top_height" to top.h,
        "${prefix}bottom_x" to bottom.x, "${prefix}bottom_y" to bottom.y, "${prefix}bottom_width" to bottom.w, "${prefix}bottom_height" to bottom.h
    ).mapValues { it.value.coerceAtLeast(0).toString() }

    // ---- NooDS: noods.ini, name=value lines ----

    internal fun noodsValues(layout: Layout): Map<String, String> {
        // screenArrangement: 0 automatic, 1 vertical, 2 horizontal, 3 single; screenSizing: 0 even, 1 top bigger, 2 bottom bigger
        val (arrangement, sizing) = when (layout) {
            Layout.STACKED, Layout.STACKED_BOTTOM_FIRST -> 1 to 0
            Layout.TOP_LARGE -> 2 to 1
            Layout.BOTTOM_LARGE -> 2 to 2
            Layout.SIDE_BY_SIDE -> 2 to 0
            Layout.TOP_ONLY, Layout.BOTTOM_ONLY -> 3 to 0
        }
        return mapOf("screenArrangement" to "$arrangement", "screenSizing" to "$sizing", "screenRotation" to "0")
    }

    internal fun withNoodsKey(ini: String, key: String, value: String): String {
        val line = Regex("(?m)^" + Regex.escape(key) + "=.*$")
        return if (line.containsMatchIn(ini)) line.replace(ini, "$key=$value")
        else ini.trimEnd('\n') + "\n$key=$value\n"
    }
}
