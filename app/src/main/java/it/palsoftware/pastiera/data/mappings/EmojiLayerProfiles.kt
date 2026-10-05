package it.palsoftware.pastiera.data.mappings

import android.content.Context
import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.shortcuts.AppCategory
import it.palsoftware.pastiera.shortcuts.AppShortcutPresets
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Emoji layer profiles (Settings > Modifiers & SYM > Emoji layer profiles): 26 emoji, one per
 * letter key, for a situation. Built-in ones cover common situations; you can save your own.
 * Applying a profile makes it your emoji layer. With "switch by app", the layer follows the app
 * you're in (chat apps get Chatting, work apps Work, and so on) without changing your saved one.
 */
data class EmojiLayerProfile(
    val id: String,
    val name: String,
    val mappings: Map<Int, String>,
    val builtIn: Boolean = false
)

object EmojiLayerProfiles {
    const val PREF_KEY = "emoji_layer_profiles"
    const val PREF_ACTIVE = "emoji_layer_active_profile"
    const val PREF_SWITCH_BY_APP = "emoji_layer_switch_by_app"

    /** The emoji layer's keys, in keyboard order (Q to P, A to L, Z to M). */
    val KEYS: List<Int> = listOf(
        KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T,
        KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P,
        KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G,
        KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L,
        KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
        KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M
    )

    private fun layer(vararg emoji: String): Map<Int, String> {
        require(emoji.size == KEYS.size) { "An emoji layer has ${KEYS.size} keys" }
        return KEYS.zip(emoji).toMap()
    }

    // The most used emoji of each situation first, on the top row where they're easiest to reach
    val BUILT_IN: List<EmojiLayerProfile> = listOf(
        EmojiLayerProfile("everyday", "Everyday", layer(
            "😀", "😂", "😍", "😊", "😎", "👍", "❤️", "😘", "😡", "😉",
            "😢", "😭", "😱", "😰", "😴", "🤔", "🤢", "🙄", "😌",
            "😔", "🥳", "😅", "🤗", "🥰", "👎", "😳"), builtIn = true),
        EmojiLayerProfile("chatting", "Chatting", layer(
            "😂", "❤️", "👍", "🙏", "😭", "🥺", "🔥", "✨", "😅", "🙌",
            "😊", "😍", "🤣", "😘", "🤔", "👀", "💀", "😎", "🥰",
            "👏", "💯", "🎉", "😢", "😉", "🙄", "🤷"), builtIn = true),
        EmojiLayerProfile("work", "Work", layer(
            "✅", "❌", "👍", "👀", "📅", "📌", "📎", "📝", "💡", "⚠️",
            "🙏", "👋", "☕", "⏰", "📞", "💬", "📊", "📈", "🚀",
            "🔥", "🎯", "🤝", "🔗", "📧", "👏", "🤔"), builtIn = true),
        EmojiLayerProfile("social", "Social media", layer(
            "🔥", "💯", "✨", "😍", "🙌", "💀", "😭", "👀", "🤩", "💅",
            "🥹", "🫶", "😂", "❤️", "📸", "🎥", "🎶", "🌈", "⭐",
            "🤣", "🥳", "👑", "💪", "😎", "🫡", "🤌"), builtIn = true),
        EmojiLayerProfile("love", "Love", layer(
            "❤️", "😍", "😘", "🥰", "💕", "💖", "💘", "💋", "🌹", "💐",
            "😊", "🥺", "🫶", "💞", "💓", "😻", "💑", "💍", "🤗",
            "☺️", "😚", "💌", "🍷", "🌙", "✨", "💝"), builtIn = true),
        EmojiLayerProfile("celebrations", "Celebrations", layer(
            "🎉", "🥳", "🎂", "🎁", "🎈", "🍾", "🥂", "🎊", "✨", "🎆",
            "🙌", "👏", "💃", "🕺", "🎶", "🍰", "🏆", "🥇", "🌟",
            "😄", "🤩", "💐", "🎀", "🍻", "🎇", "🙏"), builtIn = true),
        EmojiLayerProfile("food", "Food and drink", layer(
            "🍕", "🍔", "🍟", "🌮", "🍣", "🍜", "🍝", "🥗", "🍩", "🍪",
            "☕", "🍺", "🍷", "🍹", "🥤", "🍰", "🍫", "🍦", "🍓",
            "🍎", "🥑", "🍳", "🥐", "🧀", "🌶️", "😋"), builtIn = true),
        EmojiLayerProfile("travel", "Travel and outdoors", layer(
            "✈️", "🚗", "🚆", "🏖️", "🏔️", "🗺️", "🧳", "🏨", "📍", "🌍",
            "☀️", "🌧️", "❄️", "🌊", "🏕️", "🚴", "🥾", "📸", "🌅",
            "🚕", "⛽", "🎒", "🏝️", "🌴", "🧭", "⏰"), builtIn = true),
        EmojiLayerProfile("gaming", "Gaming", layer(
            "🎮", "🕹️", "👾", "🏆", "💀", "🔥", "⚔️", "🛡️", "🎯", "💎",
            "😤", "😂", "🤣", "😭", "🙃", "👀", "💪", "🤝", "😈",
            "🐐", "🎲", "🧩", "🏹", "⚡", "🎧", "💯"), builtIn = true)
    )

    fun all(context: Context): List<EmojiLayerProfile> = BUILT_IN + custom(context)

