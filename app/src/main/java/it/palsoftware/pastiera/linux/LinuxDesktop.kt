package it.palsoftware.pastiera.linux

import android.content.Context
import android.content.Intent
import it.palsoftware.pastiera.FluxLog
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.adb.AdbShell
import it.palsoftware.pastiera.adb.RootShell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * The Linux desktop: Debian or Ubuntu, with Xfce or KDE Plasma, run by the built-in shell. With root (Shell access through
 * su, any root manager) it's a chroot, at full speed; without, PRoot (built into the app) runs it
 * as the shell user, slower but needing nothing. Its picture is a VNC server on the phone itself
 * (127.0.0.1:5901, a fresh password each start), shown by [LinuxViewerActivity] on the phone or
 * on a TV screen. Blocking calls run on threads of their own; [state] says how it's going.
 */
object LinuxDesktop {
    private const val TAG = "FluxLinux"
    private const val KEY_MODE = "linux_mode"
    private const val KEY_PASSWORD = "linux_vnc_password"
    const val PORT = 5901
    private const val KEY_DISTRO = "linux_distro"
    private const val KEY_DESKTOP = "linux_desktop"
    private const val DEBIAN_URL =
        "https://raw.githubusercontent.com/debuerreotype/docker-debian-artifacts/dist-arm64v8/trixie/oci/blobs/rootfs.tar.gz"
    /** Ubuntu's base images, one per point release: the newest is picked from the folder. */
    private const val UBUNTU_FOLDER = "https://cdimage.ubuntu.com/ubuntu-base/releases/resolute/release/"

    enum class Mode { PROOT, CHROOT }

    /** The Linux installed: its name as shown, and as the scripts know it. */
    enum class Distro(val label: String, val id: String) { DEBIAN("Debian 13", "debian"), UBUNTU("Ubuntu 26.04", "ubuntu") }

    /** Its desktop: as shown, as the scripts know it, and as .desktop files name it (OnlyShowIn). */
    enum class Desktop(val label: String, val id: String, val xdg: String) { XFCE("Xfce", "xfce", "XFCE"), KDE("KDE Plasma", "kde", "KDE") }

    /** The Linux chosen (or installed): Debian unless Ubuntu was picked. */
    fun distro(context: Context): Distro =
        prefs(context).getString(KEY_DISTRO, null)?.let { runCatching { Distro.valueOf(it) }.getOrNull() } ?: Distro.DEBIAN

    fun desktop(context: Context): Desktop =
        prefs(context).getString(KEY_DESKTOP, null)?.let { runCatching { Desktop.valueOf(it) }.getOrNull() } ?: Desktop.XFCE

    sealed class State {
        object Checking : State()
        object NotInstalled : State()
        data class Downloading(val percent: Int) : State()
        object Installing : State()
        object Stopped : State()
        object Starting : State()
        object Running : State()
        object Stopping : State()
        data class Failed(val step: Step) : State()
    }

    enum class Step { NO_SHELL, NO_ROOT, DOWNLOAD, INSTALL, START }

    private val mutableState = MutableStateFlow<State>(State.Checking)
    val state: StateFlow<State> = mutableState
    /** The last lines the install or the desktop printed, for a look when something fails. */
    private val mutableLog = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = mutableLog

    @Volatile private var session: Process? = null

    private fun set(value: State) { mutableState.value = value }

    private fun line(text: String) {
        mutableLog.value = (mutableLog.value + text).takeLast(200)
    }

    private fun prefs(context: Context) = SettingsManager.getPreferences(context)

    /** How it's installed; before that, chroot where root is on, else PRoot. */
    fun mode(context: Context): Mode = prefs(context).getString(KEY_MODE, null)?.let { runCatching { Mode.valueOf(it) }.getOrNull() }
        ?: if (RootShell.active) Mode.CHROOT else Mode.PROOT

    fun installedMode(context: Context): Mode? = prefs(context).getString(KEY_MODE, null)?.let { runCatching { Mode.valueOf(it) }.getOrNull() }

