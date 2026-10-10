package it.palsoftware.pastiera.settings

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.gaming.GameAction

/**
 * A profile's keys drawn as what they are: the keyboard as a grid of its keys, each showing what
 * it does in the game, or a controller with the key on each of its buttons. Tap a key to choose
 * its action, or a button to press the key it goes on.
 */
@Composable
internal fun GameKeyMapper(keys: Map<Int, GameAction>, gamepad: Boolean, onChange: (Map<Int, GameAction>) -> Unit) {
    var controllerView by remember { mutableStateOf(gamepad) }
    var pickingFor by remember { mutableStateOf<Int?>(null) }
    var capturingFor by remember { mutableStateOf<GameAction?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !controllerView, onClick = { controllerView = false }, label = { Text(stringResource(R.string.game_map_keyboard)) })
        FilterChip(selected = controllerView, onClick = { controllerView = true }, label = { Text(stringResource(R.string.game_map_controller)) })
    }
    if (controllerView) {
        ControllerView(keys) { capturingFor = it }
        Text(stringResource(R.string.game_map_controller_hint), style = MaterialTheme.typography.bodySmall)
    } else {
        KeyboardView(keys) { pickingFor = it }
        Text(stringResource(R.string.game_map_keyboard_hint), style = MaterialTheme.typography.bodySmall)
    }
    pickingFor?.let { key ->
        ActionPicker(key, keys[key], onPick = { action ->
            onChange(if (action == null) keys - key else keys + (key to action))
            pickingFor = null
        }, onDismiss = { pickingFor = null })
    }
    capturingFor?.let { action ->
        KeyForButton(action, onKey = { key ->
            onChange(keys + (key to action))
            capturingFor = null
        }, onClear = {
            onChange(keys.filterValues { it != action })
            capturingFor = null
        }, onDismiss = { capturingFor = null })
    }
}

/** A key's name on its cap ("Q", "Space", "Vol +"). */
internal fun capName(code: Int): String = when (code) {
    in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> ('A' + (code - KeyEvent.KEYCODE_A)).toString()
    KeyEvent.KEYCODE_SPACE -> "Space"
    KeyEvent.KEYCODE_ENTER -> "⏎"
    KeyEvent.KEYCODE_DEL -> "⌫"
    KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> "⇧"
    KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> "Alt"
    KeyEvent.KEYCODE_SYM -> "Sym"
    KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> "Ctrl"
    KeyEvent.KEYCODE_VOLUME_UP -> "Vol +"
    KeyEvent.KEYCODE_VOLUME_DOWN -> "Vol −"
    else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ').lowercase()
        .replaceFirstChar { it.uppercase() }.take(7)
}

/** What a key does, short enough for its cap. */
internal fun shortAction(action: GameAction): String = when (action) {
    GameAction.DPAD_UP -> "↑"; GameAction.DPAD_DOWN -> "↓"
    GameAction.DPAD_LEFT -> "←"; GameAction.DPAD_RIGHT -> "→"
    GameAction.BUTTON_A -> "A"; GameAction.BUTTON_B -> "B"; GameAction.BUTTON_X -> "X"; GameAction.BUTTON_Y -> "Y"
    GameAction.L1 -> "LB"; GameAction.R1 -> "RB"; GameAction.L2 -> "LT"; GameAction.R2 -> "RT"
    GameAction.L3 -> "LS"; GameAction.R3 -> "RS"
    GameAction.START -> "Start"; GameAction.SELECT -> "Back"; GameAction.HOME -> "Home"
    GameAction.KEY_A -> "A key"
    GameAction.MOUSE_LEFT -> "Click"; GameAction.MOUSE_RIGHT -> "R-click"
    else -> action.label
}

private val ROWS = listOf(
    "QWERTYUIOP".map { KeyEvent.KEYCODE_A + (it - 'A') },
    "ASDFGHJKL".map { KeyEvent.KEYCODE_A + (it - 'A') } + KeyEvent.KEYCODE_DEL,
    listOf(KeyEvent.KEYCODE_ALT_LEFT) + "ZXCVBNM".map { KeyEvent.KEYCODE_A + (it - 'A') } + KeyEvent.KEYCODE_ENTER,
    listOf(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SYM, KeyEvent.KEYCODE_SPACE)
)

