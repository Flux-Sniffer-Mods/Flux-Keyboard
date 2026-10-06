package it.palsoftware.pastiera.inputmethod

import it.palsoftware.pastiera.core.suggestions.SuggestionResult

object DebugCaptureStore {

    enum class AutoCorrectionType { COMMIT, ATTEMPT }
    enum class AutoCorrectionTrigger { SPACE, ENTER, SUGGESTION_TAP, OTHER }
    enum class AutoCorrectionOutcome { APPLIED, SKIPPED, NOT_APPLICABLE }

    data class AutoCorrectionEvent(
        val timestampMs: Long,
        val type: String,
        val trigger: String,
        val source: String,
        val outcome: String,
        val before: String,
        val after: String?,
        val reason: String?,
        val distance: Int? = null,
        val kind: String? = null
    )

    data class SuggestionEntry(
        val candidate: String,
        val source: String,
        val kind: String
    )

    data class SuggestionsSnapshot(
        val timestampMs: Long,
        val entries: List<SuggestionEntry>
    )

    data class ImeContextSnapshot(
        val timestampMs: Long,
        val packageName: String?,
        val inputType: Int?,
        val subtypeLocale: String?,
        val resolvedLayout: String?,
        val physicalProfileOverride: String?
    )

    data class RawTrackpadEvent(
        val timestampMs: Long,
        val provider: String,
        val origin: String,
        val phase: String,
        val action: String,
        val outcome: String,
        val startX: Float?,
        val startY: Float?,
        val x: Float?,
        val y: Float?,
        val deltaX: Float?,
        val deltaY: Float?,
        val threshold: Float?,
        val deviceId: Int,
        val source: Int,
        val eventTimeUptimeMs: Long
    )

    private const val MAX_AUTOCORRECTIONS = 100
    private const val MAX_SUGGESTION_SNAPSHOTS = 50
    private const val MAX_RAW_TRACKPAD_EVENTS = 200

    private val autoCorrections = ArrayDeque<AutoCorrectionEvent>()
    private val suggestions = ArrayDeque<SuggestionsSnapshot>()
    private val rawTrackpadEvents = ArrayDeque<RawTrackpadEvent>()
    private var imeContextSnapshot: ImeContextSnapshot? = null

    /** A field the keyboard was given (the debug export lists the last few, whatever app they're in). */
    data class FieldInfo(
        val timestampMs: Long,
        val packageName: String?,
        val inputType: Int,
        val imeOptions: Int,
        val fieldId: Int,
        val hasHint: Boolean,
        val initialSelStart: Int,
        val initialSelEnd: Int,
        val shiftFieldType: String?,
        val restarting: Boolean
    )

    /** One Automatic Shift decision, with the text around the cursor masked (no real text). */
    data class AutoCapTrace(
        val timestampMs: Long,
        val packageName: String?,
        val result: String,
        val reason: String,
        val before: String?,
        val after: String?
    )

    private const val MAX_TRACKPAD_CLAIM = 60
    private val trackpadClaimEvents = ArrayDeque<Pair<Long, String>>()

    /**
     * Keyboard swipes per app: the app in front, whether swipes are taken from it, and the swipes
     * the accessibility service received (one line per swipe), for the debug export.
     */
    @Synchronized
    fun recordTrackpadClaim(message: String) {
        trackpadClaimEvents.addLast(System.currentTimeMillis() to message)
        while (trackpadClaimEvents.size > MAX_TRACKPAD_CLAIM) trackpadClaimEvents.removeFirst()
    }

    @Synchronized
    fun trackpadClaimSnapshot(): List<Pair<Long, String>> = trackpadClaimEvents.toList()

    private const val MAX_AUTOFILL = 40
    private val autofillEvents = ArrayDeque<Pair<Long, String>>()

    /** A step of inline autofill (password managers' chips), for the debug export. */
    @Synchronized
    fun recordAutofill(message: String) {
        autofillEvents.addLast(System.currentTimeMillis() to message)
        while (autofillEvents.size > MAX_AUTOFILL) autofillEvents.removeFirst()
    }

    @Synchronized
    fun autofillSnapshot(): List<Pair<Long, String>> = autofillEvents.toList()

    private const val MAX_FIELDS = 10
    private const val MAX_AUTO_CAP = 60
    private val fields = ArrayDeque<FieldInfo>()
    private val autoCapTraces = ArrayDeque<AutoCapTrace>()

    @Synchronized
    fun recordField(info: FieldInfo) {
        fields.addLast(info)
        while (fields.size > MAX_FIELDS) fields.removeFirst()
    }

    @Synchronized
    fun recordAutoCap(packageName: String?, result: String, reason: String, before: CharSequence?, after: CharSequence?) {
        autoCapTraces.addLast(
            AutoCapTrace(System.currentTimeMillis(), packageName, result, reason, mask(before, tail = true), mask(after, tail = false))
        )
        while (autoCapTraces.size > MAX_AUTO_CAP) autoCapTraces.removeFirst()
    }

    @Synchronized
    fun fieldsSnapshot(): List<FieldInfo> = fields.toList()

    @Synchronized
    fun autoCapSnapshot(): List<AutoCapTrace> = autoCapTraces.toList()

    /**
     * Text shape only: letters a, digits 0, spaces _, newlines \\n, punctuation as it is, anything
     * invisible or unusual as <U+XXXX>; the last (or first) 6 characters and the length.
     */
    internal fun mask(text: CharSequence?, tail: Boolean): String? {
        text ?: return null
        val part = if (tail) text.takeLast(6) else text.take(6)
        val shape = buildString {
            part.forEach { c ->
                append(
                    when {
                        c.isLetter() -> "a"
                        c.isDigit() -> "0"
                        c == ' ' -> "_"
                        c == '\n' -> "\\n"
                        c.code in 0x21..0x7e -> c.toString()
                        else -> "<U+%04X>".format(c.code)
                    }
                )
            }
        }
        return "len=${text.length} \"$shape\""
    }

