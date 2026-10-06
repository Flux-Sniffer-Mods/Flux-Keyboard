package it.palsoftware.pastiera

import android.content.Context
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import org.json.JSONArray
import org.json.JSONObject
import it.palsoftware.pastiera.clicks.ClicksButtonBindingTarget
import it.palsoftware.pastiera.clicks.ClicksDesiredButtonBinding
import it.palsoftware.pastiera.clicks.ClicksPowerKeyboardState
import it.palsoftware.pastiera.clicks.ClicksPowerKeyboardStateSnapshot
import it.palsoftware.pastiera.clicks.ClicksPowerKeyboardStateSnapshotCodec
import it.palsoftware.pastiera.SettingsManager.ClicksPowerButtonMode
import it.palsoftware.pastiera.SettingsManager.ClicksOverlappingKeysMode
import it.palsoftware.pastiera.SettingsManager.ClicksNumberRowInputMode

// SettingsManager: the Clicks Power Keyboard: buttons, charging, overlapping keys and the number row. The keys and defaults live in SettingsManager.kt.

fun SettingsManager.getClicksButtonMode(context: Context): ClicksPowerButtonMode =
    ClicksPowerButtonMode.fromPersistedValue(
        getPreferences(context).getString(KEY_CLICKS_BUTTON_MODE, null)
    )

fun SettingsManager.setClicksButtonMode(context: Context, mode: ClicksPowerButtonMode) {
    getPreferences(context).edit().putString(KEY_CLICKS_BUTTON_MODE, mode.persistedValue).apply()
}

fun SettingsManager.getClicksMetaButtonMode(context: Context): ClicksPowerButtonMode =
    ClicksPowerButtonMode.fromPersistedValue(
        getPreferences(context).getString(KEY_CLICKS_META_BUTTON_MODE, null)
    )

fun SettingsManager.setClicksMetaButtonMode(context: Context, mode: ClicksPowerButtonMode) {
    getPreferences(context).edit().putString(KEY_CLICKS_META_BUTTON_MODE, mode.persistedValue).apply()
}

fun SettingsManager.getClicksAltButtonMode(context: Context): ClicksPowerButtonMode =
    ClicksPowerButtonMode.fromPersistedValue(
        getPreferences(context).getString(KEY_CLICKS_ALT_BUTTON_MODE, null)
    )

fun SettingsManager.setClicksAltButtonMode(context: Context, mode: ClicksPowerButtonMode) {
    getPreferences(context).edit().putString(KEY_CLICKS_ALT_BUTTON_MODE, mode.persistedValue).apply()
}

fun SettingsManager.getClicksMicrophoneButtonMode(context: Context): ClicksPowerButtonMode =
    ClicksPowerButtonMode.fromPersistedValue(
        getPreferences(context).getString(KEY_CLICKS_MICROPHONE_BUTTON_MODE, null)
    )

fun SettingsManager.setClicksMicrophoneButtonMode(context: Context, mode: ClicksPowerButtonMode) {
    getPreferences(context).edit()
        .putString(KEY_CLICKS_MICROPHONE_BUTTON_MODE, mode.persistedValue)
        .apply()
}

internal fun SettingsManager.getClicksDesiredButtonBinding(
    context: Context,
    target: ClicksButtonBindingTarget
): ClicksDesiredButtonBinding? {
    val (choiceKey, outputKey) = clicksDesiredButtonBindingKeys(target)
    val preferences = getPreferences(context)
    val choiceId = preferences.getString(choiceKey, null)?.takeIf(String::isNotBlank) ?: return null
    val output = preferences.getString(outputKey, null)?.decodeClicksRemapOutput() ?: return null
    return ClicksDesiredButtonBinding(choiceId, output)
}

internal fun SettingsManager.setClicksDesiredButtonBinding(
    context: Context,
    target: ClicksButtonBindingTarget,
    binding: ClicksDesiredButtonBinding
) {
    require(binding.firmwareOutput.size == 2)
    val (choiceKey, outputKey) = clicksDesiredButtonBindingKeys(target)
    getPreferences(context).edit()
        .putString(choiceKey, binding.choiceId)
        .putString(outputKey, binding.firmwareOutput.encodeClicksRemapOutput())
        .apply()
}

private fun SettingsManager.clicksDesiredButtonBindingKeys(target: ClicksButtonBindingTarget): Pair<String, String> =
    when (target) {
        ClicksButtonBindingTarget.RED ->
            KEY_CLICKS_RED_BUTTON_BINDING_CHOICE to KEY_CLICKS_RED_BUTTON_BINDING_OUTPUT
        ClicksButtonBindingTarget.KEYBOARD ->
            KEY_CLICKS_KEYBOARD_BUTTON_BINDING_CHOICE to KEY_CLICKS_KEYBOARD_BUTTON_BINDING_OUTPUT
        ClicksButtonBindingTarget.MICROPHONE ->
            KEY_CLICKS_MICROPHONE_BUTTON_BINDING_CHOICE to KEY_CLICKS_MICROPHONE_BUTTON_BINDING_OUTPUT
    }

