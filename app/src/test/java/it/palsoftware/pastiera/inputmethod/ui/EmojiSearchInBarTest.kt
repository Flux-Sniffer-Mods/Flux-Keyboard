package it.palsoftware.pastiera.inputmethod.ui

import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import it.palsoftware.pastiera.inputmethod.suggestions.ui.FullSuggestionsBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EmojiSearchInBarTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun hostedSearchFieldIsVisibleAndCapturesTyping() {
        val picker = EmojiPickerView(context) {}
        val host = FrameLayout(context)

        picker.setSearchFieldHost(host)

        assertEquals(1, host.childCount)
        assertTrue(host.getChildAt(0) is EditText)
        assertTrue(picker.isSearchInputActive())
    }

    @Test
    fun searchButtonSwitchesTypingBetweenHostedFieldAndApp() {
        val picker = EmojiPickerView(context) {}
        picker.setSearchFieldHost(FrameLayout(context))
        val searchButton = picker.edgeControls.first

        searchButton.performClick()
        assertFalse(picker.isSearchInputActive())

        searchButton.performClick()
        assertTrue(picker.isSearchInputActive())
    }

    @Test
    fun releasingTheHostMovesTheFieldBackAndResetsSearch() {
        val picker = EmojiPickerView(context) {}
        val host = FrameLayout(context)
        picker.setSearchFieldHost(host)

        picker.setSearchFieldHost(null)

        assertEquals(0, host.childCount)
        assertFalse(picker.isSearchInputActive())
        assertFalse(picker.isSearchPanelShowing())
    }

    @Test
    fun sameHostOnEveryStatusUpdateKeepsTheUsersTypingChoice() {
        val picker = EmojiPickerView(context) {}
        val host = FrameLayout(context)
        picker.setSearchFieldHost(host)
        picker.edgeControls.first.performClick() // typing back to the app
        assertFalse(picker.isSearchInputActive())

        picker.setSearchFieldHost(host)
        assertFalse(picker.isSearchInputActive())
    }

    @Test
    fun reopeningThePickerCapturesTypingAgain() {
        val picker = EmojiPickerView(context) {}
        val host = FrameLayout(context)
        picker.setSearchFieldHost(host)
        picker.setSearchFieldHost(null) // picker closed

        picker.setSearchFieldHost(host) // picker reopened
        assertTrue(picker.isSearchInputActive())
        assertEquals(1, host.childCount)
    }

    @Test
    fun barShowsTheHostInPlaceOfSuggestions() {
        val bar = FullSuggestionsBar(context)
        bar.ensureView()
        val host = bar.centerAccessoryHost()
        assertNotNull(host)
        assertEquals(View.GONE, host!!.visibility)

        bar.setCenterAccessoryActive(true)
        assertEquals(View.VISIBLE, host.visibility)

        bar.setCenterAccessoryActive(false)
        assertEquals(View.GONE, host.visibility)
    }
}
