package it.palsoftware.pastiera.adb

import android.content.Context
import android.os.Handler
import android.os.Looper
import it.palsoftware.pastiera.SettingsManager
import org.json.JSONObject

/**
 * Screen size per app, through Shizuku: Android has no per-app density, so the screen's density
 * changes as apps come to the front (wm density), to the app's preset or, for apps without one,
 * to the base (the last preset chosen for the whole phone, or the phone's own density after a
 * reset). Only the density changes; the screen's size and everything else are left alone.
 */
object PerAppDensity {
    private const val PREF_APPS = "per_app_density"
    private const val PREF_BASE = "density_base"
    /** The phone's own density (wm density reset). */
    const val BASE_RESET = "reset"
    /** Packages that are never an app in front (the system's own windows, this keyboard). */
    private val IGNORED = setOf("android", "com.android.systemui")

    /**
     * Quickstep, the recent apps screen: the system's own launcher draws it, even with another
     * home app (Niagara) chosen. It keeps the density of the app it was opened from, since a
     * density change while recents draws the app's snapshot makes it redraw mid-animation (and
     * can crash it). A home app of your own is an app like any other, with its own size; only
     * when the system launcher is also the home screen do the two share it (one app, both).
     */
    private fun quickstep(context: Context): Set<String> = runCatching {
        val pm = context.packageManager
        val home = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
        pm.queryIntentActivities(home, 0)
            .filter { it.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 }
            .map { it.activityInfo.packageName }
            // The settings app's fallback home isn't recents
            .filter { it != "com.android.settings" }
            .toSet()
    }.getOrDefault(emptySet())

    /** A short wait so passing windows (a notification shade, a dialog) don't switch it. */
    private const val SETTLE_MS = 400L

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    @Volatile private var applied: String? = null
    /** The app whose screen size is in use (null for the base, or not known). */
    @Volatile private var appliedFor: String? = null
    /** While recents is in front: whether the app whose size is in use is still among them. */
    private var recentsWatch: Runnable? = null
    private const val RECENTS_CHECK_MS = 1_000L
    private const val RECENTS_CHECKS = 30

    private fun prefs(context: Context) = SettingsManager.getPreferences(context)

    /** Each app's preset (its action, as ScreenDensity.Preset.action). */
    fun apps(context: Context): Map<String, String> = runCatching {
        val json = JSONObject(prefs(context).getString(PREF_APPS, null) ?: return emptyMap())
        json.keys().asSequence().associateWith { json.getString(it) }
    }.getOrDefault(emptyMap())

    fun setApp(context: Context, packageName: String, preset: ScreenDensity.Preset?) {
        val updated = apps(context).toMutableMap()
        if (preset == null) updated.remove(packageName) else updated[packageName] = preset.action
        prefs(context).edit().putString(PREF_APPS, JSONObject(updated as Map<*, *>).toString()).apply()
        applied = null
    }

    /** The density for apps without their own: a preset's action, or [BASE_RESET]. */
    fun base(context: Context): String = prefs(context).getString(PREF_BASE, BASE_RESET) ?: BASE_RESET

    fun setBase(context: Context, base: String) {
        prefs(context).edit().putString(PREF_BASE, base).apply()
        applied = base
    }

    /** An app came to the front: its density, or the base, once it has stayed there a moment. */
    fun onAppInFront(context: Context, packageName: String?, needsConfirming: Boolean = false) {
        // Flux Keyboard's own windows count only when they're its app's screens, not the keyboard
        if (packageName == null || packageName in IGNORED || (packageName == context.packageName && needsConfirming)) return
        val app = context.applicationContext
        // Recents carries on in the last app's density, until that app is swiped away
        if (packageName in quickstep(app) && !needsConfirming) {
            pending?.let { handler.removeCallbacks(it) }
            watchRecents(app)
            return
        }
        stopRecentsWatch()
        val perApp = apps(app)
        // Nothing set per app: leave the screen as it is
        if (perApp.isEmpty() && applied == null) return
        pending?.let { handler.removeCallbacks(it) }
        pending = Runnable {
            Thread {
                if (!AdbShell.available()) return@Thread
                // The app Android actually has in front: a widget updating on a home screen
                // (a calendar or weather widget in Niagara or the stock launcher) reports the
                // widget's own app, which isn't in front at all
                val front = resumedPackage() ?: if (needsConfirming) return@Thread else packageName
                if (front in IGNORED || front in quickstep(app)) return@Thread
                val want = apps(app)[front] ?: base(app)
                appliedFor = front.takeIf { apps(app)[it] != null }
                if (want == applied) return@Thread
                val command = if (want == BASE_RESET) {
                    "wm density reset"
                } else {
                    val preset = ScreenDensity.Preset.byAction(want) ?: return@Thread
                    "wm density ${ScreenDensity.dpiFor(app, preset) ?: return@Thread}"
                }
                AdbShell.send(command)
                applied = want
            }.start()
        }.also { handler.postDelayed(it, SETTLE_MS) }
    }

    /**
     * Recents in front: once a second (for half a minute at most) it checks whether the app whose
     * screen size is in use is still among the recent apps; swiped away, the base comes back.
     * Another app coming to the front ends the watch (its own size applies then).
     */
    private fun watchRecents(app: Context) {
        stopRecentsWatch()
        val owner = appliedFor ?: return
        var checks = 0
        val check = object : Runnable {
            override fun run() {
                val again = this
                Thread {
                    if (!AdbShell.available() || appliedFor != owner) return@Thread
                    val front = resumedPackage()
                    if (front != null && front !in quickstep(app)) return@Thread
                    val open = AdbShell.run(
                        "dumpsys activity recents | grep -c -F -e '$owner/' -e ':$owner' || true", 2_000
                    )?.trim()?.toIntOrNull() ?: return@Thread
                    if (open == 0) {
                        val base = base(app)
                        val command = if (base == BASE_RESET) "wm density reset" else {
                            val preset = ScreenDensity.Preset.byAction(base) ?: return@Thread
                            "wm density ${ScreenDensity.dpiFor(app, preset) ?: return@Thread}"
                        }
                        AdbShell.send(command)
                        applied = base
                        appliedFor = null
                    } else if (++checks < RECENTS_CHECKS) {
                        handler.postDelayed(again, RECENTS_CHECK_MS)
                    }
                }.start()
            }
        }
        recentsWatch = check
        handler.postDelayed(check, RECENTS_CHECK_MS)
    }

    private fun stopRecentsWatch() {
        recentsWatch?.let { handler.removeCallbacks(it) }
        recentsWatch = null
    }

    private val resumed = Regex("(?:topResumedActivity|mResumedActivity|ResumedActivity)[^\\n]*?\\s([\\w.]+)/")

    /** The package of the activity Android has resumed, asked through the shell. */
    internal fun resumedPackage(): String? =
        AdbShell.run("dumpsys activity activities | grep -m 2 -E 'topResumedActivity|mResumedActivity'", 2_000)
            ?.let { parseResumed(it) }

    internal fun parseResumed(dump: String): String? = resumed.find(dump)?.groupValues?.get(1)
}
