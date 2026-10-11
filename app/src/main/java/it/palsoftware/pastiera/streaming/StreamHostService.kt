package it.palsoftware.pastiera.streaming

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager

/** Keeps the streaming host running while Stream to Moonlight is on, with its notification. */
class StreamHostService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END) {
            StreamHost.endStream()
            return START_STICKY
        }
        val notification = notification(this)
        val data = intent?.takeIf { it.action == ACTION_CAPTURE }?.let {
            @Suppress("DEPRECATION")
            it.getParcelableExtra<Intent>(EXTRA_DATA)
        }
        if (data != null) {
            // Capturing the screen needs the service to say so first
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            val projection = runCatching {
                getSystemService(MediaProjectionManager::class.java)?.getMediaProjection(intent.getIntExtra(EXTRA_CODE, 0), data)
            }.getOrNull()
            if (projection == null) StreamHost.session?.end()
            else {
                projection.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() { StreamHost.session?.end() }
                }, Handler(Looper.getMainLooper()))
                StreamHost.session?.onProjection(projection) ?: projection.stop()
            }
        } else {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }
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
        private const val ACTION_CAPTURE = "capture"
        private const val ACTION_END = "end"

        /**
         * The service's notification: the phone listed in Moonlight, or while a game streams,
         * which and to whom, with a way to end it (a tap asks first, the button doesn't).
         */
        fun notification(context: Context): android.app.Notification {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.stream_host_channel), NotificationManager.IMPORTANCE_LOW)
            )
            val builder = android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
            val device = StreamHost.streamingDevice
            if (StreamHost.session == null || device == null) {
                return builder.setContentTitle(context.getString(R.string.stream_host_title))
                    .setContentText(context.getString(R.string.stream_host_text, StreamHost.hostname())).build()
            }
            val end = android.app.PendingIntent.getService(
                context, 3, Intent(context, StreamHostService::class.java).setAction(ACTION_END),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val ask = android.app.PendingIntent.getActivity(
                context, 4, Intent(context, StreamStop::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val game = StreamHost.streamingGame
            return builder
                .setContentTitle(if (game != null) context.getString(R.string.stream_now_game, game) else context.getString(R.string.stream_now))
                .setContentText(context.getString(R.string.stream_now_to, device))
                .setContentIntent(ask)
                .addAction(android.app.Notification.Action.Builder(null, context.getString(R.string.stream_end), end).build())
                .build()
        }

        /** The notification brought up to date (a stream started or ended). */
        fun refresh(context: Context) {
            if (!enabled(context)) return
            runCatching { context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(context)) }
        }
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"

        /** Hands the screen capture Android allowed to the service, which starts the stream with it. */
        fun capture(context: Context, code: Int, data: Intent) {
            context.startForegroundService(Intent(context, StreamHostService::class.java).setAction(ACTION_CAPTURE)
                .putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATA, data))
        }

        private const val KEY_RESOLUTION = "stream_resolution"
        /** Automatic (what Moonlight asks for), a fixed height, or Moonlight's own. */
        val RESOLUTIONS = listOf("auto", "720", "1080", "1440")

        fun resolution(context: Context): String =
            SettingsManager.getPreferences(context).getString(KEY_RESOLUTION, "auto")?.takeIf { it in RESOLUTIONS } ?: "auto"

        fun setResolution(context: Context, value: String) =
            SettingsManager.getPreferences(context).edit().putString(KEY_RESOLUTION, value).apply()

        /** The phone's screen size while streaming to a [width] x [height] screen: its shape, at the chosen height. */
        internal fun phoneSize(choice: String, width: Int, height: Int): Pair<Int, Int> {
            val rows = when (choice) {
                "720" -> 720
                "1080" -> 1080
                "1440" -> 1440
                else -> height
            }
            return ((rows.toLong() * width / height).toInt() and 1.inv()) to (rows and 1.inv())
        }

        private const val KEY_PHONE_SCREEN = "stream_phone_screen"

        /** The phone's own screen listed in Moonlight, to show whatever is on it. */
        fun phoneScreen(context: Context): Boolean = SettingsManager.getPreferences(context).getBoolean(KEY_PHONE_SCREEN, true)

        fun setPhoneScreen(context: Context, on: Boolean) =
            SettingsManager.getPreferences(context).edit().putBoolean(KEY_PHONE_SCREEN, on).apply()

        fun enabled(context: Context): Boolean = SettingsManager.getPreferences(context).getBoolean(KEY_ENABLED, false)

        fun setEnabled(context: Context, on: Boolean) {
            SettingsManager.getPreferences(context).edit().putBoolean(KEY_ENABLED, on).apply()
            val intent = Intent(context, StreamHostService::class.java)
            if (on) context.startForegroundService(intent) else context.stopService(intent)
        }
    }
}