    /** What runs it is there: root for a chroot, the shell for PRoot. */
    fun canRun(mode: Mode): Boolean = if (mode == Mode.CHROOT) RootShell.active else AdbShell.available()

    private fun base(mode: Mode) = if (mode == Mode.CHROOT) "/data/local/flux-linux" else "/data/local/tmp/flux-linux"

    private fun files(context: Context): File = File(context.getExternalFilesDir(null), "linux").apply { mkdirs() }

    private fun tarball(context: Context, distro: Distro = distro(context)) = File(files(context), "${distro.id}-rootfs.tar.gz")

    /** A file of the app's as the shell (or root, through Android's own storage) reaches it. */
    private fun reach(file: File, mode: Mode): String {
        val path = file.absolutePath
        return if (mode == Mode.CHROOT) path.replace(Regex("^/storage/emulated/(\\d+)/"), "/data/media/$1/") else path
    }

    /** The scripts, copied where the shell can read them. */
    private fun scripts(context: Context): Pair<File, File> {
        val dir = files(context)
        fun copy(name: String) = File(dir, name).also { out ->
            context.assets.open("fork/linux/$name").use { input -> out.outputStream().use { input.copyTo(it) } }
        }
        return copy("flux-linux.sh") to copy("flux-guest.sh")
    }

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private fun command(context: Context, mode: Mode, action: String, extra: Map<String, String> = emptyMap()): String {
        val (script, guest) = scripts(context)
        val env = linkedMapOf(
            "MODE" to if (mode == Mode.CHROOT) "chroot" else "proot",
            "BASE" to base(mode),
            "TARBALL" to reach(tarball(context), mode),
            "LIBDIR" to context.applicationInfo.nativeLibraryDir,
            "GUEST" to reach(guest, mode),
            "DISTRO" to distro(context).id,
            "DESKTOP" to desktop(context).id,
            // The phone's language and time zone, carried into Linux
            "LOCALE" to java.util.Locale.getDefault().let { if (it.country.isNotEmpty()) "${it.language}_${it.country}" else it.language },
            "TZNAME" to java.util.TimeZone.getDefault().id,
            "SCALE" to scale(context).toString(),
            "GPU" to if (gpu(context)) "1" else "0"
        ) + extra
        return env.entries.joinToString(" ") { "${it.key}=${quote(it.value)}" } + " sh ${quote(reach(script, mode))} $action 2>&1"
    }

    /** Runs [action], each line it prints to [log]; its exit status (-1 when it couldn't start). */
    private fun run(context: Context, mode: Mode, action: String, extra: Map<String, String> = emptyMap(), keep: Boolean = false): Int {
        return runCatching {
            val process = AdbShell.newProcess(arrayOf("sh", "-c", command(context, mode, action, extra)))
            if (keep) session = process
            process.inputStream.bufferedReader().forEachLine { text ->
                line(text)
                if (text == "flux: starting") set(State.Starting)
            }
            process.waitFor()
        }.onFailure { FluxLog.w(TAG, "$action: $it") }.getOrDefault(-1)
    }

    private fun background(work: () -> Unit) = Thread({
        runCatching(work).onFailure { FluxLog.w(TAG, "$it") }
    }, "FluxLinux").apply { isDaemon = true; start() }

    /** [action]'s output, through the shell; null when it failed. */
    internal fun runForOutput(context: Context, mode: Mode, action: String, timeoutMs: Long = 30_000): String? =
        AdbShell.run(command(context, mode, action), timeoutMs)

    /** The ends of Linux's own logs (the VNC server, the session, sound): what to look at when it won't show. */
    fun guestLogs(context: Context): String? {
        val mode = installedMode(context) ?: return null
        return runForOutput(context.applicationContext, mode, "logs", 8_000)?.trim()
    }

