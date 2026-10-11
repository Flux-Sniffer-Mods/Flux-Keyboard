package it.palsoftware.pastiera.display

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import android.view.TextureView

/**
 * The extra screen, live, in a view on the phone: its own stream decoded as it arrives. Shown
 * while it's open; blank otherwise.
 */
class ScreenPreview(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var decoder: MediaCodec? = null
    private var surface: Surface? = null
    @Volatile private var waitingForKey = true
    @Volatile private var running = false

    private val frames = ExtraScreen.Frames { type, data, pts -> frame(type, data, pts) }

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = start(texture)
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean { stop(); return true }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun start(texture: SurfaceTexture) {
        val screen = ExtraScreen.current ?: return
        stop()
        val out = Surface(texture)
        val codec = runCatching {
            MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, screen.width, screen.height)
                if (android.os.Build.VERSION.SDK_INT >= 30) format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                configure(format, out, null, 0)
                start()
            }
        }.getOrNull() ?: run { out.release(); return }
        surface = out
        decoder = codec
        running = true
        waitingForKey = true
        Thread({ drain(codec) }, "ScreenPreview").apply { isDaemon = true; start() }
        ExtraScreen.addFrames(frames)
    }

    @Synchronized
    private fun frame(type: Int, data: ByteArray, pts: Long) {
        val codec = decoder ?: return
        if (type == ExtraScreen.TYPE_KEYFRAME) waitingForKey = false
        if (type == ExtraScreen.TYPE_FRAME && waitingForKey) return
        runCatching {
            val index = codec.dequeueInputBuffer(20_000)
            if (index < 0) {
                // Behind: skip to the next keyframe rather than show a broken picture
                if (type != ExtraScreen.TYPE_CONFIG) { waitingForKey = true; ExtraScreen.keyframe() }
                return
            }
            codec.getInputBuffer(index)?.apply { clear(); put(data) }
            codec.queueInputBuffer(index, 0, data.size, pts,
                if (type == ExtraScreen.TYPE_CONFIG) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0)
        }
    }

    private fun drain(codec: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (running) {
            val index = try { codec.dequeueOutputBuffer(info, 100_000) } catch (_: IllegalStateException) { break }
            if (index >= 0) runCatching { codec.releaseOutputBuffer(index, true) }
        }
    }

    @Synchronized
    private fun stop() {
        ExtraScreen.removeFrames(frames)
        running = false
        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        decoder = null
        surface?.release()
        surface = null
    }
}