internal fun SettingsManager.applyClicksRecommendedButtonModes(context: Context): Boolean =
    getPreferences(context).edit()
        .putString(KEY_CLICKS_BUTTON_MODE, ClicksPowerButtonMode.QUICK_LAUNCHER.persistedValue)
        .putString(KEY_CLICKS_META_BUTTON_MODE, ClicksPowerButtonMode.QUICK_LAUNCHER.persistedValue)
        .putString(KEY_CLICKS_ALT_BUTTON_MODE, ClicksPowerButtonMode.NATIVE.persistedValue)
        .putString(KEY_CLICKS_MICROPHONE_BUTTON_MODE, ClicksPowerButtonMode.NATIVE.persistedValue)
        .putBoolean(KEY_ALT_CTRL_SPEECH_SHORTCUT, true)
        .commit()

/**
 * The Clicks Power Keyboard's settings apply here: one is connected now or has been before.
 * Phones that never had one don't see its settings.
 */
fun SettingsManager.hasClicksKeyboard(context: Context): Boolean {
    if (getPreferences(context).getBoolean(KEY_CLICKS_KEYBOARD_SEEN, false)) return true
    val connected = runCatching {
        android.view.InputDevice.getDeviceIds().asSequence()
            .mapNotNull(android.view.InputDevice::getDevice)
            .any(it.palsoftware.pastiera.inputmethod.DeviceSpecific::isClicksPowerKeyboard)
    }.getOrDefault(false)
    if (connected) markClicksKeyboardSeen(context)
    return connected
}

fun SettingsManager.markClicksKeyboardSeen(context: Context) {
    val prefs = getPreferences(context)
    if (!prefs.getBoolean(KEY_CLICKS_KEYBOARD_SEEN, false)) {
        prefs.edit().putBoolean(KEY_CLICKS_KEYBOARD_SEEN, true).apply()
    }
}

fun SettingsManager.getClicksCloseInputOnDisconnect(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_CLICKS_CLOSE_INPUT_ON_DISCONNECT,
        DEFAULT_CLICKS_CLOSE_INPUT_ON_DISCONNECT
    )
}

fun SettingsManager.setClicksCloseInputOnDisconnect(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_CLICKS_CLOSE_INPUT_ON_DISCONNECT, enabled)
        .apply()
}

fun SettingsManager.getClicksShowKeyboardOnlyWithTextFocus(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_CLICKS_SHOW_KEYBOARD_ONLY_WITH_TEXT_FOCUS,
        DEFAULT_CLICKS_SHOW_KEYBOARD_ONLY_WITH_TEXT_FOCUS
    )
}

fun SettingsManager.setClicksShowKeyboardOnlyWithTextFocus(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_CLICKS_SHOW_KEYBOARD_ONLY_WITH_TEXT_FOCUS, enabled)
        .apply()
}

fun SettingsManager.hasExplainedClicksBluetoothPermission(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_CLICKS_BLUETOOTH_PERMISSION_EXPLAINED, false)
}

fun SettingsManager.setClicksBluetoothPermissionExplained(context: Context) {
    getPreferences(context).edit()
        .putBoolean(KEY_CLICKS_BLUETOOTH_PERMISSION_EXPLAINED, true)
        .apply()
}

fun SettingsManager.isClicksChargingAutomationEnabled(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_CLICKS_CHARGING_AUTOMATION, false)

fun SettingsManager.setClicksChargingAutomationEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_CLICKS_CHARGING_AUTOMATION, enabled).apply()
}

fun SettingsManager.getClicksChargingStartPercent(context: Context): Int =
    getPreferences(context).getInt(
        KEY_CLICKS_CHARGING_START_PERCENT,
        DEFAULT_CLICKS_CHARGING_START_PERCENT
    ).coerceIn(5, 90)

fun SettingsManager.setClicksChargingStartPercent(context: Context, percent: Int) {
    val start = percent.coerceIn(5, 90)
    val stop = getClicksChargingStopPercent(context).coerceAtLeast(start + 1)
    getPreferences(context).edit()
        .putInt(KEY_CLICKS_CHARGING_START_PERCENT, start)
        .putInt(KEY_CLICKS_CHARGING_STOP_PERCENT, stop.coerceAtMost(95))
        .apply()
}

fun SettingsManager.getClicksChargingStopPercent(context: Context): Int =
    getPreferences(context).getInt(
        KEY_CLICKS_CHARGING_STOP_PERCENT,
        DEFAULT_CLICKS_CHARGING_STOP_PERCENT
    ).coerceIn(6, 95)

fun SettingsManager.setClicksChargingStopPercent(context: Context, percent: Int) {
    val start = getClicksChargingStartPercent(context)
    getPreferences(context).edit()
        .putInt(KEY_CLICKS_CHARGING_STOP_PERCENT, percent.coerceIn(start + 1, 95))
        .apply()
}

