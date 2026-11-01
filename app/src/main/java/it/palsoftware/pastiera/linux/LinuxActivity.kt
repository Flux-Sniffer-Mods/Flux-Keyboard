package it.palsoftware.pastiera.linux

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.adb.RootShell
import it.palsoftware.pastiera.display.ExtraScreen
import it.palsoftware.pastiera.display.Miracast
import it.palsoftware.pastiera.modules.FluxModule
import it.palsoftware.pastiera.settings.ModuleOffBar
import it.palsoftware.pastiera.settings.ReadinessCard
import it.palsoftware.pastiera.settings.rememberModuleOn
import it.palsoftware.pastiera.ui.theme.PastieraTheme
import kotlinx.coroutines.delay

/**
 * The Desktop module's page: Linux and its desktop installed, started and stopped, its picture opened
 * on the phone or on a TV screen (a separate screen for Moonlight, or Miracast's), and removed.
 */
class LinuxActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // On the theme's own background, so text takes the theme's colours (not black on dark)
        setContent {
            PastieraTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) { Page() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val state = LinuxDesktop.state.value
        if (state !is LinuxDesktop.State.Downloading && state != LinuxDesktop.State.Installing &&
            state != LinuxDesktop.State.Starting && state != LinuxDesktop.State.Stopping) LinuxDesktop.refresh(this)
    }

    /** A TV screen to show the desktop on: the stream's separate screen, or Miracast's. */
    private fun tvScreen(): Int? = ExtraScreen.current?.id ?: Miracast.displayId(this)

    /** On the phone, a desktop the phone's own size (it draws itself bigger by its scale); on a TV, 1080p. */
    private fun size(tv: Boolean): Pair<Int, Int> {
        if (tv) return 1920 to 1080
        @Suppress("DEPRECATION")
        val metrics = android.util.DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun startOn(tv: Boolean) {
        val (w, h) = size(tv)
        val screen = if (tv) tvScreen() else null
        val context = this
        LinuxDesktop.start(this, w, h) { LinuxDesktop.show(context, screen) }
    }

    @Composable
    private fun Page() {
        val moduleOn = rememberModuleOn(FluxModule.DESKTOP)
        val state by LinuxDesktop.state.collectAsState()
        val log by LinuxDesktop.log.collectAsState()
        // TV screens come and go: looked at every couple of seconds
        var tv by remember { mutableStateOf(tvScreen()) }
        LaunchedEffect(Unit) {
            while (true) { tv = tvScreen(); delay(2_000) }
        }
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.module_desktop_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (!moduleOn.value) {
                ModuleOffBar(FluxModule.DESKTOP) { moduleOn.value = true }
                return@Column
            }
            ReadinessCard(FluxModule.DESKTOP)
            Card {
                when (val current = state) {
                    LinuxDesktop.State.Checking -> Text(stringResource(R.string.linux_checking))
                    LinuxDesktop.State.NotInstalled -> NotInstalled()
                    is LinuxDesktop.State.Downloading -> Progress(stringResource(R.string.linux_downloading, LinuxDesktop.distro(this@LinuxActivity).label, current.percent), current.percent / 100f)
                    LinuxDesktop.State.Installing -> Progress(stringResource(R.string.linux_installing), null, log.lastOrNull())
                    LinuxDesktop.State.Starting -> Progress(stringResource(R.string.linux_starting), null, log.lastOrNull())
                    LinuxDesktop.State.Stopping -> Progress(stringResource(R.string.linux_stopping), null)
                    LinuxDesktop.State.Stopped -> Stopped(tv != null)
                    LinuxDesktop.State.Running -> Running(tv)
                    is LinuxDesktop.State.Failed -> Failed(current.step, log)
                }
            }
            // Each program in Linux, a tap from opening (installed: running or not)
            if (state == LinuxDesktop.State.Stopped || state == LinuxDesktop.State.Running) Programs()
            Text(
                stringResource(R.string.linux_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }

    /** The programs in Linux: a tap opens one in the desktop, a long press puts it on the home screen. */
    @Composable
    private fun Programs() {
        val context = this
        val apps by LinuxApps.apps.collectAsState()
        // Read again each time the page opens: something new may have been installed with apt
        LaunchedEffect(Unit) {
            LinuxApps.cached(context)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { LinuxApps.refresh(context) }
        }
        Card {
            Text(stringResource(R.string.linux_programs), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.linux_programs_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (apps.isEmpty()) Text(stringResource(R.string.linux_programs_none), style = MaterialTheme.typography.bodyMedium)
            apps.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { app -> Program(app, Modifier.weight(1f)) }
                    repeat(4 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    private fun Program(app: LinuxApps.App, modifier: Modifier) {
        val context = this
        val icon = remember(app.id, app.iconName) { LinuxApps.icon(context, app)?.asImageBitmap() }
        val pinned = stringResource(R.string.linux_pinned, app.name)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(
                onClick = {
                    val (w, h) = size(tv = false)
                    LinuxApps.open(context, app, w, h)
                },
                onLongClick = {
                    if (LinuxApps.pin(context, app)) android.widget.Toast.makeText(context, pinned, android.widget.Toast.LENGTH_SHORT).show()
                }
            ).padding(vertical = 6.dp)
        ) {
            androidx.compose.foundation.layout.Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (icon == null) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            ) {
                if (icon != null) androidx.compose.foundation.Image(icon, contentDescription = null, modifier = Modifier.size(44.dp))
                else Text(app.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(app.name, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp))
        }
    }

    @Composable
    private fun Card(content: @Composable () -> Unit) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }

    @Composable
    private fun NotInstalled() {
        val root = RootShell.active
        var mode by remember { mutableStateOf(LinuxDesktop.mode(this)) }
        var distro by remember { mutableStateOf(LinuxDesktop.distro(this)) }
        var desktop by remember { mutableStateOf(LinuxDesktop.desktop(this)) }
        Text(stringResource(R.string.linux_intro), style = MaterialTheme.typography.bodyMedium)
        Heading(stringResource(R.string.linux_choose_distro))
        Choice(distro == LinuxDesktop.Distro.DEBIAN, true, LinuxDesktop.Distro.DEBIAN.label,
            stringResource(R.string.linux_distro_debian_description)) { distro = LinuxDesktop.Distro.DEBIAN }
        Choice(distro == LinuxDesktop.Distro.UBUNTU, true, LinuxDesktop.Distro.UBUNTU.label,
            stringResource(R.string.linux_distro_ubuntu_description)) { distro = LinuxDesktop.Distro.UBUNTU }
        Heading(stringResource(R.string.linux_choose_desktop))
        Choice(desktop == LinuxDesktop.Desktop.XFCE, true, LinuxDesktop.Desktop.XFCE.label,
            stringResource(R.string.linux_desktop_xfce_description)) { desktop = LinuxDesktop.Desktop.XFCE }
        Choice(desktop == LinuxDesktop.Desktop.KDE, true, LinuxDesktop.Desktop.KDE.label,
            stringResource(R.string.linux_desktop_kde_description)) { desktop = LinuxDesktop.Desktop.KDE }
        Heading(stringResource(R.string.linux_choose_mode))
        ModeChoice(LinuxDesktop.Mode.CHROOT, mode == LinuxDesktop.Mode.CHROOT, enabled = root,
            title = stringResource(R.string.linux_mode_chroot),
            description = stringResource(if (root) R.string.linux_mode_chroot_description else R.string.linux_mode_chroot_no_root)) { mode = it }
        ModeChoice(LinuxDesktop.Mode.PROOT, mode == LinuxDesktop.Mode.PROOT, enabled = true,
            title = stringResource(R.string.linux_mode_proot),
            description = stringResource(R.string.linux_mode_proot_description)) { mode = it }
        Button(onClick = { LinuxDesktop.install(this, mode, distro, desktop) }, enabled = LinuxDesktop.canRun(mode)) {
            Text(stringResource(R.string.linux_install))
        }
    }

    @Composable
    private fun Heading(text: String) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }

    @Composable
    private fun Choice(selected: Boolean, enabled: Boolean, title: String, description: String, onPick: () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, onClick = onPick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
    private fun ModeChoice(value: LinuxDesktop.Mode, selected: Boolean, enabled: Boolean, title: String, description: String,
                           onPick: (LinuxDesktop.Mode) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled) { onPick(value) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
    private fun Progress(title: String, fraction: Float?, detail: String? = null) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (!detail.isNullOrBlank()) Text(detail.removePrefix("flux: "), style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    @Composable
    private fun Stopped(tvThere: Boolean) {
        var removing by remember { mutableStateOf(false) }
        val mode = LinuxDesktop.installedMode(this)
        Text(stringResource(R.string.linux_ready, LinuxDesktop.distro(this).label, LinuxDesktop.desktop(this).label,
            stringResource(if (mode == LinuxDesktop.Mode.CHROOT) R.string.linux_mode_chroot else R.string.linux_mode_proot)),
            style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { startOn(tv = false) }) { Text(stringResource(R.string.linux_start_phone)) }
            if (tvThere) FilledTonalButton(onClick = { startOn(tv = true) }) { Text(stringResource(R.string.linux_start_tv)) }
        }
        ScaleChoice()
        TextButton(onClick = { removing = true }) { Text(stringResource(R.string.linux_remove)) }
        if (removing) AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text(stringResource(R.string.linux_remove)) },
            text = { Text(stringResource(R.string.linux_remove_confirm)) },
            confirmButton = { TextButton(onClick = { removing = false; LinuxDesktop.remove(this) }) { Text(stringResource(R.string.linux_remove)) } },
            dismissButton = { TextButton(onClick = { removing = false }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }

    /** How much bigger the desktop draws itself: from its next start. */
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    @Composable
    private fun ScaleChoice() {
        var scale by remember { mutableStateOf(LinuxDesktop.scale(this)) }
        Heading(stringResource(R.string.linux_scale))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinuxDesktop.SCALES.forEach { value ->
                androidx.compose.material3.FilterChip(
                    selected = scale == value,
                    onClick = { scale = value; LinuxDesktop.setScale(this@LinuxActivity, value) },
                    label = { Text("${java.text.DecimalFormat("0.##").format(value)}\u00D7") }
                )
            }
        }
        Text(stringResource(R.string.linux_scale_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    @Composable
    private fun Running(tv: Int?) {
        Text(stringResource(R.string.linux_running), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { LinuxDesktop.show(this@LinuxActivity) }) { Text(stringResource(R.string.linux_open)) }
            if (tv != null) FilledTonalButton(onClick = { LinuxDesktop.show(this@LinuxActivity, tv) }) { Text(stringResource(R.string.linux_open_tv)) }
        }
        OutlinedButton(onClick = { LinuxDesktop.stop(this) }) { Text(stringResource(R.string.miracast_stop)) }
        ShowLog()
    }

    /** Linux's own logs (the VNC server, the session, sound), read through the shell when asked. */
    @Composable
    private fun ShowLog() {
        var logs by remember { mutableStateOf<String?>(null) }
        TextButton(onClick = {
            logs = ""
            Thread { logs = LinuxDesktop.guestLogs(this).orEmpty().ifBlank { "\u2014" } }.start()
        }) { Text(stringResource(R.string.linux_show_log)) }
        val shown = logs
        if (shown != null) AlertDialog(
            onDismissRequest = { logs = null },
            title = { Text(stringResource(R.string.linux_show_log)) },
            text = {
                if (shown.isEmpty()) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else Text(shown, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(rememberScrollState()))
            },
            confirmButton = { TextButton(onClick = { logs = null }) { Text(stringResource(android.R.string.ok)) } }
        )
    }

    @Composable
    private fun Failed(step: LinuxDesktop.Step, log: List<String>) {
        Text(stringResource(when (step) {
            LinuxDesktop.Step.NO_SHELL -> R.string.linux_failed_shell
            LinuxDesktop.Step.NO_ROOT -> R.string.linux_failed_root
            LinuxDesktop.Step.DOWNLOAD -> R.string.linux_failed_download
            LinuxDesktop.Step.INSTALL -> R.string.linux_failed_install
            LinuxDesktop.Step.START -> R.string.linux_failed_start
        }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        if (log.isNotEmpty()) Text(log.takeLast(12).joinToString("\n"), style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace)
        Button(onClick = { LinuxDesktop.refresh(this) }) { Text(stringResource(R.string.linux_try_again)) }
    }
}
