package it.palsoftware.pastiera.adb.shell

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.provider.Settings
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.android.AdbMdns
import it.palsoftware.pastiera.R
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Setting up and starting the shell helper through wireless debugging (Android 11 and later):
 * paired once with the code Android shows, then started at each boot once Wi-Fi is up.
 * Wireless debugging is switched on just for the start and back off after.
 */
object ShellSetup {
    private const val TAG = "FluxShell"
    private const val CHANNEL = "shell_setup"
    private const val NOTIFICATION_ID = 7301
    private const val KEY_CODE = "code"
    private const val ACTION_PAIR = "it.palsoftware.pastiera.SHELL_PAIR"
    private const val EXTRA_PORT = "port"
    private const val SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
    private const val ADB_WIFI = "adb_wifi_enabled"

    fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** Paired with wireless debugging before: the helper can be started without asking. */
    fun paired(context: Context): Boolean = File(keyDir(context), "paired").exists()

    /** Can switch wireless debugging on by itself, so it starts at boot without help. */
    fun canSwitchWirelessDebugging(context: Context): Boolean =
        context.checkSelfPermission(SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    // Pairing

    /**
     * Starts pairing: waits for Wireless debugging's "Pair device with pairing code", then asks
     * for the code in a notification. Opens Wireless debugging for the user.
     */
    fun beginPairing(context: Context) {
        val app = context.applicationContext
        notify(app, app.getString(R.string.shell_pair_waiting), null)
        Thread {
            val port = discover(app, AdbMdns.SERVICE_TYPE_TLS_PAIRING, 5 * 60_000L)
            if (port <= 0) {
                notify(app, app.getString(R.string.shell_pair_timed_out), null)
            } else {
                notify(app, app.getString(R.string.shell_pair_enter_code), port)
            }
        }.start()
        openWirelessDebugging(app)
    }

    fun openWirelessDebugging(context: Context) {
        val intents = listOf(
            Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"),
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
    }

    /** The code typed into the notification. */
    class PairReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_CODE)
                ?.toString()?.trim().orEmpty()
            val port = intent.getIntExtra(EXTRA_PORT, -1)
            if (code.isEmpty() || port <= 0) return
            val app = context.applicationContext
            val pending = goAsync()
            notify(app, app.getString(R.string.shell_pair_pairing), null)
            Thread {
                try {
                    val ok = runCatching { manager(app).pair("127.0.0.1", port, code) }
                        .onFailure { Log.w(TAG, "pairing failed: $it") }
                        .getOrDefault(false)
                    if (!ok) {
                        notify(app, app.getString(R.string.shell_pair_failed), port)
                        return@Thread
                    }
                    File(keyDir(app), "paired").writeText("1")
                    val started = start(app)
                    if (started) grantSecureSettings(app)
                    notify(
                        app,
                        app.getString(if (started) R.string.shell_pair_done else R.string.shell_start_failed),
                        null
                    )
                } finally {
                    pending.finish()
                }
            }.start()
        }
    }

    // Starting

    /**
     * Starts the helper when it isn't running: connects to wireless debugging (switching it on
     * for the moment when allowed) and launches it. Blocking; false when it couldn't.
     */
    @Synchronized
    fun start(context: Context): Boolean {
        if (BuiltInShell.running()) return true
        if (!supported() || !paired(context)) return false
        val resolver = context.contentResolver
        val wasOn = Settings.Global.getInt(resolver, ADB_WIFI, 0) == 1
        val switch = !wasOn && canSwitchWirelessDebugging(context)
        if (switch) runCatching { Settings.Global.putInt(resolver, ADB_WIFI, 1) }
        try {
            if (!wasOn && !switch) return false
            val port = discover(context, AdbMdns.SERVICE_TYPE_TLS_CONNECT, 20_000L)
            if (port <= 0) return false
            val adb = manager(context)
            if (!adb.connect("127.0.0.1", port)) return false
            try {
                adb.openStream("shell:" + startCommand(context)).use { stream ->
                    runCatching { stream.openInputStream().use { it.readBytes() } }
                }
            } finally {
                runCatching { adb.disconnect() }
            }
            repeat(30) {
                Thread.sleep(100)
                BuiltInShell.markStarted()
                if (BuiltInShell.running()) return true
            }
            return false
        } catch (e: Exception) {
            Log.w(TAG, "couldn't start the shell helper: $e")
            return false
        } finally {
            if (switch) runCatching { Settings.Global.putInt(resolver, ADB_WIFI, 0) }
        }
    }

    private fun startCommand(context: Context): String {
        val apk = context.applicationInfo.sourceDir
        val uid = android.os.Process.myUid()
        val token = BuiltInShell.token(context)
        return "${ShellProtocol.TOKEN_ENV}=$token CLASSPATH='$apk' setsid /system/bin/app_process /system/bin " +
            "--nice-name=flux_shell ${ShellServer::class.java.name} $uid " +
            "</dev/null >/dev/null 2>&1 &"
    }

    private fun grantSecureSettings(context: Context) {
        if (canSwitchWirelessDebugging(context)) return
        BuiltInShell.newProcess(arrayOf("pm", "grant", context.packageName, SECURE_SETTINGS))
            ?.waitFor(5, TimeUnit.SECONDS)
    }

    private var waitingForWifi: ConnectivityManager.NetworkCallback? = null

    /**
     * Makes sure the helper is running: now when it can, otherwise once Wi-Fi connects (as at
     * boot). Returns at once.
     */
    fun ensureStarted(context: Context) {
        val app = context.applicationContext
        BuiltInShell.init(app)
        if (!supported() || !paired(app)) return
        Thread {
            if (BuiltInShell.running() || start(app)) return@Thread
            synchronized(this) {
                if (waitingForWifi != null) return@Thread
                val connectivity = app.getSystemService(ConnectivityManager::class.java) ?: return@Thread
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Thread {
                            if (BuiltInShell.running() || start(app)) {
                                synchronized(this@ShellSetup) {
                                    runCatching { connectivity.unregisterNetworkCallback(this) }
                                    waitingForWifi = null
                                }
                            }
                        }.start()
                    }
                }
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()
                runCatching { connectivity.registerNetworkCallback(request, callback) }
                    .onSuccess { waitingForWifi = callback }
            }
        }.start()
    }

    /** Forgets the pairing: the helper keeps running until the next restart. */
    fun forget(context: Context) {
        keyDir(context).deleteRecursively()
        manager = null
    }

    // Wireless debugging

    private fun discover(context: Context, type: String, timeoutMs: Long): Int {
        val port = AtomicInteger(-1)
        val found = CountDownLatch(1)
        val mdns = AdbMdns(context, type) { _, p ->
            if (p > 0) { port.set(p); found.countDown() }
        }
        mdns.start()
        try {
            found.await(timeoutMs, TimeUnit.MILLISECONDS)
        } finally {
            mdns.stop()
        }
        return port.get()
    }

    private var manager: AbsAdbConnectionManager? = null

    @SuppressLint("PrivateApi")
    @Synchronized
    private fun manager(context: Context): AbsAdbConnectionManager {
        manager?.let { return it }
        // Android's own TLS library does the pairing; its key export is hidden API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
        }
        val (key, cert) = loadOrCreateKey(context)
        return object : AbsAdbConnectionManager() {
            init { setApi(Build.VERSION.SDK_INT) }
            override fun getPrivateKey(): PrivateKey = key
            override fun getCertificate(): Certificate = cert
            override fun getDeviceName(): String = "Flux Keyboard"
        }.also { manager = it }
    }

    private fun keyDir(context: Context) = File(context.noBackupFilesDir, "shell").apply { mkdirs() }

    private fun loadOrCreateKey(context: Context): Pair<PrivateKey, Certificate> {
        val keyFile = File(keyDir(context), "key")
        val certFile = File(keyDir(context), "cert")
        if (keyFile.exists() && certFile.exists()) {
            runCatching {
                val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                val cert = CertificateFactory.getInstance("X.509").generateCertificate(certFile.inputStream())
                return key to cert
            }
        }
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val certBytes = SelfSignedCertificate.create(pair.public.encoded, pair.private, "Flux Keyboard")
        keyFile.writeBytes(pair.private.encoded)
        certFile.writeBytes(certBytes)
        return pair.private to CertificateFactory.getInstance("X.509").generateCertificate(certBytes.inputStream())
    }

    // The pairing notification

    private fun notify(context: Context, text: String, codePort: Int?) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.shell_channel), NotificationManager.IMPORTANCE_HIGH)
        )
        val builder = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.shell_pair_title))
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(codePort == null)
        if (codePort != null) {
            val input = RemoteInput.Builder(KEY_CODE)
                .setLabel(context.getString(R.string.shell_pair_code_hint))
                .build()
            val intent = Intent(context, PairReceiver::class.java)
                .setAction(ACTION_PAIR)
                .putExtra(EXTRA_PORT, codePort)
            val pending = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            builder.addAction(
                android.app.Notification.Action.Builder(
                    null, context.getString(R.string.shell_pair_code_action), pending
                ).addRemoteInput(input).build()
            )
        }
        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }
}

