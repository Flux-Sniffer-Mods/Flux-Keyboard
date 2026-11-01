package it.palsoftware.pastiera.linux

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import it.palsoftware.pastiera.R

/**
 * Keeps the app going while Linux installs or the desktop runs (the shell does the work; this
 * keeps Android from ending the app meanwhile), its notification saying which, with Stop.
 */
class LinuxService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            LinuxDesktop.stop(this)
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "linux"
        private const val NOTIFICATION_ID = 7310
        private const val ACTION_STOP = "stop"

        private fun notification(context: Context): Notification {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.module_desktop_title), NotificationManager.IMPORTANCE_LOW)
            )
            val open = PendingIntent.getActivity(
                context, 9, Intent(context, LinuxActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val stop = PendingIntent.getService(
                context, 10, Intent(context, LinuxService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val running = LinuxDesktop.state.value == LinuxDesktop.State.Running || LinuxDesktop.state.value == LinuxDesktop.State.Starting
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setContentTitle(context.getString(if (running) R.string.linux_notification_running else R.string.linux_notification_installing))
                .setContentIntent(open)
                .apply { if (running) addAction(Notification.Action.Builder(null, context.getString(R.string.miracast_stop), stop).build()) }
                .build()
        }

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, LinuxService::class.java)) }
        }

        /** Nothing left to keep going: the service ends. */
        fun idle(context: Context) {
            val busy = when (LinuxDesktop.state.value) {
                LinuxDesktop.State.Installing, LinuxDesktop.State.Starting, LinuxDesktop.State.Running -> true
                is LinuxDesktop.State.Downloading -> true
                else -> false
            }
            if (busy) runCatching { context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(context)) }
            else runCatching { context.stopService(Intent(context, LinuxService::class.java)) }
        }
    }
}
