package it.palsoftware.pastiera.settings

import android.view.KeyEvent
import it.palsoftware.pastiera.gaming.GamepadDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.gaming.FluxLayout
import it.palsoftware.pastiera.gaming.FluxLayout.Control
import it.palsoftware.pastiera.gaming.FluxLayout.Layer

/** The keyboard as laid out, the keys gaming mode reads (modifiers aside). */
internal val REMAP_ROWS = listOf(
    "QWERTYUIOP".map { KeyEvent.KEYCODE_A + (it - 'A') },
    "ASDFGHJKL".map { KeyEvent.KEYCODE_A + (it - 'A') } + KeyEvent.KEYCODE_DEL,
    "ZXCVBNM".map { KeyEvent.KEYCODE_A + (it - 'A') } + KeyEvent.KEYCODE_ENTER,
    listOf(KeyEvent.KEYCODE_SPACE),
    listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN)
)

/** The order controls are offered in. */
private val CONTROL_ORDER = listOf(
    Control.A, Control.B, Control.X, Control.Y, Control.L1, Control.R1, Control.L2, Control.R2,
    Control.L2_ANALOG, Control.R2_ANALOG, Control.START, Control.SELECT, Control.HOME, Control.L3, Control.R3,
    Control.UP, Control.DOWN, Control.LEFT, Control.RIGHT,
    Control.LS_UP, Control.LS_DOWN, Control.LS_LEFT, Control.LS_RIGHT,
    Control.RS_UP, Control.RS_DOWN, Control.RS_LEFT, Control.RS_RIGHT
)

internal fun keyCap(code: Int): String = when (code) {
    in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> ('A' + (code - KeyEvent.KEYCODE_A)).toString()
    KeyEvent.KEYCODE_SPACE -> "Space"
    KeyEvent.KEYCODE_ENTER -> "⏎"
    KeyEvent.KEYCODE_DEL -> "⌫"
    KeyEvent.KEYCODE_VOLUME_UP -> "Vol +"
    KeyEvent.KEYCODE_VOLUME_DOWN -> "Vol −"
    else -> code.toString()
}

/** A control, short enough for a key cap. */
private fun shortName(control: Control): String = when (control) {
    Control.UP -> "D↑"; Control.DOWN -> "D↓"; Control.LEFT -> "D←"; Control.RIGHT -> "D→"
    Control.L1 -> "LB"; Control.R1 -> "RB"; Control.L2 -> "LT"; Control.R2 -> "RT"
    Control.L2_ANALOG -> "LT~"; Control.R2_ANALOG -> "RT~"
    Control.SELECT -> "Select"; Control.START -> "Start"; Control.HOME -> "Home"
    Control.LS_UP -> "L↑"; Control.LS_DOWN -> "L↓"; Control.LS_LEFT -> "L←"; Control.LS_RIGHT -> "L→"
    Control.RS_UP -> "R↑"; Control.RS_DOWN -> "R↓"; Control.RS_LEFT -> "R←"; Control.RS_RIGHT -> "R→"
    else -> control.name
}

/** A control in full, for the list and for screen readers. */
@Composable
private fun longName(control: Control): String {
    val left = stringResource(R.string.game_remap_left_stick)
    val right = stringResource(R.string.game_remap_right_stick)
    val dpad = stringResource(R.string.game_remap_dpad)
    val analog = stringResource(R.string.game_remap_analog)
    return when (control) {
        Control.UP -> "$dpad ↑"; Control.DOWN -> "$dpad ↓"; Control.LEFT -> "$dpad ←"; Control.RIGHT -> "$dpad →"
        Control.LS_UP -> "$left ↑"; Control.LS_DOWN -> "$left ↓"; Control.LS_LEFT -> "$left ←"; Control.LS_RIGHT -> "$left →"
        Control.RS_UP -> "$right ↑"; Control.RS_DOWN -> "$right ↓"; Control.RS_LEFT -> "$right ←"; Control.RS_RIGHT -> "$right →"
        Control.L3 -> "$left (L3)"; Control.R3 -> "$right (R3)"
        Control.L2_ANALOG -> "LT ($analog)"; Control.R2_ANALOG -> "RT ($analog)"
        else -> shortName(control)
    }
}

