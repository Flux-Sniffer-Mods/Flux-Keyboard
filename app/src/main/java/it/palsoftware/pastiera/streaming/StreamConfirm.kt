package it.palsoftware.pastiera.streaming

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.adb.AdbShell

/**
 * A Moonlight client asking to start a stream: confirmed on the phone first, with the choice to
 * let that client start by itself from then on. Shown straight away through the shell when it
 * can be, else from its notification.
 */
class StreamConfirm : Activity() {
    private var answered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val device = StreamHost.askingDevice
        if (device == null) { finish(); return }
        val always = CheckBox(this).apply {
            text = getString(R.string.stream_confirm_always)
            setPadding(48, 24, 48, 0)
        }
        val game = StreamHost.askingGame
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.stream_confirm_title, device))
            .setMessage(if (game != null) getString(R.string.stream_confirm_text_game, game) else getString(R.string.stream_confirm_text))
            .setView(always)
            .setPositiveButton(R.string.stream_confirm_connect) { _, _ -> reply(true, always.isChecked) }
            .setNegativeButton(R.string.cancel) { _, _ -> reply(false, false) }
            .setOnCancelListener { reply(false, false) }
            .show()
    }

    private fun reply(allowed: Boolean, always: Boolean) {
        if (answered) return
        answered = true
        StreamHost.answer(allowed, always)
        finish()
    }

    companion object {
        private const val CHANNEL = "stream_pairing"
        private const val NOTIFICATION_ID = 7308

        fun ask(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.stream_pairing_channel), NotificationManager.IMPORTANCE_HIGH)
            )
            val intent = Intent(context, StreamConfirm::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val open = PendingIntent.getActivity(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.stream_confirm_title, StreamHost.askingDevice ?: "Moonlight"))
                .setContentText(StreamHost.askingGame ?: context.getString(R.string.stream_confirm_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(NOTIFICATION_ID, notification) }
            // On screen at once where the shell can open it (an app in the background can't)
            if (AdbShell.available()) AdbShell.runAsync("am start -n ${context.packageName}/${StreamConfirm::class.java.name}")
        }

        fun done(context: Context) {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }
}
