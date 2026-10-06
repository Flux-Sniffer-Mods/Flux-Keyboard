package it.palsoftware.pastiera.theme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.widget.Toast
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.settings.SettingLinkIds
import it.palsoftware.pastiera.settings.settingRow
import it.palsoftware.pastiera.getEffectiveKeyboardTheme
import it.palsoftware.pastiera.getKeyboardBackgroundAutoColours
import it.palsoftware.pastiera.getKeyboardBackgroundFraming
import it.palsoftware.pastiera.getKeyboardBackgroundKeyOpacity
import it.palsoftware.pastiera.setKeyboardBackgroundAutoColours
import it.palsoftware.pastiera.setKeyboardBackgroundFraming
import it.palsoftware.pastiera.setKeyboardBackgroundKeyOpacity

/** Flux Keyboard: a picture behind the keyboard, with Auto colours and the keys' opacity. */
@Composable
fun KeyboardBackgroundImageSettings() {
    val context = LocalContext.current
    var hasImage by remember { mutableStateOf(KeyboardBackgroundImage.exists(context)) }
    var autoColours by remember { mutableStateOf(SettingsManager.getKeyboardBackgroundAutoColours(context)) }
    var opacity by remember { mutableFloatStateOf(SettingsManager.getKeyboardBackgroundKeyOpacity(context).toFloat()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (KeyboardBackgroundImage.save(context, uri)) hasImage = true
            else Toast.makeText(context, R.string.keyboard_background_image_failed, Toast.LENGTH_SHORT).show()
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().settingRow(SettingLinkIds.KEYBOARD_BACKGROUND_IMAGE),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.keyboard_background_image_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.keyboard_background_image_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (hasImage) {
                TextButton(onClick = {
                    KeyboardBackgroundImage.remove(context)
                    hasImage = false
                }) { Text(stringResource(R.string.keyboard_background_image_remove)) }
            }
            OutlinedButton(onClick = { picker.launch("image/*") }) {
                Text(stringResource(if (hasImage) R.string.keyboard_background_image_change else R.string.keyboard_background_image_choose))
            }
        }
        if (hasImage) {
            KeyboardBackgroundFramingPreview(refreshKey = opacity to autoColours)
            Row(
                modifier = Modifier.fillMaxWidth().settingRow(SettingLinkIds.KEYBOARD_BACKGROUND_AUTO_COLOURS),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.keyboard_background_auto_colours_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.keyboard_background_auto_colours_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoColours, onCheckedChange = {
                    autoColours = it
                    SettingsManager.setKeyboardBackgroundAutoColours(context, it)
                })
            }
            if (autoColours) {
                Column(modifier = Modifier.fillMaxWidth().settingRow(SettingLinkIds.KEYBOARD_BACKGROUND_KEY_OPACITY)) {
                    Text(
                        stringResource(R.string.keyboard_background_key_opacity_title, opacity.toInt()),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it.toInt().toFloat() },
                        onValueChangeFinished = { SettingsManager.setKeyboardBackgroundKeyOpacity(context, opacity.toInt()) },
                        valueRange = 0f..100f
                    )
                }
            }
        }
    }
}

/**
 * The bar over the picture, as the keyboard draws it: drag to move the picture, the slider zooms.
 * Saved when a drag or the slider lets go.
 */
@Composable
private fun KeyboardBackgroundFramingPreview(refreshKey: Any) {
    val context = LocalContext.current
    val pictureStamp = KeyboardBackgroundImage.file(context).lastModified()
    val bitmap = remember(pictureStamp) { KeyboardBackgroundImage.bitmap(context) } ?: return
    var framing by remember { mutableStateOf(SettingsManager.getKeyboardBackgroundFraming(context)) }
    val theme = remember(refreshKey) {
        SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE)
    }
    val matrix = remember { android.graphics.Matrix() }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.keyboard_background_position_title),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            stringResource(R.string.keyboard_background_position_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .aspectRatio(4.6f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { SettingsManager.setKeyboardBackgroundFraming(context, framing) }
                    ) { change, drag ->
                        change.consume()
                        val (turnedWidth, turnedHeight) = framing.turnedSize(bitmap.width, bitmap.height)
                        val scale = maxOf(size.width.toFloat() / turnedWidth, size.height.toFloat() / turnedHeight) *
                            framing.zoom
                        val spareX = turnedWidth * scale - size.width
                        val spareY = turnedHeight * scale - size.height
                        framing = framing.copy(
                            x = if (spareX > 0f) (framing.x - drag.x / spareX).coerceIn(0f, 1f) else framing.x,
                            y = if (spareY > 0f) (framing.y - drag.y / spareY).coerceIn(0f, 1f) else framing.y
                        )
                    }
                }
        ) {
            drawIntoCanvas { canvas ->
                KeyboardBackgroundImage.frame(
                    matrix, bitmap.width, bitmap.height, 0f, 0f, size.width, size.height, framing
                )
                canvas.nativeCanvas.drawBitmap(bitmap, matrix, paint)
            }
            // The bar's buttons as the keyboard shows them: two corner buttons, three suggestions
            val gap = 3.dp.toPx()
            val cell = (size.width - gap * 6) / 5f
            val radius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
            for (index in 0 until 5) {
                val corner = index == 0 || index == 4
                drawRoundRect(
                    color = androidx.compose.ui.graphics.Color(if (corner) theme.specialKey else theme.normalKey),
                    topLeft = androidx.compose.ui.geometry.Offset(gap + index * (cell + gap), gap * 2),
                    size = androidx.compose.ui.geometry.Size(cell, size.height - gap * 4),
                    cornerRadius = radius
                )
            }
        }
        Text(
            stringResource(R.string.keyboard_background_zoom_title, (framing.zoom * 100).toInt()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        Slider(
            value = framing.zoom,
            onValueChange = { framing = framing.copy(zoom = it) },
            onValueChangeFinished = { SettingsManager.setKeyboardBackgroundFraming(context, framing) },
            valueRange = 1f..KeyboardBackgroundImage.Framing.MAX_ZOOM
        )
        androidx.compose.foundation.layout.Row {
            TextButton(onClick = {
                framing = framing.rotated()
                SettingsManager.setKeyboardBackgroundFraming(context, framing)
            }) { Text(stringResource(R.string.keyboard_background_rotate)) }
            TextButton(onClick = {
                // The turn stays: only where it sits and how big
                framing = KeyboardBackgroundImage.Framing(turns = framing.turns)
                SettingsManager.setKeyboardBackgroundFraming(context, framing)
            }) { Text(stringResource(R.string.keyboard_background_position_reset)) }
        }
    }
}
