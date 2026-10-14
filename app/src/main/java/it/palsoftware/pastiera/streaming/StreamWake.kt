package it.palsoftware.pastiera.streaming

import android.content.Context
import it.palsoftware.pastiera.FluxLog
import it.palsoftware.pastiera.adb.AdbShell
import it.palsoftware.pastiera.adb.RootShell
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
    /** Wake's ports below 1024, which only root can open: passed on to the last of [PORTS] with root. */
    private val LOW_PORTS = listOf(7, 9)
    /** Broadcasts let through to the phone (Android drops them to save battery otherwise). */
    private var multicast: android.net.wifi.WifiManager.MulticastLock? = null
    @Volatile private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        when {
            // Streaming was on when Flux Keyboard last stopped (updated, restarted, the phone
            // turned off): on again, else Moonlight sees nothing until it's turned off and on
            StreamHostService.enabled(app) -> {
                FluxLog.w(TAG, "streaming was on: started again")
                runCatching { app.startForegroundService(android.content.Intent(app, StreamHostService::class.java)) }.onFailure {
                    FluxLog.w(TAG, "streaming's service couldn't start ($it): answering Moonlight from the keyboard")
                    StreamHost.start(app)
                }
            }
            // Listed in Moonlight with streaming off, so its Wake isn't needed
            StreamHostService.alwaysAvailable(app) -> {
                FluxLog.w(TAG, "always available to Moonlight: answering with streaming off")
                StreamHost.start(app)
            }
        }
        // Moonlight's wake is mostly a broadcast: let through while a Moonlight client is paired
        if (StreamHost.pairedCount(app) > 0) runCatching {
            multicast = app.getSystemService(android.net.wifi.WifiManager::class.java)
                ?.createMulticastLock("Flux:wake")?.apply { setReferenceCounted(false); acquire() }
        }.onFailure { FluxLog.w(TAG, "broadcasts can't be let through: $it") }
        // Android's battery saving (Doze) cuts an app off the network while the phone sleeps, so a
        // wake would never arrive: Flux Keyboard let through, once the shell is up
        if (StreamHost.pairedCount(app) > 0 || StreamHostService.alwaysAvailable(app)) Thread({
            repeat(30) {
                if (AdbShell.available()) {
                    val out = AdbShell.run("cmd deviceidle whitelist +${app.packageName} 2>&1", 5_000)
                    FluxLog.w(TAG, "kept on the network while the phone sleeps: ${out?.trim()?.ifEmpty { "done" } ?: "the shell didn't answer"}")
                    // With root: the Wake sent to ports 7 and 9 (all an LG TV's Moonlight sends to),
                    // which no app can open, passed on to one Flux Keyboard listens on
                    if (RootShell.active) {
                        val redirect = LOW_PORTS.joinToString("; ") { port ->
                            "iptables -t nat -C PREROUTING -p udp --dport $port -j REDIRECT --to-ports ${PORTS.last()} 2>/dev/null || " +
                                "iptables -t nat -I PREROUTING -p udp --dport $port -j REDIRECT --to-ports ${PORTS.last()}"
                        }
                        val result = AdbShell.run("$redirect 2>&1; echo done", 5_000)
                        FluxLog.w(TAG, "Wake on ports ${LOW_PORTS.joinToString()} passed on to ${PORTS.last()} (root): ${result?.trim()?.replace("\n", " ") ?: "no answer"}")
                    }
                    return@Thread
                }
                Thread.sleep(2_000)
            }
            FluxLog.w(TAG, "not kept on the network while the phone sleeps: no shell")
        }, "StreamWakeIdle").apply { isDaemon = true; start() }
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
        // The host started afresh, so Moonlight sees it at once (a host left from before may no
        // longer answer), then its service, with its notification; when Android won't start a service from the
        // background, the streaming itself starts here, in the keyboard's process
        StreamHost.restart(context)
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
