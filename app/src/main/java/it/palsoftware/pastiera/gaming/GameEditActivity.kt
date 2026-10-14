package it.palsoftware.pastiera.gaming

import it.palsoftware.pastiera.apps.appLabel
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import it.palsoftware.pastiera.settings.GameProfileEditor
import it.palsoftware.pastiera.ui.theme.PastieraTheme

/**
 * One game's or app's gaming mode settings, on their own: from a found game's first start, the
 * Games app, or the notification for an app that looks like a game (its settings made first).
 */
class GameEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profiles = GameProfiles.all(this)
        val profile = intent.getStringExtra(EXTRA_PROFILE)?.let { id -> profiles.firstOrNull { it.id == id } }
            ?: intent.getStringExtra(EXTRA_APP)?.let { pkg -> forApp(pkg, profiles) }
        if (profile == null) { finish(); return }
        show(profile)
    }

    /**
     * An app's settings to set up: a game that's an app of its own, found already, is that one;
     * else the app's settings, made now.
     */
    private fun forApp(pkg: String, profiles: List<GameProfile>): GameProfile? {
        GameDetect.dismiss(this, pkg)
        profiles.firstOrNull { it.pending && it.isGame && pkg in it.packages && GameApps.appFor(pkg) == null }?.let { return it }
        val label = appLabel(pkg)
        return GameProfiles.ensureApp(this, pkg, label).firstOrNull()
    }

    private fun show(profile: GameProfile) {
        setContent {
            PastieraTheme {
                GameProfileEditor(profile, onDone = { saved ->
                    if (saved != null) { if (saved.pending) GameProfiles.confirm(this, saved) else GameProfiles.save(this, saved) }
                    finish()
                }, onDelete = {
                    GameProfiles.delete(this, profile.id)
                    finish()
                })
            }
        }
    }

    companion object {
        private const val EXTRA_PROFILE = "profile"
        private const val EXTRA_APP = "app"

        fun forProfile(context: Context, profileId: String): Intent =
            Intent(context, GameEditActivity::class.java).putExtra(EXTRA_PROFILE, profileId)

        /** An app's settings, made for it first. */
        fun forApp(context: Context, packageName: String): Intent =
            Intent(context, GameEditActivity::class.java).putExtra(EXTRA_APP, packageName)
    }
}
