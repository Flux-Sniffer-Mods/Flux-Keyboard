package it.palsoftware.pastiera

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import it.palsoftware.pastiera.shortcuts.UserShortcuts

/**
 * Shortcuts Flux Keyboard makes itself, for what apps only offer to the home screen: a contact
 * to call or message, a website, and Termux tasks (scripts in ~/.shortcuts, as Termux:Widget
 * runs them).
 */
/** The built-in shortcut kinds [packageName] is the app for (its dialer, messages, browser, Termux). */
internal fun builtInKindsFor(context: Context, packageName: String): List<String> = buildList {
    if (packageName == dialerPackage(context)) add(BUILT_IN_CALL)
    if (packageName == runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()) add(BUILT_IN_MESSAGE)
    if (packageName == browserPackage(context)) add(BUILT_IN_WEBSITE)
    if (packageName == UserShortcuts.TERMUX_PACKAGE) add(BUILT_IN_TERMUX)
}

internal const val BUILT_IN_CALL = "call"
internal const val BUILT_IN_MESSAGE = "message"
internal const val BUILT_IN_WEBSITE = "website"
internal const val BUILT_IN_TERMUX = "termux"

/** A built-in kind's title, for menus. */
internal fun builtInTitle(kind: String): Int = when (kind) {
    BUILT_IN_CALL -> R.string.user_shortcuts_call_title
    BUILT_IN_MESSAGE -> R.string.user_shortcuts_message_title
    BUILT_IN_WEBSITE -> R.string.user_shortcuts_website_title
    else -> R.string.user_shortcuts_termux_title
}

