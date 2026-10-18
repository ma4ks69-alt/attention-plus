package com.example.a.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.TextUnit
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

internal val DimensionalCardShape = RoundedCornerShape(14.dp)

internal val StartFaceTop = Color(0xFF58D01C)
internal val StartFaceBottom = Color(0xFF3DB212)
internal val StartEdge = Color(0xFF2C860B)
internal val StartGlow = Color(0xFF4CD11A)

internal val ChestGold = Color(0xFFFFC531)
internal val ChestOutline = Color(0xFFE0951A)
internal val ChestEdge = Color(0xFFC66F12)
internal val ChestLid = Color(0xFFF2662F)
internal val ChestLidDetail = Color(0xFFD5501F)
internal val ChestInside = Color(0xFF8B4A1F)

internal val PaperColor = Color(0xFFFFF6E0)
internal val PaperBack = Color(0xFFF0E2C0)
internal val PaperEdge = Color(0xFFE2CFA3)
internal val BindingRed = Color(0xFFEF4438)
internal val BindingDetail = Color(0xFFC92E25)
internal val RingColor = Color(0xFFE6E2DC)

internal val BookCover = Color(0xFF1E96E6)
internal val BookSpine = Color(0xFF1782CC)
internal val BookPages = Color(0xFFEEF2F6)
internal val BookLabelLine = Color(0xFFA9D2F5)
internal val BookInk = Color(0xFF1C3657)
internal val PencilInk = Color(0xFF2B2622)

/**
 * Natural handwriting from the platform "casual" family (Coming Soon on AOSP
 * devices). It needs no bundled asset and falls back to the default sans face.
 */
internal val HandwrittenFont: FontFamily by lazy {
    FontFamily(Typeface(android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL)))
}

/**
 * A rounded, toy-like slab: a darker edge peeks out below the face and a soft drop
 * shadow lifts it from the page. [gloss] adds a thin highlight to the top; flat
 * cartoon cards leave it off. [press] (0..1) pushes the face toward its edge.
 */
@Composable
internal fun DimensionalCard(
    face: Brush,
    edge: Color,
    modifier: Modifier = Modifier,
    depth: Dp = 6.dp,
    press: Float = 0f,
    pageLines: Boolean = false,
    gloss: Boolean = true,
    shape: Shape = DimensionalCardShape,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.dropShadow(shape, Shadow(radius = 14.dp, color = Color.Black.copy(alpha = 0.45f), offset = DpOffset(0.dp, 6.dp))),
    ) {
        Box(
            Modifier.matchParentSize().padding(top = depth).background(edge, shape).drawWithContent {
                drawContent()
                if (pageLines) {
                    // Stacked sheets beneath the top page.
                    val y = size.height - depth.toPx() * 0.5f
                    drawLine(Color.White.copy(alpha = 0.6f), Offset(10.dp.toPx(), y), Offset(size.width - 10.dp.toPx(), y), 1.dp.toPx())
                }
            },
        )
        Box(
            Modifier.matchParentSize().padding(bottom = depth)
                .graphicsLayer { translationY = depth.toPx() * 0.6f * press }
                .clip(shape)
                .background(face)
                .drawWithContent {
                    drawContent()
                    if (gloss) {
                        val band = 14.dp.toPx()
                        drawRect(
                            Brush.verticalGradient(0f to Color.White.copy(alpha = 0.24f), 1f to Color.Transparent, endY = band),
                            size = Size(size.width, band),
                        )
                    }
                },
            content = content,
        )
    }
}

private enum class SymbolShape { Text, Atom, Triangle, Ring, Hexagon, Square }

/**
 * One drifting symbol. Positions are card fractions; [frequency] is a whole number
 * of loops per shared cycle so every path repeats seamlessly at different speeds.
 */
private class StudySymbol(
    val x: Float,
    val y: Float,
    val size: Dp,
    val tilt: Float,
    val driftX: Dp,
    val driftY: Dp,
    val frequency: Int,
    val phase: Float,
    val spin: Float,
    val shape: SymbolShape = SymbolShape.Text,
    val text: String = "",
)

