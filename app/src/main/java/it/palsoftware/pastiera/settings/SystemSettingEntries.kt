package it.palsoftware.pastiera.settings

import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.getNavModeEnabled
import it.palsoftware.pastiera.getTitan2EliteRoundedCornerInsetsEnabled
/** Entries for system controls whose screens also expose the same stable IDs. */
internal fun systemSettingEntries(): List<SettingEntry> = listOf(
    SettingEntry("advanced.corner_calibration", R.string.corner_calibration_title,
        summaryRes = R.string.corner_calibration_description, route = SettingRoute(SettingsDestination.Developer),
        availabilityCheck = { context ->
            SettingsManager.getDeveloperOptionsEnabled(context) &&
                (it.palsoftware.pastiera.inputmethod.DeviceSpecific.isTitan2EliteDevice() ||
                    SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context))
        },
        unavailableFallbackId = SettingLinkIds.DEVELOPER_OPTIONS_ENABLED),
    SettingEntry(
        id = "nav_mode.enabled",
        titleRes = R.string.nav_mode_enable_title,
        summaryRes = R.string.nav_mode_enable_description,
        route = SettingRoute(SettingsDestination.NavMode)
    ),
    SettingEntry(
        id = "nav_mode.ctrl_hold",
        titleRes = R.string.nav_mode_ctrl_hold_title,
        summaryRes = R.string.nav_mode_ctrl_hold_description,
        route = SettingRoute(SettingsDestination.NavMode),
        availabilityCheck = { SettingsManager.getNavModeEnabled(it) },
        unavailableFallbackId = "nav_mode.enabled"
    ),
    SettingEntry(
        id = "nav_mode.layout_aware_ctrl_shortcuts",
        titleRes = R.string.layout_aware_ctrl_shortcuts_title,
        summaryRes = R.string.layout_aware_ctrl_shortcuts_description,
        route = SettingRoute(SettingsDestination.NavMode),
        availabilityCheck = { SettingsManager.getNavModeEnabled(it) },
        unavailableFallbackId = "nav_mode.enabled"
    ),
    SettingEntry(
        id = "accessibility.bounce_keys_delay",
        titleRes = R.string.settings_accessibility_bounce_keys_delay_link_title,
        summaryRes = R.string.settings_accessibility_bounce_keys_delay_description,
        route = SettingRoute(SettingsDestination.Accessibility)
    ),
    SettingEntry(
        id = "main.root",
        titleRes = R.string.root_title,
        summaryRes = R.string.root_row_description,
        route = SettingRoute(SettingsDestination.Root)
    ),
    SettingEntry(
        id = "root.backlight_screen",
        titleRes = R.string.root_backlight_screen_title,
        summaryRes = R.string.root_backlight_screen_description,
        route = SettingRoute(SettingsDestination.Root)
    ),
    SettingEntry(
        id = "root.backlight_brightness",
        titleRes = R.string.root_backlight_brightness_title,
        summaryRes = R.string.root_backlight_brightness_description,
        route = SettingRoute(SettingsDestination.Root)
    ),
    SettingEntry(
        id = "root.backlight_level",
        titleRes = R.string.root_backlight_level_title,
        summaryRes = R.string.root_backlight_brightness_description,
        route = SettingRoute(SettingsDestination.Root)
    ),
    SettingEntry(
        id = "root.backlight_timeout",
        titleRes = R.string.root_backlight_timeout_title,
        summaryRes = R.string.root_backlight_screen_description,
        route = SettingRoute(SettingsDestination.Root)
    ),
    SettingEntry(
        id = "root.backlight_flash",
        titleRes = R.string.root_backlight_flash_title,
        summaryRes = R.string.root_backlight_flash_description,
        route = SettingRoute(SettingsDestination.Root)
    ),
    SettingEntry(
        id = "trackpad.swipe_learning",
        titleRes = R.string.suggestion_swipe_learning_title,
        summaryRes = R.string.suggestion_swipe_learning_description,
        route = SettingRoute(SettingsDestination.TrackpadGestures)
    ),
    SettingEntry(
        id = "trackpad.swipe_idle",
        titleRes = R.string.suggestion_swipe_idle_title,
        route = SettingRoute(SettingsDestination.TrackpadGestures)
    ),
    SettingEntry(
        id = "trackpad.add_word",
        titleRes = R.string.trackpad_gesture_add_word_title,
        summaryRes = R.string.trackpad_gesture_add_word_description,
        route = SettingRoute(SettingsDestination.TrackpadGestures)
    ),
    SettingEntry(
        id = "trackpad.add_word_full_width",
        titleRes = R.string.trackpad_gesture_add_word_full_width_title,
        summaryRes = R.string.trackpad_gesture_add_word_full_width_description,
        route = SettingRoute(SettingsDestination.TrackpadGestures)
    ),
    SettingEntry(
        id = "trackpad.swipe_to_delete",
        titleRes = R.string.swipe_to_delete_title,
        summaryRes = R.string.swipe_to_delete_description,
        route = SettingRoute(SettingsDestination.TrackpadGestures)
    ),
    SettingEntry(
        id = "trackpad.phone_settings",
        titleRes = R.string.phone_trackpad_settings_title,
        summaryRes = R.string.phone_trackpad_settings_description,
        route = SettingRoute(SettingsDestination.TrackpadGestures),
        availabilityCheck = { _ -> it.palsoftware.pastiera.inputmethod.DeviceSpecific.isTitan2EliteDevice() },
        unavailableFallbackId = "trackpad.suggestion_swipe_directions"
    ),
    SettingEntry(
        id = "trackpad.suggestion_swipe_directions",
        titleRes = R.string.trackpad_swipe_directions_title,
        summaryRes = R.string.trackpad_swipe_directions_description,
        route = SettingRoute(SettingsDestination.TrackpadGestures)
    ),
    SettingEntry(
        id = "trackpad.swipe_to_delete_provider",
        titleRes = R.string.swipe_to_delete_provider_title,
        route = SettingRoute(SettingsDestination.TrackpadGestures)
    )
)