fun SettingsManager.getClicksManualChargingUntil(context: Context): Long =
    getPreferences(context).getLong(KEY_CLICKS_MANUAL_CHARGING_UNTIL, 0L)

fun SettingsManager.setClicksManualChargingUntil(context: Context, timestampMillis: Long) {
    getPreferences(context).edit()
        .putLong(KEY_CLICKS_MANUAL_CHARGING_UNTIL, timestampMillis.coerceAtLeast(0L))
        .apply()
}

fun SettingsManager.getClicksOverlappingKeysMode(context: Context): ClicksOverlappingKeysMode {
    val preferences = getPreferences(context)
    if (preferences.contains(KEY_CLICKS_OVERLAPPING_KEYS_MODE)) {
        return ClicksOverlappingKeysMode.fromPersistedValue(
            preferences.getString(KEY_CLICKS_OVERLAPPING_KEYS_MODE, null)
        )
    }
    return if (preferences.getBoolean(KEY_CLICKS_OVERLAPPING_KEYS_ENABLED, false)) {
        ClicksOverlappingKeysMode.ALL_NON_MODIFIERS
    } else {
        ClicksOverlappingKeysMode.OFF
    }
}

fun SettingsManager.setClicksOverlappingKeysMode(context: Context, mode: ClicksOverlappingKeysMode) {
    getPreferences(context).edit()
        .putString(KEY_CLICKS_OVERLAPPING_KEYS_MODE, mode.persistedValue)
        .remove(KEY_CLICKS_OVERLAPPING_KEYS_ENABLED)
        .apply()
}

fun SettingsManager.getClicksNumberRowInputMode(context: Context): ClicksNumberRowInputMode {
    return ClicksNumberRowInputMode.fromPersistedValue(
        getPreferences(context).getString(KEY_CLICKS_NUMBER_ROW_INPUT_MODE, null)
    )
}

fun SettingsManager.setClicksNumberRowInputMode(context: Context, mode: ClicksNumberRowInputMode) {
    getPreferences(context).edit()
        .putString(KEY_CLICKS_NUMBER_ROW_INPUT_MODE, mode.persistedValue)
        .apply()
}

fun SettingsManager.isClicksNumberRowRepeatEnabled(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_CLICKS_NUMBER_ROW_REPEAT_ENABLED, true)

fun SettingsManager.setClicksNumberRowRepeatEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_CLICKS_NUMBER_ROW_REPEAT_ENABLED, enabled)
        .apply()
}

fun SettingsManager.getClicksPowerKeyboardSnapshot(
    context: Context,
    deviceName: String
): ClicksPowerKeyboardStateSnapshot? = readClicksPowerKeyboardSnapshots(context)
    .filter { it.deviceName.equals(deviceName, ignoreCase = true) }
    .maxByOrNull { it.savedAtMillis }

fun SettingsManager.getMostRecentClicksPowerKeyboardSnapshot(context: Context): ClicksPowerKeyboardStateSnapshot? =
    readClicksPowerKeyboardSnapshots(context).maxByOrNull { it.savedAtMillis }

fun SettingsManager.saveClicksPowerKeyboardSnapshot(
    context: Context,
    deviceName: String,
    state: ClicksPowerKeyboardState
) {
    if (!ClicksPowerKeyboardStateSnapshotCodec.hasMeaningfulDeviceData(state)) return
    val snapshot = ClicksPowerKeyboardStateSnapshot(
        deviceName = deviceName,
        state = ClicksPowerKeyboardStateSnapshotCodec.forStorage(state),
        savedAtMillis = System.currentTimeMillis()
    )
    val snapshots = readClicksPowerKeyboardSnapshots(context).toMutableList()
    snapshots.removeAll {
        it.identity == snapshot.identity ||
            (it.deviceName.equals(snapshot.deviceName, ignoreCase = true) &&
                (it.state.serialNumber == null || snapshot.state.serialNumber == null))
    }
    snapshots += snapshot
    val encoded = JSONArray().also { array ->
        snapshots.sortedBy { it.savedAtMillis }.forEach { item ->
            array.put(JSONObject(ClicksPowerKeyboardStateSnapshotCodec.encode(item)))
        }
    }
    getPreferences(context).edit()
        .putString(KEY_CLICKS_POWER_KEYBOARD_SNAPSHOTS, encoded.toString())
        .apply()
}

private fun SettingsManager.readClicksPowerKeyboardSnapshots(context: Context): List<ClicksPowerKeyboardStateSnapshot> {
    val serialized = getPreferences(context)
        .getString(KEY_CLICKS_POWER_KEYBOARD_SNAPSHOTS, null)
        ?: return emptyList()
    return runCatching {
        JSONArray(serialized).let { array ->
            List(array.length()) { index ->
                ClicksPowerKeyboardStateSnapshotCodec.decode(array.getJSONObject(index).toString())
            }.filterNotNull()
        }
    }.getOrDefault(emptyList())
}