private val StudySymbols = listOf(
    StudySymbol(0.13f, 0.14f, 22.dp, -8f, 4.dp, 3.dp, 2, 0.0f, 6f, text = "+"),
    StudySymbol(0.48f, 0.09f, 18.dp, 6f, -3.dp, 4.dp, 3, 1.1f, 5f, text = "π"),
    StudySymbol(0.85f, 0.15f, 22.dp, 0f, 3.dp, -4.dp, 1, 2.3f, 14f, SymbolShape.Atom),
    StudySymbol(0.30f, 0.27f, 13.dp, 10f, 5.dp, 2.dp, 2, 3.0f, 8f, SymbolShape.Ring),
    StudySymbol(0.70f, 0.31f, 17.dp, -12f, -4.dp, -3.dp, 2, 4.2f, 9f, text = "÷"),
    StudySymbol(0.12f, 0.50f, 17.dp, -14f, 3.dp, 5.dp, 1, 5.0f, 18f, SymbolShape.Triangle),
    StudySymbol(0.90f, 0.53f, 17.dp, 8f, -3.dp, 4.dp, 3, 0.7f, 6f, text = "×"),
    StudySymbol(0.14f, 0.80f, 15.dp, -6f, 4.dp, -3.dp, 2, 2.0f, 5f, text = "√x"),
    StudySymbol(0.47f, 0.88f, 13.dp, 4f, -5.dp, 2.dp, 1, 3.6f, 4f, text = "E=mc²"),
    StudySymbol(0.84f, 0.82f, 16.dp, 12f, 3.dp, 4.dp, 2, 1.5f, 16f, SymbolShape.Hexagon),
    StudySymbol(0.66f, 0.70f, 19.dp, -4f, 4.dp, -3.dp, 3, 4.8f, 7f, text = "−"),
    StudySymbol(0.32f, 0.68f, 12.dp, 18f, -3.dp, -4.dp, 1, 5.6f, 20f, SymbolShape.Square),
    StudySymbol(0.92f, 0.33f, 13.dp, -10f, 2.dp, 3.dp, 2, 0.4f, 6f, text = "Σ"),
)

/** Faint maths and science symbols floating behind the Start Learning label. */
@Composable
internal fun FloatingStudySymbols(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "study_symbols")
    val cycle = transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "study_symbols_cycle",
    )
    val measurer = rememberTextMeasurer()
    val glyphs = remember(measurer, color) {
        StudySymbols.associateWith {
            if (it.shape != SymbolShape.Text) null
            else measurer.measure(
                it.text,
                TextStyle(color = color, fontSize = (it.size.value * 0.95f).sp, fontWeight = FontWeight.ExtraBold),
            )
        }
    }
    Canvas(modifier) {
        val t = cycle.value
        val stroke = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        for (symbol in StudySymbols) {
            // Elliptical drift; the second axis is offset in phase, so symbols
            // travel in different directions, at different speeds and timings.
            val angle = (2f * PI.toFloat()) * t * symbol.frequency + symbol.phase
            val center = Offset(
                size.width * symbol.x + symbol.driftX.toPx() * sin(angle),
                size.height * symbol.y + symbol.driftY.toPx() * sin(angle + 1.3f),
            )
            val extent = symbol.size.toPx()
            rotate(symbol.tilt + symbol.spin * sin(angle * 0.5f + symbol.phase), center) {
                when (symbol.shape) {
                    SymbolShape.Text -> glyphs[symbol]?.let { layout ->
                        drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
                    }
                    SymbolShape.Atom -> drawAtom(center, extent, color, stroke)
                    SymbolShape.Ring -> drawCircle(color, extent / 2f, center, style = stroke)
                    SymbolShape.Square -> drawRoundRect(
                        color, center - Offset(extent / 2f, extent / 2f), Size(extent, extent),
                        CornerRadius(2.dp.toPx()), style = stroke,
                    )
                    SymbolShape.Triangle -> drawPolygon(center, extent / 2f, 3, color, stroke)
                    SymbolShape.Hexagon -> drawPolygon(center, extent / 2f, 6, color, stroke)
                }
            }
        }
    }
}

private fun DrawScope.drawAtom(center: Offset, extent: Float, color: Color, stroke: Stroke) {
    val orbit = Size(extent, extent * 0.38f)
    for (turn in listOf(0f, 60f, 120f)) {
        rotate(turn, center) {
            drawOval(color, center - Offset(orbit.width / 2f, orbit.height / 2f), orbit, style = Stroke(1.6.dp.toPx()))
        }
    }
    drawCircle(color, extent * 0.09f, center)
}

