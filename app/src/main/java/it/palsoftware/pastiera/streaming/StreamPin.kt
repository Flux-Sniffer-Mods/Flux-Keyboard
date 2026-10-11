package it.palsoftware.pastiera.streaming

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import it.palsoftware.pastiera.R

/**
 * Pairing a Moonlight device: a notification asks for the PIN Moonlight shows, typed on the
 * phone in a small dialog of its own.
 */
class StreamPin : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.stream_pin_hint)
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.stream_pin_title, StreamHost.pairingDevice ?: "Moonlight"))
            .setView(field)
            .setPositiveButton(R.string.stream_pin_pair) { _, _ -> StreamHost.enterPin(field.text.toString()); finish() }
            .setNegativeButton(R.string.cancel) { _, _ -> StreamHost.refusePairing(); finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    companion object {
        private const val CHANNEL = "stream_pairing"
        private const val NOTIFICATION_ID = 7304
        private const val CAPTURE_ID = 7307

        fun ask(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.stream_pairing_channel), NotificationManager.IMPORTANCE_HIGH)
            )
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, StreamPin::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.stream_pin_title, StreamHost.pairingDevice ?: "Moonlight"))
                .setContentText(context.getString(R.string.stream_pin_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(NOTIFICATION_ID, notification) }
        }

        /** Without the shell, Moonlight's stream waits for a tap here to capture the screen. */
        fun askCapture(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.stream_pairing_channel), NotificationManager.IMPORTANCE_HIGH)
            )
            val open = PendingIntent.getActivity(
                context, 1, Intent(context, StreamCapture::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.stream_capture_title))
                .setContentText(context.getString(R.string.stream_capture_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(CAPTURE_ID, notification) }
        }

        fun done(context: Context) {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }
}
