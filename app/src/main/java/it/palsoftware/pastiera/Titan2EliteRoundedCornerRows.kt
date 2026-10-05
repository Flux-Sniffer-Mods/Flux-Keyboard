package it.palsoftware.pastiera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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

/**
 * The Titan 2 Elite's rounded status bar (moved here from the status bar screen, so every
 * Titan 2 Elite setting is on the Titan 2 Elite screen): the rounded corners, how round the
 * upper button corners are, and how far corner icons may shrink.
 */
@Composable
internal fun Titan2EliteRoundedCornerRows(onRoundedCornersChanged: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    var titan2EliteRoundedCornerInsetsEnabled by remember {
        mutableStateOf(SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context))
    }
    var topCornerMultiplier by remember { mutableStateOf(SettingsManager.getTitan2EliteTopCornerMultiplier(context)) }
    var maxIconShrink by remember { mutableStateOf(SettingsManager.getTitan2EliteMaxIconShrink(context)) }
        Surface(modifier = Modifier.fillMaxWidth().settingRow("status_bar.rounded_corners")) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.titan2_elite_rounded_corners_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.titan2_elite_rounded_corners_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = titan2EliteRoundedCornerInsetsEnabled,
                    onCheckedChange = { enabled ->
                        titan2EliteRoundedCornerInsetsEnabled = enabled
                        SettingsManager.setTitan2EliteRoundedCornerInsetsEnabled(context, enabled)
                        onRoundedCornersChanged(enabled)
                    }
                )
            }
        }

        if (titan2EliteRoundedCornerInsetsEnabled) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(stringResource(R.string.titan2_elite_top_corner_title), modifier = Modifier.fillMaxWidth().settingRow("status_bar.top_corner"), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.titan2_elite_top_corner_description),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 4, 6).forEach { multiplier ->
                        FilterChip(
                            selected = topCornerMultiplier == multiplier,
                            onClick = {
                                topCornerMultiplier = multiplier
                                SettingsManager.setTitan2EliteTopCornerMultiplier(context, multiplier)
                            },
                            label = { Text("${multiplier}×") }
                        )
                    }
                }
                Text(stringResource(R.string.titan2_elite_max_icon_shrink_title, maxIconShrink),
                    modifier = Modifier.fillMaxWidth().settingRow("status_bar.max_icon_shrink"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.titan2_elite_max_icon_shrink_description),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = maxIconShrink.toFloat(), valueRange = 0f..90f, steps = 8,
                    onValueChange = { maxIconShrink = (it / 10f).toInt() * 10 },
                    onValueChangeFinished = { SettingsManager.setTitan2EliteMaxIconShrink(context, maxIconShrink) })
            }
        }

}