private fun DrawScope.drawPolygon(center: Offset, radius: Float, sides: Int, color: Color, stroke: Stroke) {
    val path = Path()
    for (i in 0 until sides) {
        val a = -PI / 2 + 2 * PI * i / sides
        val p = Offset(center.x + radius * kotlin.math.cos(a).toFloat(), center.y + radius * sin(a).toFloat())
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color, style = stroke)
}

/** Chest geometry as fractions of the card face, shared by artwork and content. */
internal object ChestLayout {
    const val LidFraction = 0.25f
    const val FrameFraction = 0.065f
    const val TitleFraction = 0.36f
}

/** Flat red lid with golden straps and rim, behind the chest's interior. */
internal fun DrawScope.drawChestLid() {
    val w = size.width
    val lid = size.height * ChestLayout.LidFraction
    val line = 1.5.dp.toPx()
    drawRect(ChestLid, size = Size(w, lid))
    // Plank seams.
    drawLine(ChestLidDetail, Offset(0f, lid * 0.4f), Offset(w, lid * 0.4f), line)
    val strap = w * 0.12f
    for (x in listOf(w * 0.2f, w * 0.8f)) {
        drawRect(ChestGold, Offset(x - strap / 2f, 0f), Size(strap, lid))
        drawRect(ChestOutline, Offset(x - strap / 2f, 0f), Size(strap, lid), style = Stroke(line))
    }
    val rim = 5.dp.toPx()
    drawRect(ChestGold, Offset(0f, lid - rim), Size(w, rim))
    drawLine(ChestOutline, Offset(0f, lid - rim), Offset(w, lid - rim), line)
}

private fun DrawScope.drawRivet(center: Offset, radius: Float) {
    drawCircle(ChestGold, radius, center)
    drawCircle(ChestOutline, radius, center, style = Stroke(1.5.dp.toPx()))
}

/** Flat hasp, corner brackets and rivets drawn over the chest's interior. */
internal fun DrawScope.drawChestHardware() {
    val w = size.width
    val h = size.height
    val lid = h * ChestLayout.LidFraction
    val corner = CornerRadius(5.dp.toPx())
    val outline = Stroke(1.5.dp.toPx())
    val bracket = Size(w * 0.2f, h * 0.17f)
    for (left in listOf(true, false)) {
        val x = if (left) 0f else w - bracket.width
        val topLeft = Offset(x, h - bracket.height)
        drawRoundRect(ChestGold, topLeft, bracket, corner)
        drawRoundRect(ChestOutline, topLeft, bracket, corner, style = outline)
        drawRivet(Offset(x + bracket.width * if (left) 0.42f else 0.58f, h - bracket.height * 0.48f), 3.5.dp.toPx())
    }
    drawRivet(Offset(w * 0.065f, lid - 2.5.dp.toPx()), 3.dp.toPx())
    drawRivet(Offset(w * 0.935f, lid - 2.5.dp.toPx()), 3.dp.toPx())
    val hasp = Size(w * 0.18f, h * 0.17f)
    val haspTop = Offset(w / 2f - hasp.width / 2f, lid - hasp.height * 0.5f)
    drawRoundRect(ChestGold, haspTop, hasp, corner)
    drawRoundRect(ChestOutline, haspTop, hasp, corner, style = outline)
    drawRivet(Offset(w / 2f, haspTop.y + hasp.height * 0.55f), hasp.width * 0.24f)
}

/** Flat red binding with punched ring holes, across the calendar's top. */
internal fun DrawScope.drawCalendarBinding(ringXs: List<Float>) {
    drawRect(BindingRed)
    drawRect(BindingDetail, Offset(0f, size.height - 3.dp.toPx()), Size(size.width, 3.dp.toPx()))
    for (x in ringXs) {
        val hole = Size(size.width * 0.085f, size.height * 0.3f)
        drawOval(Color(0xFF7A1E17), Offset(size.width * x - hole.width / 2f, size.height * 0.42f), hole)
    }
}

/** Flat metal rings looping from above the binding into its holes. */
internal fun DrawScope.drawCalendarRings(ringXs: List<Float>, bindingTop: Float, bindingHeight: Float) {
    val ring = Size(size.width * 0.075f, bindingTop + bindingHeight * 0.55f)
    for (x in ringXs) {
        val topLeft = Offset(size.width * x - ring.width / 2f, 1.dp.toPx())
        drawRoundRect(RingColor, topLeft, ring, CornerRadius(ring.width / 2f), style = Stroke(3.2.dp.toPx()))
    }
}

