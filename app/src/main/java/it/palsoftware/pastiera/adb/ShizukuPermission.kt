package it.palsoftware.pastiera.adb

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import it.palsoftware.pastiera.R
import rikka.shizuku.Shizuku

/**
 * Asking Shizuku to allow Flux Keyboard. Shizuku shows its question only while it's running, and
 * never again once it was refused (or on versions before 11), so each of those gets a message
 * saying what to do, with Shizuku opened, rather than a tap that does nothing.
 */
object ShizukuPermission {
    private const val REQUEST_CODE = 4207
    private const val NO_ANSWER_MS = 4000L

    fun request(context: Context, onResult: (Boolean) -> Unit = {}) {
        val app = context.applicationContext
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        when {
            !running -> fail(app, R.string.shizuku_request_not_running, onResult)
            runCatching { Shizuku.isPreV11() }.getOrDefault(false) -> fail(app, R.string.shizuku_request_old, onResult)
            granted() -> onResult(true)
            runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false) ->
                fail(app, R.string.shizuku_request_refused_before, onResult)
            else -> ask(app, onResult)
        }
    }

    fun granted(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private fun ask(app: Context, onResult: (Boolean) -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        var answered = false
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != REQUEST_CODE) return
                answered = true
                runCatching { Shizuku.removeRequestPermissionResultListener(this) }
                handler.post { onResult(grantResult == PackageManager.PERMISSION_GRANTED) }
            }
        }
        val asked = runCatching {
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(REQUEST_CODE)
        }.isSuccess
        if (!asked) {
            runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
            fail(app, R.string.shizuku_request_no_question, onResult)
            return
        }
        // Some setups never show the question: say where to allow it instead
        handler.postDelayed({
            if (!answered && !granted()) {
                runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
                fail(app, R.string.shizuku_request_no_question, onResult)
            }
        }, NO_ANSWER_MS)
    }

    private fun fail(app: Context, message: Int, onResult: (Boolean) -> Unit) {
        Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        app.packageManager.getLaunchIntentForPackage(ShizukuBoot.SHIZUKU_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let { runCatching { app.startActivity(it) } }
        onResult(false)
    }
}
