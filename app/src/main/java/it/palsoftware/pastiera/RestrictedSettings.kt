package it.palsoftware.pastiera

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.widget.Toast

/**
 * Android's restricted settings: an app installed from a file (like Flux Keyboard's releases)
 * can't have its accessibility service or notification access switched on until the person
 * allows it in App info (⋮ > Allow restricted settings). Features that need either service
 * send people to App info first while Android still blocks it, then to the service's own page.
 */
object RestrictedSettings {
    private const val OP_ACCESS_RESTRICTED_SETTINGS = "android:access_restricted_settings"

    /** Android still blocks this app's accessibility service and notification access. */
    fun blocked(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = runCatching {
            appOps.unsafeCheckOpNoThrow(OP_ACCESS_RESTRICTED_SETTINGS, Process.myUid(), context.packageName)
        }.getOrNull() ?: return false
        return mode == AppOpsManager.MODE_ERRORED || mode == AppOpsManager.MODE_IGNORED
    }

    /**
     * How to allow restricted settings, step by step, with a button to App info (where ⋮ >
     * Allow restricted settings lifts the block). Without a screen to show it on, a short hint.
     */
    fun openAppInfo(context: Context) {
        val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .firstOrNull { it is android.app.Activity } as? android.app.Activity
        if (activity == null || activity.isFinishing) {
            Toast.makeText(context, R.string.restricted_settings_toast, Toast.LENGTH_LONG).show()
            launchAppInfo(context)
            return
        }
        android.app.AlertDialog.Builder(activity)
            .setTitle(R.string.restricted_settings_help_title)
            .setMessage(R.string.restricted_settings_help_steps)
            .setPositiveButton(R.string.restricted_settings_help_open) { _, _ -> launchAppInfo(activity) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Flux Keyboard's App info, straight away (where ⋮ > Allow restricted settings is). */
    fun openAppDetails(context: Context) = launchAppInfo(context)

    private fun launchAppInfo(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Opens [action]'s page, or App info first while restricted settings block it. */
    fun openServicePage(context: Context, action: String) {
        if (blocked(context)) {
            openAppInfo(context)
            return
        }
        runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openAccessibility(context: Context) = openServicePage(context, Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun openNotificationAccess(context: Context) = openServicePage(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}
