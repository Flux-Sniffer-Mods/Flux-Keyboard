package it.palsoftware.pastiera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecommendedSettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clear() {
        SettingsManager.getPreferences(context).edit().clear().commit()
    }

    @Test
    fun theRecommendedSettingsAreApplied() {
        assertTrue(RecommendedSettings.apply(context))
        assertTrue(SettingsManager.getPreferences(context).getBoolean(RecommendedSettings.PREF_APPLIED, false))
        assertTrue(SettingsManager.getSmartAltOffAfterOpening(context))
        assertTrue(SettingsManager.getSmartCtrlOffAfterShortcut(context))
        assertTrue(SettingsManager.getLedIndividualColorsEnabled(context))
        assertTrue(SettingsManager.getShiftBackspaceDelete(context))
        // The whole configuration from Flux Keyboard's Titan 2 Elite, matters of taste included
        assertFalse(SettingsManager.getEmojiSuggestionsEnabled(context))
        assertTrue(SettingsManager.getGifsEnabled(context))
        assertTrue(SettingsManager.getOneTimeCodesEnabled(context))
        // App lists aren't part of it: those stay at their defaults
        assertEquals(listOf("bitpit.launcher", "com.termux.x11"), SettingsManager.getHiddenKeyboardApps(context))
        assertEquals(SettingsManager.QUICK_LAUNCHER_BEHAVIOR_PASTIERA, SettingsManager.getQuickLauncherBehavior(context))
    }

    @Test
    fun nothingPersonalIsRecommended() {
        val keys = RecommendedSettings.values(titan2Elite = true).keys
        listOf(
            "app_enter_behavior_overrides", "sym_mappings_custom", "sym_mappings_page2_custom", "keyboard_theme_hardware",
            "menu_bar_buttons", "launcher_shortcuts", "hidden_keyboard_apps", "led_color_shift",
            "auto_correct_enabled_languages"
        ).forEach { assertFalse(it, it in keys) }
        // Titan 2 Elite extras only on that phone
        assertFalse("trackpad_gestures_enabled" in RecommendedSettings.values(titan2Elite = false))
        assertTrue("trackpad_gestures_enabled" in keys)
    }

    @Test
    fun unitTestsKeepPastierasOwnDefaults() {
        assertFalse(RecommendedSettings.applyIfFreshInstall(context))
    }

    @Test
    fun devsChoiceIsTheDefaultVariationBar() {
        assertEquals(SettingsManager.STATIC_VARIATION_PRESET_DEV_CHOICE, SettingsManager.getStaticVariationBarPreset(context))
        assertEquals(
            SettingsManager.getDevChoiceStaticVariationBasePreset(),
            it.palsoftware.pastiera.data.variation.VariationRepository.loadStaticVariations(context.assets, context)
        )
        // The older on/off switch, set before presets existed, keeps its meaning
        SettingsManager.getPreferences(context).edit().putBoolean("static_variation_bar_mode", false).commit()
        assertEquals(SettingsManager.STATIC_VARIATION_PRESET_OFF, SettingsManager.getStaticVariationBarPreset(context))
    }

    @Test
    fun niagaraIsHiddenByDefault() {
        assertEquals(listOf("bitpit.launcher", "com.termux.x11"), SettingsManager.getHiddenKeyboardApps(context))
    }

    @Test
    fun recommendedSettingsCountWhatDiffersThenMatchOnceApplied() {
        // A fresh start differs from the recommended configuration
        assertTrue(RecommendedSettings.differingSettings(context) > 0)
        assertTrue(RecommendedSettings.apply(context))
        assertEquals(0, RecommendedSettings.differingSettings(context))
        SettingsManager.setSmartAltOffAfterOpening(context, false)
        assertEquals(1, RecommendedSettings.differingSettings(context))
    }

    @Test
    fun everyRecommendedSettingHasAName() {
        RecommendedSettings.values(titan2Elite = true).keys.forEach { key ->
            val id = context.resources.getIdentifier("recommended_label_$key", "string", context.packageName)
            assertTrue("No label for $key", id != 0)
        }
    }

    @Test
    fun afterAnUpdateTheDifferencesAreOfferedOncePerVersion() {
        SettingsManager.getPreferences(context).edit().putBoolean("shift_backspace_delete", false).commit()
        val change = RecommendedSettings.changes(context).first { it.key == "shift_backspace_delete" }
        assertEquals(false, change.from)
        assertEquals(true, change.to)
        assertEquals("Shift + Backspace deletes forwards", recommendedLabel(context, change.key))
        assertEquals("off", recommendedValue(context, change.key, change.from))
        assertTrue(RecommendedSettings.shouldOfferAfterUpdate(context, "0.93"))

        RecommendedSettings.markOffered(context, "0.93")
        assertFalse(RecommendedSettings.shouldOfferAfterUpdate(context, "0.93"))
        assertTrue(RecommendedSettings.shouldOfferAfterUpdate(context, "0.94"))

        RecommendedSettings.apply(context)
        assertTrue(RecommendedSettings.changes(context).isEmpty())
        assertFalse(RecommendedSettings.shouldOfferAfterUpdate(context, "0.94"))
    }
}
