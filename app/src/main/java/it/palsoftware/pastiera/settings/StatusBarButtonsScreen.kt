package it.palsoftware.pastiera.settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.areStatusBarVariationsEnabled
import it.palsoftware.pastiera.getAvailableStatusBarButtons
import it.palsoftware.pastiera.getDynamicVariationBarResizeToContent
import it.palsoftware.pastiera.getDynamicVariationBarSlotCount
import it.palsoftware.pastiera.getMenuBarButtons
import it.palsoftware.pastiera.getPastierinaStatusBarSlotsLeft
import it.palsoftware.pastiera.getPastierinaStatusBarSlotsRight
import it.palsoftware.pastiera.getStatusBarPresentationMode
import it.palsoftware.pastiera.getStatusBarSlotsLeft
import it.palsoftware.pastiera.getStatusBarSlotsRight
import it.palsoftware.pastiera.resetMenuBarButtons
import it.palsoftware.pastiera.resetPastierinaStatusBarSlotsToDefault
import it.palsoftware.pastiera.resetStatusBarSlotsToDefault
import it.palsoftware.pastiera.setDynamicVariationBarResizeToContent
import it.palsoftware.pastiera.setDynamicVariationBarSlotCount
import it.palsoftware.pastiera.setMenuBarButtons
import it.palsoftware.pastiera.setPastierinaStatusBarSlotsLeft
import it.palsoftware.pastiera.setPastierinaStatusBarSlotsRight
import it.palsoftware.pastiera.setStatusBarPresentationMode
import it.palsoftware.pastiera.setStatusBarSlotsLeft
import it.palsoftware.pastiera.setStatusBarSlotsRight
import it.palsoftware.pastiera.setStatusBarVariationsEnabled

