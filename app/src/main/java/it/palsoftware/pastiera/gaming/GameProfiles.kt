package it.palsoftware.pastiera.gaming

import android.content.Context
import it.palsoftware.pastiera.SettingsManager
import org.json.JSONArray
import org.json.JSONObject

/** What a half of the trackpad does in a game. */
enum class TrackpadRole(val label: String) {
    NONE("Nothing"),
    LEFT_STICK("Left stick"),
    RIGHT_STICK("Right stick"),
    MOUSE("Mouse"),
    /** Pushed past the centre: W A S D held, for emulators that bind keys to a stick. */
    WASD_KEYS("Direction keys: W A S D"),
    /** The same with the arrow keys. */
    ARROW_KEYS("Direction keys: arrows")
}

/** How the screen turns while a game runs: one choice per game, fitting its app. */
enum class ScreenMode {
    /** As the app asks (GameNative: its own Portrait mode per game keeps it upright). */
    APP,
    /** Held upright: apps asking for landscape are told no (emulators draw upright gamepads). */
    UPRIGHT,
    /** The app turns sideways whatever it's told (GameHub): a landscape-shaped screen instead, the phone upright. */
    SIDEWAYS;

    companion object {
        /** What suits [packageName] when nothing's been chosen. */
        fun defaultFor(packageName: String?): ScreenMode = when {
            packageName == null -> UPRIGHT
            packageName == GameNativeBridge.PACKAGE -> APP
            packageName.contains("gamehub", true) || packageName == "com.xiaoji.egggame" -> SIDEWAYS
            else -> UPRIGHT
        }
    }
}


/**
 * One game's (or app's) setup: the apps it's for, what each trackpad half does, how the screen
 * turns and how its game starts. Keys always reach the game as they are: each launcher or
 * emulator maps them in its own settings.
 */
data class GameProfile(
    val id: String,
    val name: String,
    val packages: Set<String>,
    val leftHalf: TrackpadRole,
    val rightHalf: TrackpadRole,
    /** Sticks placed on the screen (0 left, 1 right): centre and reach (a share of the width). */
    val stickZones: Map<Int, Triple<Float, Float, Float>> = emptyMap(),
    /** What starts the game itself (an intent URI), for its home screen shortcut. */
    val launch: String? = null,
    /** How the screen turns while it runs. */
    val screen: ScreenMode = ScreenMode.UPRIGHT,
    /** With root: plays as a real controller (its keys its buttons, the trackpad its sticks). */
    val controller: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        launch?.let { put("launch", it) }
        put("screen", screen.name)
        put("controller", controller)
        put("name", name)
        put("packages", JSONArray(packages.toList()))
        put("left", leftHalf.name)
        put("right", rightHalf.name)
        put("stickZones", JSONObject().apply {
            stickZones.forEach { (stick, zone) ->
                put(stick.toString(), JSONArray(listOf(zone.first.toDouble(), zone.second.toDouble(), zone.third.toDouble())))
            }
        })
    }

    companion object {
        fun fromJson(json: JSONObject): GameProfile? = runCatching {
            GameProfile(
                id = json.getString("id"),
                name = json.getString("name"),
                packages = json.optJSONArray("packages")?.let { array -> List(array.length()) { array.getString(it) }.toSet() }.orEmpty(),
                leftHalf = TrackpadRole.entries.firstOrNull { it.name == json.optString("left") } ?: TrackpadRole.NONE,
                rightHalf = TrackpadRole.entries.firstOrNull { it.name == json.optString("right") } ?: TrackpadRole.NONE,
                stickZones = json.optJSONObject("stickZones")?.let { zones ->
                    zones.keys().asSequence().mapNotNull { key ->
                        val z = zones.optJSONArray(key) ?: return@mapNotNull null
                        key.toIntOrNull()?.let { it to Triple(z.getDouble(0).toFloat(), z.getDouble(1).toFloat(), z.getDouble(2).toFloat()) }
                    }.toMap()
                }.orEmpty(),
                launch = json.optString("launch").ifEmpty { null },
                // Before one screen choice: "sideways", and a keep-upright switch for every game
                screen = json.optString("screen").let { name -> ScreenMode.entries.firstOrNull { it.name == name } }
                    ?: if (json.optBoolean("sideways", false)) ScreenMode.SIDEWAYS
                    else ScreenMode.defaultFor(json.optJSONArray("packages")?.optString(0)),
                controller = json.optBoolean("controller", false)
            )
        }.getOrNull()
    }
}

