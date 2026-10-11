package it.palsoftware.pastiera.gaming

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.settings.GameArtIcon
import it.palsoftware.pastiera.ui.theme.PastieraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Games, an app of its own in the home screen's app list (Niagara's included): every game with
 * a profile and every game found in the game folders, each started as its home screen shortcut
 * starts it. Its long-press menu holds the games too, kept in step as games come and go.
 */
class GamesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val editId = intent.getStringExtra(EXTRA_EDIT)
        setContent { PastieraTheme { GamesList(editId) } }
    }

    private data class Entry(val name: String, val profile: GameProfile, val intent: Intent)

    @Composable
    private fun GamesList(editId: String?) {
        var entries by remember { mutableStateOf<List<Entry>?>(null) }
        var reload by remember { mutableStateOf(0) }
        // Its settings, as in Flux Keyboard's Gaming mode (opened straight away when asked to)
        var editing by remember { mutableStateOf(editId?.let { id -> GameProfiles.all(this).firstOrNull { it.id == id } }) }
        LaunchedEffect(reload) { entries = withContext(Dispatchers.IO) { games(this@GamesActivity) }.map { (profile, intent) -> Entry(profile.name, profile, intent) } }
        editing?.let { profile ->
            androidx.activity.compose.BackHandler { editing = null }
            it.palsoftware.pastiera.settings.GameProfileEditor(profile, onDone = { saved ->
                if (saved != null) { if (saved.pending) GameProfiles.confirm(this, saved) else GameProfiles.save(this, saved) }
                editing = null
                reload++
            }, onDelete = {
                GameProfiles.delete(this, profile.id)
                editing = null
                reload++
            })
            return
        }
        Scaffold { inner ->
            val list = entries
            LazyColumn(modifier = Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp)) {
                item {
                    Text(stringResource(R.string.games_app_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 16.dp))
                }
                if (list != null && list.isEmpty()) item { Text(stringResource(R.string.games_app_empty)) }
                items(list.orEmpty(), key = { it.name + it.profile.packages }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            runCatching { startActivity(entry.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GameArtIcon(entry.profile)
                        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.titleMedium)
                            if (entry.profile.pending) Text(
                                stringResource(R.string.games_app_new), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Its own settings, where it has a profile
                        if (!entry.profile.id.startsWith("found:")) androidx.compose.material3.TextButton(onClick = { editing = entry.profile }) {
                            Text("\u2699")
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val KEY_ENABLED = "games_app_enabled"
        private const val EXTRA_EDIT = "edit"

        /**
         * Every game: those with a profile, then those found in the folders without one (as a
         * profile-less stand-in, for its art and app), each with what starts it, A to Z.
         */
        internal fun games(context: Context): List<Pair<GameProfile, Intent>> {
            val profiles = GameProfiles.all(context).filter { it.isGame }
            val known = profiles.mapNotNull { it.launch }.toSet()
            val found = GameFolders.scanned(context).filter { it.launch != null && it.launch !in known }.map { game ->
                GameProfiles.newProfile(game.name, setOf(game.packageName)).copy(id = "found:" + game.launch.hashCode(), launch = game.launch) to
                    GameLaunchActivity.intentFor(context, game)
            }
            return (profiles.map { it to GameLaunchActivity.intentFor(context, it) } + found)
                .distinctBy { it.first.name.lowercase() to it.first.packages }
                .sortedBy { it.first.name.lowercase() }
        }

        fun enabled(context: Context): Boolean = SettingsManager.getPreferences(context).getBoolean(KEY_ENABLED, false)

        /** Games in the app list (on), or not: the app itself shown or hidden, its shortcuts kept in step. */
        fun setEnabled(context: Context, on: Boolean) {
            SettingsManager.getPreferences(context).edit().putBoolean(KEY_ENABLED, on).apply()
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, GamesActivity::class.java),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Thread { sync(context.applicationContext) }.start()
        }

        /**
         * The Games app's long-press menu: as many games as the home screen shows there, the
         * ones with a profile first (blocking: reads art). None while Games is off.
         */
        fun sync(context: Context) {
            val manager = context.getSystemService(ShortcutManager::class.java) ?: return
            if (!enabled(context)) { runCatching { manager.removeAllDynamicShortcuts() }; return }
            val shortcuts = games(context).sortedBy { if (it.first.id.startsWith("found:")) 1 else 0 }
                .take(manager.maxShortcutCountPerActivity.coerceAtMost(8)).mapIndexed { rank, (profile, intent) ->
                    val art = runCatching { GameArt.load(context, profile) }.getOrNull()
                    ShortcutInfo.Builder(context, "games_${profile.id}")
                        .setShortLabel(profile.name.ifBlank { "Game" })
                        .setIcon(art?.let { runCatching { GameLaunchActivity.artIcon(it) }.getOrNull() } ?: Icon.createWithResource(context, R.mipmap.ic_games))
                        .setActivity(ComponentName(context, GamesActivity::class.java))
                        .setIntent(intent)
                        .setRank(rank)
                        .build()
                }
            runCatching { manager.dynamicShortcuts = shortcuts }
        }

        /** Asks the home screen to add every game's shortcut, one after another. */
        fun pinAll(context: Context) {
            val app = context.applicationContext
            Thread {
                games(app).filterNot { it.first.id.startsWith("found:") }.forEach { (profile, _) ->
                    val art = runCatching { GameArt.load(app, profile) }.getOrNull()
                    GameLaunchActivity.pin(app, profile, art)
                }
            }.start()
        }
    }
}
