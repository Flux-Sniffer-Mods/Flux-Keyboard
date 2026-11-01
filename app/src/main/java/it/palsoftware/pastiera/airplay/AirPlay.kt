package it.palsoftware.pastiera.airplay

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import it.palsoftware.pastiera.FluxLog
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.adb.AdbShell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * AirPlay: the phone's screen and sound on an Apple TV, or a TV that takes AirPlay, with no app
 * there. The TV is paired with (transiently, or with the PIN it shows, kept after), then told to
 * play the phone's live stream ([HlsServer]): a few seconds behind, which suits apps and video
 * more than fast games. No FairPlay screen mirroring: what Apple keeps to itself isn't needed.
 */
object AirPlay {
    private const val TAG = "AirPlay"
    private const val KEY_PAIRED = "airplay_paired_"

    sealed class State {
        object Idle : State()
        data class Connecting(val name: String) : State()
        /** The TV shows a PIN: [pin] with it. [wrong] after one that didn't match. */
        data class Pin(val name: String, val wrong: Boolean = false) : State()
        data class Starting(val name: String) : State()
        data class Casting(val name: String) : State()
        data class Failed(val name: String, val refused: Boolean) : State()
    }

    private val mutableState = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = mutableState

    val casting: Boolean get() = state.value is State.Casting

    @Volatile private var receiver: AirPlayReceiver? = null
    @Volatile private var session: AirPlaySession? = null
    @Volatile private var pinPairing: Pair<AirPlayConnection, HapPairing>? = null
    @Volatile private var media: AirPlayMedia? = null
    @Volatile private var server: HlsServer? = null
    @Volatile private var projection: MediaProjection? = null
    @Volatile private var generation = 0

    private fun set(value: State) { mutableState.value = value }

    private fun background(work: () -> Unit) = Thread({
        runCatching(work).onFailure { FluxLog.w(TAG, "$it") }
    }, "AirPlay").apply { isDaemon = true; start() }

    /** Connects to [target] and pairs; then the screen capture is asked for and it plays. */
    fun cast(context: Context, target: AirPlayReceiver) {
        val app = context.applicationContext
        stop(app)
        val run = ++generation
        receiver = target
        set(State.Connecting(target.name))
        background {
            val made = runCatching { connect(app, target) }
            if (run != generation) { made.getOrNull()?.close(); return@background }
            made.onSuccess { ready ->
                if (ready != null) { session = ready; capture(app) }
                // Otherwise the PIN's asked for
            }.onFailure { e ->
                FluxLog.w(TAG, "connect ${target.name}: $e")
                fail(app, e is PairingRefused)
            }
        }
    }

    /** A paired session, or null with a PIN pairing started (the TV shows it). */
    private fun connect(context: Context, target: AirPlayReceiver): AirPlaySession? {
        val connection = AirPlayConnection.open(target.host, target.port)
        val session = AirPlaySession(target, connection)
        if (!target.pairs) return session
        val saved = HapCredentials.decode(prefs(context).getString(KEY_PAIRED + target.id, null))
        try {
            when {
                saved != null -> session.pairVerify(saved)
                target.wantsPin -> throw PairingRefused(0)
                else -> session.pairTransient()
            }
            return session
        } catch (e: PairingRefused) {
            session.close()
            // Forgotten by the TV, or it wants its own code: paired with the PIN it shows
            if (saved != null) prefs(context).edit().remove(KEY_PAIRED + target.id).apply()
        }
        val pinConnection = AirPlayConnection.open(target.host, target.port)
        val pairing = HapPairing(pinConnection)
        pairing.start()
        pinPairing = pinConnection to pairing
        set(State.Pin(target.name))
        return null
    }

    /** The PIN the TV shows, typed in: paired for good, then playing. */
    fun pin(context: Context, code: String) {
        val app = context.applicationContext
        val target = receiver ?: return
        val (pinConnection, pairing) = pinPairing ?: return
        val run = generation
        set(State.Connecting(target.name))
        background {
            try {
                val credentials = pairing.finish(code)
                pinConnection.close()
                pinPairing = null
                prefs(app).edit().putString(KEY_PAIRED + target.id, credentials.encode()).apply()
                val connection = AirPlayConnection.open(target.host, target.port)
                val paired = AirPlaySession(target, connection)
                paired.pairVerify(credentials)
                if (run != generation) { paired.close(); return@background }
                session = paired
                capture(app)
            } catch (e: PairingRefused) {
                // A wrong code ends the TV's pairing: it's asked to show a new one
                runCatching { pinConnection.close() }
                runCatching {
                    val again = AirPlayConnection.open(target.host, target.port)
                    val restart = HapPairing(again)
                    restart.start()
                    pinPairing = again to restart
                    set(State.Pin(target.name, wrong = true))
                }.onFailure { fail(app, true) }
            } catch (e: Exception) {
                FluxLog.w(TAG, "pin: $e")
                fail(app, false)
            }
        }
    }

