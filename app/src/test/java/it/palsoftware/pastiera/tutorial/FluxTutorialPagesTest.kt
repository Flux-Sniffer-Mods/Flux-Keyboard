package it.palsoftware.pastiera.tutorial

import org.junit.Assert.assertNotNull
import org.junit.Test
import it.palsoftware.pastiera.settings.SettingLinkRegistry

/** The tutorial's buttons open settings by ID; those settings must exist. */
class FluxTutorialPagesTest {
    @Test
    fun tutorialButtonsOpenSettingsThatExist() {
        fluxTutorialSettingIds.forEach { id ->
            assertNotNull("No setting $id", SettingLinkRegistry.byId(id))
        }
    }
}
