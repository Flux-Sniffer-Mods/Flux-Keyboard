package it.palsoftware.pastiera.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import it.palsoftware.pastiera.SettingsManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A shortcut added to the quick launcher by hand, the way a home screen adds one */
data class UserShortcut(val id: String, val label: String, val packageName: String, val intentUri: String)

/**
 * Shortcuts people add to the quick launcher from apps that offer them (a contact's direct dial,
 * a bookmark, a settings page). Only the default launcher may read the ones already on the home
 * screen, so each is added once here, through the offering app's own screen.
 */
object UserShortcuts {
    private const val KEY = "quick_launcher_user_shortcuts"

    /** The launch action marking a hand-added shortcut: its intent is started as the app made it */
    const val LAUNCH_ACTION = "it.palsoftware.pastiera.USER_SHORTCUT"

    fun all(context: Context): List<UserShortcut> {
        val raw = SettingsManager.getPreferences(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                UserShortcut(o.getString("id"), o.getString("label"), o.getString("package"), o.getString("intent"))
            }
        }.getOrDefault(emptyList())
    }

    private fun save(context: Context, shortcuts: List<UserShortcut>) {
        val array = JSONArray()
        shortcuts.forEach {
            array.put(JSONObject().put("id", it.id).put("label", it.label).put("package", it.packageName).put("intent", it.intentUri))
        }
        SettingsManager.getPreferences(context).edit().putString(KEY, array.toString()).apply()
    }

    private const val UNSUPPORTED_KEY = "quick_launcher_unsupported_shortcut_screens"

    /** A shortcut screen that only pins to the home screen: it isn't offered again. */
    fun markUnsupported(context: Context, component: String) {
        val prefs = SettingsManager.getPreferences(context)
        val set = prefs.getStringSet(UNSUPPORTED_KEY, emptySet()).orEmpty() + component
        prefs.edit().putStringSet(UNSUPPORTED_KEY, set).apply()
    }

    fun isUnsupported(context: Context, component: String): Boolean =
        component in SettingsManager.getPreferences(context).getStringSet(UNSUPPORTED_KEY, emptySet()).orEmpty()

    fun remove(context: Context, id: String) {
        save(context, all(context).filterNot { it.id == id })
        iconFile(context, id).delete()
    }

    private fun iconFile(context: Context, id: String) = File(File(context.filesDir, "user_shortcut_icons"), "$id.png")

    /** The shortcut's own icon, or its app's */
    fun icon(context: Context, shortcut: UserShortcut): Drawable? {
        val file = iconFile(context, shortcut.id)
        if (file.exists()) {
            BitmapFactory.decodeFile(file.path)?.let { return BitmapDrawable(context.resources, it) }
        }
        return runCatching { context.packageManager.getApplicationIcon(shortcut.packageName) }.getOrNull()
    }

    /**
     * Keeps the shortcut an app's shortcut screen returned, or returns null when it can't be
     * opened from here (a newer-style shortcut only the home screen may start).
     */
    fun addFromResult(context: Context, packageName: String, data: Intent?): UserShortcut? {
        data ?: return null
        var label: String? = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
        var intent: Intent? = legacyIntent(data)
        var icon: Bitmap? = legacyIcon(data)

        if (intent == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val info = runCatching {
                context.getSystemService(LauncherApps::class.java)?.getPinItemRequest(data)?.shortcutInfo
            }.getOrNull()
            if (info != null) {
                label = label ?: (info.shortLabel ?: info.longLabel)?.toString()
                intent = runCatching { info.intent }.getOrNull()
                if (intent == null) {
                    // A shortcut the app also declares in its manifest can be started by its intent
                    AppActionDiscovery.discover(context, info.`package`).launcherShortcuts
                        .firstOrNull { it.id == info.id }
                        ?.let { action ->
                            intent = runCatching { Intent.parseUri(action.intentUri, Intent.URI_INTENT_SCHEME) }.getOrNull()
                            if (label.isNullOrBlank()) label = action.label
                        }
                }
            }
        }
        val target = intent ?: return null
        val shortcut = UserShortcut(
            id = UUID.randomUUID().toString(),
            label = label?.takeIf { it.isNotBlank() } ?: appLabel(context, packageName),
            packageName = packageName,
            intentUri = target.toUri(Intent.URI_INTENT_SCHEME)
        )
        icon?.let { bitmap ->
            runCatching {
                val file = iconFile(context, shortcut.id)
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        save(context, all(context) + shortcut)
        return shortcut
    }

    /**
     * A shortcut Flux Keyboard makes itself (a contact to call or message, a website, a Termux
     * task): [intent] is started as it is; [packageName] is the app it belongs to (its icon).
     */
    fun addBuilt(context: Context, label: String, packageName: String, intent: Intent): UserShortcut {
        val shortcut = UserShortcut(
            id = UUID.randomUUID().toString(),
            label = label,
            packageName = packageName,
            intentUri = intent.toUri(Intent.URI_INTENT_SCHEME)
        )
        save(context, all(context) + shortcut)
        return shortcut
    }

    // Termux's run-command service (a script in ~/.shortcuts, as Termux:Widget runs it)
    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    const val TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
    const val TERMUX_HOME = "/data/data/com.termux/files/home"

    /** Runs [path] in Termux: in the background, or in a terminal session. */
    fun termuxCommand(path: String, background: Boolean, arguments: Array<String>? = null): Intent =
        Intent("com.termux.RUN_COMMAND").setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
            .putExtra("com.termux.RUN_COMMAND_PATH", path)
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
            .apply { arguments?.let { putExtra("com.termux.RUN_COMMAND_ARGUMENTS", it) } }

    @Suppress("DEPRECATION")
    private fun legacyIntent(data: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
        } else {
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT)
        }

    @Suppress("DEPRECATION")
    private fun legacyIcon(data: Intent): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java)
        } else {
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON)
        }

    private fun appLabel(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}
