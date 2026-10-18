package com.example.a.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a.R
import kotlin.math.abs

internal val FocusBlack = Color(0xFF0F0F0F)
internal val FocusPurple = Color(0xFF8B5CF6)
internal val FocusDigit = Color(0xFFB9A2FF)

/** Longest settable or countable time: 99:59. */
internal const val FocusMaxSeconds = 99 * 60 + 59

/**
 * The Focus page: a purple MM:SS timer pops up from the middle of the screen;
 * as it starts to glow, its arrows and the Focus button fade in. Each digit
 * scrolls, or steps with its arrows, until [running]; then controls and glow
 * fade and the digits show [displaySeconds]. A session needs a time above 00:00.
 */
@Composable
internal fun FocusScreen(
    setSeconds: Int,
    displaySeconds: Int,
    running: Boolean,
    onSetSecondsChange: (Int) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pop: small to full size over one second, then the glow and controls begin.
    val pop = remember { Animatable(0f) }
    var popped by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
        popped = true
    }
    val ready = popped && !running
    val controls by animateFloatAsState(if (ready) 1f else 0f, tween(if (running) 450 else 700), label = "focus_controls")
    val glow by animateFloatAsState(
        if (ready) 1f else 0f,
        tween(if (running) 1400 else 1000),
        label = "focus_glow",
    )
    val pulse = rememberInfiniteTransition(label = "focus_glow_pulse")
    val breathing = pulse.animateFloat(
        0.75f, 1f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "focus_glow_breathing",
    )

    Box(
        modifier.fillMaxSize().background(FocusBlack).testTag("focus_page"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DigitTimer(
                seconds = if (running) displaySeconds else setSeconds,
                digitColor = FocusDigit,
                accent = FocusPurple,
                glow = glow,
                controls = controls,
                enabled = ready,
                onChange = onSetSecondsChange,
                tagPrefix = "focus",
                modifier = Modifier.graphicsLayer {
                    val scale = 0.25f + 0.75f * pop.value
                    scaleX = scale
                    scaleY = scale
                    alpha = (pop.value * 3f).coerceAtMost(1f)
                }.drawBehind {
                    // Faint halo around the digits, breathing gently: a circular
                    // gradient squashed into a soft ellipse, fading fully to clear.
                    val strength = glow * breathing.value
                    if (strength > 0f) {
                        val radius = size.width * 0.62f
                        scale(1f, 0.6f, pivot = center) {
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(FocusPurple.copy(alpha = 0.3f * strength), Color.Transparent),
                                    center = center,
                                    radius = radius,
                                ),
                                radius = radius,
                                center = center,
                            )
                        }
                    }
                },
            )
            Spacer(Modifier.height(36.dp))
            DepthButton(
                text = stringResource(R.string.focus_start),
                color = FocusPurple,
                enabled = ready && setSeconds > 0,
                onClick = onStart,
                modifier = Modifier.graphicsLayer { alpha = controls }
                    .width(176.dp).height(60.dp).testTag("focus_start"),
            )
        }
    }
}

/**
 * An MM:SS timer whose digits each scroll, or step with the arrows above and
 * below them. [glow] (0..1) lights the digits in [accent]; [controls] fades the
 * arrows. Tags are "<tagPrefix>_digit_N", "_up_N" and "_down_N".
 */
@Composable
internal fun DigitTimer(
    seconds: Int,
    digitColor: Color,
    accent: Color,
    glow: Float,
    controls: Float,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    val digits = listOf(seconds / 60 / 10, seconds / 60 % 10, seconds % 60 / 10, seconds % 60 % 10)
    val style = digitStyle(digitColor, accent, glow)
    Row(modifier.testTag("${tagPrefix}_timer"), verticalAlignment = Alignment.CenterVertically) {
        digits.forEachIndexed { index, digit ->
            if (index == 2) Text(":", Modifier.width(22.dp), style = style)
            TimerDigit(
                index = index,
                value = digit,
                style = style,
                arrowColor = accent,
                controls = controls,
                enabled = enabled,
                tagPrefix = tagPrefix,
                onStep = { step -> onChange(stepDigit(seconds, index, step)) },
            )
        }
    }
}

/** Changes one digit by [step], wrapping within that digit's range. */
internal fun stepDigit(seconds: Int, index: Int, step: Int): Int {
    val digits = intArrayOf(seconds / 60 / 10, seconds / 60 % 10, seconds % 60 / 10, seconds % 60 % 10)
    val range = if (index == 2) 6 else 10
    digits[index] = ((digits[index] + step) % range + range) % range
    return (digits[0] * 10 + digits[1]) * 60 + digits[2] * 10 + digits[3]
}

