package it.palsoftware.pastiera.inputmethod.extrakeys

import android.view.KeyEvent

/**
 * A key on the extra keys row: the keys a physical keyboard leaves out (Esc, Tab, arrows, Home,
 * End) and a few edits, Termux style. Each sends a key, types a character, latches a modifier
 * for the next key, or runs a text field's own edit.
 */
enum class ExtraKey(
    val id: String,
    val label: String,
    val keyCode: Int = KeyEvent.KEYCODE_UNKNOWN,
    val text: String? = null,
    val editAction: Int = 0
) {
    ESC("esc", "Esc", KeyEvent.KEYCODE_ESCAPE),
    TAB("tab", "Tab", KeyEvent.KEYCODE_TAB),
    CTRL("ctrl", "Ctrl"),
    ALT("alt", "Alt"),
    DASH("dash", "-", text = "-"),
    SLASH("slash", "/", text = "/"),
    PIPE("pipe", "|", text = "|"),
    TILDE("tilde", "~", text = "~"),
    LEFT("left", "←", KeyEvent.KEYCODE_DPAD_LEFT),
    DOWN("down", "↓", KeyEvent.KEYCODE_DPAD_DOWN),
    UP("up", "↑", KeyEvent.KEYCODE_DPAD_UP),
    RIGHT("right", "→", KeyEvent.KEYCODE_DPAD_RIGHT),
    HOME("home", "Home", KeyEvent.KEYCODE_MOVE_HOME),
    END("end", "End", KeyEvent.KEYCODE_MOVE_END),
    PAGE_UP("page_up", "PgUp", KeyEvent.KEYCODE_PAGE_UP),
    PAGE_DOWN("page_down", "PgDn", KeyEvent.KEYCODE_PAGE_DOWN),
    DELETE("delete", "Del", KeyEvent.KEYCODE_FORWARD_DEL),
    SELECT_ALL("select_all", "All", editAction = android.R.id.selectAll),
    CUT("cut", "Cut", editAction = android.R.id.cut),
    COPY("copy", "Copy", editAction = android.R.id.copy),
    PASTE("paste", "Paste", editAction = android.R.id.paste);

    val isModifier: Boolean get() = this == CTRL || this == ALT

    companion object {
        fun byId(id: String): ExtraKey? = entries.firstOrNull { it.id == id }
    }
}

object ExtraKeySets {
    /** Terminals: the keys a shell needs that the keyboard has no key for. */
    val TERMINAL = listOf(
        ExtraKey.ESC, ExtraKey.TAB, ExtraKey.CTRL, ExtraKey.ALT, ExtraKey.DASH,
        ExtraKey.SLASH, ExtraKey.LEFT, ExtraKey.DOWN, ExtraKey.UP, ExtraKey.RIGHT
    )

    /** Everything else: moving around and editing text. */
    val TEXT = listOf(
        ExtraKey.LEFT, ExtraKey.DOWN, ExtraKey.UP, ExtraKey.RIGHT, ExtraKey.HOME,
        ExtraKey.END, ExtraKey.SELECT_ALL, ExtraKey.CUT, ExtraKey.COPY, ExtraKey.PASTE
    )

    const val MAX_KEYS = 10

    /**
     * While the row is open, the top row of letters presses its keys, left to right (Q is the
     * first key, P the tenth): the key under each letter does what the row says.
     */
    val PHYSICAL_KEYS = listOf(
        KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R,
        KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I,
        KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P
    )

    fun parse(stored: String?, fallback: List<ExtraKey>): List<ExtraKey> {
        val keys = stored?.split(',')?.mapNotNull { ExtraKey.byId(it.trim()) }?.distinct()?.take(MAX_KEYS)
        return if (keys.isNullOrEmpty()) fallback else keys
    }

    fun serialize(keys: List<ExtraKey>): String = keys.distinct().take(MAX_KEYS).joinToString(",") { it.id }

    /** The physical key that presses the key at [index], or null past the tenth. */
    fun physicalKeyFor(index: Int): Int? = PHYSICAL_KEYS.getOrNull(index)

    /** The row's key a physical key presses, or null when it isn't one of the top-row letters. */
    fun indexForPhysicalKey(keyCode: Int): Int = PHYSICAL_KEYS.indexOf(keyCode)
}

/** The row as shown: its keys, which modifiers are latched, and what pressing does. */
data class ExtraKeysRow(
    val keys: List<ExtraKey>,
    val latched: Set<ExtraKey>,
    val onKey: (ExtraKey) -> Unit,
    val onClose: () -> Unit
)

/** Opens or closes the row from outside the keyboard (a shortcut, the quick launcher). */
object ExtraKeysToggle {
    @Volatile
    var handler: (() -> Unit)? = null

    fun toggle(): Boolean = handler?.let { it(); true } ?: false
}
