package it.palsoftware.pastiera.settings
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.backup.BackupManager
import it.palsoftware.pastiera.backup.RestoreManager
import it.palsoftware.pastiera.backup.RestoreInspectionResult
import androidx.compose.material3.Surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import rikka.shizuku.Shizuku
import it.palsoftware.pastiera.RestrictedSettings
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.TutorialActivity
import it.palsoftware.pastiera.device.TrackpadGestureSettingsScreen
import it.palsoftware.pastiera.getExperimentalCandidatesViewEnabled
import it.palsoftware.pastiera.isOfflineMode
import it.palsoftware.pastiera.setExperimentalCandidatesViewEnabled

/**
 * Advanced settings screen.
 */
@Composable
fun AdvancedSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNavigate: (SettingsDestination) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val prefs = remember { SettingsManager.getPreferences(context) }

    var clipboardRetentionTime by remember {
        mutableStateOf(SettingsManager.getClipboardRetentionTime(context).toString())
    }
    var developerOptions by remember { mutableStateOf(SettingsManager.getDeveloperOptionsEnabled(context)) }
    var pasteSuggestion by remember { mutableStateOf(SettingsManager.getPasteSuggestionEnabled(context)) }
    var pasteInPasswordFields by remember { mutableStateOf(SettingsManager.getPasteSuggestionInPasswordFields(context)) }
    var oneTimeCodes by remember { mutableStateOf(SettingsManager.getOneTimeCodesEnabled(context)) }
    // Notification access, re-read when coming back from Android's settings
    var notificationAccess by remember { mutableStateOf(SettingsManager.hasNotificationAccess(context)) }
    val accessLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(accessLifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationAccess = SettingsManager.hasNotificationAccess(context)
            }
        }
        accessLifecycle.lifecycle.addObserver(observer)
        onDispose { accessLifecycle.lifecycle.removeObserver(observer) }
    }
    var cleanLinks by remember { mutableStateOf(SettingsManager.getCleanPastedLinks(context)) }
    var incognitoAlways by remember { mutableStateOf(SettingsManager.getIncognitoAlways(context)) }
    var incognitoFollowApps by remember { mutableStateOf(SettingsManager.getIncognitoFollowApps(context)) }
    var experimentalCandidatesViewEnabled by remember {
        mutableStateOf(SettingsManager.getExperimentalCandidatesViewEnabled(context))
    }
    var pendingDeviceChangeRestore by remember {
        mutableStateOf<Pair<Uri, RestoreManager.DeviceChange>?>(null)
    }
    val currentDestination = remember { when (settingsChild(context, "advanced")) {
        "ImeTest" -> AdvancedDestination.ImeTest
        "TrackpadGestures" -> AdvancedDestination.TrackpadGestures
        else -> if (context.settingsActivity().intent.data?.let(SettingLinkRegistry::parseSettingLinkUri) in TRACKPAD_SETTING_LINK_IDS) AdvancedDestination.TrackpadGestures else AdvancedDestination.Main
    } }
    val highlightedSettingId = LocalSettingHighlightId.current
    // Listen to SharedPreferences changes to update UI when values are restored
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "clipboard_retention_time" -> {
                    clipboardRetentionTime = SettingsManager.getClipboardRetentionTime(context).toString()
                }
                "experimental_candidates_view_enabled" -> {
                    experimentalCandidatesViewEnabled = SettingsManager.getExperimentalCandidatesViewEnabled(context)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }


    fun navigateTo(destination: AdvancedDestination) {
        openSettingsChild(context, "advanced", when (destination) { AdvancedDestination.Main -> "Main"; AdvancedDestination.ImeTest -> "ImeTest"; AdvancedDestination.TrackpadGestures -> "TrackpadGestures" })
    }
    fun navigateBack() { context.settingsActivity().finish() }


    fun defaultBackupName(): String {
        val formatter = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
        return "pastiera-backup-${formatter.format(Date())}.zip"
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = BackupManager.createBackup(context, uri)
                val message = when (result) {
                    is it.palsoftware.pastiera.backup.BackupResult.Success ->
                        context.getString(R.string.backup_completed)
                    is it.palsoftware.pastiera.backup.BackupResult.Failure ->
                        context.getString(R.string.backup_failed, result.reason)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    fun performRestore(uri: Uri, importMode: RestoreManager.ImportMode) {
        scope.launch {
            val result = RestoreManager.restore(context, uri, importMode)
            val message = when (result) {
                is it.palsoftware.pastiera.backup.RestoreResult.Success ->
                    context.getString(R.string.restore_completed)
                is it.palsoftware.pastiera.backup.RestoreResult.Failure ->
                    context.getString(R.string.restore_failed, result.reason)
            }
            snackbarHostState.showSnackbar(message)

            // Wait a bit for SharedPreferences to be written (apply() is asynchronous)
            kotlinx.coroutines.delay(100)

            // Explicitly reload values after restore to ensure UI is updated
            clipboardRetentionTime = SettingsManager.getClipboardRetentionTime(context).toString()
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                when (val inspection = RestoreManager.inspect(context, uri)) {
                    is RestoreInspectionResult.Success -> {
                        val deviceChange = inspection.deviceChange
                        if (deviceChange == null) {
                            performRestore(uri, RestoreManager.ImportMode.UNCHANGED)
                        } else {
                            pendingDeviceChangeRestore = uri to deviceChange
                        }
                    }
                    is RestoreInspectionResult.Failure -> {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.restore_failed, inspection.reason)
                        )
                    }
                }
            }
        }
    }

    pendingDeviceChangeRestore?.let { (uri, deviceChange) ->
        AlertDialog(
            onDismissRequest = { pendingDeviceChangeRestore = null },
            title = { Text(stringResource(R.string.restore_device_change_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.restore_device_change_message,
                        deviceChange.source.displayName,
                        deviceChange.target.displayName
                    )
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = {
                            pendingDeviceChangeRestore = null
                            performRestore(uri, RestoreManager.ImportMode.ADAPT_TO_CURRENT_DEVICE)
                        }
                    ) {
                        Text(stringResource(R.string.restore_device_change_adapt))
                    }
                    TextButton(
                        onClick = {
                            pendingDeviceChangeRestore = null
                            performRestore(uri, RestoreManager.ImportMode.UNCHANGED)
                        }
                    ) {
                        Text(stringResource(R.string.restore_device_change_unchanged))
                    }
                    TextButton(onClick = { pendingDeviceChangeRestore = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        )
    }

    val destination = currentDestination
        when (destination) {
            AdvancedDestination.Main -> {
                Scaffold(
                    topBar = {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.statusBars),
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { navigateBack() }) {
                                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.settings_back_content_description)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.settings_privacy_system_title),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { paddingValues ->
                    Column(
                        modifier = modifier
                            .fillMaxWidth()
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState())
                    ) {
                        SettingsSectionDivider(stringResource(R.string.settings_section_privacy))
                        FluxSwitchRow(
                            linkId = SettingLinkIds.PRIVACY_INCOGNITO_ALWAYS,
                            title = stringResource(R.string.incognito_always_title),
                            description = stringResource(R.string.incognito_always_description),
                            checked = incognitoAlways,
                            onCheckedChange = {
                                incognitoAlways = it
                                SettingsManager.setIncognitoAlways(context, it)
                            }
                        )
                        if (!incognitoAlways) {
                            FluxSwitchRow(
                                linkId = SettingLinkIds.PRIVACY_INCOGNITO_FOLLOW_APPS,
                                title = stringResource(R.string.incognito_follow_apps_title),
                                description = stringResource(R.string.incognito_follow_apps_description),
                                checked = incognitoFollowApps,
                                onCheckedChange = {
                                    incognitoFollowApps = it
                                    SettingsManager.setIncognitoFollowApps(context, it)
                                }
                            )
                        }

                        SettingsCategoryRow(
                            icon = Icons.Filled.CloudOff,
                            title = stringResource(R.string.flux_offline_title),
                            description = stringResource(
                                if (SettingsManager.isOfflineMode(context)) R.string.flux_offline_on else R.string.flux_offline_description
                            ),
                            linkId = SettingLinkIds.MAIN_FLUX_OFFLINE,
                            onClick = { onNavigate(SettingsDestination.FluxOffline) }
                        )
                        // Through Shizuku (the ADB shell), no root needed
                        SettingsCategoryRow(
                            icon = Icons.Filled.Code,
                            title = stringResource(R.string.root_title),
                            description = stringResource(R.string.root_row_description),
                            linkId = "main.root",
                            onClick = { onNavigate(SettingsDestination.Root) }
                        )

                        SettingsSectionDivider(stringResource(R.string.settings_section_clipboard))
                        FluxSwitchRow(
                            linkId = SettingLinkIds.PRIVACY_PASTE_SUGGESTION,
                            title = stringResource(R.string.paste_suggestion_title),
                            description = stringResource(R.string.paste_suggestion_description),
                            checked = pasteSuggestion,
                            onCheckedChange = {
                                pasteSuggestion = it
                                SettingsManager.setPasteSuggestionEnabled(context, it)
                            }
                        )
                        if (pasteSuggestion) {
                            FluxSwitchRow(
                                linkId = SettingLinkIds.PRIVACY_PASTE_IN_PASSWORD_FIELDS,
                                title = stringResource(R.string.paste_in_password_fields_title),
                                description = stringResource(R.string.paste_in_password_fields_description),
                                checked = pasteInPasswordFields,
                                onCheckedChange = {
                                    pasteInPasswordFields = it
                                    SettingsManager.setPasteSuggestionInPasswordFields(context, it)
                                }
                            )
                        }
                        FluxSwitchRow(
                            linkId = SettingLinkIds.PRIVACY_CLEAN_LINKS,
                            title = stringResource(R.string.clean_links_title),
                            description = stringResource(R.string.clean_links_description),
                            checked = cleanLinks,
                            onCheckedChange = {
                                cleanLinks = it
                                SettingsManager.setCleanPastedLinks(context, it)
                            }
                        )
                        FluxSwitchRow(
                            linkId = SettingLinkIds.PRIVACY_ONE_TIME_CODES,
                            title = stringResource(R.string.one_time_codes_title),
                            description = stringResource(R.string.one_time_codes_description),
                            checked = oneTimeCodes,
                            onCheckedChange = {
                                oneTimeCodes = it
                                SettingsManager.setOneTimeCodesEnabled(context, it)
                                // Android's own switch lets the app read notifications
                                if (it && !SettingsManager.hasNotificationAccess(context)) {
                                    RestrictedSettings.openNotificationAccess(context)
                                }
                            }
                        )
                        if (oneTimeCodes && !notificationAccess) {
                            FluxActionRow(
                                linkId = null,
                                title = stringResource(R.string.notification_access_title),
                                description = stringResource(
                                    if (RestrictedSettings.blocked(context)) R.string.restricted_settings_notifications
                                    else R.string.notification_access_off
                                ),
                                onClick = { RestrictedSettings.openNotificationAccess(context) }
                            )
                        }

                        // Clipboard Retention Time
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .settingRow(SettingLinkIds.ADVANCED_CLIPBOARD_RETENTION_TIME)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.clipboard_retention_time_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.clipboard_retention_time_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedTextField(
                                    value = clipboardRetentionTime,
                                    onValueChange = { text ->
                                        val filtered = text.filter { it.isDigit() }.take(5)
                                        clipboardRetentionTime = filtered
                                    },
                                    placeholder = { Text("min") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.widthIn(max = 120.dp),
                                    singleLine = true
                                )
                                OutlinedButton(
                                    onClick = {
                                        val minutes = clipboardRetentionTime.toLongOrNull()
                                        if (minutes != null) {
                                            SettingsManager.setClipboardRetentionTime(context, minutes)
                                        }
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = stringResource(R.string.clipboard_retention_apply)
                                    )
                                }
                            }
                        }

                        SettingsSectionDivider(stringResource(R.string.settings_category_accessibility))
                        SettingsCategoryRow(
                            icon = Icons.Filled.TouchApp,
                            title = stringResource(R.string.settings_category_accessibility),
                            description = stringResource(R.string.settings_accessibility_row_description),
                            linkId = SettingLinkIds.MAIN_ACCESSIBILITY,
                            onClick = { onNavigate(SettingsDestination.Accessibility) }
                        )

                        SettingsSectionDivider(stringResource(R.string.settings_section_language))
                        SettingsCategoryRow(
                            icon = ImageVector.vectorResource(R.drawable.translate_24),
                            title = stringResource(R.string.app_language_title),
                            description = currentAppLanguageLabel(context),
                            linkId = SettingLinkIds.MAIN_APP_LANGUAGE,
                            onClick = { onNavigate(SettingsDestination.AppLanguage) }
                        )

                        SettingsSectionDivider(stringResource(R.string.settings_section_backup))
                        RecommendedSettingsRow()
                        // Backup
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .settingRow(SettingLinkIds.ADVANCED_BACKUP) {
                                    backupLauncher.launch(defaultBackupName())
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Backup,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.backup_now),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.backup_now_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Restore
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .settingRow(SettingLinkIds.ADVANCED_RESTORE) {
                                    restoreLauncher.launch(arrayOf("application/zip"))
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.restore_from_file),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.restore_from_file_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        SettingsSectionDivider(stringResource(R.string.settings_section_help_about))
                        // Show Tutorial
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .settingRow(SettingLinkIds.ADVANCED_SHOW_TUTORIAL) {
                                    SettingsManager.resetTutorialCompleted(context)
                                    val intent = Intent(context, TutorialActivity::class.java)
                                    context.startActivity(intent)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.tutorial_show),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.tutorial_review_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        SettingsUpdateRows(context)
                        SettingsCategoryRow(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.about_title),
                            description = stringResource(
                                R.string.settings_about_version_summary,
                                BuildConfig.VERSION_NAME
                            ),
                            linkId = SettingLinkIds.MAIN_ABOUT,
                            onClick = { onNavigate(SettingsDestination.About) }
                        )

                        SettingsSectionDivider(stringResource(R.string.settings_section_experimental))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .settingRow(SettingLinkIds.ADVANCED_EXPERIMENTAL_CANDIDATES_VIEW)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.experimental_candidates_view_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.experimental_candidates_view_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = experimentalCandidatesViewEnabled,
                                    onCheckedChange = { enabled ->
                                        experimentalCandidatesViewEnabled = enabled
                                        SettingsManager.setExperimentalCandidatesViewEnabled(context, enabled)
                                    }
                                )
                            }
                        }



                        FluxSwitchRow(
                            linkId = SettingLinkIds.DEVELOPER_OPTIONS_ENABLED,
                            title = stringResource(R.string.developer_options_title),
                            description = stringResource(R.string.developer_options_description),
                            checked = developerOptions,
                            onCheckedChange = { enabled ->
                                developerOptions = enabled
                                SettingsManager.setDeveloperOptionsEnabled(context, enabled)
                            }
                        )
                        if (developerOptions) {
                            SettingsCategoryRow(
                                icon = Icons.Filled.Code,
                                title = stringResource(R.string.developer_options_title),
                                description = stringResource(R.string.developer_options_row_description),
                                linkId = SettingLinkIds.MAIN_DEVELOPER,
                                onClick = { onNavigate(SettingsDestination.Developer) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            AdvancedDestination.ImeTest -> {
                ImeTestScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }

            AdvancedDestination.TrackpadGestures -> {
                TrackpadGestureSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }

        }

}

private sealed class AdvancedDestination {
    object Main : AdvancedDestination()
    object ImeTest : AdvancedDestination()
    object TrackpadGestures : AdvancedDestination()
}



private val TRACKPAD_SETTING_LINK_IDS = setOf(
    "trackpad.add_word",
    "trackpad.add_word_full_width",
    "trackpad.swipe_to_delete",
    "trackpad.swipe_to_delete_provider",
    "trackpad.suggestion_swipe_directions",
    "trackpad.phone_settings",
    SettingLinkIds.TRACKPAD_GESTURES_ENABLED,
    SettingLinkIds.TRACKPAD_PROVIDER,
    SettingLinkIds.TRACKPAD_SHIZUKU_DEVICE,
    SettingLinkIds.TRACKPAD_SENSITIVITY,
    SettingLinkIds.TRACKPAD_SUGGESTION_SWIPE_THRESHOLD,
    SettingLinkIds.TRACKPAD_DELETE_SWIPE_THRESHOLD,
    SettingLinkIds.TRACKPAD_SIDE_SWIPE_THRESHOLD,
    SettingLinkIds.TRACKPAD_DEBUG
)
