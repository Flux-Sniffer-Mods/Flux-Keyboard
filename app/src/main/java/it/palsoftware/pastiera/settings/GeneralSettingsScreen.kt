package it.palsoftware.pastiera.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.TutorialActivity
import it.palsoftware.pastiera.backup.BackupManager
import it.palsoftware.pastiera.backup.BackupResult
import it.palsoftware.pastiera.backup.RestoreInspectionResult
import it.palsoftware.pastiera.backup.RestoreManager
import it.palsoftware.pastiera.backup.RestoreResult
import it.palsoftware.pastiera.isOfflineMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Settings (the gear on the home screen): only what every module shares. A module's own settings
 * are on its page, opened from its tile.
 */
@Composable
internal fun GeneralSettingsScreen(
    modifier: Modifier,
    onBack: () -> Unit,
    onNavigate: (SettingsDestination) -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var developerOptions by remember { mutableStateOf(SettingsManager.getDeveloperOptionsEnabled(context)) }
    var pendingDeviceChangeRestore by remember { mutableStateOf<Pair<Uri, RestoreManager.DeviceChange>?>(null) }

    fun defaultBackupName(): String =
        "flux-keyboard-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.zip"

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = when (val result = BackupManager.createBackup(context, uri)) {
                is BackupResult.Success -> context.getString(R.string.backup_completed)
                is BackupResult.Failure -> context.getString(R.string.backup_failed, result.reason)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    fun performRestore(uri: Uri, importMode: RestoreManager.ImportMode) {
        scope.launch {
            val message = when (val result = RestoreManager.restore(context, uri, importMode)) {
                is RestoreResult.Success -> context.getString(R.string.restore_completed)
                is RestoreResult.Failure -> context.getString(R.string.restore_failed, result.reason)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val inspection = RestoreManager.inspect(context, uri)) {
                is RestoreInspectionResult.Success -> {
                    // A backup from another kind of phone: adapted to this one, or kept as it is
                    val deviceChange = inspection.deviceChange
                    if (deviceChange == null) performRestore(uri, RestoreManager.ImportMode.UNCHANGED)
                    else pendingDeviceChangeRestore = uri to deviceChange
                }
                is RestoreInspectionResult.Failure ->
                    snackbarHostState.showSnackbar(context.getString(R.string.restore_failed, inspection.reason))
            }
        }
    }

    pendingDeviceChangeRestore?.let { (uri, deviceChange) ->
        AlertDialog(
            onDismissRequest = { pendingDeviceChangeRestore = null },
            title = { Text(stringResource(R.string.restore_device_change_title)) },
            text = {
                Text(stringResource(R.string.restore_device_change_message, deviceChange.source.displayName, deviceChange.target.displayName))
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        pendingDeviceChangeRestore = null
                        performRestore(uri, RestoreManager.ImportMode.ADAPT_TO_CURRENT_DEVICE)
                    }) { Text(stringResource(R.string.restore_device_change_adapt)) }
                    TextButton(onClick = {
                        pendingDeviceChangeRestore = null
                        performRestore(uri, RestoreManager.ImportMode.UNCHANGED)
                    }) { Text(stringResource(R.string.restore_device_change_unchanged)) }
                    TextButton(onClick = { pendingDeviceChangeRestore = null }) { Text(stringResource(android.R.string.cancel)) }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Surface(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars), tonalElevation = 1.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back_content_description))
                    }
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = modifier.fillMaxWidth().padding(paddingValues).verticalScroll(rememberScrollState())) {
            FluxNote(stringResource(R.string.settings_general_note))
            // Settings' own search: what every module shares (the home screen's searches every module)
            SettingsSearchArea(
                fieldModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                scope = SettingLinkRegistry.sharedCategories,
                hint = stringResource(R.string.settings_search_shared)
            ) {

            SettingsSectionDivider(stringResource(R.string.settings_section_privacy))
            SettingsCategoryRow(
                icon = Icons.Filled.CloudOff,
                title = stringResource(R.string.flux_offline_title),
                description = stringResource(
                    if (SettingsManager.isOfflineMode(context)) R.string.flux_offline_on else R.string.flux_offline_description
                ),
                linkId = SettingLinkIds.MAIN_FLUX_OFFLINE,
                onClick = { onNavigate(SettingsDestination.FluxOffline) }
            )

            // The shell every module works through, then the whole phone's density, as a whole and
            // per app (gaming mode's games too)
            SettingsSectionDivider(stringResource(R.string.settings_section_phone))
            val shell = rememberShellState()
            val shellStatus by shell.status
            val shellPaired by shell.paired
            SettingsCategoryRow(
                icon = Icons.Filled.Terminal,
                title = stringResource(R.string.root_title),
                description = stringResource(R.string.settings_shell_access_description) + " " + stringResource(
                    when {
                        shellStatus == it.palsoftware.pastiera.ShizukuStatus.Connected -> R.string.shell_access_on
                        shellPaired -> R.string.shell_access_paired
                        else -> R.string.shell_access_off
                    }
                ),
                linkId = "main.shell_access",
                onClick = { onNavigate(SettingsDestination.Root) }
            )
            SettingsCategoryRow(
                icon = Icons.Filled.AspectRatio,
                title = stringResource(R.string.density_section),
                description = stringResource(R.string.screen_size_row_description),
                linkId = "main.screen_size",
                onClick = { onNavigate(SettingsDestination.ScreenSize) }
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
            SettingsCategoryRow(
                icon = Icons.Filled.Backup,
                title = stringResource(R.string.backup_now),
                description = stringResource(R.string.backup_now_description),
                linkId = SettingLinkIds.ADVANCED_BACKUP,
                onClick = { backupLauncher.launch(defaultBackupName()) }
            )
            SettingsCategoryRow(
                icon = Icons.Filled.History,
                title = stringResource(R.string.restore_from_file),
                description = stringResource(R.string.restore_from_file_description),
                linkId = SettingLinkIds.ADVANCED_RESTORE,
                onClick = { restoreLauncher.launch(arrayOf("application/zip")) }
            )

            SettingsSectionDivider(stringResource(R.string.settings_section_help_about))
            SettingsCategoryRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.tutorial_show),
                description = stringResource(R.string.tutorial_review_description),
                linkId = SettingLinkIds.ADVANCED_SHOW_TUTORIAL,
                onClick = {
                    SettingsManager.resetTutorialCompleted(context)
                    context.startActivity(Intent(context, TutorialActivity::class.java))
                }
            )
            // The crash log, for reporting a problem with any module
            CrashLogRow()
            SettingsUpdateRows(context)
            SettingsCategoryRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.about_title),
                description = stringResource(R.string.settings_about_version_summary, BuildConfig.VERSION_NAME),
                linkId = SettingLinkIds.MAIN_ABOUT,
                onClick = { onNavigate(SettingsDestination.About) }
            )

            SettingsSectionDivider(stringResource(R.string.settings_section_experimental))
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
}
