package it.palsoftware.pastiera.streaming

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.gaming.GameApps
import it.palsoftware.pastiera.gaming.GameArt
import it.palsoftware.pastiera.gaming.GameProfile
import it.palsoftware.pastiera.gaming.GamesActivity
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * The phone as a game streaming host Moonlight can find, pair with and list games from (the
 * GameStream protocol, as Sunshine speaks it): announced on the network, a plain web server for
 * finding and pairing, and an encrypted one for paired clients. Games are the games list's
 * (cloud games once played in the phone's own player), and the phone's own screen. Launching one
 * starts a [StreamSession]: the game opens on the phone, its screen and sound go to Moonlight,
 * Moonlight's controls come back.
 */
object StreamHost {
    private const val TAG = "StreamHost"
    const val HTTP_PORT = 47989
    const val HTTPS_PORT = 47984
    private const val SERVICE_TYPE = "_nvstream._tcp"
    private const val KEY_ID = "stream_host_id"
    private const val PIN_WAIT_SECONDS = 120L
    private const val CONFIRM_WAIT_SECONDS = 60L
    private const val KEY_NAME = "stream_client_name_"
    private const val KEY_TRUSTED = "stream_client_trusted_"

    @Volatile var running = false
        private set
    private var http: ServerSocket? = null
    private var https: SSLServerSocket? = null
    private var nsd: NsdManager.RegistrationListener? = null
    private lateinit var app: Context
    private lateinit var identity: StreamCert

    /** A pairing waiting for the PIN typed on the phone. */
    @Volatile private var pairing: StreamPairing? = null
    @Volatile private var pin: CompletableFuture<String>? = null
    /** Who's asking to pair, for the PIN screen. */
    @Volatile var pairingDevice: String? = null
        private set

    private fun dir(context: Context) = File(context.filesDir, "streaming")

    /** The name Moonlight shows: the phone's. */
    fun hostname(): String = Build.MODEL.ifBlank { "Flux Keyboard" }

    private fun id(context: Context): String {
        val prefs = SettingsManager.getPreferences(context)
        return prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().uppercase().also { prefs.edit().putString(KEY_ID, it).apply() }
    }

    /**
     * The address Moonlight wakes the phone by: a stable made-up one (a phone's own isn't
     * readable), locally administered so it's no real device's.
     */
    fun mac(context: Context): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(id(context).toByteArray())
        return (listOf(0x02) + (0 until 5).map { digest[it].toInt() and 0xFF }).joinToString(":") { "%02x".format(it) }
    }

    fun start(context: Context) {
        if (running) return
        app = context.applicationContext
        running = true
        Thread {
            runCatching {
                identity = StreamCert.load(dir(app))
                http = ServerSocket(HTTP_PORT).also { serve(it, secure = false) }
                https = (tls().serverSocketFactory.createServerSocket(HTTPS_PORT) as SSLServerSocket).apply {
                    wantClientAuth = true
                }.also { serve(it, secure = true) }
                announce()
            }.onFailure {
                Log.w(TAG, "host couldn't start: $it")
                stop()
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        runCatching { nsd?.let { app.getSystemService(NsdManager::class.java)?.unregisterService(it) } }
        nsd = null
        runCatching { http?.close() }; http = null
        runCatching { https?.close() }; https = null
        pin?.cancel(true); pin = null; pairing = null; pairingDevice = null
    }

    /** On the network as a GameStream host, so Moonlight lists it by itself. */
    private fun announce() {
        val manager = app.getSystemService(NsdManager::class.java) ?: return
        val info = NsdServiceInfo().apply {
            serviceName = hostname()
            serviceType = SERVICE_TYPE
            port = HTTP_PORT
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) { Log.w(TAG, "announcing failed: $error") }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
        }
        manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        nsd = listener
    }

    /** TLS with the host's identity, taking any client certificate (paired ones are checked per request). */
    private fun tls(): SSLContext {
        val store = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("host", identity.key, CharArray(0), arrayOf(identity.cert))
        }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, CharArray(0)) }
        val anyClient = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply { init(keys.keyManagers, arrayOf(anyClient), null) }
    }

    private fun serve(server: ServerSocket, secure: Boolean) {
        Thread {
            while (running && !server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: continue
                Thread { runCatching { handle(socket, secure) }.onFailure { Log.w(TAG, "request failed: $it") }; runCatching { socket.close() } }
                    .apply { isDaemon = true; start() }
            }
        }.apply { isDaemon = true; start() }
    }

    // ---- Requests ----

    private fun handle(socket: Socket, secure: Boolean) {
        socket.soTimeout = 15_000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val line = reader.readLine() ?: return
        while (reader.readLine()?.isNotEmpty() == true) Unit
        val target = line.split(' ').getOrNull(1) ?: return
        val path = target.substringBefore('?')
        val query = parseQuery(target.substringAfter('?', ""))
        // Over TLS, a client paired before is known by its certificate
        val client = if (secure) runCatching { (socket as SSLSocket).session.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull() else null
        val paired = client != null && isPaired(client)
        val out = socket.getOutputStream()
        when (path) {
            "/serverinfo" -> xml(out, serverInfo(paired, socket))
            "/pair" -> xml(out, pair(query, secure))
            "/unpair" -> { if (client != null) unpair(client); xml(out, root("")) }
            "/applist" -> xml(out, if (paired) appList() else root("", 401, "The device isn't paired"))
            "/appasset" -> if (paired) asset(out, query["appid"]) else xml(out, root("", 401, "The device isn't paired"))
            "/launch", "/resume" -> xml(out, if (paired && client != null) launch(query, socket, client, path == "/resume") else root("", 401, "The device isn't paired"))
            "/cancel" -> { if (paired) { session?.end(); currentGame = 0 }; xml(out, root("<cancel>1</cancel>")) }
            else -> xml(out, root("", 404, "Not found"))
        }
    }

    internal fun parseQuery(query: String): Map<String, String> = query.split('&').filter { it.contains('=') }.associate {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }

    private fun root(body: String, status: Int = 200, message: String? = null): String =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?><root status_code=\"$status\"" +
            (message?.let { " status_message=\"${escape(it)}\"" } ?: "") + ">$body</root>"

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun xml(out: OutputStream, body: String) = respond(out, "application/xml", body.toByteArray())

    private fun respond(out: OutputStream, type: String, body: ByteArray) {
        out.write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
    }

    /**
     * The phone's address as the Moonlight client reached it ([socket]): over Wi-Fi, USB
     * tethering or Ethernet, whichever it came in by. Else the first one it has.
     */
    private fun localIp(socket: Socket? = null): String = socket?.localAddress
        ?.takeIf { it is Inet4Address && !it.isAnyLocalAddress && !it.isLoopbackAddress }?.hostAddress ?: runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.firstOrNull { it is Inet4Address }?.hostAddress
    }.getOrNull() ?: "0.0.0.0"

    private fun serverInfo(paired: Boolean, socket: Socket): String = root(
        "<hostname>${escape(hostname())}</hostname><appversion>7.1.431.-1</appversion><GfeVersion>3.23.0.74</GfeVersion>" +
            "<uniqueid>${id(app)}</uniqueid><HttpsPort>$HTTPS_PORT</HttpsPort><ExternalPort>$HTTP_PORT</ExternalPort>" +
            "<MaxLumaPixelsHEVC>0</MaxLumaPixelsHEVC><mac>${mac(app)}</mac><LocalIP>${localIp(socket)}</LocalIP>" +
            "<ServerCodecModeSupport>1</ServerCodecModeSupport><SupportedDisplayMode>" +
            "<DisplayMode><Width>1920</Width><Height>1080</Height><RefreshRate>60</RefreshRate></DisplayMode>" +
            "<DisplayMode><Width>1280</Width><Height>720</Height><RefreshRate>60</RefreshRate></DisplayMode>" +
            "</SupportedDisplayMode><PairStatus>${if (paired) 1 else 0}</PairStatus><currentgame>${if (paired) currentGame else 0}</currentgame>" +
            "<state>${if (currentGame != 0) "SUNSHINE_SERVER_BUSY" else "SUNSHINE_SERVER_FREE"}</state>"
    )

    // ---- Streaming ----

    /** The session streaming now, if any. */
    @Volatile internal var session: StreamSession? = null
        private set
    /** The game Moonlight started (it stays "running" between sessions, for resuming). */
    @Volatile private var currentGame = 0
    @Volatile private var pendingLaunch: android.content.Intent? = null

    /** What's streaming now, for the notification: the game, and the Moonlight client's name. */
    @Volatile var streamingGame: String? = null
        private set
    @Volatile var streamingDevice: String? = null
        private set

    /** Ends the stream from the phone: Moonlight is told, and the game stops being Moonlight's. */
    fun endStream() {
        session?.end()
        currentGame = 0
    }

    private const val SCREEN_SHARE_SETTING = "disable_screen_share_protections_for_apps_and_notifications"
    private const val KEY_SCREEN_SHARE_RESTORE = "stream_screen_share_restore"

    /**
     * Android 15 on hides notifications while the screen is captured, as it is for the stream:
     * shown as usual while streaming ([on]), Android's own setting put back after.
     */
    private fun notificationsWhileStreaming(on: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < 35 || !it.palsoftware.pastiera.adb.AdbShell.available()) return
        val prefs = SettingsManager.getPreferences(app)
        Thread {
            if (on) {
                if (!prefs.contains(KEY_SCREEN_SHARE_RESTORE)) {
                    val before = it.palsoftware.pastiera.adb.AdbShell.run("settings get global $SCREEN_SHARE_SETTING")?.trim().orEmpty()
                    prefs.edit().putString(KEY_SCREEN_SHARE_RESTORE, before).apply()
                }
                it.palsoftware.pastiera.adb.AdbShell.run("settings put global $SCREEN_SHARE_SETTING 1")
            } else {
                val before = prefs.getString(KEY_SCREEN_SHARE_RESTORE, null) ?: return@Thread
                it.palsoftware.pastiera.adb.AdbShell.run(
                    if (before == "1") "true" else if (before == "0") "settings put global $SCREEN_SHARE_SETTING 0"
                    else "settings delete global $SCREEN_SHARE_SETTING")
                prefs.edit().remove(KEY_SCREEN_SHARE_RESTORE).apply()
            }
        }.apply { isDaemon = true; start() }
    }

    /** Switches the game between the phone's screen (mirrored) and a screen of its own. */
    fun toggleScreen() {
        val current = session ?: return
        current.setSeparate(!current.separate)
    }

    /**
     * The Games app's page for the game's own screen, on the phone: it shows that screen and
     * gives it the phone's keys and touch.
     */
    fun openScreenPage(context: Context) {
        runCatching {
            context.startActivity(android.content.Intent(context, it.palsoftware.pastiera.gaming.ExtraScreenActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }

    fun closeScreenPage() = it.palsoftware.pastiera.gaming.ExtraScreenActivity.finishOpen()

    /** The game to open once the screen can be captured (once). */
    fun takeLaunch(): android.content.Intent? = pendingLaunch.also { pendingLaunch = null }

    private fun launch(query: Map<String, String>, connection: Socket, cert: X509Certificate, resume: Boolean): String {
        val client = connection.inetAddress
        val key = query["rikey"]?.let { runCatching { StreamPairing.unhex(it) }.getOrNull() }?.takeIf { it.size == 16 }
            ?: return root("", 400, "No key")
        val keyId = query["rikeyid"]?.toLongOrNull()?.toInt() ?: 0
        val mode = query["mode"]?.split('x')?.mapNotNull { it.toIntOrNull() }
        val width = mode?.getOrNull(0)?.takeIf { it > 0 } ?: 1920
        val height = mode?.getOrNull(1)?.takeIf { it > 0 } ?: 1080
        val fps = mode?.getOrNull(2)?.takeIf { it > 0 } ?: 60
        val appId = if (resume) currentGame else query["appid"]?.toIntOrNull() ?: 0
        val game = games().firstOrNull { it.first == appId }?.second
        // The phone's own screen, as it is: no game to open
        val phoneScreen = appId == PHONE_SCREEN_ID && StreamHostService.phoneScreen(app)
        // Xbox Cloud Gaming's library, in Flux Keyboard's player
        val xcloud = appId == XCLOUD_ID
        // Android's desktop on a screen of its own (the shell makes it)
        val desktop = appId == DESKTOP_ID && it.palsoftware.pastiera.adb.AdbShell.available()
        if (!resume && game == null && !phoneScreen && !xcloud && !desktop) return root("", 404, "That game isn't in the games list")
        // Asked on the phone first, unless this Moonlight client is trusted to start by itself
        val title = when {
            phoneScreen -> app.getString(it.palsoftware.pastiera.R.string.stream_phone_screen)
            xcloud -> app.getString(it.palsoftware.pastiera.R.string.games_app_xbox)
            desktop -> app.getString(it.palsoftware.pastiera.R.string.stream_screen_desktop)
            else -> game?.name
        }
        if (!trusted(cert) && !confirm(cert, nameOf(cert) ?: client.hostAddress.orEmpty(), title)) {
            return root("", 403, "Not allowed on the phone")
        }
        session?.end()
        // A game's own streaming resolution first, else streaming's: the phone's screen at the
        // Moonlight client's shape and that size while mirrored (the phone's own screen stays as it is)
        val choice = game?.let { g -> runCatching { it.palsoftware.pastiera.gaming.GameProfiles.effective(app, g).streamHeight }.getOrNull() }
            ?.toString() ?: StreamHostService.resolution(app)
        val phoneSize = if (phoneScreen || desktop) null else StreamHostService.phoneSize(choice, width, height)
        val gameIntent = when {
            xcloud -> android.content.Intent(app, it.palsoftware.pastiera.gaming.XboxPlayActivity::class.java)
                .setData(android.net.Uri.parse(it.palsoftware.pastiera.gaming.XboxPlayActivity.HOME))
            game == null -> null
            else -> GamesActivity.games(app).firstOrNull { it.first.id == game.id }?.second
        }
        val separate = desktop || gameIntent != null && StreamHostService.screenMode(app) != StreamHostService.SCREEN_MIRROR &&
            it.palsoftware.pastiera.adb.AdbShell.available()
        val started = StreamSession(app, client, appId, width, height, fps, key, keyId, gameIntent, phoneSize, separate, desktopOnly = desktop) { ended ->
            if (session === ended) {
                session = null
                streamingGame = null
                streamingDevice = null
                notificationsWhileStreaming(false)
                StreamHostService.refresh(app)
                // The screen back as gaming mode (or the phone) wants it
                it.palsoftware.pastiera.gaming.GameMode.streaming(app, null)
            }
        }
        runCatching { started.start() }.onFailure {
            Log.w(TAG, "session couldn't start: $it")
            started.end(notify = false)
            return root("", 503, "Streaming couldn't start")
        }
        session = started
        notificationsWhileStreaming(true)
        currentGame = appId
        streamingGame = title
        streamingDevice = nameOf(cert) ?: client.hostAddress
        StreamHostService.refresh(app)
        // Mirrored: the phone at the stream's shape now, the game opened once the screen can be
        // captured (a resumed one is already open). On a screen of its own, the session opens
        // or moves it there once the screen's made.
        if (!separate && phoneSize != null) it.palsoftware.pastiera.gaming.GameMode.streaming(app, phoneSize)
        pendingLaunch = if (resume || separate) null else gameIntent
        capture()
        return root("<sessionUrl0>rtsp://${localIp(connection)}:${StreamSession.RTSP_PORT}</sessionUrl0><gamesession>1</gamesession>" +
            (if (resume) "<resume>1</resume>" else ""))
    }

    /**
     * Asks to capture the screen: the shell allows it ahead so it goes straight through, and
     * captured sound needs the microphone permission. Without the shell, a notification asks.
     */
    private fun capture() {
        val pkg = app.packageName
        val activity = "$pkg/${StreamCapture::class.java.name}"
        if (it.palsoftware.pastiera.adb.AdbShell.available()) {
            it.palsoftware.pastiera.adb.AdbShell.runAsync(
                "appops set $pkg PROJECT_MEDIA allow; pm grant $pkg android.permission.RECORD_AUDIO; am start -n $activity"
            ) { result -> if (result == null) StreamPin.askCapture(app) }
        } else {
            StreamPin.askCapture(app)
        }
    }

    // ---- Pairing ----

    private fun pair(query: Map<String, String>, secure: Boolean): String {
        val fail = root("<paired>0</paired>")
        return when {
            query["phrase"] == "getservercert" -> {
                val salt = query["salt"]?.let { StreamPairing.unhex(it) } ?: return fail
                val cert = query["clientcert"]?.let { runCatching { StreamCert.fromPem(String(StreamPairing.unhex(it))) }.getOrNull() } ?: return fail
                val session = StreamPairing(identity, cert, salt)
                pairing = session
                pairingDevice = query["devicename"]?.takeIf { it.isNotBlank() && it != "roth" } ?: "Moonlight"
                // Waits for the PIN shown on the client, typed on the phone
                val waiting = CompletableFuture<String>()
                pin = waiting
                StreamPin.ask(app)
                val typed = runCatching { waiting.get(PIN_WAIT_SECONDS, TimeUnit.SECONDS) }.getOrNull()
                StreamPin.done(app)
                if (typed == null || pairing !== session) return fail
                session.pin(typed)
                root("<paired>1</paired><plaincert>${StreamPairing.hex(identity.pem.toByteArray())}</plaincert>")
            }
            query.containsKey("clientchallenge") -> pairing?.let { session ->
                runCatching { root("<paired>1</paired><challengeresponse>${StreamPairing.hex(session.clientChallenge(StreamPairing.unhex(query.getValue("clientchallenge"))))}</challengeresponse>") }.getOrNull()
            } ?: fail
            query.containsKey("serverchallengeresp") -> pairing?.let { session ->
                runCatching { root("<paired>1</paired><pairingsecret>${StreamPairing.hex(session.serverChallengeResponse(StreamPairing.unhex(query.getValue("serverchallengeresp"))))}</pairingsecret>") }.getOrNull()
            } ?: fail
            query.containsKey("clientpairingsecret") -> {
                val session = pairing ?: return fail
                val ok = runCatching { session.clientPairingSecret(StreamPairing.unhex(query.getValue("clientpairingsecret"))) }.getOrDefault(false)
                val name = pairingDevice
                pairing = null
                pairingDevice = null
                if (ok) { remember(session.client); name?.let { rename(session.client, it) }; root("<paired>1</paired>") } else fail
            }
            query["phrase"] == "pairchallenge" && secure -> root("<paired>1</paired>")
            else -> fail
        }
    }

    /** The PIN typed on the phone, for the pairing waiting on it. */
    fun enterPin(typed: String) { pin?.complete(typed.trim()) }

    /** The pairing turned down on the phone. */
    fun refusePairing() { pin?.cancel(true); pairing = null; pairingDevice = null }

    private fun clientsFile() = File(dir(app), "clients.pem")

    private fun clients(): List<X509Certificate> = runCatching {
        val text = clientsFile().takeIf { it.exists() }?.readText() ?: return emptyList()
        Regex("-----BEGIN CERTIFICATE-----[\\s\\S]*?-----END CERTIFICATE-----").findAll(text).map { StreamCert.fromPem(it.value) }.toList()
    }.getOrDefault(emptyList())

    private fun isPaired(cert: X509Certificate) = clients().any { it.encoded.contentEquals(cert.encoded) }

    @Synchronized private fun remember(cert: X509Certificate) {
        if (isPaired(cert)) return
        clientsFile().appendText(StreamCert.pem(cert.encoded))
    }

    // ---- Each Moonlight client's name, and whether it may start a stream without asking ----

    private fun fingerprint(cert: X509Certificate) = StreamPairing.hex(StreamPairing.sha256(cert.encoded))

    private fun rename(cert: X509Certificate, name: String) =
        SettingsManager.getPreferences(app).edit().putString(KEY_NAME + fingerprint(cert), name).apply()

    private fun nameOf(cert: X509Certificate): String? = SettingsManager.getPreferences(app).getString(KEY_NAME + fingerprint(cert), null)

    private fun trusted(cert: X509Certificate) = SettingsManager.getPreferences(app).getBoolean(KEY_TRUSTED + fingerprint(cert), false)

    @Volatile private var answer: CompletableFuture<Boolean>? = null
    @Volatile private var asking: X509Certificate? = null
    /** What the phone is asked: who wants to stream, and which game. */
    @Volatile var askingDevice: String? = null
        private set
    @Volatile var askingGame: String? = null
        private set

    /** Asks on the phone whether [device] may stream [game]: yes, no, or no answer in time. */
    private fun confirm(cert: X509Certificate, device: String, game: String?): Boolean {
        val waiting = CompletableFuture<Boolean>()
        answer?.cancel(true)
        answer = waiting
        asking = cert
        askingDevice = device
        askingGame = game
        StreamConfirm.ask(app)
        val allowed = runCatching { waiting.get(CONFIRM_WAIT_SECONDS, TimeUnit.SECONDS) }.getOrDefault(false)
        StreamConfirm.done(app)
        if (answer === waiting) { answer = null; asking = null; askingDevice = null; askingGame = null }
        return allowed
    }

    /** The phone's answer; [always] trusts this Moonlight client from now on. */
    fun answer(allowed: Boolean, always: Boolean) {
        val cert = asking
        if (allowed && always && cert != null) SettingsManager.getPreferences(app).edit().putBoolean(KEY_TRUSTED + fingerprint(cert), true).apply()
        answer?.complete(allowed)
    }

    @Synchronized private fun unpair(cert: X509Certificate) {
        SettingsManager.getPreferences(app).edit().remove(KEY_TRUSTED + fingerprint(cert)).remove(KEY_NAME + fingerprint(cert)).apply()
        clientsFile().writeText(clients().filterNot { it.encoded.contentEquals(cert.encoded) }.joinToString("") { StreamCert.pem(it.encoded) })
    }

    /** How many Moonlight devices are paired. */
    fun pairedCount(context: Context): Int {
        if (!::app.isInitialized) app = context.applicationContext
        return clients().size
    }

    /** Every paired device forgotten: each pairs again with a PIN. */
    fun unpairAll(context: Context) {
        if (!::app.isInitialized) app = context.applicationContext
        clientsFile().delete()
        val prefs = SettingsManager.getPreferences(app)
        prefs.edit().apply {
            prefs.all.keys.filter { it.startsWith(KEY_NAME) || it.startsWith(KEY_TRUSTED) }.forEach { key -> remove(key) }
        }.apply()
    }

    // ---- Games ----

    /** The games streamed: everything in the games list, Xbox cloud games included. */
    internal fun games(): List<Pair<Int, GameProfile>> = GamesActivity.games(app).map { it.first }
        .map { appId(it) to it }

    /** The phone's own screen, listed first in Moonlight when that's on. */
    const val PHONE_SCREEN_ID = 2
    /** Xbox Cloud Gaming's library, listed in Moonlight as XCloud Games. */
    const val XCLOUD_ID = 3
    /** Android's desktop on a screen of its own. */
    const val DESKTOP_ID = 4
    private val SPECIAL_IDS = setOf(PHONE_SCREEN_ID, XCLOUD_ID, DESKTOP_ID)

    /**
     * A game's number for Moonlight: stable while the game and its art stay the same. Moonlight
     * keeps a game's art by its number and never asks again, so new art (upright art, or a
     * picture of your own) gets a new number.
     */
    internal fun appId(profile: GameProfile): Int =
        ("${profile.id}|$ART_GENERATION|${GameArt.version(app, profile)}".hashCode() and 0x7FFFFFFF).coerceAtLeast(1)

    /** Raised when the art Moonlight is sent changes for every game. */
    private const val ART_GENERATION = 2

    private fun entry(title: String, id: Int) = "<App><IsHdrSupported>0</IsHdrSupported><AppTitle>${escape(title)}</AppTitle><ID>$id</ID></App>"

    /** Moonlight's list: the phone's screen, Android's desktop and XCloud Games, then the games. */
    private fun appList(): String = root(
        (if (StreamHostService.phoneScreen(app)) entry(app.getString(it.palsoftware.pastiera.R.string.stream_phone_screen), PHONE_SCREEN_ID) else "") +
            (if (it.palsoftware.pastiera.adb.AdbShell.available()) entry(app.getString(it.palsoftware.pastiera.R.string.stream_screen_desktop), DESKTOP_ID) else "") +
            entry(app.getString(it.palsoftware.pastiera.R.string.games_app_xbox), XCLOUD_ID) +
            games().filter { it.first !in SPECIAL_IDS }.joinToString("") { (id, profile) ->
                "<App><IsHdrSupported>0</IsHdrSupported><AppTitle>${escape(profile.name)}</AppTitle><ID>$id</ID></App>"
            }
    )

    /** An entry of the phone's own in Moonlight's list ([text]): its name on a dark grey gradient, like Flux Keyboard's key art. */
    private fun textArt(text: String): android.graphics.Bitmap? = runCatching {
        val width = 600; val height = 900
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawPaint(android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, width * 0.4f, height.toFloat(),
                0xFF4A4A4F.toInt(), 0xFF0B0B0D.toInt(), android.graphics.Shader.TileMode.CLAMP)
        })
        val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 76f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val layout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, paint, width - 120)
            .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.05f).build()
        canvas.save()
        canvas.translate(60f, (height - layout.height) / 2f)
        layout.draw(canvas)
        canvas.restore()
        bitmap
    }.getOrNull()

    /** A game's upright box art, for Moonlight's game picker. */
    private fun asset(out: OutputStream, appId: String?) {
        val profile = games().firstOrNull { it.first.toString() == appId }?.second
        val art = when (appId) {
            PHONE_SCREEN_ID.toString() -> textArt(app.getString(it.palsoftware.pastiera.R.string.stream_phone_screen_art))
            DESKTOP_ID.toString() -> textArt(app.getString(it.palsoftware.pastiera.R.string.stream_screen_desktop))
            XCLOUD_ID.toString() -> runCatching { GameArt.xboxTile(app) }.getOrNull()
            else -> null
        } ?: profile?.let { runCatching { GameArt.loadPortrait(app, it) }.getOrNull() }
        val bytes = art?.let { bitmap ->
            java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        } ?: ByteArray(0)
        respond(out, "image/png", bytes)
    }
}
