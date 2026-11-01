package it.palsoftware.pastiera.airplay

import it.palsoftware.pastiera.FluxLog
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.random.Random

/**
 * One AirPlay video session with a receiver: the TV told to play a URL (the phone's own live
 * stream), kept going and watched until it stops. Paired first ([HapPairing]): transiently, or
 * with the credentials of a PIN pairing; an AirPlay 1 receiver without pairing is just asked to
 * play. Blocking calls, off the main thread.
 */
internal class AirPlaySession(private val receiver: AirPlayReceiver, private val connection: AirPlayConnection) {
    private val sessionId = Random.nextLong(0, 0xFFFFFFFFL)
    private val dacpId = "%016X".format(Random.nextLong())
    private val activeRemote = Random.nextLong(0, 0xFFFFFFFFL)
    private val appleSession = UUID.randomUUID().toString().lowercase()
    private var cseq = 0
    private var pairing: HapPairing? = null
    private var events: AirPlayConnection? = null
    private var timing: DatagramSocket? = null
    @Volatile private var open = true

    /** The phone's address as the TV reaches it (for the stream's URL). */
    val localAddress: InetAddress get() = connection.localAddress
    val remoteAddress: InetAddress get() = connection.remoteAddress

    /** Transient pairing; [PairingRefused] when the receiver wants a PIN. */
    fun pairTransient() {
        val hap = HapPairing(connection)
        hap.transient()
        encrypt(hap)
    }

    /** Verifies an earlier PIN pairing; [PairingRefused] when the receiver has forgotten it. */
    fun pairVerify(credentials: HapCredentials) {
        val hap = HapPairing(connection)
        hap.verify(credentials)
        encrypt(hap)
    }

    private fun encrypt(hap: HapPairing) {
        val (out, `in`) = hap.keys(CONTROL_SALT, CONTROL_WRITE, CONTROL_READ)
        connection.encrypt(out, `in`)
        pairing = hap
    }

    private val uri get() = "rtsp://${host(localAddress)}/$sessionId"

    private fun rtsp(method: String, path: String = uri, body: Any? = null): Message {
        val headers = mutableListOf<Pair<String, Any>>(
            "CSeq" to cseq++,
            "DACP-ID" to dacpId,
            "Active-Remote" to activeRemote,
            "Client-Instance" to dacpId,
            "User-Agent" to USER_AGENT
        )
        val data = body?.let { Bplist.encode(it) }
        if (data != null) headers += "Content-Type" to PLIST
        return connection.request(method, path, headers, data, protocol = "RTSP/1.0")
    }

    private fun httpHeaders() = listOf<Pair<String, Any>>(
        "User-Agent" to USER_AGENT,
        "Content-Type" to PLIST,
        "X-Apple-ProtocolVersion" to "1",
        "X-Apple-Session-ID" to appleSession,
        "X-Apple-Stream-ID" to "1"
    )

    /** Has the TV play [url]; false when it said no. */
    fun play(url: String, deviceName: String): Boolean {
        if (!receiver.airPlay2) {
            val answer = connection.request("POST", "/play", httpHeaders(),
                Bplist.encode(mapOf("Content-Location" to url, "Start-Position" to 0.0)))
            if (!answer.ok) FluxLog.w(TAG, "play: ${answer.first}")
            return answer.ok
        }
        val timingPort = startTiming()
        val setup = rtsp("SETUP", body = mapOf(
            "deviceID" to DEVICE_ID,
            "sessionUUID" to UUID.randomUUID().toString().uppercase(),
            "timingPort" to timingPort,
            "timingProtocol" to "NTP",
            "isMultiSelectAirPlay" to true,
            "groupContainsGroupLeader" to false,
            "macAddress" to DEVICE_ID,
            "model" to "iPhone14,3",
            "name" to deviceName,
            "osBuildVersion" to "20F66",
            "osName" to "iPhone OS",
            "osVersion" to "16.5",
            "senderSupportsRelay" to false,
            "sourceVersion" to "690.7.1",
            "statsCollectionEnabled" to false
        ))
        if (!setup.ok) { FluxLog.w(TAG, "setup: ${setup.first}"); return false }
        val eventPort = (setup.plist?.get("eventPort") as? Number)?.toInt() ?: 0
        if (eventPort > 0) startEvents(eventPort)
        startFeedback()
        rtsp("RECORD")
        val answer = connection.request("POST", "/play", httpHeaders(), Bplist.encode(mapOf(
            "Content-Location" to url,
            "Start-Position-Seconds" to 0.0,
            "uuid" to UUID.randomUUID().toString().uppercase(),
            "streamType" to 1,
            "mediaType" to "file",
            "mightSupportStorePastisKeyRequests" to true,
            "playbackRestrictions" to 0,
            "volume" to 1.0,
            "referenceRestrictions" to 3,
            "SenderMACAddress" to DEVICE_ID,
            "model" to "iPhone14,3",
            "clientBundleID" to "FluxTitanHub",
            "clientProcName" to "FluxTitanHub",
            "osBuildVersion" to "20G1116",
            "rate" to 1.0
        )))
        if (!answer.ok) { FluxLog.w(TAG, "play: ${answer.first}"); return false }
        // Paused until the rate is set
        runCatching {
            rtsp("PUT", "/setProperty?isInterestedInDateRange", mapOf("value" to true))
            rtsp("PUT", "/setProperty?actionAtItemEnd", mapOf("value" to 0))
            rtsp("POST", "/rate?value=1.000000")
        }.onFailure { FluxLog.w(TAG, "rate: $it") }
        return true
    }

