package it.palsoftware.pastiera.settings

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsActivity
import it.palsoftware.pastiera.SettingsManager

/** Settings grouped by task: moved rows keep their IDs and route to their new screens. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsLayoutTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun resetSettings() {
        SettingsManager.getPreferences(context).edit().clear().commit()
    }

    private fun route(id: String) = requireNotNull(SettingLinkRegistry.byId(id)) { "Missing $id" }.route

    @Test
    fun movedRowsRouteToTheirNewScreens() {
        val expected = mapOf(
            SettingLinkIds.TEXT_INPUT_AUTO_SHOW_KEYBOARD to SettingsDestination.KeyboardsLayouts,
            SettingLinkIds.TEXT_INPUT_CLEAR_ALT_ON_SPACE to SettingsDestination.Modifiers,
            SettingLinkIds.TEXT_INPUT_ALT_CTRL_SPEECH_SHORTCUT to SettingsDestination.Modifiers,
            SettingLinkIds.TEXT_INPUT_SHIFT_BACKSPACE_DELETE to SettingsDestination.Modifiers,
            SettingLinkIds.TEXT_INPUT_ALT_BACKSPACE_DELETE to SettingsDestination.Modifiers,
            SettingLinkIds.TEXT_INPUT_BACKSPACE_AT_START_DELETE to SettingsDestination.EditingKeys,
            SettingLinkIds.TEXT_INPUT_DELETE_NAV_MODE to SettingsDestination.EditingKeys,
            SettingLinkIds.TEXT_INPUT_TEXT_EXPANSION to SettingsDestination.TextExpansion,
            SettingLinkIds.TEXT_INPUT_AUTO_CAPITALIZE to SettingsDestination.TextInput,
            SettingLinkIds.ADVANCED_TRACKPAD_GESTURES to SettingsDestination.TrackpadGestures,
            SettingLinkIds.ADVANCED_SWIPE_INCREMENTAL_THRESHOLD to SettingsDestination.TrackpadGestures,
            SettingLinkIds.ADVANCED_BACKUP to SettingsDestination.Advanced,
            "trackpad.swipe_to_delete" to SettingsDestination.TrackpadGestures,
            "sym.auto_close" to SettingsDestination.FluxEmojiGifs,
            "sym.emoji_height" to SettingsDestination.FluxEmojiGifs,
            SettingLinkIds.MAIN_APP_SHORTCUTS to SettingsDestination.AppShortcuts
        )
        expected.forEach { (id, destination) -> assertEquals(id, destination, route(id).destination) }
    }

    /** A moved row is drawn on the screen its search link opens, and nowhere it used to be. */
    @Test
    fun movedRowsAreDrawnWhereTheirLinksLand() {
        val sources = java.io.File("src/main/java/it/palsoftware/pastiera").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }.associateBy { it.name }
        fun read(file: String): String = requireNotNull(sources[file]) { "$file not found" }.readText()
        fun body(file: String, function: String): String {
            val text = read(file)
            val start = text.indexOf("fun $function(")
            assertTrue("$function in $file", start >= 0)
            val next = text.indexOf("\n@Composable", start).let { if (it < 0) text.length else it }
            return text.substring(start, next)
        }
        val keyboards = body("SettingsHubScreens.kt", "KeyboardsLayoutsHubScreen")
        val typing = body("SettingsHubScreens.kt", "TypingHubScreen")
        val look = body("SettingsHubScreens.kt", "LookSoundHubScreen")
        val apps = body("SettingsHubScreens.kt", "AppsHubScreen")
        val privacy = read("AdvancedSettingsScreen.kt")
        val trackpad = read("TrackpadGestureSettingsScreen.kt")

        assertTrue(keyboards.contains("SettingLinkIds.TEXT_INPUT_AUTO_SHOW_KEYBOARD"))
        assertFalse(apps.contains("TEXT_INPUT_AUTO_SHOW_KEYBOARD"))
        assertTrue(typing.contains("\"customization.variations\""))
        assertTrue(typing.contains("SettingLinkIds.MAIN_NAV_MODE"))
        assertFalse(look.contains("customization.variations"))
        assertTrue(privacy.contains("SettingLinkIds.MAIN_APP_LANGUAGE"))
        assertFalse(look.contains("MAIN_APP_LANGUAGE"))
        assertTrue(trackpad.contains("SwipePadThresholdRow()"))
        assertFalse(look.contains("SwipePadThresholdRow()"))
        assertEquals(SettingsDestination.NavMode, route(SettingLinkIds.MAIN_NAV_MODE).destination)
        assertEquals(SettingsDestination.AppLanguage, route(SettingLinkIds.MAIN_APP_LANGUAGE).destination)
    }

    @Test
    fun keyShortcutTogglesLiveOnTheKeyShortcutsPage() {
        listOf(
            "quick_launcher.sym_shortcuts",
            "quick_launcher.alt_shortcuts",
            "quick_launcher.sym_in_text_fields",
            "quick_launcher.alt_in_text_fields"
        ).forEach { id ->
            assertEquals(id, SettingsActivity.CUSTOMIZATION_DESTINATION_KEY_SHORTCUTS, route(id).customizationDestination)
        }
        assertEquals(
            SettingsActivity.CUSTOMIZATION_DESTINATION_LAUNCHER_SHORTCUTS,
            route("quick_launcher.enabled").customizationDestination
        )
    }

    @Test
    fun retiredRowsAreHiddenButOldLinksStillLand() {
        val retired = mapOf(
            SettingLinkIds.MODIFIERS_ALT_KEY_SHORTCUTS to SettingLinkIds.MODIFIERS_SYM_SHORTCUTS,
            SettingLinkIds.MAIN_CUSTOMIZATION to SettingLinkIds.MAIN_LOOK_SOUND
        )
        retired.forEach { (id, landsOn) ->
            val entry = requireNotNull(SettingLinkRegistry.byId(id))
            assertFalse(entry.isAvailable(context))
            assertEquals(landsOn, SettingLinkRegistry.visibleTarget(context, entry).id)
            assertFalse(SettingLinkRegistry.search(context, context.getString(entry.titleRes)).any { it.id == id })
        }
    }

    @Test
    fun everyScreenHasABreadcrumbTitle() {
        SettingsDestination.entries
            .filter { it != SettingsDestination.DeviceSymLayerEditor }
            .forEach { destination ->
                assertTrue("$destination lacks a title", SettingLinkRegistry.destinationTitles.containsKey(destination))
            }
    }

    @Test
    fun theNewGroupsAreSearchable() {
        mapOf(
            SettingLinkIds.MAIN_KEYBOARDS_LAYOUTS to R.string.settings_keyboards_layouts_title,
            SettingLinkIds.MAIN_TYPING to R.string.settings_typing_title,
            SettingLinkIds.MAIN_LOOK_SOUND to R.string.settings_look_sound_title,
            SettingLinkIds.MAIN_APPS to R.string.settings_apps_title,
            SettingLinkIds.MAIN_APP_SHORTCUTS to R.string.app_shortcuts_title
        ).forEach { (id, title) ->
            assertTrue(id, SettingLinkRegistry.search(context, context.getString(title)).any { it.id == id })
        }
    }

    @Test
    fun developerToolsOnlyShowWithDeveloperOptions() {
        val developerIds = listOf(
            SettingLinkIds.TRACKPAD_DEBUG,
            SettingLinkIds.ADVANCED_SHOW_RELEASE_NOTES_TUTORIAL,
            SettingLinkIds.MAIN_DEVELOPER
        )
        developerIds.forEach { id ->
            val entry = requireNotNull(SettingLinkRegistry.byId(id))
            assertFalse(id, entry.isAvailable(context))
            assertEquals(id, SettingLinkIds.DEVELOPER_OPTIONS_ENABLED, SettingLinkRegistry.visibleTarget(context, entry).id)
        }
        SettingsManager.setDeveloperOptionsEnabled(context, true)
        developerIds.forEach { id ->
            val entry = requireNotNull(SettingLinkRegistry.byId(id))
            assertTrue(id, entry.isAvailable(context))
            assertEquals(id, SettingsDestination.Developer, entry.route.destination)
        }
        // Display contour calibration also needs developer options, and lives in them
        assertEquals(SettingsDestination.Developer, route("advanced.corner_calibration").destination)
        listOf("status_bar.rounded_corners", "status_bar.top_corner", "status_bar.max_icon_shrink").forEach { id ->
            assertEquals(id, SettingsDestination.FluxTitanScreen, route(id).destination)
        }
    }

    @Test
    fun incognitoFollowsAppsUnlessAlwaysOn() {
        val noLearning = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        assertFalse(SettingsManager.isIncognitoField(context, 0))
        assertTrue(SettingsManager.isIncognitoField(context, noLearning))
        SettingsManager.setIncognitoFollowApps(context, false)
        assertFalse(SettingsManager.isIncognitoField(context, noLearning))
        SettingsManager.setIncognitoAlways(context, true)
        assertTrue(SettingsManager.isIncognitoField(context, 0))
    }

    @Test
    fun settingsForHardwareThisPhoneLacksStayHidden() {
        // Robolectric is no Titan 2 and has never had a Clicks keyboard
        listOf("clicks.backlight", SettingLinkIds.KEYBOARDS_DEVICES_KEYBOARD_ACCESSORIES, "hardware.titan2_layout").forEach { id ->
            val entry = requireNotNull(SettingLinkRegistry.byId(id))
            assertFalse(id, entry.isAvailable(context))
            assertFalse(id, SettingLinkRegistry.search(context, context.getString(entry.titleRes)).any { it.id == id })
        }
        // Once a Clicks keyboard has been connected, its settings stay
        SettingsManager.markClicksKeyboardSeen(context)
        assertTrue(requireNotNull(SettingLinkRegistry.byId("clicks.backlight")).isAvailable(context))
    }
}
