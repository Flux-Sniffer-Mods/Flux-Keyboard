package it.palsoftware.pastiera.device
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import it.palsoftware.pastiera.R

/**
 * The phone's own settings for its keyboard trackpad: on the Titan 2 Elite, "Keyboard gesture"
 * (scrolling and moving the cursor on the keys), opened directly (see [KNOWN_PAGES]). On other
 * phones the Settings app is searched for a page named like it; failing that,
 * Settings search opens with "Keyboard gesture" on the clipboard, ready to paste.
 */
object PhoneTrackpadSettings {
    private const val SETTINGS_PACKAGE = "com.android.settings"
    private const val ACTION_SETTINGS_SEARCH = "com.android.settings.action.SETTINGS_SEARCH"
    private val PAGE_NAME = Regex("(?i)(keyboa?rd.?gesture|touch.?(pad|scroll|keyboard)|keyboard.?(touch|scroll)|scroll.?assist|cursor.?(assist|control|move))")

    /** An exported page of the phone's Settings app named like its keyboard gesture settings. */
    internal fun matchesTrackpadPage(className: String): Boolean = PAGE_NAME.containsMatchIn(className.substringAfterLast('.'))

    private fun findDevicePage(context: Context): ComponentName? = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(SETTINGS_PACKAGE, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(SETTINGS_PACKAGE, PackageManager.GET_ACTIVITIES)
        }
        info.activities?.firstOrNull { it.exported && matchesTrackpadPage(it.name) }
            ?.let { ComponentName(SETTINGS_PACKAGE, it.name) }
    }.getOrNull()

    private fun start(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    /**
     * The Titan 2 Elite's pages, found with tools/find-keyboard-gesture-page.sh: Unihertz's own
     * settings app, not Android's. Scroll assistant first, straight to the switch that matters:
     * the phone passes keyboard swipes (and flicks) to apps only while it's on. Keyboard gesture,
     * the page above it, should the phone not open it directly.
     */
    internal val KNOWN_PAGES = listOf(
        ComponentName("com.agui.settings", "com.agui.settings.touchpad.ScrollAssistantActivity"),
        ComponentName("com.agui.settings", "com.agui.settings.touchpad.KeyboardGestureActivity")
    )

    fun open(context: Context): Boolean {
        // Opening an activity the phone doesn't have, or doesn't let other apps open, just fails
        KNOWN_PAGES.forEach { page ->
            if (start(context, Intent().setComponent(page))) {
                // What to set there for Flux Keyboard's swipes
                Toast.makeText(context, R.string.phone_trackpad_scroll_assist_tip, Toast.LENGTH_LONG).show()
                return true
            }
        }
        findDevicePage(context)?.let { page ->
            if (start(context, Intent().setComponent(page))) return true
        }
        // Settings search, with what to search for ready to paste
        val query = context.getString(R.string.phone_trackpad_settings_query)
        runCatching {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText(query, query))
        }
        Toast.makeText(context, context.getString(R.string.phone_trackpad_settings_fallback, query), Toast.LENGTH_LONG).show()
        return start(context, Intent(ACTION_SETTINGS_SEARCH)) || start(context, Intent(Settings.ACTION_SETTINGS))
    }
}
