package it.palsoftware.pastiera.inputmethod.trackpad

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Reads the keyboard's touch surface through Shizuku (getevent), so keyboard swipes reach the
 * keyboard without the phone's own scrolling (on the Titan 2 Elite, Scroll assistant) having to
 * be on for the app in front. Each touch is passed on as it happens (down, moves, up), in the
 * device's own coordinates, for the keyboard's gesture handling to judge.
 */
class TrackpadGestureDetector(
    private val isEnabled: () -> Boolean,
    private val onTouch: (phase: TouchPhase, x: Float, y: Float, xRange: TrackpadAxisRange) -> Unit,
    private val scope: CoroutineScope,
    private val eventDeviceSelection: String = AUTO_EVENT_DEVICE,
    private val fallbackEventDevice: String = DEFAULT_EVENT_DEVICE,
    private val trackpadMaxX: Int = DEFAULT_TRACKPAD_MAX_X,
    private val logTag: String = DEFAULT_LOG_TAG
) {
    enum class TouchPhase { DOWN, MOVE, UP }

    private var geteventJob: Job? = null
    @Volatile
    private var geteventProcess: Process? = null
    private var touchDown = false
    private var downSent = false
    private var currentX = 0
    private var currentY = 0
    private var xSet = false
    private var ySet = false
    private var trackpadXRange = TrackpadAxisRange(0f, trackpadMaxX.toFloat())

    fun start() {
        // Guard: if already running, do nothing
        if (isRunning()) {
            Log.d(DEBUG_TAG, "start() SKIPPED: detector already running")
            return
        }
        
        val enabled = isEnabled()
        Log.d(DEBUG_TAG, "start() called - isEnabled=$enabled, eventDeviceSelection=$eventDeviceSelection")
        
        if (!enabled) {
            Log.d(DEBUG_TAG, "start() ABORTED: gestures disabled in settings")
            Log.d(logTag, "Trackpad gestures disabled in settings")
            return
        }

        val shizukuRunning = try { Shizuku.pingBinder() } catch (e: Exception) { false }
        val shizukuAuthorized = try { 
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED 
        } catch (e: Exception) { false }
        val shizukuAvailable = shizukuRunning && shizukuAuthorized
        Log.d(DEBUG_TAG, "start() Shizuku status: running=$shizukuRunning, authorized=$shizukuAuthorized, available=$shizukuAvailable")
        
        if (!shizukuAvailable) {
            val reason = when {
                !shizukuRunning -> "Shizuku not running"
                !shizukuAuthorized -> "App not authorized in Shizuku"
                else -> "Unknown"
            }
            Log.d(DEBUG_TAG, "start() ABORTED: $reason")
            Log.w(logTag, "Shizuku not available ($reason), trackpad gesture detection disabled")
            return
        }

        geteventJob?.cancel()
        Log.d(DEBUG_TAG, "start() launching getevent coroutine...")
        geteventJob = scope.launch(Dispatchers.IO) {
            try {
                val discoveredDevices = runCatching {
                    ShizukuTrackpadDeviceDiscovery.discoverBlocking()
                }.onFailure { error ->
                    Log.w(DEBUG_TAG, "Unable to discover trackpad event devices", error)
                }.getOrDefault(emptyList())
                val selectedDevice = if (eventDeviceSelection == AUTO_EVENT_DEVICE) {
                    TrackpadInputDeviceDiscovery.selectAutomatic(discoveredDevices)
                } else {
                    discoveredDevices.firstOrNull { it.path == eventDeviceSelection }
                }
                val resolvedEventDevice = when {
                    eventDeviceSelection != AUTO_EVENT_DEVICE -> eventDeviceSelection
                    selectedDevice != null -> selectedDevice.path
                    else -> fallbackEventDevice
                }
                selectedDevice?.xRange?.takeIf { it.isValid }?.let { trackpadXRange = it }

                Log.d(
                    DEBUG_TAG,
                    "Invoking Shizuku getevent for $resolvedEventDevice " +
                        "(selection=$eventDeviceSelection, detected=${selectedDevice?.displayName}, " +
                        "xRange=${trackpadXRange.min}..${trackpadXRange.max}, fallback=$fallbackEventDevice)"
                )
                val process = ShizukuTrackpadDeviceDiscovery.startProcess(
                    arrayOf("getevent", "-l", resolvedEventDevice)
                )
                geteventProcess = process

                try {
                    Log.d(DEBUG_TAG, "getevent process started successfully, reading events...")
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            parseTrackpadEvent(line)
                        }
                    }
                } finally {
                    process.destroy()
                    if (geteventProcess === process) {
                        geteventProcess = null
                    }
                }
                Log.d(DEBUG_TAG, "getevent reader loop ended")
            } catch (e: Exception) {
                Log.e(DEBUG_TAG, "getevent coroutine FAILED: ${e.message}", e)
                Log.e(logTag, "Trackpad getevent failed", e)
            }
        }
        Log.d(DEBUG_TAG, "start() completed - getevent job launched")
        Log.d(logTag, "Trackpad gesture detection started")
    }

    fun stop() {
        Log.d(DEBUG_TAG, "stop() called - had active job: ${geteventJob != null}")
        geteventProcess?.destroy()
        geteventProcess = null
        geteventJob?.cancel()
        geteventJob = null
        Log.d(logTag, "Trackpad gesture detection stopped")
    }

    /**
     * Returns true if the detector is currently running (has an active getevent job).
     */
    fun isRunning(): Boolean {
        return geteventJob != null && geteventJob?.isActive == true
    }

    /**
     * One line of getevent -l: the touch surface reports one finger (BTN_TOUCH), its position
     * (ABS_MT_POSITION_X/Y) and the end of each report (SYN_REPORT).
     */
    internal fun parseTrackpadEvent(line: String) {
        when {
            line.contains("BTN_TOUCH") && line.contains("DOWN") -> {
                touchDown = true
                downSent = false
                xSet = false
                ySet = false
            }

            line.contains("BTN_TOUCH") && line.contains("UP") -> {
                if (downSent) onTouch(TouchPhase.UP, currentX.toFloat(), currentY.toFloat(), trackpadXRange)
                touchDown = false
                downSent = false
            }

            line.contains("ABS_MT_POSITION_X") -> axisValue(line)?.let { currentX = it; xSet = true }

            line.contains("ABS_MT_POSITION_Y") -> axisValue(line)?.let { currentY = it; ySet = true }

            // A report is complete: the touch starts once its position is known, then moves
            line.contains("SYN_REPORT") && touchDown && xSet && ySet -> {
                val phase = if (downSent) TouchPhase.MOVE else TouchPhase.DOWN
                downSent = true
                onTouch(phase, currentX.toFloat(), currentY.toFloat(), trackpadXRange)
            }
        }
    }

    private fun axisValue(line: String): Int? =
        line.trim().split(Regex("\\s+")).takeIf { it.size >= 3 }?.last()?.toIntOrNull(16)

    companion object {
        const val DEFAULT_TRACKPAD_MAX_X = 1440
        const val DEFAULT_EVENT_DEVICE = TrackpadEventDeviceResolver.LEGACY_EVENT_DEVICE
        const val AUTO_EVENT_DEVICE = "auto"
        const val DEFAULT_LOG_TAG = "PastieraIME"
        private const val DEBUG_TAG = "TrackpadDebug"
    }
}