/** Two staples on the exercise book's spine. */
internal fun DrawScope.drawStaples() {
    val width = 2.dp.toPx()
    for (y in listOf(0.25f, 0.75f)) {
        val center = size.height * y
        drawLine(Color(0xFFD9DEE4), Offset(size.width / 2f, center - 7.dp.toPx()), Offset(size.width / 2f, center + 7.dp.toPx()), width, StrokeCap.Round)
    }
}

/** A slightly uneven crayon underline, drawn twice for a waxy texture. */
internal fun DrawScope.drawCrayonUnderline(color: Color) {
    val y = size.height / 2f
    val width = 2.6.dp.toPx()
    fun stroke(dy: Float, alpha: Float) {
        val path = Path().apply {
            moveTo(size.width * 0.06f, y + dy + 1.dp.toPx())
            quadraticTo(size.width * 0.4f, y + dy - 1.5.dp.toPx(), size.width * 0.78f, y + dy)
        }
        drawPath(path, color.copy(alpha = alpha), style = Stroke(width, cap = StrokeCap.Round))
        drawLine(
            color.copy(alpha = alpha), Offset(size.width * 0.84f, y + dy - 0.5.dp.toPx()),
            Offset(size.width * 0.95f, y + dy - 1.dp.toPx()), width, StrokeCap.Round,
        )
    }
    stroke(0f, 0.85f)
    stroke(0.9.dp.toPx(), 0.35f)
}

/** Short orange marker sparks beside the title. */
internal fun DrawScope.drawSparks(color: Color) {
    val width = 2.dp.toPx()
    val l = size.width * 0.05f
    val r = size.width * 0.95f
    val y = size.height * 0.3f
    val len = 7.dp.toPx()
    drawLine(color, Offset(l, y), Offset(l + len * 0.5f, y + len * 0.8f), width, StrokeCap.Round)
    drawLine(color, Offset(l - len * 0.4f, y + len * 1.4f), Offset(l + len * 0.4f, y + len * 1.6f), width, StrokeCap.Round)
    drawLine(color, Offset(r, y), Offset(r - len * 0.5f, y + len * 0.8f), width, StrokeCap.Round)
    drawLine(color, Offset(r + len * 0.4f, y + len * 1.4f), Offset(r - len * 0.4f, y + len * 1.6f), width, StrokeCap.Round)
}

/** Hand-drawn tick box. */
internal fun DrawScope.drawPencilCheckbox(color: Color) {
    val s = size.minDimension
    val box = s * 0.78f
    val topLeft = Offset(0f, s - box)
    drawRoundRect(color, topLeft, Size(box, box), CornerRadius(3.dp.toPx()), style = Stroke(1.8.dp.toPx()))
    val tick = Path().apply {
        moveTo(topLeft.x + box * 0.22f, topLeft.y + box * 0.52f)
        lineTo(topLeft.x + box * 0.43f, topLeft.y + box * 0.72f)
        lineTo(topLeft.x + box * 0.8f, topLeft.y + box * 0.28f)
    }
    drawPath(tick, color, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    // Little "ping" lines at the top-right corner.
    val width = 1.4.dp.toPx()
    drawLine(color.copy(alpha = 0.6f), Offset(box + s * 0.06f, s * 0.12f), Offset(box + s * 0.12f, 0f), width, StrokeCap.Round)
    drawLine(color.copy(alpha = 0.6f), Offset(box + s * 0.08f, s * 0.24f), Offset(s, s * 0.18f), width, StrokeCap.Round)
}

/**
 * A primary action in the Start Learning style: a raised face over a darker
 * edge, a soft shadow and gloss, bold white label, and a press that sinks the
 * face. Disabled buttons dim and do not press.
 */
@Composable
internal fun DepthButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    fontSize: TextUnit = 20.sp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed && enabled) 1f else 0f,
        spring(GlassMotion.DampingRatio, GlassMotion.Stiffness * 1.5f),
        label = "depth_button_press",
    )
    val edge = lerp(color, Color.Black, 0.35f)
    DimensionalCard(
        face = Brush.verticalGradient(listOf(lerp(color, Color.White, 0.16f), color)),
        edge = edge,
        press = press,
        depth = 5.dp,
        shape = shape,
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text,
            Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
            color = Color.White,
            fontSize = fontSize,
            lineHeight = fontSize * 1.1f,
            fontWeight = FontWeight.ExtraBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(edge.copy(alpha = 0.7f), Offset(0f, 3f), 6f)),
        )
    }
}