    /**
     * What the TV says it's playing; null when the connection's gone. An `error` in it means it
     * gave up.
     */
    fun playbackInfo(): Map<*, *>? = runCatching {
        val answer = connection.request("GET", "/playback-info", listOf("User-Agent" to USER_AGENT))
        answer.plist ?: emptyMap<String, Any?>()
    }.getOrNull()

    fun stop() {
        if (!open) return
        open = false
        runCatching { connection.request("POST", "/stop", listOf("User-Agent" to USER_AGENT)) }
        if (receiver.airPlay2) runCatching { rtsp("TEARDOWN") }
        close()
    }

    fun close() {
        open = false
        runCatching { events?.close() }
        runCatching { timing?.close() }
        connection.close()
    }

    /** The TV's requests on the event channel, each answered OK. */
    private fun startEvents(port: Int) {
        val hap = pairing
        val channel = runCatching {
            var tries = 0
            var made: AirPlayConnection? = null
            while (made == null) {
                made = runCatching { AirPlayConnection.open(connection.remoteAddress, port) }.getOrNull()
                if (made == null) { if (++tries >= 5) throw IOException("no event channel"); Thread.sleep(500) }
            }
            made
        }.onFailure { FluxLog.w(TAG, "events: $it") }.getOrNull() ?: return
        if (hap != null) {
            val (out, `in`) = hap.keys(EVENTS_SALT, EVENTS_READ, EVENTS_WRITE)
            channel.encrypt(out, `in`)
        }
        events = channel
        Thread({
            runCatching {
                while (open) {
                    val request = channel.read()
                    val protocol = request.first.substringAfterLast(' ')
                    val headers = mutableListOf<Pair<String, Any>>("Content-Length" to 0, "Audio-Latency" to 0)
                    request.headers["cseq"]?.let { headers += "CSeq" to it }
                    request.headers["server"]?.let { headers += "Server" to it }
                    channel.send(Message.format("$protocol 200 OK", headers, null))
                }
            }
        }, "AirPlayEvents").apply { isDaemon = true; start() }
    }

    private fun startFeedback() {
        Thread({
            while (open) {
                runCatching { Thread.sleep(2_000) }
                if (open) runCatching { rtsp("POST", "/feedback") }
            }
        }, "AirPlayFeedback").apply { isDaemon = true; start() }
    }

    /** Answers the TV's NTP-style timing requests: this phone's clock. */
    private fun startTiming(): Int {
        val socket = DatagramSocket(0)
        timing = socket
        Thread({
            val buffer = ByteArray(128)
            while (open) {
                val packet = DatagramPacket(buffer, buffer.size)
                if (runCatching { socket.receive(packet) }.isFailure) break
                if (packet.length < 32) continue
                val request = ByteBuffer.wrap(buffer, 0, packet.length)
                val proto = buffer[0]
                val sendSec = request.getInt(24)
                val sendFrac = request.getInt(28)
                val now = ntpNow()
                val reply = ByteBuffer.allocate(32)
                    .put(proto).put(0xD3.toByte()).putShort(7).putInt(0)
                    .putInt(sendSec).putInt(sendFrac)
                    .putLong(now).putLong(now)
                    .array()
                runCatching { socket.send(DatagramPacket(reply, reply.size, packet.address, packet.port)) }
            }
        }, "AirPlayTiming").apply { isDaemon = true; start() }
        return socket.localPort
    }

    companion object {
        private const val TAG = "AirPlay"
        private const val USER_AGENT = "AirPlay/550.10"
        private const val PLIST = "application/x-apple-binary-plist"
        private const val DEVICE_ID = "AA:BB:CC:DD:EE:FF"
        private const val CONTROL_SALT = "Control-Salt"
        private const val CONTROL_WRITE = "Control-Write-Encryption-Key"
        private const val CONTROL_READ = "Control-Read-Encryption-Key"
        private const val EVENTS_SALT = "Events-Salt"
        private const val EVENTS_WRITE = "Events-Write-Encryption-Key"
        private const val EVENTS_READ = "Events-Read-Encryption-Key"

        fun ntpNow(): Long {
            val ms = System.currentTimeMillis()
            val seconds = ms / 1000 + 0x83AA7E80L
            val fraction = ((ms % 1000) shl 32) / 1000
            return (seconds shl 32) or fraction
        }

        /** An address as it goes in a URL: IPv6 in brackets, without its interface. */
        fun host(address: InetAddress): String =
            if (address is Inet6Address) "[" + address.hostAddress.orEmpty().substringBefore('%') + "]" else address.hostAddress.orEmpty()
    }
}
