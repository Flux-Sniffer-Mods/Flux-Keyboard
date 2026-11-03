package it.palsoftware.pastiera

import it.palsoftware.pastiera.update.bundledNotesHaveNewSince
import it.palsoftware.pastiera.update.bundledReleaseNotes
import it.palsoftware.pastiera.update.parseBundledReleaseNotes
import it.palsoftware.pastiera.update.friendlyVersion
import it.palsoftware.pastiera.update.shortVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The fork's own What's new ships with the app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BundledReleaseNotesTest {
    @Test
    fun anUpdateWithNothingNewSinceTheVersionLastSeenHasNothingToShow() {
        val body = """{"releases": {"0.92": "202609262030"},
            "highlights": ["Old"], "bugFixes": [{"text": "Fix", "after": "202609262058"}]}"""
        assertEquals(true, bundledNotesHaveNewSince(body, "0.92"))
        assertEquals(true, bundledNotesHaveNewSince(body, "0.93-flux.202609262058"))
        assertFalse(bundledNotesHaveNewSince(body, "0.93-flux.202609262101")!!)
        // A version whose build time isn't known (Pastiera's own): not for this to decide
        assertNull(bundledNotesHaveNewSince(body, "0.85"))
    }

    @Test
    fun forkNotesAreBundled() {
        val notes = assertNotNullAndGet(bundledReleaseNotes(RuntimeEnvironment.getApplication(), "0.86-flux.1"))
        assertTrue(notes.title.startsWith("Flux Keyboard"))
        assertTrue(notes.highlights.size >= 3)
        assertTrue(notes.docsUrl.endsWith("FORK_CHANGES.md"))
    }

    @Test
    fun notesSayWhatTheyCoverAndKeepThePastieraTeamsWorkApart() {
        val notes = assertNotNullAndGet(bundledReleaseNotes(RuntimeEnvironment.getApplication(), "0.86-flux.1"))
        assertTrue(notes.intro.orEmpty().contains("Pastiera 0.86"))
        assertEquals("Added by Flux Keyboard", notes.sectionTitle)
        assertTrue(notes.upstreamTitle.orEmpty().startsWith("From the Pastiera team"))
        assertTrue(notes.upstreamChanges.size >= 5)
    }

    @Test
    fun forkVersionsReadAsADate() {
        assertEquals("0.86 · 26 Sep 2026, 04:16", friendlyVersion("0.86-flux.202609260416", java.util.Locale.US))
        assertEquals("0.86", shortVersion("0.86-flux.202609260416"))
        assertEquals("0.85", friendlyVersion("0.85", java.util.Locale.UK))
        assertEquals("Flux Keyboard 0.86", bundledReleaseNotes(RuntimeEnvironment.getApplication(), "0.86-flux.202609260416")?.title)
    }

    @Test
    fun afterAnUpdateOnlyNewerEntriesAreListed() {
        val body = """{"title":"Flux Keyboard","intro":"All","introSince":"Since","highlights":["old",{"text":"new","after":"202609261444"}],"upstream":["team"]}"""
        val since = assertNotNullAndGet(parseBundledReleaseNotes(body, "0.90-flux.1", 202609261444L))
        assertEquals(listOf("new"), since.highlights)
        assertEquals(emptyList<String>(), since.upstreamChanges)
        assertEquals("Since", since.intro)
        val all = assertNotNullAndGet(parseBundledReleaseNotes(body, "0.90-flux.1", null))
        assertEquals(listOf("old", "new"), all.highlights)
        assertEquals("All", all.intro)
        // Nothing newer than a later build: no notes, so the full list is shown instead
        assertEquals(null, parseBundledReleaseNotes(body, "0.90-flux.1", 202609271200L))
    }

    private fun <T> assertNotNullAndGet(value: T?): T { assertNotNull(value); return value!! }
}
