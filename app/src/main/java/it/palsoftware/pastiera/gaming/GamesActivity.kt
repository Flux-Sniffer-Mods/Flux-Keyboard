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
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
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

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    @Composable
    private fun GamesList(editId: String?) {
        var entries by remember { mutableStateOf<List<Entry>?>(null) }
        var reload by remember { mutableStateOf(0) }
        // Its settings, as in Flux Keyboard's Gaming mode (opened straight away when asked to)
        var editing by remember { mutableStateOf(editId?.let { id -> GameProfiles.all(this).firstOrNull { it.id == id } }) }
        // Adding a game, as in Gaming mode: a launcher's or folder's, one by hand, or an Xbox cloud game
        var importing by remember { mutableStateOf(false) }
        var addingOwn by remember { mutableStateOf(false) }
        var addingXbox by remember { mutableStateOf(false) }
        val pick: (GameLibrary.Game) -> Unit = { game ->
            importing = false; addingOwn = false; addingXbox = false
            editing = GameProfiles.profileFor(this, game)
        }
        // Gaming mode's own settings, opened from here too
        var settings by remember { mutableStateOf(false) }
        if (settings) {
            androidx.activity.compose.BackHandler { settings = false; reload++ }
            it.palsoftware.pastiera.settings.GameModeScreen(onBack = { settings = false; reload++ })
            return
        }
        var streaming by remember { mutableStateOf(it.palsoftware.pastiera.streaming.StreamHostService.enabled(this)) }
        if (importing) it.palsoftware.pastiera.settings.GameImportDialog(onPicked = pick, onByHand = { importing = false; addingOwn = true }, onDismiss = { importing = false })
        if (addingOwn) it.palsoftware.pastiera.settings.OwnGameDialog(onAdded = pick, onDismiss = { addingOwn = false })
        if (addingXbox) it.palsoftware.pastiera.settings.XboxCloudDialog(XboxCloud.app(this), onPicked = pick, onDismiss = { addingXbox = false })
        LaunchedEffect(reload) { entries = withContext(Dispatchers.IO) { GameProfiles.addDetected(this@GamesActivity, GameLibrary.installedGames(this@GamesActivity)); games(this@GamesActivity) }.map { (profile, intent) -> Entry(profile.name, profile, intent) } }
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
        var page by remember { mutableStateOf(Page.GAMES) }
        if (page == Page.APPS) androidx.activity.compose.BackHandler { page = Page.GAMES }
        val xbox = it.palsoftware.pastiera.settings.rememberXboxAccount()
        var xboxMenu by remember { mutableStateOf(false) }
        if (xboxMenu) androidx.compose.material3.AlertDialog(
            onDismissRequest = { xboxMenu = false },
            title = { Text(stringResource(R.string.games_app_xbox)) },
            text = { Text(stringResource(if (xbox.signedIn) R.string.game_mode_xbox_sign_out_description else R.string.game_mode_xbox_sign_in_description)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { xboxMenu = false; xbox.click() }) {
                    Text(stringResource(if (xbox.signedIn) R.string.game_mode_xbox_sign_out else R.string.game_mode_xbox_sign_in))
                }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { xboxMenu = false }) { Text(stringResource(R.string.cancel)) } }
        )
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back from the launchers' page; else gaming mode's settings
                    if (page == Page.APPS) androidx.compose.material3.IconButton(onClick = { page = Page.GAMES }) { Text("\u2190", style = MaterialTheme.typography.titleLarge) }
                    else androidx.compose.material3.IconButton(onClick = { settings = true }) { Text("\u2699", style = MaterialTheme.typography.titleLarge) }
                    Text(
                        stringResource(if (page == Page.APPS) R.string.games_app_launchers else R.string.games_app_title),
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                    // Streaming to Moonlight, on or off at a tap
                    androidx.compose.material3.FilterChip(
                        selected = streaming,
                        onClick = {
                            streaming = !streaming
                            it.palsoftware.pastiera.streaming.StreamHostService.setEnabled(this@GamesActivity, streaming)
                        },
                        label = { Text(stringResource(R.string.games_app_stream)) },
                        leadingIcon = if (streaming) { { Text("\u2713") } } else null
                    )
                }
            }
        ) { inner ->
            val list = entries
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(104.dp),
                modifier = Modifier.fillMaxSize().padding(inner).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (page == Page.GAMES) {
                    items(list.orEmpty().size, key = { list!![it].profile.id }) { index ->
                        val entry = list!![index]
                        Tile(
                            name = entry.name,
                            sub = if (entry.profile.pending) stringResource(R.string.games_app_new) else null,
                            art = { GameCover(entry.profile) },
                            onClick = { runCatching { startActivity(entry.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                            onLongClick = { editing = entry.profile }
                        )
                    }
                    item(key = "add") { Tile(stringResource(R.string.game_mode_import), null, { Symbol("+") }, { importing = true }, { importing = true }) }
                    // Xbox cloud games: signed in first, then picked; long-press to sign out
                    item(key = "xbox") {
                        Tile(stringResource(R.string.games_app_xbox), null, { Symbol("\u2601") },
                            onClick = { if (xbox.signedIn) addingXbox = true else xbox.click() },
                            onLongClick = { xboxMenu = true })
                    }
                    item(key = "apps") { Tile(stringResource(R.string.games_app_launchers), null, { Symbol("\u25A6") }, { page = Page.APPS }, { page = Page.APPS }) }
                } else {
                    val apps = launchers()
                    items(apps.size, key = { apps[it].first }) { index ->
                        val (pkg, profile) = apps[index]
                        Tile(
                            name = profile?.name ?: it.palsoftware.pastiera.settings.appLabel(this@GamesActivity, pkg),
                            sub = null,
                            art = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { it.palsoftware.pastiera.settings.FluxAppIcon(pkg, 64.dp) } },
                            onClick = { packageManager.getLaunchIntentForPackage(pkg)?.let { launch -> runCatching { startActivity(launch) } } },
                            onLongClick = {
                                editing = profile ?: GameProfiles.newProfile(it.palsoftware.pastiera.settings.appLabel(this@GamesActivity, pkg), setOf(pkg))
                            }
                        )
                    }
                }
                if (page == Page.GAMES && list != null && list.isEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.games_app_empty))
                }
            }
        }
    }

    private enum class Page { GAMES, APPS }

    /** Launchers and emulators: those with gaming mode settings, then the known ones installed. */
    private fun launchers(): List<Pair<String, GameProfile?>> {
        val profiles = GameProfiles.all(this).filter { !it.isGame }
        val withProfile = profiles.mapNotNull { profile -> profile.packages.firstOrNull()?.let { it to profile } }.distinctBy { it.first }
        val known = GameFolders.Player.entries.flatMap { player -> player.activities.map { it.first } } +
            listOf("org.dolphinemu.dolphinemu", "org.ppsspp.ppsspp", "org.ppsspp.ppssppgold")
        val installed = known.distinct().filter { pkg -> withProfile.none { it.first == pkg } && packageManager.getLaunchIntentForPackage(pkg) != null }
        return withProfile + installed.map { it to null }
    }

    /** One tile: an upright picture, its name beneath; long-press for its settings. */
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    private fun Tile(name: String, sub: String?, art: @Composable () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit) {
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) { art() }
            Text(
                name, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
            )
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }

    @Composable
    private fun Symbol(text: String) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    /** A game's upright art, the same Moonlight shows; its app's icon while there's none. */
    @Composable
    private fun GameCover(profile: GameProfile) {
        val version = GameArt.version(this, profile)
        val art by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, profile.id, profile.launch, version) {
            value = withContext(Dispatchers.IO) {
                runCatching { GameArt.loadPortrait(this@GamesActivity, profile)?.asImageBitmap() }.getOrNull()
            }
        }
        val image = art
        if (image != null) androidx.compose.foundation.Image(
            bitmap = image, contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize()
        ) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            profile.packages.firstOrNull()?.let { pkg -> it.palsoftware.pastiera.settings.FluxAppIcon(pkg, 48.dp) }
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
