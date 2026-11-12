package it.palsoftware.pastiera.inputmethod

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.InputContextState
import it.palsoftware.pastiera.setAutoCorrectEnabled
import it.palsoftware.pastiera.setAutoCorrectEnabledLanguages
import it.palsoftware.pastiera.setExperimentalSuggestionsEnabled
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * Text replacements typed on the real keys (issue #3). Letters on keys with an Alt character,
 * which is nearly every letter, are typed through the long-press path; a word whose replacement
 * was undone there was never replaced again until the keyboard restarted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TextReplacementUndoKeysTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var controller: ServiceController<PhysicalKeyboardInputMethodService>
    private lateinit var service: PhysicalKeyboardInputMethodService
    private var text = ""
    private var clock = 10_000L

    @Before
    fun setUp() {
        SettingsManager.getPreferences(context).edit().clear().commit()
        // A quick launcher left in front by another test would own every key pressed here
        QuickLauncherActivity.frontSince = 0L
        SettingsManager.setAutoCorrectEnabled(context, true)
        // The test keyboard has no language of its own to go by
        SettingsManager.setAutoCorrectEnabledLanguages(context, setOf("en"))
    }

    @After
    fun tearDown() {
        controller.destroy()
        SettingsManager.getPreferences(context).edit().clear().commit()
    }

    @Test
    fun anUndoneReplacementAppliesAgainOnceTypingGoesOn_suggestionsOn() = undoneThenTypedAgain(engineOn = true)

    @Test
    fun anUndoneReplacementAppliesAgainOnceTypingGoesOn_suggestionsOff() = undoneThenTypedAgain(engineOn = false)

    private fun undoneThenTypedAgain(engineOn: Boolean) {
        SettingsManager.setExperimentalSuggestionsEnabled(context, engineOn)
        startService()
        text = "so "

        type("im ")
        assertEquals("so I'm ", text)
        press(KeyEvent.KEYCODE_DEL)
        assertEquals("so im ", text)

        type("ok im ")
        assertEquals("so im ok I'm ", text)
    }

    @Test
    fun backspaceAfterMoreTypingDoesNotUndoAnEarlierReplacement() {
        startService()
        text = "so "

        type("im o")
        assertEquals("so I'm o", text)
        // The letter is deleted by the app, as an ordinary Backspace is
        text = text.dropLast(1)
        press(KeyEvent.KEYCODE_DEL)

        // An ordinary Backspace (whoever deletes the space): the replacement stays
        assertEquals("so I'm", text.trimEnd())
    }

    private fun startService() {
        controller = Robolectric.buildService(PhysicalKeyboardInputMethodService::class.java).create()
        service = controller.get()
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            packageName = "it.palsoftware.pastiera.test"
        }
        val connection = connection()
        setField("mInputConnection", connection)
        setField("mStartedInputConnection", connection)
        setField("mInputEditorInfo", editorInfo)
        setField("isInputViewActive", true)
        setField("inputContextState", InputContextState.fromEditorInfo(editorInfo))
        // As on the Titan keyboards: every letter used here has an Alt character
        val alt = getField<AlternateCharacterManager>("alternateCharacterManager")
        "imok".forEachIndexed { index, letter -> alt.addAltKeyMapping(keyCodeOf(letter), index.toString()) }
    }

    private fun type(keys: String) = keys.forEach { press(keyCodeOf(it)) }

    private fun keyCodeOf(char: Char): Int =
        if (char == ' ') KeyEvent.KEYCODE_SPACE else KeyEvent.KEYCODE_A + (char - 'a')

    private fun press(keyCode: Int): Boolean {
        val start = clock
        clock += 200L
        val down = service.onKeyDown(keyCode, KeyEvent(start, start, KeyEvent.ACTION_DOWN, keyCode, 0, 0))
        service.onKeyUp(keyCode, KeyEvent(start, start + 30L, KeyEvent.ACTION_UP, keyCode, 0, 0))
        return down
    }

    private fun connection(): InputConnection = Proxy.newProxyInstance(
        InputConnection::class.java.classLoader,
        arrayOf(InputConnection::class.java)
    ) { _, method, args ->
        when (method.name) {
            "commitText" -> {
                text += args?.getOrNull(0)?.toString().orEmpty()
                true
            }
            "deleteSurroundingText" -> {
                val before = (args?.getOrNull(0) as? Int) ?: 0
                text = text.dropLast(before.coerceAtMost(text.length))
                true
            }
            "getTextBeforeCursor" -> text.takeLast((args?.getOrNull(0) as? Int) ?: text.length)
            "getTextAfterCursor" -> ""
            "getExtractedText" -> ExtractedText().apply {
                this.text = this@TextReplacementUndoKeysTest.text
                selectionStart = this@TextReplacementUndoKeysTest.text.length
                selectionEnd = selectionStart
            }
            "beginBatchEdit", "endBatchEdit", "finishComposingText", "sendKeyEvent" -> true
            else -> when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                else -> null
            }
        }
    } as InputConnection

    @Suppress("UNCHECKED_CAST")
    private fun <T> getField(name: String): T = field(name).get(service) as T

    private fun setField(name: String, value: Any?) = field(name).set(service, value)

    private fun field(name: String): java.lang.reflect.Field {
        var type: Class<*>? = service.javaClass
        while (type != null) {
            runCatching { return type.getDeclaredField(name).apply { isAccessible = true } }
            type = type.superclass
        }
        error("No field $name")
    }
}
