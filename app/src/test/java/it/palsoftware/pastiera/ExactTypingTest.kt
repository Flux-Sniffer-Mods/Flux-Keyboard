package it.palsoftware.pastiera

import android.text.InputType
import android.view.inputmethod.EditorInfo
import it.palsoftware.pastiera.core.InputContextState
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Exact typing (Settings > Apps): chosen apps keep every character as typed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExactTypingTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val text = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

    @After
    fun clear() {
        SettingsManager.setExactTypingApps(context, emptyList())
        SettingsManager.setExactTypingForNoSuggestionFields(context, false)
    }

    @Test
    fun onlyTheChosenAppsGetExactTyping() {
        assertFalse(SettingsManager.isExactTypingField(context, "org.connectbot", text))
        SettingsManager.setExactTypingApps(context, listOf("org.connectbot"))
        assertTrue(SettingsManager.isExactTypingField(context, "org.connectbot", text))
        assertFalse(SettingsManager.isExactTypingField(context, "com.whatsapp", text))
    }

    @Test
    fun noSuggestionFieldsOnlyWhenChosen() {
        val noSuggestions = text or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        assertFalse(SettingsManager.isExactTypingField(context, "any.app", noSuggestions))
        SettingsManager.setExactTypingForNoSuggestionFields(context, true)
        assertTrue(SettingsManager.isExactTypingField(context, "any.app", noSuggestions))
        assertFalse(SettingsManager.isExactTypingField(context, "any.app", text))
        // Asking for auto-correct too (Instagram's message box): smart typing after all
        assertFalse(SettingsManager.isExactTypingField(context, "any.app", noSuggestions or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT))
    }

    @Test
    fun exactTypingTurnsOffEveryRewrite() {
        val info = EditorInfo().apply { inputType = text or InputType.TYPE_TEXT_FLAG_CAP_WORDS }
        val normal = InputContextState.fromEditorInfo(info)
        assertFalse(normal.shouldDisableAutoCorrect)
        assertTrue(normal.requiresCapSentences)
        val exact = normal.copy(exactTyping = true)
        assertTrue(exact.shouldDisableAutoCorrect)
        assertTrue(exact.shouldDisableAutoCapitalize)
        assertTrue(exact.shouldDisableDoubleSpaceToPeriod)
        assertFalse(exact.requiresCapSentences)
        assertFalse(exact.requiresCapWords)
        // Suggestions still show
        assertFalse(exact.shouldDisableSuggestions)
    }
}
