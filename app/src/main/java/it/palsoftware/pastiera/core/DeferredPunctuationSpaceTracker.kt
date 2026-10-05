package it.palsoftware.pastiera.core

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager

/**
 * Defers spaces after configured punctuation until more text is actually typed.
 * This avoids trailing whitespace when punctuation ends a message.
 */
object DeferredPunctuationSpaceTracker {
    private const val NO_SPACE_BEFORE: String = ".,;:!?/\\)]}»›"

    /** Mouths that follow ":" or ";" straight away in emoticons such as :D ;P :O :3 */
    private const val EMOTICON_LETTERS: String = "DPpOoXxSsbc3"

    @Volatile
    private var pending: Boolean = false

    /** The punctuation whose space is pending */
    @Volatile
    private var pendingAfter: Char? = null

    /** A letter typed straight after ":" or ";", held back as a possible emoticon (":D") */
    @Volatile
    private var heldEmoticonLetter: Char? = null

    /** Off in fields where a space after "." or "@" would break what's typed: see [appliesTo] */
    @Volatile
    private var enabled: Boolean = true

    /** Called as each field starts */
    fun startField(info: EditorInfo?) {
        enabled = appliesTo(info)
        clear()
    }

    /**
     * Spaces after punctuation belong in prose only: not in email addresses, sign-in names,
     * web addresses, passwords, numbers or dates.
     */
    fun appliesTo(info: EditorInfo?): Boolean {
        if (info == null) return true
        val type = info.inputType
        if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return type == InputType.TYPE_NULL
        val variation = type and InputType.TYPE_MASK_VARIATION
        if (variation in setOf(
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_FILTER
            )) return false
        // Sign-in fields often only say so in their hint text or name
        val hints = (info.hintText?.toString().orEmpty() +
            " " + info.fieldName.orEmpty()).lowercase()
        return listOf("email", "e-mail", "username", "user name", "login", "sign in", "url", "website")
            .none { it in hints }
    }

    fun prepareForTextCommit(
        context: Context,
        inputConnection: InputConnection,
        text: CharSequence
    ): Boolean {
        val first = text.firstOrNull() ?: return false
        if (!enabled) {
            clear()
            return false
        }
        val held = heldEmoticonLetter
        heldEmoticonLetter = null
        if (first.isWhitespace()) {
            clear()
            return false
        }

        var insertedSpace = false
        // ":D" went on into a word (":Do"): it was a word after all, so it gets its space
        if (held != null && first.isLetterOrDigit()) {
            val before = inputConnection.getTextBeforeCursor(1, 0)
            if (before?.length == 1 && before[0] == held) {
                inputConnection.deleteSurroundingText(1, 0)
                inputConnection.commitText(" $held", 1)
                insertedSpace = true
            }
        }

        val hadPending = pending
        val after = pendingAfter
        // Numbers, times and decimals (1,000  12:30  3.14) stay together
        if (hadPending && after != null && first.isDigit() && followsDigit(inputConnection, after)) {
            pending = false
            pendingAfter = null
            return insertedSpace
        }
        if (hadPending && first !in NO_SPACE_BEFORE && after != null &&
            SettingsManager.getEmoticonPunctuation(context) && continuesEmoticon(after, first)
        ) {
            // Punctuation typed straight into more, as in :-) ;( :D, keeps its shape
            pending = false
            pendingAfter = null
            if (first.isLetterOrDigit()) heldEmoticonLetter = first
            return insertedSpace
        }
        if (hadPending && first !in NO_SPACE_BEFORE) {
            inputConnection.commitText(" ", 1)
            pending = false
            insertedSpace = true
        }

        val configured = SettingsManager.getSpaceAfterPunctuation(context)
        pending = when {
            first in configured -> true
            hadPending && first in NO_SPACE_BEFORE -> true
            else -> false
        }
        pendingAfter = if (first in configured) first else if (pending) after else null
        return insertedSpace
    }

    /** Whether the text before the cursor ends in a digit then [punctuation], as in "12:" */
    private fun followsDigit(inputConnection: InputConnection, punctuation: Char): Boolean {
        val before = inputConnection.getTextBeforeCursor(2, 0) ?: return false
        return before.length == 2 && before[0].isDigit() && before[1] == punctuation
    }

    fun clear() {
        pending = false
        pendingAfter = null
        heldEmoticonLetter = null
    }

    /**
     * Whether [next], typed straight after [punctuation], makes an emoticon rather than starting
     * the next word. Never after a full stop; opening quotes and brackets only after ":" and ";".
     */
    internal fun continuesEmoticon(punctuation: Char, next: Char): Boolean {
        if (punctuation == '.' || next.isWhitespace()) return false
        val face = punctuation == ':' || punctuation == ';'
        // Any letter after ":" or ";" waits: the key after it tells a word (": Do") from a face
        // (":v", ":T"), so the space only goes in once it's a word
        if (next.isLetter()) return face
        if (next.isDigit()) return face && next in EMOTICON_LETTERS
        if (next in "\"“”«„") return false
        if (!face && next in "([{'‘") return false
        return true
    }

    fun onTextCommitted(context: Context, text: CharSequence) {
        if (!enabled) return
        val first = text.firstOrNull() ?: return
        if (first in SettingsManager.getSpaceAfterPunctuation(context)) {
            pending = true
            pendingAfter = first
        }
    }

    internal fun isPending(): Boolean = pending
}
