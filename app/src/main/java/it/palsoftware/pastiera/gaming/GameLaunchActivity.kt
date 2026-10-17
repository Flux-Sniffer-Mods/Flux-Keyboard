package it.palsoftware.pastiera.gaming

import it.palsoftware.pastiera.apps.appLabelOrNull
import android.app.Activity
import it.palsoftware.pastiera.FluxLog
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle

/**
 * A game's home screen shortcut: makes its profile the one gaming mode uses for its launcher,
 * then starts the game (or, when the launcher can't be told which game, the launcher). A game
 * from the quick launcher may have no profile of its own: its app's settings play it.
 */
class GameLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Made again (the screen changed as the game's size was set): the first one starts the
        // game, so this one doesn't start it a second time
        if (savedInstanceState != null) {
            FluxLog.w(TAG, "launch screen made again: not started twice")
            finish()
            return
        }
        val launchUri = intent.getStringExtra(EXTRA_LAUNCH)
        val profile = intent.getStringExtra(EXTRA_PROFILE)?.let { id -> GameProfiles.all(this).firstOrNull { it.id == id } }
            ?: launchUri?.let { uri -> GameProfiles.all(this).firstOrNull { it.launch == uri } }
        val packages = profile?.packages ?: setOfNotNull(intent.getStringExtra(EXTRA_PACKAGE))
        val launch = profile?.launch ?: launchUri
        if (packages.isEmpty()) {
            finish()
            return
        }
        proceed(profile, packages, launch,
            tvAsked = intent.getBooleanExtra(EXTRA_TV_ASKED, false), asked = intent.getBooleanExtra(EXTRA_ASKED, false))
    }

    /**
     * The game started, after the questions it needs: on the TV or the phone while streaming, and
     * set up or played as it is the first time. Each answer carries on here, in this same screen
     * (starting the screen again could start the game twice, or not at all).
     */
    private fun proceed(profile: GameProfile?, packages: Set<String>, launch: String?, tvAsked: Boolean, asked: Boolean) {
        // Streaming, with a Moonlight client set up: played there (turned on, Moonlight opened into
        // the game) or on the phone, as asked (not when the stream itself opens it)
        if (!tvAsked && it.palsoftware.pastiera.streaming.TvPrompt.applies(this, profile)) {
            it.palsoftware.pastiera.streaming.TvPrompt.open(this, profile,
                onPhone = {
                    FluxLog.w(TAG, "${profile?.name}: played on the phone, not the Moonlight client")
                    proceed(profile, packages, launch, tvAsked = true, asked = asked)
                },
                onTv = { profile?.let { p -> p.packages.forEach { GameProfiles.setActive(this, it, p.id) } }; finish() },
                onCancel = { finish() })
            return
        }
        // A game found by itself, starting for the first time: set it up, or play it as it is
        if (profile != null && profile.pending && !asked) {
            ask(profile) { confirmed -> proceed(confirmed, packages, launch, tvAsked = true, asked = true) }
            return
        }
        profile?.let { p -> p.packages.forEach { GameProfiles.setActive(this, it, p.id) } }
        val parsed = launch?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
        val game = parsed
            ?.takeIf { (it.`package` ?: it.component?.packageName) in packages }
            // An activity that isn't there (another GameHub build): the app itself instead
            ?.takeIf { packageManager.resolveActivity(it, 0) != null }
            ?.let { withGameFile(withGameHubIds(it)) }
        if (launch != null && game == null) FluxLog.w(TAG, when {
            parsed == null -> "game's launch couldn't be read: $launch"
            (parsed.`package` ?: parsed.component?.packageName) !in packages -> "game's launch is for another app ($packages): $launch"
            else -> "game's activity isn't there (opening the app instead): $launch"
        })
        val app = packages.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        val emulatorGame = game != null && (game.data != null || game.hasExtra("AutoStartFile"))
        val pkg = game?.`package` ?: game?.component?.packageName
        // (Flux Keyboard's own player isn't one: never stopped)
        if (emulatorGame && pkg != null && pkg != packageName && it.palsoftware.pastiera.adb.AdbShell.available()) {
            // An emulator only opens the game it's given when it starts afresh (Dolphin reads it
            // as its screen is made, PPSSPP ignores a new one while running): stopped first
            Thread {
                it.palsoftware.pastiera.adb.AdbShell.run("am force-stop $pkg", 3_000)
                // Dolphin's on-screen controls set for gaming mode while it's stopped (a game's own:
                // off, invisible or shown, as set for it), kept on for the trackpad's Wii pointer
                if (GameApps.emulator(pkg) == "Dolphin") runCatching {
                    FluxLayout.dolphinDir(pkg)?.let { dir ->
                        val played = profile?.takeIf { it.isGame }?.let { game -> GameProfiles.effective(this, game).copy(dolphinOverlay = game.dolphinOverlay) }
                        FluxLayout.dolphinOverlay(dir, played?.overlayMode, pointer = played?.pointer == true, context = this)
                        FluxLog.w(TAG, "${played?.name}: Dolphin's on-screen controls ${played?.overlayMode?.name?.lowercase() ?: "unchanged"}" +
                            if (played?.pointer == true) ", the trackpad its pointer" else "")
                    }
                }
                // A DS or 3DS game's screens, as set for it (Azahar reads them as the game starts)
                if (DualScreens.layouts(pkg).isNotEmpty()) runCatching {
                    val effective = profile?.let { GameProfiles.effective(this, it) }
                    DualScreens.layout(effective?.screens)?.let { layout ->
                        val window = DualScreens.window(this, layout.landscape, effective?.phoneHeight)
                        val ok = window != null && DualScreens.apply(pkg, layout, window)
                        FluxLog.w("FluxGameLaunch", "screens $layout for a ${window?.first}x${window?.second} screen: ${if (ok) "written" else "settings file not found"}")
                    }
                }
                // Its controls given the current layout, if they had an older one
                runCatching { FluxLayout.refreshIfOutdated(this, pkg) }
                // What Dolphin was given, for the crash log
                if (GameApps.emulator(pkg) == "Dolphin") runCatching {
                    FluxLayout.dolphinDir(pkg)?.let { dir ->
                        val lines = it.palsoftware.pastiera.adb.AdbShell.run(
                            "cd \"$dir/Config\" && grep -H -E '^\\[|Device|Source|Buttons/A |Main Stick/Up|Triggers/L|Nunchuk/Stick/Up|PadType|WiimoteSource|PadProfile' GCPadNew.ini WiimoteNew.ini Dolphin.ini 2>/dev/null | head -n 40", 3_000)
                        FluxLog.w("FluxGameLaunch", "Dolphin's controls: " + lines.orEmpty().replace("\n", " | "))
                    }
                }
                // The game's own controller (a GameCube controller for a Wii game, say) and its
                // own keys, set again as it starts
                if (GameApps.emulator(pkg) == "Dolphin") runCatching {
                    val path = launch?.let { GameFiles.gameFile(it) }
                    val id = path?.let { GameIds.id(this, "Dolphin", it) }
                    val wii = path?.let { GameFiles.isWii(it) } == true
                    val pad = profile?.wiiPad?.let { name -> FluxLayout.WiiPad.entries.firstOrNull { it.name == name } }
                        ?: if (wii) FluxLayout.WiiPad.NUNCHUK else FluxLayout.WiiPad.GAMECUBE
                    val remap = profile?.padRemap(pad).orEmpty()
                    val result = if (id != null) FluxLayout.setGamePad(this, pkg, id, wii, pad, remap, keepChosen = profile?.keepDolphinProfile == true) else null
                    val ini = id?.let { gameId -> FluxLayout.dolphinDir(pkg)?.let { dir ->
                        it.palsoftware.pastiera.adb.AdbShell.run("cat \"$dir/GameSettings/$gameId.ini\" 2>&1 | head -n 20", 3_000)
                    } }
                    FluxLog.w("FluxGameLaunch", "controller $pad (${remap.size} changed) for game ${id ?: "(no ID read from $path)"}: $result; its Dolphin settings: " +
                        ini.orEmpty().replace("\n", " | "))
                }
                runOnUiThread { launch(profile, game, app) }
            }.start()
        } else {
            launch(profile, game, app)
        }
    }

    /** Set up first (Games, at its settings), or played with its app's settings from now on. */
    private fun ask(profile: GameProfile, onPlay: (GameProfile) -> Unit) {
        val app = profile.packages.firstOrNull()?.let { pkg ->
            appLabelOrNull(pkg)
        } ?: ""
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(it.palsoftware.pastiera.R.string.game_first_start_title, profile.name))
            .setMessage(getString(it.palsoftware.pastiera.R.string.game_first_start_message, app))
            .setPositiveButton(it.palsoftware.pastiera.R.string.game_first_start_set_up) { _, _ ->
                startActivity(GameEditActivity.forProfile(this, profile.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }
            .setNegativeButton(it.palsoftware.pastiera.R.string.game_first_start_play) { _, _ ->
                onPlay(GameProfiles.confirm(this, profile))
            }
            .setOnCancelListener { finish() }
            .show()
    }

    /** The game, or the app when the game can't be started. */
    /** Started once its screen is made (a launcher resized as it opens a game closes it), else straight on. */
    private fun launch(profile: GameProfile?, game: Intent?, app: Intent?) {
        val pkg = (game ?: app)?.let { it.component?.packageName ?: it.`package` }
        if (profile == null || pkg == null || pkg == packageName) start(game, app)
        else GameMode.prepareLaunch(this, profile, pkg) { start(game, app) }
    }

    private fun start(game: Intent?, app: Intent?) {
        val started = game?.let {
            runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onSuccess { _ -> FluxLog.w(TAG, "game started: ${it.toUri(Intent.URI_INTENT_SCHEME)}") }
                .onFailure { e -> FluxLog.w(TAG, "game couldn't start (opening the app instead): $e") }.isSuccess
        } == true
        if (!started) app?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        finish()
    }

    /** A game file you picked yourself: the emulator may read it, as Flux Keyboard may. */
    /**
     * A GameHub Lite game saved before its Steam ID was also sent as its local game ID: given
     * both, as ES-DE sends them (with the Steam one alone, GameHub Lite stops at the game's page).
     */
    private fun withGameHubIds(intent: Intent): Intent {
        if (intent.action != "gamehub.lite.LAUNCH_GAME" || intent.hasExtra("localGameId")) return intent
        val steam = intent.getStringExtra("steamAppId") ?: return intent
        return intent.putExtra("localGameId", steam)
    }

    private fun withGameFile(intent: Intent): Intent {
        val file = intent.data ?: intent.getStringExtra("AutoStartFile")?.let { Uri.parse(it) } ?: return intent
        val held = contentResolver.persistedUriPermissions.any { it.uri == file && it.isReadPermission }
        if (!held) return intent
        intent.clipData = android.content.ClipData.newRawUri("", file)
        return intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    companion object {
        private const val TAG = "FluxGameLaunch"
        private const val EXTRA_PROFILE = "profile"
        private const val EXTRA_LAUNCH = "launch"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_ASKED = "asked"
        /** Already played on the phone, not asked about the TV (picked so, or opened by the stream). */
        const val EXTRA_TV_ASKED = "tv_asked"

        /** Starts a game found in the folders (or a launcher), through its profile when it has one. */
        /** The app [intent] (one of these shortcuts) opens a game in. */
        fun packageOf(context: Context, intent: Intent): String? {
            intent.getStringExtra(EXTRA_PROFILE)?.let { id -> GameProfiles.all(context).firstOrNull { it.id == id } }
                ?.packages?.firstOrNull()?.let { return it }
            return intent.getStringExtra(EXTRA_PACKAGE)
        }

        fun intentFor(context: Context, game: GameLibrary.Game): Intent =
            Intent(context, GameLaunchActivity::class.java).setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_LAUNCH, game.launch).putExtra(EXTRA_PACKAGE, game.packageName)

        /** Starts [profile]'s game. */
        fun intentFor(context: Context, profile: GameProfile): Intent =
            Intent(context, GameLaunchActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(EXTRA_PROFILE, profile.id)

        /** Asks the home screen to add [profile]'s shortcut; false when it can't. */
        /** The game's art as a home screen icon: cropped square, filling the icon's shape. */
        internal fun artIcon(art: android.graphics.Bitmap): Icon {
            // Adaptive icons are 108 dp, with the middle 72 dp always shown: the art fills it all
            val size = 432
            val side = minOf(art.width, art.height)
            val square = android.graphics.Bitmap.createBitmap(art, (art.width - side) / 2, (art.height - side) / 2, side, side)
            return Icon.createWithAdaptiveBitmap(android.graphics.Bitmap.createScaledBitmap(square, size, size, true))
        }

        /** Puts [profile]'s game on the home screen, with its [art] when it has some, else its app's icon. */
        fun pin(context: Context, profile: GameProfile, art: android.graphics.Bitmap? = null): Boolean {
            val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
            if (!manager.isRequestPinShortcutSupported) return false
            val icon = art?.let { runCatching { artIcon(it) }.getOrNull() } ?: profile.packages.firstNotNullOfOrNull { pkg ->
                runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
            }?.let { drawable ->
                val size = (48 * context.resources.displayMetrics.density).toInt()
                val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(android.graphics.Canvas(bitmap))
                Icon.createWithBitmap(bitmap)
            } ?: Icon.createWithResource(context, it.palsoftware.pastiera.R.mipmap.ic_launcher)
            val shortcut = ShortcutInfo.Builder(context, "game_${profile.id}")
                .setShortLabel(profile.name.ifBlank { "Game" })
                .setIcon(icon)
                .setIntent(intentFor(context, profile))
                .build()
            return runCatching { manager.requestPinShortcut(shortcut, null) }.getOrDefault(false)
        }
    }
}
