package it.palsoftware.pastiera.theme
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.BitmapShader
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import androidx.core.graphics.ColorUtils
import java.io.File
import it.palsoftware.pastiera.SettingsManager

/**
 * A picture behind the keyboard, kept as filesDir/keyboard_background.jpg (and in backups).
 * With Auto colours, keys go see-through and are shaded against the picture: dark keys with light
 * text over a bright picture, light keys with dark text over a dark one.
 */
object KeyboardBackgroundImage {
    const val FILE_NAME = "keyboard_background.jpg"
    private const val MAX_SIDE_PX = 1600

    private data class Cached(val stamp: Long, val bitmap: Bitmap?, val luminance: Double, val average: Int)
    @Volatile private var cached: Cached? = null

    fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun exists(context: Context) = file(context).let { it.isFile && it.length() > 0 }

    private fun load(context: Context): Cached? {
        val f = file(context)
        if (!f.isFile) return null
        val stamp = f.lastModified() xor (f.length() shl 20)
        cached?.takeIf { it.stamp == stamp }?.let { return it }
        val bitmap = runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() ?: return null
        val (luminance, average) = measure(bitmap)
        return Cached(stamp, bitmap, luminance, average).also { cached = it }
    }

    fun bitmap(context: Context): Bitmap? = load(context)?.bitmap

    /** How bright the picture is, 0 (black) to 1 (white), weighted towards the keys' area. */
    fun luminance(context: Context): Double? = load(context)?.luminance

    /** The picture's average colour, opaque: for the navigation bar under the keyboard. */
    fun averageColour(context: Context): Int? = load(context)?.average

