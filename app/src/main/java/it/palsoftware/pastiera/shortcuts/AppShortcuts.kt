package it.palsoftware.pastiera.shortcuts

import android.view.KeyEvent
import it.palsoftware.pastiera.R

/**
 * Universal app shortcuts: one set of standard key combos that does the same thing in
 * every app. Each app's preset says which of its own documented shortcuts a standard combo
 * becomes; Pastiera sends the app that shortcut instead of the combo you pressed.
 *
 * Key shortcuts in the presets are only those an app documents for its Android version (or
 * ships in its source code). Where an app documents none, its preset can suggest intents into
 * its own screens instead (SuggestedAppShortcuts). Research and sources: docs/app-shortcuts.md.
 */

/** A key and the modifiers held with it. */
data class KeyCombo(
    val keyCode: Int,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val meta: Boolean = false
) {
    /** The meta state an app sees on a key event carrying this combo. */
    val metaState: Int
        get() = (if (ctrl) KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON else 0) or
            (if (alt) KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON else 0) or
            (if (shift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0) or
            (if (meta) KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON else 0)

    /** A bare key that types a character: sent only where nothing is being typed. */
    val typesCharacter: Boolean
        get() = !ctrl && !alt && !meta && isCharacterKey(keyCode)

    /** A bare arrow key: in a text field it would move the cursor, not the app's selection. */
    val movesCursor: Boolean
        get() = !ctrl && !alt && !meta && keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT

    /** Stored form, e.g. "ctrl+shift:46". */
    fun serialize(): String = buildString {
        if (ctrl) append("ctrl+")
        if (alt) append("alt+")
        if (shift) append("shift+")
        if (meta) append("meta+")
        if (isNotEmpty()) setLength(length - 1)
        append(':').append(keyCode)
    }

    companion object {
        fun ctrl(keyCode: Int, shift: Boolean = false) = KeyCombo(keyCode, ctrl = true, shift = shift)
        fun alt(keyCode: Int) = KeyCombo(keyCode, alt = true)
        fun key(keyCode: Int, shift: Boolean = false) = KeyCombo(keyCode, shift = shift)

        fun parse(value: String): KeyCombo? {
            val separator = value.lastIndexOf(':')
            if (separator < 0) return null
            val keyCode = value.substring(separator + 1).toIntOrNull() ?: return null
            if (keyCode <= KeyEvent.KEYCODE_UNKNOWN) return null
            val modifiers = value.substring(0, separator).split('+').filter { it.isNotEmpty() }.toSet()
            if (modifiers.any { it !in setOf("ctrl", "alt", "shift", "meta") }) return null
            return KeyCombo(
                keyCode,
                ctrl = "ctrl" in modifiers,
                alt = "alt" in modifiers,
                shift = "shift" in modifiers,
                meta = "meta" in modifiers
            )
        }

        fun isCharacterKey(keyCode: Int): Boolean = when (keyCode) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9,
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z,
            in KeyEvent.KEYCODE_COMMA..KeyEvent.KEYCODE_PERIOD,
            in KeyEvent.KEYCODE_GRAVE..KeyEvent.KEYCODE_AT,
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_COMMA,
            in KeyEvent.KEYCODE_NUMPAD_EQUALS..KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN,
            KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_POUND, KeyEvent.KEYCODE_PLUS,
            KeyEvent.KEYCODE_SPACE -> true
            else -> false
        }
    }
}

/** Where a standard shortcut applies. */
enum class ShortcutScope {
    /** Also while typing (the app's shortcut is not a typed character). */
    Anywhere,
    /** Only when no text field is focused: lists, message views, the page. */
    OutsideTextFields
}

