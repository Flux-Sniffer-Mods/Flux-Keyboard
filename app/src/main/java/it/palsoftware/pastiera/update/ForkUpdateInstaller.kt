package it.palsoftware.pastiera.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Flux Keyboard installs its own updates: the APK attached to this repository's release is
 * downloaded to the cache, checked to be a newer build of this same app, and handed to
 * Android's package installer. Android still asks before installing, and refuses an APK
 * signed with another key.
 */
object ForkUpdateInstaller {
    private const val TAG = "ForkUpdateInstaller"
    private const val DIR = "updates"
    private const val APK_MIME = "application/vnd.android.package-archive"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val main by lazy { Handler(Looper.getMainLooper()) }
    @Volatile private var downloading = false

    /** Only APKs attached to this repository's own releases are downloaded. */
    internal fun isTrustedApkUrl(url: String): Boolean =
        url.startsWith("https://github.com/${BuildConfig.FORK_GITHUB_REPOSITORY}/releases/download/", ignoreCase = true) &&
            url.endsWith(".apk", ignoreCase = true)

    /** Whether the downloaded archive is a newer build of this app. */
    internal fun isNewerBuildOfThisApp(archivePackage: String?, archiveVersion: String?, ownPackage: String, ownVersion: String): Boolean =
        archivePackage == ownPackage && archiveVersion != null &&
            (compareReleaseVersions(archiveVersion, ownVersion) ?: -1) > 0

    fun downloadAndInstall(context: Context, apkUrl: String, releasePageUrl: String?) {
        val app = context.applicationContext
        val fallback = releasePageUrl ?: forkReleasesPage()
        if (!isTrustedApkUrl(apkUrl)) {
            open(app, fallback)
            return
        }
        if (downloading) return
        downloading = true
        Toast.makeText(app, R.string.fork_update_downloading, Toast.LENGTH_SHORT).show()
        Thread {
            val apk = runCatching { download(app, apkUrl) }
                .onFailure { Log.w(TAG, "Update download failed", it) }
                .getOrNull()
            downloading = false
            main.post {
                if (apk == null) {
                    Toast.makeText(app, R.string.fork_update_download_failed, Toast.LENGTH_LONG).show()
                    open(app, fallback)
                } else if (canInstall(app)) {
                    install(app, apk)
                } else {
                    askToAllowInstalls(app)
                }
            }
        }.start()
    }

    private fun download(context: Context, url: String): File? {
        val dir = File(context.cacheDir, DIR).apply { deleteRecursively(); mkdirs() }
        val target = File(dir, "flux-keyboard-update.apk")
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            target.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(target.path, 0)
        if (!isNewerBuildOfThisApp(info?.packageName, info?.versionName, context.packageName, BuildConfig.VERSION_NAME)) {
            Log.w(TAG, "Downloaded APK isn't a newer build of this app: ${info?.packageName} ${info?.versionName}")
            target.delete()
            return null
        }
        return target
    }

    private fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                Log.w(TAG, "No installer for the update", it)
                Toast.makeText(context, R.string.fork_update_download_failed, Toast.LENGTH_LONG).show()
            }
    }

    private fun canInstall(context: Context) =
        context.packageManager.canRequestPackageInstalls()

    private fun pendingApk(context: Context) = File(File(context.cacheDir, DIR), "flux-keyboard-update.apk")

    /**
     * Android's "Install unknown apps" switch, the first time. Flipping it restarts the app, so
     * the download is kept and [UpdateInstallActivity], which Android brings back afterwards,
     * picks the install up again.
     */
    private fun askToAllowInstalls(context: Context) {
        Toast.makeText(context, R.string.fork_update_allow_installs, Toast.LENGTH_LONG).show()
        runCatching {
            context.startActivity(Intent(context, UpdateInstallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { open(context, "package:${context.packageName}", Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES) }
    }

    internal fun allowInstallsIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Installs a kept download once installs are allowed. Whether it did. */
    internal fun installPending(context: Context): Boolean {
        val apk = pendingApk(context)
        if (!apk.isFile || !canInstall(context)) return false
        install(context.applicationContext, apk)
        return true
    }

    /** A downloaded update is only needed until it's installed (or while installs are being allowed). */
    fun clearDownloads(context: Context) {
        if (downloading) return
        val apk = pendingApk(context)
        @Suppress("DEPRECATION")
        val info = if (apk.isFile) context.packageManager.getPackageArchiveInfo(apk.path, 0) else null
        if (isNewerBuildOfThisApp(info?.packageName, info?.versionName, context.packageName, BuildConfig.VERSION_NAME)) return
        File(context.cacheDir, DIR).deleteRecursively()
    }

    private fun open(context: Context, url: String, action: String = Intent.ACTION_VIEW) {
        runCatching {
            context.startActivity(Intent(action, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
