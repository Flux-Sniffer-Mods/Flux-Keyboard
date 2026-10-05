package it.palsoftware.pastiera.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/** One of an app's own screens found on the phone: a launcher shortcut or its settings. */
data class DiscoveredAction(val id: String, val label: String, val intentUri: String)

/**
 * What an installed app itself offers to other apps: the shortcuts it shows when you long-press
 * its launcher icon (its shortcuts.xml, in the app's order) and its settings screen
 * (Intent.ACTION_APPLICATION_PREFERENCES). Only screens the app lets other apps open are kept.
 */
data class DiscoveredAppActions(
    val launcherShortcuts: List<DiscoveredAction> = emptyList(),
    val settings: DiscoveredAction? = null
) {
    /**
     * The app's own screen for [shortcut]: the numbered app shortcuts and settings, and New and
     * Search when one of its launcher shortcuts says so (e.g. "compose", "new_post", "Search").
     */
    fun forStandard(shortcut: StandardShortcut): DiscoveredAction? = when (shortcut) {
        StandardShortcut.Settings -> settings
        StandardShortcut.AppAction1 -> launcherShortcuts.getOrNull(0)
        StandardShortcut.AppAction2 -> launcherShortcuts.getOrNull(1)
        StandardShortcut.AppAction3 -> launcherShortcuts.getOrNull(2)
        StandardShortcut.AppAction4 -> launcherShortcuts.getOrNull(3)
        StandardShortcut.New -> launcherShortcuts.firstOrNull { it.means(NEW_WORDS) }
        StandardShortcut.Search -> launcherShortcuts.firstOrNull { it.means(SEARCH_WORDS) }
        else -> null
    }

    companion object {
        val NONE = DiscoveredAppActions()
        private val NEW_WORDS = setOf("new", "compose", "create", "write", "post", "draft")
        private val SEARCH_WORDS = setOf("search", "find")

        /** "newPost", "compose_message", "New chat" -> new, post, compose, message, chat. */
        internal fun words(text: String): Set<String> =
            text.replace(Regex("([a-z])([A-Z])"), "$1 $2")
                .lowercase()
                .split(Regex("[^a-z]+"))
                .filter { it.isNotEmpty() }
                .toSet()

        private fun DiscoveredAction.means(vocabulary: Set<String>): Boolean =
            (words(id) + words(label)).any { it in vocabulary }
    }
}

object AppActionDiscovery {
    private const val TAG = "PastieraAppActions"
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val SHORTCUTS_META_DATA = "android.app.shortcuts"
    private const val ACTION_APPLICATION_PREFERENCES = "android.intent.action.APPLICATION_PREFERENCES"

    fun discover(context: Context, packageName: String): DiscoveredAppActions = try {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
        val shortcuts = pm.queryIntentActivities(launcher, PackageManager.GET_META_DATA)
            .flatMap { info ->
                val resId = info.activityInfo.metaData?.getInt(SHORTCUTS_META_DATA) ?: 0
                if (resId == 0) emptyList() else readShortcuts(context, info.activityInfo.applicationInfo, resId)
            }
            .distinctBy { it.id }
        val settings = Intent(ACTION_APPLICATION_PREFERENCES).setPackage(packageName)
            .takeIf { openable(context, it, packageName) }
            ?.let { DiscoveredAction("settings", "", it.toUri(Intent.URI_INTENT_SCHEME)) }
        DiscoveredAppActions(shortcuts, settings)
    } catch (error: Exception) {
        Log.w(TAG, "$packageName: app shortcuts not read", error)
        DiscoveredAppActions.NONE
    }

    /** The intent of [action], ready to start, or null when the app no longer lets it be opened. */
    fun intentFor(context: Context, packageName: String, action: DiscoveredAction): Intent? {
        val intent = runCatching { Intent.parseUri(action.intentUri, Intent.URI_INTENT_SCHEME) }.getOrNull()
            ?: return null
        intent.selector = null
        if (!openable(context, intent, packageName)) return null
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Opens one of [packageName]'s own activities that other apps may start. */
    private fun openable(context: Context, intent: Intent, packageName: String): Boolean {
        val target = intent.component?.packageName ?: intent.`package`
        if (target != packageName) return false
        return context.packageManager.queryIntentActivities(intent, 0).any {
            it.activityInfo.packageName == packageName && it.activityInfo.exported && it.activityInfo.enabled
        }
    }

    private fun readShortcuts(context: Context, app: ApplicationInfo, resId: Int): List<DiscoveredAction> {
        val res = context.packageManager.getResourcesForApplication(app)
        val parser = res.getXml(resId)
        val found = mutableListOf<DiscoveredAction>()
        try {
            var id: String? = null
            var label = ""
            var enabled = true
            var lastIntent: Intent? = null
            while (true) {
                when (parser.next()) {
                    XmlPullParser.END_DOCUMENT -> break
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "shortcut" -> {
                            id = parser.getAttributeValue(ANDROID_NS, "shortcutId")
                            enabled = parser.getAttributeBooleanValue(ANDROID_NS, "enabled", true)
                            val labelRes = parser.getAttributeResourceValue(ANDROID_NS, "shortcutShortLabel", 0)
                            label = if (labelRes != 0) {
                                runCatching { res.getString(labelRes) }.getOrDefault("")
                            } else {
                                parser.getAttributeValue(ANDROID_NS, "shortcutShortLabel").orEmpty()
                            }
                            lastIntent = null
                        }
                        // With several intents the last one is the screen that opens (the others are its back stack)
                        "intent" -> if (id != null) {
                            lastIntent = Intent.parseIntent(res, parser, Xml.asAttributeSet(parser))
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "shortcut") {
                        val intent = lastIntent
                        if (id != null && enabled && intent != null) {
                            if (intent.component == null && intent.`package` == null) intent.setPackage(app.packageName)
                            if (openable(context, intent, app.packageName)) {
                                found += DiscoveredAction(id, label.ifEmpty { id }, intent.toUri(Intent.URI_INTENT_SCHEME))
                            }
                        }
                        id = null
                        lastIntent = null
                    }
                }
            }
        } finally {
            parser.close()
        }
        return found
    }
}
