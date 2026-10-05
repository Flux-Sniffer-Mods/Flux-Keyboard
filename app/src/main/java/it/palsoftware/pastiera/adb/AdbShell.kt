package it.palsoftware.pastiera.adb

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * Commands run as the ADB shell, through Shizuku (no root needed): the keyboard light, ADB
 * shortcuts. Only while Shizuku is running and has allowed Flux Keyboard.
 */
object AdbShell {
    private const val TAG = "FluxAdb"

    /** Shizuku is running and has allowed Flux Keyboard. */
    fun available(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private fun newProcess(command: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(null, command, null, null) as Process
    }

    /** Runs [command] in the ADB shell; its output, or null when it failed or took over [timeoutMs]. */
    fun run(command: String, timeoutMs: Long = 4_000): String? {
        if (!available()) return null
        return runCatching {
            val process = newProcess(arrayOf("sh", "-c", command))
            val output = StringBuilder()
            val reader = Thread {
                runCatching { output.append(process.inputStream.bufferedReader().readText()) }
            }.apply { isDaemon = true; start() }
            // Shizuku's process has a timed wait of its own: Android's default one asks
            // exitValue() until it stops throwing, and through Shizuku "still running" arrives as
            // another exception, so every command looked like it failed
            val finished = (process as? rikka.shizuku.ShizukuRemoteProcess)
                ?.waitForTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                ?: process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                return null
            }
            reader.join(500)
            if (process.exitValue() != 0) {
                Log.w(TAG, "command failed (${process.exitValue()})")
                null
            } else output.toString()
        }.onFailure { Log.w(TAG, "command couldn't run: $it") }.getOrNull()
    }

    /**
     * One ADB shell kept open for commands sent often (the keyboard light following the screen):
     * each command is a line on its stdin, so nothing new is started per command. Reopened when
     * it has died.
     */
    private var session: Process? = null
    private var sessionInput: java.io.Writer? = null

    @Synchronized
    fun send(command: String): Boolean {
        if (!available()) return false
        return runCatching {
            val alive = session?.let { runCatching { it.exitValue(); false }.getOrDefault(true) } == true
            if (!alive) {
                val process = newProcess(arrayOf("sh"))
                // Nothing is read back: drain the output so the shell never blocks on it
                listOf(process.inputStream, process.errorStream).forEach { stream ->
                    Thread { runCatching { val buffer = ByteArray(512); while (stream.read(buffer) >= 0) Unit } }
                        .apply { isDaemon = true; start() }
                }
                session = process
                sessionInput = process.outputStream.bufferedWriter()
            }
            sessionInput!!.apply { write(command); write(" >/dev/null 2>&1\n"); flush() }
            true
        }.getOrElse {
            Log.w(TAG, "shell session failed: $it")
            session = null
            sessionInput = null
            false
        }
    }

    /**
     * Starts [script] as a long-running ADB shell process (a loop), its output drained. Null when
     * it couldn't start or ended at once (exited within [settleMs], as when it finds nothing to do).
     */
    fun startLoop(script: String, settleMs: Long = 400): Process? {
        if (!available()) return null
        return runCatching {
            val process = newProcess(arrayOf("sh", "-c", script))
            listOf(process.inputStream, process.errorStream).forEach { stream ->
                Thread { runCatching { val buffer = ByteArray(256); while (stream.read(buffer) >= 0) Unit } }
                    .apply { isDaemon = true; start() }
            }
            val ended = (process as? rikka.shizuku.ShizukuRemoteProcess)
                ?.waitForTimeout(settleMs, TimeUnit.MILLISECONDS) ?: false
            if (ended) null else process
        }.onFailure { Log.w(TAG, "loop couldn't start: $it") }.getOrNull()
    }

    /** Runs [command] off the main thread. */
    fun runAsync(command: String, onDone: (String?) -> Unit = {}) {
        Thread { onDone(run(command)) }.start()
    }
}
