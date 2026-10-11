package it.palsoftware.pastiera.streaming

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager

/** Keeps the streaming host running while Stream to Moonlight is on, with its notification. */
class StreamHostService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.stream_host_channel), NotificationManager.IMPORTANCE_LOW))
        val notification = android.app.Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.stream_host_title))
            .setContentText(getString(R.string.stream_host_text, StreamHost.hostname()))
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        StreamHost.start(this)
        return START_STICKY
    }

    override fun onDestroy() {
        StreamHost.stop()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "stream_host"
        private const val NOTIFICATION_ID = 7305
        private const val KEY_ENABLED = "stream_host_enabled"

        fun enabled(context: Context): Boolean = SettingsManager.getPreferences(context).getBoolean(KEY_ENABLED, false)

        fun setEnabled(context: Context, on: Boolean) {
            SettingsManager.getPreferences(context).edit().putBoolean(KEY_ENABLED, on).apply()
            val intent = Intent(context, StreamHostService::class.java)
            if (on) context.startForegroundService(intent) else context.stopService(intent)
        }
    }
}
