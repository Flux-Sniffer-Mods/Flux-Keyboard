package it.palsoftware.pastiera.shortcuts

import android.content.Intent
import android.net.Uri

/** The intent for [packageName] itself, started from the keyboard (so in a new task). */
fun AppIntent.toIntent(packageName: String): Intent {
    val spec = this
    return Intent(spec.action).apply {
        val uri = spec.data?.let { Uri.parse(it) }
        when {
            uri != null && spec.type != null -> setDataAndType(uri, spec.type)
            uri != null -> setData(uri)
            spec.type != null -> setType(spec.type)
        }
        spec.categories.forEach { addCategory(it) }
        setPackage(packageName)
        // An empty share: the app opens its compose screen with nothing filled in
        if (spec.action == AppIntent.ACTION_SEND) putExtra(Intent.EXTRA_TEXT, "")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
