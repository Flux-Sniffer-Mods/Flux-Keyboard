package it.palsoftware.pastiera.update

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import it.palsoftware.pastiera.getForkUpdateChannel

internal fun successorReleasesApiUrl(): String =
    "https://api.github.com/repos/${BuildConfig.SUCCESSOR_GITHUB_REPOSITORY}/releases?per_page=20"

internal fun successorReleasesPage(): String =
    "https://github.com/${BuildConfig.SUCCESSOR_GITHUB_REPOSITORY}/releases"

/** Flux Keyboard stable builds update from the fork's own releases rather than upstream's. */
internal fun forkUpdatesEnabled(): Boolean =
    BuildConfig.RELEASE_CHANNEL == "stable" && BuildConfig.FORK_GITHUB_REPOSITORY.isNotBlank()

private const val FORK_UPDATE_PREFS = "fork_update"
private const val KEY_ANNOUNCED_FORK_RELEASE = "announced_release"

/** Remembers the release a notification announced, so it can be cleared once installed. */
internal fun rememberAnnouncedForkRelease(context: Context, tag: String) {
    context.getSharedPreferences(FORK_UPDATE_PREFS, Context.MODE_PRIVATE).edit()
        .putString(KEY_ANNOUNCED_FORK_RELEASE, tag).apply()
}

/**
 * After an update, a notification announcing this build (or an older one) is stale: it's
 * cleared, so the new version never prompts for itself.
 */
fun clearStaleForkUpdateNotice(context: Context) {
    ForkUpdateInstaller.clearDownloads(context)
    val prefs = context.getSharedPreferences(FORK_UPDATE_PREFS, Context.MODE_PRIVATE)
    val tag = prefs.getString(KEY_ANNOUNCED_FORK_RELEASE, null) ?: return
    if (forkReleaseIsNewer(tag, BuildConfig.VERSION_NAME)) return
    it.palsoftware.pastiera.inputmethod.NotificationHelper.cancelUpdateNotification(context)
    prefs.edit().remove(KEY_ANNOUNCED_FORK_RELEASE).apply()
}

internal fun forkReleasesPage(): String =
    "https://github.com/${BuildConfig.FORK_GITHUB_REPOSITORY}/releases"

private val client = OkHttpClient()
private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

internal data class UpdateCheckResult(
    val successful: Boolean,
    val hasAnnouncement: Boolean = false,
    val releaseTag: String? = null,
    val displayName: String? = null,
    val releasePageUrl: String? = null,
    val downloadUrl: String? = null,
    val isNightlyUpdate: Boolean = false,
    val isForkUpdate: Boolean = false,
    val isPastieraStableUpdate: Boolean = false,
    val followUpAnnouncement: UpdateCheckResult? = null
)

internal fun combineStableUpdateResults(
    pastiera: UpdateCheckResult,
    successor: UpdateCheckResult
): UpdateCheckResult {
    val successful = pastiera.successful && successor.successful
    return when {
        successor.hasAnnouncement -> successor.copy(
            successful = successful,
            followUpAnnouncement = pastiera.takeIf { it.hasAnnouncement }
        )
        pastiera.hasAnnouncement -> pastiera.copy(successful = successful)
        else -> UpdateCheckResult(successful = successful)
    }
}

internal fun combineNightlyUpdateResults(
    pastiera: UpdateCheckResult,
    successor: UpdateCheckResult
): UpdateCheckResult = combineStableUpdateResults(pastiera, successor)

private enum class ReleaseFeed {
    SUCCESSOR,
    PASTIERA_STABLE,
    PASTIERA_NIGHTLY,
    /** Flux Keyboard's own releases, in place of Pastiera's and its successor's */
    FORK
}

internal fun checkForUpdate(
    context: Context,
    releaseChannel: String,
    ignoreDismissedReleases: Boolean = true,
    callback: (UpdateCheckResult) -> Unit
) = checkRelease(
    context, releaseChannel, ignoreDismissedReleases,
    if (forkUpdatesEnabled()) ReleaseFeed.FORK else ReleaseFeed.SUCCESSOR,
    callback
)

private fun checkForPastieraStableUpdate(
    context: Context,
    ignoreDismissedReleases: Boolean,
    callback: (UpdateCheckResult) -> Unit
) = checkRelease(
    context,
    "stable",
    ignoreDismissedReleases,
    ReleaseFeed.PASTIERA_STABLE,
    callback
)


internal fun checkForNightlyUpdate(
    context: Context,
    ignoreDismissedReleases: Boolean = true,
    callback: (UpdateCheckResult) -> Unit
) {
    if (BuildConfig.RELEASE_CHANNEL != "nightly") {
        postResult(callback, UpdateCheckResult(successful = true))
        return
    }
    checkRelease(context, "nightly", ignoreDismissedReleases, ReleaseFeed.PASTIERA_NIGHTLY, callback)
}