/** The standard combos, each meaning one thing in every app. */
enum class StandardShortcut(val combo: KeyCombo, val scope: ShortcutScope, val titleRes: Int) {
    New(KeyCombo.ctrl(KeyEvent.KEYCODE_N), ShortcutScope.Anywhere, R.string.app_shortcut_new),
    Search(KeyCombo.ctrl(KeyEvent.KEYCODE_F), ShortcutScope.Anywhere, R.string.app_shortcut_search),
    Open(KeyCombo.ctrl(KeyEvent.KEYCODE_O), ShortcutScope.OutsideTextFields, R.string.app_shortcut_open),
    Refresh(KeyCombo.ctrl(KeyEvent.KEYCODE_R), ShortcutScope.OutsideTextFields, R.string.app_shortcut_refresh),
    Send(KeyCombo.ctrl(KeyEvent.KEYCODE_ENTER), ShortcutScope.Anywhere, R.string.app_shortcut_send),
    Close(KeyCombo.ctrl(KeyEvent.KEYCODE_W), ShortcutScope.Anywhere, R.string.app_shortcut_close),
    Reply(KeyCombo.ctrl(KeyEvent.KEYCODE_R, shift = true), ShortcutScope.OutsideTextFields, R.string.app_shortcut_reply),
    ReplyAll(KeyCombo.ctrl(KeyEvent.KEYCODE_A, shift = true), ShortcutScope.OutsideTextFields, R.string.app_shortcut_reply_all),
    Forward(KeyCombo.ctrl(KeyEvent.KEYCODE_F, shift = true), ShortcutScope.OutsideTextFields, R.string.app_shortcut_forward),
    Archive(KeyCombo.ctrl(KeyEvent.KEYCODE_E), ShortcutScope.OutsideTextFields, R.string.app_shortcut_archive),
    Delete(KeyCombo.ctrl(KeyEvent.KEYCODE_D), ShortcutScope.OutsideTextFields, R.string.app_shortcut_delete),
    MarkUnread(KeyCombo.ctrl(KeyEvent.KEYCODE_U), ShortcutScope.OutsideTextFields, R.string.app_shortcut_mark_unread),
    Star(KeyCombo.ctrl(KeyEvent.KEYCODE_S, shift = true), ShortcutScope.OutsideTextFields, R.string.app_shortcut_star),
    Undo(KeyCombo.ctrl(KeyEvent.KEYCODE_Z), ShortcutScope.OutsideTextFields, R.string.app_shortcut_undo),
    Next(KeyCombo.alt(KeyEvent.KEYCODE_DPAD_DOWN), ShortcutScope.Anywhere, R.string.app_shortcut_next),
    Previous(KeyCombo.alt(KeyEvent.KEYCODE_DPAD_UP), ShortcutScope.Anywhere, R.string.app_shortcut_previous),
    Menu(KeyCombo.ctrl(KeyEvent.KEYCODE_M), ShortcutScope.Anywhere, R.string.app_shortcut_menu),
    Help(KeyCombo.ctrl(KeyEvent.KEYCODE_SLASH), ShortcutScope.Anywhere, R.string.app_shortcut_help),
    /** The app's own settings screen (Android's standard entry point for it). */
    Settings(KeyCombo.ctrl(KeyEvent.KEYCODE_COMMA), ShortcutScope.Anywhere, R.string.app_shortcut_settings),
    /**
     * The app's own launcher shortcuts (long-press its icon), in its order. Ctrl+Alt+1 to 4; on a
     * keyboard without a number row, the keys whose Alt character is 1 to 4.
     */
    AppAction1(KeyCombo(KeyEvent.KEYCODE_1, ctrl = true, alt = true), ShortcutScope.Anywhere, R.string.app_shortcut_app_action_1),
    AppAction2(KeyCombo(KeyEvent.KEYCODE_2, ctrl = true, alt = true), ShortcutScope.Anywhere, R.string.app_shortcut_app_action_2),
    AppAction3(KeyCombo(KeyEvent.KEYCODE_3, ctrl = true, alt = true), ShortcutScope.Anywhere, R.string.app_shortcut_app_action_3),
    AppAction4(KeyCombo(KeyEvent.KEYCODE_4, ctrl = true, alt = true), ShortcutScope.Anywhere, R.string.app_shortcut_app_action_4);

    companion object {
        fun forCombo(combo: KeyCombo): StandardShortcut? = entries.firstOrNull { it.combo == combo }
        fun byName(name: String): StandardShortcut? = entries.firstOrNull { it.name == name }
    }
}

