package it.palsoftware.pastiera.core

/**
 * Email addresses and phone numbers typed by hand, kept in the user dictionary so they can be
 * offered again ("Remember emails and phone numbers"). Pure logic: what counts as one, what to
 * learn from a field, and which saved ones match what's being typed.
 */
object ContactDetails {
    enum class Kind { EMAIL, PHONE }

    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")
    private val PHONE = Regex("^\\+?\\(?[0-9][0-9 ().-]*[0-9]$")
    private const val MIN_PHONE_DIGITS = 7
    private const val MAX_PHONE_DIGITS = 15
    private const val TRAILING_PUNCTUATION = ".,;:!?)\"'»”’"

    fun isEmail(text: String): Boolean = text.length <= 254 && EMAIL.matches(text)

    fun isPhone(text: String): Boolean {
        if (!PHONE.matches(text)) return false
        return digits(text).length in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS
    }

    fun kindOf(text: String): Kind? = when {
        isEmail(text) -> Kind.EMAIL
        isPhone(text) -> Kind.PHONE
        else -> null
    }

    fun digits(text: String): String = text.filter { it.isDigit() }

    /**
     * What to remember from a field, or null. In an email or phone field the whole entry counts;
     * anywhere else only the word just typed, and only an email or a number written without
     * spaces (a number with spaces can't be told apart from the words around it).
     * [typedByHand]: characters typed on the keyboard in this field; text that was pasted or
     * filled in is never learned.
     */
    fun toLearn(textBeforeCursor: String, fieldKind: Kind?, typedByHand: Int): String? {
        val candidate = if (fieldKind != null) {
            textBeforeCursor.trim()
        } else {
            textBeforeCursor.trimEnd().substringAfterLast(' ').substringAfterLast('\n')
                .trimStart('(', '"', '\'', '«', '“', '‘', '<')
                .trimEnd { it in TRAILING_PUNCTUATION }
        }
        if (candidate.isEmpty() || candidate.length > typedByHand) return null
        val kind = kindOf(candidate) ?: return null
        if (fieldKind != null && kind != fieldKind) return null
        return candidate
    }

    /** Whether [candidate] is already among [saved] (emails ignore case, numbers their spacing). */
    fun alreadySaved(saved: List<String>, candidate: String): Boolean = when (kindOf(candidate)) {
        Kind.EMAIL -> saved.any { it.equals(candidate, ignoreCase = true) }
        Kind.PHONE -> saved.any { isPhone(it) && digits(it) == digits(candidate) }
        null -> true
    }

    /**
     * Saved details of [kind] that go on from what's typed so far ([typed], the field up to the
     * cursor), most recently used first as [saved] is ordered. Blank [typed] offers them all.
     */
    fun matching(saved: List<String>, typed: String, kind: Kind, limit: Int = 3): List<String> {
        val prefix = typed.trim()
        return saved.asSequence()
            .filter { kindOf(it) == kind }
            .filter { entry ->
                when (kind) {
                    Kind.EMAIL -> entry.startsWith(prefix, ignoreCase = true) && !entry.equals(prefix, ignoreCase = true)
                    Kind.PHONE -> {
                        val typedDigits = digits(prefix)
                        digits(entry).startsWith(typedDigits) && digits(entry) != typedDigits
                    }
                }
            }
            .take(limit)
            .toList()
    }
}