    /** Opens [exec] in the running desktop ([terminal]: in a terminal). Blocking, briefly. */
    fun launch(context: Context, exec: String, terminal: Boolean) {
        val mode = installedMode(context) ?: return
        val said = AdbShell.run(command(context, mode, "launch", mapOf("EXEC" to exec, "TERMINAL" to if (terminal) "1" else "0")), 10_000)
        if (said?.contains("launched") != true) FluxLog.w(TAG, "launch $exec: ${said?.trim()}")
    }

    /** Looks again: installed, running or neither. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        background {
            val mode = installedMode(app)
            if (mode == null) { set(State.NotInstalled); return@background }
            if (!canRun(mode)) { set(State.Failed(if (mode == Mode.CHROOT) Step.NO_ROOT else Step.NO_SHELL)); return@background }
            val out = AdbShell.run(command(app, mode, "status"), 8_000).orEmpty()
            set(when {
                "not-installed" in out -> State.NotInstalled
                "running" in out -> State.Running
                else -> State.Stopped
            })
            if (state.value == State.Running) LinuxNotifications.watch(app)
        }
    }

    /**
     * Downloads [distro] (once) and sets [desktop] up on it in [mode]: some minutes, most of it
     * downloading and installing packages.
     */
    fun install(context: Context, mode: Mode, distro: Distro = Distro.DEBIAN, desktop: Desktop = Desktop.XFCE) {
        val app = context.applicationContext
        if (!canRun(mode)) { set(State.Failed(if (mode == Mode.CHROOT) Step.NO_ROOT else Step.NO_SHELL)); return }
        prefs(app).edit().putString(KEY_DISTRO, distro.name).putString(KEY_DESKTOP, desktop.name).apply()
        mutableLog.value = emptyList()
        LinuxService.start(app)
        background {
            try {
                if (!download(app)) { set(State.Failed(Step.DOWNLOAD)); return@background }
                set(State.Installing)
                val result = run(app, mode, "install")
                if (result != 0) { set(State.Failed(Step.INSTALL)); return@background }
                prefs(app).edit().putString(KEY_MODE, mode.name).apply()
                // The download's no longer needed: Linux is unpacked
                tarball(app).delete()
                set(State.Stopped)
            } finally {
                LinuxService.idle(app)
            }
        }
    }