enum class AppCategory(val titleRes: Int) {
    Social(R.string.app_shortcuts_category_social),
    Communication(R.string.app_shortcuts_category_communication),
    Browsers(R.string.app_shortcuts_category_browsers),
    Productivity(R.string.app_shortcuts_category_productivity),
    Entertainment(R.string.app_shortcuts_category_entertainment),
    VideoPlayers(R.string.app_shortcuts_category_video_players),
    Music(R.string.app_shortcuts_category_music),
    Shopping(R.string.app_shortcuts_category_shopping),
    Maps(R.string.app_shortcuts_category_maps),
    Travel(R.string.app_shortcuts_category_travel),
    News(R.string.app_shortcuts_category_news),
    Books(R.string.app_shortcuts_category_books),
    Photography(R.string.app_shortcuts_category_photography),
    Finance(R.string.app_shortcuts_category_finance),
    Education(R.string.app_shortcuts_category_education),
    Health(R.string.app_shortcuts_category_health),
    Food(R.string.app_shortcuts_category_food),
    Lifestyle(R.string.app_shortcuts_category_lifestyle),
    Business(R.string.app_shortcuts_category_business),
    Tools(R.string.app_shortcuts_category_tools),
    Dating(R.string.app_shortcuts_category_dating),
    Sports(R.string.app_shortcuts_category_sports),
    Weather(R.string.app_shortcuts_category_weather),
    Terminal(R.string.app_shortcuts_category_terminal),
    Other(R.string.app_shortcuts_category_other)
}

/**
 * An Android intent into the app itself (its package is added when it is sent), e.g. its share
 * screen to write a new post, or one of its web links that opens its search.
 */
data class AppIntent(
    val action: String,
    val data: String? = null,
    val type: String? = null,
    val categories: List<String> = emptyList()
) {
    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_SENDTO = "android.intent.action.SENDTO"
        const val ACTION_SEARCH = "android.intent.action.SEARCH"
        const val CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE"

        /** The app's share screen with nothing filled in: a new post, message or note. */
        fun share() = AppIntent(ACTION_SEND, type = "text/plain")
        /** The app's own search screen, when it declares one. */
        fun search() = AppIntent(ACTION_SEARCH)
        /** A link (web or the app's own scheme) the app opens itself. */
        fun link(uri: String) = AppIntent(ACTION_VIEW, data = uri, categories = listOf(CATEGORY_BROWSABLE))
    }
}

/**
 * An app's shortcuts for the standard combos: the key shortcuts it documents and, where it
 * documents none, suggested intents into its own screens (tried in order; the first the app on
 * the phone accepts is used).
 */
data class AppShortcutPreset(
    val packageName: String,
    val appName: String,
    val category: AppCategory,
    /** Where the key shortcuts are documented; null when the preset only has suggestions. */
    val source: String?,
    val shortcuts: Map<StandardShortcut, KeyCombo>,
    val suggested: Map<StandardShortcut, List<AppIntent>> = emptyMap()
)

/** The built-in presets: documented key shortcuts, then the top apps' suggested intents. */
object AppShortcutPresets {
    private const val CHROMIUM_SOURCE =
        "https://chromium.googlesource.com/chromium/src/+/main/chrome/android/java/src/org/chromium/chrome/browser/KeyboardShortcuts.java"

    private fun chromium(packageName: String, appName: String) = AppShortcutPreset(
        packageName, appName, AppCategory.Browsers, CHROMIUM_SOURCE,
        mapOf(
            StandardShortcut.New to KeyCombo.ctrl(KeyEvent.KEYCODE_T),
            StandardShortcut.Search to KeyCombo.ctrl(KeyEvent.KEYCODE_F),
            StandardShortcut.Open to KeyCombo.ctrl(KeyEvent.KEYCODE_L),
            StandardShortcut.Refresh to KeyCombo.ctrl(KeyEvent.KEYCODE_R),
            StandardShortcut.Close to KeyCombo.ctrl(KeyEvent.KEYCODE_W),
            StandardShortcut.Star to KeyCombo.ctrl(KeyEvent.KEYCODE_D),
            StandardShortcut.Next to KeyCombo.ctrl(KeyEvent.KEYCODE_TAB),
            StandardShortcut.Previous to KeyCombo.ctrl(KeyEvent.KEYCODE_TAB, shift = true),
            StandardShortcut.Menu to KeyCombo.alt(KeyEvent.KEYCODE_F)
        )
    )