/** A self-signed X.509 certificate for the pairing key: wireless debugging only reads its key. */
internal object SelfSignedCertificate {
    private val SHA256_WITH_RSA = byteArrayOf(
        0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B, 0x05, 0x00
    )

    fun create(publicKeyInfo: ByteArray, key: PrivateKey, commonName: String): ByteArray {
        val algorithm = tlv(0x30, SHA256_WITH_RSA)
        val name = tlv(0x30, tlv(0x31, tlv(0x30,
            byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03) + tlv(0x0C, commonName.toByteArray())
        )))
        val now = System.currentTimeMillis()
        val validity = tlv(0x30, time(now - 86_400_000L) + time(now + 20L * 365 * 86_400_000L))
        val tbs = tlv(0x30,
            tlv(0xA0, tlv(0x02, byteArrayOf(2))) +
                tlv(0x02, BigInteger.valueOf(now).toByteArray()) +
                algorithm + name + validity + name + publicKeyInfo
        )
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(key)
            update(tbs)
            sign()
        }
        return tlv(0x30, tbs + algorithm + tlv(0x03, byteArrayOf(0) + signature))
    }

    private fun time(millis: Long): ByteArray {
        val format = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return tlv(0x17, format.format(Date(millis)).toByteArray())
    }

    private fun tlv(tag: Int, value: ByteArray): ByteArray {
        val length = value.size
        val lengthBytes = when {
            length < 0x80 -> byteArrayOf(length.toByte())
            length < 0x100 -> byteArrayOf(0x81.toByte(), length.toByte())
            else -> byteArrayOf(0x82.toByte(), (length shr 8).toByte(), length.toByte())
        }
        return byteArrayOf(tag.toByte()) + lengthBytes + value
    }
}
