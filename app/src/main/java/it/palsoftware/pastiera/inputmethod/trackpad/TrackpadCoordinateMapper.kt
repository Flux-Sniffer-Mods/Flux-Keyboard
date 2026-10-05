package it.palsoftware.pastiera.inputmethod.trackpad

data class TrackpadAxisRange(
    val min: Float,
    val max: Float
) {
    val span: Float
        get() = max - min

    val isValid: Boolean
        get() = min.isFinite() && max.isFinite() && span > 0f
}

object TrackpadCoordinateMapper {
    fun normalized(value: Float, range: TrackpadAxisRange): Float {
        if (!range.isValid || !value.isFinite()) return 0f
        return ((value - range.min) / range.span).coerceIn(0f, 1f)
    }

    fun third(value: Float, range: TrackpadAxisRange): Int {
        val normalized = normalized(value, range)
        return when {
            normalized < 1f / 3f -> 0
            normalized < 2f / 3f -> 1
            else -> 2
        }
    }

    /** The Titan 2 Elite's keys as a trackpad: ten roughly square keys across its X range (0-1079) */
    const val KEYS_ACROSS = 10

    /**
     * How far a suggestion swipe must travel, either way: [KEY_FRACTION] of one key, so sliding a
     * finger from one key onto the next picks wherever it starts, while the drift of a key press
     * (under about 100 of 1080) doesn't. A larger configured distance still applies.
     */
    fun pickDistance(configured: Float, xRange: TrackpadAxisRange?): Float {
        if (xRange == null || !xRange.isValid) return configured
        return maxOf(configured, xRange.span / KEYS_ACROSS * KEY_FRACTION)
    }

    /** Quick enough: over within [MAX_PICK_MS], or a slower slide that still keeps moving */
    fun pickQuickEnough(distance: Float, needed: Float, durationMs: Long, minVelocityPxPerMs: Float): Boolean =
        durationMs <= MAX_PICK_MS || distance / durationMs.coerceAtLeast(1L) >= minOf(minVelocityPxPerMs, needed / MAX_PICK_MS)

    const val KEY_FRACTION = 0.95f
    const val MAX_PICK_MS = 450L
}
