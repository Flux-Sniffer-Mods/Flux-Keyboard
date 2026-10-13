package it.palsoftware.pastiera

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Warnings from gaming mode, streaming and game launches, kept in a file of the app's own as
 * well as Android's log: the phone's log turns over in seconds, so by the time a crash log is
 * saved they'd be gone. The crash log adds the file.
 */
object FluxLog {
    private const val MAX_BYTES = 256 * 1024
    @Volatile private var file: File? = null
    private val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        file = context.getExternalFilesDir(null)?.let { File(it, FILE_NAME) }
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        val out = file ?: return
        synchronized(this) {
            runCatching {
                if (out.length() > MAX_BYTES) {
                    // The newer half kept
                    val kept = out.readText().takeLast(MAX_BYTES / 2)
                    out.writeText(kept.substringAfter('\n'))
                }
                out.appendText("${time.format(Date())} $tag: $message\n")
            }
        }
    }

    const val FILE_NAME = "flux-log.txt"
}