    /** Screen capture asked for (straight through when the shell allows it ahead). */
    private fun capture(context: Context) {
        val target = receiver ?: return
        set(State.Starting(target.name))
        if (AdbShell.available()) {
            val pkg = context.packageName
            AdbShell.run("appops set $pkg PROJECT_MEDIA allow; pm grant $pkg android.permission.RECORD_AUDIO")
        }
        context.startActivity(Intent(context, AirPlayCapture::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** The capture Android allowed (from [AirPlayService]): the stream starts and the TV plays it. */
    internal fun onProjection(context: Context, capture: MediaProjection) {
        val app = context.applicationContext
        val target = receiver
        val paired = session
        if (target == null || paired == null) { capture.stop(); return }
        projection = capture
        val run = generation
        background {
            try {
                val dpi = app.resources.displayMetrics.densityDpi
                val started = AirPlayMedia(capture, dpi)
                media = started
                val stream = started.start()
                val hls = HlsServer(stream, paired.remoteAddress)
                server = hls
                // The TV starts from what's there: a few segments first
                val until = System.currentTimeMillis() + 10_000
                while (!stream.ready() && System.currentTimeMillis() < until && run == generation) Thread.sleep(100)
                if (run != generation) return@background
                val url = "http://${AirPlaySession.host(paired.localAddress)}:${hls.port}/flux.m3u8"
                if (!stream.ready() || !paired.play(url, deviceName(app))) { fail(app, false); return@background }
                set(State.Casting(target.name))
                AirPlayService.refresh(app)
                watch(app, paired, run)
            } catch (e: Exception) {
                // The encoder or the stream wouldn't start: the capture (and its notification) ends too
                FluxLog.w(TAG, "cast: $e")
                if (run == generation) fail(app, false)
            }
        }
    }

    /** Asks the TV how it's going every second, until it stops (or the phone does). */
    private fun watch(context: Context, paired: AirPlaySession, run: Int) {
        var seen = false
        var quiet = 0
        while (run == generation) {
            Thread.sleep(1_000)
            if (run != generation) return
            val info = paired.playbackInfo()
            if (info == null) break
            if (info["error"] != null) { FluxLog.w(TAG, "TV: ${info["error"]}"); break }
            val playing = info.containsKey("rate") || info.containsKey("duration") || info.containsKey("position")
            if (playing) { seen = true; quiet = 0 } else quiet++
            // Stopped on the TV: it says nothing more of what plays
            if ((seen && quiet >= 5) || (!seen && quiet >= 20)) break
        }
        if (run == generation) stop(context)
    }

    private fun fail(context: Context, refused: Boolean) {
        val name = receiver?.name.orEmpty()
        stop(context)
        set(State.Failed(name, refused))
    }

    /** Ends casting (and any pairing half done). */
    fun stop(context: Context) {
        generation++
        val app = context.applicationContext
        val ending = session
        session = null
        pinPairing?.first?.let { runCatching { it.close() } }
        pinPairing = null
        media?.let { runCatching { it.stop() } }
        media = null
        server?.let { runCatching { it.close() } }
        server = null
        // Cleared first: stopping it calls back here
        val capture = projection
        projection = null
        runCatching { capture?.stop() }
        if (ending != null) Thread { runCatching { ending.stop() } }.apply { isDaemon = true; start() }
        set(State.Idle)
        runCatching { app.stopService(Intent(app, AirPlayService::class.java)) }
    }

    /** The capture this cast uses (another one ending isn't this cast ending). */
    internal fun uses(capture: MediaProjection) = projection === capture

    /** Forgets the state a dialog showed (a failure read). */
    fun dismiss() {
        if (state.value is State.Failed) set(State.Idle)
    }

    private fun prefs(context: Context) = SettingsManager.getPreferences(context)

    private fun deviceName(context: Context): String =
        runCatching { android.provider.Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL
}
