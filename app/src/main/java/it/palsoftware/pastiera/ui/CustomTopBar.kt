package it.palsoftware.pastiera.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import it.palsoftware.pastiera.T2eCornerCalibration
import it.palsoftware.pastiera.T2eCornerGeometry
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R
import kotlin.math.hypot

// Flux Keyboard: the dessert's lattice, in purples
private val PastieraBeige = Color(0xFF3F2F66)
private val PastieraBeigeDark = Color(0xFF54408A)
private val PastieraOrangeLight = Color(0xFFC9A2FF)
private val PastieraYellow = Color(0xFFB98AF0)

/**
 * Custom top bar with Pastiera lattice pattern.
 * Features light diagonal stripes over a dark toasted gradient.
 */
@Composable
fun CustomTopBar(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val compactHeader = DeviceSpecific.isTitan2EliteDevice()
    val headerContentOffset = if (compactHeader) 0.dp else statusBarInset / 2
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    var calibration by remember { mutableStateOf(T2eCornerCalibration.read(context)) }
    DisposableEffect(context) {
        val prefs = SettingsManager.getPreferences(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == T2eCornerCalibration.KEY) calibration = T2eCornerCalibration.read(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val headerShape: Shape = if (DeviceSpecific.isTitan2EliteDevice()) {
        val fallback = with(density) { 50.dp.toPx() }
        fun radius(position: Int) = if (android.os.Build.VERSION.SDK_INT >= 31) {
            view.rootWindowInsets?.getRoundedCorner(position)?.radius?.takeIf { it > 0 }?.toFloat() ?: fallback
        } else fallback
        val left = radius(android.view.RoundedCorner.POSITION_BOTTOM_LEFT)
        val right = radius(android.view.RoundedCorner.POSITION_BOTTOM_RIGHT)
        remember(calibration, left, right) {
            object : Shape {
                override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                    density: androidx.compose.ui.unit.Density): Outline {
                    // Reuse only the calibrated corner shape; this header stays flush to its bounds.
                    val shapeCalibration = calibration.copy(offsetPx = 0f, shiftXPx = 0f, shiftYPx = 0f)
                    val path = T2eCornerGeometry.path(size.width, size.height, left, right, shapeCalibration)
                    // Limit the outline to this header; the shared contour extends upward for short IME bars.
                    val bounds = android.graphics.Path().apply {
                        addRect(0f, 0f, size.width, size.height, android.graphics.Path.Direction.CW)
                    }
                    path.op(bounds, android.graphics.Path.Op.INTERSECT)
                    return Outline.Generic(path.asComposePath())
                }
            }
        }
    } else RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = headerShape
            ),
        shape = headerShape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = PastieraBeigeDark)
        ) {
            PastieraPattern(
                modifier = Modifier
                    .fillMaxWidth()
                    .matchParentSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // On the Titan 2 Elite's short screen the text sits just below the status bar
                    // with even space around it; elsewhere a little more lattice below the
                    // "Based on Pastiera" credit
                    .padding(
                        start = 16.dp, end = 16.dp,
                        top = if (compactHeader) statusBarInset + 2.dp else 32.dp,
                        bottom = when {
                            compactHeader -> 20.dp
                            it.palsoftware.pastiera.BuildConfig.APP_NAME != "Pastiera" -> 46.dp
                            else -> 32.dp
                        }
                    )
            ) {
                // Centered title and subtitle
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = headerContentOffset),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = it.palsoftware.pastiera.BuildConfig.APP_NAME,
                        style = if (compactHeader) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Go with the flux",
                        style = if (compactHeader) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                    )
                    // A fork under its own name says what it's built on, and links to it
                    if (it.palsoftware.pastiera.BuildConfig.APP_NAME != "Pastiera") {
                        Text(
                            text = "Based on Pastiera by PalSoftware ↗",
                            style = if (compactHeader) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                            modifier = Modifier
                                .padding(top = if (compactHeader) 2.dp else 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse("https://github.com/palsoftware/pastiera")
                                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Settings icon on the right
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(y = headerContentOffset)
                        .size(if (compactHeader) 48.dp else 64.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            color = PastieraBeige.copy(alpha = 0.9f)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings_content_description),
                        tint = Color.White,
                        modifier = Modifier.size(if (compactHeader) 28.dp else 36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PastieraPattern(
    modifier: Modifier = Modifier,
    stripeWidth: Dp = 36.dp,
    stripeSpacing: Dp = 96.dp
) {
    val density = LocalDensity.current
    val widthPx = with(density) { stripeWidth.toPx() }
    val spacingPx = with(density) { stripeSpacing.toPx() }

    val infiniteTransition = rememberInfiniteTransition(label = "pastieraPatternScroll")
    val stripePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = spacingPx,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "stripeOffset"
    )

    Canvas(modifier = modifier) {
        drawPastieraPattern(
            stripeWidth = widthPx,
            stripeSpacing = spacingPx,
            phase = stripePhase
        )
    }
}

private fun DrawScope.drawPastieraPattern(
    stripeWidth: Float,
    stripeSpacing: Float,
    phase: Float
) {
    // Base gradient now dark so the light stripes pop.
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                PastieraBeigeDark.copy(alpha = 0.95f),
                PastieraBeige.copy(alpha = 0.9f),
                PastieraBeigeDark.copy(alpha = 0.98f)
            ),
            startY = 0f,
            endY = size.height
        )
    )

    val adjustedStripeWidth = stripeWidth * 1.2f
    val verticalStretch = 1.3f
    val diagonal = hypot(size.width, size.height)
    val stripeLength = diagonal * 1.4f
    val startX = -stripeLength - stripeSpacing
    val phaseWrapped = phase % stripeSpacing

    fun drawStripeSet(angle: Float, color: Color) {
        rotate(degrees = angle, pivot = center) {
            var x = startX - phaseWrapped
            while (x < stripeLength + stripeSpacing) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, -stripeLength / 2f),
                    size = Size(adjustedStripeWidth, stripeLength),
                    cornerRadius = CornerRadius(
                        x = adjustedStripeWidth / 2f,
                        y = adjustedStripeWidth / 2f
                    )
                )
                x += stripeSpacing
            }
        }
    }

    // Scale the stripe layer vertically to elongate the diamonds.
    withTransform({
        scale(scaleX = 1f, scaleY = verticalStretch, pivot = center)
    }) {
        drawStripeSet(angle = 45f, color = PastieraYellow.copy(alpha = 0.75f))
        drawStripeSet(angle = -45f, color = PastieraOrangeLight.copy(alpha = 0.7f))
    }
}