    private fun measure(bitmap: Bitmap): Pair<Double, Int> {
        val small = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
        var lum = 0.0; var r = 0L; var g = 0L; var b = 0L
        val n = small.width * small.height
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val c = small.getPixel(x, y)
            lum += ColorUtils.calculateLuminance(c or 0xFF000000.toInt())
            r += Color.red(c); g += Color.green(c); b += Color.blue(c)
        }
        if (small !== bitmap) small.recycle()
        return lum / n to Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    /** Copies the chosen picture in, scaled down to at most 1600 px a side. */
    fun save(context: Context, uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE_PX) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        // Camera photos are stored sideways with an EXIF note of which way is up: honour it, and
        // scale down in the same step
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                android.media.ExifInterface(it).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: android.media.ExifInterface.ORIENTATION_NORMAL
        val scale = minOf(1f, MAX_SIDE_PX.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                android.media.ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                android.media.ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            }
        }
        val bitmap = if (!matrix.isIdentity) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it !== decoded) decoded.recycle() }
        } else decoded
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (!tmp.renameTo(file(context))) { tmp.copyTo(file(context), overwrite = true); tmp.delete() }
        cached = null
        SettingsManager.touchKeyboardBackgroundImage(context)
        true
    }.getOrDefault(false)

    fun remove(context: Context) {
        file(context).delete()
        cached = null
        SettingsManager.touchKeyboardBackgroundImage(context)
    }

    /**
     * The theme over the picture: the background becomes see-through. With Auto colours the keys
     * are shaded against the picture at the chosen opacity; without, the theme's key colours stay.
     */
    fun recolour(
        theme: SettingsManager.KeyboardThemeSettings,
        luminance: Double,
        autoColours: Boolean,
        keyOpacityPercent: Int,
        average: Int? = null
    ): SettingsManager.KeyboardThemeSettings {
        if (!autoColours) return theme.copy(background = Color.TRANSPARENT)
        val bright = luminance > 0.5
        val shade = if (bright) Color.BLACK else Color.WHITE
        val alpha = (keyOpacityPercent.coerceIn(0, 100) * 255 / 100)
        fun tint(a: Int) = ColorUtils.setAlphaComponent(shade, a.coerceIn(0, 255))
        val normal = tint(alpha)
        val special = tint(alpha + 40)
        // Text in black or white, whichever reads better on the key as it's seen over the picture
        // Opaque for the contrast check: the blend's alpha rounds to 254, which it refuses
        val seen = ColorUtils.blendARGB(greyOfLuminance(luminance), shade, alpha / 255f) or 0xFF000000.toInt()
        val text = if (ColorUtils.calculateContrast(Color.BLACK, seen) >= ColorUtils.calculateContrast(Color.WHITE, seen)) {
            Color.BLACK
        } else Color.WHITE
        val popup = popupOver(average ?: greyOfLuminance(luminance), text)
        return theme.copy(
            background = Color.TRANSPARENT,
            divider = tint(alpha / 2),
            normalKey = normal,
            specialKey = special,
            textAndIcons = text,
            keyPopup = popup,
            suggestion = normal,
            statusBarButton = special
        )
    }

    /**
     * Popups over the picture (accents, variations, skin tones) take its colour rather than a flat
     * grey: lightened toward white under black text (as a screen blend would) or darkened toward
     * black under white text (as a multiply would), only as far as the text needs to read well.
     * Solid, so the keys under a popup don't show through it.
     */
    internal fun popupOver(pictureColour: Int, text: Int): Int {
        val toward = if (text == Color.BLACK) Color.WHITE else Color.BLACK
        val base = pictureColour or 0xFF000000.toInt()
        var amount = 0.5f
        var popup = ColorUtils.blendARGB(base, toward, amount)
        while (ColorUtils.calculateContrast(text, popup) < 7.0 && amount < 0.95f) {
            amount += 0.05f
            popup = ColorUtils.blendARGB(base, toward, amount)
        }
        return popup or 0xFF000000.toInt()
    }

    private fun greyOfLuminance(luminance: Double): Int {
        val l = luminance.coerceIn(0.0, 1.0)
        val v = if (l <= 0.0031308) 12.92 * l else 1.055 * Math.pow(l, 1 / 2.4) - 0.055
        val c = (v * 255).toInt().coerceIn(0, 255)
        return Color.rgb(c, c, c)
    }

    /**
     * Where the picture sits: [x] and [y] from 0 (left, top) to 1 (right, bottom) of the part
     * that doesn't fit, [zoom] from 1 (just covering) up, and [turns] quarter turns clockwise.
     */
    data class Framing(val x: Float = 0.5f, val y: Float = 1f, val zoom: Float = 1f, val turns: Int = 0) {
        fun encode() = "$x,$y,$zoom,$turns"

        /** The picture turned a further quarter clockwise */
        fun rotated() = copy(turns = (turns + 1) % 4)

        /** The picture's width and height once turned */
        fun turnedSize(width: Int, height: Int): Pair<Int, Int> =
            if (turns % 2 == 1) height to width else width to height

        companion object {
            const val MAX_ZOOM = 3f

            fun decode(value: String?): Framing {
                val parts = value?.split(',')?.mapNotNull { it.toFloatOrNull() } ?: return Framing()
                if (parts.size != 3 && parts.size != 4) return Framing()
                return Framing(parts[0].coerceIn(0f, 1f), parts[1].coerceIn(0f, 1f), parts[2].coerceIn(1f, MAX_ZOOM),
                    parts.getOrNull(3)?.toInt()?.mod(4) ?: 0)
            }
        }
    }

    /** Fits [bitmapWidth] x [bitmapHeight], turned, over the destination rectangle by [framing]. */
    fun frame(
        matrix: Matrix, bitmapWidth: Int, bitmapHeight: Int,
        left: Float, top: Float, width: Float, height: Float, framing: Framing
    ) {
        val (turnedWidth, turnedHeight) = framing.turnedSize(bitmapWidth, bitmapHeight)
        val scale = maxOf(width / turnedWidth, height / turnedHeight) * framing.zoom
        // Turn about the top left corner, then bring the turned picture back to start at 0, 0
        matrix.setRotate(90f * framing.turns)
        when (framing.turns) {
            1 -> matrix.postTranslate(bitmapHeight.toFloat(), 0f)
            2 -> matrix.postTranslate(bitmapWidth.toFloat(), bitmapHeight.toFloat())
            3 -> matrix.postTranslate(0f, bitmapWidth.toFloat())
        }
        matrix.postScale(scale, scale)
        matrix.postTranslate(
            left + (width - turnedWidth * scale) * framing.x,
            top + (height - turnedHeight * scale) * framing.y
        )
    }

    /**
     * The keyboard's background: the picture, framed as chosen (centred and kept to the bottom
     * edge unless moved), under the theme's background colour. A ColorDrawable, so code that
     * recolours the background keeps the picture.
     */
    class Drawable(val bitmap: Bitmap) : ColorDrawable(Color.TRANSPARENT) {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val matrix = Matrix()
        var framing: Framing = Framing()
            set(value) {
                if (field == value) return
                field = value
                invalidateSelf()
            }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.width() > 0 && b.height() > 0) {
                frame(matrix, bitmap.width, bitmap.height, b.left.toFloat(), b.top.toFloat(),
                    b.width().toFloat(), b.height().toFloat(), framing)
                canvas.save()
                canvas.clipRect(b)
                canvas.drawBitmap(bitmap, matrix, paint)
                canvas.restore()
            }
            super.draw(canvas)
        }

        private val shaderPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        private val shaderMatrix = Matrix()

        /** Fills [path] with the picture as framed in [bounds], with smooth (anti-aliased) edges */
        fun fillPath(canvas: Canvas, path: Path) {
            val b = bounds
            if (b.width() <= 0 || b.height() <= 0) return
            frame(shaderMatrix, bitmap.width, bitmap.height, b.left.toFloat(), b.top.toFloat(),
                b.width().toFloat(), b.height().toFloat(), framing)
            shaderPaint.shader.setLocalMatrix(shaderMatrix)
            canvas.drawPath(path, shaderPaint)
        }

        override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
    }
}
