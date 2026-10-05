package it.palsoftware.pastiera.core

import android.content.Context
import it.palsoftware.pastiera.SettingsManager

/**
 * Suggestion swipes that learn how you swipe (on by default):
 *
 * - A pick you undo straight away (Backspace, or the swipe that deletes a word, within a few
 *   seconds) was a scroll taken for a pick: swipes need to go a little further.
 * - A swipe that nearly picked, followed soon after by a real pick, was a pick that fell short:
 *   swipes need a little less.
 * - After a pause in typing (Settings: idle time, 10 s by default), a swipe is a scroll and picks
 *   nothing, so reading or scrolling through a page doesn't pick a suggestion.
 *
 * The learning is a scale on the swipe distances you set, kept between 0.6 and 1.8 of them.
 */
object SuggestionSwipeLearning {
    private const val KEY_ENABLED = "suggestion_swipe_learning"
    private const val KEY_SCALE = "suggestion_swipe_learned_scale"
    private const val KEY_IDLE_SECONDS = "suggestion_swipe_idle_seconds"
    private const val MIN_SCALE = 0.6f
    private const val MAX_SCALE = 1.8f
    private const val UNDO_WINDOW_MS = 3_000L
    private const val NEAR_MISS_WINDOW_MS = 2_500L
    private const val NEAR_MISS_RATIO = 0.6f

    @Volatile private var lastPickAt = 0L
    @Volatile private var lastNearMissAt = 0L
    @Volatile private var lastTypedAt = 0L

    fun enabled(context: Context): Boolean = SettingsManager.getPreferences(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        SettingsManager.getPreferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Seconds without typing after which swipes don't pick (0: always pick). */
    fun idleSeconds(context: Context): Int =
        SettingsManager.getPreferences(context).getInt(KEY_IDLE_SECONDS, 10).coerceIn(0, 60)

    fun setIdleSeconds(context: Context, seconds: Int) {
        SettingsManager.getPreferences(context).edit().putInt(KEY_IDLE_SECONDS, seconds.coerceIn(0, 60)).apply()
    }

    /** The learned scale on the swipe distances (1 until something was learned). */
    fun scale(context: Context): Float =
        if (!enabled(context)) 1f
        else SettingsManager.getPreferences(context).getFloat(KEY_SCALE, 1f).coerceIn(MIN_SCALE, MAX_SCALE)

    fun resetLearned(context: Context) {
        SettingsManager.getPreferences(context).edit().remove(KEY_SCALE).apply()
    }

    /** A key was typed: swipes right after count as picks again. */
    fun onTyped(now: Long = System.currentTimeMillis()) {
        lastTypedAt = now
    }

    /** Whether a swipe now is a pick, or a scroll after a pause in typing. */
    fun swipesPick(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val idle = idleSeconds(context)
        return idle == 0 || lastTypedAt == 0L || now - lastTypedAt <= idle * 1000L
    }

    /** A swipe picked a suggestion. */
    fun onPicked(context: Context, now: Long = System.currentTimeMillis()) {
        if (now - lastNearMissAt <= NEAR_MISS_WINDOW_MS) {
            // The swipe before it fell short of this one: a little less needed
            adjust(context, 0.95f)
            lastNearMissAt = 0L
        }
        lastPickAt = now
        lastTypedAt = now
    }

    /** A swipe ended without picking: [reached] of the [needed] distance. */
    fun onMissed(reached: Float, needed: Float, now: Long = System.currentTimeMillis()) {
        if (needed > 0f && reached >= needed * NEAR_MISS_RATIO) lastNearMissAt = now
    }

    /** Backspace or a word deleted: right after a pick, the pick was a mistake. */
    fun onDeleted(context: Context, now: Long = System.currentTimeMillis()) {
        if (lastPickAt != 0L && now - lastPickAt <= UNDO_WINDOW_MS) {
            adjust(context, 1.08f)
            lastPickAt = 0L
        }
    }

    private fun adjust(context: Context, factor: Float) {
        if (!enabled(context)) return
        val next = (scale(context) * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        SettingsManager.getPreferences(context).edit().putFloat(KEY_SCALE, next).apply()
    }

    internal fun resetForTest() {
        lastPickAt = 0L
        lastNearMissAt = 0L
        lastTypedAt = 0L
    }
}
