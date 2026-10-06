package it.palsoftware.pastiera.settings
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.core.Punctuation
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.foldCommaSpaceIntoPunctuationSpacing
import it.palsoftware.pastiera.getAutoCapAfterEmoticon
import it.palsoftware.pastiera.getAutoCapitalizeAfterPeriod
import it.palsoftware.pastiera.getAutoCapitalizeFirstLetter
import it.palsoftware.pastiera.getAutoCapitalizeRespectManualShiftOff
import it.palsoftware.pastiera.getAutoCapitalizeRestrictedFields
import it.palsoftware.pastiera.getAutoSpacePunctuation
import it.palsoftware.pastiera.getBackspaceAtStartDelete
import it.palsoftware.pastiera.getDoubleSpaceToPeriod
import it.palsoftware.pastiera.getEmoticonPunctuation
import it.palsoftware.pastiera.getFrenchPunctuationOnlyFrenchLayouts
import it.palsoftware.pastiera.getFrenchPunctuationSpacing
import it.palsoftware.pastiera.getMidWordQuoteToApostrophe
import it.palsoftware.pastiera.getSmartQuotes
import it.palsoftware.pastiera.getSmartQuotesStyle
import it.palsoftware.pastiera.getSpaceAfterPunctuation
import it.palsoftware.pastiera.getSpacedHyphenDashStyle
import it.palsoftware.pastiera.getSpacedHyphenToEnDash
import it.palsoftware.pastiera.setAutoCapAfterEmoticon
import it.palsoftware.pastiera.setAutoCapitalizeAfterPeriod
import it.palsoftware.pastiera.setAutoCapitalizeFirstLetter
import it.palsoftware.pastiera.setAutoCapitalizeRespectManualShiftOff
import it.palsoftware.pastiera.setAutoSpacePunctuation
import it.palsoftware.pastiera.setBackspaceAtStartDelete
import it.palsoftware.pastiera.setDoubleSpaceToPeriod
import it.palsoftware.pastiera.setEmoticonPunctuation
import it.palsoftware.pastiera.setFrenchPunctuationOnlyFrenchLayouts
import it.palsoftware.pastiera.setFrenchPunctuationSpacing
import it.palsoftware.pastiera.setMidWordQuoteToApostrophe
import it.palsoftware.pastiera.setSmartQuotes
import it.palsoftware.pastiera.setSmartQuotesStyle
import it.palsoftware.pastiera.setSpaceAfterPunctuation
import it.palsoftware.pastiera.setSpacedHyphenDashStyle
import it.palsoftware.pastiera.setSpacedHyphenToEnDash

/** The two Typing screens built from these rows. */
enum class TextInputPage {
    CapitalisationPunctuation,
    EditingKeys
}