@Composable
internal fun layerName(layer: Layer): String = when (layer) {
    Layer.NONE -> stringResource(R.string.game_remap_alone)
    Layer.SYM -> "SYM"
    Layer.RIGHT_SHIFT -> stringResource(R.string.game_remap_right_shift)
    Layer.ALT -> "Alt"
    Layer.SHIFT -> "Shift"
}

/**
 * The keyboard controls drawn as the keyboard: each key shows what it does, alone or with a
 * modifier held (chosen above it). Tap a key to change what it does; changed keys are outlined.
 */
@Composable
internal fun GameLayoutRemapper(modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val initial = remember { FluxLayout.loadOverrides(context); FluxLayout.overrides }
    KeyLayoutEditor(
        title = stringResource(R.string.game_mode_layout_title),
        initial = initial,
        base = { code, layer -> FluxLayout.defaultControlFor(code, FluxLayout.modifiersOf(layer)) },
        onChange = { FluxLayout.setOverrides(context, it) },
        onBack = onBack,
        modifier = modifier
    )
}

/**
 * The keyboard drawn as it is, each key showing the control it makes, alone or with the modifier
 * chosen; tapping one changes it. [base] is what a key makes unchanged (the layout's, or for one
 * game the layout as changed in Keyboard controls); [onChange] gets the keys changed from it.
 */
