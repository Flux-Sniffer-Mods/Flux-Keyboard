package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.AutoCorrector
import it.palsoftware.pastiera.setAutoCorrectEnabledLanguages
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Text replacements work with the suggestion engine switched off, and so does their undo. What
 * the undo keeps (the last replacement, and the word whose replacement was undone) has to follow
 * the typing either way: left behind, an undone word was never replaced again until the keyboard
 * restarted, and Backspace undid a replacement made long before (issue #3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TextReplacementUndoStateTest {

    private lateinit var context: Context
    private val controllers = mutableListOf<SuggestionController>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SettingsManager.getPreferences(context).edit().clear().commit()
        SettingsManager.setAutoCorrectEnabledLanguages(context, setOf("en"))
        AutoCorrector.loadCorrections(context.assets, context)
    }

    @After
    fun tearDown() {
        controllers.forEach(SuggestionController::destroy)
        SettingsManager.getPreferences(context).edit().clear().commit()
    }

    @Test
    fun anUndoneReplacementAppliesAgainOnceTypingGoesOn() {
        for (engineOn in listOf(false, true)) {
            val controller = newController(engineOn)
            val input = FakeInputConnection(context, "")
            val state = "suggestion engine on=$engineOn"

            type(controller, input, "im")
            assertTrue(state, controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input).replaced)
            assertEquals(state, "I'm ", input.text)
            assertTrue(state, controller.handleBackspaceUndo(KeyEvent.KEYCODE_DEL, input))
            assertEquals(state, "im ", input.text)

            type(controller, input, "ok")
            controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input)
            type(controller, input, "im")
            assertTrue(state, controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input).replaced)
            assertEquals(state, "im ok I'm ", input.text)
        }
    }

    @Test
    fun aFinishedWordEndsTheRejectionEvenWhenItsLettersWereNotReported() {
        for (engineOn in listOf(false, true)) {
            val controller = newController(engineOn)
            val input = FakeInputConnection(context, "")
            val state = "suggestion engine on=$engineOn"

            type(controller, input, "im")
            assertTrue(state, controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input).replaced)
            assertTrue(state, controller.handleBackspaceUndo(KeyEvent.KEYCODE_DEL, input))
            assertEquals(state, "im ", input.text)

            // Typed on keys with a long-press layer: the editor has the letters, the controller
            // was never told
            input.commitText("ok", 1)
            controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input)
            input.commitText("im", 1)
            assertTrue(state, controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input).replaced)
            assertEquals(state, "im ok I'm ", input.text)
        }
    }

    @Test
    fun backspaceAfterMoreTypingIsAnOrdinaryDelete() {
        for (engineOn in listOf(false, true)) {
            val controller = newController(engineOn)
            val input = FakeInputConnection(context, "")
            val state = "suggestion engine on=$engineOn"

            type(controller, input, "im")
            assertTrue(state, controller.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, input).replaced)
            type(controller, input, "o")
            // The letter is deleted again, by the editor: the cursor is back after "I'm "
            input.deleteSurroundingText(1, 0)
            assertEquals(state, "I'm ", input.text)

            assertFalse(state, controller.handleBackspaceUndo(KeyEvent.KEYCODE_DEL, input))
            assertEquals(state, "I'm ", input.text)
        }
    }

    @Test
    fun anUndoneReplacementStaysUndoneUntilALetterIsTyped() {
        for (engineOn in listOf(false, true)) {
            val controller = newController(engineOn)
            val input = FakeInputConnection(context, "")
            val state = "suggestion engine on=$engineOn"

            type(controller, input, "im")
            assertTrue(state, controller.onBoundaryKey(KeyEvent.KEYCODE_ENTER, null, input).replaced)
            assertEquals(state, "I'm\n", input.text)
            assertTrue(state, controller.handleBackspaceUndo(KeyEvent.KEYCODE_DEL, input))
            assertEquals(state, "im\n", input.text)

            // The line break is deleted and typed again: the word stays as it was put back
            input.deleteSurroundingText(1, 0)
            assertFalse(state, controller.onBoundaryKey(KeyEvent.KEYCODE_ENTER, null, input).replaced)
            assertEquals(state, "im\n", input.text)
        }
    }

    private fun type(controller: SuggestionController, input: FakeInputConnection, text: String) {
        text.forEach { ch ->
            input.commitText(ch.toString(), 1)
            controller.onCharacterCommitted(ch.toString(), input)
        }
    }

    private fun newController(engineOn: Boolean): SuggestionController {
        val repository = FakeDictionaryRepository()
        return SuggestionController(
            context = context,
            assets = context.assets,
            settingsProvider = {
                SuggestionSettings(textReplacementsEnabled = true, suggestionsEnabled = engineOn)
            },
            isEnabled = { engineOn },
            onSuggestionsUpdated = {},
            currentLocale = Locale.ENGLISH,
            dictionaryRepositoryFactory = { _, _, _, _, _ -> repository }
        ).also { controllers += it }
    }

    private class FakeInputConnection(
        context: Context,
        initialText: String
    ) : BaseInputConnection(View(context), true) {
        private val buffer = StringBuilder(initialText)

        val text: String
            get() = buffer.toString()

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = buffer.takeLast(n)

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val start = (buffer.length - beforeLength).coerceAtLeast(0)
            buffer.delete(start, buffer.length)
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            buffer.append(text ?: "")
            return true
        }

        override fun beginBatchEdit(): Boolean = true

        override fun endBatchEdit(): Boolean = true
    }
}
