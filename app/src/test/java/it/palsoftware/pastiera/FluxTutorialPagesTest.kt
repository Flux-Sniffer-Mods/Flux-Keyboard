package it.palsoftware.pastiera

import org.junit.Assert.assertNotNull
import org.junit.Test

/** The tutorial's buttons open settings by ID; those settings must exist. */
class FluxTutorialPagesTest {
    @Test
    fun tutorialButtonsOpenSettingsThatExist() {
        fluxTutorialSettingIds.forEach { id ->
            assertNotNull("No setting $id", SettingLinkRegistry.byId(id))
        }
    }
}