@Composable
private fun KeyboardView(keys: Map<Int, GameAction>, onKey: (Int) -> Unit) {
    // The keys laid out as on the keyboard, then the phone's buttons and any other set
    val laid = ROWS.flatten().toSet()
    val others = (listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN) + keys.keys.filterNot { it in laid })
        .distinct()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { code -> KeyCap(code, keys[code], weight = if (code == KeyEvent.KEYCODE_SPACE) 4f else 1f, onClick = { onKey(code) }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            others.forEach { code -> KeyCap(code, keys[code], weight = 1f, onClick = { onKey(code) }) }
        }
    }
}

@Composable
private fun RowScope.KeyCap(code: Int, action: GameAction?, weight: Float, onClick: () -> Unit) {
    val mapped = action != null
    Column(
        modifier = Modifier
            .weight(weight)
            .height(48.dp)
            .background(
                if (mapped) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(capName(code), fontSize = 10.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            action?.let { shortAction(it) } ?: "",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/** Each controller button's place on a 100 x 60 drawing. */
private val PLACES: List<Pair<GameAction, Pair<Float, Float>>> = listOf(
    GameAction.L2 to (10f to 2f), GameAction.L1 to (10f to 12f), GameAction.R2 to (90f to 2f), GameAction.R1 to (90f to 12f),
    GameAction.DPAD_UP to (22f to 30f), GameAction.DPAD_DOWN to (22f to 50f),
    GameAction.DPAD_LEFT to (12f to 40f), GameAction.DPAD_RIGHT to (32f to 40f),
    GameAction.BUTTON_Y to (78f to 22f), GameAction.BUTTON_A to (78f to 42f),
    GameAction.BUTTON_X to (68f to 32f), GameAction.BUTTON_B to (88f to 32f),
    GameAction.SELECT to (42f to 22f), GameAction.HOME to (50f to 30f), GameAction.START to (58f to 22f),
    GameAction.L3 to (38f to 50f), GameAction.R3 to (62f to 50f)
)

@Composable
private fun ControllerView(keys: Map<Int, GameAction>, onButton: (GameAction) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(28.dp))
    ) {
        val w = maxWidth
        val h = maxHeight
        PLACES.forEach { (action, at) ->
            val bound = keys.filterValues { it == action }.keys
            val size = 44.dp
            Box(
                modifier = Modifier
                    .offset(x = w * (at.first / 100f) - size / 2, y = h * (at.second / 60f) + 4.dp)
                    .size(size)
                    .background(
                        if (bound.isEmpty()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onButton(action) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(shortAction(action), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(bound.joinToString(" ") { capName(it) }.ifEmpty { "–" }, fontSize = 9.sp, maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The actions a key can take, grouped: controller, keyboard, mouse. */
@Composable
private fun ActionPicker(key: Int, current: GameAction?, onPick: (GameAction?) -> Unit, onDismiss: () -> Unit) {
    val groups = listOf(
        R.string.game_map_group_controller to GameAction.entries.filter { it.gamepad },
        R.string.game_map_group_keyboard to GameAction.entries.filter { !it.gamepad && it.keyCode > 0 },
        R.string.game_map_group_mouse to GameAction.entries.filter { it.keyCode < 0 }
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(capName(key)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                PickerRow(stringResource(R.string.game_mode_key_as_is), current == null) { onPick(null) }
                groups.forEach { (title, actions) ->
                    Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                    actions.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { action ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .background(
                                            if (action == current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { onPick(action) },
                                    contentAlignment = Alignment.Center
                                ) { Text(shortAction(action), fontSize = 12.sp, maxLines = 1) }
                            }
                            repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                        }
                        Box(Modifier.height(4.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)
    )
}

/** A controller button: press the key it goes on. */
@Composable
private fun KeyForButton(action: GameAction, onKey: (Int) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(action.label) },
        text = {
            Text(
                stringResource(R.string.game_map_press_for_button),
                modifier = Modifier
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) onKey(event.nativeKeyEvent.keyCode)
                        true
                    }
            )
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        dismissButton = { TextButton(onClick = onClear) { Text(stringResource(R.string.game_map_clear_button)) } }
    )
}
