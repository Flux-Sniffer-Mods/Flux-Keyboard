package it.palsoftware.pastiera.gaming

import it.palsoftware.pastiera.apps.appLabel
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
    /** The half spread over the whole screen, touching where the finger is (Dolphin's Wii pointer, its IR mode on Follow). */
    POINTER("Pointer (touch)"),
    /**
     * Spread over a DS or 3DS's bottom screen (the area placed for it), touching where the finger
     * is: the stylus. Both halves set to it: the whole trackpad.
     */
    BOTTOM_SCREEN("Bottom screen (touch)"),
    /** Pushed past the centre: W A S D held, for emulators that bind keys to a stick. */
    WASD_KEYS("Direction keys: W A S D"),
    /** The same with the arrow keys. */
    ARROW_KEYS("Direction keys: arrows")
}

/** How the screen turns while a game runs: one choice per game, fitting its app. */
enum class ScreenMode {
    /** As the app asks (GameNative: its own Portrait mode per game keeps it upright). */
    APP,
    /** Kept upright: a landscape-shaped resolution forced, so the game draws sideways with the phone upright. */
    SIDEWAYS,
    /** Kept upright, drawn upright too: a DS or 3DS emulator's screens one above the other. */
    UPRIGHT;

    companion object {
        /** What suits [packageName] when nothing's been chosen: kept upright for game apps (drawn upright for a DS or 3DS). */
        fun defaultFor(packageName: String?): ScreenMode = when {
            packageName == null -> APP
            GameApps.twoScreens(packageName) != null -> UPRIGHT
            GameApps.appFor(packageName) != null -> SIDEWAYS
            else -> APP
        }
    }
}


/**
 * One game's (or app's) setup: the apps it's for, what each trackpad half does, how the screen
 * turns and how its game starts. Keys reach the game as they are (each launcher or emulator maps
 * them in its own settings), except keys placed on the screen, which tap there.
 */