@Composable
internal fun KeyLayoutEditor(
    title: String,
    initial: Map<Pair<Layer, Int>, Control?>,
    base: (Int, Layer) -> Control?,
    onChange: (Map<Pair<Layer, Int>, Control?>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
    note: String? = null
) {
    var overrides by remember { mutableStateOf(initial) }
    var layer by remember { mutableStateOf(Layer.NONE) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var showingList by remember { mutableStateOf(false) }
    // The same keys seen from the controller's side: each button with the keys that press it
    var controllerView by remember { mutableStateOf(false) }
    var pickingButton by remember { mutableStateOf<GamepadDrawing.Spot?>(null) }
    fun save(changed: Map<Pair<Layer, Int>, Control?>) { overrides = changed; onChange(changed) }
    fun current(code: Int): Control? = (layer to code).let { if (it in overrides) overrides[it] else base(code, layer) }

    FluxScreenScaffold(title, onBack, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            FilterChip(selected = !controllerView, onClick = { controllerView = false }, label = { Text(stringResource(R.string.game_remap_view_keyboard)) })
            FilterChip(selected = controllerView, onClick = { controllerView = true }, label = { Text(stringResource(R.string.game_remap_view_controller)) })
        }
        if (controllerView) {
            FluxNote(stringResource(R.string.game_remap_controller_description))
            GamepadView(overrides, base) { pickingButton = it }
        } else {
        FluxNote(note ?: stringResource(R.string.game_remap_description))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Layer.entries.forEach { each ->
                FilterChip(selected = layer == each, onClick = { layer = each }, label = { Text(layerName(each), maxLines = 1) })
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            REMAP_ROWS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { code ->
                        val control = current(code)
                        RemapKey(
                            cap = keyCap(code),
                            label = control?.let { shortName(it) },
                            description = keyCap(code) + ": " + (control?.let { longName(it) } ?: stringResource(R.string.game_remap_nothing)),
                            changed = (layer to code) in overrides,
                            weight = if (code == KeyEvent.KEYCODE_SPACE) 4f else 1f
                        ) { picking = code }
                    }
                }
            }
        }
        FluxNote(stringResource(R.string.game_remap_hint))
        }
        FluxActionRow(
            linkId = null,
            icon = "≡",
            title = stringResource(R.string.game_remap_list),
            description = stringResource(R.string.game_mode_layout_description)
        ) { showingList = true }
        if (overrides.isNotEmpty()) FluxActionRow(
            linkId = null,
            icon = "↺",
            title = stringResource(R.string.game_remap_reset),
            description = stringResource(R.string.game_remap_reset_description)
        ) { save(emptyMap()) }
    }

    if (showingList) AlertDialog(
        onDismissRequest = { showingList = false },
        title = { Text(stringResource(R.string.game_mode_layout_title)) },
        text = { Text(stringResource(R.string.game_mode_layout), modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { showingList = false }) { Text(stringResource(android.R.string.ok)) } }
    )

    pickingButton?.let { spot ->
        var pickLayer by remember(spot) { mutableStateOf(Layer.NONE) }
        val now = GamepadDrawing.keysFor(overrides, base, REMAP_KEYS, spot.control)
        AlertDialog(
            onDismissRequest = { pickingButton = null },
            title = { Text(longName(spot.control)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val defaults = GamepadDrawing.keysFor(emptyMap(), base, REMAP_KEYS, spot.control)
                    val changed = now != defaults
                    Text(
                        (if (!changed) "✓ " else "") + stringResource(
                            R.string.game_remap_default,
                            defaults.map { pairName(it) }.joinToString(", ").ifEmpty { stringResource(R.string.dolphin_pad_no_key) }
                        ),
                        fontWeight = if (!changed) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clickable {
                            save(GamepadDrawing.restore(overrides, base, spot.control)); pickingButton = null
                        }.padding(vertical = 8.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Layer.entries.forEach { each ->
                            FilterChip(selected = pickLayer == each, onClick = { pickLayer = each }, label = { Text(layerName(each), maxLines = 1) })
                        }
                    }
                    REMAP_ROWS.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { code ->
                                val selected = (pickLayer to code) in now
                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    tonalElevation = if (selected) 8.dp else 2.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.weight(if (code == KeyEvent.KEYCODE_SPACE) 4f else 1f).height(38.dp).clickable {
                                        save(GamepadDrawing.assign(overrides, base, REMAP_KEYS, spot.control, pickLayer, code))
                                        pickingButton = null
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
            confirmButton = { TextButton(onClick = { pickingButton = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    picking?.let { code ->
        val key = layer to code
        val default = base(code, layer)
        val title = (if (layer == Layer.NONE) "" else layerName(layer) + " + ") + keyCap(code)
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    @Composable
                    fun choice(text: String, selected: Boolean, onPick: () -> Unit) {
                        Text(
                            (if (selected) "✓ " else "") + text,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(); picking = null }.padding(vertical = 10.dp)
                        )
                    }
                    val now = current(code)
                    choice(stringResource(R.string.game_remap_default, default?.let { longName(it) } ?: stringResource(R.string.game_remap_nothing)),
                        key !in overrides) { save(overrides - key) }
                    choice(stringResource(R.string.game_remap_nothing), key in overrides && now == null) { save(overrides + (key to null)) }
                    CONTROL_ORDER.forEach { control ->
                        choice(longName(control), key in overrides && now == control) { save(overrides + (key to control)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun RowScope.RemapKey(cap: String, label: String?, description: String, changed: Boolean, weight: Float, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Surface(
        shape = shape,
        tonalElevation = if (label != null) 3.dp else 0.dp,
        modifier = Modifier
            .weight(weight)
            .height(52.dp)
            .then(if (changed) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(cap, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                label ?: "",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Every key the drawings offer. */
internal val REMAP_KEYS: List<Int> = REMAP_ROWS.flatten()

/** A key and the modifier held with it, short: "Alt+Q". */
@Composable
private fun pairName(key: Pair<Layer, Int>): String =
    (if (key.first == Layer.NONE) "" else layerName(key.first) + "+") + keyCap(key.second)

/** A gamepad's buttons where they sit as it's held, each showing the keys that press it; tapped to change them. */
@Composable
private fun GamepadView(overrides: Map<Pair<Layer, Int>, Control?>, base: (Int, Layer) -> Control?, onPick: (GamepadDrawing.Spot) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 28.dp)) {
        val width = maxWidth
        val height = width / GamepadDrawing.ASPECT
        // Only the buttons, where they sit on a controller
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(width).height(height))
        val chipW = 40.dp
        val chipH = 30.dp
        GamepadDrawing.SPOTS.forEach { spot ->
            val keys = GamepadDrawing.keysFor(overrides, base, REMAP_KEYS, spot.control)
            val text = keys.take(2).map { pairName(it) }.joinToString(" ")
            val changed = keys != GamepadDrawing.keysFor(emptyMap(), base, REMAP_KEYS, spot.control)
            val shape = RoundedCornerShape(8.dp)
            Surface(
                shape = shape,
                tonalElevation = 4.dp,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .offset(x = width * spot.x - chipW / 2, y = height * spot.y - chipH / 2)
                    .width(chipW).height(chipH)
                    .then(if (changed) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                    .clickable { onPick(spot) }
                    .semantics { contentDescription = spot.label + ": " + text }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(spot.label, fontSize = 8.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text.ifEmpty { "—" }, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Clip, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
