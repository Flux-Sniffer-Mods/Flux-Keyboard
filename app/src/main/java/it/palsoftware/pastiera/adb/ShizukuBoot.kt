package it.palsoftware.pastiera.adb

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import it.palsoftware.pastiera.R

/**
 * Shizuku starting itself at boot, without root: Shizuku can turn wireless debugging on and
 * start its service at boot once it holds WRITE_SECURE_SETTINGS. While Shizuku is running, the
 * ADB shell can grant it that, so it's one tap here, then Start on boot in Shizuku's settings.
 */
object ShizukuBoot {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"

    fun installed(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) }.isSuccess

    /** Shizuku already holds the permission it needs to start at boot. */
    fun granted(context: Context): Boolean =
        context.packageManager.checkPermission(PERMISSION, SHIZUKU_PACKAGE) == PackageManager.PERMISSION_GRANTED

    /** Grants Shizuku the permission (off the main thread), then opens Shizuku to switch it on. */
    fun setUp(context: Context, onDone: (Boolean) -> Unit) {
        val app = context.applicationContext
        Thread {
            if (!granted(app)) AdbShell.run("pm grant $SHIZUKU_PACKAGE $PERMISSION")
            val ok = granted(app)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(
                    app,
                    if (ok) R.string.shizuku_boot_granted else R.string.shizuku_boot_failed,
                    Toast.LENGTH_LONG
                ).show()
                if (ok) {
                    app.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?.let { runCatching { app.startActivity(it) } }
                }
                onDone(ok)
            }
        }.start()
    }
}
