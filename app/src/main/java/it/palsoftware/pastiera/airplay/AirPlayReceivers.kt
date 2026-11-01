package it.palsoftware.pastiera.airplay

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import it.palsoftware.pastiera.FluxLog
import java.net.Inet4Address
import java.net.InetAddress

/** An AirPlay receiver on the network, as it announces itself. */
data class AirPlayReceiver(
    val name: String,
    val host: InetAddress,
    val port: Int,
    /** Its own ID (`deviceid`), what a PIN pairing is kept under. */
    val id: String,
    val model: String,
    val features: Long,
    val flags: Long
) {
    /** AirPlay 2 (the session set up first) rather than AirPlay 1 (just asked to play). */
    val airPlay2: Boolean get() = features and (UNIFIED_MEDIA_CONTROL or CORE_UTILS_PAIRING) != 0L
    /** Pairs HomeKit's way (transient or with a PIN). */
    val pairs: Boolean get() = features and (SYSTEM_PAIRING or CORE_UTILS_PAIRING) != 0L
    /** Asks for a PIN shown on the TV before anything plays. */
    val wantsPin: Boolean get() = flags and PIN_REQUIRED != 0L
    /** Plays video sent to it (some speakers take sound only). */
    val video: Boolean get() = features == 0L || features and (VIDEO or VIDEO_2) != 0L

    companion object {
        private const val VIDEO = 1L
        private const val VIDEO_2 = 1L shl 49
        private const val SYSTEM_PAIRING = 1L shl 43
        private const val UNIFIED_MEDIA_CONTROL = 1L shl 38
        private const val CORE_UTILS_PAIRING = 1L shl 48
        private const val PIN_REQUIRED = 0x8L

        /** `features` as announced: one hex number, or two (`low,high`). */
        internal fun features(text: String?): Long {
            val parts = text?.split(',')?.map { it.trim().removePrefix("0x").removePrefix("0X") } ?: return 0
            val low = parts.getOrNull(0)?.toLongOrNull(16) ?: 0
            val high = parts.getOrNull(1)?.toLongOrNull(16) ?: 0
            return (high shl 32) or (low and 0xFFFFFFFFL)
        }

        internal fun flags(text: String?): Long = text?.trim()?.removePrefix("0x")?.toLongOrNull(16) ?: 0

        internal fun from(name: String, host: InetAddress, port: Int, txt: Map<String, String>) = AirPlayReceiver(
            name = name,
            host = host,
            port = port,
            id = txt["deviceid"]?.takeIf { it.isNotBlank() } ?: txt["pi"] ?: name,
            model = txt["model"].orEmpty(),
            features = features(txt["features"] ?: txt["ft"]),
            flags = flags(txt["flags"] ?: txt["sf"])
        )
    }
}

/**
 * Finds AirPlay receivers on the Wi-Fi (`_airplay._tcp`), each told to [onFound] once it's
 * resolved. Resolved one at a time, as older Android needs.
 */
class AirPlayReceivers(context: Context, private val onFound: (AirPlayReceiver) -> Unit) {
    private val app = context.applicationContext
    private val nsd = app.getSystemService(NsdManager::class.java)
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var lock: WifiManager.MulticastLock? = null
    private var listener: NsdManager.DiscoveryListener? = null
    @Volatile private var running = false

    fun start() {
        if (running || nsd == null) return
        running = true
        lock = runCatching {
            app.getSystemService(WifiManager::class.java)?.createMulticastLock("FluxAirPlay")?.apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { FluxLog.w(TAG, "discovery failed: $errorCode") }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(queue) { queue.addLast(serviceInfo) }
                next()
            }
        }
        listener = discovery
        runCatching { nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, discovery) }
            .onFailure { FluxLog.w(TAG, "discovery: $it") }
    }

    private fun next() {
        val service = synchronized(queue) {
            if (resolving || !running) return
            queue.removeFirstOrNull()?.also { resolving = true }
        } ?: return
        @Suppress("DEPRECATION")
        runCatching {
            nsd!!.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = done()
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = address(info)
                    if (host != null && running) {
                        val txt = info.attributes.mapValues { (_, v) -> v?.toString(Charsets.UTF_8).orEmpty() }
                            .mapKeys { it.key.lowercase() }
                        runCatching { onFound(AirPlayReceiver.from(info.serviceName, host, info.port, txt)) }
                    }
                    done()
                }
            })
        }.onFailure { done() }
    }

    private fun done() {
        synchronized(queue) { resolving = false }
        next()
    }

    /** An IPv4 address where it has one: the stream's URL is simplest that way. */
    @Suppress("DEPRECATION")
    private fun address(info: NsdServiceInfo): InetAddress? {
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            val all = info.hostAddresses
            return all.firstOrNull { it is Inet4Address } ?: all.firstOrNull()
        }
        return info.host
    }

    fun stop() {
        if (!running) return
        running = false
        listener?.let { runCatching { nsd?.stopServiceDiscovery(it) } }
        listener = null
        runCatching { lock?.release() }
        lock = null
        synchronized(queue) { queue.clear() }
    }

    private companion object {
        const val TAG = "AirPlay"
        const val TYPE = "_airplay._tcp"
    }
}
