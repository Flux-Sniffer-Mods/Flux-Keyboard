package it.palsoftware.pastiera.core.suggestions

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Words that aren't in the dictionary, counted each time they're typed; used [threshold] times,
 * one goes into the user dictionary ("Learn words you use often"). Counts are kept between
 * sessions, for the [maxTracked] most recently seen words.
 */
class FrequentWordLearner(
    private val prefs: SharedPreferences,
    private val threshold: Int = DEFAULT_THRESHOLD,
    private val maxTracked: Int = MAX_TRACKED
) {
    private data class Seen(val form: String, val count: Int, val lastSeen: Long)

    private var seen: MutableMap<String, Seen>? = null
    // What was last read or written: stored counts changed by something else (a restored
    // backup) are read again instead of being overwritten
    private var seenRaw: String? = null

    /**
     * One more use of [word]. Returns the form to add to the dictionary once it has been used
     * [threshold] times (lowercase if it was ever typed that way, so a word capitalised only at
     * the start of sentences isn't kept capitalised), else null.
     */
    fun countUse(word: String, now: Long = System.currentTimeMillis()): String? {
        if (!isLearnable(word)) return null
        val all = load()
        val key = word.lowercase(Locale.ROOT)
        val previous = all[key]
        val form = when {
            previous == null -> word
            word == key -> key
            else -> previous.form
        }
        val count = (previous?.count ?: 0) + 1
        if (count >= threshold) {
            all.remove(key)
            save(all)
            return form
        }
        all[key] = Seen(form, count, now)
        if (all.size > maxTracked) {
            all.entries.sortedBy { it.value.lastSeen }.take(all.size - maxTracked).forEach { all.remove(it.key) }
        }
        save(all)
        return null
    }

    fun forget(word: String) {
        val all = load()
        if (all.remove(word.lowercase(Locale.ROOT)) != null) save(all)
    }

    private fun load(): MutableMap<String, Seen> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        seen?.let { if (raw == seenRaw) return it }
        val map = mutableMapOf<String, Seen>()
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val form = o.getString("w")
                map[form.lowercase(Locale.ROOT)] = Seen(form, o.optInt("c", 1), o.optLong("t", 0L))
            }
        }
        seen = map
        seenRaw = raw
        return map
    }

    private fun save(all: Map<String, Seen>) {
        val array = JSONArray()
        all.values.forEach { array.put(JSONObject().put("w", it.form).put("c", it.count).put("t", it.lastSeen)) }
        val raw = array.toString()
        seenRaw = raw
        prefs.edit().putString(KEY, raw).apply()
    }

    companion object {
        const val KEY = "frequent_unknown_words"
        const val DEFAULT_THRESHOLD = 3
        private const val MAX_TRACKED = 300
        private val WORD = Regex("^\\p{L}[\\p{L}\\p{M}]*(?:['’-]\\p{L}[\\p{L}\\p{M}]*)*$")

        /** Real words only: letters (with an inner apostrophe or hyphen), three or more of them. */
        fun isLearnable(word: String): Boolean = word.length in 3..32 && WORD.matches(word)
    }
}
