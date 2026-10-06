package it.palsoftware.pastiera.apps
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.settings.FluxAppIcon

/**
 * Picks several apps at once, searchable, with Select all and Select none: the ones ticked when
 * it opens come first. [onDone] gets the whole choice.
 */
@Composable
fun MultiAppPickerDialog(
    title: String,
    initial: Set<String>,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val apps = remember {
        // Flux Keyboard itself included: its own settings can keep keyboard swipes out too
        AppListHelper.getInstalledApps(context)
            .sortedWith(compareBy<InstalledApp> { it.packageName !in initial }.thenBy { it.appName.lowercase() })
    }
    var chosen by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    val shown = remember(query) {
        if (query.isBlank()) apps else apps.filter {
            it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 12.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp)
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    placeholder = { Text(stringResource(R.string.app_picker_search_placeholder)) },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Select all: every app shown (all of them, or the search's)
                    TextButton(onClick = { chosen = chosen + shown.map { it.packageName } }) {
                        Text(stringResource(R.string.app_picker_select_all))
                    }
                    TextButton(onClick = { chosen = chosen - shown.map { it.packageName }.toSet() }) {
                        Text(stringResource(R.string.app_picker_select_none))
                    }
                }
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(shown, key = { it.packageName }) { app ->
                        val ticked = app.packageName in chosen
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { chosen = if (ticked) chosen - app.packageName else chosen + app.packageName }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FluxAppIcon(app.packageName)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Checkbox(checked = ticked, onCheckedChange = null)
                        }
                    }
                }
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.app_picker_cancel)) }
                    TextButton(onClick = { onDone(chosen) }) {
                        Text(stringResource(R.string.app_picker_done, chosen.size))
                    }
                }
            }
        }
    }
}
