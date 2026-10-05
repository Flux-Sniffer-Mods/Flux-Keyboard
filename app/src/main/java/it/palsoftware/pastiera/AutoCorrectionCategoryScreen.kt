package it.palsoftware.pastiera

import it.palsoftware.pastiera.core.writeTextAtomically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import android.content.Intent
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Auto-correction category screen.
 */
@Composable
fun AutoCorrectionCategoryScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var autoCorrectEnabled by remember {
        mutableStateOf(SettingsManager.getAutoCorrectEnabled(context))
    }
    var experimentalSuggestionsEnabled by remember {
        mutableStateOf(SettingsManager.isExperimentalSuggestionsEnabled(context))
    }
    var suggestionsEnabled by remember {
        mutableStateOf(SettingsManager.getSuggestionsEnabled(context))
    }
    var inlineAutofillEnabled by remember {
        mutableStateOf(SettingsManager.getInlineAutofillEnabled(context))
    }
    var suggestionsBold by remember { mutableStateOf(SettingsManager.getSuggestionsBold(context)) }
    var suggestionKeys by remember { mutableStateOf(SettingsManager.getSuggestionKeys(context)) }
    var choosingSuggestionKeys by remember { mutableStateOf(false) }
    var emojiSuggestionsEnabled by remember {
        mutableStateOf(SettingsManager.getEmojiSuggestionsEnabled(context))
    }
    var accentMatchingEnabled by remember {
        mutableStateOf(SettingsManager.getAccentMatchingEnabled(context))
    }
    var autoReplaceOnSpaceEnter by remember {
        mutableStateOf(SettingsManager.getAutoReplaceOnSpaceEnter(context))
    }
    var maxAutoReplaceDistance by remember {
        mutableStateOf(SettingsManager.getMaxAutoReplaceDistance(context))
    }
    var useKeyboardProximity by remember {
        mutableStateOf(SettingsManager.getUseKeyboardProximity(context))
    }
    var useEditTypeRanking by remember {
        mutableStateOf(SettingsManager.getUseEditTypeRanking(context))
    }
    val currentDestination = when (val page = settingsChild(context, "autocorrection")) {
        "Settings" -> AutoCorrectionDestination.Settings
        "UserDictionary" -> AutoCorrectionDestination.UserDictionary
        else -> if (page?.startsWith("Edit:") == true) AutoCorrectionDestination.Edit(page.removePrefix("Edit:")) else AutoCorrectionDestination.Main
    }
    fun navigateTo(destination: AutoCorrectionDestination) {
        openSettingsChild(context, "autocorrection", when (destination) { AutoCorrectionDestination.Main -> "Main"; AutoCorrectionDestination.Settings -> "Settings"; AutoCorrectionDestination.UserDictionary -> "UserDictionary"; is AutoCorrectionDestination.Edit -> "Edit:${destination.languageCode}" })
    }
    fun navigateBack() { context.settingsActivity().finish() }


    val destination = currentDestination
        when (destination) {
            AutoCorrectionDestination.Main -> {
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
                                IconButton(onClick = { navigateBack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.settings_back_content_description)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.settings_category_auto_correction),
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
                        SettingsSectionDivider(stringResource(R.string.autocorrect_section_suggestions))
                        // Advanced suggestions master toggle
                        Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 80.dp)
                                    .settingRow(SettingLinkIds.AUTO_CORRECTION_EXPERIMENTAL_SUGGESTIONS)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Code,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.experimental_suggestions_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = stringResource(R.string.experimental_suggestions_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    FeatureStatusIcon(FeatureStatus.Experimental)
                                    Switch(
                                        checked = experimentalSuggestionsEnabled,
                                        onCheckedChange = { enabled ->
                                            experimentalSuggestionsEnabled = enabled
                                            SettingsManager.setExperimentalSuggestionsEnabled(context, enabled)
                                            if (enabled && !suggestionsEnabled) {
                                                suggestionsEnabled = true
                                                SettingsManager.setSuggestionsEnabled(context, true)
                                            }
                                        }
                                    )
                                }
                            }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .settingRow(SettingLinkIds.AUTO_CORRECTION_SUGGESTIONS)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TextFields,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.auto_correct_suggestions_toggle_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Switch(
                                        checked = suggestionsEnabled,
                                        onCheckedChange = { enabled ->
                                            suggestionsEnabled = enabled
                                            SettingsManager.setSuggestionsEnabled(context, enabled)
                                        },
                                        enabled = experimentalSuggestionsEnabled
                                    )
                                }
                            }

                        // Automatic correction: dictionary/suggestion based guessing on boundaries.
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp)
                                .settingRow(SettingLinkIds.AUTO_CORRECTION_AUTO_REPLACE)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TextFields,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.auto_correct_auto_replace_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.auto_correct_auto_replace_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = autoReplaceOnSpaceEnter,
                                    onCheckedChange = { enabled ->
                                        autoReplaceOnSpaceEnter = enabled
                                        SettingsManager.setAutoReplaceOnSpaceEnter(context, enabled)
                                    },
                                    enabled = experimentalSuggestionsEnabled
                                )
                            }
                        }

                        // Max auto-replace distance slider (only shown when automatic correction is enabled)
                        if (autoReplaceOnSpaceEnter) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .settingRow(SettingLinkIds.AUTO_CORRECTION_MAX_DISTANCE)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.auto_correct_max_distance_title),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = if (maxAutoReplaceDistance == 0) {
                                                stringResource(R.string.auto_correct_max_distance_off)
                                            } else {
                                                maxAutoReplaceDistance.toString()
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Slider(
                                        value = maxAutoReplaceDistance.toFloat(),
                                        onValueChange = { value ->
                                            val newValue = value.toInt().coerceIn(0, 3)
                                            maxAutoReplaceDistance = newValue
                                            SettingsManager.setMaxAutoReplaceDistance(context, newValue)
                                        },
                                        valueRange = 0f..3f,
                                        steps = 2,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = stringResource(R.string.auto_correct_max_distance_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        FluxActionRow(
                            linkId = SettingLinkIds.AUTO_CORRECTION_SUGGESTION_KEYS,
                            title = stringResource(R.string.suggestion_keys_title),
                            description = suggestionKeysLabel(suggestionKeys),
                            onClick = { choosingSuggestionKeys = true }
                        )
                        if (choosingSuggestionKeys) {
                            AlertDialog(
                                onDismissRequest = { choosingSuggestionKeys = false },
                                title = { Text(stringResource(R.string.suggestion_keys_title)) },
                                text = {
                                    Column {
                                        it.palsoftware.pastiera.inputmethod.SuggestionKeys.OPTIONS.forEach { option ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth().clickable {
                                                    suggestionKeys = option
                                                    SettingsManager.setSuggestionKeys(context, option)
                                                    choosingSuggestionKeys = false
                                                }.padding(vertical = 6.dp)
                                            ) {
                                                RadioButton(selected = option == suggestionKeys, onClick = null)
                                                Text(suggestionKeysLabel(option), modifier = Modifier.padding(start = 12.dp))
                                            }
                                        }
                                    }
                                },
                                confirmButton = {},
                                dismissButton = {
                                    TextButton(onClick = { choosingSuggestionKeys = false }) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                }
                            )
                        }

                        FluxSwitchRow(
                            linkId = SettingLinkIds.AUTO_CORRECTION_SUGGESTIONS_BOLD,
                            title = stringResource(R.string.suggestions_bold_title),
                            description = stringResource(R.string.suggestions_bold_description),
                            checked = suggestionsBold,
                            onCheckedChange = { enabled ->
                                suggestionsBold = enabled
                                SettingsManager.setSuggestionsBold(context, enabled)
                            }
                        )

                        FluxSwitchRow(
                            linkId = SettingLinkIds.AUTO_CORRECTION_EMOJI_SUGGESTIONS,
                            title = stringResource(R.string.emoji_suggestions_title),
                            description = stringResource(R.string.emoji_suggestions_description),
                            checked = emojiSuggestionsEnabled,
                            onCheckedChange = { enabled ->
                                emojiSuggestionsEnabled = enabled
                                SettingsManager.setEmojiSuggestionsEnabled(context, enabled)
                            }
                        )

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            FluxSwitchRow(
                                linkId = SettingLinkIds.AUTO_CORRECTION_INLINE_AUTOFILL,
                                title = stringResource(R.string.inline_autofill_title),
                                description = stringResource(R.string.inline_autofill_description),
                                checked = inlineAutofillEnabled,
                                onCheckedChange = { enabled ->
                                    inlineAutofillEnabled = enabled
                                    SettingsManager.setInlineAutofillEnabled(context, enabled)
                                }
                            )
                        }

                        // Gestures that pick, add or delete words live with the suggestions
                        FluxActionRow(
                            linkId = SettingLinkIds.ADVANCED_TRACKPAD_GESTURES,
                            title = stringResource(R.string.settings_trackpad_gestures_title),
                            description = stringResource(R.string.settings_trackpad_gestures_description),
                            onClick = {
                                openSettingsPage(context, SettingsPage(SettingsDestination.TrackpadGestures))
                            }
                        )

                        SettingsSectionDivider(stringResource(R.string.autocorrect_section_dictionary))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp)
                                .settingRow(SettingLinkIds.AUTO_CORRECTION_USER_DICTIONARY) {
                                    navigateTo(AutoCorrectionDestination.UserDictionary)
                                }
                        ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
	                                        text = stringResource(R.string.auto_correct_manage_user_dict_title),
	                                        style = MaterialTheme.typography.titleMedium,
	                                        fontWeight = FontWeight.Medium
	                                    )
	                                    Text(
	                                        text = stringResource(R.string.auto_correct_manage_user_dict_description),
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

                        // What it learns as you type: words, and the emails and numbers you type
                        FluxCheckTable(
                            linkId = "auto_correction.learn_frequent_words",
                            title = stringResource(R.string.learn_table_title),
                            description = stringResource(R.string.learn_table_description),
                            columns = listOf(stringResource(R.string.learn_table_words), stringResource(R.string.learn_table_contacts)),
                            rows = listOf(
                                "" to listOf(
                                    CheckCell({ SettingsManager.getLearnFrequentWords(context) }) { SettingsManager.setLearnFrequentWords(context, it) },
                                    CheckCell({ SettingsManager.getLearnContactDetails(context) }, "auto_correction.learn_contact_details") { SettingsManager.setLearnContactDetails(context, it) }
                                )
                            )
                        )

                        var showAddWordSuggestion by remember { mutableStateOf(SettingsManager.getShowAddWordSuggestion(context)) }
                        FluxSwitchRow(
                            linkId = "auto_correction.show_add_word",
                            title = stringResource(R.string.show_add_word_suggestion_title),
                            description = stringResource(R.string.show_add_word_suggestion_description),
                            checked = showAddWordSuggestion,
                            onCheckedChange = { enabled ->
                                showAddWordSuggestion = enabled
                                SettingsManager.setShowAddWordSuggestion(context, enabled)
                            }
                        )
                        var addLastWordShortcut by remember { mutableStateOf(SettingsManager.getAddLastWordShortcut(context)) }
                        FluxSwitchRow(
                            linkId = "auto_correction.add_last_word_shortcut",
                            title = stringResource(R.string.add_last_word_shortcut_title),
                            description = stringResource(R.string.add_last_word_shortcut_description),
                            checked = addLastWordShortcut,
                            onCheckedChange = { enabled ->
                                addLastWordShortcut = enabled
                                SettingsManager.setAddLastWordShortcut(context, enabled)
                            }
                        )
                        FluxActionRow(
                            linkId = SettingLinkIds.AUTO_CORRECTION_SPELL_CHECKER,
                            title = stringResource(R.string.spell_checker_title),
                            description = stringResource(R.string.spell_checker_description),
                            onClick = { it.palsoftware.pastiera.spellcheck.SpellCheckRules.openSystemSettings(context) }
                        )
                        var phoneSpellChecker by remember {
                            mutableStateOf(SettingsManager.getPreferences(context).getBoolean(it.palsoftware.pastiera.spellcheck.PhoneSpellChecker.KEY_ENABLED, true))
                        }
                        FluxSwitchRow(
                            linkId = "auto_correction.phone_spell_checker",
                            title = stringResource(R.string.phone_spell_checker_title),
                            description = stringResource(R.string.phone_spell_checker_description),
                            checked = phoneSpellChecker,
                            onCheckedChange = { enabled ->
                                phoneSpellChecker = enabled
                                SettingsManager.getPreferences(context).edit()
                                    .putBoolean(it.palsoftware.pastiera.spellcheck.PhoneSpellChecker.KEY_ENABLED, enabled).apply()
                            }
                        )

                        SettingsSectionDivider(stringResource(R.string.autocorrect_section_replacements))
                        // Text replacements: explicit user/default rules like "ca -> ça".
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp)
                                .settingRow(SettingLinkIds.AUTO_CORRECTION_TEXT_REPLACEMENTS)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TextFields,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
	                                Column(modifier = Modifier.weight(1f)) {
	                                    Text(
	                                        text = stringResource(R.string.auto_correct_title),
	                                        style = MaterialTheme.typography.titleMedium,
	                                        fontWeight = FontWeight.Medium
	                                    )
	                                    Text(
	                                        text = stringResource(R.string.auto_correct_title_description),
	                                        style = MaterialTheme.typography.bodySmall,
	                                        color = MaterialTheme.colorScheme.onSurfaceVariant
	                                    )
	                                }
                                Switch(
                                    checked = autoCorrectEnabled,
                                    onCheckedChange = { enabled ->
                                        autoCorrectEnabled = enabled
                                        SettingsManager.setAutoCorrectEnabled(context, enabled)
                                    }
                                )
                            }
                        }

                        // Text replacement language/rule sets (only if text replacements are enabled)
                        if (autoCorrectEnabled) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 80.dp)
                                    .settingRow(SettingLinkIds.AUTO_CORRECTION_LANGUAGES) {
                                        navigateTo(AutoCorrectionDestination.Settings)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Language,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
	                                    Column(modifier = Modifier.weight(1f)) {
	                                        Text(
	                                            text = stringResource(R.string.auto_correct_languages_title),
	                                            style = MaterialTheme.typography.titleMedium,
	                                            fontWeight = FontWeight.Medium
	                                        )
	                                        Text(
	                                            text = stringResource(R.string.auto_correct_languages_description),
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
                        }

                        SettingsSectionDivider(stringResource(R.string.autocorrect_section_tuning))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .settingRow(SettingLinkIds.AUTO_CORRECTION_ACCENT_MATCHING)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TextFields,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.auto_correct_accent_matching_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.auto_correct_accent_matching_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = accentMatchingEnabled,
                                    onCheckedChange = { enabled ->
                                        accentMatchingEnabled = enabled
                                        SettingsManager.setAccentMatchingEnabled(context, enabled)
                                    },
                                    enabled = experimentalSuggestionsEnabled
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .settingRow(SettingLinkIds.AUTO_CORRECTION_KEYBOARD_PROXIMITY)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TextFields,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.auto_correct_keyboard_proximity_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.auto_correct_keyboard_proximity_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = useKeyboardProximity,
                                    onCheckedChange = { enabled ->
                                        useKeyboardProximity = enabled
                                        SettingsManager.setUseKeyboardProximity(context, enabled)
                                    },
                                    enabled = experimentalSuggestionsEnabled
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .settingRow(SettingLinkIds.AUTO_CORRECTION_EDIT_TYPE_RANKING)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TextFields,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.auto_correct_edit_type_ranking_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.auto_correct_edit_type_ranking_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = useEditTypeRanking,
                                    onCheckedChange = { enabled ->
                                        useEditTypeRanking = enabled
                                        SettingsManager.setUseEditTypeRanking(context, enabled)
                                    },
                                    enabled = experimentalSuggestionsEnabled
                                )
                            }
                        }

                    }
                }
            }

            AutoCorrectionDestination.Settings -> {
                AutoCorrectSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onEditLanguage = { languageCode ->
                        navigateTo(AutoCorrectionDestination.Edit(languageCode))
                    }
                )
            }

            is AutoCorrectionDestination.Edit -> {
                AutoCorrectEditScreen(
                    languageCode = destination.languageCode,
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }

            AutoCorrectionDestination.UserDictionary -> {
                UserDictionaryScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
        }

}