internal fun checkForUpdateNotices(
    context: Context,
    releaseChannel: String,
    ignoreDismissedReleases: Boolean = true,
    callback: (UpdateCheckResult) -> Unit
) {
    // Flux Keyboard includes Pastiera's releases: it offers only its own, never Pastiera's or
    // its successor's
    if (forkUpdatesEnabled()) {
        checkForUpdate(context, releaseChannel, ignoreDismissedReleases, callback)
    } else if (BuildConfig.RELEASE_CHANNEL == "nightly") {
        checkForNightlyUpdate(context, ignoreDismissedReleases) { nightly ->
            checkForUpdate(context, "nightly", ignoreDismissedReleases) { successor ->
                callback(combineNightlyUpdateResults(nightly, successor))
            }
        }
    } else {
        checkForPastieraStableUpdate(context, ignoreDismissedReleases) { pastiera ->
            checkForUpdate(context, "stable", ignoreDismissedReleases) { successor ->
                callback(combineStableUpdateResults(pastiera, successor))
            }
        }
    }
}

private fun checkRelease(
    context: Context,
    releaseChannel: String,
    ignoreDismissedReleases: Boolean,
    feed: ReleaseFeed,
    callback: (UpdateCheckResult) -> Unit
) {
    if (!shouldUseGithubUpdateChecks(context)) {
        postResult(callback, UpdateCheckResult(successful = true))
        return
    }

    val fork = feed == ReleaseFeed.FORK
    val includeDev = fork && SettingsManager.getForkUpdateChannel(context) == SettingsManager.FORK_UPDATE_CHANNEL_DEV
    val request = Request.Builder()
        .url(when (feed) {
            ReleaseFeed.FORK -> forkReleasesApiUrl()
            ReleaseFeed.SUCCESSOR -> successorReleasesApiUrl()
            else -> "https://api.github.com/repos/palsoftware/pastiera/releases?per_page=20"
        })
        .header("Accept", "application/vnd.github+json")
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            postResult(callback, UpdateCheckResult(successful = false))
        }

        override fun onResponse(call: Call, response: Response) {
            response.use { res ->
                if (!res.isSuccessful) {
                    postResult(callback, UpdateCheckResult(successful = false))
                    return
                }

                val body = res.body?.string().orEmpty()
                if (body.isBlank()) {
                    postResult(callback, UpdateCheckResult(successful = false))
                    return
                }

                val latestRelease = try {
                    val releases = if (feed == ReleaseFeed.FORK) {
                        // The fork's release tags (see forkReleasesApiUrl)
                        val refs = JSONArray(body)
                        forkReleasesFromTags(
                            (0 until refs.length()).mapNotNull { refs.optJSONObject(it)?.optString("ref") },
                            editionSuffix = it.palsoftware.pastiera.LanguageEdition.apkSuffix(context)
                        )
                    } else parseGitHubReleases(JSONArray(body))
                    when (feed) {
                        ReleaseFeed.PASTIERA_NIGHTLY -> findNewerNightlyRelease(releases, BuildConfig.VERSION_NAME)
                        ReleaseFeed.PASTIERA_STABLE -> findNewerStableRelease(releases, BuildConfig.VERSION_NAME)
                        ReleaseFeed.FORK -> findNewerForkRelease(releases, BuildConfig.VERSION_NAME, includeDev)
                        ReleaseFeed.SUCCESSOR -> findLatestRelease(releases, releaseChannel)
                    }
                } catch (_: Exception) {
                    postResult(callback, UpdateCheckResult(successful = false))
                    return
                }
                if (latestRelease == null) {
                    postResult(callback, UpdateCheckResult(successful = true))
                    return
                }

                val releaseTag = latestRelease.tagName
                if (ignoreDismissedReleases) {
                    val isDismissed = SettingsManager.isReleaseDismissed(context, dismissKey(releaseTag, feed))
                    if (isDismissed) {
                        // Release was dismissed, don't show update
                        postResult(callback, UpdateCheckResult(successful = true))
                        return
                    }
                }
                
                postResult(
                    callback,
                    UpdateCheckResult(
                        successful = true,
                        hasAnnouncement = true,
                        releaseTag = releaseTag,
                        displayName = latestRelease.displayName,
                        releasePageUrl = latestRelease.releasePageUrl,
                        downloadUrl = latestRelease.downloadUrl,
                        isNightlyUpdate = feed == ReleaseFeed.PASTIERA_NIGHTLY,
                        isPastieraStableUpdate = feed == ReleaseFeed.PASTIERA_STABLE,
                        isForkUpdate = feed == ReleaseFeed.FORK
                    )
                )
            }
        }
    })
}

