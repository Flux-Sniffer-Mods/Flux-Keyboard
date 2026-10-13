package it.palsoftware.pastiera.streaming

import android.util.Log
import it.palsoftware.pastiera.FluxLog
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * The session's control stream: the host side of ENet (Moonlight's reliable UDP) on port 47999,
 * just enough of it for one client. Moonlight's messages arrive encrypted: keyframe requests,
 * input, a goodbye. The host only ever sends one of its own, the session's end.
 */
internal class StreamControl(
    private val cipher: StreamProtocol.ControlCipher,
    private val onMessage: (type: Int, payload: ByteArray) -> Unit,
    private val onGone: () -> Unit
) {
    private val socket = DatagramSocket(null).apply { reuseAddress = true; bind(java.net.InetSocketAddress(PORT)) }
    @Volatile private var running = true
    private var client: Pair<InetAddress, Int>? = null
    private var clientPeerId = 0
    private var verify: ByteArray? = null
    private val outgoing = IntArray(256)
    private val seen = LinkedHashSet<Int>()
    @Volatile private var lastHeard = System.currentTimeMillis()

    fun start() {
        Thread({
            val buffer = ByteArray(4096)
            socket.soTimeout = 1000
            while (running) {
                val packet = DatagramPacket(buffer, buffer.size)
                val got = runCatching { socket.receive(packet); true }.getOrDefault(false)
                if (!running) break
                if (!got) {
                    // Nothing for a while: Moonlight went away without saying
                    if (client != null && System.currentTimeMillis() - lastHeard > TIMEOUT_MS) { gone(); break }
                    continue
                }
                lastHeard = System.currentTimeMillis()
                runCatching { receive(buffer.copyOf(packet.length), packet.address, packet.port) }
                    .onFailure { FluxLog.w(TAG, "control packet: $it") }
            }
        }, "StreamControl").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        runCatching { socket.close() }
    }

    /** Tells Moonlight the session is over (the game closed, or streaming was turned off). */
    fun end() {
        // NVST_DISCONN_SERVER_TERMINATED_CLOSED: a normal end
        sendReliable(0, cipher.seal(StreamProtocol.CONTROL_TERMINATION, byteArrayOf(0x80.toByte(), 0x03, 0x00, 0x23)))
    }

    private fun gone() {
        if (!running) return
        running = false
        onGone()
    }

    private fun receive(data: ByteArray, address: InetAddress, port: Int) {
        val datagram = Enet.parse(data) ?: return
        val acks = mutableListOf<Enet.Command>()
        for (command in datagram.commands) {
            if (command.acknowledge && datagram.sentTime != null && command.type != Enet.CONNECT) acks += command
            when (command.type) {
                Enet.CONNECT -> {
                    client = address to port
                    clientPeerId = command.connect!!.outgoingPeerId
                    verify = Enet.verifyConnect(command.connect, datagram.sentTime ?: 0)
                    send(verify!!)
                }
                Enet.DISCONNECT -> {
                    if (acks.isNotEmpty()) send(Enet.acknowledgements(clientPeerId, acks, datagram.sentTime ?: 0))
                    gone()
                    return
                }
                Enet.SEND_RELIABLE, Enet.SEND_UNRELIABLE, Enet.SEND_UNSEQUENCED -> {
                    // A reliable command sent again (its acknowledgement was lost) is only handled once
                    if (command.type == Enet.SEND_RELIABLE) {
                        val key = (command.channel shl 16) or command.sequence
                        if (!seen.add(key)) continue
                        if (seen.size > 512) seen.remove(seen.first())
                    }
                    command.data?.let { body -> cipher.open(body)?.let { (type, payload) -> onMessage(type, payload) } }
                }
            }
        }
        if (acks.isNotEmpty() && client != null) send(Enet.acknowledgements(clientPeerId, acks, datagram.sentTime ?: 0))
    }

    @Synchronized
    private fun sendReliable(channel: Int, data: ByteArray) {
        if (client == null) return
        val sequence = ++outgoing[channel]
        send(Enet.reliable(clientPeerId, channel, sequence, data))
    }

    private fun send(data: ByteArray) {
        val (address, port) = client ?: return
        runCatching { socket.send(DatagramPacket(data, data.size, address, port)) }
    }

    /** ENet's packets, as far as the host needs them (its fields are big-endian). */
    object Enet {
        const val ACKNOWLEDGE = 1
        const val CONNECT = 2
        const val VERIFY_CONNECT = 3
        const val DISCONNECT = 4
        const val PING = 5
        const val SEND_RELIABLE = 6
        const val SEND_UNRELIABLE = 7
        const val SEND_FRAGMENT = 8
        const val SEND_UNSEQUENCED = 9
        private val SIZES = intArrayOf(0, 8, 48, 44, 8, 4, 6, 8, 24, 8, 12, 16, 24)
        private const val FLAG_ACKNOWLEDGE = 0x80
        private const val HEADER_SENT_TIME = 0x8000
        private const val HEADER_COMPRESSED = 0x4000
        /** The session IDs both sides start from (0xFF, "pick one"), stepped once: 0. */
        private const val SESSION = 0

        class Connect(
            val outgoingPeerId: Int, val mtu: Int, val windowSize: Int, val channelCount: Int,
            val throttleInterval: Int, val throttleAcceleration: Int, val throttleDeceleration: Int, val connectId: Int
        )

        class Command(val type: Int, val channel: Int, val sequence: Int, val acknowledge: Boolean, val data: ByteArray? = null, val connect: Connect? = null)

        class Datagram(val sentTime: Int?, val commands: List<Command>)

        fun parse(data: ByteArray): Datagram? {
            if (data.size < 2) return null
            val buffer = ByteBuffer.wrap(data)
            val peer = buffer.short.toInt() and 0xFFFF
            if (peer and HEADER_COMPRESSED != 0) return null
            val sentTime = if (peer and HEADER_SENT_TIME != 0) { if (data.size < 4) return null; buffer.short.toInt() and 0xFFFF } else null
            val commands = mutableListOf<Command>()
            while (buffer.remaining() >= 4) {
                val start = buffer.position()
                val first = buffer.get().toInt() and 0xFF
                val type = first and 0x0F
                val channel = buffer.get().toInt() and 0xFF
                val sequence = buffer.short.toInt() and 0xFFFF
                val size = SIZES.getOrNull(type) ?: break
                if (size == 0 || start + size > data.size) break
                val acknowledge = first and FLAG_ACKNOWLEDGE != 0
                when (type) {
                    CONNECT -> {
                        val outgoingPeerId = buffer.short.toInt() and 0xFFFF
                        buffer.get(); buffer.get()
                        val mtu = buffer.int; val window = buffer.int; val channels = buffer.int
                        buffer.int; buffer.int
                        val interval = buffer.int; val acceleration = buffer.int; val deceleration = buffer.int
                        val connectId = buffer.int
                        buffer.int
                        commands += Command(type, channel, sequence, acknowledge,
                            connect = Connect(outgoingPeerId, mtu, window, channels, interval, acceleration, deceleration, connectId))
                    }
                    SEND_RELIABLE -> {
                        val length = buffer.short.toInt() and 0xFFFF
                        if (buffer.remaining() < length) break
                        commands += Command(type, channel, sequence, acknowledge, ByteArray(length).also { buffer.get(it) })
                    }
                    SEND_UNRELIABLE, SEND_UNSEQUENCED -> {
                        buffer.short
                        val length = buffer.short.toInt() and 0xFFFF
                        if (buffer.remaining() < length) break
                        commands += Command(type, channel, sequence, acknowledge, ByteArray(length).also { buffer.get(it) })
                    }
                    SEND_FRAGMENT, 12 -> {
                        // Control messages are small: never split, so a fragment is skipped (still acknowledged)
                        buffer.short
                        val length = buffer.short.toInt() and 0xFFFF
                        buffer.position(minOf(data.size, start + size + length))
                        commands += Command(type, channel, sequence, acknowledge)
                    }
                    else -> {
                        buffer.position(start + size)
                        commands += Command(type, channel, sequence, acknowledge)
                    }
                }
            }
            return Datagram(sentTime, commands)
        }

        private fun header(peerId: Int, sentTime: Int?): ByteBuffer {
            val buffer = ByteBuffer.allocate(1400)
            if (sentTime != null) buffer.putShort((peerId or (SESSION shl 12) or HEADER_SENT_TIME).toShort()).putShort(sentTime.toShort())
            else buffer.putShort((peerId or (SESSION shl 12)).toShort())
            return buffer
        }

        private fun ByteBuffer.bytes() = array().copyOf(position())

        /** The answer to Moonlight's connect: its own settings back, our peer ID 0. */
        fun verifyConnect(connect: Connect, sentTime: Int): ByteArray = header(connect.outgoingPeerId, sentTime).apply {
            put((VERIFY_CONNECT or FLAG_ACKNOWLEDGE).toByte()).put(0xFF.toByte()).putShort(1)
            putShort(0).put(SESSION.toByte()).put(SESSION.toByte())
            putInt(connect.mtu).putInt(connect.windowSize).putInt(connect.channelCount.coerceIn(1, 255))
            putInt(0).putInt(0)
            putInt(connect.throttleInterval).putInt(connect.throttleAcceleration).putInt(connect.throttleDeceleration)
            putInt(connect.connectId)
        }.bytes()

        fun acknowledgements(peerId: Int, commands: List<Command>, sentTime: Int): ByteArray = header(peerId, null).apply {
            for (command in commands) {
                put(ACKNOWLEDGE.toByte()).put(command.channel.toByte()).putShort(command.sequence.toShort())
                putShort(command.sequence.toShort()).putShort(sentTime.toShort())
            }
        }.bytes()

        fun reliable(peerId: Int, channel: Int, sequence: Int, data: ByteArray): ByteArray =
            header(peerId, (System.currentTimeMillis() and 0xFFFF).toInt()).apply {
                put((SEND_RELIABLE or FLAG_ACKNOWLEDGE).toByte()).put(channel.toByte()).putShort(sequence.toShort())
                putShort(data.size.toShort()).put(data)
            }.bytes()
    }

    companion object {
        private const val TAG = "StreamControl"
        const val PORT = 47999
        private const val TIMEOUT_MS = 10_000L
    }
}