object GameProfiles {
    private const val KEY_PROFILES = "game_profiles"
    private const val KEY_ENABLED = "game_mode_enabled"
    private const val KEY_ACTIVE_PREFIX = "game_mode_active_"

    /**
     * A new profile for [packages]: the trackpad as the game's sticks. GameHub's and GameNative's
     * games move their on-screen sticks; an emulator's get the direction keys (W A S D on the left,
     * the arrows on the right), which it maps like any other key.
     */
    fun newProfile(name: String, packages: Set<String>, controller: Boolean = false): GameProfile {
        val app = packages.firstOrNull()?.let { GameApps.appFor(it) }
        // A real controller, or a launcher's on-screen sticks: the trackpad as sticks
        val touch = controller || app == "GameHub" || app == "GameNative"
        return GameProfile(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            packages = packages,
            leftHalf = if (touch) TrackpadRole.LEFT_STICK else TrackpadRole.WASD_KEYS,
            rightHalf = if (touch) TrackpadRole.RIGHT_STICK else TrackpadRole.ARROW_KEYS,
            screen = ScreenMode.defaultFor(packages.firstOrNull()),
            controller = controller
        )
    }

    private fun prefs(context: Context) = SettingsManager.getPreferences(context)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()


    fun all(context: Context): List<GameProfile> = runCatching {
        val array = JSONArray(prefs(context).getString(KEY_PROFILES, "[]"))
        List(array.length()) { GameProfile.fromJson(array.getJSONObject(it)) }.filterNotNull()
    }.getOrDefault(emptyList())

    private fun saveAll(context: Context, profiles: List<GameProfile>) {
        prefs(context).edit().putString(KEY_PROFILES, JSONArray(profiles.map { it.toJson() }).toString()).apply()
    }

    fun save(context: Context, profile: GameProfile) {
        val profiles = all(context).toMutableList()
        val at = profiles.indexOfFirst { it.id == profile.id }
        if (at >= 0) profiles[at] = profile else profiles += profile
        saveAll(context, profiles)
    }

    fun delete(context: Context, id: String) = saveAll(context, all(context).filterNot { it.id == id })

    /** The profiles for an app; several when it launches several games. */
    fun forPackage(context: Context, packageName: String): List<GameProfile> =
        all(context).filter { profile -> profile.packages.any { covers(it, packageName) } }

    /**
     * A profile's app covers [front] when it's that app, or one of its own parts that runs a game
     * in front under a longer name (a launcher's game player, "launcher.package.player").
     */
    fun covers(app: String, front: String): Boolean = front == app || front.startsWith("$app.")

    /** The app a choice is kept for: the profile's own app, whichever of its parts is in front. */
    private fun owner(context: Context, packageName: String): String =
        all(context).flatMap { it.packages }.filter { covers(it, packageName) }.maxByOrNull { it.length } ?: packageName

    /** The profile in use for an app: the one picked last there, else its first. */
    fun active(context: Context, packageName: String): GameProfile? {
        val profiles = forPackage(context, packageName)
        val chosen = prefs(context).getString(KEY_ACTIVE_PREFIX + owner(context, packageName), null)
        return profiles.firstOrNull { it.id == chosen } ?: profiles.firstOrNull()
    }

    fun setActive(context: Context, packageName: String, id: String) =
        prefs(context).edit().putString(KEY_ACTIVE_PREFIX + owner(context, packageName), id).apply()
}
