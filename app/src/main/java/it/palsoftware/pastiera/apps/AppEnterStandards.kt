package it.palsoftware.pastiera.apps
import it.palsoftware.pastiera.shortcuts.AppCategory
import it.palsoftware.pastiera.shortcuts.AppShortcutPreset
import it.palsoftware.pastiera.shortcuts.AppShortcutPresets
import it.palsoftware.pastiera.SettingsManager

/**
 * Enter by category, for every app in the app shortcut list (Settings > Apps >
 * Enter per app). Messengers on the curated, tested list keep their own strategies; per-app
 * overrides always win.
 *
 * - Chat (social, dating and messaging apps): the messaging preset (e.g. Enter sends,
 *   Shift+Enter new line), only in fields that say they send (IME action Send: message and
 *   comment boxes). A post composer or a note field says nothing, so Enter stays a new line there.
 * - Email: Enter is a new line, Ctrl+Enter sends, as Gmail and Outlook document it.
 * - Everything else: the app's own Enter (new line, search, next field).
 */
enum class EnterStandard { Chat, Email, Notes, AppDefault }

object AppEnterStandards {
    /** Email apps whose compose screen documents Ctrl+Enter to send. */
    val EMAIL_CTRL_ENTER_APPS = setOf("com.google.android.gm", "com.microsoft.office.outlook")

    /** Chat apps outside the Social, Dating and Communication categories, or not ranked there. */
    private val EXTRA_CHAT_APPS = setOf(
        "org.telegram.messenger", "org.thoughtcrime.securesms", "com.discord", "com.Slack",
        "com.microsoft.teams", "com.google.android.apps.dynamite", "com.viber.voip",
        "jp.naver.line.android", "com.kakao.talk", "com.tencent.mm", "im.vector.app",
        "ch.threema.app", "ch.threema.app.libre", "com.linkedin.android", "us.zoom.videomeetings",
        "com.whatsapp", "com.whatsapp.w4b", "com.facebook.orca", "com.snapchat.android",
        "com.google.android.apps.messaging", "com.twitter.android", "com.pinterest"
    )

    /** Communication apps that are not chats: browsers, the phone book, caller ID. */
    private val NOT_CHAT_APPS = setOf(
        "com.android.chrome", "com.google.android.contacts", "com.truecaller",
        "com.UCMobile.intl", "com.google.android.dialer"
    )

    /** Note-taking and writing apps, where Enter is always a new line. */
    private val NOTE_APPS = setOf(
        "com.google.android.keep", "notion.id", "md.obsidian", "com.microsoft.office.onenote",
        "com.samsung.android.app.notes", "com.evernote", "com.simplemobiletools.notes.pro",
        "net.cozic.joplin", "com.standardnotes", "com.automattic.simplenote", "com.orgzly",
        "net.gsantner.markor", "org.fossify.notes", "com.google.android.apps.docs.editors.docs",
        "com.microsoft.office.word", "com.microsoft.office.officehubrow", "com.todoist",
        "com.ticktick.task", "com.google.android.apps.tasks"
    )

    fun standardFor(packageName: String?): EnterStandard {
        if (packageName.isNullOrEmpty()) return EnterStandard.AppDefault
        if (packageName in EMAIL_CTRL_ENTER_APPS) return EnterStandard.Email
        if (packageName in NOTE_APPS) return EnterStandard.Notes
        if (packageName in EXTRA_CHAT_APPS) return EnterStandard.Chat
        if (packageName in NOT_CHAT_APPS) return EnterStandard.AppDefault
        val preset = AppShortcutPresets.forPackage(packageName)
        if (preset != null) return when (preset.category) {
            AppCategory.Social, AppCategory.Dating, AppCategory.Communication -> EnterStandard.Chat
            AppCategory.Productivity -> EnterStandard.Notes
            else -> EnterStandard.AppDefault
        }
        // Any other installed app: the category Android records for it
        return when (systemCategory?.invoke(packageName)) {
            android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> EnterStandard.Chat
            android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> EnterStandard.Notes
            else -> EnterStandard.AppDefault
        }
    }

    /** An installed app's Android category (ApplicationInfo.category); set when the app starts. */
    @Volatile
    var systemCategory: ((String) -> Int?)? = null

    fun installSystemCategoryLookup(context: android.content.Context) {
        val pm = context.applicationContext.packageManager
        systemCategory = { packageName ->
            runCatching { pm.getApplicationInfo(packageName, 0).category }.getOrNull()
        }
    }

    /**
     * What Enter does in [packageName] under its standard, for the messaging [preset]; null
     * leaves it to the app. [fieldSends]: the field's own Enter action is Send.
     */
    fun behaviorFor(packageName: String?, preset: String, fieldSends: Boolean): String? =
        when (standardFor(packageName)) {
            EnterStandard.Email -> SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND
            // Notes: Enter is always a new line; Ctrl+Enter is the app's own action (done, save)
            EnterStandard.Notes -> SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND
            // Chats and every other app: the preset, in every field (Flux Keyboard's default:
            // Enter sends, Shift+Enter is a new line)
            EnterStandard.Chat, EnterStandard.AppDefault -> when (preset) {
                SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE ->
                    SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE
                SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_CTRL_SEND ->
                    SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND
                SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_ONLY ->
                    SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE
                SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_SHIFT_SEND ->
                    SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_SHIFT_SEND
                else -> null
            }
        }

    /** How the send happens under the standard: the app's documented Ctrl+Enter, or its Send action. */
    fun sendStrategyFor(packageName: String?): String? = when (standardFor(packageName)) {
        EnterStandard.Email -> SettingsManager.ENTER_SEND_STRATEGY_CTRL_ENTER
        EnterStandard.Notes -> SettingsManager.ENTER_SEND_STRATEGY_EDITOR_ACTION
        EnterStandard.Chat -> SettingsManager.ENTER_SEND_STRATEGY_EDITOR_ACTION
        EnterStandard.AppDefault -> SettingsManager.ENTER_SEND_STRATEGY_EDITOR_ACTION
    }

    /** The apps in the shortcut list whose Enter follows a standard (chat or email), by name. */
    fun standardApps(): List<Pair<AppShortcutPreset, EnterStandard>> =
        AppShortcutPresets.all
            .map { it to standardFor(it.packageName) }
            .filter { it.second != EnterStandard.AppDefault }
            .sortedBy { it.first.appName.lowercase() }
}
