package it.palsoftware.pastiera.streaming

import android.content.Context
import it.palsoftware.pastiera.FluxLog
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * Moonlight's Wake: its wake-on-LAN packet (six 0xFF bytes, then the address it was given 16
 * times) starts streaming on the phone if it isn't running, so a game can be picked straight
 * after. Listened for in the keyboard's process, which is always running, on the ports Moonlight
 * sends to that an app can open.
 */
object StreamWake {
    private const val TAG = "StreamWake"
    /**
     * Moonlight sends its wake to 7, 9, 47998, 47999, 48000, 48002 and 48010: of those, the ones
     * an app can open and streaming doesn't use itself by UDP (48010 is its RTSP, over TCP).
     * 47009 kept for older clients.
     */
    private val PORTS = listOf(48002, 48010, 47009)
    /** Broadcasts let through to the phone (Android drops them to save battery otherwise). */
    private var multicast: android.net.wifi.WifiManager.MulticastLock? = null
    @Volatile private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        // Moonlight's wake is mostly a broadcast: let through while a Moonlight client is paired
        if (StreamHost.pairedCount(app) > 0) runCatching {
            multicast = app.getSystemService(android.net.wifi.WifiManager::class.java)
                ?.createMulticastLock("Flux:wake")?.apply { setReferenceCounted(false); acquire() }
        }.onFailure { FluxLog.w(TAG, "broadcasts can't be let through: $it") }
        for (port in PORTS) Thread({
            val socket = runCatching {
                DatagramSocket(null).apply { reuseAddress = true; broadcast = true; bind(InetSocketAddress(port)) }
            }.onFailure { FluxLog.w(TAG, "can't listen on $port: $it") }.getOrNull() ?: return@Thread
            FluxLog.w(TAG, "listening for Moonlight's wake on $port (broadcasts ${if (multicast?.isHeld == true) "let through" else "not let through"})")
            val buffer = ByteArray(256)
            while (true) {
                val packet = DatagramPacket(buffer, buffer.size)
                if (runCatching { socket.receive(packet) }.isFailure) break
                // Logged as a warning, so the crash log shows whether Moonlight's Wake arrives at all
                val ours = isWake(buffer.copyOf(packet.length), StreamHost.mac(app))
                FluxLog.w(TAG, "packet on $port from ${packet.address?.hostAddress} (${packet.length} bytes): ${if (ours) "wake for this phone" else "not this phone's wake"}")
                if (ours) wake(app)
            }
        }, "StreamWake$port").apply { isDaemon = true; start() }
    }

    private fun wake(context: Context) {
        // Only for a phone that streams at all: paired with a Moonlight client before
        if (StreamHost.pairedCount(context) == 0) { FluxLog.w(TAG, "woken, but no Moonlight client is paired"); return }
        // Its service, with its notification; when Android won't start a service from the
        // background, the streaming itself starts here, in the keyboard's process
        runCatching { StreamHostService.setEnabled(context, true) }.onFailure {
            FluxLog.w(TAG, "the streaming service couldn't start ($it): streaming without it")
            StreamHost.start(context)
        }
    }

    /** A magic packet for [mac] ("02:ab:..."): six 0xFF, then the address 16 times. */
    internal fun isWake(data: ByteArray, mac: String): Boolean {
        val address = mac.split(':').mapNotNull { it.toIntOrNull(16)?.toByte() }
        if (address.size != 6) return false
        val start = (0..data.size - 102).firstOrNull { at -> (0 until 6).all { data[at + it] == 0xFF.toByte() } } ?: return false
        return (0 until 16).all { copy -> (0 until 6).all { data[start + 6 + copy * 6 + it] == address[it] } }
    }
}
