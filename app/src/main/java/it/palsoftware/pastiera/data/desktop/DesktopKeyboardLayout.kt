package it.palsoftware.pastiera.data.desktop

import android.content.Context
import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.data.mappings.AltModifierMappingResolver
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import java.io.File

/**
 * The Linux desktop's keyboard layout: an X keyboard layout ("titan") built from Pastiera's own
 * Alt map and SYM symbols page, so the desktop's Alt and Sym layers match what's set here.
 *
 * It's written to Pastiera's folder on shared storage (no permission needed), where the Titan 2
 * Elite Debian chroot's boot script picks it up at the next desktop start. Shift, Ctrl, Alt and
 * Sym latch as in Pastiera: tap for the next key only, double-tap to lock, or hold.
 */
object DesktopKeyboardLayout {
    const val FILE_NAME = "titan_xkb_symbols"

    // The Titan 2 Elite's letter rows, by X key name prefix
    private val ROWS = listOf("AD" to "qwertyuiop", "AC" to "asdfghjkl", "AB" to "zxcvbnm")

    /** Where the layout is written, or null while shared storage isn't available. */
    fun file(context: Context): File? =
        context.getExternalFilesDir(null)?.let { File(it, FILE_NAME) }

    /**
     * Builds the layout from the current mappings and writes it when it changed. Returns the
     * file, or null if shared storage isn't available.
     */
    fun export(context: Context): File? {
        val target = file(context) ?: return null
        val assets = context.assets
        val text = build(
            alt = AltModifierMappingResolver.resolve(assets, context),
            symCustom = SettingsManager.getSymMappingsPage2(context),
            symDefault = KeyMappingLoader.loadSymKeyMappingsPage2(assets)
        )
        if (target.isFile && target.readText() == text) return target
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "$FILE_NAME.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.writeText(text)
            temp.delete()
        }
        return target
    }

    /**
     * The layout for these mappings (key code -> character). Sym+Shift gives the SYM page's
     * default where a key was changed, so the original stays reachable.
     */
    fun build(alt: Map<Int, String>, symCustom: Map<Int, String>, symDefault: Map<Int, String>): String =
        buildString {
            append(HEADER)
            ROWS.forEach { (row, letters) ->
                letters.forEachIndexed { index, letter ->
                    val keyCode = KeyEvent.KEYCODE_A + (letter - 'a')
                    val altValue = alt[keyCode]
                    val symValue = symCustom[keyCode] ?: symDefault[keyCode]
                    val symShiftValue = symDefault[keyCode]
                        ?.takeIf { symCustom[keyCode] != null && it != symCustom[keyCode] }
                        ?: symValue
                    val a = keysym(altValue)
                    val s = keysym(symValue)
                    val ss = keysym(symShiftValue)
                    append("    key <").append(row).append("%02d".format(index + 1)).append("> { ")
                    append("type[Group1] = \"EIGHT_LEVEL\", symbols[Group1] = [ ")
                    append(listOf(letter.toString(), letter.uppercase(), a, a, s, ss, a, a).joinToString(", "))
                    append(" ] };  // Alt: ").append(altValue ?: "-").append("  Sym: ").append(symValue ?: "-")
                    if (ss != s) append(" (Shift: ").append(symShiftValue).append(")")
                    append('\n')
                }
            }
            append(MODIFIERS)
        }

    /**
     * The X keysym for a mapping: letters and digits by name, anything else as Unicode (Uxxxx).
     * NoSymbol when empty or longer than one character (an X key carries one; emoji sequences).
     */
    fun keysym(value: String?): String {
        if (value.isNullOrEmpty()) return "NoSymbol"
        val codePoint = value.codePointAt(0)
        if (Character.charCount(codePoint) != value.length) return "NoSymbol"
        return when (codePoint) {
            in 'a'.code..'z'.code, in 'A'.code..'Z'.code, in '0'.code..'9'.code -> value
            else -> "U%04X".format(codePoint)
        }
    }

    private const val HEADER = """// titan_xkb_symbols - X keyboard layout for the Unihertz Titan 2 Elite, installed
// as the "titan" layout. Written by Flux Keyboard from its Alt map and symbols (Sym)
// page (Settings > Linux desktop). Shift, Ctrl, Alt and Sym behave as in Pastiera:
// tap for the next key only, double-tap to lock, hold for as long as it is held.
//   Level 1/2: letter / Shift+letter
//   Level 3/4: Alt+key        (Pastiera Alt map)
//   Level 5/6: Sym+key        (Pastiera SYM symbols page; Shift gives its default where changed)
default partial alphanumeric_keys modifier_keys
xkb_symbols "basic" {
    include "us(basic)"
    name[Group1] = "English (Titan 2 Elite)";
"""

    private const val MODIFIERS = """
    // Alt = Pastiera Alt layer, Sym (Pastiera sends it as Right Alt) = SYM layer.
    key <LALT> { type[Group1] = "ONE_LEVEL", symbols[Group1] = [ ISO_Level3_Latch ] };
    key <RALT> { type[Group1] = "ONE_LEVEL", symbols[Group1] = [ ISO_Level5_Latch ] };
    modifier_map Mod5 { <LALT> };
    modifier_map Mod3 { <RALT> };

    // Shift: tap = next letter capital, double-tap = caps lock, hold = normal.
    key <LFSH> { type[Group1] = "ONE_LEVEL", symbols[Group1] = [ ISO_Level2_Latch ] };
    key <RTSH> { type[Group1] = "ONE_LEVEL", symbols[Group1] = [ ISO_Level2_Latch ] };
    modifier_map Shift { <LFSH>, <RTSH> };

    // Ctrl: tap = Ctrl for the next key only (Ctrl, C copies), double-tap = locked,
    // tap again = off, hold = normal.
    key <LCTL> { type[Group1] = "ONE_LEVEL", symbols[Group1] = [ Control_L ],
                 actions[Group1] = [ LatchMods(modifiers = Control, clearLocks, latchToLock) ] };
    key <RCTL> { type[Group1] = "ONE_LEVEL", symbols[Group1] = [ Control_R ],
                 actions[Group1] = [ LatchMods(modifiers = Control, clearLocks, latchToLock) ] };
    modifier_map Control { <LCTL>, <RCTL> };
};
"""
}
