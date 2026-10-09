package it.palsoftware.pastiera.gaming

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle

/**
 * A game's home screen shortcut: makes its profile the one gaming mode uses for its launcher,
 * then starts the game (or, when the launcher can't be told which game, the launcher).
 */
class GameLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profile = intent.getStringExtra(EXTRA_PROFILE)?.let { id -> GameProfiles.all(this).firstOrNull { it.id == id } }
        if (profile == null) {
            finish()
            return
        }
        profile.packages.forEach { GameProfiles.setActive(this, it, profile.id) }
        val game = profile.launch?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
            ?.takeIf { (it.`package` ?: it.component?.packageName) in profile.packages }
            // An activity that isn't there (another GameHub build): the app itself instead
            ?.takeIf { packageManager.resolveActivity(it, 0) != null }
            ?.let { withGameFile(it) }
        val app = profile.packages.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        val emulatorGame = game != null && (game.data != null || game.hasExtra("AutoStartFile"))
        val pkg = game?.`package` ?: game?.component?.packageName
        if (emulatorGame && pkg != null && it.palsoftware.pastiera.adb.AdbShell.available()) {
            // An emulator only opens the game it's given when it starts afresh (Dolphin reads it
            // as its screen is made, PPSSPP ignores a new one while running): stopped first
            Thread {
                it.palsoftware.pastiera.adb.AdbShell.run("am force-stop $pkg", 3_000)
                runOnUiThread { start(game, app) }
            }.start()
        } else {
            start(game, app)
        }
    }

    /** The game, or the app when the game can't be started. */
    private fun start(game: Intent?, app: Intent?) {
        val started = game?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess } == true
        if (!started) app?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        finish()
    }

    /** A game file you picked yourself: the emulator may read it, as Flux Keyboard may. */
    private fun withGameFile(intent: Intent): Intent {
        val file = intent.data ?: intent.getStringExtra("AutoStartFile")?.let { Uri.parse(it) } ?: return intent
        val held = contentResolver.persistedUriPermissions.any { it.uri == file && it.isReadPermission }
        if (!held) return intent
        intent.clipData = android.content.ClipData.newRawUri("", file)
        return intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    companion object {
        private const val EXTRA_PROFILE = "profile"

        /** Asks the home screen to add [profile]'s shortcut; false when it can't. */
        fun pin(context: Context, profile: GameProfile): Boolean {
            val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
            if (!manager.isRequestPinShortcutSupported) return false
            val icon = profile.packages.firstNotNullOfOrNull { pkg ->
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
                .setIntent(
                    Intent(context, GameLaunchActivity::class.java).setAction(Intent.ACTION_VIEW)
                        .putExtra(EXTRA_PROFILE, profile.id)
                )
                .build()
            return runCatching { manager.requestPinShortcut(shortcut, null) }.getOrDefault(false)
        }
    }
}
