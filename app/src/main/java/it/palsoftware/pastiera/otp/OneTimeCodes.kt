package it.palsoftware.pastiera.otp

import android.os.Handler
import android.os.Looper

/**
 * One-time codes from notifications (SMS, email, authenticator apps): the latest code is kept
 * in memory only, for a few minutes, until it's typed. Nothing is stored or sent anywhere.
 */
object OneTimeCodes {
    const val LIFETIME_MS = 3 * 60 * 1000L

    private data class Code(val value: String, val at: Long)

    @Volatile private var latest: Code? = null
    // The code last typed: apps re-post the same notification (marked read, a
    // reply, a new message in the thread), and a code that's been used isn't offered again
    @Volatile private var used: Code? = null

    /** Told when a new code arrives, on the main thread (the keyboard offers it straight away). */
    @Volatile var onNewCode: ((String) -> Unit)? = null

    private val main by lazy { Handler(Looper.getMainLooper()) }

    // Words that mark a message as carrying a code, in the keyboard's languages. Each starts a
    // word ("shipping" and "Pinterest" hold no PIN, "barcode" no code), and the short ones end
    // one too ("opinion", "pins"). "Security" alone isn't one: security codes say "code" too,
    // and "security briefing in room 2204" carries no code
    private val keyword = Regex(
        "(?<![\\p{L}\\p{N}])(?:(?:pin|otp|2fa)(?![\\p{L}])|code|passcode|verif|two.factor|one.time|login|sign.in|" +
            "authenticat|codice|código|codigo|kod|код|mã|pinnwort|bestätigung|vérification|verificación|weryfik)",
        RegexOption.IGNORE_CASE
    )
    // 4–8 digits, optionally split in two halves ("123-456", "123 456") or after a prefix ("G-123456")
    // (a full stop or colon next to it is fine; a decimal point or time separator between digits isn't)
    private val candidate = Regex("(?<!\\d|\\d[.,/:])(?:[A-Z]{1,3}-)?(\\d{3,4}[- ]\\d{3,4}|\\d{4,8})(?![\\d%]|[.,/:]\\d)")
    private val currencyBefore = Regex("[$€£¥₹]\\s*$")

    private class Candidate(val code: String, val range: IntRange, val prefixed: Boolean)

    /**
     * The one-time code in a notification's text, if it looks like it carries one: one with a
     * sender's prefix ("G-123456"), else the number nearest a word like "code" ("Order 845921:
     * your code is 4417" gives 4417), six digits first when two are as near.
     */
    fun extract(text: String): String? {
        val keywords = keyword.findAll(text).map { it.range }.toList()
        if (keywords.isEmpty()) return null
        val codes = candidate.findAll(text)
            .filterNot { currencyBefore.containsMatchIn(text.substring(0, it.range.first)) }
            .map { Candidate(it.groupValues[1].replace("-", "").replace(" ", ""), it.range, it.value.first().isLetter()) }
            .filter { it.code.length in 4..8 }
            .toList()
        // A bare year is rarely the code when there's anything else
        val nonYears = codes.filterNot { it.code.length == 4 && (it.code.startsWith("19") || it.code.startsWith("20")) }
        val pool = nonYears.ifEmpty { codes }
        fun distance(range: IntRange) = keywords.minOf { word ->
            if (range.first > word.last) range.first - word.last else maxOf(0, word.first - range.last)
        }
        return pool.minWithOrNull(
            compareBy<Candidate> { if (it.prefixed) 0 else 1 }
                .thenBy { distance(it.range) }
                .thenBy { if (it.code.length == 6) 0 else 1 }
                // Two as likely: the later one, as a message's newest part comes last
                .thenByDescending { it.range.first }
        )?.code
    }

    private val copyWord = Regex("(?i)(copy|kopier|copia|copier|copiar|kopiuj|копир|копіюв|sao chép)")
    private val copyCode = Regex("(?<!\\d)(\\d{4,8})(?!\\d)")

    /** The code a notification's copy button names ("Copy 123456"), or null for any other button. */
    fun fromCopyAction(title: String): String? {
        if (!copyWord.containsMatchIn(title)) return null
        return copyCode.findAll(title.replace(Regex("(?<=\\d)[ -](?=\\d)"), "")).map { it.groupValues[1] }.singleOrNull()
    }

    fun offer(code: String, now: Long = System.currentTimeMillis()) {
        if (used?.let { it.value == code && now - it.at <= LIFETIME_MS } == true) return
        // The same code again is the same notification updated: keep its first arrival time
        if (latest?.value == code && current(now) != null) return
        latest = Code(code, now)
        main.post { onNewCode?.invoke(code) }
    }

    /** The current code, if one arrived in the last few minutes. */
    fun current(now: Long = System.currentTimeMillis()): String? =
        latest?.takeIf { now - it.at <= LIFETIME_MS }?.value

    fun consume(now: Long = System.currentTimeMillis()) {
        latest?.let { used = Code(it.value, now) }
        latest = null
    }
}
