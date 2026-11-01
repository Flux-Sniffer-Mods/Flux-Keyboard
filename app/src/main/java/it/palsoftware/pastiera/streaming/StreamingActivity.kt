package it.palsoftware.pastiera.streaming

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.display.ExtraScreen
import it.palsoftware.pastiera.display.Miracast
import it.palsoftware.pastiera.airplay.AirPlay
import it.palsoftware.pastiera.airplay.AirPlayReceiver
import it.palsoftware.pastiera.airplay.AirPlayReceivers
import it.palsoftware.pastiera.gaming.StreamSignal
import it.palsoftware.pastiera.ui.theme.PastieraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Streaming, an app of its own in the app list as Games is: what's streaming (or how to start),
 * then every app to open on the TV while streaming to it (on the stream's own screen), the ones
 * Moonlight lists first. An app long-pressed is listed in Moonlight itself too, started straight
 * from there. Its ⚙ opens streaming's settings, the same page as its tile's.
 */
class StreamingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PastieraTheme { StreamingHome() } }
    }

    /** Shown on the TV (Moonlight's [Flux Apps]): apps open on this screen, not the phone's. */
    private fun ownScreen(): Int? {
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay?.displayId ?: android.view.Display.DEFAULT_DISPLAY
        return display.takeIf { it != android.view.Display.DEFAULT_DISPLAY }
    }

    private sealed class Tile(val key: String) {
        class App(val app: StreamApps.App) : Tile(app.packageName)
        class Script(val script: String) : Tile("termux:$script")
    }

    private fun openSettings() = it.palsoftware.pastiera.settings.openSettingsPage(this,
        it.palsoftware.pastiera.settings.SettingsPage(it.palsoftware.pastiera.settings.SettingsDestination.Streaming))

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun StreamingHome() {
        var streaming by remember { mutableStateOf(StreamHostService.enabled(this)) }
        var listening by remember { mutableStateOf(listening()) }
        var apps by remember { mutableStateOf<List<StreamApps.App>?>(null) }
        var listed by remember { mutableStateOf(StreamApps.listed(this)) }
        var reload by remember { mutableIntStateOf(0) }
        LaunchedEffect(reload) { apps = withContext(Dispatchers.IO) { StreamApps.installed(this@StreamingActivity) } }
        // Read again on coming back (from settings: every app listed, Termux's scripts, streaming on)
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
        androidx.compose.runtime.DisposableEffect(lifecycle) {
            var first = true
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    streaming = StreamHostService.enabled(this@StreamingActivity)
                    listening = listening()
                    listed = StreamApps.listed(this@StreamingActivity)
                    if (first) first = false else reload++
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        // The Moonlight device streaming now, looked at every couple of seconds
        var device by remember { mutableStateOf(nowStreamingTo()) }
        LaunchedEffect(Unit) {
            while (true) {
                device = nowStreamingTo()
                kotlinx.coroutines.delay(2_000)
            }
        }
        // A search, typed once tapped
        var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
        var searching by remember { mutableStateOf(false) }
        val focus = androidx.compose.ui.platform.LocalFocusManager.current
        androidx.activity.compose.BackHandler(enabled = searching || query.isNotEmpty()) { query = ""; focus.clearFocus() }
        fun matches(name: String) = query.isBlank() || name.contains(query.trim(), ignoreCase = true)
        val moduleOn = it.palsoftware.pastiera.settings.rememberModuleOn(it.palsoftware.pastiera.modules.FluxModule.STREAMING)
        Scaffold(
            topBar = {
                Bar {
                    it.palsoftware.pastiera.settings.SettingsSearchField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        hint = stringResource(R.string.streaming_app_search),
                        onFocusChanged = { searching = it }
                    )
                    // Streaming's settings, as in the Games app
                    IconButton(onClick = { openSettings() }) { Text("\u2699", style = MaterialTheme.typography.titleLarge) }
                    // Streaming to Moonlight on or off at a tap; its settings on a long-press (with
                    // the module on: off, its Turn on bar is the way)
                    if (moduleOn.value) Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (streaming) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                        border = if (streaming) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).combinedClickable(
                            onClick = {
                                streaming = !streaming
                                StreamHostService.setEnabled(this@StreamingActivity, streaming)
                                listening = listening()
                            },
                            onLongClick = { openSettings() }
                        )
                    ) {
                        val label = stringResource(R.string.games_app_stream)
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                .semantics { contentDescription = label; stateDescription = if (streaming) "on" else "off" }
                        ) { StreamSignal(streaming) }
                    }
                }
            }
        ) { inner ->
            // Moonlight's own first (every app is, with every app listed), then Termux:Widget's
            // scripts (with them on for Moonlight's list too), then the rest, each A to Z
            val all = StreamApps.allApps(this@StreamingActivity)
            val scripts = remember(reload) { StreamApps.scripts(this@StreamingActivity) }
            val shown = apps.orEmpty().filter { matches(it.label) }
            val (inMoonlight, rest) = if (all) shown to emptyList() else shown.partition { it.packageName in listed }
            val tiles = inMoonlight.map { Tile.App(it) } +
                scripts.filter { script -> matches(it.palsoftware.pastiera.shortcuts.TermuxScripts.label(script)) }.map { Tile.Script(it) } +
                rest.map { Tile.App(it) }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(92.dp),
                modifier = Modifier.fillMaxSize().padding(inner).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!moduleOn.value) it.palsoftware.pastiera.settings.ModuleOffBar(it.palsoftware.pastiera.modules.FluxModule.STREAMING) {
                            moduleOn.value = true
                        } else {
                            it.palsoftware.pastiera.settings.ReadinessCard(it.palsoftware.pastiera.modules.FluxModule.STREAMING)
                            StatusBar(device, listening) {
                                streaming = true
                                StreamHostService.setEnabled(this@StreamingActivity, true)
                                listening = listening()
                            }
                            // Miracast: the phone's screen on a TV that takes it, no app there
                            MiracastBar()
                            // AirPlay: the same on an Apple TV, a few seconds behind
                            AirPlayBar()
                        }
                        Text(
                            stringResource(if (all) R.string.streaming_app_note_all else R.string.streaming_app_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(tiles.size, key = { tiles[it].key }) { index ->
                    when (val tile = tiles[index]) {
                        is Tile.Script -> AppTile(it.palsoftware.pastiera.shortcuts.TermuxScripts.label(tile.script), "Termux",
                            { Symbol("\u276F") }, onClick = { run(tile.script) }, onLongClick = { run(tile.script) })
                        is Tile.App -> {
                            // Every app in Moonlight's list, or the ones added here with a long-press
                            val listedHere = tile.app.packageName in listed
                            AppTile(tile.app.label, if (listedHere && !all) stringResource(R.string.streaming_app_in_moonlight) else null,
                                { AppArt(tile.app.packageName) },
                                onClick = { open(tile.app) },
                                onLongClick = {
                                    if (all) hint(R.string.streaming_app_all_listed) else {
                                        StreamApps.setListed(this@StreamingActivity, tile.app.packageName, !listedHere)
                                        listed = StreamApps.listed(this@StreamingActivity)
                                        Toast.makeText(this@StreamingActivity, getString(
                                            if (listedHere) R.string.streaming_app_unlisted else R.string.streaming_app_listed, tile.app.label), Toast.LENGTH_SHORT).show()
                                    }
                                })
                        }
                    }
                }
                if (query.isNotBlank() && tiles.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.games_app_no_match))
                }
            }
        }
    }

    /** Moonlight can find the phone: streaming on, or always available. */
    private fun listening(): Boolean = StreamHostService.enabled(this) || StreamHostService.alwaysAvailable(this)

    private fun nowStreamingTo(): String? = StreamHost.streamingDevice.takeIf { StreamHost.session != null }

    /**
     * What's streaming: to which device, with a way to end it; else how to start (from Moonlight,
     * which lists this phone), or a button to turn streaming on.
     */
    @Composable
    private fun StatusBar(device: String?, listening: Boolean, onStart: () -> Unit) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (device != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val onColor = if (device != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            device != null -> stringResource(R.string.tile_streaming_to, device)
                            listening -> stringResource(R.string.streaming_app_status_idle)
                            else -> stringResource(R.string.tile_streaming_off)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = onColor
                    )
                    Text(
                        when {
                            device != null -> stringResource(R.string.streaming_app_status_streaming)
                            listening -> stringResource(R.string.streaming_app_status_idle_description, "[" + stringResource(R.string.stream_flux_apps) + "]")
                            else -> stringResource(R.string.streaming_app_status_off_description)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = onColor
                    )
                }
                when {
                    // Ended from the phone: Moonlight is told
                    device != null -> androidx.compose.material3.FilledTonalButton(onClick = { Thread { runCatching { StreamHost.endStream() } }.start() }) {
                        Text(stringResource(R.string.stream_end))
                    }
                    !listening -> androidx.compose.material3.Button(onClick = onStart) { Text(stringResource(R.string.module_turn_on)) }
                }
            }
        }
    }

    /** An app on the TV: on the stream's own screen, or this one when shown there; nothing streaming, a word on how. */
    private fun open(app: StreamApps.App) {
        val launch = packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        val screen = ownScreen() ?: ExtraScreen.current?.id ?: Miracast.displayId(this)
        if (screen == null && AirPlay.casting) {
            // AirPlay shows the phone's screen: the app opens here, and so there
            startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (screen == null) {
            // Nothing streaming, with a TV set up: turned on and Moonlight opened there into the app, as a game is
            if (TvRemote.ready(this) && StreamHost.session == null) {
                StreamApps.requested = app.packageName
                TvPrompt.play(this, StreamApps.idOf(app.packageName), app.label)
            } else hint(R.string.streaming_app_not_streaming_tv)
            return
        }
        val context = applicationContext
        Thread { runCatching { ExtraScreen.bring(context, launch, screen) } }.start()
        Toast.makeText(this, getString(R.string.streaming_app_opened, app.label), Toast.LENGTH_SHORT).show()
    }

    /** A Termux:Widget script run, Termux's terminal shown where the app would be. */
    private fun run(script: String) {
        val termux = packageManager.getLaunchIntentForPackage(it.palsoftware.pastiera.shortcuts.UserShortcuts.TERMUX_PACKAGE) ?: return
        val screen = ownScreen() ?: ExtraScreen.current?.id ?: Miracast.displayId(this)
        if (screen == null && AirPlay.casting) {
            runCatching { startForegroundService(it.palsoftware.pastiera.shortcuts.TermuxScripts.command(script)) }
            startActivity(termux.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (screen == null) {
            if (TvRemote.ready(this) && StreamHost.session == null) {
                StreamApps.requestedScript = script
                TvPrompt.play(this, StreamApps.scriptId(script), it.palsoftware.pastiera.shortcuts.TermuxScripts.label(script))
            } else hint(R.string.streaming_app_not_streaming_tv)
            return
        }
        runCatching { startForegroundService(it.palsoftware.pastiera.shortcuts.TermuxScripts.command(script)) }
        val context = applicationContext
        Thread { runCatching { ExtraScreen.bring(context, termux, screen) } }.start()
        Toast.makeText(this, getString(R.string.streaming_app_opened, it.palsoftware.pastiera.shortcuts.TermuxScripts.label(script)), Toast.LENGTH_SHORT).show()
    }

    private fun hint(text: Int) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    /**
     * Miracast: casting to which screen, with Stop; else a way to find screens and connect. While
     * casting the phone is mirrored, and an app tapped below goes on the TV alone.
     */
    @Composable
    private fun MiracastBar() {
        val context = this
        // Casting: Android's Miracast display there, read again whenever displays change
        var casting by remember { mutableStateOf(Miracast.display(context)?.name) }
        androidx.compose.runtime.DisposableEffect(Unit) {
            val manager = getSystemService(android.hardware.display.DisplayManager::class.java)
            val listener = object : android.hardware.display.DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) { casting = Miracast.display(context)?.name }
                override fun onDisplayRemoved(displayId: Int) { casting = Miracast.display(context)?.name }
                override fun onDisplayChanged(displayId: Int) = Unit
            }
            manager?.registerDisplayListener(listener, android.os.Handler(android.os.Looper.getMainLooper()))
            onDispose { manager?.unregisterDisplayListener(listener) }
        }
        var picking by remember { mutableStateOf(false) }
        val shell = it.palsoftware.pastiera.adb.AdbShell.available()
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (casting != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val onColor = if (casting != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        casting?.let { stringResource(R.string.miracast_casting_to, it) } ?: stringResource(R.string.miracast_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = onColor
                    )
                    Text(
                        when {
                            casting != null -> stringResource(R.string.miracast_casting_description)
                            !shell -> stringResource(R.string.miracast_description) + " " + stringResource(R.string.miracast_needs_shell)
                            else -> stringResource(R.string.miracast_description)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = onColor
                    )
                }
                if (casting != null) androidx.compose.material3.FilledTonalButton(onClick = {
                    Thread { runCatching { Miracast.disconnect(context) } }.start()
                }) { Text(stringResource(R.string.miracast_stop)) }
                else androidx.compose.material3.Button(onClick = { picking = true }, enabled = shell) {
                    Text(stringResource(R.string.miracast_find))
                }
            }
        }
        if (picking) MiracastPicker(onDone = { picking = false })
    }

    /** Screens found nearby, one tapped to cast to it; looked for again on asking. */
    @Composable
    private fun MiracastPicker(onDone: () -> Unit) {
        val context = this
        var status by remember { mutableStateOf<Miracast.Status?>(null) }
        var scanning by remember { mutableStateOf(true) }
        var scans by remember { mutableIntStateOf(0) }
        var connecting by remember { mutableStateOf<Miracast.Screen?>(null) }
        var failed by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(scans) {
            scanning = true
            status = withContext(Dispatchers.IO) { Miracast.scan(context) }
            scanning = false
        }
        LaunchedEffect(connecting) {
            val screen = connecting ?: return@LaunchedEffect
            failed = null
            val result = withContext(Dispatchers.IO) { Miracast.connect(context, screen.address) }
            if (result?.connected == true) onDone() else {
                failed = screen.name
                connecting = null
            }
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDone,
            title = { Text(stringResource(R.string.miracast_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val screens = status?.offered.orEmpty()
                    when {
                        connecting != null -> Text(stringResource(R.string.miracast_connecting, connecting!!.name))
                        status?.feature == Miracast.FEATURE_UNAVAILABLE -> Text(stringResource(R.string.miracast_unavailable))
                        scanning && screens.isEmpty() -> Text(stringResource(R.string.miracast_scanning))
                        screens.isEmpty() -> Text(stringResource(R.string.miracast_none_found))
                    }
                    failed?.let {
                        Text(stringResource(R.string.miracast_failed, it), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (connecting == null) screens.forEach { screen ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { connecting = screen }
                        ) {
                            Text("\u25AD  " + screen.name, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                if (connecting == null && !scanning && status?.feature != Miracast.FEATURE_UNAVAILABLE) {
                    androidx.compose.material3.TextButton(onClick = { scans++ }) { Text(stringResource(R.string.miracast_again)) }
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = onDone) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }

    /**
     * AirPlay: casting to which TV, with Stop; else a way to find TVs. The phone's screen goes
     * there with its sound, a few seconds behind.
     */
    @Composable
    private fun AirPlayBar() {
        val context = this
        val state by AirPlay.state.collectAsState()
        var picking by remember { mutableStateOf(false) }
        val active = state is AirPlay.State.Casting
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val onColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        (state as? AirPlay.State.Casting)?.let { stringResource(R.string.airplay_casting_to, it.name) }
                            ?: stringResource(R.string.airplay_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = onColor
                    )
                    Text(
                        stringResource(if (active) R.string.airplay_casting_description else R.string.airplay_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = onColor
                    )
                }
                if (active) androidx.compose.material3.FilledTonalButton(onClick = { AirPlay.stop(context) }) {
                    Text(stringResource(R.string.miracast_stop))
                } else androidx.compose.material3.Button(onClick = { picking = true }) {
                    Text(stringResource(R.string.airplay_find))
                }
            }
        }
        // Shown while it connects, pairs or starts, as well as to pick
        val busy = state is AirPlay.State.Connecting || state is AirPlay.State.Pin || state is AirPlay.State.Starting || state is AirPlay.State.Failed
        if (picking || busy) AirPlayPicker(state, onDone = {
            picking = false
            AirPlay.dismiss()
        })
    }

    /** TVs found on the Wi-Fi, one tapped to cast to it; its PIN typed when it shows one. */
    @Composable
    private fun AirPlayPicker(state: AirPlay.State, onDone: () -> Unit) {
        val context = this
        val found = remember { androidx.compose.runtime.mutableStateListOf<AirPlayReceiver>() }
        var searched by remember { mutableStateOf(false) }
        androidx.compose.runtime.DisposableEffect(Unit) {
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            val receivers = AirPlayReceivers(context) { receiver ->
                main.post {
                    // Video TVs only (speakers take sound alone), each once
                    if (receiver.video && found.none { it.id == receiver.id }) found += receiver
                }
            }
            receivers.start()
            main.postDelayed({ searched = true }, 6_000)
            onDispose { receivers.stop(); main.removeCallbacksAndMessages(null) }
        }
        var pin by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                if (state !is AirPlay.State.Casting && state !is AirPlay.State.Idle && state !is AirPlay.State.Failed) AirPlay.stop(context)
                onDone()
            },
            title = { Text(stringResource(R.string.airplay_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (state) {
                        is AirPlay.State.Connecting -> Text(stringResource(R.string.airplay_connecting, state.name))
                        is AirPlay.State.Starting -> Text(stringResource(R.string.airplay_starting, state.name))
                        is AirPlay.State.Pin -> {
                            Text(stringResource(R.string.airplay_pin_prompt, state.name))
                            if (state.wrong) Text(stringResource(R.string.airplay_pin_wrong), color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                            androidx.compose.material3.OutlinedTextField(
                                value = pin,
                                onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                                )
                            )
                        }
                        else -> {
                            if (state is AirPlay.State.Failed) Text(
                                stringResource(if (state.refused) R.string.airplay_refused else R.string.airplay_failed, state.name),
                                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
                            )
                            when {
                                found.isEmpty() && !searched -> Text(stringResource(R.string.airplay_searching))
                                found.isEmpty() -> Text(stringResource(R.string.airplay_none_found))
                            }
                            found.sortedBy { it.name.lowercase() }.forEach { receiver ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                        .clickable { pin = ""; AirPlay.cast(context, receiver) }
                                ) {
                                    Text("\u25AD  " + receiver.name, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (state is AirPlay.State.Pin) androidx.compose.material3.TextButton(
                    onClick = { AirPlay.pin(context, pin); pin = "" }, enabled = pin.length >= 4
                ) { Text(stringResource(R.string.airplay_pin_ok)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (state !is AirPlay.State.Idle && state !is AirPlay.State.Failed) AirPlay.stop(context)
                    onDone()
                }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
        // Playing: the picker's done
        if (state is AirPlay.State.Casting) androidx.compose.runtime.LaunchedEffect(Unit) { onDone() }
    }

    /** The top bar: clear of the status bar and the camera cutout. */
    @Composable
    private fun Bar(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }

    /** One app: its icon on a square, its name beneath; long-press to list it in Moonlight. */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun AppTile(name: String, sub: String?, art: @Composable () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit) {
        val shape = RoundedCornerShape(12.dp)
        Column(
            modifier = Modifier.fillMaxWidth().clip(shape).combinedClickable(onClick = onClick, onLongClick = onLongClick),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { art() }
            Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.basicMarquee(iterations = 1))
            }
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
    }

    /**
     * An app's icon filling its tile: an adaptive icon's own square (its background and picture,
     * without the round mask), else the icon as it is, as large as it fits.
     */
    @Composable
    private fun AppArt(packageName: String) {
        val art by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, packageName) {
            value = withContext(Dispatchers.IO) { runCatching { squareIcon(packageName)?.asImageBitmap() }.getOrNull() }
        }
        art?.let { image ->
            androidx.compose.foundation.Image(bitmap = image, contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }

    private fun squareIcon(packageName: String): android.graphics.Bitmap? {
        val icon = packageManager.getApplicationIcon(packageName)
        val size = 288
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        if (icon is android.graphics.drawable.AdaptiveIconDrawable) {
            // An adaptive icon's layers are drawn half as big again: only their middle two thirds show
            val bleed = size / 4
            icon.background?.apply { setBounds(-bleed, -bleed, size + bleed, size + bleed); draw(canvas) }
            icon.foreground?.apply { setBounds(-bleed, -bleed, size + bleed, size + bleed); draw(canvas) }
        } else {
            icon.setBounds(0, 0, size, size)
            icon.draw(canvas)
        }
        return bitmap
    }

    @Composable
    private fun Symbol(text: String) {
        Text(text, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

}
