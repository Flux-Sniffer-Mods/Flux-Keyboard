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
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
        setContent {
            PastieraTheme {
                // Shown on a TV (through Moonlight): zoomed out to a TV's layout
                val metrics = resources.displayMetrics
                val base = androidx.compose.ui.platform.LocalDensity.current
                val tv = tvView()
                val density = if (!tv) base else {
                    val wide = maxOf(metrics.widthPixels, metrics.heightPixels)
                    androidx.compose.ui.unit.Density((wide / TV_WIDTH_DP).coerceAtMost(base.density), base.fontScale)
                }
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides density) {
                    GamesList(editId, tv)
                }
            }
        }
    }

    /**
     * Shown through Moonlight: on a screen of its own, or on the phone's while its screen is
     * mirrored (the phone at the TV's shape).
     */
    private fun tvView(): Boolean {
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay?.displayId ?: android.view.Display.DEFAULT_DISPLAY
        if (display != android.view.Display.DEFAULT_DISPLAY) return true
        val session = it.palsoftware.pastiera.streaming.StreamHost.session ?: return false
        return !session.separate
    }

    private data class Entry(val name: String, val profile: GameProfile, val intent: Intent)

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    private fun GamesList(editId: String?, tv: Boolean = false) {
        // Keys move through the grid only while it's what's shown (not a game's settings, where they type)
        keyHandler = null
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
        // Streaming's settings, from a long-press on Stream
        var streamSettings by remember { mutableStateOf(intent.getBooleanExtra(EXTRA_STREAM_SETTINGS, false)) }
        // A page of its own, like the other settings pages (not settings squeezed into a dialog)
        if (streamSettings) androidx.compose.ui.window.Dialog(
            onDismissRequest = { streamSettings = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing)) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.IconButton(onClick = { streamSettings = false }) { Text("\u2190", style = MaterialTheme.typography.titleLarge) }
                        Text(stringResource(R.string.stream_section), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
                    }
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        it.palsoftware.pastiera.settings.StreamingSettings(onStreamingChanged = { on -> streaming = on })
                    }
                }
            }
        }
        if (importing) it.palsoftware.pastiera.settings.GameImportDialog(onPicked = pick, onByHand = { importing = false; addingOwn = true }, onDismiss = { importing = false })
        if (addingOwn) it.palsoftware.pastiera.settings.OwnGameDialog(onAdded = pick, onDismiss = { addingOwn = false })
        if (addingXbox) it.palsoftware.pastiera.settings.XboxCloudDialog(XboxCloud.app(this), onPicked = pick, onDismiss = { addingXbox = false },
            // A tap plays it; long-press to add it to the games (and Moonlight)
            onPlay = { game ->
                game.launch?.let { uri -> runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }.getOrNull() }
                    ?.let { launch -> runCatching { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            },
            onAdded = { reload++ })
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
            text = {
                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    Text(stringResource(if (xbox.signedIn) R.string.game_mode_xbox_sign_out_description else R.string.game_mode_xbox_sign_in_description))
                    // The tile's own art
                    it.palsoftware.pastiera.settings.GameArtworkRow(GameArt.XBOX_TILE)
                }
            },
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
                    // Clear of the status bar and the camera cutout
                    modifier = Modifier.fillMaxWidth()
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(
                            androidx.compose.foundation.layout.WindowInsetsSides.Top + androidx.compose.foundation.layout.WindowInsetsSides.Horizontal))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back from the launchers' page
                    if (page == Page.APPS) androidx.compose.material3.IconButton(onClick = { page = Page.GAMES }) { Text("\u2190", style = MaterialTheme.typography.titleLarge) }
                    // The page's title, scrolling sideways when it doesn't fit
                    Text(
                        stringResource(if (page == Page.APPS) R.string.games_app_launchers else R.string.games_app_title),
                        style = MaterialTheme.typography.titleLarge, maxLines = 1,
                        modifier = Modifier.weight(1f).padding(start = if (page == Page.APPS) 4.dp else 12.dp).basicMarquee()
                    )
                    // A game on a screen of its own: its page, which gives it the phone's keys and touch
                    var screenOpen by remember { mutableStateOf(it.palsoftware.pastiera.display.ExtraScreen.current != null) }
                    androidx.compose.runtime.DisposableEffect(Unit) {
                        val watcher = { screenOpen = it.palsoftware.pastiera.display.ExtraScreen.current != null }
                        it.palsoftware.pastiera.display.ExtraScreen.watch(watcher)
                        onDispose { it.palsoftware.pastiera.display.ExtraScreen.unwatch(watcher) }
                    }
                    if (screenOpen) androidx.compose.material3.AssistChip(
                        onClick = { it.palsoftware.pastiera.streaming.StreamHost.openScreenPage(this@GamesActivity) },
                        label = { Text(stringResource(R.string.extra_screen_title)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    // Gaming settings, on the right with streaming, apart from the title
                    androidx.compose.material3.IconButton(onClick = { settings = true }) { Text("\u2699", style = MaterialTheme.typography.titleLarge) }
                    // Streaming to Moonlight: on or off at a tap, its settings on a long-press
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        color = if (streaming) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                        border = if (streaming) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).combinedClickable(
                            onClick = {
                                streaming = !streaming
                                it.palsoftware.pastiera.streaming.StreamHostService.setEnabled(this@GamesActivity, streaming)
                            },
                            onLongClick = { streamSettings = true }
                        )
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (streaming) Text("\u2713 ", style = MaterialTheme.typography.labelLarge)
                            Text(stringResource(R.string.games_app_stream), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        ) { inner ->
            val list = entries
            // Every tile on this page, in order: shown in the grid, and moved through by keys
            val tiles = mutableListOf<TileSpec>()
            if (page == Page.GAMES) {
                list.orEmpty().forEach { entry ->
                    tiles += TileSpec(entry.profile.id, entry.name,
                        if (entry.profile.pending) stringResource(R.string.games_app_new) else null,
                        { GameCover(entry.profile) },
                        { runCatching { startActivity(entry.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                        { editing = entry.profile })
                }
                // Xbox cloud games: signed in first, then picked; long-press to sign out
                tiles += TileSpec("xbox", stringResource(R.string.games_app_xbox), null, { XboxCover() },
                    { if (xbox.signedIn) addingXbox = true else xbox.click() }, { xboxMenu = true })
                tiles += TileSpec("add", stringResource(R.string.game_mode_import), null, { Symbol("+") }, { importing = true }, { importing = true })
                tiles += TileSpec("apps", stringResource(R.string.games_app_launchers), null, { Symbol("\u25A6") }, { page = Page.APPS }, { page = Page.APPS })
            } else {
                launchers().forEach { (pkg, profile) ->
                    val label = profile?.name ?: it.palsoftware.pastiera.settings.appLabel(this@GamesActivity, pkg)
                    tiles += TileSpec(pkg, label, null,
                        { AppCover(profile ?: GameProfiles.newProfile(label, setOf(pkg)).copy(id = "app:$pkg"), pkg) },
                        { packageManager.getLaunchIntentForPackage(pkg)?.let { launch -> runCatching { startActivity(launch) } } },
                        { editing = profile ?: GameProfiles.newProfile(label, setOf(pkg)) })
                }
            }
            val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            // The tile picked with keys (shown only once keys are used, not for touch)
            var selected by remember(page) { mutableStateOf(0) }
            // On a TV, picked with a controller: the frame shows from the start
            var byKeys by remember { mutableStateOf(tv) }
            LaunchedEffect(selected, byKeys) { if (byKeys && selected in tiles.indices) grid.animateScrollToItem(selected) }
            // Keys reach the window, not the grid: what each does, set afresh as the page changes
            keyHandler = { native ->
                navigation(native.keyCode)?.let { action ->
                    if (native.action == android.view.KeyEvent.ACTION_DOWN && (native.repeatCount == 0 || action in MOVES)) {
                        // Columns: how many tiles sit in the first row shown
                        val first = grid.layoutInfo.visibleItemsInfo.firstOrNull()?.row
                        val columns = grid.layoutInfo.visibleItemsInfo.count { it.row == first }.coerceAtLeast(1)
                        val wasByKeys = byKeys
                        byKeys = true
                        when (action) {
                            Nav.LEFT -> if (wasByKeys) selected = (selected - 1).coerceAtLeast(0)
                            Nav.RIGHT -> if (wasByKeys) selected = (selected + 1).coerceAtMost(tiles.lastIndex)
                            Nav.UP -> if (wasByKeys) selected = (selected - columns).coerceAtLeast(0)
                            Nav.DOWN -> if (wasByKeys) selected = (selected + columns).coerceAtMost(tiles.lastIndex)
                            Nav.OPEN -> tiles.getOrNull(selected)?.onClick?.invoke()
                            Nav.OPTIONS -> tiles.getOrNull(selected)?.onLongClick?.invoke()
                            Nav.BACK -> if (page == Page.APPS) page = Page.GAMES
                            Nav.PAGE -> page = if (page == Page.GAMES) Page.APPS else Page.GAMES
                            Nav.SETTINGS -> settings = true
                        }
                    }
                    true
                } ?: false
            }
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                state = grid,
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(104.dp),
                modifier = Modifier.fillMaxSize().padding(inner).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(tiles.size, key = { tiles[it].key }) { index ->
                    val tile = tiles[index]
                    Tile(tile.name, tile.sub, tile.art, tile.onClick, tile.onLongClick,
                        selected = byKeys && index == selected, dimmed = byKeys && index != selected)
                }
                if (page == Page.GAMES && list != null && list.isEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.games_app_empty))
                }
            }
        }
    }

    private class TileSpec(
        val key: String, val name: String, val sub: String?,
        val art: @Composable () -> Unit, val onClick: () -> Unit, val onLongClick: () -> Unit
    )

    /** What a key does in the Games app, in Flux Keyboard's controller layout (or a real controller's). */
    private enum class Nav { UP, DOWN, LEFT, RIGHT, OPEN, OPTIONS, BACK, PAGE, SETTINGS }
    private val MOVES = setOf(Nav.UP, Nav.DOWN, Nav.LEFT, Nav.RIGHT)

    /** What the page shown does with a key; false when it doesn't take it. */
    private var keyHandler: ((android.view.KeyEvent) -> Boolean)? = null

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (controllerKeys(this) && keyHandler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        inFront = true
    }

    override fun onPause() {
        inFront = false
        super.onPause()
    }

    private fun navigation(code: Int): Nav? = when (code) {
        android.view.KeyEvent.KEYCODE_W, android.view.KeyEvent.KEYCODE_DPAD_UP -> Nav.UP
        android.view.KeyEvent.KEYCODE_S, android.view.KeyEvent.KEYCODE_DPAD_DOWN -> Nav.DOWN
        android.view.KeyEvent.KEYCODE_A, android.view.KeyEvent.KEYCODE_DPAD_LEFT -> Nav.LEFT
        android.view.KeyEvent.KEYCODE_D, android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> Nav.RIGHT
        // L is the A button, K the X button, P the B button
        android.view.KeyEvent.KEYCODE_L, android.view.KeyEvent.KEYCODE_BUTTON_A, android.view.KeyEvent.KEYCODE_DPAD_CENTER,
        android.view.KeyEvent.KEYCODE_ENTER -> Nav.OPEN
        android.view.KeyEvent.KEYCODE_K, android.view.KeyEvent.KEYCODE_BUTTON_X -> Nav.OPTIONS
        android.view.KeyEvent.KEYCODE_P, android.view.KeyEvent.KEYCODE_BUTTON_B -> Nav.BACK
        // Q E the bumpers: between games and launchers
        android.view.KeyEvent.KEYCODE_Q, android.view.KeyEvent.KEYCODE_E,
        android.view.KeyEvent.KEYCODE_BUTTON_L1, android.view.KeyEvent.KEYCODE_BUTTON_R1 -> Nav.PAGE
        android.view.KeyEvent.KEYCODE_SPACE, android.view.KeyEvent.KEYCODE_BUTTON_START -> Nav.SETTINGS
        else -> null
    }

    private enum class Page { GAMES, APPS }

    /** A TV's layout: this many dp across, as Android TV lays out 1080p. */
    private val TV_WIDTH_DP = 960f

    /** Launchers and emulators: those with gaming mode settings, then the known ones installed. */
    private fun launchers(): List<Pair<String, GameProfile?>> {
        val all = GameProfiles.all(this)
        // An app that's a game itself (a Play Store game) is on the games page, not here
        val gameApps = games(this).map { it.first }.filter { game ->
            val launch = game.launch?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
            launch == null || launch.component?.packageName == game.packages.firstOrNull() && (launch.extras?.isEmpty ?: true) && launch.data == null
        }.flatMap { it.packages }.toSet()
        val known = GameFolders.Player.entries.flatMap { player -> player.activities.map { it.first } } +
            listOf("org.dolphinemu.dolphinemu", "org.ppsspp.ppsspp", "org.ppsspp.ppssppgold")
        val profiles = all.filter { !it.isGame }
        val withProfile = profiles.mapNotNull { profile -> profile.packages.firstOrNull()?.let { it to profile } }
            .distinctBy { it.first }.filter { (pkg, _) -> pkg in known || pkg !in gameApps }
        val installed = known.distinct().filter { pkg -> withProfile.none { it.first == pkg } && packageManager.getLaunchIntentForPackage(pkg) != null }
        return withProfile + installed.map { it to null }
    }

    /** One tile: an upright picture, its name beneath; long-press for its settings. */
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    private fun Tile(name: String, sub: String?, art: @Composable () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit,
                     selected: Boolean = false, dimmed: Boolean = false) {
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                art()
                // Picked with keys: a frame round it, the rest a little darker
                if (dimmed) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)))
                if (selected) Box(Modifier.fillMaxSize().border(4.dp, MaterialTheme.colorScheme.primary, shape))
            }
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

    /** A launcher's or emulator's tile: art picked for it, else its icon, upright like the games'. */
    @Composable
    private fun AppCover(profile: GameProfile, pkg: String) {
        val version = GameArt.version(this, profile)
        val art by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, profile.id, version) {
            value = withContext(Dispatchers.IO) { runCatching { GameArt.appPortrait(this@GamesActivity, profile, pkg)?.asImageBitmap() }.getOrNull() }
        }
        Cover(art) { it.palsoftware.pastiera.settings.FluxAppIcon(pkg, 64.dp) }
    }

    /** Xbox Cloud Gaming's tile: Game Pass art, a cloud while there's none. */
    @Composable
    private fun XboxCover() {
        val version = GameArt.version(this, GameArt.XBOX_TILE)
        val art by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, version) {
            value = withContext(Dispatchers.IO) { runCatching { GameArt.xboxTile(this@GamesActivity)?.asImageBitmap() }.getOrNull() }
        }
        Cover(art) { Symbol("\u2601") }
    }

    @Composable
    private fun Cover(image: androidx.compose.ui.graphics.ImageBitmap?, fallback: @Composable () -> Unit) {
        if (image != null) androidx.compose.foundation.Image(
            bitmap = image, contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize()
        ) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { fallback() }
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

        private const val EXTRA_STREAM_SETTINGS = "stream_settings"

        /** The Games app opened at streaming's settings. */
        fun streamSettings(context: Context): Intent =
            Intent(context, GamesActivity::class.java).putExtra(EXTRA_STREAM_SETTINGS, true)

        private const val KEY_CONTROLLER_KEYS = "games_app_controller_keys"
        @Volatile private var inFront = false

        /** The Games app in front, moved through with keys: [event] is its, not the keyboard's. */
        fun ownsKey(context: Context, event: android.view.KeyEvent?): Boolean =
            inFront && event != null && controllerKeys(context) && event.keyCode in NAV_KEYS

        private val NAV_KEYS = setOf(
            android.view.KeyEvent.KEYCODE_W, android.view.KeyEvent.KEYCODE_A, android.view.KeyEvent.KEYCODE_S, android.view.KeyEvent.KEYCODE_D,
            android.view.KeyEvent.KEYCODE_L, android.view.KeyEvent.KEYCODE_K, android.view.KeyEvent.KEYCODE_P,
            android.view.KeyEvent.KEYCODE_Q, android.view.KeyEvent.KEYCODE_E, android.view.KeyEvent.KEYCODE_SPACE,
            android.view.KeyEvent.KEYCODE_ENTER
        )

        /** The Games app moved through with gaming mode's keys (W A S D, L to open...). */
        fun controllerKeys(context: Context): Boolean = SettingsManager.getPreferences(context).getBoolean(KEY_CONTROLLER_KEYS, true)

        fun setControllerKeys(context: Context, on: Boolean) =
            SettingsManager.getPreferences(context).edit().putBoolean(KEY_CONTROLLER_KEYS, on).apply()

        fun enabled(context: Context): Boolean = SettingsManager.getPreferences(context).getBoolean(KEY_ENABLED, false)

        /** Games in the app list (on), or not: the app itself shown or hidden, its shortcuts kept in step. */
        /** Its entry in the app list, shown with Games in the app list on. */
        private const val LAUNCHER_ALIAS = "it.palsoftware.pastiera.gaming.GamesLauncher"

        /**
         * The app list's entry as the setting says (once after updating from when the activity
         * itself was the entry, turned off with it).
         */
        fun syncLauncher(context: Context) {
            val pm = context.packageManager
            val component = ComponentName(context, LAUNCHER_ALIAS)
            val shown = pm.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            if (shown != enabled(context)) setEnabled(context, enabled(context))
        }

        fun setEnabled(context: Context, on: Boolean) {
            SettingsManager.getPreferences(context).edit().putBoolean(KEY_ENABLED, on).apply()
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, LAUNCHER_ALIAS),
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
                        .setActivity(ComponentName(context, LAUNCHER_ALIAS))
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