/**
 * Typing settings: capitalisation and punctuation, or the editing keys
 * (Backspace combinations and Enter per app).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    page: TextInputPage = TextInputPage.CapitalisationPunctuation,
    onNavModeSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var showTextExpansion by remember { mutableStateOf(settingsChild(context, "text") == "expansion") }
    val linkedSetting = LocalSettingHighlightId.current
    LaunchedEffect(linkedSetting) {
        if (linkedSetting != null) {
            showTextExpansion = linkedSetting.startsWith("text_expansion.")
        }
    }

    var autoCapitalizeFirstLetter by remember {
        mutableStateOf(SettingsManager.getAutoCapitalizeFirstLetter(context))
    }

    var autoCapitalizeAfterPeriod by remember {
        mutableStateOf(SettingsManager.getAutoCapitalizeAfterPeriod(context))
    }

    var autoCapitalizeRespectManualShiftOff by remember {
        mutableStateOf(SettingsManager.getAutoCapitalizeRespectManualShiftOff(context))
    }

    var autoCapitalizeRestrictedFields by remember {
        mutableStateOf(SettingsManager.getAutoCapitalizeRestrictedFields(context))
    }

    var doubleSpaceToPeriod by remember {
        mutableStateOf(SettingsManager.getDoubleSpaceToPeriod(context))
    }

    var spacedHyphenToEnDash by remember {
        mutableStateOf(SettingsManager.getSpacedHyphenToEnDash(context))
    }

    var spacedHyphenDashStyle by remember {
        mutableStateOf(SettingsManager.getSpacedHyphenDashStyle(context))
    }

    var spacedHyphenDashExpanded by remember { mutableStateOf(false) }

    var midWordQuoteToApostrophe by remember {
        mutableStateOf(SettingsManager.getMidWordQuoteToApostrophe(context))
    }

    var frenchPunctuationSpacing by remember {
        mutableStateOf(SettingsManager.getFrenchPunctuationSpacing(context))
    }

    var frenchPunctuationOnlyFrenchLayouts by remember {
        mutableStateOf(SettingsManager.getFrenchPunctuationOnlyFrenchLayouts(context))
    }

    // Before the rows below read it: an old Space after comma setting moves into punctuation spacing
    remember { SettingsManager.foldCommaSpaceIntoPunctuationSpacing(context); true }

    var autoSpacePunctuation by remember {
        mutableStateOf(SettingsManager.getAutoSpacePunctuation(context))
    }
    var emoticonPunctuation by remember {
        mutableStateOf(SettingsManager.getEmoticonPunctuation(context))
    }
    var spaceAfterPunctuation by remember {
        mutableStateOf(SettingsManager.getSpaceAfterPunctuation(context))
    }

    var autoSpacePunctuationDialogVisible by remember { mutableStateOf(false) }
    var autoSpacePunctuationHelpVisible by remember { mutableStateOf(false) }

    var smartQuotes by remember {
        mutableStateOf(SettingsManager.getSmartQuotes(context))
    }

    var smartQuotesStyle by remember {
        mutableStateOf(SettingsManager.getSmartQuotesStyle(context))
    }

    var smartQuotesExpanded by remember { mutableStateOf(false) }

    var backspaceAtStartDelete by remember {
        mutableStateOf(SettingsManager.getBackspaceAtStartDelete(context))
    }

    // Handle system back button


    if (showTextExpansion) {
        TextExpansionSettingsScreen(onBack = { context.settingsActivity().finish() })
        return
    }

    if (autoSpacePunctuationDialogVisible) {
        AlertDialog(
            onDismissRequest = { autoSpacePunctuationDialogVisible = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.auto_space_punctuation_title),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { autoSpacePunctuationHelpVisible = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = stringResource(R.string.auto_space_punctuation_help_action)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.auto_space_punctuation_dialog_instruction),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.auto_space_punctuation_character_column),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.auto_space_punctuation_before_column),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.width(80.dp)
                        )
                        Text(
                            text = stringResource(R.string.auto_space_punctuation_after_column),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.width(80.dp)
                        )
                    }
                    Column {
                        autoSpacePunctuationOptions().forEach { punctuation ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = autoSpacePunctuationLabel(punctuation),
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.width(28.dp)
                                    )
                                    autoSpacePunctuationName(punctuation)?.let { name ->
                                        Text(
                                            text = stringResource(name),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Checkbox(
                                    checked = punctuation in autoSpacePunctuation,
                                    onCheckedChange = { checked ->
                                        autoSpacePunctuation = if (checked) {
                                            addAutoSpacePunctuation(autoSpacePunctuation, punctuation)
                                        } else {
                                            autoSpacePunctuation.filterNot { it == punctuation }
                                        }
                                        SettingsManager.setAutoSpacePunctuation(context, autoSpacePunctuation)
                                    },
                                    modifier = Modifier.width(80.dp)
                                )
                                Checkbox(
                                    checked = punctuation in spaceAfterPunctuation,
                                    onCheckedChange = { checked ->
                                        spaceAfterPunctuation = if (checked) {
                                            addAutoSpacePunctuation(spaceAfterPunctuation, punctuation)
                                        } else {
                                            spaceAfterPunctuation.filterNot { it == punctuation }
                                        }
                                        SettingsManager.setSpaceAfterPunctuation(context, spaceAfterPunctuation)
                                    },
                                    modifier = Modifier.width(80.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { autoSpacePunctuationDialogVisible = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        autoSpacePunctuation = Punctuation.DEFAULT_AUTO_SPACE
                        spaceAfterPunctuation = ""
                        SettingsManager.setAutoSpacePunctuation(context, autoSpacePunctuation)
                        SettingsManager.setSpaceAfterPunctuation(context, spaceAfterPunctuation)
                    }
                ) {
                    Text(stringResource(R.string.auto_space_punctuation_reset))
                }
            }
        )
    }
    if (autoSpacePunctuationHelpVisible) {
        AlertDialog(
            onDismissRequest = { autoSpacePunctuationHelpVisible = false },
            title = { Text(stringResource(R.string.auto_space_punctuation_help_title)) },
            text = { Text(stringResource(R.string.auto_space_punctuation_help_text)) },
            confirmButton = {
                TextButton(onClick = { autoSpacePunctuationHelpVisible = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

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
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = stringResource(
                            when (page) {
                                TextInputPage.CapitalisationPunctuation -> R.string.settings_capitalisation_punctuation_title
                                TextInputPage.EditingKeys -> R.string.settings_editing_keys_title
                            }
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            if (page == TextInputPage.CapitalisationPunctuation) {
            SettingsSectionHeader(text = stringResource(R.string.text_input_section_capitalization))
            SettingsSwitchRow(
                title = stringResource(R.string.auto_capitalize_title),
                description = stringResource(R.string.auto_capitalize_description),
                checked = autoCapitalizeFirstLetter,
                linkId = SettingLinkIds.TEXT_INPUT_AUTO_CAPITALIZE,
                onCheckedChange = { enabled ->
                    autoCapitalizeFirstLetter = enabled
                    SettingsManager.setAutoCapitalizeFirstLetter(context, enabled)
                }
            )
            AnimatedVisibility(visible = autoCapitalizeFirstLetter) {
                Column {
                    SettingsSwitchRow(
                        title = stringResource(R.string.auto_capitalize_respect_manual_shift_off_title),
                        description = stringResource(R.string.auto_capitalize_respect_manual_shift_off_description),
                        checked = autoCapitalizeRespectManualShiftOff,
                        inset = 52.dp,
                        linkId = SettingLinkIds.TEXT_INPUT_AUTO_CAPITALIZE_RESPECT_MANUAL_SHIFT_OFF,
                        onCheckedChange = { enabled ->
                            autoCapitalizeRespectManualShiftOff = enabled
                            SettingsManager.setAutoCapitalizeRespectManualShiftOff(context, enabled)
                        }
                    )
                    AutoShiftFieldTypesRow()
                }
            }
            // Where a new sentence starts: after a full stop, after an emoticon
            FluxCheckTable(
                linkId = SettingLinkIds.TEXT_INPUT_AUTO_CAPITALIZE_AFTER_PERIOD,
                title = stringResource(R.string.capital_after_title),
                description = stringResource(R.string.capital_after_description),
                columns = listOf(stringResource(R.string.capital_after_full_stop), stringResource(R.string.capital_after_emoticon)),
                rows = listOf(
                    "" to listOf(
                        CheckCell({ SettingsManager.getAutoCapitalizeAfterPeriod(context) }) { SettingsManager.setAutoCapitalizeAfterPeriod(context, it) },
                        CheckCell({ SettingsManager.getAutoCapAfterEmoticon(context) }, SettingLinkIds.TEXT_INPUT_AUTO_CAP_AFTER_EMOTICON) { SettingsManager.setAutoCapAfterEmoticon(context, it) }
                    )
                )
            )

            SettingsSectionHeader(text = stringResource(R.string.text_input_section_spacing_punctuation))
            SettingsSwitchRow(
                title = stringResource(R.string.double_space_to_period_title),
                description = stringResource(R.string.double_space_to_period_description),
                checked = doubleSpaceToPeriod,
                linkId = SettingLinkIds.TEXT_INPUT_DOUBLE_SPACE_TO_PERIOD,
                onCheckedChange = { enabled ->
                    doubleSpaceToPeriod = enabled
                    SettingsManager.setDoubleSpaceToPeriod(context, enabled)
                }
            )
            SettingsNavigationRow(
                title = stringResource(R.string.auto_space_punctuation_title),
                description = autoSpacePunctuationSummary(
                    beforePunctuation = autoSpacePunctuation,
                    afterPunctuation = spaceAfterPunctuation,
                    beforeLabel = stringResource(R.string.auto_space_punctuation_before_column),
                    afterLabel = stringResource(R.string.auto_space_punctuation_after_column),
                    offLabel = stringResource(R.string.auto_space_punctuation_choose_off)
                ),
                linkId = SettingLinkIds.TEXT_INPUT_AUTO_SPACE_PUNCTUATION,
                onClick = { autoSpacePunctuationDialogVisible = true }
            )
            AnimatedVisibility(visible = spaceAfterPunctuation.any { it != '.' }) {
                SettingsSwitchRow(
                    title = stringResource(R.string.emoticon_punctuation_title),
                    description = stringResource(R.string.emoticon_punctuation_description),
                    checked = emoticonPunctuation,
                    linkId = SettingLinkIds.TEXT_INPUT_EMOTICON_PUNCTUATION,
                    onCheckedChange = { enabled ->
                        emoticonPunctuation = enabled
                        SettingsManager.setEmoticonPunctuation(context, enabled)
                    }
                )
            }
            SettingsSwitchRow(
                title = stringResource(R.string.french_punctuation_spacing_title),
                description = stringResource(R.string.french_punctuation_spacing_description),
                checked = frenchPunctuationSpacing,
                linkId = SettingLinkIds.TEXT_INPUT_FRENCH_PUNCTUATION_SPACING,
                onCheckedChange = { enabled ->
                    frenchPunctuationSpacing = enabled
                    SettingsManager.setFrenchPunctuationSpacing(context, enabled)
                }
            )
            AnimatedVisibility(visible = frenchPunctuationSpacing) {
                SettingsSwitchRow(
                    title = stringResource(R.string.french_punctuation_only_french_title),
                    description = stringResource(R.string.french_punctuation_only_french_description),
                    checked = frenchPunctuationOnlyFrenchLayouts,
                    inset = 52.dp,
                    linkId = SettingLinkIds.TEXT_INPUT_FRENCH_PUNCTUATION_ONLY_FRENCH,
                    onCheckedChange = { enabled ->
                        frenchPunctuationOnlyFrenchLayouts = enabled
                        SettingsManager.setFrenchPunctuationOnlyFrenchLayouts(context, enabled)
                    }
                )
            }

            SettingsSectionHeader(text = stringResource(R.string.text_input_section_typography))
            SettingsDropdownSwitchRow(
                title = stringResource(R.string.spaced_hyphen_to_en_dash_title),
                checked = spacedHyphenToEnDash,
                linkId = SettingLinkIds.TEXT_INPUT_SPACED_HYPHEN_TO_EN_DASH,
                onCheckedChange = { enabled ->
                    spacedHyphenToEnDash = enabled
                    SettingsManager.setSpacedHyphenToEnDash(context, enabled)
                    if (!enabled) {
                        spacedHyphenDashExpanded = false
                    }
                },
                expanded = spacedHyphenDashExpanded,
                onExpandedChange = { spacedHyphenDashExpanded = it },
                value = dashStyleLabel(spacedHyphenDashStyle),
                options = dashStyleOptions(),
                optionLabel = ::dashStyleLabel,
                onOptionSelected = { style ->
                    spacedHyphenDashStyle = style
                    SettingsManager.setSpacedHyphenDashStyle(context, style)
                    spacedHyphenDashExpanded = false
                }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.mid_word_quote_to_apostrophe_title),
                description = stringResource(R.string.mid_word_quote_to_apostrophe_description),
                checked = midWordQuoteToApostrophe,
                linkId = SettingLinkIds.TEXT_INPUT_MID_WORD_QUOTE_TO_APOSTROPHE,
                onCheckedChange = { enabled ->
                    midWordQuoteToApostrophe = enabled
                    SettingsManager.setMidWordQuoteToApostrophe(context, enabled)
                }
            )
            SettingsDropdownSwitchRow(
                title = stringResource(R.string.smart_quotes_title),
                checked = smartQuotes,
                linkId = SettingLinkIds.TEXT_INPUT_SMART_QUOTES,
                onCheckedChange = { enabled ->
                    smartQuotes = enabled
                    SettingsManager.setSmartQuotes(context, enabled)
                    if (!enabled) {
                        smartQuotesExpanded = false
                    }
                },
                expanded = smartQuotesExpanded,
                onExpandedChange = { smartQuotesExpanded = it },
                value = smartQuotesStyleLabel(smartQuotesStyle),
                options = smartQuoteStyleOptions(),
                optionLabel = ::smartQuotesStyleLabel,
                onOptionSelected = { style ->
                    smartQuotesStyle = style
                    SettingsManager.setSmartQuotesStyle(context, style)
                    smartQuotesExpanded = false
                }
            )

            }

            if (page == TextInputPage.EditingKeys) {
            SettingsSectionHeader(text = stringResource(R.string.text_input_section_delete))
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.delete_alternatives_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingRow(SettingLinkIds.TEXT_INPUT_BACKSPACE_AT_START_DELETE),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.backspace_at_start_delete_title),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = backspaceAtStartDelete,
                            onCheckedChange = { enabled ->
                                backspaceAtStartDelete = enabled
                                SettingsManager.setBackspaceAtStartDelete(context, enabled)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.delete_alternatives_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingsNavigationRow(
                        title = stringResource(R.string.delete_alternatives_nav_mode_title),
                        description = stringResource(R.string.settings_nav_mode_configure),
                        iconInset = 0.dp,
                        linkId = SettingLinkIds.TEXT_INPUT_DELETE_NAV_MODE,
                        onClick = onNavModeSettingsClick
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.delete_alternatives_selection_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun SettingsRowFrame(
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 64.dp,
    inset: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = inset, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    inset: androidx.compose.ui.unit.Dp = 16.dp,
    onCheckedChange: (Boolean) -> Unit,
    linkId: String? = null
) {
    SettingsRowFrame(
        modifier = Modifier.settingRow(linkId),
        inset = inset,
        minHeight = if (description == null) 64.dp else 72.dp
    ) {
        Icon(
            imageVector = Icons.Filled.TextFields,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    description: String,
    iconInset: androidx.compose.ui.unit.Dp = 16.dp,
    linkId: String? = null,
    onClick: () -> Unit
) {
    SettingsRowFrame(
        modifier = Modifier.settingRow(linkId, onClick),
        minHeight = 72.dp,
        inset = iconInset
    ) {
        Icon(
            imageVector = Icons.Filled.TextFields,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SettingsDropdownSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    value: String,
    options: List<String>,
    optionLabel: (String) -> String,
    onOptionSelected: (String) -> Unit,
    linkId: String? = null
) {
    SettingsRowFrame(
        modifier = Modifier.settingRow(linkId),
        minHeight = 88.dp
    ) {
        Icon(
            imageVector = Icons.Filled.TextFields,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { onExpandedChange(!expanded) }
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    enabled = checked,
                    textStyle = MaterialTheme.typography.bodySmall,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .menuAnchor(
                            type = MenuAnchorType.PrimaryNotEditable,
                            enabled = checked
                        )
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(optionLabel(option)) },
                            onClick = { onOptionSelected(option) },
                            enabled = checked
                        )
                    }
                }
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private fun dashStyleOptions(): List<String> {
    return listOf(
        SettingsManager.DASH_STYLE_EN,
        SettingsManager.DASH_STYLE_EM
    )
}

private fun dashStyleLabel(style: String): String {
    return when (style) {
        SettingsManager.DASH_STYLE_EM -> "—"
        else -> "–"
    }
}

private fun smartQuoteStyleOptions(): List<String> {
    return listOf(
        SettingsManager.SMART_QUOTES_STYLE_GERMAN_GUILLEMETS,
        SettingsManager.SMART_QUOTES_STYLE_FRENCH_GUILLEMETS,
        SettingsManager.SMART_QUOTES_STYLE_FRENCH_GUILLEMETS_NARROW_SPACED,
        SettingsManager.SMART_QUOTES_STYLE_GERMAN_LOW_HIGH,
        SettingsManager.SMART_QUOTES_STYLE_ENGLISH_CURLY
    )
}

// These labels intentionally use explicit quote pairs instead of generic placeholders.
// Change them only with care: quotation marks are interpreted visually and some
// combinations render ambiguously in Android text fields and dropdowns.
private fun smartQuotesStyleLabel(style: String): String {
    return when (style) {
        SettingsManager.SMART_QUOTES_STYLE_FRENCH_GUILLEMETS -> "«...»"
        SettingsManager.SMART_QUOTES_STYLE_FRENCH_GUILLEMETS_NARROW_SPACED -> "« ... »"
        SettingsManager.SMART_QUOTES_STYLE_GERMAN_LOW_HIGH -> "„...“"
        SettingsManager.SMART_QUOTES_STYLE_ENGLISH_CURLY -> "“...”"
        else -> "»...«"
    }
}

internal fun autoSpacePunctuationOptions(): List<Char> {
    return Punctuation.AUTO_SPACE_CANDIDATES.toList()
}

private fun autoSpacePunctuationSummary(
    beforePunctuation: String,
    afterPunctuation: String,
    beforeLabel: String,
    afterLabel: String,
    offLabel: String
): String {
    if (beforePunctuation.isNotEmpty() && beforePunctuation == afterPunctuation) {
        return "$beforeLabel, $afterLabel: ${beforePunctuation.map(::autoSpacePunctuationLabel).joinToString(" ")}"
    }
    val parts = buildList {
        beforePunctuation.takeIf { it.isNotEmpty() }?.let {
            add("$beforeLabel: ${it.map(::autoSpacePunctuationLabel).joinToString(" ")}")
        }
        afterPunctuation.takeIf { it.isNotEmpty() }?.let {
            add("$afterLabel: ${it.map(::autoSpacePunctuationLabel).joinToString(" ")}")
        }
    }
    return parts.joinToString(" · ").ifEmpty { offLabel }
}

private fun autoSpacePunctuationName(punctuation: Char): Int? = when (punctuation) {
    '.' -> R.string.punctuation_name_full_stop
    ',' -> R.string.punctuation_name_comma
    ';' -> R.string.punctuation_name_semicolon
    ':' -> R.string.punctuation_name_colon
    '!' -> R.string.punctuation_name_exclamation
    '?' -> R.string.punctuation_name_question
    else -> null
}

internal fun autoSpacePunctuationLabel(punctuation: Char): String {
    return when (punctuation) {
        '\\' -> "\\"
        '"' -> "\""
        else -> punctuation.toString()
    }
}

private fun addAutoSpacePunctuation(current: String, punctuation: Char): String {
    val selected = current.toSet() + punctuation
    return Punctuation.AUTO_SPACE_CANDIDATES.filter { it in selected }
}

internal fun toggleAutoSpacePunctuation(current: String, punctuation: Char): String {
    return if (punctuation in current) {
        current.filterNot { it == punctuation }
    } else {
        addAutoSpacePunctuation(current, punctuation)
    }
}

/** Automatic Shift by kind of text field: a summary row opening a checklist. */
@Composable
private fun AutoShiftFieldTypesRow() {
    val context = LocalContext.current
    var types by remember { mutableStateOf(it.palsoftware.pastiera.inputmethod.ShiftFieldTypes.enabled(context)) }
    var picking by remember { mutableStateOf(false) }
    val all = it.palsoftware.pastiera.inputmethod.ShiftFieldTypes.Type.entries
    val summary = all.filter { type -> type in types }
        .map { type -> stringResource(type.labelRes) }
        .ifEmpty { listOf(stringResource(R.string.shift_field_none)) }
        .joinToString(", ")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .settingRow(SettingLinkIds.TEXT_INPUT_AUTO_CAPITALIZE_RESTRICTED_FIELDS) { picking = true }
    ) {
        Column(modifier = Modifier.padding(start = 52.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Text(
                stringResource(R.string.auto_capitalize_restricted_fields_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.auto_capitalize_restricted_fields_title)) },
            text = {
                // Scrolls when the list is taller than the dialog (long labels, large text)
                Column(modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    Text(
                        stringResource(R.string.auto_capitalize_restricted_fields_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    all.forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    types = if (type in types) types - type else types + type
                                    it.palsoftware.pastiera.inputmethod.ShiftFieldTypes.setEnabled(context, types)
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Checkbox(checked = type in types, onCheckedChange = null)
                            Text(stringResource(type.labelRes), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.whats_new_done)) } }
        )
    }
}
