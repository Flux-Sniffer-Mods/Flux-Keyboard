package it.palsoftware.pastiera.inputmethod.expansion

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dynamic parts of a snippet, filled in when it expands (palsoftware/pastiera#282):
 * {date}, {time}, {datetime}, {isodate}, {day} and {clipboard}. Anything else in braces
 * stays as written.
 */
object SnippetPlaceholders {
    private val PLACEHOLDER = Regex("""\{(date|time|datetime|isodate|day|clipboard)\}""")

    fun hasPlaceholders(text: String): Boolean = PLACEHOLDER.containsMatchIn(text)

    fun fill(
        text: String,
        now: Date = Date(),
        locale: Locale = Locale.getDefault(),
        clipboard: () -> String? = { null }
    ): String {
        if (!hasPlaceholders(text)) return text
        return PLACEHOLDER.replace(text) { match ->
            when (match.groupValues[1]) {
                "date" -> DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(now)
                "time" -> DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(now)
                "datetime" -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(now)
                "isodate" -> SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(now)
                "day" -> SimpleDateFormat("EEEE", locale).format(now)
                else -> clipboard().orEmpty()
            }
        }
    }
}