data class GameProfile(
    val id: String,
    val name: String,
    val packages: Set<String>,
    val leftHalf: TrackpadRole,
    val rightHalf: TrackpadRole,
    /** Sticks placed on the screen (0 left, 1 right): centre and reach (a share of the width). */
    val stickZones: Map<Int, Triple<Float, Float, Float>> = emptyMap(),
    /**
     * Keys placed on the screen, for games with only touch controls: each taps its spot (a share
     * of the screen's width and height) while held.
     */
    val taps: Map<Int, Pair<Float, Float>> = emptyMap(),
    /** What starts the game itself (an intent URI), for its home screen shortcut. */
    val launch: String? = null,
    /** How the screen turns while it runs. */
    val screen: ScreenMode = ScreenMode.SIDEWAYS,
    /**
     * J B N M move the right stick, the trackpad's right half then no stick of its own: the
     * Titan's trackpad follows one finger at a time, so two sticks on it don't move together.
     */
    val rightStickKeys: Boolean = false,
    /** With root: plays as a real controller (its keys its buttons, the trackpad its sticks). */
    val controller: Boolean = false,
    /**
     * A game's own settings (trackpad, screen, sticks, controller); without, it plays with its
     * app's. App profiles (no [launch]) always have their own.
     */
    val own: Boolean = false,
    /** Found by itself (the game folders), not set up yet: asked about the first time it starts. */
    val pending: Boolean = false,
    /** A game's own screen choice, kept even while it plays with its app's other settings. */
    val ownScreen: Boolean = false,
    /**
     * Sticks and keys placed for streaming (the screen in the Moonlight client's shape), apart
     * from the phone's own; empty: the phone's are used.
     */
    val streamStickZones: Map<Int, Triple<Float, Float, Float>> = emptyMap(),
    val streamTaps: Map<Int, Pair<Float, Float>> = emptyMap(),
    /** The height to draw at on the phone, kept upright (its shape kept); null: the screen's own. */
    val phoneHeight: Int? = null,
    /** The height to draw at while streaming; null: streaming's own choice. */
    val streamHeight: Int? = null,
    /** A Dolphin Wii game's controller ([FluxLayout.WiiPad]'s name; null: Wii Remote and Nunchuk). */
    val wiiPad: String? = null,
    /** Dolphin's two app profiles: "wii" or "gamecube", each game getting the one it is. */
    val variant: String? = null,
    /**
     * Each placed stick's dead zone (the share of a push ignored, 0 to 0.5) and range (how far
     * a full push reaches, a share of its circle); missing: [DEFAULT_TUNING].
     */
    val stickTuning: Map<Int, Pair<Float, Float>> = emptyMap(),
    /**
     * Where a DS or 3DS's bottom screen is (left, top, right, bottom, shares of the screen), for
     * the trackpad's [TrackpadRole.BOTTOM_SCREEN]; null: where its emulator draws it by default.
     */
    val touchArea: List<Float>? = null,
    /**
     * A and B, and X and Y, the other way round (Nintendo's), so the key where a button sits
     * presses it; null: on for Nintendo emulators.
     */
    val swapFace: Boolean? = null,
    /**
     * Dolphin: the game's own keys for each controller ([FluxLayout.WiiPad]'s name to
     * [DolphinPads.format]'s text); a controller missing has the layout's.
     */
    val padControls: Map<String, String> = emptyMap(),
    /**
     * Keys changed from the keyboard layout for this game or app alone ([FluxLayout.formatOverrides]'s
     * text, on top of the layout as changed in Keyboard controls); null: none of its own.
     */
    val keyOverrides: String? = null,
    /** Dolphin: its on-screen controls for this game ([FluxLayout.DolphinOverlay]'s name); null: as suits it. */
    val dolphinOverlay: String? = null,
    /** Dolphin: a profile chosen for the game in Dolphin itself kept, rather than Flux Keyboard's controls. */
    val keepDolphinProfile: Boolean = false
) {
    /**
     * Dolphin's on-screen controls for this game, as chosen; else invisible when sticks or keys
     * are placed on them (they still take the touches), otherwise off.
     */
    val overlayMode: FluxLayout.DolphinOverlay
        get() = FluxLayout.DolphinOverlay.entries.firstOrNull { it.name == dolphinOverlay }
            ?: if (stickZones.isNotEmpty() || taps.isNotEmpty()) FluxLayout.DolphinOverlay.INVISIBLE else FluxLayout.DolphinOverlay.OFF

    /** The game's own keys for Dolphin's [pad] (empty: the layout's). */
    fun padRemap(pad: FluxLayout.WiiPad): Map<String, List<DolphinPads.Binding>> = DolphinPads.parse(padControls[pad.name])
    /** Whether A and B (X and Y) are swapped, as set or as suits its app. */
    val swapsFace: Boolean get() = swapFace ?: packages.any { GameApps.nintendo(it) }

    /** A stick's dead zone and range. */
    fun tuning(stick: Int): Pair<Float, Float> = stickTuning[stick] ?: DEFAULT_TUNING

    /** A game of its app (it starts one game), rather than the app's own settings. */
    val isGame: Boolean get() = launch != null

    /** The sticks and keys placed for the screen in use: streaming's when streaming and placed. */
    fun forScreen(streaming: Boolean): GameProfile =
        if (streaming && (streamStickZones.isNotEmpty() || streamTaps.isNotEmpty())) copy(stickZones = streamStickZones, taps = streamTaps)
        else this

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        launch?.let { put("launch", it) }
        put("screen", screen.name)
        put("controller", controller)
        put("rightStickKeys", rightStickKeys)
        put("own", own)
        variant?.let { put("variant", it) }
        wiiPad?.let { put("wiiPad", it) }
        if (padControls.isNotEmpty()) put("padControls", JSONObject(padControls))
        keyOverrides?.let { put("keyOverrides", it) }
        dolphinOverlay?.let { put("dolphinOverlay", it) }
        if (keepDolphinProfile) put("keepDolphinProfile", true)
        if (pending) put("pending", true)
        if (ownScreen) put("ownScreen", true)
        phoneHeight?.let { put("phoneHeight", it) }
        streamHeight?.let { put("streamHeight", it) }
        put("name", name)
        put("packages", JSONArray(packages.toList()))
        put("left", leftHalf.name)
        put("right", rightHalf.name)
        put("stickZones", zonesJson(stickZones))
        put("taps", tapsJson(taps))
        if (streamStickZones.isNotEmpty()) put("streamStickZones", zonesJson(streamStickZones))
        if (streamTaps.isNotEmpty()) put("streamTaps", tapsJson(streamTaps))
        touchArea?.let { put("touchArea", JSONArray(it.map { v -> v.toDouble() })) }
        swapFace?.let { put("swapFace", it) }
        if (stickTuning.isNotEmpty()) put("stickTuning", JSONObject().apply {
            stickTuning.forEach { (stick, t) -> put(stick.toString(), JSONArray(listOf(t.first.toDouble(), t.second.toDouble()))) }
        })
    }

    companion object {
        /** A stick's dead zone and range unless set: a little ignored around the middle, full reach at the circle's edge. */
        val DEFAULT_TUNING = 0.08f to 1f

        private fun zonesJson(zones: Map<Int, Triple<Float, Float, Float>>) = JSONObject().apply {
            zones.forEach { (stick, zone) ->
                put(stick.toString(), JSONArray(listOf(zone.first.toDouble(), zone.second.toDouble(), zone.third.toDouble())))
            }
        }

        private fun tapsJson(taps: Map<Int, Pair<Float, Float>>) = JSONObject().apply {
            taps.forEach { (key, at) -> put(key.toString(), JSONArray(listOf(at.first.toDouble(), at.second.toDouble()))) }
        }

        private fun zonesFrom(zones: JSONObject?): Map<Int, Triple<Float, Float, Float>> = zones?.let {
            it.keys().asSequence().mapNotNull { key ->
                val z = it.optJSONArray(key) ?: return@mapNotNull null
                key.toIntOrNull()?.let { stick -> stick to Triple(z.getDouble(0).toFloat(), z.getDouble(1).toFloat(), z.getDouble(2).toFloat()) }
            }.toMap()
        }.orEmpty()

        private fun tapsFrom(taps: JSONObject?): Map<Int, Pair<Float, Float>> = taps?.let {
            it.keys().asSequence().mapNotNull { key ->
                val at = it.optJSONArray(key) ?: return@mapNotNull null
                key.toIntOrNull()?.let { code -> code to Pair(at.getDouble(0).toFloat(), at.getDouble(1).toFloat()) }
            }.toMap()
        }.orEmpty()

        fun fromJson(json: JSONObject): GameProfile? = runCatching {
            GameProfile(
                id = json.getString("id"),
                name = json.getString("name"),
                packages = json.optJSONArray("packages")?.let { array -> List(array.length()) { array.getString(it) }.toSet() }.orEmpty(),
                leftHalf = TrackpadRole.entries.firstOrNull { it.name == json.optString("left") } ?: TrackpadRole.NONE,
                rightHalf = TrackpadRole.entries.firstOrNull { it.name == json.optString("right") } ?: TrackpadRole.NONE,
                stickZones = zonesFrom(json.optJSONObject("stickZones")),
                taps = tapsFrom(json.optJSONObject("taps")),
                streamStickZones = zonesFrom(json.optJSONObject("streamStickZones")),
                streamTaps = tapsFrom(json.optJSONObject("streamTaps")),
                stickTuning = tapsFrom(json.optJSONObject("stickTuning")),
                swapFace = if (json.has("swapFace")) json.optBoolean("swapFace") else null,
                touchArea = json.optJSONArray("touchArea")?.let { array -> (0 until array.length()).map { array.optDouble(it).toFloat() } }
                    ?.takeIf { it.size == 4 && it.none { v -> v.isNaN() } },
                launch = json.optString("launch").ifEmpty { null },
                // Before one screen choice: "sideways", and a keep-upright switch for every game
                screen = json.optString("screen").let { name -> ScreenMode.entries.firstOrNull { it.name == name } }
                    ?: if (json.optBoolean("sideways", false)) ScreenMode.SIDEWAYS
                    else ScreenMode.defaultFor(json.optJSONArray("packages")?.optString(0)),
                controller = json.optBoolean("controller", false),
                rightStickKeys = json.optBoolean("rightStickKeys", false),
                // Games made before apps had settings of their own keep theirs
                own = json.optBoolean("own", true),
                variant = json.optString("variant").ifEmpty { null },
                wiiPad = json.optString("wiiPad").ifEmpty { null },
                keyOverrides = json.optString("keyOverrides").ifEmpty { null },
                dolphinOverlay = json.optString("dolphinOverlay").ifEmpty { null },
                keepDolphinProfile = json.optBoolean("keepDolphinProfile", false),
                padControls = json.optJSONObject("padControls")?.let { o -> o.keys().asSequence().associateWith { o.optString(it) } }
                    ?.filterValues { it.isNotEmpty() }.orEmpty(),
                pending = json.optBoolean("pending", false),
                ownScreen = json.optBoolean("ownScreen", false),
                phoneHeight = json.optInt("phoneHeight", 0).takeIf { it > 0 },
                streamHeight = json.optInt("streamHeight", 0).takeIf { it > 0 }
            )
        }.getOrNull()
    }
}

