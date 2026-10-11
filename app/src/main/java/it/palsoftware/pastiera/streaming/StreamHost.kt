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
 * finding and pairing, and an encrypted one for paired clients. Games are the games list's,
 * cloud games left out: those stream from the cloud to the TV's own Xbox app or browser
 * directly, not through the phone. Starting a stream comes next.
 */
object StreamHost {
    private const val TAG = "StreamHost"
    const val HTTP_PORT = 47989
    const val HTTPS_PORT = 47984
    private const val SERVICE_TYPE = "_nvstream._tcp"
    private const val KEY_ID = "stream_host_id"
    private const val PIN_WAIT_SECONDS = 120L

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
            "/launch", "/resume" -> xml(out, root("", 503, "Streaming from the phone isn't ready yet"))
            "/cancel" -> xml(out, root("<cancel>1</cancel>"))
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
            "</SupportedDisplayMode><PairStatus>${if (paired) 1 else 0}</PairStatus><currentgame>0</currentgame>" +
            "<state>SUNSHINE_SERVER_FREE</state>"
    )

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
                pairing = null
                pairingDevice = null
                if (ok) { remember(session.client); root("<paired>1</paired>") } else fail
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

    @Synchronized private fun unpair(cert: X509Certificate) {
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
    }

    // ---- Games ----

    /** The games streamed: the games list's, cloud games left out (they stream to the TV directly). */
    internal fun games(): List<Pair<Int, GameProfile>> = GamesActivity.games(app).map { it.first }
        .filter { profile -> profile.packages.none { it == app.packageName || GameApps.appFor(it) == GameApps.XBOX_NAME } }
        .map { appId(it) to it }

    /** A game's number for Moonlight: stable for as long as the game is listed. */
    internal fun appId(profile: GameProfile): Int = (profile.id.hashCode() and 0x7FFFFFFF).coerceAtLeast(1)

    private fun appList(): String = root(games().joinToString("") { (id, profile) ->
        "<App><IsHdrSupported>0</IsHdrSupported><AppTitle>${escape(profile.name)}</AppTitle><ID>$id</ID></App>"
    })

    /** A game's upright box art, for Moonlight's game picker. */
    private fun asset(out: OutputStream, appId: String?) {
        val profile = games().firstOrNull { it.first.toString() == appId }?.second
        val art = profile?.let { runCatching { GameArt.loadPortrait(app, it) }.getOrNull() }
        val bytes = art?.let { bitmap ->
            java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        } ?: ByteArray(0)
        respond(out, "image/png", bytes)
    }
}
