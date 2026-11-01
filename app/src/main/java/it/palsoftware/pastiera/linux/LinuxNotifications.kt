package it.palsoftware.pastiera.linux

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import it.palsoftware.pastiera.R

/**
 * Linux's notifications in the phone's shade while the desktop runs: Linux queues each one
 * (flux-notify-bridge), the shell hands them over every few seconds, and each is posted here
 * (an update to one replacing it). A tap opens the desktop.
 */
object LinuxNotifications {
    private const val CHANNEL = "linux_notifications"
    private const val EVERY_MS = 3_000L

    data class Note(val tag: String, val title: String, val body: String, val app: String)

    @Volatile private var watching = false

    /** Starts handing notifications over (once; it ends by itself when the desktop stops). */
    fun watch(context: Context) {
        if (watching) return
        watching = true
        val app = context.applicationContext
        Thread({
            try {
                while (LinuxDesktop.state.value == LinuxDesktop.State.Running) {
                    Thread.sleep(EVERY_MS)
                    val mode = LinuxDesktop.installedMode(app) ?: break
                    val text = LinuxDesktop.runForOutput(app, mode, "notifications", 5_000) ?: continue
                    parse(text).forEach { post(app, it) }
                }
            } finally {
                watching = false
            }
        }, "LinuxNotifications").apply { isDaemon = true; start() }
    }

    /** The queue's lines: tag, title, body and app, tab-separated; others skipped. */
    internal fun parse(text: String): List<Note> = text.lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size < 2 || parts[0].isBlank()) return@mapNotNull null
        Note(parts[0].trim(), parts[1].trim(), parts.getOrNull(2).orEmpty().trim(), parts.getOrNull(3).orEmpty().trim())
            .takeIf { it.title.isNotEmpty() || it.body.isNotEmpty() }
    }.toList()

    private fun post(context: Context, note: Note) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.linux_notifications_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 11,
            Intent(context, LinuxViewerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(note.title.ifEmpty { note.app })
            .setContentText(note.body)
            .setStyle(Notification.BigTextStyle().bigText(note.body))
            .setSubText(note.app.ifEmpty { null })
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify("linux:" + note.tag.take(64), 1, notification) }
    }
}