private enum class StatusBarEditorMode { Extended, Pastierina }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusBarButtonsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onCustomizeVariations: () -> Unit,
    onOpenModifiers: () -> Unit
) {
    val context = LocalContext.current
    var leftSlots by remember { mutableStateOf(SettingsManager.getStatusBarSlotsLeft(context)) }
    var rightSlots by remember { mutableStateOf(SettingsManager.getStatusBarSlotsRight(context)) }
    var pastierinaLeftSlots by remember {
        mutableStateOf(SettingsManager.getPastierinaStatusBarSlotsLeft(context))
    }
    var pastierinaRightSlots by remember {
        mutableStateOf(SettingsManager.getPastierinaStatusBarSlotsRight(context))
    }
    var variationsVisible by remember {
        mutableStateOf(SettingsManager.areStatusBarVariationsEnabled(context))
    }
    var dynamicVariationSlotCount by remember {
        mutableStateOf(SettingsManager.getDynamicVariationBarSlotCount(context))
    }
    var dynamicVariationsResizeToContent by remember {
        mutableStateOf(SettingsManager.getDynamicVariationBarResizeToContent(context))
    }
    var editorMode by remember {
        mutableStateOf(
            if (
                SettingsManager.getStatusBarPresentationMode(context) ==
                    SettingsManager.StatusBarPresentationMode.PASTIERINA
            ) {
                StatusBarEditorMode.Pastierina
            } else {
                StatusBarEditorMode.Extended
            }
        )
    }

    fun selectExtendedButton(buttonId: String, targetSide: String, targetIndex: Int) {
        if (buttonId != SettingsManager.STATUS_BAR_BUTTON_NONE) {
            leftSlots = leftSlots.mapIndexed { index, current ->
                if (current == buttonId && !(targetSide == "left" && targetIndex == index)) {
                    SettingsManager.STATUS_BAR_BUTTON_NONE
                } else current
            }
            rightSlots = rightSlots.mapIndexed { index, current ->
                if (current == buttonId && !(targetSide == "right" && targetIndex == index)) {
                    SettingsManager.STATUS_BAR_BUTTON_NONE
                } else current
            }
        }
        if (targetSide == "left") {
            leftSlots = leftSlots.toMutableList().also { it[targetIndex] = buttonId }
        } else {
            rightSlots = rightSlots.toMutableList().also { it[targetIndex] = buttonId }
        }
        SettingsManager.setStatusBarSlotsLeft(context, leftSlots)
        SettingsManager.setStatusBarSlotsRight(context, rightSlots)
    }

    fun selectPastierinaButton(buttonId: String, targetSide: String, targetIndex: Int) {
        if (buttonId != SettingsManager.STATUS_BAR_BUTTON_NONE) {
            pastierinaLeftSlots = pastierinaLeftSlots.mapIndexed { index, current ->
                if (current == buttonId && !(targetSide == "left" && targetIndex == index)) {
                    SettingsManager.STATUS_BAR_BUTTON_NONE
                } else current
            }
            pastierinaRightSlots = pastierinaRightSlots.mapIndexed { index, current ->
                if (current == buttonId && !(targetSide == "right" && targetIndex == index)) {
                    SettingsManager.STATUS_BAR_BUTTON_NONE
                } else current
            }
        }
        if (targetSide == "left") {
            pastierinaLeftSlots = pastierinaLeftSlots.toMutableList().also { it[targetIndex] = buttonId }
        } else {
            pastierinaRightSlots = pastierinaRightSlots.toMutableList().also { it[targetIndex] = buttonId }
        }
        SettingsManager.setPastierinaStatusBarSlotsLeft(context, pastierinaLeftSlots)
        SettingsManager.setPastierinaStatusBarSlotsRight(context, pastierinaRightSlots)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
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
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back_content_description)
                    )
                }
                Text(
                    text = stringResource(R.string.status_bar_buttons_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                )
                IconButton(
                    onClick = {
                        val defaults = SettingsManager.resetStatusBarSlotsToDefault(context)
                        leftSlots = listOf(defaults.left)
                        rightSlots = listOf(defaults.right1, defaults.right2)
                        SettingsManager.resetPastierinaStatusBarSlotsToDefault(context)
                        pastierinaLeftSlots = SettingsManager.getPastierinaStatusBarSlotsLeft(context)
                        pastierinaRightSlots = SettingsManager.getPastierinaStatusBarSlotsRight(context)
                        variationsVisible = SettingsManager.areStatusBarVariationsEnabled(context)
                        dynamicVariationSlotCount = SettingsManager.getDynamicVariationBarSlotCount(context)
                        dynamicVariationsResizeToContent = SettingsManager.getDynamicVariationBarResizeToContent(context)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.status_bar_buttons_reset),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Surface(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.status_bar_buttons_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .settingRow(SettingLinkIds.MODIFIERS_INDICATORS, onOpenModifiers)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.modifier_keys_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.modifier_indicators_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.modifier_indicators_description),
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

        SettingsSectionDivider(stringResource(R.string.status_bar_style_section))
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth().settingRow("status_bar.presentation")
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            StatusBarEditorMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = editorMode == mode,
                    onClick = {
                        editorMode = mode
                        SettingsManager.setStatusBarPresentationMode(
                            context,
                            if (mode == StatusBarEditorMode.Pastierina) {
                                SettingsManager.StatusBarPresentationMode.PASTIERINA
                            } else {
                                SettingsManager.StatusBarPresentationMode.FULL_STATUS_BAR
                            }
                        )
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, StatusBarEditorMode.entries.size)
                ) {
                    Text(
                        text = stringResource(
                            if (mode == StatusBarEditorMode.Extended) {
                                R.string.extended_status_bar_title
                            } else {
                                R.string.pastierina_status_bar_buttons_title
                            }
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        StatusBarLayoutPreview(
            leftSlots = if (editorMode == StatusBarEditorMode.Extended) leftSlots else pastierinaLeftSlots,
            rightSlots = if (editorMode == StatusBarEditorMode.Extended) rightSlots else pastierinaRightSlots,
            centerText = if (editorMode == StatusBarEditorMode.Extended && variationsVisible) {
                "· · ·"
            } else if (editorMode == StatusBarEditorMode.Pastierina) {
                stringResource(R.string.pastierina_preview_suggestions)
            } else null
        )

        if (editorMode == StatusBarEditorMode.Extended) {
            SettingsSectionDivider(stringResource(R.string.extended_status_bar_features_section))
            Surface(modifier = Modifier.fillMaxWidth().settingRow("status_bar.variations_visible")) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.status_bar_variations_visible_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.status_bar_variations_visible_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = variationsVisible,
                        onCheckedChange = { visible ->
                            variationsVisible = visible
                            SettingsManager.setStatusBarVariationsEnabled(context, visible)
                        }
                    )
                }
            }

            Surface(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.status_bar_swipe_cursor_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (variationsVisible) {
                Surface(modifier = Modifier.fillMaxWidth().settingRow("status_bar.variation_slots")) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dynamic_variation_slot_count_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(
                                R.string.dynamic_variation_slot_count_description,
                                dynamicVariationSlotCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = dynamicVariationSlotCount.toFloat(),
                            onValueChange = { value ->
                                dynamicVariationSlotCount = value.toInt().coerceIn(
                                    SettingsManager.MIN_DYNAMIC_VARIATION_BAR_SLOT_COUNT,
                                    SettingsManager.MAX_DYNAMIC_VARIATION_BAR_SLOT_COUNT
                                )
                            },
                            onValueChangeFinished = {
                                SettingsManager.setDynamicVariationBarSlotCount(context, dynamicVariationSlotCount)
                            },
                            valueRange = SettingsManager.MIN_DYNAMIC_VARIATION_BAR_SLOT_COUNT.toFloat()..
                                SettingsManager.MAX_DYNAMIC_VARIATION_BAR_SLOT_COUNT.toFloat(),
                            steps = SettingsManager.MAX_DYNAMIC_VARIATION_BAR_SLOT_COUNT -
                                SettingsManager.MIN_DYNAMIC_VARIATION_BAR_SLOT_COUNT - 1
                        )
                    }
                }

                Surface(modifier = Modifier.fillMaxWidth().settingRow("status_bar.resize_variations")) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.dynamic_variation_resize_to_content_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.dynamic_variation_resize_to_content_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = dynamicVariationsResizeToContent,
                            onCheckedChange = { enabled ->
                                dynamicVariationsResizeToContent = enabled
                                SettingsManager.setDynamicVariationBarResizeToContent(context, enabled)
                            }
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().settingRow("customization.variations", onCustomizeVariations)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.variation_customize_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SettingsSectionDivider(stringResource(R.string.status_bar_buttons_section))
            SlotGroup(
                title = stringResource(R.string.status_bar_slots_left),
                linkId = "status_bar.extended_left",
                slots = leftSlots,
                slotPrefix = "L",
                onSlotSelected = { index, buttonId -> selectExtendedButton(buttonId, "left", index) },
                onAddSlot = {
                    leftSlots = leftSlots + SettingsManager.STATUS_BAR_BUTTON_NONE
                    SettingsManager.setStatusBarSlotsLeft(context, leftSlots)
                },
                onRemoveSlot = { index ->
                    leftSlots = leftSlots.toMutableList().also { it.removeAt(index) }
                    SettingsManager.setStatusBarSlotsLeft(context, leftSlots)
                }
            )
            SlotGroup(
                title = stringResource(R.string.status_bar_slots_right),
                linkId = "status_bar.extended_right",
                slots = rightSlots,
                slotPrefix = "R",
                onSlotSelected = { index, buttonId -> selectExtendedButton(buttonId, "right", index) },
                onAddSlot = {
                    rightSlots = rightSlots + SettingsManager.STATUS_BAR_BUTTON_NONE
                    SettingsManager.setStatusBarSlotsRight(context, rightSlots)
                },
                onRemoveSlot = { index ->
                    rightSlots = rightSlots.toMutableList().also { it.removeAt(index) }
                    SettingsManager.setStatusBarSlotsRight(context, rightSlots)
                }
            )
        } else {
            SettingsSectionDivider(stringResource(R.string.pastierina_status_bar_buttons_title))
            Text(
                text = stringResource(R.string.pastierina_status_bar_buttons_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            SettingsSectionDivider(stringResource(R.string.status_bar_buttons_section))
            SlotGroup(
                title = stringResource(R.string.status_bar_slots_left),
                linkId = "status_bar.pastierina_left",
                slots = pastierinaLeftSlots,
                slotPrefix = "L",
                onSlotSelected = { index, buttonId -> selectPastierinaButton(buttonId, "left", index) },
                onAddSlot = {
                    pastierinaLeftSlots = pastierinaLeftSlots + SettingsManager.STATUS_BAR_BUTTON_NONE
                    SettingsManager.setPastierinaStatusBarSlotsLeft(context, pastierinaLeftSlots)
                },
                onRemoveSlot = { index ->
                    pastierinaLeftSlots = pastierinaLeftSlots.toMutableList().also { it.removeAt(index) }
                    SettingsManager.setPastierinaStatusBarSlotsLeft(context, pastierinaLeftSlots)
                }
            )
            SlotGroup(
                title = stringResource(R.string.status_bar_slots_right),
                linkId = "status_bar.pastierina_right",
                slots = pastierinaRightSlots,
                slotPrefix = "R",
                onSlotSelected = { index, buttonId -> selectPastierinaButton(buttonId, "right", index) },
                onAddSlot = {
                    pastierinaRightSlots = pastierinaRightSlots + SettingsManager.STATUS_BAR_BUTTON_NONE
                    SettingsManager.setPastierinaStatusBarSlotsRight(context, pastierinaRightSlots)
                },
                onRemoveSlot = { index ->
                    pastierinaRightSlots = pastierinaRightSlots.toMutableList().also { it.removeAt(index) }
                    SettingsManager.setPastierinaStatusBarSlotsRight(context, pastierinaRightSlots)
                }
            )
        }

        SettingsSectionDivider(stringResource(R.string.menu_bar_section))
        MenuBarEditor()

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** The menu bar (opened with ☰): which buttons it shows and in what order. */
@Composable
private fun MenuBarEditor() {
    val context = LocalContext.current
    var shown by remember { mutableStateOf(SettingsManager.getMenuBarButtons(context)) }
    val hidden = SettingsManager.MENU_BAR_BUTTON_OPTIONS.filter { it !in shown }
    fun save(buttons: List<String>) {
        shown = buttons
        SettingsManager.setMenuBarButtons(context, buttons)
    }
    Text(
        text = stringResource(R.string.menu_bar_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).settingRow("status_bar.menu_bar")
    )
    // The menu bar as it will look: close first, then your buttons, sharing the width equally
    // like the real bar
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        @Composable
        fun RowScope.PreviewKey(content: @Composable () -> Unit) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    // The bar's height stays put: fewer buttons make each one wider, not taller
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) { content() }
        }
        PreviewKey {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        shown.forEach { buttonId ->
            PreviewKey {
                Icon(
                    painter = painterResource(getButtonIconRes(buttonId)),
                    contentDescription = getButtonDisplayName(buttonId),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
    (shown + hidden).forEach { buttonId ->
        val index = shown.indexOf(buttonId)
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val on = index >= 0
            // Explicit colours: these rows aren't inside a Surface, so nothing else sets them
            val textColor = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            Icon(
                painter = painterResource(getButtonIconRes(buttonId)),
                contentDescription = null,
                tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = getButtonDisplayName(buttonId),
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
            if (on) {
                val arrowColors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                )
                IconButton(
                    onClick = { save(shown.toMutableList().also { it.add(index - 1, it.removeAt(index)) }) },
                    enabled = index > 0,
                    colors = arrowColors
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.menu_bar_move_up))
                }
                IconButton(
                    onClick = { save(shown.toMutableList().also { it.add(index + 1, it.removeAt(index)) }) },
                    enabled = index < shown.size - 1,
                    colors = arrowColors
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.menu_bar_move_down))
                }
            }
            Switch(
                checked = index >= 0,
                onCheckedChange = { on -> save(if (on) shown + buttonId else shown - buttonId) }
            )
        }
    }
    TextButton(
        onClick = {
            SettingsManager.resetMenuBarButtons(context)
            shown = SettingsManager.getMenuBarButtons(context)
        },
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Text(stringResource(R.string.menu_bar_reset))
    }
}
@Composable
private fun StatusBarLayoutPreview(
    leftSlots: List<String>,
    rightSlots: List<String>,
    centerText: String?
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                leftSlots.forEach { SlotPreview(buttonId = it, label = "") }
            }
            if (centerText != null) {
                Surface(
                    modifier = Modifier.weight(1f).height(32.dp).padding(horizontal = 8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = centerText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rightSlots.forEach { SlotPreview(buttonId = it, label = "") }
            }
        }
    }
}