    private fun googleEditor(packageName: String, appName: String, source: String, extra: Map<StandardShortcut, KeyCombo>) =
        AppShortcutPreset(
            packageName, appName, AppCategory.Productivity, source,
            mapOf(
                StandardShortcut.Search to KeyCombo.ctrl(KeyEvent.KEYCODE_F),
                StandardShortcut.Help to KeyCombo.ctrl(KeyEvent.KEYCODE_SLASH)
            ) + extra
        )

    private val documented: List<AppShortcutPreset> = listOf(
        // Browsers
        chromium("com.android.chrome", "Chrome"),
        chromium("com.brave.browser", "Brave"),
        chromium("com.vivaldi.browser", "Vivaldi"),

        // Email
        AppShortcutPreset(
            "com.google.android.gm", "Gmail", AppCategory.Communication,
            "https://support.google.com/mail/answer/6594?co=GENIE.Platform%3DAndroid",
            mapOf(
                StandardShortcut.New to KeyCombo.key(KeyEvent.KEYCODE_C),
                StandardShortcut.Refresh to KeyCombo.ctrl(KeyEvent.KEYCODE_U),
                StandardShortcut.Send to KeyCombo.ctrl(KeyEvent.KEYCODE_ENTER),
                StandardShortcut.Close to KeyCombo.ctrl(KeyEvent.KEYCODE_W),
                StandardShortcut.Reply to KeyCombo.key(KeyEvent.KEYCODE_R),
                StandardShortcut.ReplyAll to KeyCombo.key(KeyEvent.KEYCODE_A),
                StandardShortcut.Forward to KeyCombo.key(KeyEvent.KEYCODE_F),
                StandardShortcut.Archive to KeyCombo.key(KeyEvent.KEYCODE_E),
                StandardShortcut.Delete to KeyCombo.key(KeyEvent.KEYCODE_POUND),
                StandardShortcut.MarkUnread to KeyCombo.key(KeyEvent.KEYCODE_U, shift = true),
                StandardShortcut.Star to KeyCombo.key(KeyEvent.KEYCODE_S),
                StandardShortcut.Undo to KeyCombo.key(KeyEvent.KEYCODE_Z),
                StandardShortcut.Next to KeyCombo.key(KeyEvent.KEYCODE_J),
                StandardShortcut.Previous to KeyCombo.key(KeyEvent.KEYCODE_K),
                StandardShortcut.Menu to KeyCombo.key(KeyEvent.KEYCODE_M),
                StandardShortcut.Help to KeyCombo.key(KeyEvent.KEYCODE_SLASH, shift = true)
            )
        ),
        AppShortcutPreset(
            "com.microsoft.office.outlook", "Outlook", AppCategory.Communication,
            "https://support.microsoft.com/en-us/office/android-keyboard-shortcuts-3ac2d1f9-3843-461f-bc89-0e1d143cb2e1",
            mapOf(
                StandardShortcut.Send to KeyCombo.ctrl(KeyEvent.KEYCODE_ENTER),
                StandardShortcut.Archive to KeyCombo.ctrl(KeyEvent.KEYCODE_A),
                StandardShortcut.Undo to KeyCombo.ctrl(KeyEvent.KEYCODE_Z),
                StandardShortcut.MarkUnread to KeyCombo.ctrl(KeyEvent.KEYCODE_U),
                StandardShortcut.Star to KeyCombo.ctrl(KeyEvent.KEYCODE_F),
                StandardShortcut.Forward to KeyCombo.ctrl(KeyEvent.KEYCODE_J)
            )
        ),

        // Messaging
        AppShortcutPreset(
            "com.google.android.apps.dynamite", "Google Chat", AppCategory.Communication,
            "https://support.google.com/chat/answer/7649271?co=GENIE.Platform%3DAndroid",
            mapOf(
                StandardShortcut.New to KeyCombo.ctrl(KeyEvent.KEYCODE_K, shift = true),
                StandardShortcut.Search to KeyCombo.ctrl(KeyEvent.KEYCODE_F),
                StandardShortcut.Send to KeyCombo.key(KeyEvent.KEYCODE_ENTER),
                StandardShortcut.Reply to KeyCombo.key(KeyEvent.KEYCODE_R),
                StandardShortcut.Next to KeyCombo.key(KeyEvent.KEYCODE_DPAD_DOWN),
                StandardShortcut.Previous to KeyCombo.key(KeyEvent.KEYCODE_DPAD_UP),
                StandardShortcut.Menu to KeyCombo.ctrl(KeyEvent.KEYCODE_G),
                StandardShortcut.Help to KeyCombo.key(KeyEvent.KEYCODE_SLASH, shift = true)
            )
        ),

        // Documents
        googleEditor(
            "com.google.android.apps.docs.editors.docs", "Google Docs",
            "https://support.google.com/docs/answer/179738?co=GENIE.Platform%3DAndroid",
            mapOf(StandardShortcut.Open to KeyCombo.ctrl(KeyEvent.KEYCODE_O))
        ),
        googleEditor(
            "com.google.android.apps.docs.editors.sheets", "Google Sheets",
            "https://support.google.com/docs/answer/181110?co=GENIE.Platform%3DAndroid",
            mapOf(
                StandardShortcut.New to KeyCombo.ctrl(KeyEvent.KEYCODE_T),
                StandardShortcut.Open to KeyCombo.ctrl(KeyEvent.KEYCODE_O),
                StandardShortcut.Next to KeyCombo.alt(KeyEvent.KEYCODE_DPAD_DOWN),
                StandardShortcut.Previous to KeyCombo.alt(KeyEvent.KEYCODE_DPAD_UP)
            )
        ),
        googleEditor(
            "com.google.android.apps.docs.editors.slides", "Google Slides",
            "https://support.google.com/docs/answer/1696717?co=GENIE.Platform%3DAndroid",
            mapOf(StandardShortcut.New to KeyCombo.ctrl(KeyEvent.KEYCODE_M))
        ),
        AppShortcutPreset(
            "com.microsoft.office.word", "Word", AppCategory.Productivity,
            "https://support.microsoft.com/en-us/office/use-an-external-keyboard-with-word-for-android-515129a8-2f5e-410a-87aa-78b65504c244",
            mapOf(StandardShortcut.Search to KeyCombo.ctrl(KeyEvent.KEYCODE_F))
        ),
        AppShortcutPreset(
            "com.microsoft.office.excel", "Excel", AppCategory.Productivity,
            "https://support.microsoft.com/en-us/office/use-an-external-keyboard-with-excel-for-android-efe053d5-4b50-4e8b-b63f-e8a70c80974f",
            mapOf(StandardShortcut.Search to KeyCombo.ctrl(KeyEvent.KEYCODE_F))
        ),

        // Notes and tasks
        AppShortcutPreset(
            "com.google.android.keep", "Google Keep", AppCategory.Productivity,
            "https://support.google.com/keep/answer/12862970?co=GENIE.Platform%3DAndroid",
            mapOf(
                StandardShortcut.New to KeyCombo.ctrl(KeyEvent.KEYCODE_N),
                StandardShortcut.Search to KeyCombo.key(KeyEvent.KEYCODE_SLASH),
                StandardShortcut.Close to KeyCombo.key(KeyEvent.KEYCODE_ESCAPE),
                StandardShortcut.Archive to KeyCombo.key(KeyEvent.KEYCODE_E),
                StandardShortcut.Delete to KeyCombo.key(KeyEvent.KEYCODE_D),
                StandardShortcut.Star to KeyCombo.key(KeyEvent.KEYCODE_F),
                StandardShortcut.Next to KeyCombo.key(KeyEvent.KEYCODE_J),
                StandardShortcut.Previous to KeyCombo.key(KeyEvent.KEYCODE_K),
                StandardShortcut.Menu to KeyCombo.ctrl(KeyEvent.KEYCODE_M),
                StandardShortcut.Help to KeyCombo.key(KeyEvent.KEYCODE_SLASH, shift = true)
            )
        ),
        AppShortcutPreset(
            "com.todoist", "Todoist", AppCategory.Productivity,
            "https://www.todoist.com/help/todoist/features/use-keyboard-shortcuts-in-todoist-Wyovn2",
            mapOf(
                StandardShortcut.New to KeyCombo.key(KeyEvent.KEYCODE_Q),
                StandardShortcut.Search to KeyCombo.key(KeyEvent.KEYCODE_SLASH),
                StandardShortcut.Menu to KeyCombo.key(KeyEvent.KEYCODE_M),
                StandardShortcut.Help to KeyCombo.key(KeyEvent.KEYCODE_SLASH, shift = true)
            )
        ),

        // Calendar
        AppShortcutPreset(
            "com.google.android.calendar", "Google Calendar", AppCategory.Productivity,
            "https://support.google.com/calendar/answer/37034?co=GENIE.Platform%3DAndroid",
            mapOf(
                StandardShortcut.New to KeyCombo.ctrl(KeyEvent.KEYCODE_N),
                StandardShortcut.Search to KeyCombo.ctrl(KeyEvent.KEYCODE_F),
                StandardShortcut.Open to KeyCombo.ctrl(KeyEvent.KEYCODE_G),
                StandardShortcut.Refresh to KeyCombo.ctrl(KeyEvent.KEYCODE_R),
                StandardShortcut.Close to KeyCombo.key(KeyEvent.KEYCODE_ESCAPE),
                StandardShortcut.Delete to KeyCombo.key(KeyEvent.KEYCODE_FORWARD_DEL),
                StandardShortcut.Next to KeyCombo.ctrl(KeyEvent.KEYCODE_J),
                StandardShortcut.Previous to KeyCombo.ctrl(KeyEvent.KEYCODE_K),
                StandardShortcut.Menu to KeyCombo.ctrl(KeyEvent.KEYCODE_T, shift = true)
            )
        ),

        // Terminal: Ctrl+letter belongs to the shell, so only session switching is mapped
        AppShortcutPreset(
            "com.termux", "Termux", AppCategory.Terminal,
            "https://github.com/termux/termux-app/blob/master/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java",
            mapOf(
                StandardShortcut.Next to KeyCombo(KeyEvent.KEYCODE_DPAD_DOWN, ctrl = true, alt = true),
                StandardShortcut.Previous to KeyCombo(KeyEvent.KEYCODE_DPAD_UP, ctrl = true, alt = true)
            )
        )
    )