private sealed class AutoCorrectionDestination {
    object Main : AutoCorrectionDestination()
    object Settings : AutoCorrectionDestination()
    data class Edit(val languageCode: String) : AutoCorrectionDestination()
    object UserDictionary : AutoCorrectionDestination()
}

private enum class UserDictSource { DEFAULT, USER }

private data class UserDictItem(val word: String, val source: UserDictSource)

@Composable
private fun UserDictionaryScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val defaultStore = remember(context) { DefaultUserDefaultsStore(context) }
    val userStore = remember { it.palsoftware.pastiera.core.suggestions.UserDictionaryStore() }
    var entries by remember { mutableStateOf(emptyList<UserDictItem>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<UserDictItem?>(null) }
    var dialogWord by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    val filteredEntries = remember(entries, searchQuery) {
        if (searchQuery.isBlank()) {
            entries
        } else {
            val query = searchQuery.lowercase()
            entries.filter { it.word.lowercase().contains(query) }
        }
    }
    val hasEntries = entries.isNotEmpty()

    fun refreshEntries() {
        // Ensure cache is populated from persistent storage before snapshots/removals.
        userStore.loadUserEntries(context)
        val defaults = defaultStore.loadEntries().map { UserDictItem(it.word, UserDictSource.DEFAULT) }
        val users = userStore.getSnapshot().map { it.word }.map { UserDictItem(it, UserDictSource.USER) }
        entries = (defaults + users).sortedBy { it.word.lowercase() }
    }

    LaunchedEffect(Unit) {
        refreshEntries()
    }

    fun notifyDictionaryUpdated() {
        val intent = Intent(AppBroadcastActions.USER_DICTIONARY_UPDATED).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    fun addWord(word: String) {
        val trimmed = word.trim()
        if (trimmed.isNotEmpty()) {
            userStore.addWord(context, trimmed)
            refreshEntries()
            notifyDictionaryUpdated()
        }
    }

    fun updateWord(entry: UserDictItem, newWord: String) {
        val trimmed = newWord.trim()
        if (trimmed.isNotEmpty() && trimmed != entry.word) {
            when (entry.source) {
                UserDictSource.DEFAULT -> defaultStore.update(entry.word, trimmed)
                UserDictSource.USER -> userStore.updateWord(context, entry.word, trimmed)
            }
            refreshEntries()
            notifyDictionaryUpdated()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
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
                            text = stringResource(R.string.user_dict_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (searchActive) {
                                    searchActive = false
                                    searchQuery = ""
                                } else {
                                    searchActive = true
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (searchActive) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = stringResource(
                                    if (searchActive) R.string.auto_correct_clear_search
                                    else R.string.auto_correct_search_description
                                )
                            )
                        }
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.user_dict_add_button)
                            )
                        }
                    }
                    if (searchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            placeholder = {
                                Text(stringResource(R.string.auto_correct_search_placeholder))
                            },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = stringResource(R.string.auto_correct_search_description)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.auto_correct_clear_search)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filteredEntries.isEmpty()) {
                item {
                    Text(
                        text = if (hasEntries) {
                            stringResource(R.string.auto_correct_no_corrections_found)
                        } else {
                            stringResource(R.string.user_dict_empty_state)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(filteredEntries) { entry ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = entry.word,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                editingEntry = entry
                                dialogWord = entry.word
                            }) {
                                Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                            }
                            IconButton(onClick = {
                                when (entry.source) {
                                    UserDictSource.DEFAULT -> defaultStore.remove(entry.word)
                                    UserDictSource.USER -> userStore.removeWord(context, entry.word)
                                }
                                refreshEntries()
                                notifyDictionaryUpdated()
                            }) {
                                Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }

    // Add word dialog
    if (showAddDialog) {
        UserDictWordDialog(
            title = stringResource(R.string.user_dict_add_hint),
            word = dialogWord,
            onWordChange = { dialogWord = it },
            onConfirm = {
                addWord(dialogWord)
                showAddDialog = false
                dialogWord = ""
            },
            onDismiss = {
                showAddDialog = false
                dialogWord = ""
            }
        )
    }

    // Edit word dialog
    editingEntry?.let { entry ->
        UserDictWordDialog(
            title = stringResource(R.string.user_dict_edit_title),
            word = dialogWord,
            onWordChange = { dialogWord = it },
            onConfirm = {
                updateWord(entry, dialogWord)
                editingEntry = null
                dialogWord = ""
            },
            onDismiss = {
                editingEntry = null
                dialogWord = ""
            }
        )
    }
}

@Composable
private fun UserDictWordDialog(
    title: String,
    word: String,
    onWordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = word,
                onValueChange = onWordChange,
                label = { Text(title) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (word.isNotBlank()) {
                        IconButton(onClick = { onWordChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = null
                            )
                        }
                    }
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = word.isNotBlank()
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}



private data class DefaultUserWord(val word: String, val frequency: Int)

/**
 * Handles editable default user dictionary stored in app-private storage.
 * Uses JSON format aligned with assets/common/dictionaries/user_defaults.json.
 */
private class DefaultUserDefaultsStore(private val context: Context) {
    private val fileName = "user_defaults.json"
    private val assetPath = "common/dictionaries/$fileName"

    private fun ensureLocalFile(): java.io.File {
        val file = context.getFileStreamPath(fileName)
        if (!file.exists()) {
            try {
                context.assets.open(assetPath).use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (_: Exception) {
                // Asset missing; create empty file
                file.parentFile?.mkdirs()
                file.writeText("[]")
            }
        }
        return file
    }

    fun loadEntries(): List<DefaultUserWord> {
        val file = ensureLocalFile()
        return try {
            val jsonString = file.readText()
            val jsonArray = JSONArray(jsonString)
            buildList {
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val word = obj.getString("w")
                    val freq = obj.optInt("f", 1)
                    add(DefaultUserWord(word, freq))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addOrBump(word: String, baseFrequency: Int = 10) {
        val file = ensureLocalFile()
        val entries = loadEntries().toMutableList()
        val existingIndex = entries.indexOfFirst { it.word.equals(word, ignoreCase = true) }
        if (existingIndex >= 0) {
            val existing = entries[existingIndex]
            entries[existingIndex] = existing.copy(frequency = existing.frequency + 1)
        } else {
            entries.add(DefaultUserWord(word, baseFrequency))
        }
        persist(entries, file)
    }

    fun remove(word: String) {
        val file = ensureLocalFile()
        val entries = loadEntries().filterNot { it.word.equals(word, ignoreCase = true) }
        persist(entries, file)
    }

    fun update(oldWord: String, newWord: String) {
        val file = ensureLocalFile()
        val entries = loadEntries().map {
            if (it.word.equals(oldWord, ignoreCase = true)) it.copy(word = newWord) else it
        }
        persist(entries, file)
    }

    private fun persist(entries: List<DefaultUserWord>, file: java.io.File) {
        try {
            val array = JSONArray()
            entries.forEach { entry ->
                val obj = JSONObject()
                obj.put("w", entry.word)
                obj.put("f", entry.frequency)
                array.put(obj)
            }
            file.writeTextAtomically(array.toString())
        } catch (_: Exception) {
            // Ignore persistence errors; UI will just not reflect changes
        }
    }
}

@Composable
private fun suggestionKeysLabel(option: String): String = stringResource(
    when (option) {
        it.palsoftware.pastiera.inputmethod.SuggestionKeys.CTRL_SHIFT_QWE -> R.string.suggestion_keys_ctrl_shift_qwe
        it.palsoftware.pastiera.inputmethod.SuggestionKeys.CTRL_DIGITS -> R.string.suggestion_keys_ctrl_digits
        else -> R.string.suggestion_keys_off
    }
)
