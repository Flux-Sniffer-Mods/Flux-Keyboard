package it.palsoftware.pastiera.core

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/**
 * Undo and redo for typing, in any app (Ctrl+Z, Ctrl+Shift+Z): many apps' fields (chats, web
 * pages, notes) have no undo of their own. The keyboard keeps a short history of the field as you
 * type: a step after each word, and after each pause. Undo puts the field back as it was a step
 * before, changing only the part that differs.
 *
 * Kept in memory only, for the last few fields; never for passwords.
 */
object EditHistory {
    private const val MAX_STEPS = 60
    private const val MAX_FIELDS = 6
    private const val MAX_TEXT = 20_000

    data class Snapshot(val text: String, val selStart: Int, val selEnd: Int)

    private class Field {
        val undo = ArrayDeque<Snapshot>()
        val redo = ArrayDeque<Snapshot>()
    }

    private val fields = LinkedHashMap<String, Field>(MAX_FIELDS, 0.75f, true)
    private var currentKey: String? = null
    private var enabled = true
    /** The keyboard's own undo or redo is changing the field: not a step of its own. */
    @Volatile private var applying = false

    @Synchronized
    fun onFieldStarted(key: String?, recordable: Boolean) {
        currentKey = key
        enabled = recordable && key != null
        if (!enabled) return
        fields.getOrPut(key!!) { Field() }
        while (fields.size > MAX_FIELDS) fields.remove(fields.keys.first())
    }

    private fun field(): Field? = if (enabled) currentKey?.let { fields[it] } else null

    fun read(ic: InputConnection): Snapshot? {
        val extracted = runCatching { ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = MAX_TEXT }, 0) }
            .getOrNull() ?: return null
        val text = extracted.text?.toString() ?: return null
        // Only the whole field: positions in part of it would undo the wrong place
        if (text.length >= MAX_TEXT || extracted.startOffset != 0) return null
        val a = extracted.selectionStart.coerceIn(0, text.length)
        val b = extracted.selectionEnd.coerceIn(0, text.length)
        return Snapshot(text, minOf(a, b), maxOf(a, b))
    }

    /** The field as it is now, as a step (when its text changed since the last one). */
    @Synchronized
    fun record(ic: InputConnection) {
        if (applying) return
        val field = field() ?: return
        val now = read(ic) ?: return
        val last = field.undo.lastOrNull()
        if (last != null && last.text == now.text) {
            // Only the cursor moved: undo comes back to here
            field.undo[field.undo.lastIndex] = now
            return
        }
        field.undo.addLast(now)
        while (field.undo.size > MAX_STEPS) field.undo.removeFirst()
        field.redo.clear()
    }

    /** Undoes the last step; false when there's nothing to undo (the app's own undo can try). */
    @Synchronized
    fun undo(ic: InputConnection): Boolean {
        val field = field() ?: return false
        val now = read(ic) ?: return false
        // Typing since the last step is a step of its own
        if (field.undo.lastOrNull()?.text != now.text) {
            field.undo.addLast(now)
        }
        if (field.undo.size < 2) return false
        val current = field.undo.removeLast()
        val target = field.undo.last()
        field.redo.addLast(current)
        apply(ic, now, target)
        return true
    }

    /** Redoes the last undone step. */
    @Synchronized
    fun redo(ic: InputConnection): Boolean {
        val field = field() ?: return false
        if (field.redo.isEmpty()) return false
        val now = read(ic) ?: return false
        val target = field.redo.removeLast()
        field.undo.addLast(target)
        apply(ic, now, target)
        return true
    }

    /** Changes [now] into [target] by replacing only the part between them that differs. */
    private fun apply(ic: InputConnection, now: Snapshot, target: Snapshot) {
        val (prefix, suffix) = diff(now.text, target.text)
        val replacement = target.text.substring(prefix, target.text.length - suffix)
        applying = true
        try {
            ic.beginBatchEdit()
            ic.finishComposingText()
            ic.setSelection(prefix, now.text.length - suffix)
            ic.commitText(replacement, 1)
            ic.setSelection(target.selStart, target.selEnd)
            ic.endBatchEdit()
        } finally {
            applying = false
        }
    }

    /** Lengths of the common start and end of [a] and [b] (not overlapping). */
    internal fun diff(a: String, b: String): Pair<Int, Int> {
        val max = minOf(a.length, b.length)
        var prefix = 0
        while (prefix < max && a[prefix] == b[prefix]) prefix++
        var suffix = 0
        while (suffix < max - prefix && a[a.length - 1 - suffix] == b[b.length - 1 - suffix]) suffix++
        return prefix to suffix
    }

    internal fun resetForTest() {
        fields.clear()
        currentKey = null
        enabled = true
    }
}
