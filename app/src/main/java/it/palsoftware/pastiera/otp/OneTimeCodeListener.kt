package it.palsoftware.pastiera.otp

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import it.palsoftware.pastiera.SettingsManager

/**
 * Reads new notifications for one-time codes, only while "One-time codes" is on and Android's
 * notification access is granted to the app. Only the code itself is kept (see [OneTimeCodes]).
 */
class OneTimeCodeListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        // Root: the keyboard's backlight flashes for a notification (the Root page)
        if (sbn.packageName != packageName && !sbn.isOngoing) it.palsoftware.pastiera.adb.KeyboardBacklight.flash(this)
        if (sbn.packageName == packageName || !SettingsManager.getOneTimeCodesEnabled(this)) return
        val extras = notification.extras ?: return
        // Newest first: a conversation's notification (SMS, chat) keeps its older messages, with
        // their older codes, in its big text and message list, so the latest message decides
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val parts = listOfNotNull(
            latestMessage(notification),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.lastOrNull()?.toString()
        ).filter { it.isNotBlank() }
        // A "Copy 123456" button (Google Messages and others add one) names the code outright
        val code = copyActionCode(notification) ?: parts.firstNotNullOfOrNull { part ->
            OneTimeCodes.extract(part) ?: OneTimeCodes.extract("$title $part")
        } ?: return
        OneTimeCodes.offer(code)
    }

    /** The code on a notification's copy button ("Copy 123456", "Copy code 1234"), if it has one. */
    private fun copyActionCode(notification: Notification): String? =
        notification.actions.orEmpty().firstNotNullOfOrNull { action ->
            OneTimeCodes.fromCopyAction(action.title?.toString().orEmpty())
        }

    /** The newest message of a conversation-style notification, if it is one. */
    private fun latestMessage(notification: Notification): String? = runCatching {
        Notification.MessagingStyle.Message
            .getMessagesFromBundleArray(notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES))
            .maxByOrNull { it.timestamp }?.text?.toString()
    }.getOrNull()
}