    fun get(context: Context, id: String?): EmojiLayerProfile? = all(context).firstOrNull { it.id == id }

    fun custom(context: Context): List<EmojiLayerProfile> {
        val stored = prefs(context).getString(PREF_KEY, null) ?: return emptyList()
        val array = runCatching { JSONArray(stored) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::fromJson) }
    }

    fun saveCustom(context: Context, profile: EmojiLayerProfile) {
        require(!profile.builtIn)
        val updated = custom(context).filterNot { it.id == profile.id } + profile
        prefs(context).edit().putString(PREF_KEY, JSONArray().apply { updated.forEach { put(toJson(it)) } }.toString()).apply()
    }

    fun deleteCustom(context: Context, id: String) {
        val updated = custom(context).filterNot { it.id == id }
        prefs(context).edit().putString(PREF_KEY, JSONArray().apply { updated.forEach { put(toJson(it)) } }.toString()).apply()
        if (activeId(context) == id) prefs(context).edit().remove(PREF_ACTIVE).apply()
    }

    /** A new profile of your own from [mappings] (e.g. the emoji layer as it is now). */
    fun createCustom(context: Context, name: String, mappings: Map<Int, String>): EmojiLayerProfile =
        EmojiLayerProfile(UUID.randomUUID().toString().take(8), name, mappings.filterKeys { it in KEYS })
            .also { saveCustom(context, it) }

    /**
     * A layer's emoji moved off the keys the Recents, GIF and search buttons take: in order onto
     * the free keys, so the least-used (last) ones drop off instead of hiding under a button.
     */
    fun aroundButtons(mappings: Map<Int, String>, buttonKeys: Set<Int>): Map<Int, String> {
        val reserved = buttonKeys.filter { it in KEYS }.toSet()
        if (reserved.isEmpty()) return mappings
        val emoji = KEYS.mapNotNull { mappings[it] }
        return KEYS.filterNot { it in reserved }.zip(emoji).toMap()
    }

    /** The emoji layer's button keys as set now. */
    fun buttonKeys(context: Context): Set<Int> = setOf(
        SettingsManager.getEmojiLayerRecentsKey(context),
        SettingsManager.activeEmojiLayerGifKey(context),
        SettingsManager.getSearchKey(context)
    ) - KeyEvent.KEYCODE_UNKNOWN

    /** A built-in profile as it goes on the layer, around the buttons; your own stay as saved. */
    fun layerMappings(context: Context, profile: EmojiLayerProfile): Map<Int, String> =
        if (profile.builtIn) aroundButtons(profile.mappings, buttonKeys(context)) else profile.mappings

    /** Makes [profile] your emoji layer. */
    fun apply(context: Context, profile: EmojiLayerProfile) {
        SettingsManager.saveSymMappings(context, layerMappings(context, profile))
        prefs(context).edit().putString(PREF_ACTIVE, profile.id).apply()
    }

    fun activeId(context: Context): String? = prefs(context).getString(PREF_ACTIVE, null)

    fun switchByApp(context: Context): Boolean = prefs(context).getBoolean(PREF_SWITCH_BY_APP, false)

    fun setSwitchByApp(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_SWITCH_BY_APP, enabled).apply()
    }

    /**
     * The built-in profile for the app you're in when switching by app, from its category in the
     * app shortcut list; null keeps your own emoji layer (apps outside these situations).
     */
    fun forApp(packageName: String?): EmojiLayerProfile? {
        // Email is work, though Play files Gmail and Outlook under Communication
        if (packageName in it.palsoftware.pastiera.AppEnterStandards.EMAIL_CTRL_ENTER_APPS) return BUILT_IN.first { it.id == "work" }
        val category = packageName?.let { AppShortcutPresets.forPackage(it)?.category } ?: return null
        val id = when (category) {
            AppCategory.Communication -> "chatting"
            AppCategory.Social -> "social"
            AppCategory.Dating -> "love"
            AppCategory.Productivity, AppCategory.Business -> "work"
            AppCategory.Food -> "food"
            AppCategory.Travel, AppCategory.Maps, AppCategory.Weather -> "travel"
            else -> return null
        }
        return BUILT_IN.first { it.id == id }
    }

    private val keyNames by lazy { KeyMappingLoader.keyCodeMap.entries.associate { (name, code) -> code to name } }

    fun toJson(profile: EmojiLayerProfile): JSONObject = JSONObject().apply {
        put("id", profile.id)
        put("name", profile.name)
        put("mappings", JSONObject().apply {
            KEYS.forEach { key -> profile.mappings[key]?.let { put(keyNames[key] ?: KeyEvent.keyCodeToString(key), it) } }
        })
    }

    fun fromJson(json: JSONObject, newId: Boolean = false): EmojiLayerProfile? {
        val mappingsJson = json.optJSONObject("mappings") ?: return null
        val mappings = mutableMapOf<Int, String>()
        mappingsJson.keys().forEach { name ->
            val key = KeyMappingLoader.keyCodeMap[name] ?: KeyEvent.keyCodeFromString(name)
            val emoji = mappingsJson.optString(name)
            if (key in KEYS && emoji.isNotEmpty()) mappings[key] = emoji
        }
        val id = json.optString("id").takeIf { it.isNotBlank() && !newId } ?: UUID.randomUUID().toString().take(8)
        return EmojiLayerProfile(id, json.optString("name").ifBlank { "Imported" }, mappings)
    }

    private fun prefs(context: Context) = SettingsManager.getPreferences(context)
}