@Composable
internal fun BuiltInShortcuts(
    row: @Composable (icon: Drawable?, title: String, description: String, onClick: () -> Unit) -> Unit,
    onAdded: () -> Unit,
    /** One kind to start straight away (from an app's long-press menu in the quick launcher). */
    direct: String? = null,
    /** The direct one is done, added or cancelled. */
    onDirectDone: () -> Unit = {}
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val dialer = remember { defaultApp(context, dialerPackage(context)) }
    val messages = remember { defaultApp(context, runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()) }
    val browser = remember { defaultApp(context, browserPackage(context)) }
    val termux = remember { defaultApp(context, UserShortcuts.TERMUX_PACKAGE) }

    fun added(label: String) {
        onAdded()
        Toast.makeText(context, context.getString(R.string.user_shortcuts_added, label), Toast.LENGTH_SHORT).show()
        if (direct != null) onDirectDone()
    }
    fun cancelled() {
        if (direct != null) onDirectDone()
    }

    // Call or message a contact: Android's own picker, no contacts permission needed
    var pickingFor by rememberSaveable { mutableStateOf<String?>(null) }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val kind = pickingFor ?: return@rememberLauncherForActivityResult
        pickingFor = null
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult cancelled()
        val (name, number) = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) (c.getString(0) ?: "") to (c.getString(1) ?: "") else null }
        }.getOrNull() ?: return@rememberLauncherForActivityResult cancelled()
        if (number.isBlank()) return@rememberLauncherForActivityResult cancelled()
        val who = name.ifBlank { number }
        val label: String
        val shortcut = if (kind == "call") {
            label = context.getString(R.string.user_shortcuts_call_label, who)
            UserShortcuts.addBuilt(context, label, dialer?.packageName ?: "com.android.dialer",
                Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)))
        } else {
            label = context.getString(R.string.user_shortcuts_message_label, who)
            UserShortcuts.addBuilt(context, label, messages?.packageName ?: "com.google.android.apps.messaging",
                Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))
        }
        added(shortcut.label)
    }
    fun pickContact(kind: String) {
        pickingFor = kind
        runCatching {
            contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        }.onFailure { pickingFor = null; cancelled() }
    }

    // Calling straight away needs the phone permission; without it the dialer opens with the number
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { pickContact("call") }

    var websiteDialog by rememberSaveable { mutableStateOf(false) }
    var termuxDialog by rememberSaveable { mutableStateOf(false) }
    val termuxPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { termuxDialog = true }

    fun startCall() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            pickContact("call")
        } else {
            runCatching { callPermission.launch(android.Manifest.permission.CALL_PHONE) }.onFailure { pickContact("call") }
        }
    }
    fun startTermux() {
        if (ContextCompat.checkSelfPermission(context, UserShortcuts.TERMUX_RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            termuxDialog = true
        } else {
            runCatching { termuxPermission.launch(UserShortcuts.TERMUX_RUN_COMMAND_PERMISSION) }.onFailure { termuxDialog = true }
        }
    }

    // From the quick launcher: that one, straight away
    var directStarted by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(direct) {
        if (direct == null || directStarted) return@LaunchedEffect
        directStarted = true
        when (direct) {
            BUILT_IN_CALL -> startCall()
            BUILT_IN_MESSAGE -> pickContact("message")
            BUILT_IN_WEBSITE -> websiteDialog = true
            BUILT_IN_TERMUX -> startTermux()
            else -> onDirectDone()
        }
    }
    if (direct != null) {
        // Only the dialogs below, no rows
    } else {
        // Only what this phone can do: no dialer or messages app, no such rows
        if (dialer != null) row(dialer.icon, stringResource(R.string.user_shortcuts_call_title), dialer.name) { startCall() }
        if (messages != null) row(messages.icon, stringResource(R.string.user_shortcuts_message_title), messages.name) { pickContact("message") }
        if (browser != null) row(browser.icon, stringResource(R.string.user_shortcuts_website_title), browser.name) { websiteDialog = true }
        if (termux != null) {
            row(termux.icon, stringResource(R.string.user_shortcuts_termux_title), stringResource(R.string.user_shortcuts_termux_description)) { startTermux() }
        }
    }

    if (websiteDialog) {
        var address by rememberSaveable { mutableStateOf("") }
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { websiteDialog = false; cancelled() },
            title = { Text(stringResource(R.string.user_shortcuts_website_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(address, { address = it }, singleLine = true,
                        label = { Text(stringResource(R.string.user_shortcuts_website_address)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(name, { name = it }, singleLine = true,
                        label = { Text(stringResource(R.string.user_shortcuts_name)) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = address.isNotBlank(), onClick = {
                    val url = address.trim().let { if ("://" in it) it else "https://$it" }
                    val label = name.trim().ifBlank { Uri.parse(url).host ?: url }
                    UserShortcuts.addBuilt(context, label, browser?.packageName ?: "com.android.chrome",
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    websiteDialog = false
                    added(label)
                }) { Text(stringResource(R.string.user_shortcuts_add)) }
            },
            dismissButton = { TextButton(onClick = { websiteDialog = false; cancelled() }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }

    if (termuxDialog) {
        TermuxTaskDialog(
            onPicked = { path ->
                termuxDialog = false
                // As Termux:Widget: scripts in tasks run in the background, the others in a terminal
                val background = path.startsWith("tasks/")
                val label = path.substringAfterLast('/').substringBeforeLast('.').ifBlank { path }
                UserShortcuts.addBuilt(context, label, UserShortcuts.TERMUX_PACKAGE,
                    UserShortcuts.termuxCommand("${UserShortcuts.TERMUX_HOME}/.shortcuts/$path", background))
                added(label)
            },
            onDismiss = { termuxDialog = false; cancelled() }
        )
    }
}

/** The scripts in ~/.shortcuts, listed by Termux itself; how to set it up if it can't. */
@Composable
private fun TermuxTaskDialog(onPicked: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // null: still asking Termux; empty: none found (or Termux wouldn't answer)
    var scripts by remember { mutableStateOf<List<String>?>(null) }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val action = "${context.packageName}.TERMUX_SCRIPTS"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val result = intent?.getBundleExtra("result")
                val out = result?.getString("stdout").orEmpty()
                failed = result == null || !result.getString("errmsg").isNullOrBlank()
                scripts = it.palsoftware.pastiera.shortcuts.TermuxScripts.parse(out)
                // Kept for the quick launcher's own Termux list too
                if (!failed) it.palsoftware.pastiera.shortcuts.TermuxScripts.store(context, out)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        val reply = PendingIntent.getBroadcast(
            context, 0, Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val list = it.palsoftware.pastiera.shortcuts.TermuxScripts.listCommand(reply)
        val started = runCatching { context.startForegroundService(list) }.isSuccess
        if (!started) {
            failed = true
            scripts = emptyList()
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    // Termux that isn't set up for other apps never answers: say how after a while
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(8_000)
        if (scripts == null) {
            failed = true
            scripts = emptyList()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.user_shortcuts_termux_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val found = scripts
                when {
                    found == null -> Text("…")
                    found.isEmpty() -> {
                        Text(stringResource(R.string.user_shortcuts_termux_none))
                        Text(stringResource(R.string.user_shortcuts_termux_setup),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> found.forEach { script ->
                        Column(Modifier.fillMaxWidth().clickable { onPicked(script) }.padding(vertical = 8.dp)) {
                            Text(script, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                it.palsoftware.pastiera.shortcuts.TermuxScripts.kind(context, script.startsWith("tasks/")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (failed && !found.isNullOrEmpty()) {
                    Text(stringResource(R.string.user_shortcuts_termux_setup),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

private data class DefaultApp(val packageName: String, val name: String, val icon: Drawable?)

private fun defaultApp(context: Context, packageName: String?): DefaultApp? {
    packageName ?: return null
    val pm = context.packageManager
    return runCatching {
        val info = pm.getApplicationInfo(packageName, 0)
        DefaultApp(packageName, pm.getApplicationLabel(info).toString(), runCatching { pm.getApplicationIcon(info) }.getOrNull())
    }.getOrNull()
}

private fun dialerPackage(context: Context): String? =
    runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
        ?: context.packageManager.resolveActivity(Intent(Intent.ACTION_DIAL), 0)?.activityInfo?.packageName

private fun browserPackage(context: Context): String? {
    val pm = context.packageManager
    val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
    return pm.resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        ?.takeIf { it != "android" }
        ?: pm.queryIntentActivities(view, 0).firstOrNull()?.activityInfo?.packageName
}
