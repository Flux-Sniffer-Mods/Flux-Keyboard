package it.palsoftware.pastiera.streaming

import android.app.Activity
import android.widget.Toast
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.gaming.GameProfile

/**
 * A game opened while streaming, with a Moonlight client set up: streamed to it (turned on, Moonlight opened
 * there straight into it) or on the phone, asked each time unless set to one.
 */
object TvPrompt {
    /** Whether opening [profile] now would go to the Moonlight client or be asked about. */
    fun applies(activity: Activity, profile: GameProfile?): Boolean =
        profile != null && profile.isGame && StreamHostService.enabled(activity) && TvRemote.ready(activity) &&
            StreamHost.session == null && TvRemote.games(activity) != TvRemote.Games.PHONE

    /**
     * [profile] opened: streamed to the Moonlight client, or [onPhone] to play it on the phone; [onCancel] when the
     * question is dismissed. Straight to the phone when there's nothing to ask.
     */
    fun open(activity: Activity, profile: GameProfile?, onPhone: () -> Unit, onTv: () -> Unit = {}, onCancel: () -> Unit = {}) {
        if (profile == null || !applies(activity, profile)) return onPhone()
        if (TvRemote.games(activity) == TvRemote.Games.TV) { play(activity, profile); return onTv() }
        val tvName = TvRemote.tv(activity)?.name.orEmpty()
        android.app.AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(activity.getString(R.string.tv_play_title, profile.name))
            .setMessage(activity.getString(R.string.tv_play_message, tvName))
            .setPositiveButton(R.string.tv_play_on_tv) { _, _ -> play(activity, profile); onTv() }
            .setNegativeButton(R.string.tv_play_on_phone) { _, _ -> onPhone() }
            .setOnCancelListener { onCancel() }
            .show()
    }

    /** [profile]'s game on the Moonlight client: woken, Moonlight opened there straight into it; how it goes in toasts. */
    fun play(activity: Activity, profile: GameProfile) {
        val app = activity.applicationContext
        val tvName = TvRemote.tv(app)?.name.orEmpty()
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        fun say(text: String) = main.post { Toast.makeText(app, text, Toast.LENGTH_LONG).show() }
        say(app.getString(R.string.tv_starting, profile.name, tvName))
        Thread {
            val result = runCatching { TvRemote.play(app, profile) { step -> say(step) } }.getOrDefault(TvRemote.Result.FAILED)
            when (result) {
                TvRemote.Result.STARTED -> Unit
                TvRemote.Result.NO_TV -> say(app.getString(R.string.tv_no_answer, tvName))
                TvRemote.Result.REFUSED -> say(app.getString(R.string.tv_refused, tvName))
                else -> say(app.getString(R.string.tv_failed, tvName))
            }
        }.start()
    }
}
