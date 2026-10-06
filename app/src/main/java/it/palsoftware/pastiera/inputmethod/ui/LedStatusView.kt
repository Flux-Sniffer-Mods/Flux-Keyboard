package it.palsoftware.pastiera.inputmethod.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.T2eCornerCalibration
import it.palsoftware.pastiera.T2eCornerGeometry
import it.palsoftware.pastiera.inputmethod.StatusBarController
import kotlin.math.roundToInt

/**
 * Compact controller around the LED strip at the bottom of the IME status bar.
 */
class LedStatusView(
    private val context: Context
) {
    internal data class ButtonContour(
        val points: List<PointF>,
        val borderHalfWidthPx: Float
    )

    internal data class ContourGeometry(
        val buttonTopPx: Float,
        val leftButtonEndPx: Float,
        val rightButtonStartPx: Float,
        val leftButtonContour: ButtonContour? = null,
        val rightButtonContour: ButtonContour? = null
    )

    companion object {
        private val LED_COLOR_GRAY_OFF = Color.argb(100, 17, 17, 17)
        private val LED_COLOR_RED_LOCKED = Color.rgb(247, 99, 0)
        private val LED_COLOR_BLUE_ACTIVE = Color.rgb(100, 150, 255)
        private const val CONTOUR_STEPS = 64
        /** Pastiera's contour LEDs: a band this tall under the bar; the lifted fork layouts use [MERGED_LED_ZONE_HEIGHT_DP]. */
        internal const val LED_ZONE_HEIGHT_DP = 6.5f
        /** One LED contour per side (Flux Keyboard's Titan 2 Elite layouts) */
        internal const val MERGED_LED_ZONE_HEIGHT_DP = 3.1f
        /** Contoured LEDs: the rail's thickness, and the clear gap between it and the buttons */
        private const val CONTOUR_LED_STROKE_DP = 1.6f
        private const val CONTOUR_LED_GAP_DP = 2f
        /** Space between neighbouring LEDs on the rail, in rail thicknesses */
        private const val CONTOUR_LED_SPACING = 2.5f

        /**
         * Contoured LEDs: how far inside the calibrated display edge the buttons stop, corner
         * and bottom buttons alike: the LED offset, the rail and a 2 dp gap, so every button
         * sits the same distance above the LEDs and the corner ones follow their curve.
         */
        /**
         * How far the rail climbs the display's sides from its bottom edge, the same on every page:
         * 103 px on the Titan 2 Elite's 1080 px wide screen, as it looks best there
         */
        internal const val CONTOUR_RAIL_RISE_PX = 103f
        internal const val CONTOUR_RAIL_RISE_REFERENCE_WIDTH = 1080f

        internal fun contourButtonInsetPx(context: Context): Float =
            T2eCornerCalibration.read(context).ledOffsetPx +
                (2f * CONTOUR_LED_STROKE_DP + 2f * CONTOUR_LED_GAP_DP) * context.resources.displayMetrics.density

        /** How far across the display each corner's rails run, without the corner buttons' own edge. */
        private const val CONTOUR_CORNER_SHARE = 0.164f
    }

    private val ledHeight: Int by lazy {
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            5.5f,
            context.resources.displayMetrics
        ).toInt()
    }
    private val topPadding: Int by lazy {
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            1f,
            context.resources.displayMetrics
        ).toInt()
    }
    private val cornerRadius: Float by lazy {
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            context.resources.displayMetrics
        )
    }

    private var container: ModifierLedCanvas? = null
    private val ledsByState = mutableMapOf<ModifierLedState, MutableList<View>>()
    private val segmentsByView = mutableMapOf<View, ModifierLedSegment>()
    private val statePriority = mutableMapOf<ModifierLedState, Int>()

    var bottomCornerRadiiPx: Pair<Int, Int>? = null
        set(value) {
            if (field == value) return
            field = value
            container?.cornerRadiiPx = value
            container?.let { canvas ->
                for (index in 0 until canvas.childCount) canvas.getChildAt(index).invalidate()
            }
        }

    internal var layout: ModifierLedLayout = ModifierLedLayouts.DEFAULT
        set(value) {
            if (field == value) return
            field = value
            rebuildSegments()
        }

    /**
     * False when Right Shift is dedicated to another job (the emoji picker key). The merged
     * Titan 2 Elite layout then shows only Sym on its right-hand LED, so Left Shift can't light it.
     */
    var rightShiftIsShift: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            ledsByState[ModifierLedState.SHIFT].orEmpty().forEach { it.invalidate() }
        }

    var contourIntegrated: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            rebuildSegments()
            container?.invalidate()
            railChrome()?.invalidate()
        }

    internal var contourGeometry: ContourGeometry? = null
        set(value) {
            if (field == value) return
            field = value
            railChrome()?.invalidate()
            container?.let { canvas ->
                for (index in 0 until canvas.childCount) canvas.getChildAt(index).invalidate()
            }
        }

    // Locked LEDs' moving gradient (Status LED colours > Animate locked LEDs): 0..1, one sweep
    private var lockPhase = 0f
    private var lockAnimator: ValueAnimator? = null

    private fun lockAnimationOn(): Boolean = LedColors.lockedAnimationEnabled(context)

    /** Runs the sweep while an LED is locked and the option is on; stops it otherwise. */
    private fun syncLockAnimation() {
        val wanted = lockAnimationOn() && statePriority.values.any { it == 2 } && container?.isAttachedToWindow == true
        if (wanted == (lockAnimator != null)) return
        if (!wanted) {
            lockAnimator?.cancel()
            lockAnimator = null
            invalidateAllLeds()
            return
        }
        lockAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                lockPhase = it.animatedValue as Float
                invalidateAllLeds()
            }
            start()
        }
    }

    private fun invalidateAllLeds() {
        container?.let { canvas -> for (index in 0 until canvas.childCount) canvas.getChildAt(index).invalidate() }
        railChrome()?.invalidate()
    }

    /** How high the contoured LEDs rise up the corner buttons on the plain bar. */

    /** Each LED's current colour on the contoured rail. */
    private val railColors = mutableMapOf<ModifierLedSegment, Int>()
    private val railPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun railChrome(): StatusBarController.ImeChromeLayout? {
        var ancestor = container?.parent
        while (ancestor != null && ancestor !is StatusBarController.ImeChromeLayout) ancestor = ancestor.parent
        return ancestor as? StatusBarController.ImeChromeLayout
    }

    /**
     * Contoured LEDs, drawn by the keyboard frame after everything in it, in its coordinates:
     * nothing (buttons, the close button, fills) can cover them.
     */
    fun drawRailOverlay(canvas: Canvas) {
        if (!contourIntegrated || !ModifierLedLayouts.isSplit(layout)) return
        // Drawn by the frame whenever it shows, lit or not, on every page: whether the LED strip's
        // own view is shown there (menu bar, symbol and emoji pages) doesn't matter
        val chrome = railChrome() ?: return
        if (!chrome.isShown) return
        val radii = bottomCornerRadiiPx ?: return
        val density = context.resources.displayMetrics.density
        val stroke = CONTOUR_LED_STROKE_DP * density
        val gap = CONTOUR_LED_GAP_DP * density
        val outerRail = railPoints(0f) ?: return
        val innerRail = railPoints(stroke + gap) ?: return
        val width = chrome.width.toFloat()
        // Pastiera's contour LEDs: two rails round each corner button, as on the keyboard: Left
        // Shift outside and Alt inside on the left; on the right Right Shift (or the emoji key's
        // LED) outside, and Sym and Ctrl sharing the inside
        // The same length on every panel: up to the panels' shared side-button width
        val side = chrome.ledSideWidthPx.takeIf { it > 0 }?.toFloat() ?: (width * CONTOUR_CORNER_SHARE)
        val leftEnd = side
        val rightStart = width - side
        val emojiLed = layout.segments.any { it.state == ModifierLedState.EMOJI }
        val rightOuter = if (emojiLed) ModifierLedState.EMOJI else ModifierLedState.SHIFT

        val calibration = T2eCornerCalibration.read(context)
        val outward = maxOf(radii.first, radii.second) * calibration.size + calibration.offsetPx + stroke
        // The outer rails also fill outward, down to the bottom edge and sideways into the square
        // corners, over whatever is there; everything inside them stays as it is
        val outerInside = T2eCornerGeometry.path(
            width, chrome.height.toFloat(), radii.first.toFloat(), radii.second.toFloat(),
            calibration, calibration.ledOffsetPx + stroke
        )
        val save = canvas.save()
        canvas.clipOutPath(outerInside)
        drawRail(canvas, chrome, sidePart(outerRail, leftEnd, left = true), ModifierLedState.SHIFT, 2f * outward)
        if (rightOuter != ModifierLedState.SHIFT || rightShiftIsShift) {
            drawRail(canvas, chrome, sidePart(outerRail, rightStart, left = false), rightOuter, 2f * outward)
        } else {
            drawRail(canvas, chrome, sidePart(outerRail, rightStart, left = false), null, 2f * outward)
        }
        canvas.restoreToCount(save)
        drawRail(canvas, chrome, sidePart(innerRail, leftEnd, left = true), ModifierLedState.ALT, 0f)
        // Right inside: Sym from the top, Ctrl towards the middle, a little apart
        val right = sidePart(innerRail, rightStart, left = false)
        if (right.size >= 2) {
            val path = polyline(right)
            val measure = PathMeasure(path, false)
            val apart = stroke * CONTOUR_LED_SPACING / 2f
            val half = measure.length * 0.5f
            drawRail(canvas, chrome, null, ModifierLedState.SYM, 0f, measure, 0f, half - apart)
            drawRail(canvas, chrome, null, ModifierLedState.CTRL, 0f, measure, half + apart, measure.length)
        }
    }

    /** An LED's colour now, as its drawable last set it. */
    private fun railColor(state: ModifierLedState?): Int {
        if (state == null) return ledColor(ModifierLedState.SHIFT, 0)
        val segment = layout.segments.firstOrNull { it.state == state }
        return segment?.let { railColors[it] } ?: ledColor(state, statePriority[state] ?: 0)
    }

    /** One LED on a rail: [points] (or a [measure]d part of a rail), widened outward by [extraWidth]. */
    private fun drawRail(
        canvas: Canvas,
        chrome: StatusBarController.ImeChromeLayout,
        points: List<PointF>?,
        state: ModifierLedState?,
        extraWidth: Float,
        measure: PathMeasure? = null,
        from: Float = 0f,
        to: Float = 0f
    ) {
        val raw = railColor(state)
        // Only lit LEDs (apps with the keyboard hidden): nothing where one is off
        if (hideOffLeds && Color.alpha(raw) == 0) return
        // Unlit LEDs too, opaque: their see-through grey is mixed with the keyboard's colour so the
        // whole rail, lit or not, covers what is under it the same way
        val color = if (Color.alpha(raw) == 255) raw
            else androidx.core.graphics.ColorUtils.compositeColors(raw, chrome.bottomFillColors.first or 0xFF000000.toInt())
        railPaint.shader = null
        railPaint.color = color
        if (state != null && lockAnimator != null && statePriority[state] == 2) {
            railPaint.shader = lockShader(color, chrome.width.toFloat())
        }
        val piece = if (measure != null) {
            if (to <= from) return
            Path().also { measure.getSegment(from, to, it, true) }
        } else {
            if (points == null || points.size < 2) return
            polyline(points)
        }
        val stroke = CONTOUR_LED_STROKE_DP * context.resources.displayMetrics.density
        railPaint.style = Paint.Style.STROKE
        railPaint.strokeWidth = stroke + extraWidth
        // Widened outward: square ends, which fan apart round the convex corners and never overlap
        railPaint.strokeCap = if (extraWidth > 0f) Paint.Cap.BUTT else Paint.Cap.ROUND
        railPaint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(piece, railPaint)
    }

    private fun polyline(points: List<PointF>): Path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }

    /**
     * A corner's part of a rail: from the top of the display's side round the corner to [limitX]
     * (the corner button's inner edge), always starting at the top.
     */
    private fun sidePart(rail: List<PointF>, limitX: Float, left: Boolean): List<PointF> {
        val ordered = if (left) rail else rail.asReversed()
        val part = ArrayList<PointF>()
        for (point in ordered) {
            val inside = if (left) point.x <= limitX else point.x >= limitX
            if (inside) {
                part += point
                continue
            }
            val previous = part.lastOrNull()
            if (previous != null && point.x != previous.x) {
                val ratio = ((limitX - previous.x) / (point.x - previous.x)).coerceIn(0f, 1f)
                part += PointF(limitX, previous.y + (point.y - previous.y) * ratio)
            }
            break
        }
        return part
    }

    /** The gradient a locked LED sweeps: its colour, a more intense version, and back. */
    private fun lockShader(color: Int, width: Float): android.graphics.Shader {
        val intense = LedColors.intensify(color)
        val deep = LedColors.deepen(color)
        val span = width.coerceAtLeast(1f)
        val offset = lockPhase * span * 2f
        return android.graphics.LinearGradient(
            offset - span, 0f, offset + span, 0f,
            intArrayOf(deep, intense, deep), floatArrayOf(0f, 0.5f, 1f),
            android.graphics.Shader.TileMode.MIRROR
        )
    }

    var onLongPressListener: (() -> Unit)? = null
    var themeOverride: KeyboardThemeColors? = null

    fun ensureView(): ViewGroup {
        container?.let { return it }

        container = ModifierLedCanvas(context, ledHeight).apply {
            cornerRadiiPx = bottomCornerRadiiPx
            setPadding(0, topPadding, 0, 0)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnLongClickListener {
                onLongPressListener?.invoke()
                true
            }
        }

        rebuildSegments()

        return container!!
    }

    fun getView(): ViewGroup? = container

    fun update(snapshot: StatusBarController.StatusSnapshot) {
        val shiftLocked = snapshot.capsLockEnabled
        val shiftActive = (snapshot.shiftPhysicallyPressed || snapshot.shiftOneShot) && !shiftLocked
        updateLeds(ModifierLedState.SHIFT, shiftLocked, shiftActive)

        val ctrlLocked = snapshot.ctrlLatchActive
        val ctrlActive = (snapshot.ctrlPhysicallyPressed || snapshot.ctrlOneShot) && !ctrlLocked
        updateLeds(ModifierLedState.CTRL, ctrlLocked, ctrlActive)

        val altLocked = snapshot.altLatchActive
        val altActive = (snapshot.altPhysicallyPressed || snapshot.altOneShot) && !altLocked
        updateLeds(ModifierLedState.ALT, altLocked, altActive)

        updateSymLeds(snapshot)
        syncLockAnimation()
    }

    private fun rebuildSegments() {
        val canvas = container ?: return
        ledsByState.clear()
        segmentsByView.clear()
        canvas.replaceSegments(layout.segments) { segment ->
            createLedView(ledColor(segment.state, 0), segment).also { led ->
                ledsByState.getOrPut(segment.state) { mutableListOf() }.add(led)
            }
        }
    }

    private fun createLedView(initialColor: Int, segment: ModifierLedSegment): View {
        return View(context).apply {
            segmentsByView[this] = segment
            background = createDrawable(initialColor, segment)
            setTag(R.id.led_previous_color, initialColor)
        }
    }

    private fun createDrawable(color: Int, segment: ModifierLedSegment): GradientDrawable {
        railColors[segment] = color
        railChrome()?.invalidate()
        return object : GradientDrawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }

            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

            override fun draw(canvas: Canvas) {
                val radii = bottomCornerRadiiPx
                if (radii == null) {
                    if (lockAnimator != null && statePriority[segment.state] == 2) {
                        // Locked: the LED's colour sweeps to a more intense version and back
                        fillPaint.shader = lockShader(color, bounds.width().toFloat())
                        val box = android.graphics.RectF(bounds)
                        canvas.drawRoundRect(box, this@LedStatusView.cornerRadius, this@LedStatusView.cornerRadius, fillPaint)
                        return
                    }
                    super.draw(canvas)
                    return
                }
                paint.shader = null
                // Pastiera's contour LEDs: rails along the outer buttons (bar not lifted)
                // (drawn by the keyboard frame over everything else: see drawRailOverlay)
                if (contourIntegrated && ModifierLedLayouts.isSplit(layout)) return
                // One physical contour per side in rounded mode. Alt/Sym and
                // Shift share it; a locked modifier wins over an active one.
                if (layout == ModifierLedLayouts.TITAN_2_ELITE && segment.y == 0f) return
                if (layout == ModifierLedLayouts.TITAN_2_ELITE && segment.state == ModifierLedState.SHIFT) {
                    val rightSide = segment.x >= 0.5f
                    val otherState = if (rightSide) ModifierLedState.SYM else ModifierLedState.ALT
                    val shiftPriority = if (rightSide && !rightShiftIsShift) 0
                        else statePriority[ModifierLedState.SHIFT] ?: 0
                    val priority = maxOf(shiftPriority, statePriority[otherState] ?: 0)
                    // The colour of whichever modifier lights the shared LED
                    val shown = if (shiftPriority >= (statePriority[otherState] ?: 0)) ModifierLedState.SHIFT else otherState
                    paint.color = ledColor(shown, priority)
                    if (lockAnimator != null && priority == 2) paint.shader = lockShader(paint.color, bounds.width().toFloat())
                }
                val width = bounds.width().toFloat()
                val height = bounds.height().toFloat()
                // Each row follows a concentric contour, so the indicator retains
                // its thickness through the bend instead of being cut off by it.
                val inset = (1f - segment.y - segment.height / 2f) * ledHeight + topPadding
                val leftRadius = radii.first.toFloat().coerceIn(inset, maxOf(inset, width / 2f))
                val rightRadius = radii.second.toFloat().coerceIn(inset, maxOf(inset, width / 2f))
                val leftArc = leftRadius - inset
                val rightArc = rightRadius - inset
                // One LED per modifier: spread along the corners and bottom edge only; the vertical
                // side runs sit behind the bar and would swallow the outer LEDs.
                val cornersAndBottomOnly = ModifierLedLayouts.isSplit(layout)
                val contour = straightContour() ?: liftedContour(radii) ?: Path().apply {
                    if (cornersAndBottomOnly) {
                        moveTo(inset, height - leftRadius)
                    } else {
                        moveTo(inset, 0f)
                        lineTo(inset, height - leftRadius)
                    }
                    if (leftArc > 0f) {
                        arcTo(inset, height - leftRadius - leftArc,
                            leftRadius + leftArc, height - inset, 180f, -90f, false)
                    }
                    lineTo(width - rightRadius, height - inset)
                    if (rightArc > 0f) {
                        arcTo(width - rightRadius - rightArc, height - rightRadius - rightArc,
                            width - inset, height - inset, 90f, -90f, false)
                    }
                    if (!cornersAndBottomOnly) lineTo(width - inset, 0f)
                }
                if (lockAnimator != null && statePriority[segment.state] == 2 && paint.shader == null &&
                    !(layout == ModifierLedLayouts.TITAN_2_ELITE && segment.state == ModifierLedState.SHIFT)
                ) {
                    paint.shader = lockShader(paint.color, width)
                }
                val measure = PathMeasure(contour, false)
                paint.strokeWidth = segment.height * ledHeight
                val joinRightIndicators = layout == ModifierLedLayouts.TITAN_2_ELITE &&
                    (segment.state == ModifierLedState.CTRL ||
                        (segment.state == ModifierLedState.SHIFT && segment.x > 0.5f))
                paint.strokeCap = if (joinRightIndicators) Paint.Cap.BUTT else Paint.Cap.ROUND
                // Leave room for the round caps at both ends of each segment.
                val cap = if (joinRightIndicators) 0f else paint.strokeWidth / 2f
                val start = measure.length * segment.x + cap
                val endFraction = if (joinRightIndicators && segment.state == ModifierLedState.CTRL) {
                    layout.segments.first { it.state == ModifierLedState.SHIFT && it.x > segment.x }.x
                } else segment.x + segment.width
                val end = measure.length * endFraction - cap
                if (end > start) {
                    val stroke = Path()
                    measure.getSegment(start, end, stroke, true)
                    canvas.drawPath(stroke, paint)
                }
            }
        }.apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = this@LedStatusView.cornerRadius
        }
    }

    /**
     * Titan 2 Elite with the status bar lifted: the LEDs run in the band under the bar, on the
     * calibrated display curve (the one the outer buttons use) raised by half the lift, so they
     * stay clear of the physical corners. Null when the bar isn't lifted.
     */
    /**
     * Straight outer buttons: the corner buttons fill the corners, so the LEDs run in a straight
     * line through the band under the other buttons or keys, between the two corner buttons.
     */
    private fun straightContour(): Path? {
        val canvasView = container ?: return null
        var ancestor = canvasView.parent
        while (ancestor != null && ancestor !is StatusBarController.ImeChromeLayout) ancestor = ancestor.parent
        val chrome = ancestor as? StatusBarController.ImeChromeLayout ?: return null
        val span = chrome.straightLedSpanPx ?: return null
        // Top of the band under the buttons: the bar's row, or an emoji/SYM screen's bottom keys
        val rowBottom = chrome.straightLedBandTopPx
        if (rowBottom < 0 || rowBottom >= chrome.height) return null
        val location = IntArray(2)
        val chromeLocation = IntArray(2)
        canvasView.getLocationInWindow(location)
        chrome.getLocationInWindow(chromeLocation)
        val lineY = (rowBottom + chrome.height) / 2f
        return Path().apply {
            moveTo(span.first.toFloat(), lineY)
            lineTo(span.second.toFloat(), lineY)
            // Chrome coordinates to this view's
            offset((chromeLocation[0] - location[0]).toFloat(), (chromeLocation[1] - location[1]).toFloat())
        }
    }

    private fun liftedContour(radii: Pair<Int, Int>): Path? {
        val canvasView = container ?: return null
        var ancestor = canvasView.parent
        while (ancestor != null && ancestor !is StatusBarController.ImeChromeLayout) ancestor = ancestor.parent
        val chrome = ancestor as? StatusBarController.ImeChromeLayout ?: return null
        val lift = chrome.nestedRowLiftPx
        if (lift <= 0 || chrome.width <= 0 || chrome.height <= 0) return null
        val location = IntArray(2)
        val chromeLocation = IntArray(2)
        canvasView.getLocationInWindow(location)
        chrome.getLocationInWindow(chromeLocation)
        val x = (location[0] - chromeLocation[0]).toFloat()
        val y = (location[1] - chromeLocation[1]).toFloat()
        val calibration = T2eCornerCalibration.read(context)
        val width = chrome.width.toFloat()
        val bottom = chrome.height - lift / 2f
        val left = radii.first.toFloat().coerceIn(0f, width / 2f)
        val right = radii.second.toFloat().coerceIn(0f, width / 2f)
        fun point(radius: Float, step: Int): T2eCornerGeometry.Point =
            T2eCornerGeometry.point(radius, bottom, Math.PI / 2 * step / CONTOUR_STEPS, calibration)
        // Left arc, bottom, right arc; LED segments are spread over what shows below the bar
        val points = (0..CONTOUR_STEPS).map { point(left, it) } +
            (CONTOUR_STEPS downTo 0).map { point(right, it).let { p -> T2eCornerGeometry.Point(width - p.x, p.y) } }
        val rowBottom = chrome.nestedRowBottomPx
        val visibleTop = if (rowBottom >= 0) rowBottom + ledHeight / 2f - calibration.shiftYPx else Float.NEGATIVE_INFINITY
        val visible = points.filter { it.y >= visibleTop }.takeIf { it.size >= 2 } ?: points
        return Path().apply {
            moveTo(visible.first().x, visible.first().y)
            visible.drop(1).forEach { lineTo(it.x, it.y) }
            // Chrome coordinates to this view's, plus the calibrated shift the buttons use
            offset(calibration.shiftXPx - x, calibration.shiftYPx - y)
        }
    }

    /**
     * An LED's colour at [level] (0 off, 1 active, 2 locked): its own colour when each LED is
     * coloured individually, otherwise the theme's shared LED colours.
     */
    /** Only lit LEDs show (apps with the keyboard hidden): an LED that's off isn't drawn. */
    var hideOffLeds: Boolean = false

    private fun ledColor(state: ModifierLedState, level: Int): Int {
        if (level == 0 && hideOffLeds) return Color.TRANSPARENT
        if (LedColors.enabled(context)) {
            val base = LedColors.baseColor(context, LedColors.ledFor(state))
            return LedColors.shade(base, when (level) {
                2 -> LedColors.Level.LOCKED
                1 -> LedColors.Level.ACTIVE
                else -> LedColors.Level.OFF
            })
        }
        val theme = themeOverride
        return when (level) {
            2 -> theme?.ledLocked ?: LED_COLOR_RED_LOCKED
            1 -> theme?.ledActive ?: LED_COLOR_BLUE_ACTIVE
            else -> theme?.ledInactive ?: LED_COLOR_GRAY_OFF
        }
    }

    /**
     * A rail in the keyboard frame's coordinates (with the calibrated shift the buttons use),
     * [extraInset] further inside than the outer one: up the display's left side past the curve,
     * round the bottom, and up the right side, the same height on every page.
     */
    private fun railPoints(extraInset: Float): List<PointF>? {
        val radii = bottomCornerRadiiPx ?: return null
        val chrome = railChrome() ?: return null
        if (chrome.width <= 0 || chrome.height <= 0) return null
        val calibration = T2eCornerCalibration.read(context)
        val stroke = CONTOUR_LED_STROKE_DP * context.resources.displayMetrics.density
        val inset = calibration.ledOffsetPx + stroke / 2f + extraInset
        val width = chrome.width.toFloat()
        val bottom = chrome.height.toFloat()
        val left = radii.first.toFloat().coerceIn(0f, width / 2f)
        val right = radii.second.toFloat().coerceIn(0f, width / 2f)
        fun point(radius: Float, step: Int) =
            T2eCornerGeometry.point(radius, bottom, Math.PI / 2 * step / CONTOUR_STEPS, calibration, inset)
        val leftArc = (0..CONTOUR_STEPS).map { point(left, it) }
        val rightArc = (CONTOUR_STEPS downTo 0).map { point(right, it).let { p -> T2eCornerGeometry.Point(width - p.x, p.y) } }
        val top = bottom - CONTOUR_RAIL_RISE_PX * width / CONTOUR_RAIL_RISE_REFERENCE_WIDTH
        val points = buildList {
            if (top < leftArc.first().y) add(T2eCornerGeometry.Point(leftArc.first().x, top))
            addAll(leftArc)
            addAll(rightArc)
            if (top < rightArc.last().y) add(T2eCornerGeometry.Point(rightArc.last().x, top))
        }
        return points.map { PointF(it.x + calibration.shiftXPx, it.y + calibration.shiftYPx) }
    }

    private fun updateLeds(state: ModifierLedState, isLocked: Boolean, isActive: Boolean = false) {
        val level = if (isLocked) 2 else if (isActive) 1 else 0
        statePriority[state] = level
        val targetColor = ledColor(state, level)
        ledsByState[state].orEmpty().forEach { led -> animateLedColor(led, targetColor) }
    }

    /**
     * SYM: active while held, locked while it's sticky or a symbols page is open. The emoji key's
     * LED, when shown, works the same way for the emoji layer and picker; without it, those
     * pages show on the SYM LED as active.
     */
    private fun updateSymLeds(snapshot: StatusBarController.StatusSnapshot) {
        val symPage = snapshot.symPage
        val emojiPage = symPage == 1 || symPage == 4
        val emojiLed = ledsByState.containsKey(ModifierLedState.EMOJI)
        val symLevel = when {
            snapshot.symSticky || symPage == 2 || symPage == 5 -> 2
            snapshot.symHeld || snapshot.symPhysicallyPressed -> 1
            emojiPage && !emojiLed -> 1
            else -> 0
        }
        statePriority[ModifierLedState.SYM] = symLevel
        val targetColor = ledColor(ModifierLedState.SYM, symLevel)
        ledsByState[ModifierLedState.SYM].orEmpty().forEach { led -> animateLedColor(led, targetColor) }
        if (emojiLed) {
            val emojiLevel = when {
                snapshot.emojiSticky || emojiPage -> 2
                snapshot.emojiHeld -> 1
                else -> 0
            }
            statePriority[ModifierLedState.EMOJI] = emojiLevel
            val emojiColor = ledColor(ModifierLedState.EMOJI, emojiLevel)
            ledsByState[ModifierLedState.EMOJI].orEmpty().forEach { led -> animateLedColor(led, emojiColor) }
        }
        // The visible idle contour depends on both the upper and lower states.
        ledsByState[ModifierLedState.SHIFT].orEmpty().forEach { it.invalidate() }
    }

    private fun animateLedColor(led: View?, targetColor: Int) {
        led ?: return
        val previousColor = (led.getTag(R.id.led_previous_color) as? Int) ?: LED_COLOR_GRAY_OFF
        led.setTag(R.id.led_previous_color, targetColor)

        if (previousColor == targetColor) {
            led.background = createDrawable(targetColor, segmentsByView.getValue(led))
            return
        }

        ValueAnimator.ofArgb(previousColor, targetColor).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                segmentsByView[led]?.let { led.background = createDrawable(color, it) }
            }
        }.start()
    }

    private class ModifierLedCanvas(
        context: Context,
        private val contentHeightPx: Int
    ) : ViewGroup(context) {
        private val segments = mutableListOf<ModifierLedSegment>()
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            // Rounded indicators overlap the controls. Their transparent center
            // must not become a full-row long-press target above those controls.
            if (cornerRadiiPx != null && event.actionMasked == MotionEvent.ACTION_DOWN) {
                val edge = MERGED_LED_ZONE_HEIGHT_DP * resources.displayMetrics.density
                if (event.x > edge && event.x < width - edge && event.y < height - edge) {
                    return false
                }
            }
            return super.dispatchTouchEvent(event)
        }

        var cornerRadiiPx: Pair<Int, Int>? = null
            set(value) {
                field = value
                requestLayout()
            }

        fun replaceSegments(
            newSegments: List<ModifierLedSegment>,
            createView: (ModifierLedSegment) -> View
        ) {
            removeAllViews()
            segments.clear()
            newSegments.forEach { segment ->
                segments.add(segment)
                addView(createView(segment))
            }
            requestLayout()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val measuredWidth = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
            val curveHeight = cornerRadiiPx?.let { maxOf(it.first, it.second) } ?: 0
            val desiredHeight = paddingTop + maxOf(contentHeightPx, curveHeight) + paddingBottom
            val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
            val contentWidth = (measuredWidth - paddingLeft - paddingRight).coerceAtLeast(0)
            val availableHeight = (measuredHeight - paddingTop - paddingBottom).coerceAtLeast(0)

            for (index in 0 until childCount) {
                val child = getChildAt(index)
                val segment = segments[index]
                if (cornerRadiiPx != null) {
                    child.measure(
                        MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
                    )
                    continue
                }
                child.measure(
                    MeasureSpec.makeMeasureSpec(
                        (contentWidth * segment.width).roundToInt().coerceAtLeast(1),
                        MeasureSpec.EXACTLY
                    ),
                    MeasureSpec.makeMeasureSpec(
                        (availableHeight * segment.height).roundToInt().coerceAtLeast(1),
                        MeasureSpec.EXACTLY
                    )
                )
            }
            setMeasuredDimension(measuredWidth, measuredHeight)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val contentWidth = (right - left - paddingLeft - paddingRight).coerceAtLeast(0)
            val availableHeight = (bottom - top - paddingTop - paddingBottom).coerceAtLeast(0)
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                val segment = segments[index]
                if (cornerRadiiPx != null) {
                    child.layout(0, 0, right - left, bottom - top)
                    continue
                }
                val childLeft = paddingLeft + (contentWidth * segment.x).roundToInt()
                val childTop = paddingTop + (availableHeight * segment.y).roundToInt()
                child.layout(
                    childLeft,
                    childTop,
                    childLeft + child.measuredWidth,
                    childTop + child.measuredHeight
                )
            }
        }

    }
}
