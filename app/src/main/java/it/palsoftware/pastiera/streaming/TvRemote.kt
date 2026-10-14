package it.palsoftware.pastiera.streaming

import android.content.Context
import it.palsoftware.pastiera.FluxLog
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.gaming.GameProfile
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * A TV started from the phone: woken (Wake-on-LAN), then Moonlight opened on it straight into a
 * game the phone streams, so a game can be picked on either side. LG's webOS TVs, through their
 * second-screen protocol (SSAP, a WebSocket on port 3001 or 3000), with Moonlight for webOS
 * (com.limelight.webos), which starts the game it's given as it opens (host_uuid, host_app_id).
 */
object TvRemote {
    private const val TAG = "TvRemote"
    private const val KEY_HOST = "tv_host"
    private const val KEY_NAME = "tv_name"
    private const val KEY_CLIENT = "tv_client_key"
    private const val KEY_MACS = "tv_macs"
    private const val KEY_GAMES_ON_TV = "tv_starts_games"
    private const val KEY_GAMES = "tv_games"
    private const val MOONLIGHT = "com.limelight.webos"

    data class Tv(val host: String, val name: String)

    fun tv(context: Context): Tv? {
        val prefs = SettingsManager.getPreferences(context)
        val host = prefs.getString(KEY_HOST, null) ?: return null
        return Tv(host, prefs.getString(KEY_NAME, null) ?: host)
    }

    /** Set up (paired with the TV): picking a game while streaming can start it there. */
    fun ready(context: Context): Boolean = tv(context) != null && SettingsManager.getPreferences(context).contains(KEY_CLIENT)

    /** Where a game opened while streaming plays: asked each time, or always on the TV or the phone. */
    enum class Games { ASK, TV, PHONE }

    fun games(context: Context): Games {
        val prefs = SettingsManager.getPreferences(context)
        prefs.getString(KEY_GAMES, null)?.let { name -> Games.entries.firstOrNull { it.name == name } }?.let { return it }
        // The earlier on/off switch: off kept as the phone
        return if (prefs.getBoolean(KEY_GAMES_ON_TV, true)) Games.ASK else Games.PHONE
    }

    fun setGames(context: Context, games: Games) {
        SettingsManager.getPreferences(context).edit().putString(KEY_GAMES, games.name).apply()
    }

    fun forget(context: Context) {
        SettingsManager.getPreferences(context).edit().remove(KEY_HOST).remove(KEY_NAME).remove(KEY_CLIENT).remove(KEY_MACS).apply()
    }

    // ---- Finding LG TVs on the Wi-Fi (SSDP) ----

