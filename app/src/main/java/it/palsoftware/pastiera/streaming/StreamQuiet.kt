package it.palsoftware.pastiera.streaming

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import it.palsoftware.pastiera.FluxLog
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.adb.AdbShell

/**
 * The phone quiet while it streams: the game's sound only on the TV, or every sound of the
 * phone's too. Volumes are turned to nothing, not muted: the sound streamed is taken at full
 * volume whatever the phone's (Android's playback capture), while a mute can reach it too.
 * Everything is put back as it was when the stream ends, or when Flux Keyboard next starts if
 * it stopped mid-stream.
 */
object StreamQuiet {
    private const val TAG = "StreamQuiet"
    private const val KEY_MODE = "stream_quiet"
    /** What was there before ("stream:volume" pairs, then "ringer:mode"), kept until put back. */
    private const val KEY_SAVED = "stream_quiet_saved"

    enum class Mode { OFF, GAME, ALL }

    fun mode(context: Context): Mode =
        Mode.entries.firstOrNull { it.name == SettingsManager.getPreferences(context).getString(KEY_MODE, null) } ?: Mode.GAME

    fun setMode(context: Context, mode: Mode) {
        SettingsManager.getPreferences(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    /** Media for the game's sound; notifications, the ringer and the phone's own sounds too for all of them. */
    private fun streams(mode: Mode): List<Int> = when (mode) {
        Mode.OFF -> emptyList()
        Mode.GAME -> listOf(AudioManager.STREAM_MUSIC)
        Mode.ALL -> listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_SYSTEM, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_RING)
    }

    /** The stream started: the phone made quiet as set. Blocking for the shell when every sound is. */
    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext
        val prefs = SettingsManager.getPreferences(app)
        // Already quiet (a stream before this one never put it back): its saved state kept
        if (prefs.contains(KEY_SAVED)) return
        val mode = mode(app)
        if (mode == Mode.OFF) return
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        // The ringer and notifications turned to nothing needs Do Not Disturb access: given through the shell
        if (mode == Mode.ALL) {
            val policy = app.getSystemService(NotificationManager::class.java)
            if (policy?.isNotificationPolicyAccessGranted == false && AdbShell.available()) {
                AdbShell.run("cmd notification allow_dnd ${app.packageName}", 3_000)
            }
        }
        val saved = streams(mode).associateWith { audio.getStreamVolume(it) }
        prefs.edit().putString(KEY_SAVED, saved.entries.joinToString(",") { "${it.key}:${it.value}" } + ",ringer:${audio.ringerMode}").apply()
        val failed = saved.keys.filter { stream ->
            runCatching { audio.setStreamVolume(stream, 0, 0) }.isFailure
        }
        FluxLog.w(TAG, "phone quiet while streaming (${mode.name.lowercase()})" +
            if (failed.isNotEmpty()) "; couldn't quieten streams $failed" else "")
    }

    /** The stream ended (or Flux Keyboard started after stopping mid-stream): volumes put back. */
    @Synchronized
    fun stop(context: Context) {
        val app = context.applicationContext
        val prefs = SettingsManager.getPreferences(app)
        val saved = prefs.getString(KEY_SAVED, null) ?: return
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        val pairs = saved.split(',').mapNotNull { part ->
            part.substringBefore(':') to (part.substringAfter(':').toIntOrNull() ?: return@mapNotNull null)
        }
        // The ringer's mode first, so its volume comes back as it was
        pairs.firstOrNull { it.first == "ringer" }?.let { (_, ringer) -> runCatching { audio.ringerMode = ringer } }
        pairs.filter { it.first != "ringer" }.forEach { (stream, volume) ->
            stream.toIntOrNull()?.let { runCatching { audio.setStreamVolume(it, volume, 0) } }
        }
        prefs.edit().remove(KEY_SAVED).apply()
        FluxLog.w(TAG, "phone's sound put back")
    }
}
