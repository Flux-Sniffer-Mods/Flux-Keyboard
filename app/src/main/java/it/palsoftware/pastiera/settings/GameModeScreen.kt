package it.palsoftware.pastiera.settings

import android.view.KeyEvent
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.apps.AppPickerDialog
import it.palsoftware.pastiera.apps.InstalledApp
import it.palsoftware.pastiera.gaming.GameAction
import it.palsoftware.pastiera.gaming.GameLibrary
import it.palsoftware.pastiera.gaming.GameProfile
import it.palsoftware.pastiera.gaming.GameProfiles
import it.palsoftware.pastiera.gaming.GameStyle
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun refresh() { profiles = GameProfiles.all(context) }

    editing?.let { profile ->
        GameProfileEditor(profile, onDone = { saved ->
            if (saved != null) GameProfiles.save(context, saved)
            editing = null
            refresh()
        }, onDelete = {
            GameProfiles.delete(context, profile.id)
            editing = null
            refresh()
        })
        return
    }

    FluxScreenScaffold(stringResource(R.string.game_mode_title), onBack, modifier) {
        FluxNote(stringResource(R.string.game_mode_note))
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
                title = stringResource(R.string.game_mode_seen_title, appLabel(context, seen)),
                description = stringResource(R.string.game_mode_seen_description, seen)
            ) { addingSeen = true }
            if (addingSeen) {
                AlertDialog(
                    onDismissRequest = { addingSeen = false },
                    title = { Text(stringResource(R.string.game_mode_seen_title, appLabel(context, seen))) },
                    text = {
                        Column {
                            profiles.forEach { profile ->
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
        SettingsSectionDivider(stringResource(R.string.game_mode_profiles))
        profiles.forEach { profile ->
            FluxActionRow(
                linkId = null,
                title = profile.name,
                description = profile.style.label + " · " +
                    profile.packages.joinToString { pkg -> appLabel(context, pkg) }.ifEmpty { stringResource(R.string.game_mode_no_apps) }
            ) { editing = profile }
        }
        FluxActionRow(
            linkId = null,
            icon = "+",
            title = stringResource(R.string.game_mode_add),
            description = stringResource(R.string.game_mode_add_description)
        ) { picking = true }
        FluxActionRow(
            linkId = null,
            icon = "\u2913",
            title = stringResource(R.string.game_mode_gamenative_save),
            description = stringResource(R.string.game_mode_gamenative_save_description)
        ) { gameNativeSetup = true }
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
        FluxActionRow(
            linkId = null,
            icon = "+",
            title = stringResource(R.string.game_mode_own_game),
            description = stringResource(R.string.game_mode_own_game_description)
        ) { addingOwn = true }
        CustomGameFolders()
        FluxActionRow(
            linkId = null,
            icon = "⇩",
            title = stringResource(R.string.game_mode_import),
            description = stringResource(R.string.game_mode_import_description)
        ) { importing = true }
    }

    if (picking) {
        AppPickerDialog(
            onAppSelected = { app: InstalledApp ->
                picking = false
                editing = freshProfile(context, app.appName, app.packageName)
            },
            onDismiss = { picking = false }
        )
    }
    val pickGame: (GameLibrary.Game) -> Unit = { game ->
        importing = false
        addingOwn = false
        editing = freshProfile(context, game.name, game.packageName)
            .copy(launch = GameLibrary.launchIntent(game)?.toUri(android.content.Intent.URI_INTENT_SCHEME))
        if (game.packageName == it.palsoftware.pastiera.gaming.GameNativeBridge.PACKAGE) gameNativeSetup = true
    }
    if (importing) GameImportDialog(onPicked = pickGame, onDismiss = { importing = false })
    if (addingOwn) OwnGameDialog(onAdded = pickGame, onDismiss = { addingOwn = false })
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
private fun OwnGameDialog(onAdded: (GameLibrary.Game) -> Unit, onDismiss: () -> Unit) {
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

/**
 * A new gamepad profile for [pkg]; for an emulator whose on-screen gamepad comes from its code
 * (Dolphin, Azahar, Eden), its spots straight away.
 */
private fun freshProfile(context: android.content.Context, name: String, pkg: String): GameProfile {
    val fresh = GameProfiles.newProfile(name, GameStyle.GAMEPAD, setOf(pkg))
    val layouts = it.palsoftware.pastiera.gaming.EmulatorLayouts
    if (layouts.name(pkg) !in setOf("Dolphin", "Azahar", "Eden")) return fresh
    return layouts.layout(context, pkg)?.let { layout -> layouts.apply(fresh, layout) } ?: fresh
}

private fun appLabel(context: android.content.Context, pkg: String): String = runCatching {
    context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)

/** The games of the game launchers installed, one tap making a profile for it. */
@Composable
private fun GameImportDialog(onPicked: (GameLibrary.Game) -> Unit, onDismiss: () -> Unit) {
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
                            "${game.name}  (${appLabel(context, game.packageName)})",
                            modifier = Modifier.fillMaxWidth().clickable { onPicked(game) }.padding(vertical = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** One profile: its name, style, apps, trackpad halves and every key's action. */
@Composable
private fun GameProfileEditor(profile: GameProfile, onDone: (GameProfile?) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(profile) }
    var addingApp by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    if (capturing) {
        KeyCaptureDialog(onKey = { code ->
            capturing = false
            if (code !in current.keys) {
                val action = if (current.style == GameStyle.GAMEPAD) GameAction.R2 else GameAction.MOUSE_LEFT
                current = current.copy(keys = current.keys + (code to action))
            }
        }, onDismiss = { capturing = false })
    }
    FluxScreenScaffold(current.name, { onDone(current) }, Modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = current.name,
                onValueChange = { current = current.copy(name = it) },
                label = { Text(stringResource(R.string.game_mode_profile_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ChoiceRow(stringResource(R.string.game_mode_style), current.style.label, GameStyle.entries.map { it.label }) { index ->
                val style = GameStyle.entries[index]
                if (style != current.style) {
                    val fresh = GameProfiles.newProfile(current.name, style, current.packages)
                    current = fresh.copy(id = current.id)
                }
            }
            ChoiceRow(stringResource(R.string.game_mode_left_half), current.leftHalf.label, TrackpadRole.entries.map { it.label }) {
                current = current.copy(leftHalf = TrackpadRole.entries[it])
            }
            ChoiceRow(stringResource(R.string.game_mode_right_half), current.rightHalf.label, TrackpadRole.entries.map { it.label }) {
                current = current.copy(rightHalf = TrackpadRole.entries[it])
            }
            // One screen choice per game, its app's suited one to start with
            val screens = it.palsoftware.pastiera.gaming.ScreenMode.entries
            val screenNames = listOf(
                stringResource(R.string.game_mode_screen_app),
                stringResource(R.string.game_mode_screen_upright),
                stringResource(R.string.game_mode_screen_sideways)
            )
            ChoiceRow(stringResource(R.string.game_mode_screen), screenNames[current.screen.ordinal], screenNames) { index ->
                current = current.copy(screen = screens[index])
            }
            Text(
                stringResource(
                    when (current.screen) {
                        it.palsoftware.pastiera.gaming.ScreenMode.APP -> R.string.game_mode_screen_app_description
                        it.palsoftware.pastiera.gaming.ScreenMode.UPRIGHT -> R.string.game_mode_screen_upright_description
                        it.palsoftware.pastiera.gaming.ScreenMode.SIDEWAYS -> R.string.game_mode_screen_sideways_description
                    }
                ),
                style = MaterialTheme.typography.bodySmall
            )
            // For starting the game from inside its launcher or emulator: its own form of this profile
            val nativeApp = it.palsoftware.pastiera.gaming.NativeProfiles.appFor(current)
            // Dolphin: the controller the game gets, Wii or GameCube found from its file unless picked
            if (nativeApp == "Dolphin") {
                val pads = stringArrayResource(R.array.dolphin_pads)
                val launch = current.launch
                val detected by androidx.compose.runtime.produceState<String?>(null, launch) {
                    value = withContext(Dispatchers.IO) {
                        val file = launch?.let { l -> it.palsoftware.pastiera.gaming.DolphinPads.gameFile(l) }
                        file?.let { f -> if (it.palsoftware.pastiera.gaming.DolphinPads.isWii(f)) "Wii" else "GameCube" }
                    }
                }
                val autoLabel = detected?.let { found -> stringResource(R.string.dolphin_pad_auto_found, found) } ?: pads[0]
                ChoiceRow(
                    stringResource(R.string.dolphin_pad_title),
                    if (current.dolphinPad == it.palsoftware.pastiera.gaming.DolphinPad.AUTO) autoLabel else pads[current.dolphinPad.ordinal],
                    listOf(autoLabel) + pads.drop(1)
                ) { index -> current = current.copy(dolphinPad = it.palsoftware.pastiera.gaming.DolphinPad.entries[index]) }
                Text(stringResource(R.string.dolphin_pad_description), style = MaterialTheme.typography.bodySmall)
            }
            if (nativeApp != null) {
                var steps by remember { mutableStateOf<String?>(null) }
                val saveScope = androidx.compose.runtime.rememberCoroutineScope()
                FluxActionRow(
                    linkId = null,
                    icon = "\u2913",
                    title = stringResource(R.string.native_save, nativeApp),
                    description = stringResource(R.string.native_save_description, nativeApp)
                ) {
                    saveScope.launch {
                        val result = withContext(Dispatchers.IO) { it.palsoftware.pastiera.gaming.NativeProfiles.save(context, current) }
                        if (result.nativeKeys) current = current.copy(nativeKeys = true)
                        steps = result.steps
                    }
                }
                steps?.let { text ->
                    AlertDialog(
                        onDismissRequest = { steps = null },
                        title = { Text(stringResource(R.string.native_save, nativeApp)) },
                        text = { Text(text) },
                        confirmButton = { TextButton(onClick = { steps = null }) { Text(stringResource(android.R.string.ok)) } }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.native_keys))
                        Text(stringResource(R.string.native_keys_description), style = MaterialTheme.typography.bodySmall)
                    }
                    androidx.compose.material3.Switch(
                        checked = current.nativeKeys,
                        onCheckedChange = { current = current.copy(nativeKeys = it) }
                    )
                }
            }
            // A known emulator: its on-screen gamepad's spots, read from its code or settings
            val emulator = current.packages.firstNotNullOfOrNull { pkg ->
                it.palsoftware.pastiera.gaming.EmulatorLayouts.name(pkg)?.let { pkg to it }
            }
            if (emulator != null) {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                TextButton(onClick = {
                    scope.launch {
                        val layout = withContext(Dispatchers.IO) {
                            it.palsoftware.pastiera.gaming.EmulatorLayouts.layout(context, emulator.first)
                        }
                        if (layout != null) {
                            current = it.palsoftware.pastiera.gaming.EmulatorLayouts.apply(current, layout)
                            android.widget.Toast.makeText(context, R.string.game_mode_emulator_applied, android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, R.string.game_mode_emulator_unreadable, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text(stringResource(R.string.game_mode_use_emulator_controls, emulator.second)) }
            }
            TextButton(onClick = {
                if (!it.palsoftware.pastiera.gaming.GameLaunchActivity.pin(context, current.also { profile -> GameProfiles.save(context, profile) })) {
                    android.widget.Toast.makeText(context, R.string.game_mode_home_screen_failed, android.widget.Toast.LENGTH_LONG).show()
                }
            }) { Text(stringResource(R.string.game_mode_home_screen)) }
            Text(stringResource(R.string.game_mode_apps), style = MaterialTheme.typography.titleMedium)
            current.packages.forEach { pkg ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(appLabel(context, pkg), modifier = Modifier.weight(1f))
                    TextButton(onClick = { current = current.copy(packages = current.packages - pkg) }) {
                        Text(stringResource(R.string.game_mode_remove))
                    }
                }
            }
            TextButton(onClick = { addingApp = true }) { Text(stringResource(R.string.game_mode_add_app)) }
            Text(stringResource(R.string.game_mode_keys), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.game_mode_keys_note), style = MaterialTheme.typography.bodySmall)
            // The keyboard as a grid, or a controller with each button's key
            GameKeyMapper(current.keys, gamepad = current.style == GameStyle.GAMEPAD) { keys ->
                current = current.copy(keys = keys)
            }
            TextButton(onClick = { capturing = true }) { Text(stringResource(R.string.game_mode_add_key)) }
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

/** Waits for a key: the next one pressed (the phone's own buttons included, where Android passes them on). */
@Composable
private fun KeyCaptureDialog(onKey: (Int) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_mode_add_key)) },
        text = {
            Text(
                stringResource(R.string.game_mode_press_key),
                modifier = Modifier
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                            onKey(event.nativeKeyEvent.keyCode)
                        }
                        true
                    }
            )
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
