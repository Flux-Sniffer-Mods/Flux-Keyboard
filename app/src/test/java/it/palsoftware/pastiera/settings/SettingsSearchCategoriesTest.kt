package it.palsoftware.pastiera.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchCategoriesTest {
    @Test
    fun everyPageHasAMainCategory() {
        SettingsDestination.values().forEach { page ->
            assertTrue(page.name, SettingLinkRegistry.categoryOf(page) in SettingLinkRegistry.categories)
        }
        assertEquals(SettingsDestination.Typing, SettingLinkRegistry.categoryOf(SettingsDestination.AutoCorrection))
        assertEquals(SettingsDestination.Apps, SettingLinkRegistry.categoryOf(SettingsDestination.Customization))
        assertEquals(SettingsDestination.Advanced, SettingLinkRegistry.categoryOf(SettingsDestination.FluxOffline))
        SettingLinkRegistry.categories.forEach { assertTrue(it.name, it in SettingLinkRegistry.destinationTitles) }
    }
}
