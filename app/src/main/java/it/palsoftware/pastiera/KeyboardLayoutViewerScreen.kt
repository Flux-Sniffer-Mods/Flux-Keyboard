package it.palsoftware.pastiera

import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.data.layout.BundledLayoutAssets
import it.palsoftware.pastiera.data.layout.JsonLayoutLoader
import it.palsoftware.pastiera.data.layout.LayoutFileStore
import it.palsoftware.pastiera.data.layout.LayoutFileStore.LayoutConflictPolicy
import it.palsoftware.pastiera.data.layout.LayoutFileStore.LayoutImportError
import it.palsoftware.pastiera.data.layout.LayoutFileStore.LayoutImportResult
import it.palsoftware.pastiera.data.layout.LayoutMapping
import it.palsoftware.pastiera.data.layout.TapMapping

private data class KeyMappingRowModel(
    val keyCode: Int,
    val keyLabel: String,
    val mapping: LayoutMapping?
)

/** A key's label without Android's KEYCODE_ prefix: "Q", "1", "LEFT_BRACKET". */
private fun keyLabel(keyCode: Int): String = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")

/**
 * A keyboard layout's map, editable in place: tap a key to change what it types, with and
 * without Shift. Saving a bundled layout keeps your copy alongside it, and it can be restored.
 */
@Composable
fun KeyboardLayoutViewerScreen(
    layoutName: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val working = remember(layoutName) { mutableStateMapOf<Int, LayoutMapping>() }
    var isLoading by remember(layoutName) { mutableStateOf(true) }
    var loadFailed by remember(layoutName) { mutableStateOf(false) }
    var dirty by remember(layoutName) { mutableStateOf(false) }
    var reloadKey by remember(layoutName) { mutableStateOf(0) }
    var editingKey by remember { mutableStateOf<Int?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var copyDialog by remember { mutableStateOf(false) }

    val isBundled = remember(layoutName) {
        runCatching { BundledLayoutAssets.openLayout(context.assets, layoutName)?.use { true } ?: false }.getOrDefault(false)
    }
    var hasOwnCopy by remember(layoutName, reloadKey) { mutableStateOf(LayoutFileStore.layoutExists(context, layoutName)) }
    val metadata = remember(layoutName, reloadKey) {
        LayoutFileStore.getLayoutMetadata(context, layoutName)
            ?: LayoutFileStore.getLayoutMetadataFromAssets(context.assets, layoutName)
    }

    LaunchedEffect(layoutName, reloadKey) {
        val loaded = JsonLayoutLoader.loadLayout(context.assets, layoutName, context)
        working.clear()
        loaded?.let { working.putAll(it) }
        loadFailed = loaded == null
        dirty = false
        isLoading = false
    }

    val rows = LayoutFileStore.editableKeyCodes.map { KeyMappingRowModel(it, keyLabel(it), working[it]) }

    fun save(): Boolean {
        val saved = LayoutFileStore.saveLayout(
            context, layoutName, working.toMap(),
            name = metadata?.name, description = metadata?.description
        )
        if (saved) {
            dirty = false
            hasOwnCopy = true
            SettingsManager.notifyKeyboardLayoutAutoMappingUpdated(context)
        }
        Toast.makeText(context, if (saved) R.string.layout_editor_saved else R.string.layout_editor_save_failed, Toast.LENGTH_SHORT).show()
        return saved
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = LayoutFileStore.buildLayoutJsonString(layoutName, working.toMap(), metadata?.name ?: layoutName, metadata?.description)
        val exported = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) } != null
        }.getOrDefault(false)
        Toast.makeText(context, if (exported) R.string.layout_editor_exported else R.string.layout_editor_export_failed, Toast.LENGTH_SHORT).show()
    }

    fun leave() {
        if (dirty) confirmDiscard = true else onBack()
    }
    BackHandler { leave() }

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
                    IconButton(onClick = { leave() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                        Text(
                            text = stringResource(R.string.keyboard_layout_viewer_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.keyboard_layout_viewer_subtitle, metadata?.name ?: layoutName),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { save() }, enabled = dirty) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = stringResource(R.string.layout_save_content_description)
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }, enabled = !loadFailed) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.layout_editor_export)) },
                                onClick = { showMenu = false; exportLauncher.launch("$layoutName.json") }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.layout_editor_copy)) },
                                onClick = { showMenu = false; copyDialog = true }
                            )
                            if (isBundled && hasOwnCopy) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.layout_editor_restore)) },
                                    onClick = {
                                        showMenu = false
                                        LayoutFileStore.deleteLayout(context, layoutName)
                                        SettingsManager.notifyKeyboardLayoutAutoMappingUpdated(context)
                                        reloadKey++
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                loadFailed -> {
                    Text(
                        text = stringResource(R.string.keyboard_layout_viewer_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                text = stringResource(
                                    if (isBundled && hasOwnCopy) R.string.layout_editor_intro_changed else R.string.layout_editor_intro
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                        items(rows, key = { it.keyCode }) { item ->
                            KeyMappingRow(item, onClick = { editingKey = item.keyCode })
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }

    editingKey?.let { keyCode ->
        KeyEditDialog(
            keyLabel = keyLabel(keyCode),
            mapping = working[keyCode],
            onDismiss = { editingKey = null },
            onSave = { lower, upper ->
                val old = working[keyCode]
                if (lower.isEmpty()) {
                    if (old != null) { working.remove(keyCode); dirty = true }
                } else {
                    val updated = (old ?: LayoutMapping(lower, upper)).copy(lowercase = lower, uppercase = upper.ifEmpty { lower })
                    if (updated != old) { working[keyCode] = updated; dirty = true }
                }
                editingKey = null
            }
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.layout_editor_unsaved_title)) },
            text = { Text(stringResource(R.string.layout_editor_unsaved_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; if (save()) onBack() }) {
                    Text(stringResource(R.string.layout_editor_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false; onBack() }) {
                    Text(stringResource(R.string.layout_editor_discard))
                }
            }
        )
    }

    if (copyDialog) {
        CopyLayoutDialog(
            suggested = (metadata?.name ?: layoutName) + " " + stringResource(R.string.layout_editor_copy_suffix),
            onDismiss = { copyDialog = false },
            onCopy = { name ->
                val id = name.trim()
                val result = if (runCatching { BundledLayoutAssets.openLayout(context.assets, id)?.use { true } }.getOrNull() == true) {
                    LayoutImportResult.Failure(LayoutImportError.NAME_CONFLICT)
                } else {
                    LayoutFileStore.saveLayoutFromJson(
                        context, id,
                        LayoutFileStore.buildLayoutJsonString(id, working.toMap(), id, metadata?.description),
                        LayoutConflictPolicy.FAIL
                    )
                }
                when (result) {
                    is LayoutImportResult.Success -> {
                        copyDialog = false
                        Toast.makeText(context, context.getString(R.string.layout_editor_copied, id), Toast.LENGTH_SHORT).show()
                        null
                    }
                    is LayoutImportResult.Failure -> context.getString(
                        if (result.error == LayoutImportError.NAME_CONFLICT) R.string.layout_editor_copy_exists else R.string.layout_editor_copy_invalid
                    )
                }
            }
        )
    }
}

@Composable
private fun KeyMappingRow(
    model: KeyMappingRowModel,
    onClick: () -> Unit
) {
    val mapping = model.mapping
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 0.dp,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = model.keyLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = 28.dp).padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            if (mapping == null) {
                Text(
                    text = stringResource(R.string.layout_editor_unmapped),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                return@Row
            }

            Text(
                text = "${mapping.lowercase}/${mapping.uppercase}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.widthIn(min = 72.dp)
            )

            MultiTapBadge(enabled = mapping.multiTapEnabled)

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                mapping.taps.forEach { tap ->
                    val label = if (tap.uppercase.isNotBlank()) {
                        "${tap.lowercase}/${tap.uppercase}"
                    } else {
                        tap.lowercase
                    }
                    TapChip(label = label)
                }
            }
        }
    }
}

