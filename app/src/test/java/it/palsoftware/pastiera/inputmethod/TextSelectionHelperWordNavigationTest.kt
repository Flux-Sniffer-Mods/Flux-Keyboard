package it.palsoftware.pastiera.inputmethod

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TextSelectionHelperWordNavigationTest {

    @Test
    fun moveCursorWordLeft_movesToPreviousWordStart() {
        val inputConnection = inputConnectionWithText(
            text = "alpha beta gamma",
            selectionStart = 16,
            selectionEnd = 16
        )

        assertTrue(TextSelectionHelper.moveCursorWordLeft(inputConnection))

        verify(inputConnection).setSelection(11, 11)
    }

    @Test
    fun moveCursorWordRight_movesToNextWordStart() {
        val inputConnection = inputConnectionWithText(
            text = "alpha beta gamma",
            selectionStart = 0,
            selectionEnd = 0
        )

        assertTrue(TextSelectionHelper.moveCursorWordRight(inputConnection))

        verify(inputConnection).setSelection(6, 6)
    }

    @Test
    fun expandSelectionWordLeft_extendsSelectionToPreviousWordStart() {
        val inputConnection = inputConnectionWithText(
            text = "alpha beta gamma",
            selectionStart = 11,
            selectionEnd = 16
        )

        assertTrue(TextSelectionHelper.expandSelectionWordLeft(inputConnection))

        verify(inputConnection).setSelection(6, 16)
    }

    @Test
    fun expandSelectionWordRight_extendsSelectionToNextWordStart() {
        val inputConnection = inputConnectionWithText(
            text = "alpha beta gamma",
            selectionStart = 0,
            selectionEnd = 5
        )

        assertTrue(TextSelectionHelper.expandSelectionWordRight(inputConnection))

        verify(inputConnection).setSelection(0, 10)
    }

    @Test
    fun wordSelection_reversingDirectionShrinksSelection() {
        val text = "alpha beta gamma"
        val firstInputConnection = inputConnectionWithText(
            text = text,
            selectionStart = 0,
            selectionEnd = 0
        )
        assertTrue(TextSelectionHelper.expandSelectionWordRight(firstInputConnection))
        verify(firstInputConnection).setSelection(0, 5)

        val secondInputConnection = inputConnectionWithText(
            text = text,
            selectionStart = 0,
            selectionEnd = 5
        )
        assertTrue(TextSelectionHelper.expandSelectionWordLeft(secondInputConnection))
        verify(secondInputConnection).setSelection(0, 0)
    }

    @Test
    fun characterSelection_reversingDirectionShrinksSelection() {
        val text = "alpha"
        val firstInputConnection = inputConnectionWithText(
            text = text,
            selectionStart = 5,
            selectionEnd = 5
        )
        assertTrue(TextSelectionHelper.expandSelectionLeft(firstInputConnection))
        verify(firstInputConnection).setSelection(4, 5)

        val secondInputConnection = inputConnectionWithText(
            text = text,
            selectionStart = 4,
            selectionEnd = 5
        )
        assertTrue(TextSelectionHelper.expandSelectionRight(secondInputConnection))
        verify(secondInputConnection).setSelection(5, 5)
    }

    @Test
    fun partOfALongField_movesInTheWholeFieldsPositions() {
        // The app handed over the field from position 1000 on
        val wordLeft = inputConnectionWithText(
            text = "alpha beta gamma",
            selectionStart = 16,
            selectionEnd = 16,
            startOffset = 1000
        )
        assertTrue(TextSelectionHelper.moveCursorWordLeft(wordLeft))
        verify(wordLeft).setSelection(1011, 1011)

        val charRight = inputConnectionWithText(
            text = "alpha beta gamma",
            selectionStart = 5,
            selectionEnd = 5,
            startOffset = 1000
        )
        assertTrue(TextSelectionHelper.expandSelectionRight(charRight))
        verify(charRight).setSelection(1005, 1006)
    }

    private fun inputConnectionWithText(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        startOffset: Int = 0
    ): InputConnection {
        val inputConnection = mock(InputConnection::class.java)
        `when`(inputConnection.getExtractedText(any(ExtractedTextRequest::class.java), anyInt())).thenReturn(
            ExtractedText().apply {
                this.text = text
                this.selectionStart = selectionStart
                this.selectionEnd = selectionEnd
                this.startOffset = startOffset
            }
        )
        `when`(inputConnection.setSelection(anyInt(), anyInt())).thenReturn(true)
        return inputConnection
    }
}
