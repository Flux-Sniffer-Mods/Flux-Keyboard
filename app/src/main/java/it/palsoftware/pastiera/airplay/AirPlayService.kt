package it.palsoftware.pastiera.airplay

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import it.palsoftware.pastiera.R

/** Keeps AirPlay casting with the phone's screen captured, its notification saying where, with Stop. */
class AirPlayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AirPlay.stop(this)
            stopSelf()
            return START_NOT_STICKY
        }
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        // Capturing the screen needs the service to say so first
        startForeground(NOTIFICATION_ID, notification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val projection = data?.let {
            runCatching {
                getSystemService(MediaProjectionManager::class.java)?.getMediaProjection(intent.getIntExtra(EXTRA_CODE, 0), it)
            }.getOrNull()
        }
        if (projection == null) {
            AirPlay.stop(this)
            stopSelf()
            return START_NOT_STICKY
        }
        val app = applicationContext
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { if (AirPlay.uses(projection)) AirPlay.stop(app) }
        }, Handler(Looper.getMainLooper()))
        AirPlay.onProjection(this, projection)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "airplay"
        private const val NOTIFICATION_ID = 7307
        private const val ACTION_STOP = "stop"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"

        private fun notification(context: Context): Notification {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.airplay_title), NotificationManager.IMPORTANCE_LOW)
            )
            val stop = PendingIntent.getService(
                context, 8, Intent(context, AirPlayService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val name = when (val state = AirPlay.state.value) {
                is AirPlay.State.Casting -> state.name
                is AirPlay.State.Starting -> state.name
                else -> null
            }
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setContentTitle(name?.let { context.getString(R.string.airplay_casting_to, it) } ?: context.getString(R.string.airplay_title))
                .setContentText(context.getString(R.string.airplay_casting_description))
                .addAction(Notification.Action.Builder(null, context.getString(R.string.miracast_stop), stop).build())
                .build()
        }

        /** The notification brought up to date (casting started). */
        fun refresh(context: Context) {
            runCatching { context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(context)) }
        }

        fun capture(context: Context, code: Int, data: Intent) {
            context.startForegroundService(Intent(context, AirPlayService::class.java)
                .putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATA, data))
        }
    }
}

/**
 * Asks for what casting needs: the microphone permission (Android's rule for capturing the
 * sound that plays; without it the picture goes alone), then the screen capture, which is
 * handed to [AirPlayService].
 */
class AirPlayCapture : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_SOUND)
        } else askCapture()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_SOUND) askCapture()
    }

    private fun askCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        if (manager == null) { AirPlay.stop(this); finish(); return }
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    @Deprecated("Activity result API not used in this plain activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) return
        if (resultCode == RESULT_OK && data != null) AirPlayService.capture(this, resultCode, data)
        else AirPlay.stop(this)
        finish()
    }

    private companion object {
        const val REQUEST_SOUND = 7308
        const val REQUEST_CAPTURE = 7309
    }
}
