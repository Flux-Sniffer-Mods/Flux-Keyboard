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
            "/serverinfo" -> xml(out, serverInfo(paired))
            "/pair" -> xml(out, pair(query, secure))
            "/unpair" -> { if (client != null) unpair(client); xml(out, root("")) }
            "/applist" -> xml(out, if (paired) appList() else root("", 401, "The device isn't paired"))
            "/appasset" -> if (paired) asset(out, query["appid"]) else xml(out, root("", 401, "The device isn't paired"))
            "/launch", "/resume" -> xml(out, if (paired && client != null) launch(query, socket.inetAddress, client, path == "/resume") else root("", 401, "The device isn't paired"))
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

    private fun localIp(): String = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.firstOrNull { it is Inet4Address }?.hostAddress
    }.getOrNull() ?: "0.0.0.0"

    private fun serverInfo(paired: Boolean): String = root(
        "<hostname>${escape(hostname())}</hostname><appversion>7.1.431.-1</appversion><GfeVersion>3.23.0.74</GfeVersion>" +
            "<uniqueid>${id(app)}</uniqueid><HttpsPort>$HTTPS_PORT</HttpsPort><ExternalPort>$HTTP_PORT</ExternalPort>" +
            "<MaxLumaPixelsHEVC>0</MaxLumaPixelsHEVC><mac>00:00:00:00:00:00</mac><LocalIP>${localIp()}</LocalIP>" +
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

    /** The game to open once the screen can be captured (once). */
    fun takeLaunch(): android.content.Intent? = pendingLaunch.also { pendingLaunch = null }

    private fun launch(query: Map<String, String>, client: java.net.InetAddress, cert: X509Certificate, resume: Boolean): String {
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
        if (!resume && game == null && !phoneScreen) return root("", 404, "That game isn't in the games list")
        // Asked on the phone first, unless this Moonlight client is trusted to start by itself
        val title = if (phoneScreen) app.getString(it.palsoftware.pastiera.R.string.stream_phone_screen) else game?.name
        if (!trusted(cert) && !confirm(cert, nameOf(cert) ?: client.hostAddress.orEmpty(), title)) {
            return root("", 403, "Not allowed on the phone")
        }
        session?.end()
        val started = StreamSession(app, client, appId, width, height, fps, key, keyId) { ended ->
            if (session === ended) {
                session = null
                streamingGame = null
                streamingDevice = null
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
        currentGame = appId
        streamingGame = title
        streamingDevice = nameOf(cert) ?: client.hostAddress
        StreamHostService.refresh(app)
        // The game drawn at the Moonlight client's shape and the chosen size, ahead of gaming mode's own choice
        // (the phone's own screen stays as it is, fitted into the stream)
        // A game's own streaming resolution first, else streaming's
        val choice = game?.let { g -> runCatching { it.palsoftware.pastiera.gaming.GameProfiles.effective(app, g).streamHeight }.getOrNull() }
            ?.toString() ?: StreamHostService.resolution(app)
        if (!phoneScreen) it.palsoftware.pastiera.gaming.GameMode.streaming(app, StreamHostService.phoneSize(choice, width, height))
        // A resumed game is already open; a new one opens once the screen can be captured
        pendingLaunch = if (resume || game == null) null else GamesActivity.games(app).firstOrNull { it.first.id == game.id }?.second
        capture()
        return root("<sessionUrl0>rtsp://${localIp()}:${StreamSession.RTSP_PORT}</sessionUrl0><gamesession>1</gamesession>" +
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

    /** The games streamed: the games list's, cloud games left out (they stream to the Moonlight client's device directly). */
    internal fun games(): List<Pair<Int, GameProfile>> = GamesActivity.games(app).map { it.first }
        // Cloud games only once played in Flux Keyboard's own player (so they're known to work)
        .filter { profile ->
            val cloud = profile.packages.any { it == app.packageName || GameApps.appFor(it) == GameApps.XBOX_NAME }
            !cloud || it.palsoftware.pastiera.gaming.XboxPlayActivity.played(app, profile)
        }
        .map { appId(it) to it }

    /** The phone's own screen, listed first in Moonlight when that's on. */
    const val PHONE_SCREEN_ID = 1

    /**
     * A game's number for Moonlight: stable while the game and its art stay the same. Moonlight
     * keeps a game's art by its number and never asks again, so new art (upright art, or a
     * picture of your own) gets a new number.
     */
    internal fun appId(profile: GameProfile): Int =
        ("${profile.id}|$ART_GENERATION|${GameArt.version(app, profile)}".hashCode() and 0x7FFFFFFF).coerceAtLeast(1)

    /** Raised when the art Moonlight is sent changes for every game. */
    private const val ART_GENERATION = 2

    private fun appList(): String = root(
        (if (StreamHostService.phoneScreen(app)) "<App><IsHdrSupported>0</IsHdrSupported><AppTitle>" +
            escape(app.getString(it.palsoftware.pastiera.R.string.stream_phone_screen)) + "</AppTitle><ID>$PHONE_SCREEN_ID</ID></App>" else "") +
            games().filter { it.first != PHONE_SCREEN_ID }.joinToString("") { (id, profile) ->
                "<App><IsHdrSupported>0</IsHdrSupported><AppTitle>${escape(profile.name)}</AppTitle><ID>$id</ID></App>"
            }
    )

    /** The phone screen's art: Flux Keyboard's icon on a dark upright card. */
    private fun phoneScreenArt(): android.graphics.Bitmap? = runCatching {
        val icon = app.packageManager.getApplicationIcon(app.packageName)
        val square = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 512, 512)
        icon.draw(android.graphics.Canvas(square))
        GameArt.portrait(square)
    }.getOrNull()

    /** A game's upright box art, for Moonlight's game picker. */
    private fun asset(out: OutputStream, appId: String?) {
        val profile = games().firstOrNull { it.first.toString() == appId }?.second
        val art = if (appId == PHONE_SCREEN_ID.toString()) phoneScreenArt()
            else profile?.let { runCatching { GameArt.loadPortrait(app, it) }.getOrNull() }
        val bytes = art?.let { bitmap ->
            java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        } ?: ByteArray(0)
        respond(out, "image/png", bytes)
    }
}
