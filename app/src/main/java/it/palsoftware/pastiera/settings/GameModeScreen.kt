package it.palsoftware.pastiera.settings

import it.palsoftware.pastiera.apps.appLabel
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.size

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.asImageBitmap

import android.view.KeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.apps.AppPickerDialog
import it.palsoftware.pastiera.apps.InstalledApp
import it.palsoftware.pastiera.gaming.GameLibrary
import it.palsoftware.pastiera.gaming.GameProfile
import it.palsoftware.pastiera.gaming.GameProfiles
import it.palsoftware.pastiera.gaming.GameApps
import it.palsoftware.pastiera.gaming.FluxLayout
import it.palsoftware.pastiera.gaming.DualScreens
import it.palsoftware.pastiera.gaming.DolphinPads
import it.palsoftware.pastiera.gaming.GameFiles
import it.palsoftware.pastiera.gaming.GameIds
import it.palsoftware.pastiera.gaming.TrackpadRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gaming mode: its switches, the game profiles, games found in game launchers, and each
 * profile's keys and trackpad.
 */
@Composable
fun GameModeScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    var profiles by remember { mutableStateOf(GameProfiles.all(context)) }
    var editing by remember { mutableStateOf<GameProfile?>(null) }
    var enabled by remember { mutableStateOf(GameProfiles.enabled(context)) }
    var picking by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var gameNativeSetup by remember { mutableStateOf(false) }
    var addingOwn by remember { mutableStateOf(false) }
    var addingXbox by remember { mutableStateOf(false) }
    val xboxApp: String? = remember { it.palsoftware.pastiera.gaming.XboxCloud.app(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun refresh() { profiles = GameProfiles.all(context) }
    // The game folders read again (games found join the list, not set up yet), and games named
    // by their file given their titles once their IDs are read
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (it.palsoftware.pastiera.adb.AdbShell.available()) it.palsoftware.pastiera.gaming.GameFolders.games(context)
            else GameProfiles.addDetected(context, GameLibrary.installedGames(context))
            GameProfiles.retitle(context)
        }
        refresh()
    }

    var remapping by remember { mutableStateOf(false) }
    if (remapping) {
        androidx.activity.compose.BackHandler { remapping = false }
        GameLayoutRemapper(modifier) { remapping = false }
        return
    }

    editing?.let { profile ->
        GameProfileEditor(profile, onDone = { saved ->
            // A found game, looked at: set up from now on
            if (saved != null) { if (saved.pending) GameProfiles.confirm(context, saved) else GameProfiles.save(context, saved) }
            // An emulator given the layout: written again if A and B are now the other way round
            if (saved != null && !saved.isGame) {
                val app = context.applicationContext
                Thread {
                    saved.packages.filter { pkg -> it.palsoftware.pastiera.gaming.GameApps.emulator(pkg) in setOf("Dolphin", "PPSSPP") }
                        .forEach { pkg -> runCatching { it.palsoftware.pastiera.gaming.FluxLayout.refreshIfOutdated(app, pkg) } }
                }.start()
            }
            editing = null
            refresh()
        }, onDelete = {
            GameProfiles.delete(context, profile.id); it.palsoftware.pastiera.gaming.GameArt.forget(context, profile)
            it.palsoftware.pastiera.gaming.GameArt.clearCustom(context, profile)
            editing = null
            refresh()
        })
        return
    }

    FluxScreenScaffold(stringResource(R.string.game_mode_title), onBack, modifier) {
        FluxNote(stringResource(R.string.game_mode_note))
        // The keyboard's limits, and what plays best: a controller made for the phone
        FluxNote(stringResource(R.string.game_mode_disclaimer))
        FluxActionRow(
            linkId = null,
            icon = "\u2328",
            title = stringResource(R.string.game_mode_layout_title),
            description = stringResource(R.string.game_remap_row_description)
        ) { remapping = true }
        FluxSwitchRow(
            linkId = "main.root.game_mode",
            title = stringResource(R.string.game_mode_title),
            description = stringResource(R.string.game_mode_switch_description),
            checked = enabled
        ) {
            enabled = it
            GameProfiles.setEnabled(context, it)
        }
        // The app a game last ran in: a launcher may play its games in a part of its own, which a
        // profile then needs as one of its apps
        val seen = remember { it.palsoftware.pastiera.gaming.GameMode.lastApp() }
        if (seen != null && profiles.isNotEmpty() && profiles.none { p -> p.packages.any { GameProfiles.covers(it, seen) } }) {
            var addingSeen by remember { mutableStateOf(false) }
            FluxActionRow(
                linkId = null,
                icon = "+",
                title = stringResource(R.string.game_mode_seen_title, context.appLabel(seen)),
                description = stringResource(R.string.game_mode_seen_description, seen)
            ) { addingSeen = true }
            if (addingSeen) {
                AlertDialog(
                    onDismissRequest = { addingSeen = false },
                    title = { Text(stringResource(R.string.game_mode_seen_title, context.appLabel(seen))) },
                    text = {
                        Column {
                            profiles.filter { !it.isGame }.forEach { profile ->
                                Text(profile.name, modifier = Modifier.fillMaxWidth().clickable {
                                    GameProfiles.save(context, profile.copy(packages = profile.packages + seen))
                                    refresh()
                                    addingSeen = false
                                }.padding(vertical = 10.dp))
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { addingSeen = false }) { Text(stringResource(R.string.cancel)) } }
                )
            }
        }
        // Each app's settings, which its games play with (Dolphin: one for GameCube, one for Wii)
        SettingsSectionDivider(stringResource(R.string.game_mode_apps_section))
        profiles.filter { !it.isGame }.forEach { profile ->
            FluxActionRow(
                linkId = null,
                title = profile.name,
                description = profile.leftHalf.label + " · " + profile.rightHalf.label,
                appPackage = profile.packages.firstOrNull()
            ) { editing = profile }
        }
        FluxActionRow(
            linkId = null,
            icon = "+",
            title = stringResource(R.string.game_mode_add),
            description = stringResource(R.string.game_mode_add_description)
        ) { picking = true }
        // The games: home screen shortcuts, art and detection, their app's settings unless their own
        SettingsSectionDivider(stringResource(R.string.game_mode_games_section))
        profiles.filter { it.isGame }.forEach { profile ->
            val appName = profile.packages.firstOrNull()?.let { pkg -> context.appLabel(pkg) }.orEmpty()
            FluxActionRow(
                linkId = null,
                title = profile.name,
                description = when {
                    profile.pending -> stringResource(R.string.game_mode_found_short, appName)
                    profile.own -> stringResource(R.string.game_mode_own_settings_short)
                    else -> stringResource(R.string.game_mode_app_settings_short, appName)
                },
                leading = { GameArtIcon(profile) }
            ) { editing = profile }
        }
        // One way in for every game: the launchers' and folders' games, or one by hand
        FluxActionRow(
            linkId = null,
            icon = "+",
            title = stringResource(R.string.game_mode_import),
            description = stringResource(R.string.game_mode_import_description)
        ) { importing = true }
        // The official GameHub can only be opened; GameHub Lite starts the game itself
        val gameHubLite = remember { it.palsoftware.pastiera.gaming.GameFolders.gameHubLiteMissing(context) }
        if (gameHubLite) FluxNote(stringResource(R.string.game_mode_gamehub_lite_note))
        // Xbox Cloud Gaming's games, from its catalogue, each a game of its own
        if (xboxApp != null) FluxActionRow(
            linkId = null,
            icon = "+",
            title = stringResource(R.string.game_mode_xbox_add),
            description = stringResource(R.string.game_mode_xbox_add_description)
        ) { addingXbox = true }
        // Signing in to Xbox (a plain browser page, keys typing), or out once signed in
        val xbox = rememberXboxAccount()
        FluxActionRow(
            linkId = null,
            icon = "\u2192",
            title = stringResource(if (xbox.signedIn) R.string.game_mode_xbox_sign_out else R.string.game_mode_xbox_sign_in),
            description = stringResource(if (xbox.signedIn) R.string.game_mode_xbox_sign_out_description else R.string.game_mode_xbox_sign_in_description)
        ) { xbox.click() }
        // Which cloud games the catalogue lists: the ones the Game Pass plan plays
        XboxPlanRow()
        // The phone's own game mode, which can take over as the home screen
        PhoneGameModeRow()
        // Streaming games to a Moonlight client
        SettingsSectionDivider(stringResource(R.string.stream_section))
        StreamingSettings()
        // Where games are found: Flux Keyboard's folders, ES-DE's, and your own
        SettingsSectionDivider(stringResource(R.string.game_mode_folders_section))
        FluxActionRow(
            linkId = null,
            icon = "+",
            title = stringResource(R.string.game_mode_folders),
            description = stringResource(R.string.game_mode_folders_description)
        ) {
            scope.launch {
                val made = withContext(Dispatchers.IO) { it.palsoftware.pastiera.gaming.GameFolders.create() }
                android.widget.Toast.makeText(
                    context, if (made) R.string.game_mode_folders_made else R.string.adb_not_running, android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
        CustomGameFolders()
        // The games in the quick launcher too, as apps are
        var inLauncher by remember { mutableStateOf(it.palsoftware.pastiera.commands.GameCommandSource.enabled(context)) }
        FluxSwitchRow(
            linkId = null,
            title = stringResource(R.string.game_mode_quick_launcher),
            description = stringResource(R.string.game_mode_quick_launcher_description),
            checked = inLauncher
        ) { on ->
            inLauncher = on
            it.palsoftware.pastiera.commands.GameCommandSource.setEnabled(context, on)
        }
        // The games in the home screen's app list too, as an app of their own (Niagara's list)
        var inAppList by remember { mutableStateOf(it.palsoftware.pastiera.gaming.GamesActivity.enabled(context)) }
        FluxSwitchRow(
            linkId = null,
            title = stringResource(R.string.games_app_switch),
            description = stringResource(R.string.games_app_switch_description),
            checked = inAppList
        ) { on ->
            inAppList = on
            it.palsoftware.pastiera.gaming.GamesActivity.setEnabled(context, on)
        }
        var controllerKeys by remember { mutableStateOf(it.palsoftware.pastiera.gaming.GamesActivity.controllerKeys(context)) }
        FluxSwitchRow(
            linkId = null,
            title = stringResource(R.string.games_app_controller_keys),
            description = stringResource(R.string.games_app_controller_keys_description),
            checked = controllerKeys
        ) { on ->
            controllerKeys = on
            it.palsoftware.pastiera.gaming.GamesActivity.setControllerKeys(context, on)
        }
        FluxActionRow(
            linkId = null,
            icon = "+",
            title = stringResource(R.string.games_pin_all),
            description = stringResource(R.string.games_pin_all_description)
        ) { it.palsoftware.pastiera.gaming.GamesActivity.pinAll(context) }
    }

    if (picking) {
        AppPickerDialog(
            onAppSelected = { app: InstalledApp ->
                picking = false
                editing = GameProfiles.ensureApp(context, app.packageName, app.appName).first()
                refresh()
            },
            onDismiss = { picking = false }
        )
    }
    val pickGame: (GameLibrary.Game) -> Unit = { game ->
        importing = false
        addingOwn = false
        // Already found by itself: that one, set up now; else a new one with its app's settings
        editing = GameProfiles.profileFor(context, game)
        refresh()
        if (game.packageName == it.palsoftware.pastiera.gaming.GameNativeBridge.PACKAGE) gameNativeSetup = true
    }
    if (importing) GameImportDialog(onPicked = pickGame, onByHand = { importing = false; addingOwn = true }, onDismiss = { importing = false })
    if (addingOwn) OwnGameDialog(onAdded = pickGame, onDismiss = { addingOwn = false })
    if (addingXbox && xboxApp != null) XboxCloudDialog(xboxApp, onPicked = { game -> addingXbox = false; pickGame(game) }, onDismiss = { addingXbox = false })
    if (gameNativeSetup) {
        AlertDialog(
            onDismissRequest = { gameNativeSetup = false },
            title = { Text(stringResource(R.string.game_mode_gamenative_save)) },
            text = { Text(stringResource(R.string.game_mode_gamenative_steps)) },
            confirmButton = {
                TextButton(onClick = {
                    gameNativeSetup = false
                    context.packageManager.getLaunchIntentForPackage(it.palsoftware.pastiera.gaming.GameNativeBridge.PACKAGE)
                        ?.let { launch -> runCatching { context.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                }) { Text(stringResource(R.string.game_mode_gamenative_open)) }
            },
            dismissButton = { TextButton(onClick = { gameNativeSetup = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/**
 * A game you add yourself, like GameNative's and GameHub's custom games: its name, the launcher
 * or emulator you added it to, and its file (an emulator's game, a launcher's exported file) or ID.
 */
@Composable
internal fun OwnGameDialog(onAdded: (GameLibrary.Game) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val folders = it.palsoftware.pastiera.gaming.GameFolders
    val players = remember { folders.installedPlayers(context) }
    var name by remember { mutableStateOf("") }
    var player by remember { mutableStateOf(players.firstOrNull()) }
    var file by remember { mutableStateOf<android.net.Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var id by remember { mutableStateOf("") }
    val stores = listOf("STEAM" to "Steam", "EPIC" to "Epic", "GOG" to "GOG", "AMAZON" to "Amazon", "CUSTOM_GAME" to stringResource(R.string.game_mode_own_game_custom_store), "LOCAL" to "GameHub")
    var store by remember { mutableStateOf(stores.first()) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        file = uri
        fileName = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
        if (name.isBlank()) name = fileName?.substringBeforeLast('.').orEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_mode_own_game)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (players.isEmpty()) {
                    Text(stringResource(R.string.game_mode_own_game_none))
                    return@Column
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(R.string.game_mode_profile_name)) }, modifier = Modifier.fillMaxWidth())
                ChoiceRow(stringResource(R.string.game_mode_own_game_added_to), player.orEmpty(), players) { player = players[it] }
                val launcher = player?.let { folders.isLauncher(it) } == true
                TextButton(onClick = { runCatching { picker.launch(arrayOf("*/*")) } }) {
                    Text(fileName ?: stringResource(if (launcher) R.string.game_mode_own_game_export_file else R.string.game_mode_own_game_file))
                }
                if (launcher && file == null) {
                    OutlinedTextField(value = id, onValueChange = { id = it.trim() }, singleLine = true,
                        label = { Text(stringResource(R.string.game_mode_own_game_id)) }, modifier = Modifier.fillMaxWidth())
                    ChoiceRow(stringResource(R.string.game_mode_own_game_store), store.second, stores.map { it.second }) { store = stores[it] }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = player != null && name.isNotBlank(), onClick = {
                val game = folders.customGame(context, name.trim(), player ?: return@TextButton, file, id, store.first)
                if (game == null) {
                    android.widget.Toast.makeText(context, R.string.game_mode_own_game_none, android.widget.Toast.LENGTH_LONG).show()
                } else onAdded(game)
            }) { Text(stringResource(R.string.game_mode_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/**
 * Your own game folders: the ones set in a launcher or emulator (picked with Android's folder
 * picker), each read for games of the player it's set to, or by its folders' names.
 */
@Composable
private fun CustomGameFolders() {
    val context = LocalContext.current
    val folders = it.palsoftware.pastiera.gaming.GameFolders
    var custom by remember { mutableStateOf(folders.custom(context)) }
    fun save(list: List<it.palsoftware.pastiera.gaming.GameFolders.Custom>) {
        custom = list
        folders.setCustom(context, list)
    }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        val path = tree?.let { folders.pathOf(it) } ?: return@rememberLauncherForActivityResult
        if (custom.none { it.path == path }) save(custom + it.palsoftware.pastiera.gaming.GameFolders.Custom(path))
    }
    val byNames = stringResource(R.string.game_mode_folder_by_names)
    custom.forEach { folder ->
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(folder.path.removePrefix("/storage/emulated/0/"), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = { save(custom - folder) }) { Text(stringResource(R.string.game_mode_remove)) }
            }
            ChoiceRow(stringResource(R.string.game_mode_folder_games_of), folder.player ?: byNames, listOf(byNames) + folders.playerNames) { index ->
                val player = if (index == 0) null else folders.playerNames[index - 1]
                save(custom.map { if (it == folder) it.copy(player = player) else it })
            }
        }
    }
    FluxActionRow(
        linkId = null,
        icon = "+",
        title = stringResource(R.string.game_mode_folder_add),
        description = stringResource(R.string.game_mode_folder_add_description)
    ) { runCatching { picker.launch(null) } }
}


/** The games of the game launchers installed, one tap making a profile for it. */
@Composable
internal fun GameImportDialog(onPicked: (GameLibrary.Game) -> Unit, onByHand: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var games by remember { mutableStateOf<List<GameLibrary.Game>?>(null) }
    LaunchedEffect(Unit) {
        games = withContext(Dispatchers.IO) {
            GameLibrary.launchers(context).flatMap { launcher ->
                GameLibrary.games(launcher.packageName) +
                    // The launcher itself, for a profile covering all its games
                    GameLibrary.Game(launcher.appName, launcher.packageName)
            }.let { fromLaunchers ->
                // The game folders' games first: an exported file knows the game's store and ID
                // exactly, where a home screen shortcut only gives its ID
                val fromFolders = it.palsoftware.pastiera.gaming.GameFolders.games(context)
                val known = fromFolders.map { it.name.lowercase() to it.packageName }.toSet()
                fromFolders + fromLaunchers.filter { (it.name.lowercase() to it.packageName) !in known }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_mode_import)) },
        text = {
            Column {
                val list = games
                when {
                    list == null -> Text(stringResource(R.string.game_mode_import_reading))
                    list.isEmpty() -> Text(stringResource(R.string.game_mode_import_none))
                    else -> list.forEach { game ->
                        Text(
                            "${game.name}  (${context.appLabel(game.packageName)})",
                            modifier = Modifier.fillMaxWidth().clickable { onPicked(game) }.padding(vertical = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        // A game added to a launcher or emulator by hand
        dismissButton = { TextButton(onClick = onByHand) { Text(stringResource(R.string.game_mode_own_game)) } }
    )
}

/** One profile: its name, trackpad halves, screen, home screen shortcut and apps. */
@Composable
internal fun GameProfileEditor(profile: GameProfile, onDone: (GameProfile?) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(profile) }
    var addingApp by remember { mutableStateOf(false) }
    // The phone's Back keeps the changes too, as the arrow does
    androidx.activity.compose.BackHandler { onDone(current) }
    FluxScreenScaffold(current.name, { onDone(current) }, Modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = current.name,
                onValueChange = { current = current.copy(name = it) },
                label = { Text(stringResource(R.string.game_mode_profile_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            // A game's art and screen: its own, whatever its controls follow
            if (current.isGame) {
                GameArtworkRow(current)
                ScreenChoice(current) { current = it }
                DualScreenRows(current) { current = it }
                ResolutionChoices(current) { current = it }
            }
            // A Dolphin Wii game: the Wii controller that suits it, set in its own Dolphin settings
            if (current.isGame) DolphinControllerRows(current) { changed -> current = changed }
            // Keys of its own, for anything gaming mode sends buttons to (Dolphin reads the keyboard itself: its own above)
            if (current.packages.none { p -> GameApps.emulator(p) == "Dolphin" }) GameKeysRow(current) { changed -> current = changed }
            // A game plays with its app's settings, unless it needs its own (sticks elsewhere)
            if (current.isGame) {
                val ownScope = androidx.compose.runtime.rememberCoroutineScope()
                val appName = current.packages.firstOrNull()?.let { pkg -> context.appLabel(pkg) }.orEmpty()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.game_mode_own_settings))
                        Text(
                            stringResource(if (current.own) R.string.game_mode_own_settings_on else R.string.game_mode_own_settings_off, appName),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = current.own,
                        onCheckedChange = { on ->
                            if (!on) current = current.copy(own = false)
                            // Starting from the app's settings, so only what differs needs changing
                            else ownScope.launch {
                                val app = withContext(Dispatchers.IO) { GameProfiles.effective(context, current.copy(own = false)) }
                                current = current.copy(
                                    own = true, leftHalf = app.leftHalf, rightHalf = app.rightHalf, stickZones = app.stickZones, taps = app.taps,
                                    screen = app.screen, swapFace = app.swapFace
                                )
                            }
                        }
                    )
                }
            }
            if (!current.isGame || current.own) {
            ChoiceRow(stringResource(R.string.game_mode_left_half), current.leftHalf.label, TrackpadRole.entries.map { it.label }) {
                current = current.copy(leftHalf = TrackpadRole.entries[it])
            }
            ChoiceRow(stringResource(R.string.game_mode_right_half), current.rightHalf.label, TrackpadRole.entries.map { it.label }) {
                current = current.copy(rightHalf = TrackpadRole.entries[it])
            }
            // Nintendo's A and B (X and Y) where they sit on its controllers
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.game_mode_swap_face))
                    Text(stringResource(R.string.game_mode_swap_face_description), style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.Switch(checked = current.swapsFace, onCheckedChange = { on -> current = current.copy(swapFace = on) })
            }
            // An app's screen choice (a game's sits with its art, below its name)
            if (!current.isGame) {
                // Its tile in the Games app
                GameArtworkRow(current)
                ScreenChoice(current) { current = it }
                DualScreenRows(current) { current = it }
            }
            // An emulator: Flux Keyboard's layout written into its own controller settings; other
            // apps map the keys themselves
            val emulatorPkg = current.packages.firstOrNull { pkg -> it.palsoftware.pastiera.gaming.GameApps.emulator(pkg) != null }
            if (emulatorPkg == null) Text(
                when (val app = GameApps.appFor(current)) {
                    // Cloud gaming takes no keyboard: a real controller, or its touch controls
                    GameApps.XBOX_NAME -> stringResource(R.string.game_mode_xbox_note)
                    null -> stringResource(R.string.game_mode_keys_pass)
                    else -> stringResource(R.string.game_mode_keys_pass_app, app)
                },
                style = MaterialTheme.typography.bodySmall
            )
            if (emulatorPkg != null) {
                val emulatorName = it.palsoftware.pastiera.gaming.GameApps.emulator(emulatorPkg).orEmpty()
                var setupResult by remember { mutableStateOf<String?>(null) }
                val setupScope = androidx.compose.runtime.rememberCoroutineScope()
                TextButton(onClick = {
                    setupScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            it.palsoftware.pastiera.gaming.FluxLayout.apply(context, emulatorPkg).also { done ->
                                // The games' own Dolphin profiles built on the layout just written
                                if (done == it.palsoftware.pastiera.gaming.FluxLayout.Result.DONE && GameApps.emulator(emulatorPkg) == "Dolphin")
                                    it.palsoftware.pastiera.gaming.FluxLayout.refreshGames(context, emulatorPkg)
                            }
                        }
                        if (result == it.palsoftware.pastiera.gaming.FluxLayout.Result.DONE) it.palsoftware.pastiera.gaming.FluxLayout.markWritten(context, emulatorPkg)
                        // Its graphics too, where its settings can be written: suited to this phone's GPU
                        val graphics = withContext(Dispatchers.IO) {
                            if (it.palsoftware.pastiera.gaming.GraphicsTuning.writable(emulatorPkg) &&
                                it.palsoftware.pastiera.gaming.GraphicsTuning.tune(context, emulatorPkg) == it.palsoftware.pastiera.gaming.FluxLayout.Result.DONE
                            ) "\n\n" + it.palsoftware.pastiera.gaming.GraphicsTuning.summary(context, emulatorPkg) else ""
                        }
                        setupResult = when (result) {
                            it.palsoftware.pastiera.gaming.FluxLayout.Result.DONE -> context.getString(R.string.game_mode_setup_done, emulatorName) + graphics
                            it.palsoftware.pastiera.gaming.FluxLayout.Result.NOT_FOUND -> context.getString(R.string.game_mode_setup_not_found, emulatorName)
                            it.palsoftware.pastiera.gaming.FluxLayout.Result.BY_HAND ->
                                context.getString(R.string.game_mode_setup_by_hand, emulatorName, it.palsoftware.pastiera.gaming.FluxLayout.bindList())
                            it.palsoftware.pastiera.gaming.FluxLayout.Result.FAILED -> context.getString(R.string.adb_not_running)
                        }
                    }
                }) { Text(stringResource(R.string.game_mode_setup_controls, emulatorName)) }
                // Everything Flux Keyboard set in the emulator, put back as it was
                if (it.palsoftware.pastiera.gaming.GraphicsTuning.writable(emulatorPkg)) {
                    var resetting by remember { mutableStateOf(false) }
                    TextButton(onClick = { resetting = true }) { Text(stringResource(R.string.game_mode_reset_emulator, emulatorName)) }
                    if (resetting) AlertDialog(
                        onDismissRequest = { resetting = false },
                        title = { Text(stringResource(R.string.game_mode_reset_emulator, emulatorName)) },
                        text = { Text(stringResource(R.string.game_mode_reset_emulator_confirm, emulatorName)) },
                        confirmButton = {
                            TextButton(onClick = {
                                resetting = false
                                setupScope.launch {
                                    val result = withContext(Dispatchers.IO) { it.palsoftware.pastiera.gaming.FluxLayout.reset(emulatorPkg) }
                                    setupResult = when (result) {
                                        it.palsoftware.pastiera.gaming.FluxLayout.Result.DONE -> context.getString(R.string.game_mode_reset_emulator_done, emulatorName)
                                        it.palsoftware.pastiera.gaming.FluxLayout.Result.NOT_FOUND -> context.getString(R.string.game_mode_setup_not_found, emulatorName)
                                        else -> context.getString(R.string.adb_not_running)
                                    }
                                }
                            }) { Text(stringResource(R.string.game_mode_reset)) }
                        },
                        dismissButton = { TextButton(onClick = { resetting = false }) { Text(stringResource(R.string.cancel)) } }
                    )
                }
                setupResult?.let { message ->
                    AlertDialog(
                        onDismissRequest = { setupResult = null },
                        title = { Text(stringResource(R.string.game_mode_setup_controls, emulatorName)) },
                        text = { Text(message) },
                        confirmButton = { TextButton(onClick = { setupResult = null }) { Text(stringResource(android.R.string.ok)) } }
                    )
                }
            }
            }
            // Apps whose graphics settings can't be written: what suits this phone, to set by hand
            val advice = remember(current.packages) {
                val app = GameApps.appFor(current)
                if (current.packages.any { pkg -> it.palsoftware.pastiera.gaming.GraphicsTuning.writable(pkg) }) null
                else runCatching { it.palsoftware.pastiera.gaming.GraphicsTuning.advice(context, app) }.getOrNull()
            }
            if (advice != null) {
                var showAdvice by remember { mutableStateOf(false) }
                FluxActionRow(
                    linkId = null,
                    icon = "\u2699",
                    title = stringResource(R.string.game_mode_advice),
                    description = stringResource(R.string.game_mode_advice_description)
                ) { showAdvice = true }
                if (showAdvice) AlertDialog(
                    onDismissRequest = { showAdvice = false },
                    title = { Text(stringResource(R.string.game_mode_advice)) },
                    text = { Text(advice) },
                    confirmButton = { TextButton(onClick = { showAdvice = false }) { Text(stringResource(android.R.string.ok)) } }
                )
            }
            val pinScope = androidx.compose.runtime.rememberCoroutineScope()
            if (current.isGame) TextButton(onClick = {
                val profile = current.also { saved -> GameProfiles.save(context, saved) }
                pinScope.launch {
                    // The game's art for its icon, as in the profile list (fetched if need be)
                    val art = withContext(Dispatchers.IO) {
                        runCatching { it.palsoftware.pastiera.gaming.GameArt.load(context, profile) }.getOrNull()
                    }
                    if (!it.palsoftware.pastiera.gaming.GameLaunchActivity.pin(context, profile, art)) {
                        android.widget.Toast.makeText(context, R.string.game_mode_home_screen_failed, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }) { Text(stringResource(R.string.game_mode_home_screen)) }
            // GameNative: its on-screen controller to turn on, for the trackpad's sticks
            if (!current.isGame && GameApps.appFor(current) == "GameNative") {
                var steps by remember { mutableStateOf(false) }
                FluxActionRow(
                    linkId = null,
                    icon = "\u2913",
                    title = stringResource(R.string.game_mode_gamenative_save),
                    description = stringResource(R.string.game_mode_gamenative_save_description)
                ) { steps = true }
                if (steps) AlertDialog(
                    onDismissRequest = { steps = false },
                    title = { Text(stringResource(R.string.game_mode_gamenative_save)) },
                    text = { Text(stringResource(R.string.game_mode_gamenative_steps)) },
                    confirmButton = { TextButton(onClick = { steps = false }) { Text(stringResource(android.R.string.ok)) } }
                )
            }
            // An app's own parts that play its games under another name (a launcher's player)
            if (!current.isGame) Text(stringResource(R.string.game_mode_apps), style = MaterialTheme.typography.titleMedium)
            if (!current.isGame) current.packages.forEach { pkg ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(context.appLabel(pkg), modifier = Modifier.weight(1f))
                    TextButton(onClick = { current = current.copy(packages = current.packages - pkg) }) {
                        Text(stringResource(R.string.game_mode_remove))
                    }
                }
            }
            if (!current.isGame) TextButton(onClick = { addingApp = true }) { Text(stringResource(R.string.game_mode_add_app)) }
            Spacer(Modifier.height(8.dp))
            Row {
                TextButton(onClick = { onDone(current) }) { Text(stringResource(R.string.game_mode_save)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDelete) { Text(stringResource(R.string.game_mode_delete)) }
            }
        }
    }
    if (addingApp) {
        AppPickerDialog(
            onAppSelected = { app ->
                addingApp = false
                current = current.copy(packages = current.packages + app.packageName)
            },
            onDismiss = { addingApp = false },
            excludePackages = current.packages
        )
    }
}

/** A label and its value, the value picked from a menu. */
/**
 * A Dolphin game's controller, set in its own Dolphin settings: for a Wii game, Wii Remote and
 * Nunchuk, Classic Controller, held sideways or a GameCube controller; and for any game, keys of
 * its own for that controller's buttons, drawn as the controller ([DolphinPadRemapper]).
 */
/**
 * A DS, 3DS or Wii U emulator's screens: which shows, which is bigger, which comes first, written
 * into the emulator where its settings can be reached (choosing one sets Screen to suit it), else
 * a note on where to set them.
 */
@Composable
private fun DualScreenRows(profile: GameProfile, onChanged: (GameProfile) -> Unit) {
    val context = LocalContext.current
    val pkg = profile.packages.firstOrNull { p -> GameApps.twoScreens(p) != null || GameApps.emulator(p) == "Cemu" } ?: return
    val appName = context.appLabel(pkg)
    val layouts = DualScreens.layouts(pkg)
    if (layouts.isEmpty()) {
        Text(
            if (GameApps.emulator(pkg) == "Cemu") stringResource(R.string.dual_screens_note_cemu)
            else stringResource(R.string.dual_screens_note_other, appName),
            style = MaterialTheme.typography.bodySmall
        )
        return
    }
    val labels = mapOf(
        DualScreens.Layout.STACKED to R.string.dual_screens_stacked,
        DualScreens.Layout.STACKED_BOTTOM_FIRST to R.string.dual_screens_stacked_bottom_first,
        DualScreens.Layout.TOP_LARGE to R.string.dual_screens_top_large,
        DualScreens.Layout.BOTTOM_LARGE to R.string.dual_screens_bottom_large,
        DualScreens.Layout.SIDE_BY_SIDE to R.string.dual_screens_side_by_side,
        DualScreens.Layout.TOP_ONLY to R.string.dual_screens_top_only,
        DualScreens.Layout.BOTTOM_ONLY to R.string.dual_screens_bottom_only
    ).mapValues { stringResource(it.value) }
    val names = listOf(stringResource(R.string.dual_screens_as_set, appName)) + layouts.map { labels.getValue(it) }
    val chosen = DualScreens.layout(profile.screens)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    ChoiceRow(stringResource(R.string.dual_screens_title), chosen?.let { labels[it] } ?: names[0], names) { index ->
        val layout = layouts.getOrNull(index - 1)
        // The screen that suits it: one above the other upright, the rest landscape
        val changed = if (layout == null) profile.copy(screens = null)
            else profile.copy(screens = layout.name, screen = layout.screen, ownScreen = profile.isGame || profile.ownScreen)
        onChanged(changed)
        // An app's layout written now (a game's as it starts), for games started from the emulator itself
        if (layout != null && !profile.isGame) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val window = DualScreens.window(context, layout.landscape, profile.phoneHeight)
                window != null && DualScreens.apply(pkg, layout, window)
            }
            if (!ok) android.widget.Toast.makeText(context, context.getString(R.string.game_mode_setup_not_found, appName), android.widget.Toast.LENGTH_LONG).show()
        }
    }
    Text(
        stringResource(if (DualScreens.appliesAtNextStart(pkg)) R.string.dual_screens_note_noods else R.string.dual_screens_note_written, appName),
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun DolphinControllerRows(profile: GameProfile, onChanged: (GameProfile) -> Unit) {
    val context = LocalContext.current
    val pkg = profile.packages.firstOrNull { p -> GameApps.emulator(p) == "Dolphin" } ?: return
    // The game's ID, and whether it's a Wii game (read from its file through the shell)
    var game by remember(profile.launch) { mutableStateOf<Pair<String, Boolean>?>(null) }
    LaunchedEffect(profile.launch) {
        game = withContext(Dispatchers.IO) {
            val path = profile.launch?.let { launch -> GameFiles.gameFile(launch) } ?: return@withContext null
            GameIds.id(context, "Dolphin", path)?.let { it to GameFiles.isWii(path) }
        }
    }
    val (id, wii) = game ?: return
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val names = listOf(
        stringResource(R.string.game_mode_wii_nunchuk),
        stringResource(R.string.game_mode_wii_classic),
        stringResource(R.string.game_mode_wii_sideways),
        stringResource(R.string.game_mode_wii_gamecube)
    )
    val pad = if (!wii) FluxLayout.WiiPad.GAMECUBE
        else FluxLayout.WiiPad.entries.firstOrNull { it.name == profile.wiiPad } ?: FluxLayout.WiiPad.NUNCHUK
    /** Written into Dolphin now (it's also written again each time the game starts from Games). */
    fun apply(changed: GameProfile, message: String) {
        val chosen = if (!wii) FluxLayout.WiiPad.GAMECUBE
            else FluxLayout.WiiPad.entries.firstOrNull { it.name == changed.wiiPad } ?: FluxLayout.WiiPad.NUNCHUK
        scope.launch {
            val result = withContext(Dispatchers.IO) { FluxLayout.setGamePad(context, pkg, id, wii, chosen, changed.padRemap(chosen), activate = false, keepChosen = changed.keepDolphinProfile) }
            android.widget.Toast.makeText(
                context,
                when (result) {
                    FluxLayout.Result.DONE -> message
                    FluxLayout.Result.NOT_FOUND -> context.getString(R.string.game_mode_setup_not_found, "Dolphin")
                    else -> context.getString(R.string.adb_not_running)
                },
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    if (wii) ChoiceRow(stringResource(R.string.game_mode_wii_controller), names[pad.ordinal], names) { index ->
        val picked = FluxLayout.WiiPad.entries[index]
        val changed = profile.copy(wiiPad = picked.name.takeIf { picked != FluxLayout.WiiPad.NUNCHUK })
        onChanged(changed)
        apply(changed, context.getString(R.string.game_mode_wii_set, names[index]))
    }
    // When it all takes effect, and what doesn't follow
    FluxNote(stringResource(R.string.dolphin_game_applied_note))
    // Flux Keyboard's controls, or a profile chosen for the game in Dolphin itself
    FluxSwitchRow(
        linkId = null,
        title = stringResource(R.string.dolphin_use_flux_title),
        description = stringResource(R.string.dolphin_use_flux_description),
        checked = !profile.keepDolphinProfile
    ) { on ->
        val changed = profile.copy(keepDolphinProfile = !on)
        onChanged(changed)
        apply(changed, context.getString(R.string.dolphin_pad_saved))
    }
    // Dolphin's on-screen controls in this game, set as it starts from Games
    val overlays = listOf(
        stringResource(R.string.dolphin_overlay_off),
        stringResource(R.string.dolphin_overlay_invisible),
        stringResource(R.string.dolphin_overlay_shown)
    )
    ChoiceRow(stringResource(R.string.dolphin_overlay_title), overlays[profile.overlayMode.ordinal], overlays) { index ->
        onChanged(profile.copy(dolphinOverlay = FluxLayout.DolphinOverlay.entries[index].name))
    }
    var remapping by remember { mutableStateOf(false) }
    FluxActionRow(
        linkId = null,
        icon = "\u271A",
        title = stringResource(R.string.dolphin_pad_row),
        description = stringResource(R.string.dolphin_pad_row_description, names[pad.ordinal])
    ) { remapping = true }
    if (remapping) DolphinPadRemapper(
        pad = pad,
        padName = names[pad.ordinal],
        remap = profile.padRemap(pad),
        swap = FluxLayout.swapFor(context, pkg),
        onSave = { remap ->
            if (remap == profile.padRemap(pad)) return@DolphinPadRemapper
            val text = DolphinPads.format(remap)
            val changed = profile.copy(padControls = if (remap.isEmpty()) profile.padControls - pad.name else profile.padControls + (pad.name to text))
            onChanged(changed)
            apply(changed, context.getString(R.string.dolphin_pad_saved))
        },
        onDismiss = { remapping = false }
    )
}

/** A game's or app's own keys, on top of Keyboard controls: drawn as the keyboard, changed there. */
@Composable
private fun GameKeysRow(profile: GameProfile, onChanged: (GameProfile) -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    val own = remember(profile.keyOverrides) { FluxLayout.parseOverrides(profile.keyOverrides.orEmpty()) }
    FluxActionRow(
        linkId = null,
        icon = "\u2328",
        title = stringResource(if (profile.isGame) R.string.game_keys_row else R.string.game_keys_row_app),
        description = stringResource(R.string.game_keys_description)
    ) { editing = true }
    if (editing) androidx.compose.ui.window.Dialog(
        onDismissRequest = { editing = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val base = remember { FluxLayout.loadOverrides(context); true }
        KeyLayoutEditor(
            title = profile.name,
            initial = own,
            base = { code, layer -> if (base) FluxLayout.layoutControlFor(code, FluxLayout.modifiersOf(layer)) else null },
            onChange = { changed -> onChanged(profile.copy(keyOverrides = FluxLayout.formatOverrides(changed).ifEmpty { null })) },
            onBack = { editing = false },
            modifier = Modifier.fillMaxSize(),
            note = stringResource(R.string.game_keys_note)
        )
    }
}

/** Xbox Cloud Gaming's games, searched by name: one picked becomes a game of its own. */
@Composable
internal fun XboxCloudDialog(
    pkg: String,
    onPicked: (GameLibrary.Game) -> Unit,
    onDismiss: () -> Unit,
    /** A tap plays the game straight away (the Games app); else a tap picks it, as [onPicked]. */
    onPlay: ((GameLibrary.Game) -> Unit)? = null,
    onAdded: () -> Unit = {}
) {
    val context = LocalContext.current
    // A game long-pressed: added to the games (the Games app and Moonlight), or its settings
    var menuFor by remember { mutableStateOf<it.palsoftware.pastiera.gaming.XboxCloud.CloudGame?>(null) }
    menuFor?.let { cloud ->
        val game = it.palsoftware.pastiera.gaming.XboxCloud.asGame(cloud, pkg)
        val profile = remember(cloud.id) { GameProfiles.profileFor(context, game) }
        val added = remember(cloud.id) { GameProfiles.all(context).any { p -> p.id == profile.id } }
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(cloud.name) },
            text = { Text(stringResource(if (added) R.string.game_mode_xbox_in_games else R.string.game_mode_xbox_add_to_games_description)) },
            confirmButton = {
                if (added) TextButton(onClick = {
                    GameProfiles.delete(context, profile.id)
                    menuFor = null
                    onAdded()
                }) { Text(stringResource(R.string.game_mode_xbox_remove_from_games)) }
                if (!added) androidx.compose.material3.Button(onClick = {
                    GameProfiles.save(context, profile)
                    android.widget.Toast.makeText(context, context.getString(R.string.game_mode_xbox_added, cloud.name), android.widget.Toast.LENGTH_SHORT).show()
                    menuFor = null
                    onAdded()
                }) { Text(stringResource(R.string.game_mode_xbox_add_to_games)) }
            },
            dismissButton = {
                Row {
                    if (onPlay != null) TextButton(onClick = { menuFor = null; onPicked(game) }) { Text(stringResource(R.string.games_app_settings_short)) }
                    TextButton(onClick = { menuFor = null }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }
    var games by remember { mutableStateOf<List<it.palsoftware.pastiera.gaming.XboxCloud.CloudGame>?>(null) }
    var query by remember { mutableStateOf("") }
    // Signed in first: the games only play once Xbox knows who's playing
    val signedIn = remember { it.palsoftware.pastiera.gaming.XboxCloud.signedIn() }
    if (!signedIn) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.game_mode_xbox_add)) },
            text = { Text(stringResource(R.string.game_mode_xbox_sign_in_first)) },
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
                    runCatching { context.startActivity(it.palsoftware.pastiera.gaming.XboxPlayActivity.signIn(context)) }
                    onDismiss()
                }) { Text(stringResource(R.string.game_mode_xbox_sign_in)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
        )
        return
    }
    LaunchedEffect(Unit) { games = withContext(Dispatchers.IO) { it.palsoftware.pastiera.gaming.XboxCloud.games(context) } }
    // A grid of the games' covers, a search above: nothing else
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text(stringResource(R.string.game_mode_xbox_search)) }, modifier = Modifier.fillMaxWidth()
                )
                val list = games
                when {
                    list == null -> Text(stringResource(R.string.game_mode_import_reading), modifier = Modifier.padding(top = 12.dp))
                    list.isEmpty() -> Text(stringResource(R.string.game_mode_xbox_none), modifier = Modifier.padding(top = 12.dp))
                    else -> {
                        // Xbox Cloud Gaming itself first, for every game
                        val shown = (listOf(it.palsoftware.pastiera.gaming.XboxCloud.HOME) + list)
                            .filter { game -> query.isBlank() || game.name.contains(query.trim(), ignoreCase = true) }
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(96.dp),
                            modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(shown.size, key = { shown[it].id.ifEmpty { "home" } }) { i ->
                                val game = shown[i]
                                CloudGameTile(game, onLongClick = { if (game.id.isNotEmpty()) menuFor = game }) {
                                    val picked = it.palsoftware.pastiera.gaming.XboxCloud.asGame(game, pkg)
                                    onPlay?.invoke(picked) ?: onPicked(picked)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A cloud game's cover and name. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun CloudGameTile(game: it.palsoftware.pastiera.gaming.XboxCloud.CloudGame, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    val context = LocalContext.current
    val image by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, game.art) {
        value = withContext(Dispatchers.IO) {
            // Xbox Cloud Gaming itself: the XCloud Games tile's art, as in the Games app
            if (game.id.isEmpty()) runCatching { it.palsoftware.pastiera.gaming.GameArt.xboxTile(context)?.asImageBitmap() }.getOrNull()
            else game.art?.takeIf { it.isNotEmpty() }?.let { url -> it.palsoftware.pastiera.gaming.GameArt.thumbnail(url)?.asImageBitmap() }
        }
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    Column(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape)
                .then(Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)),
            contentAlignment = Alignment.Center
        ) {
            image?.let {
                androidx.compose.foundation.Image(bitmap = it, contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize())
            } ?: Text("\u2601", style = MaterialTheme.typography.headlineMedium)
        }
        Text(game.name, style = MaterialTheme.typography.bodySmall, maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ChoiceRow(title: String, value: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.primary)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { open = false; onPick(index) })
            }
        }
    }
}

/**
 * How the screen turns while it plays: as the app wants, or kept upright (a landscape-shaped
 * screen). A game can also follow its app's choice, unless it has all its own settings.
 */
@Composable
private fun ScreenChoice(profile: GameProfile, onChange: (GameProfile) -> Unit) {
    val screens = it.palsoftware.pastiera.gaming.ScreenMode.entries
    val follows = profile.isGame && !profile.own
    val names = listOfNotNull(
        if (follows) stringResource(R.string.game_mode_screen_follow) else null,
        stringResource(R.string.game_mode_screen_app),
        stringResource(R.string.game_mode_screen_sideways),
        stringResource(R.string.game_mode_screen_upright)
    )
    val chosen = if (follows && !profile.ownScreen) 0 else profile.screen.ordinal + if (follows) 1 else 0
    Column {
        ChoiceRow(stringResource(R.string.game_mode_screen), names[chosen], names) { index ->
            onChange(
                when {
                    follows && index == 0 -> profile.copy(ownScreen = false)
                    else -> profile.copy(ownScreen = profile.isGame, screen = screens[index - if (follows) 1 else 0])
                }
            )
        }
        if (!(follows && !profile.ownScreen)) Text(
            stringResource(
                when (profile.screen) {
                    it.palsoftware.pastiera.gaming.ScreenMode.APP -> R.string.game_mode_screen_app_description
                    it.palsoftware.pastiera.gaming.ScreenMode.SIDEWAYS -> R.string.game_mode_screen_sideways_description
                    it.palsoftware.pastiera.gaming.ScreenMode.UPRIGHT -> R.string.game_mode_screen_upright_description
                }
            ),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** A game's sizes: on the phone (kept upright, its shape kept) and while streaming. */
@Composable
private fun ResolutionChoices(profile: GameProfile, onChange: (GameProfile) -> Unit) {
    val heights = listOf(null, 1080, 900, 720, 540)
    val phoneNames = listOf(stringResource(R.string.game_mode_resolution_full)) + heights.drop(1).map { "${it}p" }
    Column {
        ChoiceRow(stringResource(R.string.game_mode_resolution_phone), phoneNames[heights.indexOf(profile.phoneHeight).coerceAtLeast(0)], phoneNames) { index ->
            onChange(profile.copy(phoneHeight = heights[index]))
        }
        Text(stringResource(R.string.game_mode_resolution_phone_description), style = MaterialTheme.typography.bodySmall)
        val streamHeights = listOf(null, 720, 1080, 1440)
        val streamNames = listOf(stringResource(R.string.game_mode_resolution_stream_default)) + streamHeights.drop(1).map { "${it}p" }
        ChoiceRow(stringResource(R.string.game_mode_resolution_stream), streamNames[streamHeights.indexOf(profile.streamHeight).coerceAtLeast(0)], streamNames) { index ->
            onChange(profile.copy(streamHeight = streamHeights[index]))
        }
    }
}

/** A game's art, picked from the phone's pictures for its list entry and Moonlight, or the art found for it. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun GameArtworkRow(profile: GameProfile) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var version by remember { mutableLongStateOf(it.palsoftware.pastiera.gaming.GameArt.version(context, profile)) }
    fun changed() {
        it.palsoftware.pastiera.gaming.GameArt.forget(context, profile)
        version = it.palsoftware.pastiera.gaming.GameArt.version(context, profile)
        val app = context.applicationContext
        Thread { runCatching { it.palsoftware.pastiera.gaming.GamesActivity.sync(app) } }.start()
    }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { it.palsoftware.pastiera.gaming.GameArt.setCustom(context, profile, uri) }
            if (ok) changed()
            else android.widget.Toast.makeText(context, R.string.game_mode_artwork_failed, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.runtime.key(version) { GameArtIcon(profile, version) }
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.game_mode_artwork))
            Text(stringResource(R.string.game_mode_artwork_description), style = MaterialTheme.typography.bodySmall)
        }
    }
    var choosing by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.material3.OutlinedButton(onClick = { choosing = true }) { Text(stringResource(R.string.game_mode_artwork_found)) }
        androidx.compose.material3.OutlinedButton(onClick = {
            picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) { Text(stringResource(R.string.game_mode_artwork_choose)) }
        if (version > 0) androidx.compose.material3.OutlinedButton(onClick = {
            it.palsoftware.pastiera.gaming.GameArt.clearCustom(context, profile)
            changed()
        }) { Text(stringResource(R.string.game_mode_artwork_reset)) }
    }
    if (choosing) ArtChoicesDialog(profile, onDismiss = { choosing = false }) { url, upright ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { it.palsoftware.pastiera.gaming.GameArt.choose(context, profile, url, upright) }
            if (ok) changed()
            else android.widget.Toast.makeText(context, R.string.game_mode_artwork_failed, android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * Every art the sources have for a game, to pick from: icons (its list entry and shortcut) and
 * upright covers (Moonlight). Each tap keeps that one; the dialog stays open to pick the other.
 */
@Composable
private fun ArtChoicesDialog(profile: GameProfile, onDismiss: () -> Unit, onPicked: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    // A title of its own to look for, instead of renaming the game
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf("") }
    val found by androidx.compose.runtime.produceState<Pair<List<it.palsoftware.pastiera.gaming.GameArt.Choice>, List<it.palsoftware.pastiera.gaming.GameArt.Choice>>?>(null, profile.id, searched) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching { it.palsoftware.pastiera.gaming.GameArt.choices(context, profile, searched) }.getOrDefault(emptyList<it.palsoftware.pastiera.gaming.GameArt.Choice>() to emptyList())
        }
    }
    var picked by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_mode_artwork_found)) },
        text = {
            val choices = found
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(profile.name) },
                    label = { Text(stringResource(R.string.game_mode_artwork_search)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { searched = query }),
                    trailingIcon = { TextButton(onClick = { searched = query }) { Text("\u2192") } },
                    modifier = Modifier.fillMaxWidth()
                )
                when {
                    choices == null -> Text(stringResource(R.string.game_mode_import_reading))
                    choices.first.isEmpty() && choices.second.isEmpty() -> Text(stringResource(R.string.game_mode_artwork_none))
                    else -> {
                        for ((title, list, upright) in listOf(
                            Triple(R.string.game_mode_artwork_icons, choices.first, false),
                            Triple(R.string.game_mode_artwork_upright, choices.second, true)
                        )) {
                            if (list.isEmpty()) continue
                            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(list.size) { index ->
                                    val choice = list[index]
                                    ArtThumbnail(choice, upright, choice.full in picked) {
                                        picked = picked + choice.full
                                        onPicked(choice.full, upright)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } }
    )
}

@Composable
private fun ArtThumbnail(choice: it.palsoftware.pastiera.gaming.GameArt.Choice, upright: Boolean, picked: Boolean, onClick: () -> Unit) {
    val image by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, choice.thumb) {
        value = withContext(Dispatchers.IO) { it.palsoftware.pastiera.gaming.GameArt.thumbnail(choice.thumb)?.asImageBitmap() }
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    val size = if (upright) Modifier.size(width = 80.dp, height = 120.dp) else Modifier.size(96.dp)
    Box(
        modifier = size.clip(shape)
            .then(if (picked) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        image?.let {
            androidx.compose.foundation.Image(bitmap = it, contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize())
        } ?: androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

/** Signed in to Xbox in Flux Keyboard's player, checked again whenever the screen comes back. */
internal class XboxAccount(private val context: android.content.Context) {
    var signedIn by mutableStateOf(it.palsoftware.pastiera.gaming.XboxCloud.signedIn())
    var confirming by mutableStateOf(false)

    /** Signs in, or asks before signing out. */
    fun click() {
        if (signedIn) confirming = true
        else runCatching { context.startActivity(it.palsoftware.pastiera.gaming.XboxPlayActivity.signIn(context)) }
    }
}

@Composable
internal fun rememberXboxAccount(): XboxAccount {
    val context = LocalContext.current
    val account = remember { XboxAccount(context) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) account.signedIn = it.palsoftware.pastiera.gaming.XboxCloud.signedIn()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (account.confirming) AlertDialog(
        onDismissRequest = { account.confirming = false },
        title = { Text(stringResource(R.string.game_mode_xbox_sign_out)) },
        text = { Text(stringResource(R.string.game_mode_xbox_sign_out_confirm)) },
        confirmButton = {
            TextButton(onClick = {
                it.palsoftware.pastiera.gaming.XboxCloud.signOut()
                account.signedIn = false
                account.confirming = false
            }) { Text(stringResource(R.string.game_mode_xbox_sign_out)) }
        },
        dismissButton = { TextButton(onClick = { account.confirming = false }) { Text(stringResource(R.string.cancel)) } }
    )
    return account
}

/** A profile's game art, cropped square; its app's icon while there's none. */
@Composable
internal fun GameArtIcon(profile: GameProfile, version: Long = 0) {
    val context = LocalContext.current
    val art by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, profile.id, profile.launch, version) {
        value = withContext(Dispatchers.IO) {
            runCatching { it.palsoftware.pastiera.gaming.GameArt.load(context, profile)?.asImageBitmap() }.getOrNull()
        }
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    val image = art
    if (image != null) {
        androidx.compose.foundation.Image(
            bitmap = image,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(shape)
        )
    } else {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            profile.packages.firstOrNull()?.let { pkg -> FluxAppIcon(pkg) }
        }
    }
}

/** A recording of every touch surface, saved to share: what the trackpad reports. */
@Composable
internal fun TrackpadRecordRow() {
    val context = LocalContext.current
    var recording by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf<Pair<String?, Int>?>(null) }
    var recordFailed by remember { mutableStateOf(false) }
    val recordScope = androidx.compose.runtime.rememberCoroutineScope()
    FluxActionRow(
        linkId = null,
        icon = "\u25CE",
        title = stringResource(if (recording) R.string.game_mode_trackpad_test_running else R.string.game_mode_trackpad_test),
        description = stringResource(R.string.game_mode_trackpad_test_description, it.palsoftware.pastiera.gaming.TrackpadTest.SECONDS)
    ) {
        if (!recording) {
            recording = true
            recordScope.launch {
                val result = withContext(Dispatchers.IO) { it.palsoftware.pastiera.gaming.TrackpadTest.record() }
                recording = false
                if (result == null) recordFailed = true else recorded = result
            }
        }
    }
    recorded?.let { (device, fingers) ->
        AlertDialog(
            onDismissRequest = { recorded = null },
            title = { Text(stringResource(R.string.game_mode_trackpad_test)) },
            text = { Text(stringResource(R.string.game_mode_trackpad_test_result, fingers, device ?: "-")) },
            confirmButton = { TextButton(onClick = { recorded = null }) { Text(stringResource(android.R.string.ok)) } }
        )
    }
    if (recordFailed) AlertDialog(
        onDismissRequest = { recordFailed = false },
        title = { Text(stringResource(R.string.game_mode_trackpad_test)) },
        text = { Text(stringResource(R.string.adb_not_running)) },
        confirmButton = { TextButton(onClick = { recordFailed = false }) { Text(stringResource(android.R.string.ok)) } }
    )
}

/**
 * The app's last times not responding, with where each thread was (its main thread first), kept
 * where the shell can read them; null when there are none.
 */
private fun anrTraces(context: android.content.Context): java.io.File? = runCatching {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return null
    val manager = context.getSystemService(android.app.ActivityManager::class.java) ?: return null
    val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 10)
        .filter { it.reason == android.app.ApplicationExitInfo.REASON_ANR }.take(2)
    if (exits.isEmpty()) return null
    val dir = context.getExternalFilesDir(null) ?: return null
    val file = java.io.File(dir, "anr-traces.txt")
    file.bufferedWriter().use { out ->
        for (exit in exits) {
            out.write("--- ${java.util.Date(exit.timestamp)}: ${exit.description}\n")
            val trace = exit.traceInputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            // The main thread, then the rest up to a size worth sending
            val main = Regex("(?s)\"main\".*?\n\n").find(trace)?.value.orEmpty()
            out.write(main)
            out.write(trace.take(60_000))
            out.write("\n")
        }
    }
    file
}.getOrNull()

/** Android's record of the last crashes (Flux Keyboard's and its web pages'), saved to share. */
@Composable
internal fun CrashLogRow() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    FluxActionRow(
        linkId = null,
        icon = "\u26A0",
        title = stringResource(R.string.crash_log),
        description = stringResource(R.string.crash_log_description)
    ) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val file = "/storage/emulated/0/Download/Flux Keyboard/crash-log.txt"
                val app = it.palsoftware.pastiera.BuildConfig.APPLICATION_ID
                // When the app stopped answering: where its threads were, which only the app can read
                val traces = anrTraces(context)
                it.palsoftware.pastiera.adb.AdbShell.run(
                    "f='$file'; mkdir -p \"\${f%/*}\"; { " +
                        // Why Android last ended the app: a crash, its web page's renderer dying, memory running low
                        "echo '== why it stopped =='; dumpsys activity exit-info $app | head -n 120; " +
                        "echo '== crashes =='; logcat -d -b crash -t 400; " +
                        "echo '== warnings and errors =='; logcat -d -b main,system '*:W' 'ActivityTaskManager:I' | " +
                        "grep -i -E '$app|gamehub|egggame|Permission Denial|quickstep|launcher3|Recents|OverviewProxy|FluxGameMode|FluxGameLaunch|FluxExtraScreen|Stream(Wake|Host|Session|Input|Audio|Control)|AndroidRuntime|chromium|cr_|webview|lowmemorykiller|has died|Killing' | grep -v 'Changing focus' | tail -n 400; " +
                        // The screen and window settings gaming mode and the desktop change
                        // (through a pipe: Android's services can't write into a file in Download)
                        // The input devices (the keyboard, the side buttons): which one each key comes from
                        "echo '== input devices =='; dumpsys input | grep -E '^ +Device -?[0-9]+:' | head -30; " +
                        "echo '== screen and window settings =='; wm size | cat; wm density | cat; " +
                        "for k in force_resizable_activities enable_non_resizable_multi_window enable_freeform_support " +
                        "force_desktop_mode_on_external_displays override_desktop_mode_features override_desktop_experience_features; do " +
                        "echo \"\$k=\$(settings get global \$k | cat)\"; done; " +
                        "echo \"rotation=\$(settings get system user_rotation | cat) auto=\$(settings get system accelerometer_rotation | cat)\"; " +
                        // Recents: whether apps' turning is ignored, and what Recents and its screen are doing
                        "echo \"ignore orientation=\$(cmd window get-ignore-orientation-request | cat)\"; " +
                        "echo '== recents =='; dumpsys activity recents | head -n 12; " +
                        "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|RecentsActivity|quickstep' | head -n 10; " +
                        // The official GameHub's ways in (its activities, actions and links), to start its games directly
                        "for p in com.xiaoji.egggame gamehub.lite; do pm path \$p >/dev/null 2>&1 && { echo \"== \$p ==\"; " +
                        "dumpsys package \$p | grep -E 'Activity|Action:|Scheme:|Authority:|Path' | grep -v -i 'permission' | head -n 150; }; done; " +
                        // Flux Keyboard's own record of gaming mode, streaming and game launches (the phone's turns over in seconds)
                        "echo '== gaming and streaming =='; tail -c 120000 '${context.getExternalFilesDir(null)?.path}/${it.palsoftware.pastiera.FluxLog.FILE_NAME}'; " +
                        (if (traces != null) "echo '== not responding: threads =='; cat '${traces.path}'; " else "") +
                        // Everything through a pipe: Android's services can't write into a file in Download
                        "} 2>&1 | cat > \"\$f\"; echo ok",
                    15_000
                )?.contains("ok") == true
            }
            android.widget.Toast.makeText(
                context, if (saved) R.string.crash_log_saved else R.string.adb_not_running, android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    // A fresh start: Android's logs and Flux Keyboard's own record emptied, so the next crash
    // log holds only what happens from now (and saves quickly)
    FluxActionRow(
        linkId = null,
        icon = "\u2715",
        title = stringResource(R.string.crash_log_clear),
        description = stringResource(R.string.crash_log_clear_description)
    ) {
        scope.launch {
            val cleared = withContext(Dispatchers.IO) {
                runCatching {
                    java.io.File(context.getExternalFilesDir(null), it.palsoftware.pastiera.FluxLog.FILE_NAME).delete()
                }
                it.palsoftware.pastiera.adb.AdbShell.run("logcat -c -b main -b system -b crash; echo ok", 10_000)?.contains("ok") == true
            }
            android.widget.Toast.makeText(
                context, if (cleared) R.string.crash_log_cleared else R.string.adb_not_running, android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}

/**
 * The phone's own game mode (Unihertz's, com.agui.game): it can take over as the home screen,
 * at odds with gaming mode. Turned off here, or back on (through the shell: it's a system app);
 * not shown on phones without it.
 */
@Composable
internal fun PhoneGameModeRow() {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Installed and turned on: null while it's being read, nothing shown when it isn't there
    var state by remember { mutableStateOf<Boolean?>(null) }
    var present by remember { mutableStateOf(false) }
    fun load() = scope.launch {
        val shell = it.palsoftware.pastiera.adb.AdbShell
        val (installed, on) = withContext(Dispatchers.IO) {
            val all = shell.run("pm list packages -s $PHONE_GAME_MODE ; true", 5_000).orEmpty()
            val disabled = shell.run("pm list packages -d $PHONE_GAME_MODE ; true", 5_000).orEmpty()
            fun has(list: String) = list.lines().any { it.trim() == "package:$PHONE_GAME_MODE" }
            has(all) to !has(disabled)
        }
        present = installed
        state = on
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { load() }
    if (!present) return
    FluxSwitchRow(
        linkId = null,
        title = stringResource(R.string.phone_game_mode),
        description = stringResource(R.string.phone_game_mode_description),
        checked = state == true
    ) { want ->
        state = want
        scope.launch {
            withContext(Dispatchers.IO) {
                it.palsoftware.pastiera.adb.AdbShell.run(
                    if (want) "pm enable $PHONE_GAME_MODE" else "pm disable-user --user 0 $PHONE_GAME_MODE", 5_000
                )
            }
            load()
        }
    }
}

private const val PHONE_GAME_MODE = "com.agui.game"

/**
 * Streaming to Moonlight: on or off, and how games stream (the screen, resolution, devices,
 * paired clients). In Gaming settings, and long-pressing Stream in the Games app.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun StreamingSettings(onStreamingChanged: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    var streaming by remember { mutableStateOf(it.palsoftware.pastiera.streaming.StreamHostService.enabled(context)) }
    var paired by remember { mutableIntStateOf(it.palsoftware.pastiera.streaming.StreamHost.pairedCount(context)) }
    FluxSwitchRow(
        linkId = null,
        title = stringResource(R.string.stream_switch),
        description = stringResource(R.string.stream_switch_description),
        checked = streaming
    ) { on ->
        streaming = on
        it.palsoftware.pastiera.streaming.StreamHostService.setEnabled(context, on)
        onStreamingChanged(on)
    }
    // Listed in Moonlight with streaming off, for clients whose Wake can't reach the phone
    var always by remember { mutableStateOf(it.palsoftware.pastiera.streaming.StreamHostService.alwaysAvailable(context)) }
    FluxSwitchRow(
        linkId = null,
        title = stringResource(R.string.stream_always),
        description = stringResource(R.string.stream_always_description),
        checked = always
    ) { on ->
        always = on
        it.palsoftware.pastiera.streaming.StreamHostService.setAlwaysAvailable(context, on)
    }
    // The phone's own screen as one more entry in Moonlight
    if (streaming) {
        var phoneScreen by remember { mutableStateOf(it.palsoftware.pastiera.streaming.StreamHostService.phoneScreen(context)) }
        FluxSwitchRow(
            linkId = null,
            title = stringResource(R.string.stream_phone_screen_switch),
            description = stringResource(R.string.stream_phone_screen_description),
            checked = phoneScreen
        ) { on ->
            phoneScreen = on
            it.palsoftware.pastiera.streaming.StreamHostService.setPhoneScreen(context, on)
        }
    }
    // The game's size for the Moonlight client: the phone's screen takes its shape while streaming
    if (streaming) {
        val resolutions = it.palsoftware.pastiera.streaming.StreamHostService.RESOLUTIONS
        val names = listOf(
            stringResource(R.string.stream_resolution_client), "720p", "1080p", "1440p"
        )
        var resolution by remember { mutableStateOf(it.palsoftware.pastiera.streaming.StreamHostService.resolution(context)) }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            ChoiceRow(stringResource(R.string.stream_resolution), names[resolutions.indexOf(resolution).coerceAtLeast(0)], names) { index ->
                resolution = resolutions[index]
                it.palsoftware.pastiera.streaming.StreamHostService.setResolution(context, resolution)
            }
            Text(stringResource(R.string.stream_resolution_description), style = MaterialTheme.typography.bodySmall)
        }
    }
    // Where the game plays while it streams: the phone's screen mirrored, or one of its own
    if (streaming) {
        val modes = it.palsoftware.pastiera.streaming.StreamHostService.SCREEN_MODES
        val names = listOf(
            stringResource(R.string.stream_screen_mirror),
            stringResource(R.string.stream_screen_separate),
            stringResource(R.string.stream_screen_desktop)
        )
        var mode by remember { mutableStateOf(it.palsoftware.pastiera.streaming.StreamHostService.screenMode(context)) }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            ChoiceRow(stringResource(R.string.stream_screen), names[modes.indexOf(mode).coerceAtLeast(0)], names) { index ->
                mode = modes[index]
                it.palsoftware.pastiera.streaming.StreamHostService.setScreenMode(context, mode)
            }
            Text(stringResource(R.string.stream_screen_description), style = MaterialTheme.typography.bodySmall)
        }
        // Keyboards, mice and controllers plugged into the phone, for the game's own screen
        if (mode != it.palsoftware.pastiera.streaming.StreamHostService.SCREEN_MIRROR) {
            var passThrough by remember { mutableStateOf(it.palsoftware.pastiera.streaming.StreamHostService.passThrough(context)) }
            FluxSwitchRow(
                linkId = null,
                title = stringResource(R.string.stream_pass_through),
                description = stringResource(R.string.stream_pass_through_description),
                checked = passThrough
            ) { on ->
                passThrough = on
                it.palsoftware.pastiera.streaming.StreamHostService.setPassThrough(context, on)
            }
        }
        // The phone quiet while the TV plays: the game's sound, or every sound of the phone's
        val quietModes = it.palsoftware.pastiera.streaming.StreamQuiet.Mode.entries
        val quietNames = listOf(
            stringResource(R.string.stream_quiet_off),
            stringResource(R.string.stream_quiet_game),
            stringResource(R.string.stream_quiet_all)
        )
        var quiet by remember { mutableStateOf(it.palsoftware.pastiera.streaming.StreamQuiet.mode(context)) }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            ChoiceRow(stringResource(R.string.stream_quiet), quietNames[quietModes.indexOf(quiet)], quietNames) { index ->
                quiet = quietModes[index]
                it.palsoftware.pastiera.streaming.StreamQuiet.setMode(context, quiet)
            }
            Text(stringResource(R.string.stream_quiet_description), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (paired > 0) FluxActionRow(
        linkId = null,
        icon = "\u2715",
        title = stringResource(R.string.stream_unpair),
        description = stringResource(R.string.stream_unpair_description, paired)
    ) {
        it.palsoftware.pastiera.streaming.StreamHost.unpairAll(context)
        paired = 0
    }
}

/** The Game Pass plan: the XCloud catalogue lists only the cloud games it plays. */
@Composable
internal fun XboxPlanRow() {
    val context = LocalContext.current
    val plans = it.palsoftware.pastiera.gaming.XboxCloud.Plan.entries
    val names = listOf(
        stringResource(R.string.game_mode_xbox_plan_ultimate),
        stringResource(R.string.game_mode_xbox_plan_premium),
        stringResource(R.string.game_mode_xbox_plan_essential)
    )
    var plan by remember { mutableStateOf(it.palsoftware.pastiera.gaming.XboxCloud.plan(context)) }
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ChoiceRow(stringResource(R.string.game_mode_xbox_plan), names[plans.indexOf(plan)], names) { index ->
            plan = plans[index]
            it.palsoftware.pastiera.gaming.XboxCloud.setPlan(context, plan)
        }
        Text(stringResource(R.string.game_mode_xbox_plan_description), style = MaterialTheme.typography.bodySmall)
    }
}
