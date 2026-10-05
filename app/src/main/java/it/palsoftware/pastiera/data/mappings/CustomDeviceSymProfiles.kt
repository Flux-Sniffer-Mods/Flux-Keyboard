package it.palsoftware.pastiera.data.mappings

import android.content.Context
import android.content.res.AssetManager
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Device SYM profiles you make yourself (Settings > Keyboards & layouts > Hardware keyboard >
 * Device SYM layer editor): a name, the keyboards it applies to, and what each key types on the
 * Device SYM layer (and with Alt, when Alt uses it). Kept in one preference, so Pastiera's backup
 * includes them.
 */
data class CustomDeviceSymProfile(
    val id: String,
    val name: String,
    /** Bundled profile ids (e.g. "titan2") this profile replaces when that keyboard is in use. */
    val matchProfiles: Set<String> = emptySet(),
    val mappings: Map<Int, String> = emptyMap()
) {
    /** The value an Alt binding or a SYM profile choice stores for this profile. */
    val profileRef: String get() = CustomDeviceSymProfiles.REF_PREFIX + id
}

object CustomDeviceSymProfiles {
    const val PREF_KEY = "custom_device_sym_profiles"
    /** The Device SYM layer in use: "auto" (from the keyboard), a curated profile id, or "custom:<id>". */
    const val PREF_CHOICE = "device_sym_profile_choice"
    const val AUTO = "auto"

    fun choice(context: Context): String = prefs(context).getString(PREF_CHOICE, AUTO) ?: AUTO

    fun setChoice(context: Context, choice: String) {
        prefs(context).edit().putString(PREF_CHOICE, choice).apply()
    }
    const val REF_PREFIX = "custom:"

    /** Bundled profiles, in the order the pickers show them. */
    val BUNDLED = listOf("key2", "Q25", "titan", "titan2", "titan2elite_qwerty", "mp01", "clicks_razr", "clicks_pixel", "clicks_power", "virtual")

    /** Keys the editor offers, in keyboard order. */
    val EDITABLE_KEYS: List<Int> =
        listOf(
            KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T,
            KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P,
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G,
            KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
            KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M,
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4,
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9,
            KeyEvent.KEYCODE_GRAVE, KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD
        )

    private fun prefs(context: Context) = it.palsoftware.pastiera.SettingsManager.getPreferences(context)

    fun all(context: Context): List<CustomDeviceSymProfile> =
        parseList(prefs(context).getString(PREF_KEY, null))

    fun get(context: Context, id: String): CustomDeviceSymProfile? = all(context).firstOrNull { it.id == id }

    fun save(context: Context, profile: CustomDeviceSymProfile) {
        val updated = all(context).filterNot { it.id == profile.id } + profile
        prefs(context).edit().putString(PREF_KEY, toJson(updated).toString()).apply()
    }

    fun delete(context: Context, id: String) {
        if (choice(context) == REF_PREFIX + id) setChoice(context, AUTO)
        prefs(context).edit().putString(PREF_KEY, toJson(all(context).filterNot { it.id == id }).toString()).apply()
    }

    fun create(context: Context, name: String, template: Map<Int, String> = emptyMap()): CustomDeviceSymProfile =
        CustomDeviceSymProfile(UUID.randomUUID().toString().take(8), name, mappings = template).also { save(context, it) }

    /** A bundled profile's mappings, to clone. */
    fun bundledMappings(assets: AssetManager, profileId: String): Map<Int, String> =
        runCatching { KeyMappingLoader.loadStringMappings(assets, "devices/$profileId/device_sym_key_mappings.json") }
            .getOrDefault(emptyMap())

    /** The custom profile for [ref] ("custom:<id>"), or null. */
    fun forRef(context: Context, ref: String?): CustomDeviceSymProfile? =
        ref?.takeIf { it.startsWith(REF_PREFIX) }?.let { get(context, it.removePrefix(REF_PREFIX)) }

    /** The first custom profile set to replace [resolvedProfileId], or null. */
    fun matching(context: Context, resolvedProfileId: String): CustomDeviceSymProfile? =
        all(context).firstOrNull { profile -> profile.matchProfiles.any { it.equals(resolvedProfileId, ignoreCase = true) } }

    // The same file format as the bundled profiles, plus the name and matching
    fun toJson(profile: CustomDeviceSymProfile): JSONObject = JSONObject().apply {
        put("id", profile.id)
        put("name", profile.name)
        put("match", JSONArray(profile.matchProfiles.toList()))
        put("mappings", JSONObject().apply {
            profile.mappings.toSortedMap().forEach { (keyCode, text) ->
                put(keyName(keyCode), text)
            }
        })
    }

    private val keyNames by lazy { KeyMappingLoader.keyCodeMap.entries.associate { (name, code) -> code to name } }

    private fun keyName(keyCode: Int): String = keyNames[keyCode] ?: KeyEvent.keyCodeToString(keyCode)

    private fun toJson(list: List<CustomDeviceSymProfile>) = JSONArray().apply { list.forEach { put(toJson(it)) } }

    /** A profile from exported JSON; a new id when [newId], so an import never overwrites. */
    fun fromJson(json: JSONObject, newId: Boolean = false): CustomDeviceSymProfile? {
        val mappingsJson = json.optJSONObject("mappings") ?: return null
        val mappings = mutableMapOf<Int, String>()
        mappingsJson.keys().forEach { name ->
            val keyCode = KeyMappingLoader.keyCodeMap[name] ?: KeyEvent.keyCodeFromString(name)
            val text = mappingsJson.optString(name)
            if (keyCode != KeyEvent.KEYCODE_UNKNOWN && text.isNotEmpty()) mappings[keyCode] = text
        }
        val match = json.optJSONArray("match")?.let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }.orEmpty()
        val id = json.optString("id").takeIf { it.isNotBlank() && !newId } ?: UUID.randomUUID().toString().take(8)
        return CustomDeviceSymProfile(id, json.optString("name").ifBlank { "Imported" }, match, mappings)
    }

    fun parseList(stored: String?): List<CustomDeviceSymProfile> {
        if (stored.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(stored) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { fromJson(it) } }
    }
}