    private fun download(context: Context): Boolean {
        val target = tarball(context)
        if (target.isFile && target.length() > 10_000_000) return true
        val part = File(target.path + ".part")
        set(State.Downloading(0))
        val distro = distro(context)
        line("flux: downloading ${distro.label}")
        return runCatching {
            val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
            val url = when (distro) {
                Distro.DEBIAN -> DEBIAN_URL
                Distro.UBUNTU -> UBUNTU_FOLDER + newestUbuntu(client.newCall(Request.Builder().url(UBUNTU_FOLDER).build()).execute()
                    .use { it.body?.string().orEmpty() })
            }
            // A download cut short goes on from where it stopped
            val have = if (part.isFile) part.length() else 0L
            val request = Request.Builder().url(url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                val resumed = response.code == 206
                val body = response.body ?: throw IllegalStateException("empty")
                val total = (if (resumed) have else 0L) + body.contentLength()
                var done = if (resumed) have else 0L
                java.io.FileOutputStream(part, resumed).use { out ->
                    val buffer = ByteArray(1 shl 16)
                    val input = body.byteStream()
                    var lastPercent = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        val percent = if (total > 0) (done * 100 / total).toInt() else 0
                        if (percent != lastPercent) { lastPercent = percent; set(State.Downloading(percent)) }
                    }
                }
            }
            part.renameTo(target)
        }.onFailure { line("flux: download failed: $it"); FluxLog.w(TAG, "download: $it") }.getOrDefault(false)
    }

    private const val KEY_SCALE = "linux_scale"
    val SCALES = listOf(1.0f, 1.25f, 1.5f, 2.0f)

    /** How much bigger everything is drawn, for a phone's small screen (from the next start). */
    fun scale(context: Context): Float = prefs(context).getFloat(KEY_SCALE, 1.5f).takeIf { it in SCALES } ?: 1.5f

    fun setScale(context: Context, value: Float) = prefs(context).edit().putFloat(KEY_SCALE, value).apply()

    private const val KEY_GPU = "linux_gpu"

    /** Programs in Linux draw on the phone's GPU (through virglrenderer), from the next start. */
    fun gpu(context: Context): Boolean = prefs(context).getBoolean(KEY_GPU, true)

    fun setGpu(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_GPU, on).apply()

    /** The VNC password for this run (new each start). */
    fun password(context: Context): String = prefs(context).getString(KEY_PASSWORD, null).orEmpty()

    /** Starts the desktop at [width] x [height]; [onReady] (on its own thread) once its picture can be seen. */
    fun start(context: Context, width: Int, height: Int, onReady: () -> Unit = {}) {
        val app = context.applicationContext
        val mode = installedMode(app) ?: return
        if (!canRun(mode)) { set(State.Failed(if (mode == Mode.CHROOT) Step.NO_ROOT else Step.NO_SHELL)); return }
        val alphabet = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        val password = String(CharArray(8) { alphabet[random.nextInt(alphabet.length)] })
        prefs(app).edit().putString(KEY_PASSWORD, password).apply()
        mutableLog.value = emptyList()
        set(State.Starting)
        LinuxService.start(app)
        background {
            run(app, mode, "start", mapOf("GEOMETRY" to "${width and 1.inv()}x${height and 1.inv()}", "PASSWORD" to password), keep = true)
            session = null
            // Ended before its picture ever came: it couldn't start (the log says why, Linux's own too)
            if (state.value == State.Starting) {
                guestLogs(app)?.lineSequence()?.forEach { line(it) }
                set(State.Failed(Step.START))
            } else set(State.Stopped)
            LinuxService.idle(app)
        }
        // Ready once the VNC server answers, however long an update first takes (a failed start ends it)
        background {
            while (state.value == State.Starting) {
                if (listening()) {
                    set(State.Running)
                    LinuxService.idle(app)
                    LinuxNotifications.watch(app)
                    onReady()
                    return@background
                }
                Thread.sleep(500)
            }
        }
    }

    /** The desktop's VNC server answers. */
    fun listening(): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", PORT), 500); true }
    }.getOrDefault(false)

    fun stop(context: Context) {
        val app = context.applicationContext
        val mode = installedMode(app) ?: return
        set(State.Stopping)
        background {
            run(app, mode, "stop")
            runCatching { session?.destroy() }
            session = null
            set(State.Stopped)
            LinuxService.idle(app)
        }
    }

    /** The newest arm64 base image in Ubuntu's release folder's listing. */
    internal fun newestUbuntu(listing: String): String =
        Regex("ubuntu-base-(26\\.04(?:\\.\\d+)?)-base-arm64\\.tar\\.gz").findAll(listing)
            .maxByOrNull { match -> match.groupValues[1].split('.').map { it.toIntOrNull() ?: 0 }.let { it.getOrElse(2) { 0 } } }
            ?.value ?: throw IllegalStateException("no Ubuntu image listed")

    /** Deletes Linux and everything in it (the phone's own files, mounted there, are left alone). */
    fun remove(context: Context) {
        val app = context.applicationContext
        val mode = installedMode(app) ?: return
        set(State.Stopping)
        background {
            val result = run(app, mode, "remove")
            if (result == 0) {
                prefs(app).edit().remove(KEY_MODE).remove(KEY_DISTRO).remove(KEY_DESKTOP).apply()
                for (distro in Distro.values()) {
                    tarball(app, distro).delete()
                    File(tarball(app, distro).path + ".part").delete()
                }
                set(State.NotInstalled)
            } else set(State.Stopped)
        }
    }

    /** The desktop's picture opened: on [displayId] (a TV screen), or this phone's. */
    fun show(context: Context, displayId: Int? = null) {
        val intent = Intent(context, LinuxViewerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (displayId == null) context.startActivity(intent)
        else Thread { runCatching { it.palsoftware.pastiera.display.ExtraScreen.bring(context.applicationContext, intent, displayId) } }.start()
    }
}
