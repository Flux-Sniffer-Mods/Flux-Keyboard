package it.palsoftware.pastiera.inputmethod.ui

/**
 * Logical modifier states that can be projected onto one or more physical key positions.
 */
internal enum class ModifierLedState {
    SHIFT,
    SYM,
    CTRL,
    ALT,
    // Flux Keyboard: the dedicated emoji key, on an optional fifth LED
    EMOJI
}

/**
 * A single LED segment in normalized coordinates inside the LED canvas.
 *
 * Keeping both axes and both dimensions configurable allows device presets to mirror
 * one- or multi-row physical keyboard layouts without changing the renderer.
 */
internal data class ModifierLedSegment(
    val state: ModifierLedState,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    init {
        require(x in 0f..1f) { "x must be normalized" }
        require(y in 0f..1f) { "y must be normalized" }
        require(width > 0f && x + width <= 1.0001f) { "width must fit the LED canvas" }
        require(height > 0f && y + height <= 1.0001f) { "height must fit the LED canvas" }
    }
}

internal data class ModifierLedLayout(
    val id: String,
    val segments: List<ModifierLedSegment>
) {
    init {
        require(segments.isNotEmpty()) { "An LED layout needs at least one segment" }
    }
}

internal object ModifierLedLayouts {
    /** Reproduces the previous six-zone layout, including the two empty middle zones. */
    val DEFAULT = ModifierLedLayout(
        id = "default",
        segments = listOf(
            ModifierLedSegment(ModifierLedState.SHIFT, x = 0.000f, y = 0f, width = 0.164f, height = 1f),
            ModifierLedSegment(ModifierLedState.SYM, x = 0.168f, y = 0f, width = 0.164f, height = 1f),
            ModifierLedSegment(ModifierLedState.CTRL, x = 0.672f, y = 0f, width = 0.164f, height = 1f),
            ModifierLedSegment(ModifierLedState.ALT, x = 0.840f, y = 0f, width = 0.160f, height = 1f)
        )
    )

    /**
     * Photo-derived Titan 2 Elite modifier map.
     *
     * Alt and Sym follow their upper physical row. Shift is intentionally mirrored at both
     * outer bottom positions because the keyboard tracks the active Shift state, not its origin.
     * Ctrl follows the Fn key that users configure as Ctrl, just inside the right Shift key.
     */
    val TITAN_2_ELITE = ModifierLedLayout(
        id = "titan2-elite",
        segments = listOf(
            ModifierLedSegment(ModifierLedState.ALT, x = 0.000f, y = 0.000f, width = 0.164f, height = 0.38f),
            ModifierLedSegment(ModifierLedState.SYM, x = 0.840f, y = 0.000f, width = 0.160f, height = 0.38f),
            ModifierLedSegment(ModifierLedState.SHIFT, x = 0.000f, y = 0.620f, width = 0.164f, height = 0.38f),
            ModifierLedSegment(ModifierLedState.CTRL, x = 0.672f, y = 0.620f, width = 0.164f, height = 0.38f),
            ModifierLedSegment(ModifierLedState.SHIFT, x = 0.840f, y = 0.620f, width = 0.160f, height = 0.38f)
        )
    )

    /**
     * Titan 2 Elite: one LED per modifier, left to right as the keys sit (Shift, Alt, Sym,
     * Ctrl), on every screen. Nothing is shared, so Shift never lights the Sym LED, which matters
     * when Right Shift is the emoji picker key.
     */
    val TITAN_2_ELITE_SPLIT = ModifierLedLayout(
        id = "titan2-elite-split",
        segments = listOf(
            ModifierLedSegment(ModifierLedState.SHIFT, x = 0.00f, y = 0.5f, width = 0.22f, height = 0.5f),
            ModifierLedSegment(ModifierLedState.ALT, x = 0.26f, y = 0.5f, width = 0.22f, height = 0.5f),
            ModifierLedSegment(ModifierLedState.SYM, x = 0.52f, y = 0.5f, width = 0.22f, height = 0.5f),
            ModifierLedSegment(ModifierLedState.CTRL, x = 0.78f, y = 0.5f, width = 0.22f, height = 0.5f)
        )
    )

    /**
     * Five equal LEDs, the fifth for the emoji key: the four modifiers shrink to make room, and
     * every LED keeps the same size.
     */
    private fun fiveEqual(id: String, order: List<ModifierLedState>): ModifierLedLayout {
        val gap = 0.04f
        val width = (1f - gap * (order.size - 1)) / order.size
        return ModifierLedLayout(id, order.mapIndexed { index, state ->
            ModifierLedSegment(state, x = (index * (width + gap)).coerceAtMost(1f - width), y = 0.5f, width = width, height = 0.5f)
        })
    }

    /** Titan 2 Elite in key order with the emoji key (Right Shift by default) last. */
    val TITAN_2_ELITE_SPLIT_EMOJI = fiveEqual(
        "titan2-elite-split-emoji",
        listOf(ModifierLedState.SHIFT, ModifierLedState.ALT, ModifierLedState.SYM, ModifierLedState.CTRL, ModifierLedState.EMOJI)
    )

    val DEFAULT_EMOJI = fiveEqual(
        "default-emoji",
        listOf(ModifierLedState.SHIFT, ModifierLedState.SYM, ModifierLedState.EMOJI, ModifierLedState.CTRL, ModifierLedState.ALT)
    ).let { layout -> layout.copy(segments = layout.segments.map { it.copy(y = 0f, height = 1f) }) }

    /** Split layouts: one LED per modifier along the corners and bottom edge. */
    fun isSplit(layout: ModifierLedLayout): Boolean =
        layout == TITAN_2_ELITE_SPLIT || layout == TITAN_2_ELITE_SPLIT_EMOJI

    fun resolve(
        physicalProfileOverride: String?,
        titan2EliteAutoDetected: Boolean,
        emojiLed: Boolean = false
    ): ModifierLedLayout {
        val base = resolve(physicalProfileOverride, titan2EliteAutoDetected)
        if (!emojiLed) return base
        return if (base == TITAN_2_ELITE_SPLIT) TITAN_2_ELITE_SPLIT_EMOJI else DEFAULT_EMOJI
    }

    fun resolve(
        physicalProfileOverride: String?,
        titan2EliteAutoDetected: Boolean
    ): ModifierLedLayout {
        val normalizedOverride = physicalProfileOverride?.trim()?.lowercase().orEmpty()
        val useAutoDetectedProfile = normalizedOverride.isEmpty() || normalizedOverride == "auto"
        return if (
            normalizedOverride == "titan2elite_qwerty" ||
            (useAutoDetectedProfile && titan2EliteAutoDetected)
        ) {
            TITAN_2_ELITE_SPLIT
        } else {
            DEFAULT
        }
    }
}