    /**
     * Every preset: the documented ones, then the suggested-only ones for apps without documented
     * shortcuts. An app in both keeps its documented keys and gains suggestions for the rest.
     */
    val all: List<AppShortcutPreset> = run {
        val suggested = SuggestedAppShortcuts.all.associateBy { it.packageName }
        val merged = documented.map { preset ->
            suggested[preset.packageName]?.let { preset.copy(suggested = it.suggested) } ?: preset
        } + SuggestedAppShortcuts.all.filter { suggestion -> documented.none { it.packageName == suggestion.packageName } }
        // Social is Play's Social category plus the hand-picked social media apps
        val ranked = merged.map { preset ->
            if (preset.packageName in HandPickedSocialApps.packages) preset.copy(category = AppCategory.Social) else preset
        }
        ranked + HandPickedSocialApps.added.filter { added -> ranked.none { it.packageName == added.packageName } }
    }

    private val byPackage = all.associateBy { it.packageName }

    fun forPackage(packageName: String): AppShortcutPreset? = byPackage[packageName]
}

/** What you changed for one app: off, its name when you added it, and your own shortcuts. */
data class AppShortcutAppSettings(
    val enabled: Boolean = true,
    /** Set for apps you added yourself (not in the presets). */
    val appName: String? = null,
    /** Your shortcut for a standard combo; null means "don't remap it in this app". */
    val overrides: Map<StandardShortcut, KeyCombo?> = emptyMap()
)