    @Synchronized
    fun recordAutoCorrectionAttempt(
        before: String,
        trigger: AutoCorrectionTrigger,
        source: String = "UNKNOWN",
        after: String? = null,
        outcome: AutoCorrectionOutcome = AutoCorrectionOutcome.NOT_APPLICABLE,
        reason: String? = null,
        distance: Int? = null,
        kind: String? = null
    ) {
        // Suppress low-signal noise when auto-replace is off and there is no current word context.
        if (
            outcome == AutoCorrectionOutcome.NOT_APPLICABLE &&
            reason == "auto_replace_disabled" &&
            before.isBlank() &&
            after.isNullOrBlank()
        ) {
            return
        }
        autoCorrections.addLast(
            AutoCorrectionEvent(
                timestampMs = System.currentTimeMillis(),
                type = AutoCorrectionType.ATTEMPT.name.lowercase(),
                trigger = trigger.name.lowercase(),
                source = source,
                outcome = outcome.name.lowercase(),
                before = before,
                after = after,
                reason = reason,
                distance = distance,
                kind = kind
            )
        )
        while (autoCorrections.size > MAX_AUTOCORRECTIONS) {
            autoCorrections.removeFirst()
        }
    }

    @Synchronized
    fun recordAutoCorrectionCommit(
        before: String,
        after: String,
        trigger: AutoCorrectionTrigger,
        source: String = "UNKNOWN",
        distance: Int? = null,
        kind: String? = null
    ) {
        autoCorrections.addLast(
            AutoCorrectionEvent(
                timestampMs = System.currentTimeMillis(),
                type = AutoCorrectionType.COMMIT.name.lowercase(),
                trigger = trigger.name.lowercase(),
                source = source,
                outcome = AutoCorrectionOutcome.APPLIED.name.lowercase(),
                before = before,
                after = after,
                reason = null,
                distance = distance,
                kind = kind
            )
        )
        while (autoCorrections.size > MAX_AUTOCORRECTIONS) {
            autoCorrections.removeFirst()
        }
    }

    @Synchronized
    fun recordAutoCorrectionApplied(
        originalWord: String,
        correctedWord: String,
        trigger: AutoCorrectionTrigger = AutoCorrectionTrigger.OTHER,
        source: String = "TEXT_REPLACEMENT"
    ) {
        recordAutoCorrectionCommit(
            before = originalWord,
            after = correctedWord,
            trigger = trigger,
            source = source
        )
    }

    @Synchronized
    fun recordSuggestionsUpdated(suggestionResults: List<SuggestionResult>) {
        val entries = suggestionResults.map { result ->
            SuggestionEntry(
                candidate = result.candidate,
                source = result.source.name,
                kind = result.kind.name
            )
        }
        suggestions.addLast(
            SuggestionsSnapshot(
                timestampMs = System.currentTimeMillis(),
                entries = entries
            )
        )
        while (suggestions.size > MAX_SUGGESTION_SNAPSHOTS) {
            suggestions.removeFirst()
        }
    }

    @Synchronized
    fun updateImeContext(
        packageName: String?,
        inputType: Int?,
        subtypeLocale: String?,
        resolvedLayout: String?,
        physicalProfileOverride: String?
    ) {
        imeContextSnapshot = ImeContextSnapshot(
            timestampMs = System.currentTimeMillis(),
            packageName = packageName,
            inputType = inputType,
            subtypeLocale = subtypeLocale,
            resolvedLayout = resolvedLayout,
            physicalProfileOverride = physicalProfileOverride
        )
    }

    @Synchronized
    fun recordRawTrackpadEvent(
        provider: String,
        origin: String,
        phase: String,
        action: String,
        outcome: String,
        startX: Float? = null,
        startY: Float? = null,
        x: Float? = null,
        y: Float? = null,
        deltaX: Float? = null,
        deltaY: Float? = null,
        threshold: Float? = null,
        deviceId: Int = -1,
        source: Int = 0,
        eventTimeUptimeMs: Long = 0L
    ) {
        rawTrackpadEvents.addLast(
            RawTrackpadEvent(
                timestampMs = System.currentTimeMillis(),
                provider = provider,
                origin = origin,
                phase = phase,
                action = action,
                outcome = outcome,
                startX = startX,
                startY = startY,
                x = x,
                y = y,
                deltaX = deltaX,
                deltaY = deltaY,
                threshold = threshold,
                deviceId = deviceId,
                source = source,
                eventTimeUptimeMs = eventTimeUptimeMs
            )
        )
        while (rawTrackpadEvents.size > MAX_RAW_TRACKPAD_EVENTS) {
            rawTrackpadEvents.removeFirst()
        }
    }

    @Synchronized
    fun autoCorrectionsSnapshot(): List<AutoCorrectionEvent> = autoCorrections.toList()

    @Synchronized
    fun suggestionsSnapshot(): List<SuggestionsSnapshot> = suggestions.toList()

    @Synchronized
    fun rawTrackpadEventsSnapshot(): List<RawTrackpadEvent> = rawTrackpadEvents.toList()

    @Synchronized
    fun imeContextSnapshot(): ImeContextSnapshot? = imeContextSnapshot

    @Synchronized
    fun clearAll() {
        autoCorrections.clear()
        suggestions.clear()
        rawTrackpadEvents.clear()
        imeContextSnapshot = null
        fields.clear()
        autoCapTraces.clear()
        autofillEvents.clear()
    }
}
