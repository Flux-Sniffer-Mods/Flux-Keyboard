package it.palsoftware.pastiera.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.adb.KeyboardBacklight
import it.palsoftware.pastiera.commands.GameCommandSource
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import it.palsoftware.pastiera.gaming.GameProfiles
import it.palsoftware.pastiera.gaming.GamesActivity
import it.palsoftware.pastiera.getAutoCapitalizeFirstLetter
import it.palsoftware.pastiera.getAutoCorrectEnabled
import it.palsoftware.pastiera.getDoubleSpaceToPeriod
import it.palsoftware.pastiera.getEmojiSuggestionsEnabled
import it.palsoftware.pastiera.getGifsEnabled
import it.palsoftware.pastiera.getTrackpadGesturesEnabled
import it.palsoftware.pastiera.setAutoCapitalizeFirstLetter
import it.palsoftware.pastiera.setAutoCorrectEnabled
import it.palsoftware.pastiera.setDoubleSpaceToPeriod
import it.palsoftware.pastiera.setEmojiSuggestionsEnabled
import it.palsoftware.pastiera.setGifsEnabled
import it.palsoftware.pastiera.setTrackpadGesturesEnabled
import it.palsoftware.pastiera.streaming.StreamHostService

/**
 * The switches used most, grouped by what they're for rather than where they live in Settings:
 * gaming has its own shell-backed parts, typing its own. Each is the same setting as on its page.
 */
@Composable
internal fun QuickSettingsScreen(modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current

    @Composable
    fun Switch(title: Int, description: Int, read: () -> Boolean, write: (Boolean) -> Unit) {
        var on by remember { mutableStateOf(read()) }
        FluxSwitchRow(linkId = null, title = stringResource(title), description = stringResource(description), checked = on) {
            on = it
            write(it)
        }
    }

    FluxScreenScaffold(stringResource(R.string.quick_settings_title), onBack, modifier) {
        FluxNote(stringResource(R.string.quick_settings_description))

        SettingsSectionDivider(stringResource(R.string.settings_typing_title))
        Switch(R.string.auto_capitalize_title, R.string.auto_capitalize_description,
            { SettingsManager.getAutoCapitalizeFirstLetter(context) }) { SettingsManager.setAutoCapitalizeFirstLetter(context, it) }
        Switch(R.string.double_space_to_period_title, R.string.double_space_to_period_description,
            { SettingsManager.getDoubleSpaceToPeriod(context) }) { SettingsManager.setDoubleSpaceToPeriod(context, it) }
        Switch(R.string.auto_correct_title, R.string.auto_correct_title_description,
            { SettingsManager.getAutoCorrectEnabled(context) }) { SettingsManager.setAutoCorrectEnabled(context, it) }
        Switch(R.string.trackpad_gestures_enabled_title, R.string.trackpad_gestures_enabled_description,
            { SettingsManager.getTrackpadGesturesEnabled(context) }) { SettingsManager.setTrackpadGesturesEnabled(context, it) }

        SettingsSectionDivider(stringResource(R.string.settings_emoji_symbols_gifs_title))
        Switch(R.string.emoji_suggestions_title, R.string.emoji_suggestions_description,
            { SettingsManager.getEmojiSuggestionsEnabled(context) }) { SettingsManager.setEmojiSuggestionsEnabled(context, it) }
        Switch(R.string.gif_settings_title, R.string.gif_settings_description,
            { SettingsManager.getGifsEnabled(context) }) { SettingsManager.setGifsEnabled(context, it) }

        // Gaming: its own parts of the shell (screen size, the keys sent to games) come with it
        SettingsSectionDivider(stringResource(R.string.game_mode_title))
        Switch(R.string.game_mode_title, R.string.game_mode_switch_description,
            { GameProfiles.enabled(context) }) { GameProfiles.setEnabled(context, it) }
        Switch(R.string.games_app_switch, R.string.games_app_switch_description,
            { GamesActivity.enabled(context) }) { GamesActivity.setEnabled(context, it) }
        Switch(R.string.game_mode_quick_launcher, R.string.game_mode_quick_launcher_description,
            { GameCommandSource.enabled(context) }) { GameCommandSource.setEnabled(context, it) }
        Switch(R.string.stream_switch, R.string.stream_switch_description,
            { StreamHostService.enabled(context) }) { StreamHostService.setEnabled(context, it) }

        // The keyboard light: the phones that have one Flux Keyboard can reach (through the shell)
        if (DeviceSpecific.isTitan2EliteDevice()) {
            SettingsSectionDivider(stringResource(R.string.quick_settings_keyboard_light))
            Switch(R.string.root_backlight_screen_title, R.string.root_backlight_screen_description,
                { KeyboardBacklight.followScreen(context) }) { on ->
                SettingsManager.getPreferences(context).edit().putBoolean(KeyboardBacklight.KEY_FOLLOW_SCREEN, on).apply()
                KeyboardBacklight.start(context)
            }
        }
    }
}
