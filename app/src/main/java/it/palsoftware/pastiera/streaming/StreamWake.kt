package it.palsoftware.pastiera.streaming

import android.content.Context
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
    /** Moonlight for PCs sends to 47009, Moonlight for Android to 48002 (among ports apps can't open). */
    private val PORTS = listOf(47009, 48002)
    @Volatile private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        for (port in PORTS) Thread({
            val socket = runCatching {
                DatagramSocket(null).apply { reuseAddress = true; bind(InetSocketAddress(port)) }
            }.onFailure { Log.w(TAG, "can't listen on $port: $it") }.getOrNull() ?: return@Thread
            val buffer = ByteArray(256)
            while (true) {
                val packet = DatagramPacket(buffer, buffer.size)
                if (runCatching { socket.receive(packet) }.isFailure) break
                if (isWake(buffer.copyOf(packet.length), StreamHost.mac(app))) wake(app)
            }
        }, "StreamWake$port").apply { isDaemon = true; start() }
    }

    private fun wake(context: Context) {
        Log.i(TAG, "woken by Moonlight")
        // Only for a phone that streams at all: paired with a Moonlight client before
        if (StreamHost.pairedCount(context) == 0) return
        runCatching { StreamHostService.setEnabled(context, true) }.onFailure { Log.w(TAG, "streaming couldn't start: $it") }
    }

    /** A magic packet for [mac] ("02:ab:..."): six 0xFF, then the address 16 times. */
    internal fun isWake(data: ByteArray, mac: String): Boolean {
        val address = mac.split(':').mapNotNull { it.toIntOrNull(16)?.toByte() }
        if (address.size != 6) return false
        val start = (0..data.size - 102).firstOrNull { at -> (0 until 6).all { data[at + it] == 0xFF.toByte() } } ?: return false
        return (0 until 16).all { copy -> (0 until 6).all { data[start + 6 + copy * 6 + it] == address[it] } }
    }
}
