package it.palsoftware.pastiera.shortcuts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import it.palsoftware.pastiera.R

/**
 * One command to paste into Termux that sets it up for the keyboard: other apps may run commands
 * (the quick launcher's scripts need it, and Termux won't take it from an app until it's set),
 * and, for a phone with a keyboard like the Titan 2 Elite, no extra-keys row or its on-screen text
 * entry box. Each setting replaces any line of its own in
 * ~/.termux/termux.properties, so pasting it again changes nothing.
 */
object TermuxSetup {
    private val PROPERTIES = listOf(
        "allow-external-apps" to "true",
        // The extra-keys row and its text entry box (a swipe on the row) aren't needed with keys
        "extra-keys" to "[]"
    )

    val COMMAND: String = buildString {
        append("f=~/.termux/termux.properties; mkdir -p ~/.termux; touch \"\$f\"; ")
        PROPERTIES.forEach { (key, value) ->
            append("sed -i '/^[#[:space:]]*$key[[:space:]]*=/d' \"\$f\"; ")
            append("echo '$key = $value' >> \"\$f\"; ")
        }
        append("termux-reload-settings; echo 'Termux is set up for Flux Keyboard'")
    }

    fun installed(context: Context): Boolean =
        runCatching { context.packageManager.getApplicationInfo(UserShortcuts.TERMUX_PACKAGE, 0) }.isSuccess

    /** Copies [COMMAND] and opens Termux, ready to paste it with Ctrl+Alt+V or a long press. */
    fun copyAndOpen(context: Context) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Termux setup", COMMAND))
        Toast.makeText(context, R.string.termux_setup_copied, Toast.LENGTH_LONG).show()
        context.packageManager.getLaunchIntentForPackage(UserShortcuts.TERMUX_PACKAGE)
            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let { intent -> runCatching { context.startActivity(intent) } }
    }
}
