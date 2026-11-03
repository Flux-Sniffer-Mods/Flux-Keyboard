package it.palsoftware.pastiera.settings

import it.palsoftware.pastiera.R

/** The rows of the smaller pages, so the search finds them too. */
internal fun moduleSettingEntries(): List<SettingEntry> = listOf(
    SettingEntry("system.crash_log", R.string.crash_log, summaryRes = R.string.crash_log_description, route = SettingRoute(SettingsDestination.Main)),
    SettingEntry("system.clear_logs", R.string.crash_log_clear, summaryRes = R.string.crash_log_clear_description, route = SettingRoute(SettingsDestination.Main)),
    SettingEntry("shell_extras.density_per_app", R.string.density_per_app_add, summaryRes = R.string.density_per_app_add_description, route = SettingRoute(SettingsDestination.ScreenSize)),
    SettingEntry("main.shell_access", R.string.root_title, summaryRes = R.string.settings_shell_access_description, route = SettingRoute(SettingsDestination.Main)),
    SettingEntry("main.screen_size", R.string.density_section, summaryRes = R.string.screen_size_row_description, route = SettingRoute(SettingsDestination.ScreenSize)),
    SettingEntry("terminal_mode.add_app", R.string.terminal_mode_add_app, summaryRes = R.string.terminal_mode_add_app_description, route = SettingRoute(SettingsDestination.TerminalMode)),
    SettingEntry("exact_typing.add_app", R.string.exact_typing_add_app, summaryRes = R.string.exact_typing_add_app_description, route = SettingRoute(SettingsDestination.ExactTyping)),
    SettingEntry("app_shortcuts.show_not_installed", R.string.app_shortcuts_show_not_installed, route = SettingRoute(SettingsDestination.AppShortcuts)),
    SettingEntry("trackpad.apps_choose", R.string.trackpad_apps_choose_title, summaryRes = R.string.trackpad_apps_choose_description, route = SettingRoute(SettingsDestination.TrackpadGestures)),
    SettingEntry("led_colors.reset", R.string.led_colors_reset_title, summaryRes = R.string.led_colors_reset_description, route = SettingRoute(SettingsDestination.LedColors)),
    SettingEntry("emoji_profiles.import", R.string.emoji_profiles_import_title, summaryRes = R.string.emoji_profiles_import_description, route = SettingRoute(SettingsDestination.EmojiProfiles)),
)
