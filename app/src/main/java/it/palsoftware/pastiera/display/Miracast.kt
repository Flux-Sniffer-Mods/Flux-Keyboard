package it.palsoftware.pastiera.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import it.palsoftware.pastiera.FluxLog
import it.palsoftware.pastiera.adb.AdbShell

/**
 * Miracast: the phone's screen, with its sound, on a TV or PC that takes Miracast (LG Screen
 * Share, Samsung Screen Mirroring, Windows' Wireless Display), with no app on the TV. Android
 * does the casting; the shell ([WifiDisplayServer]) finds screens and connects, as only the
 * system and the shell may. While casting the phone is mirrored, and an app put on the TV
 * ([ExtraScreen.bring] with [displayId]) shows there alone. Blocking calls, off the main thread.
 */
object Miracast {
    private const val TAG = "Miracast"

    /** WifiDisplayStatus.FEATURE_STATE_UNAVAILABLE: this phone's Android has no Miracast. */
    const val FEATURE_UNAVAILABLE = 0
    /** WifiDisplayStatus.FEATURE_STATE_ON. */
    const val FEATURE_ON = 3
    /** WifiDisplayStatus.DISPLAY_STATE_CONNECTED. */
    const val CONNECTED = 2

    /** Display.TYPE_WIFI. */
    private const val TYPE_WIFI = 3

    data class Screen(val address: String, val name: String, val available: Boolean, val canConnect: Boolean, val remembered: Boolean)

    data class Status(val feature: Int, val active: Int, val activeAddress: String?, val screens: List<Screen>) {
        val connected get() = active == CONNECTED
        /** Screens to offer: the ones a scan just found, then the rest remembered. */
        val offered get() = screens.filter { it.available }.sortedBy { it.name.lowercase() } +
            screens.filter { !it.available && it.remembered }.sortedBy { it.name.lowercase() }
    }

    /** [WifiDisplayServer]'s lines read back; null when it said nothing it should. */
    internal fun parse(output: String): Status? {
        var status: Status? = null
        val screens = mutableListOf<Screen>()
        for (line in output.lineSequence()) {
            val parts = line.trimEnd('\r').split('\t')
            when (parts.firstOrNull()) {
                "state" -> status = Status(
                    feature = parts.getOrNull(1)?.toIntOrNull() ?: FEATURE_UNAVAILABLE,
                    active = parts.getOrNull(3)?.toIntOrNull() ?: 0,
                    activeAddress = parts.getOrNull(4)?.takeIf { it.isNotBlank() },
                    screens = emptyList()
                )
                "screen" -> if (parts.size >= 6 && parts[1].isNotBlank()) screens += Screen(
                    address = parts[1],
                    name = parts[2].ifBlank { parts[1] },
                    available = parts[3] == "1",
                    canConnect = parts[4] == "1",
                    remembered = parts[5] == "1"
                )
            }
        }
        // The same screen once (found by a scan and remembered alike)
        return status?.copy(screens = screens.distinctBy { it.address })
    }

    private fun helper(context: Context, args: String, timeoutMs: Long): Status? {
        val apk = context.applicationContext.applicationInfo.sourceDir
        val out = AdbShell.run("CLASSPATH='$apk' app_process /system/bin ${WifiDisplayServer::class.java.name} $args", timeoutMs)
        val status = out?.let(::parse)
        if (status == null) FluxLog.w(TAG, "$args: ${out?.trim()}")
        return status
    }

    /** Wi-Fi display switched on in Android's settings (off, it finds nothing). */
    private fun switchOn() {
        AdbShell.run("settings put global wifi_display_on 1")
    }

    fun status(context: Context): Status? = helper(context, "status", 6_000)

    /** Looks for screens for [seconds]; null when the shell isn't running. */
    fun scan(context: Context, seconds: Int = 8): Status? {
        switchOn()
        return helper(context, "scan $seconds", (seconds + 6) * 1000L)
    }

    /** Connects to the screen at [address], waiting up to [seconds] for it (the TV may ask first). */
    fun connect(context: Context, address: String, seconds: Int = 40): Status? {
        switchOn()
        val safe = address.filter { it.isLetterOrDigit() || it == ':' }
        return helper(context, "connect $safe $seconds", (seconds + 6) * 1000L)
    }

    fun disconnect(context: Context): Status? = helper(context, "disconnect", 6_000)

    /** The Miracast screen Android has now, as a display: its ID; null when not casting. */
    fun display(context: Context): Display? {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return null
        return manager.displays.firstOrNull { display ->
            display.displayId != Display.DEFAULT_DISPLAY &&
                runCatching { Display::class.java.getMethod("getType").invoke(display) as Int }.getOrNull() == TYPE_WIFI
        }
    }

    fun displayId(context: Context): Int? = display(context)?.displayId
}
