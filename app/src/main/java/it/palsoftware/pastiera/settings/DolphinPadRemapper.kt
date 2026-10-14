package it.palsoftware.pastiera.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.gaming.DolphinPads
import it.palsoftware.pastiera.gaming.DolphinPads.Binding
import it.palsoftware.pastiera.gaming.FluxLayout
import it.palsoftware.pastiera.gaming.FluxLayout.Layer
import it.palsoftware.pastiera.gaming.FluxLayout.WiiPad

/** A key and its modifier, short: "Alt+Q". */
@Composable
private fun bindingName(binding: Binding): String =
    (if (binding.layer == Layer.NONE) "" else layerName(binding.layer) + "+") + keyCap(binding.key)

/**
 * One Dolphin game's [pad] drawn as it's held, each button and movement where it sits with the
 * key that presses it: the layout's, or the game's own ([remap]), changed by tapping it.
 * [onSave] gets the game's own keys as they're left.
 */
@Composable
internal fun DolphinPadRemapper(
    pad: WiiPad,
    padName: String,
    remap: Map<String, List<Binding>>,
    swap: Boolean,
    onSave: (Map<String, List<Binding>>) -> Unit,
    onDismiss: () -> Unit
) {
    var own by remember { mutableStateOf(remap) }
    var picking by remember { mutableStateOf<DolphinPads.Input?>(null) }
    val inputs = remember(pad) { DolphinPads.inputs(pad) }
    val controls = remember(pad) { FluxLayout.dolphinControls(if (pad == WiiPad.GAMECUBE) WiiPad.GAMECUBE else pad) }
    val taken = own.values.flatten().toSet()
    fun keysFor(input: DolphinPads.Input): List<Binding> =
        own[input.id] ?: FluxLayout.defaultBindings(controls[input.keys.first()].orEmpty(), swap, taken)

    Dialog(onDismissRequest = { onSave(own); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FluxScreenScaffold(padName, { onSave(own); onDismiss() }, Modifier.fillMaxSize()) {
            FluxNote(stringResource(R.string.dolphin_pad_hint))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 28.dp)) {
                val width = maxWidth
                val height = width / DolphinPads.aspect(pad)
                val body = MaterialTheme.colorScheme.surfaceVariant
                Canvas(modifier = Modifier.width(width).height(height)) { drawBody(pad, body) }
                val chipW = 40.dp
                val chipH = 30.dp
                inputs.forEach { input ->
                    val keys = keysFor(input)
                    val text = keys.take(2).map { bindingName(it) }.joinToString(" ")
                    val changed = input.id in own
                    val shape = RoundedCornerShape(8.dp)
                    Surface(
                        shape = shape,
                        tonalElevation = 4.dp,
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .offset(x = width * input.x - chipW / 2, y = height * input.y - chipH / 2)
                            .width(chipW).height(chipH)
                            .then(if (changed) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                            .clickable { picking = input }
                            .semantics { contentDescription = input.label + ": " + text }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(input.label, fontSize = 8.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text.ifEmpty { "—" }, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Clip, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            if (own.isNotEmpty()) FluxActionRow(
                linkId = null,
                icon = "↺",
                title = stringResource(R.string.game_remap_reset),
                description = stringResource(R.string.game_remap_reset_description)
            ) { own = emptyMap() }
        }
    }

    picking?.let { input ->
        var layer by remember(input.id) { mutableStateOf(Layer.NONE) }
        val defaults = FluxLayout.defaultBindings(controls[input.keys.first()].orEmpty(), swap, taken - own[input.id].orEmpty().toSet())
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(input.label) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    @Composable
                    fun choice(text: String, selected: Boolean, onPick: () -> Unit) = Text(
                        (if (selected) "✓ " else "") + text,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(); picking = null }.padding(vertical = 8.dp)
                    )
                    val defaultText = defaults.take(3).map { bindingName(it) }.joinToString(", ").ifEmpty { stringResource(R.string.dolphin_pad_no_key) }
                    choice(stringResource(R.string.game_remap_default, defaultText), input.id !in own) { own = own - input.id }
                    choice(stringResource(R.string.dolphin_pad_no_key), own[input.id]?.isEmpty() == true) { own = own + (input.id to emptyList()) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Layer.entries.forEach { each ->
                            FilterChip(selected = layer == each, onClick = { layer = each }, label = { Text(layerName(each), maxLines = 1) })
                        }
                    }
                    REMAP_ROWS.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { code ->
                                val binding = Binding(layer, code)
                                val selected = own[input.id]?.contains(binding) == true
                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    tonalElevation = if (selected) 8.dp else 2.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.weight(if (code == android.view.KeyEvent.KEYCODE_SPACE) 4f else 1f).height(38.dp)
                                        .clickable {
                                            // The key now presses this alone
                                            own = own.mapValues { (_, keys) -> keys - binding } + (input.id to listOf(binding))
                                            picking = null
                                        }
                                ) {
                                    Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(keyCap(code), fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/** The controller's outline, as it's held: grips and body, or a Nunchuk beside an upright Wii Remote. */
private fun DrawScope.drawBody(pad: WiiPad, color: androidx.compose.ui.graphics.Color) {
    val w = size.width
    val h = size.height
    fun box(l: Float, t: Float, r: Float, b: Float, radius: Float) =
        drawRoundRect(color, Offset(w * l, h * t), Size(w * (r - l), h * (b - t)), CornerRadius(w * radius, w * radius))
    when (pad) {
        WiiPad.GAMECUBE, WiiPad.CLASSIC -> {
            box(0.04f, 0.14f, 0.96f, 0.66f, 0.12f)
            drawCircle(color, w * 0.16f, Offset(w * 0.2f, h * 0.72f))
            drawCircle(color, w * 0.16f, Offset(w * 0.8f, h * 0.72f))
            box(0.08f, 0.02f, 0.3f, 0.16f, 0.04f)
            box(0.7f, 0.02f, 0.92f, 0.16f, 0.04f)
        }
        WiiPad.NUNCHUK -> {
            box(0.1f, 0.06f, 0.34f, 0.8f, 0.1f)
            box(0.56f, 0.04f, 0.78f, 0.8f, 0.06f)
            box(0.8f, 0.2f, 0.92f, 0.4f, 0.03f)
            box(0.02f, 0.8f, 0.98f, 0.98f, 0.03f)
        }
        WiiPad.SIDEWAYS -> {
            box(0.04f, 0.18f, 0.96f, 0.66f, 0.06f)
            box(0.02f, 0.8f, 0.98f, 0.99f, 0.02f)
        }
    }
}