data class AppShortcutConfig(
    val enabled: Boolean = true,
    /** Suggested intents for apps that document no key shortcut for an action. */
    val suggestionsEnabled: Boolean = true,
    val apps: Map<String, AppShortcutAppSettings> = emptyMap()
)

/** What Pastiera does for a standard combo in an app. */
sealed class ShortcutAction {
    /** Send the app its own key shortcut. */
    data class SendKeys(val combo: KeyCombo) : ShortcutAction()
    /** Open one of the app's own screens: the first of [intents] the app accepts. */
    data class OpenInApp(val intents: List<AppIntent>) : ShortcutAction()
    /** Open a screen the app itself offers on this phone (AppActionDiscovery). */
    data class OpenDiscovered(val action: DiscoveredAction) : ShortcutAction()
}

/** Picks what to do in an app for a combo pressed on the keyboard. */
object AppShortcutRemapper {

    /** The app's key shortcut for [shortcut]: yours if you set one, otherwise its documented one. */
    fun effectiveShortcut(config: AppShortcutConfig, packageName: String, shortcut: StandardShortcut): KeyCombo? {
        val app = config.apps[packageName]
        if (app != null && app.overrides.containsKey(shortcut)) return app.overrides[shortcut]
        return AppShortcutPresets.forPackage(packageName)?.shortcuts?.get(shortcut)
    }