private fun digitStyle(color: Color, accent: Color, glow: Float) = TextStyle(
    color = color,
    fontSize = 64.sp,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
    shadow = if (glow > 0f) Shadow(accent.copy(alpha = 0.85f * glow), Offset.Zero, 28f) else null,
)

@Composable
private fun TimerDigit(
    index: Int,
    value: Int,
    style: TextStyle,
    arrowColor: Color,
    controls: Float,
    enabled: Boolean,
    tagPrefix: String,
    onStep: (Int) -> Unit,
) {
    val stepPx = with(LocalDensity.current) { 30.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    var lastStep by remember { mutableIntStateOf(1) }
    val upLabel = stringResource(R.string.focus_increase)
    val downLabel = stringResource(R.string.focus_decrease)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TimerArrow(up = true, color = arrowColor, alpha = controls, enabled = enabled, label = upLabel, tag = "${tagPrefix}_up_$index") {
            lastStep = 1
            onStep(1)
        }
        Box(
            Modifier.width(48.dp).height(84.dp).testTag("${tagPrefix}_digit_$index")
                .draggable(
                    rememberDraggableState { delta ->
                        // Scrolling up advances the digit, like a wheel.
                        drag -= delta
                        while (abs(drag) >= stepPx) {
                            val step = if (drag > 0f) 1 else -1
                            drag -= step * stepPx
                            lastStep = step
                            onStep(step)
                        }
                    },
                    Orientation.Vertical,
                    enabled = enabled,
                    onDragStopped = { drag = 0f },
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    val dir = lastStep
                    (slideInVertically(tween(180)) { it * dir / 2 } + fadeIn(tween(180))) togetherWith
                        (slideOutVertically(tween(180)) { -it * dir / 2 } + fadeOut(tween(180)))
                },
                label = "focus_digit_roll",
            ) { digit ->
                Text("$digit", style = style)
            }
        }
        TimerArrow(up = false, color = arrowColor, alpha = controls, enabled = enabled, label = downLabel, tag = "${tagPrefix}_down_$index") {
            lastStep = -1
            onStep(-1)
        }
    }
}

@Composable
private fun TimerArrow(up: Boolean, color: Color, alpha: Float, enabled: Boolean, label: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).graphicsLayer { this.alpha = alpha }
            .testTag(tag)
            .semantics { contentDescription = label }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val stroke = 2.4.dp.toPx()
            val tipY = if (up) size.height * 0.3f else size.height * 0.7f
            val baseY = if (up) size.height * 0.7f else size.height * 0.3f
            val color = color.copy(alpha = 0.9f)
            drawLine(color, Offset(size.width * 0.15f, baseY), Offset(size.width / 2f, tipY), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width / 2f, tipY), Offset(size.width * 0.85f, baseY), stroke, StrokeCap.Round)
        }
    }
}

/** The faint purple Exit link shown top-left once a session has run for five seconds. */
@Composable
internal fun FocusExit(visible: Boolean, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(900), label = "focus_exit")
    if (alpha > 0f) {
        Text(
            stringResource(R.string.focus_exit),
            modifier.graphicsLayer { this.alpha = alpha }
                .testTag("focus_exit")
                .clickable(enabled = visible, role = Role.Button, onClick = onExit),
            color = FocusPurple.copy(alpha = 0.6f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * The dark surface spreading up over the header when a session starts. Its leading
 * edge bulges ahead in the middle, like ink spreading, and retracts on exit.
 */
@Composable
internal fun FocusSpread(progress: () -> Float, headerBottom: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val p = progress()
        if (p <= 0f) return@Canvas
        val bulge = 36.dp.toPx() * kotlin.math.sin(Math.PI.toFloat() * p)
        // The edge starts slightly below the header, so the bulge enters smoothly.
        val edge = (headerBottom + bulge) * (1f - p)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, headerBottom + 1f)
            lineTo(0f, edge)
            quadraticTo(size.width / 2f, edge - bulge * 2f, size.width, edge)
            lineTo(size.width, headerBottom + 1f)
            close()
        }
        drawPath(path, FocusBlack)
        if (p >= 1f) drawRect(FocusBlack, size = androidx.compose.ui.geometry.Size(size.width, headerBottom + 1f))
    }
}