    /** The LG TVs answering on the Wi-Fi within [timeoutMs]. Blocking. */
    fun discover(context: Context, timeoutMs: Long = 3_000): List<Tv> {
        val lock = context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
            ?.createMulticastLock("Flux:tv")?.apply { setReferenceCounted(false); acquire() }
        val found = linkedMapOf<String, Tv>()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 500
                val search = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n" +
                    "ST: urn:lge-com:service:webos-second-screen:1\r\n\r\n").toByteArray()
                val target = InetSocketAddress(InetAddress.getByName("239.255.255.250"), 1900)
                repeat(2) { socket.send(DatagramPacket(search, search.size, target)) }
                val until = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(2048)
                while (System.currentTimeMillis() < until) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    if (runCatching { socket.receive(packet) }.isFailure) continue
                    val text = String(packet.data, 0, packet.length)
                    val host = packet.address?.hostAddress ?: continue
                    if (host in found || !text.contains("webos", ignoreCase = true)) continue
                    val location = Regex("(?im)^location:\\s*(\\S+)").find(text)?.groupValues?.get(1)
                    found[host] = Tv(host, location?.let { friendlyName(it) } ?: "LG TV ($host)")
                }
            }
        } catch (e: Exception) {
            FluxLog.w(TAG, "looking for TVs failed: $e")
        } finally {
            runCatching { lock?.release() }
        }
        FluxLog.w(TAG, "TVs found: ${found.values.joinToString { "${it.name} at ${it.host}" }.ifEmpty { "none" }}")
        return found.values.toList()
    }

    /** The TV's name, from its description. */
    private fun friendlyName(location: String): String? = runCatching {
        val response = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build()
            .newCall(Request.Builder().url(location).build()).execute()
        response.use { Regex("<friendlyName>([^<]+)</friendlyName>").find(it.body?.string().orEmpty())?.groupValues?.get(1)?.trim() }
    }.getOrNull()

    // ---- The TV's protocol: a WebSocket, JSON both ways ----

    private class Link(val socket: WebSocket, val inbox: LinkedBlockingQueue<JSONObject>) {
        private var next = 0
        fun send(type: String, uri: String? = null, payload: JSONObject? = null, id: String = "flux_${next++}"): String {
            val message = JSONObject().put("type", type).put("id", id)
            uri?.let { message.put("uri", it) }
            payload?.let { message.put("payload", it) }
            socket.send(message.toString())
            return id
        }

        /** The next message for [id] (of [types]), or null after [timeoutMs]. */
        fun await(id: String, types: Set<String>, timeoutMs: Long): JSONObject? {
            val until = System.currentTimeMillis() + timeoutMs
            while (true) {
                val left = until - System.currentTimeMillis()
                if (left <= 0) return null
                val message = inbox.poll(left, TimeUnit.MILLISECONDS) ?: return null
                if (message.optString("id") == id && message.optString("type") in types) return message
                if (message.optString("type") == "closed") return null
            }
        }

        fun request(uri: String, payload: JSONObject? = null, timeoutMs: Long = 5_000): JSONObject? =
            await(send("request", uri, payload), setOf("response", "error"), timeoutMs)
                ?.takeIf { it.optString("type") == "response" }?.optJSONObject("payload")

        fun close() = runCatching { socket.close(1000, null) }
    }

    /** TLS taking the TV's own certificate (made by the TV itself, signed by no one). */
    private val client: OkHttpClient by lazy {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
        OkHttpClient.Builder()
            .sslSocketFactory(tls.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(10, TimeUnit.SECONDS)
            .build()
    }

    /** The TV's socket: secure (newer TVs) or plain (older ones); null when it doesn't answer. */
    private fun open(host: String): Link? {
        for (url in listOf("wss://$host:3001/", "ws://$host:3000/")) {
            val inbox = LinkedBlockingQueue<JSONObject>()
            val opened = LinkedBlockingQueue<Boolean>()
            val socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { opened.offer(true) }
                override fun onMessage(webSocket: WebSocket, text: String) { runCatching { inbox.offer(JSONObject(text)) } }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    opened.offer(false); inbox.offer(JSONObject().put("type", "closed"))
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { inbox.offer(JSONObject().put("type", "closed")) }
            })
            if (opened.poll(4, TimeUnit.SECONDS) == true) return Link(socket, inbox)
            socket.cancel()
        }
        return null
    }

    /**
     * Registered with the TV: with the key it gave before, else it asks on screen ([onPrompt])
     * and a new key is kept. False when it's refused or not answered within a minute.
     */
    private fun register(context: Context, link: Link, onPrompt: () -> Unit): Boolean {
        val prefs = SettingsManager.getPreferences(context)
        val payload = JSONObject(REGISTRATION)
        prefs.getString(KEY_CLIENT, null)?.let { payload.put("client-key", it) }
        val id = link.send("register", payload = payload)
        val until = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < until) {
            val message = link.await(id, setOf("response", "registered", "error"), until - System.currentTimeMillis()) ?: return false
            when (message.optString("type")) {
                "registered" -> {
                    message.optJSONObject("payload")?.optString("client-key")?.takeIf { it.isNotEmpty() }?.let {
                        prefs.edit().putString(KEY_CLIENT, it).apply()
                    }
                    return true
                }
                "response" -> if (message.optJSONObject("payload")?.optString("pairingType") == "PROMPT") onPrompt()
                else -> { FluxLog.w(TAG, "the TV refused: ${message.optString("error")}"); return false }
            }
        }
        return false
    }

    /** The TV's network addresses (wired and Wi-Fi), kept for waking it. */
    private fun keepMacs(context: Context, link: Link) {
        val info = link.request("ssap://com.webos.service.connectionmanager/getinfo") ?: return
        val macs = listOfNotNull(
            info.optJSONObject("wiredInfo")?.optString("macAddress"),
            info.optJSONObject("wifiInfo")?.optString("macAddress")
        ).filter { Regex("([0-9a-fA-F]{2}[:-]){5}[0-9a-fA-F]{2}").matches(it) }
        if (macs.isNotEmpty()) SettingsManager.getPreferences(context).edit().putString(KEY_MACS, macs.joinToString(",")).apply()
    }

    /**
     * [tv] set up: registered (the TV asks once, [onPrompt] says so), its addresses kept for
     * waking it. Blocking.
     */
    fun pair(context: Context, tv: Tv, onPrompt: () -> Unit): Boolean {
        val prefs = SettingsManager.getPreferences(context)
        if (prefs.getString(KEY_HOST, null) != tv.host) prefs.edit().remove(KEY_CLIENT).remove(KEY_MACS).apply()
        prefs.edit().putString(KEY_HOST, tv.host).putString(KEY_NAME, tv.name).apply()
        val link = open(tv.host) ?: run { FluxLog.w(TAG, "${tv.name} didn't answer"); return false }
        try {
            if (!register(context, link, onPrompt)) return false
            keepMacs(context, link)
            FluxLog.w(TAG, "set up with ${tv.name} at ${tv.host}")
            return true
        } finally {
            link.close()
        }
    }

    /** Wake-on-LAN to the TV's addresses (its "Turn on via Wi-Fi" setting on). */
    private fun wake(context: Context) {
        val macs = SettingsManager.getPreferences(context).getString(KEY_MACS, null)?.split(',').orEmpty()
        val host = tv(context)?.host
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                macs.forEach { mac ->
                    val bytes = mac.split(':', '-').map { it.toInt(16).toByte() }
                    val packet = ByteArray(6) { 0xFF.toByte() } + (1..16).flatMap { bytes }.toByteArray()
                    val targets = listOfNotNull(InetAddress.getByName("255.255.255.255"), host?.let { InetAddress.getByName(it) })
                    targets.forEach { address -> listOf(9, 7).forEach { port -> socket.send(DatagramPacket(packet, packet.size, address, port)) } }
                }
            }
        }.onFailure { FluxLog.w(TAG, "the TV couldn't be woken: $it") }
    }

    enum class Result { STARTED, NOT_SET_UP, NO_TV, REFUSED, FAILED }

    /**
     * [profile]'s game on the TV: the TV woken if it's off, then Moonlight opened there straight
     * into it (reopened, if it was open, so it takes the game). [onStep] says what's happening.
     * Blocking, up to half a minute while the TV wakes.
     */
    fun play(context: Context, profile: GameProfile, onStep: (String) -> Unit): Result {
        val tv = tv(context) ?: return Result.NOT_SET_UP
        // The phone answering Moonlight before the TV asks
        if (!StreamHostService.enabled(context)) runCatching { StreamHostService.setEnabled(context, true) }
        StreamHost.start(context)
        var link = open(tv.host)
        if (link == null) {
            onStep(context.getString(it.palsoftware.pastiera.R.string.tv_waking, tv.name))
            val until = System.currentTimeMillis() + 30_000
            while (link == null && System.currentTimeMillis() < until) {
                wake(context)
                Thread.sleep(2_000)
                link = open(tv.host)
            }
        }
        if (link == null) { FluxLog.w(TAG, "${tv.name} didn't answer or wake"); return Result.NO_TV }
        try {
            if (!register(context, link) { onStep(context.getString(it.palsoftware.pastiera.R.string.tv_accept, tv.name)) }) return Result.REFUSED
            if (!SettingsManager.getPreferences(context).contains(KEY_MACS)) keepMacs(context, link)
            // Moonlight already open: closed first, so it opens again with the game
            val front = link.request("ssap://com.webos.applicationManager/getForegroundAppInfo")?.optString("appId")
            if (front == MOONLIGHT) {
                link.request("ssap://system.launcher/close", JSONObject().put("id", MOONLIGHT))
                Thread.sleep(1_500)
            }
            val params = JSONObject().put("host_uuid", StreamHost.hostId(context)).put("host_app_id", StreamHost.appId(profile))
            val launched = link.request("ssap://system.launcher/launch", JSONObject().put("id", MOONLIGHT).put("params", params))
            FluxLog.w(TAG, "${profile.name} on ${tv.name}: ${launched ?: "no answer"}")
            return if (launched?.optBoolean("returnValue", false) == true) Result.STARTED else Result.FAILED
        } finally {
            link.close()
        }
    }

    /**
     * What the TV is asked for, with LG's own signed manifest (the TV checks its signature), as
     * LG's remote apps and the open-source ones that speak to LG TVs send it.
     */
    private val REGISTRATION = """{"forcePairing":false,"manifest":{"appVersion":"1.1","manifestVersion":1,"permissions":["LAUNCH","LAUNCH_WEBAPP","APP_TO_APP","CLOSE","CONTROL_POWER","READ_APP_STATUS","READ_NETWORK_STATE","READ_RUNNING_APPS","READ_POWER_STATE","WRITE_NOTIFICATION_TOAST","CONTROL_TV_POWER","CONTROL_WOL"],"signatures":[{"signature":"eyJhbGdvcml0aG0iOiJSU0EtU0hBMjU2Iiwia2V5SWQiOiJ0ZXN0LXNpZ25pbmctY2VydCIsInNpZ25hdHVyZVZlcnNpb24iOjF9.hrVRgjCwXVvE2OOSpDZ58hR+59aFNwYDyjQgKk3auukd7pcegmE2CzPCa0bJ0ZsRAcKkCTJrWo5iDzNhMBWRyaMOv5zWSrthlf7G128qvIlpMT0YNY+n/FaOHE73uLrS/g7swl3/qH/BGFG2Hu4RlL48eb3lLKqTt2xKHdCs6Cd4RMfJPYnzgvI4BNrFUKsjkcu+WD4OO2A27Pq1n50cMchmcaXadJhGrOqH5YmHdOCj5NSHzJYrsW0HPlpuAx/ECMeIZYDh6RMqaFM2DXzdKX9NmmyqzJ3o/0lkk/N97gfVRLW5hA29yeAwaCViZNCP8iC9aO0q9fQojoa7NQnAtw==","signatureVersion":1}],"signed":{"appId":"com.lge.test","created":"20140509","localizedAppNames":{"":"LG Remote App","ko-KR":"리모컨 앱","zxx-XX":"ЛГ Rэмotэ AПП"},"localizedVendorNames":{"":"LG Electronics"},"permissions":["TEST_SECURE","CONTROL_INPUT_TEXT","CONTROL_MOUSE_AND_KEYBOARD","READ_INSTALLED_APPS","READ_LGE_SDX","READ_NOTIFICATIONS","SEARCH","WRITE_SETTINGS","WRITE_NOTIFICATION_ALERT","CONTROL_POWER","READ_CURRENT_CHANNEL","READ_RUNNING_APPS","READ_UPDATE_INFO","UPDATE_FROM_REMOTE_APP","READ_LGE_TV_INPUT_EVENTS","READ_TV_CURRENT_TIME"],"serial":"2f930e2d2cfe083771f68e4fe7bb07","vendorId":"com.lge"}},"pairingType":"PROMPT"}"""

}