    /**
     * Suggested intents for [shortcut], used when the app has no key shortcut for it: none when
     * suggestions are off or you set this shortcut yourself.
     */
    fun suggestedIntents(config: AppShortcutConfig, packageName: String, shortcut: StandardShortcut): List<AppIntent> {
        if (!config.suggestionsEnabled) return emptyList()
        if (config.apps[packageName]?.overrides?.containsKey(shortcut) == true) return emptyList()
        val preset = AppShortcutPresets.forPackage(packageName) ?: return emptyList()
        if (preset.shortcuts.containsKey(shortcut)) return emptyList()
        return preset.suggested[shortcut].orEmpty()
    }

    /**
     * The app's own screen for [shortcut] found on the phone, used when neither a key shortcut nor
     * a suggestion covers it: none when suggestions are off or you set this shortcut yourself.
     */
    fun discoveredAction(
        config: AppShortcutConfig,
        packageName: String,
        shortcut: StandardShortcut,
        discovered: DiscoveredAppActions?
    ): DiscoveredAction? {
        if (discovered == null || !config.suggestionsEnabled) return null
        if (config.apps[packageName]?.overrides?.containsKey(shortcut) == true) return null
        return discovered.forStandard(shortcut)
    }

    fun isAppEnabled(config: AppShortcutConfig, packageName: String): Boolean =
        config.apps[packageName]?.enabled ?: true

    /**
     * What to do instead of passing [pressed] to the app, or null to let the key through unchanged.
     * [inTextField]: a text field has focus, so typed characters and list-only actions are left alone.
     * [discovered]: what the app offers on this phone, looked up only when a standard combo is pressed.
     */
    fun resolve(
        config: AppShortcutConfig,
        packageName: String?,
        pressed: KeyCombo,
        inTextField: Boolean,
        discovered: (() -> DiscoveredAppActions?)? = null
    ): ShortcutAction? {
        if (!config.enabled || packageName.isNullOrEmpty()) return null
        if (!isAppEnabled(config, packageName)) return null
        val shortcut = StandardShortcut.forCombo(pressed) ?: return null
        if (inTextField && shortcut.scope == ShortcutScope.OutsideTextFields) return null
        val target = effectiveShortcut(config, packageName, shortcut)
        if (target != null) {
            if (target == pressed) return null
            if (inTextField && (target.typesCharacter || target.movesCursor)) return null
            return ShortcutAction.SendKeys(target)
        }
        val intents = suggestedIntents(config, packageName, shortcut)
        if (intents.isNotEmpty()) return ShortcutAction.OpenInApp(intents)
        if (discovered == null || !config.suggestionsEnabled) return null
        return discoveredAction(config, packageName, shortcut, discovered())?.let { ShortcutAction.OpenDiscovered(it) }
    }
}
