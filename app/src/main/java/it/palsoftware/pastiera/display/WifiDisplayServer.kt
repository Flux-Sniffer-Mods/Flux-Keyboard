package it.palsoftware.pastiera.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Looper
import kotlin.system.exitProcess

/**
 * Miracast, run by the ADB shell (`app_process`), which holds Android's Wi-Fi display permission
 * an app can't have: `scan seconds` looks for screens, `connect address seconds` connects to one
 * (looking for it first) and waits until it's connected, `disconnect` ends it and `status` says
 * how things are. Each prints [Miracast]'s lines: `state` (the feature's, the scan's and the
 * connection's states with the connected screen's address) and a `screen` line per screen found
 * or remembered. Android keeps the connection once this has ended.
 */
object WifiDisplayServer {
    /** WifiDisplayStatus.DISPLAY_STATE_CONNECTED. */
    private const val CONNECTED = 2

    @JvmStatic
    fun main(args: Array<String>) {
        Looper.prepareMainLooper()
        val manager = DisplayManager::class.java.getDeclaredConstructor(Context::class.java)
            .apply { isAccessible = true }.newInstance(ShellContext.get())
        try {
            when (args.firstOrNull()) {
                "scan" -> {
                    val seconds = args.getOrNull(1)?.toIntOrNull() ?: 8
                    call(manager, "startWifiDisplayScan")
                    Thread.sleep(seconds * 1000L)
                    print(manager)
                    call(manager, "stopWifiDisplayScan")
                }
                "connect" -> {
                    val address = args.getOrNull(1) ?: exitProcess(2)
                    val seconds = args.getOrNull(2)?.toIntOrNull() ?: 30
                    val until = System.currentTimeMillis() + seconds * 1000L
                    // Android connects only to a screen a scan has just found
                    call(manager, "startWifiDisplayScan")
                    while (System.currentTimeMillis() < until && screens(manager).none { it.address == address && it.available }) {
                        Thread.sleep(500)
                    }
                    call(manager, "stopWifiDisplayScan")
                    manager.javaClass.getMethod("connectWifiDisplay", String::class.java).invoke(manager, address)
                    while (System.currentTimeMillis() < until && active(manager) != CONNECTED) Thread.sleep(500)
                    print(manager)
                }
                "disconnect" -> {
                    call(manager, "disconnectWifiDisplay")
                    Thread.sleep(1000)
                    print(manager)
                }
                else -> print(manager)
            }
        } catch (e: Throwable) {
            System.err.println("miracast: $e")
            exitProcess(1)
        }
        exitProcess(0)
    }

    private class Found(val address: String, val name: String, val available: Boolean, val canConnect: Boolean, val remembered: Boolean)

    private fun call(manager: DisplayManager, method: String) {
        manager.javaClass.getMethod(method).invoke(manager)
    }

    private fun status(manager: DisplayManager): Any = manager.javaClass.getMethod("getWifiDisplayStatus").invoke(manager)!!

    private fun int(target: Any, method: String): Int = target.javaClass.getMethod(method).invoke(target) as Int

    private fun active(manager: DisplayManager): Int = int(status(manager), "getActiveDisplayState")

    private fun screens(manager: DisplayManager): List<Found> {
        val status = status(manager)
        val all = status.javaClass.getMethod("getDisplays").invoke(status) as Array<*>
        return all.filterNotNull().map { display ->
            fun text(method: String) = display.javaClass.getMethod(method).invoke(display) as String?
            fun flag(method: String) = runCatching { display.javaClass.getMethod(method).invoke(display) as Boolean }.getOrDefault(false)
            Found(
                address = text("getDeviceAddress").orEmpty(),
                name = text("getDeviceAlias")?.takeIf { it.isNotBlank() } ?: text("getDeviceName").orEmpty(),
                available = flag("isAvailable"),
                canConnect = flag("canConnect"),
                remembered = flag("isRemembered")
            )
        }
    }

    /** The lines [Miracast.parse] reads; names have their tabs and line breaks taken out. */
    private fun print(manager: DisplayManager) {
        val status = status(manager)
        val activeDisplay = status.javaClass.getMethod("getActiveDisplay").invoke(status)
        val activeAddress = activeDisplay?.let { it.javaClass.getMethod("getDeviceAddress").invoke(it) as String? }.orEmpty()
        println("state\t${int(status, "getFeatureState")}\t${int(status, "getScanState")}\t${int(status, "getActiveDisplayState")}\t$activeAddress")
        for (screen in screens(manager)) {
            val name = screen.name.replace(Regex("[\\t\\r\\n]"), " ")
            println("screen\t${screen.address}\t$name\t${if (screen.available) 1 else 0}\t${if (screen.canConnect) 1 else 0}\t${if (screen.remembered) 1 else 0}")
        }
        System.out.flush()
    }
}
