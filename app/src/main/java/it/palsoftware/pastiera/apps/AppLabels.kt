package it.palsoftware.pastiera.apps

import android.content.Context

/** An installed app's name, as the launcher shows it; null when it isn't installed. */
fun Context.appLabelOrNull(packageName: String): String? = runCatching {
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
}.getOrNull()

/** An installed app's name, or [packageName] itself when it isn't installed. */
fun Context.appLabel(packageName: String): String = appLabelOrNull(packageName) ?: packageName
