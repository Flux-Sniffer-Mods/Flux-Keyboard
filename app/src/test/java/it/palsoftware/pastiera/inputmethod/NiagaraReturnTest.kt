package it.palsoftware.pastiera.inputmethod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Back out of Niagara's search returns to the app it was opened from. */
class NiagaraReturnTest {
    private val niagara = "bitpit.launcher"
    private val returns = QuickLauncherOpener.NiagaraReturn

    @Test
    fun backingOutOfTheSearchReturnsToTheApp() {
        returns.arm("org.telegram.messenger")
        assertNull(returns.onInputStarted("org.telegram.messenger", editable = false))
        assertNull(returns.onInputStarted(niagara, editable = true))
        assertEquals("org.telegram.messenger", returns.onInputStarted(niagara, editable = false))
        // Only once
        assertNull(returns.onInputStarted(niagara, editable = false))
    }

    @Test
    fun openingAnotherAppDoesNotReturn() {
        returns.arm("org.telegram.messenger")
        assertNull(returns.onInputStarted(niagara, editable = true))
        assertNull(returns.onInputStarted("com.android.chrome", editable = true))
        assertNull(returns.onInputStarted(niagara, editable = false))
    }

    @Test
    fun staleOpeningsAreForgotten() {
        returns.arm("org.telegram.messenger")
        val later = System.currentTimeMillis() + 10 * 60_000L
        assertNull(returns.onInputStarted(niagara, editable = true, now = later))
        assertNull(returns.onInputStarted(niagara, editable = false, now = later))
    }

    @Test
    fun searchClosingWithoutANewInputStillReturns() {
        // With Niagara's keyboard hidden, closing its search may only finish the input
        returns.arm("org.telegram.messenger")
        assertNull(returns.onInputStarted(niagara, editable = true))
        val closed = System.currentTimeMillis()
        assertEquals(true, returns.onInputFinished(niagara, now = closed))
        assertNull(returns.decide(now = closed + 100))
        assertEquals("org.telegram.messenger", returns.decide(now = closed + QuickLauncherOpener.NiagaraReturn.DECIDE_AFTER_MS))
    }

    @Test
    fun anAppOpeningFromTheSearchCancelsTheReturn() {
        returns.arm("org.telegram.messenger")
        assertNull(returns.onInputStarted(niagara, editable = true))
        val closed = System.currentTimeMillis()
        returns.onInputFinished(niagara, now = closed)
        assertNull(returns.onInputStarted("com.android.chrome", editable = true))
        assertNull(returns.decide(now = closed + QuickLauncherOpener.NiagaraReturn.DECIDE_AFTER_MS))
    }

    @Test
    fun backInNiagarasSearchReturnsStraightAway() {
        returns.arm("org.telegram.messenger")
        assertNull(returns.onBackPressed("org.telegram.messenger"))
        assertEquals("org.telegram.messenger", returns.onBackPressed(niagara))
        assertNull(returns.onBackPressed(niagara))
    }
}
