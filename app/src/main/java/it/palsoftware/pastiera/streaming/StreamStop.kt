package it.palsoftware.pastiera.streaming

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import it.palsoftware.pastiera.R

/** The streaming notification tapped: end the stream, or keep it going. */
class StreamStop : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val device = StreamHost.streamingDevice
        if (StreamHost.session == null || device == null) { finish(); return }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.stream_end_title)
            .setMessage(getString(R.string.stream_now_to, device))
            .setPositiveButton(R.string.stream_end) { _, _ -> StreamHost.endStream(); finish() }
            .setNegativeButton(R.string.stream_keep) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