object GameProfiles {
    private const val KEY_PROFILES = "game_profiles"
    private const val KEY_ENABLED = "game_mode_enabled"
    private const val KEY_ACTIVE_PREFIX = "game_mode_active_"

    /** A new profile for [packages]: the keys play as a controller, the trackpad unused. */
    fun newProfile(name: String, packages: Set<String>): GameProfile {
        // The keys play as a controller ([FluxLayout]); the trackpad is left alone unless chosen
        return GameProfile(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            packages = packages,
            leftHalf = TrackpadRole.NONE,
            rightHalf = TrackpadRole.NONE,
            screen = ScreenMode.defaultFor(packages.firstOrNull())
        )
    }

    private fun prefs(context: Context) = SettingsManager.getPreferences(context)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        if (!on) GameMode.profilesChanged(context)
    }


    fun all(context: Context): List<GameProfile> = runCatching {
        val array = JSONArray(prefs(context).getString(KEY_PROFILES, "[]"))
        val profiles = List(array.length()) { GameProfile.fromJson(array.getJSONObject(it)) }.filterNotNull()
        keysAsSticks(context, profiles)
    }.getOrDefault(emptyList())

    private const val KEY_KEYS_AS_STICKS = "game_mode_keys_as_sticks"

    /**
     * Once, from the update where the keys became the sticks: the trackpad's sticks (and
     * direction keys) set before are switched off. Its pointer and mouse stay (the Wii's pointer).
     */
    private fun keysAsSticks(context: Context, profiles: List<GameProfile>): List<GameProfile> {
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_KEYS_AS_STICKS, false)) return profiles
        fun off(role: TrackpadRole) = if (role == TrackpadRole.POINTER || role == TrackpadRole.MOUSE) role else TrackpadRole.NONE
        val updated = profiles.map { it.copy(leftHalf = off(it.leftHalf), rightHalf = off(it.rightHalf), controller = false, rightStickKeys = false) }
        prefs.edit().putString(KEY_PROFILES, JSONArray(updated.map { it.toJson() }).toString()).putBoolean(KEY_KEYS_AS_STICKS, true).apply()
        return updated
    }

    private fun saveAll(context: Context, profiles: List<GameProfile>) {
        prefs(context).edit().putString(KEY_PROFILES, JSONArray(profiles.map { it.toJson() }).toString()).apply()
        // The Games app's long-press menu kept in step
        if (GamesActivity.enabled(context)) context.applicationContext.let { app -> Thread { GamesActivity.sync(app) }.start() }
    }

    fun save(context: Context, profile: GameProfile) {
        val profiles = all(context).toMutableList()
        val at = profiles.indexOfFirst { it.id == profile.id }
        if (at >= 0) profiles[at] = profile else profiles += profile
        saveAll(context, profiles)
    }

    fun delete(context: Context, id: String) {
        saveAll(context, all(context).filterNot { it.id == id })
        // The one in use deleted: gaming mode moves on rather than keep it
        GameMode.profilesChanged(context)
    }

    /** The profiles for an app; several when it launches several games. */
    /** The profiles gaming mode plays [packageName] with: found games not set up yet aren't among them. */
    fun forPackage(context: Context, packageName: String): List<GameProfile> =
        all(context).filter { profile -> !profile.pending && profile.packages.any { covers(it, packageName) } }

    /**
     * A profile's app covers [front] when it's that app, or one of its own parts that runs a game
     * in front under a longer name (a launcher's game player, "launcher.package.player").
     */
    fun covers(app: String, front: String): Boolean = front == app || front.startsWith("$app.")

    /** The app a choice is kept for: the profile's own app, whichever of its parts is in front. */
    private fun owner(context: Context, packageName: String): String =
        all(context).flatMap { it.packages }.filter { covers(it, packageName) }.maxByOrNull { it.length } ?: packageName

    /** The profile in use for an app: the one picked last there, else the app's own, else its first. */
    fun active(context: Context, packageName: String): GameProfile? {
        val profiles = forPackage(context, packageName)
        val chosen = prefs(context).getString(KEY_ACTIVE_PREFIX + owner(context, packageName), null)
        return profiles.firstOrNull { it.id == chosen } ?: profiles.firstOrNull { !it.isGame } ?: profiles.firstOrNull()
    }

    /** An app's own settings: one profile, or Dolphin's two (GameCube and Wii). */
    fun appProfiles(context: Context, packageName: String): List<GameProfile> =
        forPackage(context, packageName).filter { !it.isGame }

    /**
     * The app profiles for [packageName] named [name], made if it has none: Dolphin gets a
     * GameCube and a Wii one, since each controller wants its own trackpad. Returns them all.
     */
    fun ensureApp(context: Context, packageName: String, name: String): List<GameProfile> {
        appProfiles(context, packageName).takeIf { it.isNotEmpty() }?.let { return it }
        val made = if (GameApps.appFor(packageName) == "Dolphin") listOf(
            newProfile("$name (GameCube)", setOf(packageName)).copy(variant = VARIANT_GAMECUBE),
            // The right half the Wii pointer
            newProfile("$name (Wii)", setOf(packageName)).copy(variant = VARIANT_WII, rightHalf = TrackpadRole.POINTER)
        ) else listOf(newProfile(name, setOf(packageName)))
        made.forEach { save(context, it) }
        return made
    }

    const val VARIANT_GAMECUBE = "gamecube"
    const val VARIANT_WII = "wii"

    /**
     * What [profile] plays with: a game with settings of its own, its own; else its app's (for
     * Dolphin, the Wii or GameCube one its file is). Blocking: may read the game's file.
     */
    fun effective(context: Context, profile: GameProfile): GameProfile {
        if (!profile.isGame || profile.own) return profile
        // The game's own screen choice, whatever else comes from its app
        return appSettings(context, profile).let { app ->
            // The game's own screen and resolutions, whatever else comes from its app
            (if (profile.ownScreen) app.copy(screen = profile.screen) else app)
                .copy(phoneHeight = profile.phoneHeight ?: app.phoneHeight, streamHeight = profile.streamHeight ?: app.streamHeight,
                    // The game's own keys, whatever else comes from its app
                    keyOverrides = profile.keyOverrides ?: app.keyOverrides)
        }
    }

    private fun appSettings(context: Context, profile: GameProfile): GameProfile {
        val pkg = profile.packages.firstOrNull() ?: return profile
        val apps = appProfiles(context, pkg).ifEmpty { return profile }
        if (apps.size == 1) return apps.first()
        // A Wii game given a GameCube controller plays with the GameCube settings
        val wii = profile.wiiPad != FluxLayout.WiiPad.GAMECUBE.name &&
            profile.launch?.let { GameFiles.gameFile(it) }?.let { GameFiles.isWii(it) } == true
        return apps.firstOrNull { it.variant == if (wii) VARIANT_WII else VARIANT_GAMECUBE } ?: apps.first()
    }

    /**
     * Games found by themselves (the game folders, a launcher's list), each added as a game
     * that isn't set up yet: playing with its app's settings, asked about when it first starts.
     * Ones already there (by what starts them) are left alone. Its app's settings aren't made
     * until it's set up.
     */
    fun addDetected(context: Context, games: List<GameLibrary.Game>) {
        val profiles = all(context)
        val known = profiles.mapNotNull { it.launch }.toMutableSet()
        val added = games.mapNotNull { game ->
            val launch = game.launch ?: GameLibrary.launchIntent(game)?.toUri(android.content.Intent.URI_INTENT_SCHEME) ?: return@mapNotNull null
            if (!known.add(launch)) return@mapNotNull null
            newProfile(game.name, setOf(game.packageName)).copy(launch = launch, own = false, pending = true)
        }
        if (added.isNotEmpty()) saveAll(context, profiles + added)
    }

    /**
     * The game [game] picked to add: the one found already, else a new one (not saved yet),
     * playing with its app's settings, made for the app if it has none (Flux Keyboard's own
     * player has none).
     */
    fun profileFor(context: Context, game: GameLibrary.Game): GameProfile {
        if (game.packageName != context.packageName) {
            val label = context.appLabel(game.packageName)
            ensureApp(context, game.packageName, label)
        }
        val launch = game.launch ?: GameLibrary.launchIntent(game)?.toUri(android.content.Intent.URI_INTENT_SCHEME)
        return all(context).firstOrNull { it.isGame && it.launch == launch }
            ?: newProfile(game.name, setOf(game.packageName))
                .copy(launch = launch, own = false)
    }

    /** A found game set up (or played as it is): no longer asked about, its app's settings made. */
    fun confirm(context: Context, profile: GameProfile): GameProfile {
        // A launcher's or emulator's game plays with its app's settings, made now; a game that
        // is an app of its own (from the Play Store) has its own (Flux Keyboard's player has none)
        val pkg = profile.packages.firstOrNull()?.takeIf { it != context.packageName }
        val player = pkg?.let { GameApps.appFor(it) }
        if (pkg != null && player != null) {
            val label = context.appLabel(pkg)
            ensureApp(context, pkg, label)
        }
        return profile.copy(pending = false, own = profile.own || (pkg != null && player == null)).also { save(context, it) }
    }

    /**
     * Emulator games still named by their file: named by their title instead, where their game ID
     * can be read (or a title found in the file's name). Blocking (reads files through the
     * shell); true when any was renamed.
     */
    fun retitle(context: Context): Boolean {
        var renamed = false
        for (profile in all(context).filter { it.isGame }) {
            val emulator = profile.packages.firstNotNullOfOrNull { GameApps.emulator(it) } ?: continue
            val path = profile.launch?.let { GameFiles.gameFile(it) } ?: continue
            val file = path.substringAfterLast('/').substringBeforeLast('.')
            if (!profile.name.equals(file, ignoreCase = true)) continue
            val title = GameIds.title(context, emulator, path)?.takeIf { it.isNotBlank() && it != profile.name } ?: continue
            save(context, profile.copy(name = title))
            renamed = true
        }
        return renamed
    }

    fun setActive(context: Context, packageName: String, id: String) =
        prefs(context).edit().putString(KEY_ACTIVE_PREFIX + owner(context, packageName), id).apply()
}
