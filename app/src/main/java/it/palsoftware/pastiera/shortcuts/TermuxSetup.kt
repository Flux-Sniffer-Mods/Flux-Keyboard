package it.palsoftware.pastiera.shortcuts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import it.palsoftware.pastiera.R

/**
 * One command to paste into Termux that sets it up for the keyboard (assets/fork/termux/setup.sh):
 * other apps may run commands (the quick launcher's scripts need it, and Termux won't take it
 * from an app until it's set), and, for a phone with a keyboard like the Titan 2 Elite, no
 * extra-keys row or its on-screen text entry box. Each setting replaces any line of its own in
 * ~/.termux/termux.properties, so pasting it again changes nothing. For Shizuku users, a second
 * command has Termux:Boot start Shizuku after a restart (assets/fork/termux/shizuku-boot.sh and
 * start-shizuku.sh): adb, the boot script and the one-time pairing with wireless debugging. The
 * built-in shell needs neither.
 */
object TermuxSetup {
    private const val ASSETS = "fork/termux"
    private const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"

    private fun asset(context: Context, name: String): String =
        context.assets.open("$ASSETS/$name").use { it.readBytes() }
            .let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }

    /** The scripts, carried in the command itself (Termux can't read the app's files). */
    fun command(context: Context, shizuku: Boolean = false): String =
        "d=\"\$PREFIX/tmp/flux-keyboard\"; mkdir -p \"\$d\"; " + if (shizuku) {
            "echo '${asset(context, "start-shizuku.sh")}' | base64 -d > \"\$d/start-shizuku.sh\"; " +
                "echo '${asset(context, "shizuku-boot.sh")}' | base64 -d > \"\$d/shizuku-boot.sh\"; " +
                "FLUX_START_SHIZUKU=\"\$d/start-shizuku.sh\" sh \"\$d/shizuku-boot.sh\""
        } else {
            "echo '${asset(context, "setup.sh")}' | base64 -d > \"\$d/setup.sh\"; sh \"\$d/setup.sh\""
        }

    fun installed(context: Context): Boolean =
        runCatching { context.packageManager.getApplicationInfo(UserShortcuts.TERMUX_PACKAGE, 0) }.isSuccess

    /** Copies [command] and opens Termux, ready to paste it with Ctrl+Alt+V or a long press. */
    fun copyAndOpen(context: Context, shizuku: Boolean = false) {
        // Shizuku running: Termux may switch wireless debugging on at boot, to start Shizuku
        if (shizuku && it.palsoftware.pastiera.adb.AdbShell.available()) {
            it.palsoftware.pastiera.adb.AdbShell.runAsync("pm grant ${UserShortcuts.TERMUX_PACKAGE} $WRITE_SECURE_SETTINGS")
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Termux setup", command(context, shizuku)))
        Toast.makeText(context, R.string.termux_setup_copied, Toast.LENGTH_LONG).show()
        context.packageManager.getLaunchIntentForPackage(UserShortcuts.TERMUX_PACKAGE)
            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let { intent -> runCatching { context.startActivity(intent) } }
    }
}