@Composable
private fun KeyEditDialog(
    keyLabel: String,
    mapping: LayoutMapping?,
    onDismiss: () -> Unit,
    onSave: (lowercase: String, uppercase: String) -> Unit
) {
    var lower by remember { mutableStateOf(mapping?.lowercase.orEmpty()) }
    var upper by remember { mutableStateOf(mapping?.uppercase.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.layout_editor_key_title, keyLabel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lower,
                    onValueChange = { lower = it },
                    label = { Text(stringResource(R.string.layout_editor_key_lower)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = upper,
                    onValueChange = { upper = it },
                    label = { Text(stringResource(R.string.layout_editor_key_upper)) },
                    placeholder = { Text(lower.uppercase()) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(
                        if (mapping?.multiTapEnabled == true) R.string.layout_editor_key_multitap_note else R.string.layout_editor_key_note
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(lower, upper.ifEmpty { lower.uppercase() }) }) {
                Text(stringResource(R.string.layout_editor_done))
            }
        },
        dismissButton = {
            Row {
                if (mapping != null) {
                    TextButton(onClick = { onSave("", "") }) {
                        Text(stringResource(R.string.layout_editor_key_clear))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    )
}

/** [onCopy] returns an error to show, or null once the copy is saved. */
@Composable
private fun CopyLayoutDialog(
    suggested: String,
    onDismiss: () -> Unit,
    onCopy: (String) -> String?
) {
    var name by remember { mutableStateOf(suggested) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.layout_editor_copy)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text(stringResource(R.string.layout_editor_copy_name)) },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { error = onCopy(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.layout_editor_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun MultiTapBadge(enabled: Boolean) {
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        tonalElevation = 0.dp,
        color = containerColor
    ) {
        Text(
            text = if (enabled) {
                stringResource(R.string.keyboard_layout_viewer_multitap_on)
            } else {
                stringResource(R.string.keyboard_layout_viewer_multitap_off)
            },
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun TapChip(
    label: String
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
