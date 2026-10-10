package it.palsoftware.pastiera.adb

import android.content.Context
import it.palsoftware.pastiera.SettingsManager
import java.util.concurrent.TimeUnit

/**
 * Root, when the phone has it and it's switched on in Shell access: every shell command then
 * runs through su instead of the built-in shell or Shizuku (no pairing, ready at boot without
 * Wi-Fi), and gaming mode can be a real controller. Off by default.
 */
object RootShell {
    private const val KEY_ENABLED = "root_shell_enabled"

    /** Switched on, and su answered as root. */
    @Volatile var active = false
        private set

    fun enabled(context: Context): Boolean = SettingsManager.getPreferences(context).getBoolean(KEY_ENABLED, false)

    /** Reads the switch and asks su once whether it gives root (blocking: su may ask the user). */
    fun init(context: Context) {
        active = enabled(context) && granted()
    }

    fun setEnabled(context: Context, on: Boolean) {
        SettingsManager.getPreferences(context).edit().putBoolean(KEY_ENABLED, on).apply()
        active = on && granted()
    }

    /** su runs a command as root (asking the root manager the first time). */
    fun granted(): Boolean = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id -u"))
        val finished = process.waitFor(15, TimeUnit.SECONDS)
        finished && process.exitValue() == 0 && process.inputStream.bufferedReader().readText().trim() == "0"
    }.getOrDefault(false)

    /** [command] (as the shell would be given it) started through su. */
    fun newProcess(command: Array<String>): Process {
        val su = when {
            command.size == 1 && command[0] == "sh" -> arrayOf("su")
            command.size == 3 && command[0] == "sh" && command[1] == "-c" -> arrayOf("su", "-c", command[2])
            else -> arrayOf("su", "-c", command.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" })
        }
        return Runtime.getRuntime().exec(su)
    }
}