private fun dismissKey(tag: String, feed: ReleaseFeed): String = when (feed) {
    ReleaseFeed.PASTIERA_NIGHTLY -> "pastiera-nightly:$tag"
    ReleaseFeed.PASTIERA_STABLE -> "pastiera-stable:$tag"
    ReleaseFeed.FORK -> "pastiera-flux:$tag"
    ReleaseFeed.SUCCESSOR -> tag
}

private fun postResult(
    callback: (UpdateCheckResult) -> Unit,
    result: UpdateCheckResult
) {
    mainHandler.post {
        callback(result)
    }
}

fun showUpdateDialog(
    context: Context,
    releaseTag: String,
    displayName: String,
    releasePageUrl: String?,
    onClosed: () -> Unit = {}
) {
    val dialog = AlertDialog.Builder(context)
        .setTitle(R.string.successor_dialog_title)
        .setMessage(context.getString(R.string.successor_dialog_message, displayName))
        .setPositiveButton(R.string.successor_dialog_open_release) { _, _ ->
            openUrl(context, releasePageUrl ?: successorReleasesPage())
            onClosed()
        }
        .setNeutralButton(R.string.successor_dialog_later) { _, _ ->
            SettingsManager.addDismissedRelease(context, releaseTag)
            onClosed()
        }
        .create()
    dialog.setOnCancelListener { onClosed() }
    dialog.show()
}

private fun showPastieraStableUpdateDialog(
    context: Context,
    releaseTag: String,
    displayName: String,
    releasePageUrl: String?,
    onClosed: () -> Unit
) {
    val dialog = AlertDialog.Builder(context)
        .setTitle(R.string.pastiera_stable_update_title)
        .setMessage(context.getString(R.string.pastiera_stable_update_message, displayName))
        .setPositiveButton(R.string.pastiera_stable_update_open) { _, _ ->
            openUrl(context, releasePageUrl ?: "https://github.com/palsoftware/pastiera/releases")
            onClosed()
        }
        .setNeutralButton(R.string.successor_dialog_later) { _, _ ->
            SettingsManager.addDismissedRelease(context, "pastiera-stable:$releaseTag")
            onClosed()
        }
        .create()
    dialog.setOnCancelListener { onClosed() }
    dialog.show()
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // No browser (or none enabled) to open it in
    runCatching { context.startActivity(intent) }
}

internal fun showReleaseNotice(context: Context, result: UpdateCheckResult) {
    val tag = result.releaseTag ?: return
    val name = result.displayName ?: return
    if (result.isForkUpdate) {
        // Checked again here: a result from before an update may name the installed build
        if (!forkReleaseIsNewer(tag, BuildConfig.VERSION_NAME)) return
        showForkUpdateDialog(context, tag, name, result.releasePageUrl, result.downloadUrl)
        return
    }
    val showFollowUp = {
        result.followUpAnnouncement?.let { showReleaseNotice(context, it) }
        Unit
    }
    if (result.isPastieraStableUpdate) {
        showPastieraStableUpdateDialog(context, tag, name, result.releasePageUrl, showFollowUp)
        return
    }
    if (!result.isNightlyUpdate) {
        showUpdateDialog(context, tag, name, result.releasePageUrl, showFollowUp)
        return
    }
    val builder = AlertDialog.Builder(context)
        .setTitle(R.string.nightly_update_title)
        .setMessage(context.getString(R.string.nightly_update_message, name))
        .setPositiveButton(R.string.nightly_update_open) { _, _ ->
            openUrl(context, result.releasePageUrl ?: "https://github.com/palsoftware/pastiera/releases")
            showFollowUp()
        }
        .setNeutralButton(R.string.successor_dialog_later) { _, _ ->
            SettingsManager.addDismissedRelease(context, "pastiera-nightly:$tag")
            showFollowUp()
        }
    result.downloadUrl?.let { url ->
        builder.setNegativeButton(R.string.nightly_update_download) { _, _ ->
            openUrl(context, url)
            showFollowUp()
        }
    }
    val dialog = builder.create()
    dialog.setOnCancelListener { showFollowUp() }
    dialog.show()
}

private fun showForkUpdateDialog(
    context: Context,
    tag: String,
    name: String,
    releasePageUrl: String?,
    downloadUrl: String?
) {
    val builder = AlertDialog.Builder(context)
        .setTitle(R.string.fork_update_title)
        .setMessage(context.getString(R.string.fork_update_message, name))
        .setPositiveButton(R.string.nightly_update_open) { _, _ ->
            openUrl(context, releasePageUrl ?: forkReleasesPage())
        }
        .setNeutralButton(R.string.successor_dialog_later) { _, _ ->
            SettingsManager.addDismissedRelease(context, "pastiera-flux:$tag")
        }
    downloadUrl?.let { url ->
        builder.setNegativeButton(R.string.fork_update_install) { _, _ ->
            ForkUpdateInstaller.downloadAndInstall(context, url, releasePageUrl)
        }
    }
    builder.show()
}