@Composable
private fun SlotGroup(
    linkId: String,
    title: String,
    slots: List<String>,
    slotPrefix: String,
    onSlotSelected: (Int, String) -> Unit,
    onAddSlot: () -> Unit,
    onRemoveSlot: (Int) -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth().settingRow(linkId)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = onAddSlot) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.status_bar_slot_add)
                    )
                }
            }

            slots.forEachIndexed { index, buttonId ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SlotDropdown(
                        slotLabel = title,
                        slotNumber = "$slotPrefix${index + 1}",
                        selectedButton = buttonId,
                        excludedButtons = emptyList(),
                        modifier = Modifier.weight(1f),
                        onButtonSelected = { selected -> onSlotSelected(index, selected) }
                    )
                    IconButton(
                        onClick = { onRemoveSlot(index) },
                        enabled = slots.size > 1
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.status_bar_slot_remove),
                            tint = if (slots.size > 1) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModifierIndicatorMultiSelect(
    modifier: Modifier = Modifier,
    selectedIndicators: Set<String>,
    onIndicatorsSelected: (Set<String>) -> Unit
) {
    val options = listOf(
        SettingsManager.MODIFIER_INDICATOR_BOTTOM_STRIP,
        SettingsManager.MODIFIER_INDICATOR_MENU_BAR,
        SettingsManager.MODIFIER_INDICATOR_STATUS_BAR
    )
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { indicator ->
                val selected = indicator in selectedIndicators
                val linkId = when (indicator) {
                    SettingsManager.MODIFIER_INDICATOR_BOTTOM_STRIP ->
                        SettingLinkIds.MODIFIERS_INDICATOR_BOTTOM_STRIP
                    SettingsManager.MODIFIER_INDICATOR_MENU_BAR ->
                        SettingLinkIds.MODIFIERS_INDICATOR_MENU_BAR
                    SettingsManager.MODIFIER_INDICATOR_STATUS_BAR ->
                        SettingLinkIds.MODIFIERS_INDICATOR_STATUS_BAR
                    else -> null
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .settingRow(linkId) {
                            onIndicatorsSelected(
                                if (selected) selectedIndicators - indicator else selectedIndicators + indicator
                            )
                        },
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = getModifierIndicatorLabel(indicator),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        if (selectedIndicators.isEmpty()) {
            Text(
                text = stringResource(R.string.modifier_indicators_off_state),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun getModifierIndicatorLabel(indicator: String): String {
    return when (indicator) {
        SettingsManager.MODIFIER_INDICATOR_BOTTOM_STRIP -> stringResource(R.string.modifier_indicators_bottom_strip)
        SettingsManager.MODIFIER_INDICATOR_MENU_BAR -> stringResource(R.string.modifier_indicators_menu_bar)
        SettingsManager.MODIFIER_INDICATOR_STATUS_BAR -> stringResource(R.string.modifier_indicators_status_bar)
        else -> indicator
    }
}

@Composable
private fun SlotPreview(
    buttonId: String,
    label: String
) {
    Surface(
        modifier = Modifier.size(32.dp),
        color = if (buttonId == SettingsManager.STATUS_BAR_BUTTON_NONE)
            MaterialTheme.colorScheme.surface
        else
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (buttonId == SettingsManager.STATUS_BAR_BUTTON_NONE) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    painter = painterResource(id = getButtonIconRes(buttonId)),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotDropdown(
    slotLabel: String,
    slotNumber: String,
    selectedButton: String,
    excludedButtons: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    onButtonSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // Filter out buttons that are already used in other slots (but always keep "none" available)
    val availableButtons = SettingsManager.getAvailableStatusBarButtons()
        .filter { it == SettingsManager.STATUS_BAR_BUTTON_NONE || it !in excludedButtons }

    Surface(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "$slotLabel ($slotNumber)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = getButtonDisplayName(selectedButton),
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = {
                        if (selectedButton == SettingsManager.STATUS_BAR_BUTTON_NONE) {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = getButtonIconRes(selectedButton)),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    availableButtons.forEach { buttonId ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (buttonId == SettingsManager.STATUS_BAR_BUTTON_NONE) {
                                        Box(
                                            modifier = Modifier.size(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "—",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        Icon(
                                            painter = painterResource(id = getButtonIconRes(buttonId)),
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = getButtonDisplayName(buttonId),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = getButtonDescription(buttonId),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onButtonSelected(buttonId)
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun getButtonDisplayName(buttonId: String): String {
    return when (buttonId) {
        SettingsManager.STATUS_BAR_BUTTON_NONE -> stringResource(R.string.status_bar_button_none)
        SettingsManager.STATUS_BAR_BUTTON_CLIPBOARD -> stringResource(R.string.status_bar_button_clipboard)
        SettingsManager.STATUS_BAR_BUTTON_MICROPHONE -> stringResource(R.string.status_bar_button_microphone)
        SettingsManager.STATUS_BAR_BUTTON_EMOJI -> stringResource(R.string.status_bar_button_emoji)
        SettingsManager.STATUS_BAR_BUTTON_GIF -> stringResource(R.string.status_bar_button_gif)
        SettingsManager.STATUS_BAR_BUTTON_LANGUAGE -> stringResource(R.string.status_bar_button_language)
        SettingsManager.STATUS_BAR_BUTTON_HAMBURGER -> stringResource(R.string.status_bar_button_hamburger)
        SettingsManager.STATUS_BAR_BUTTON_MINIMAL_UI -> stringResource(R.string.status_bar_button_minimal_ui)
        SettingsManager.STATUS_BAR_BUTTON_SOFTWARE_KEYBOARD_MODE -> stringResource(R.string.status_bar_button_software_keyboard_mode)
        SettingsManager.STATUS_BAR_BUTTON_SETTINGS -> stringResource(R.string.status_bar_button_settings)
        SettingsManager.STATUS_BAR_BUTTON_SYMBOLS -> stringResource(R.string.status_bar_button_symbols)
        SettingsManager.STATUS_BAR_BUTTON_UNDO -> stringResource(R.string.status_bar_button_undo)
        SettingsManager.STATUS_BAR_BUTTON_REDO -> stringResource(R.string.status_bar_button_redo)
        else -> buttonId
    }
}

@Composable
private fun getButtonDescription(buttonId: String): String {
    return when (buttonId) {
        SettingsManager.STATUS_BAR_BUTTON_NONE -> stringResource(R.string.status_bar_button_none_description)
        SettingsManager.STATUS_BAR_BUTTON_CLIPBOARD -> stringResource(R.string.status_bar_button_clipboard_description)
        SettingsManager.STATUS_BAR_BUTTON_MICROPHONE -> stringResource(R.string.status_bar_button_microphone_description)
        SettingsManager.STATUS_BAR_BUTTON_EMOJI -> stringResource(R.string.status_bar_button_emoji_description)
        SettingsManager.STATUS_BAR_BUTTON_GIF -> stringResource(R.string.status_bar_button_gif_description)
        SettingsManager.STATUS_BAR_BUTTON_LANGUAGE -> stringResource(R.string.status_bar_button_language_description)
        SettingsManager.STATUS_BAR_BUTTON_HAMBURGER -> stringResource(R.string.status_bar_button_hamburger_description)
        SettingsManager.STATUS_BAR_BUTTON_MINIMAL_UI -> stringResource(R.string.status_bar_button_minimal_ui_description)
        SettingsManager.STATUS_BAR_BUTTON_SOFTWARE_KEYBOARD_MODE -> stringResource(R.string.status_bar_button_software_keyboard_mode_description)
        SettingsManager.STATUS_BAR_BUTTON_SETTINGS -> stringResource(R.string.status_bar_button_settings_description)
        SettingsManager.STATUS_BAR_BUTTON_SYMBOLS -> stringResource(R.string.status_bar_button_symbols_description)
        SettingsManager.STATUS_BAR_BUTTON_UNDO -> stringResource(R.string.status_bar_button_undo_description)
        SettingsManager.STATUS_BAR_BUTTON_REDO -> stringResource(R.string.status_bar_button_redo_description)
        else -> ""
    }
}

/**
 * Returns the drawable resource ID for the button icon.
 * Note: STATUS_BAR_BUTTON_NONE should be handled separately (no icon).
 */
private fun getButtonIconRes(buttonId: String): Int {
    return when (buttonId) {
        SettingsManager.STATUS_BAR_BUTTON_CLIPBOARD -> R.drawable.ic_content_paste_24
        SettingsManager.STATUS_BAR_BUTTON_MICROPHONE -> R.drawable.ic_baseline_mic_24
        SettingsManager.STATUS_BAR_BUTTON_EMOJI -> R.drawable.ic_emoji_emotions_24
        SettingsManager.STATUS_BAR_BUTTON_GIF -> R.drawable.ic_gif_24
        SettingsManager.STATUS_BAR_BUTTON_LANGUAGE -> R.drawable.ic_globe_24
        SettingsManager.STATUS_BAR_BUTTON_HAMBURGER -> R.drawable.ic_menu_24
        SettingsManager.STATUS_BAR_BUTTON_MINIMAL_UI -> R.drawable.ic_minimal_ui_24
        SettingsManager.STATUS_BAR_BUTTON_SOFTWARE_KEYBOARD_MODE -> R.drawable.expansion_panels_24
        SettingsManager.STATUS_BAR_BUTTON_SETTINGS -> R.drawable.ic_settings_24
        SettingsManager.STATUS_BAR_BUTTON_SYMBOLS -> R.drawable.ic_emoji_symbols_24
        SettingsManager.STATUS_BAR_BUTTON_UNDO -> R.drawable.ic_undo_24
        SettingsManager.STATUS_BAR_BUTTON_REDO -> R.drawable.ic_redo_24
        else -> R.drawable.ic_settings_24 // Fallback
    }
}
