package com.example.a.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow as TextShadow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun HomeScreen(
    onStartLearning: () -> Unit,
    modifier: Modifier = Modifier,
    playEntrance: Boolean = false,
    onEntranceStarted: () -> Unit = {},
) {
    // The host owns the session flag; this mounted scene keeps its own entrance
    // even after claiming that flag, and subsequent mounts start fully emerged.
    val entering = remember { playEntrance }
    val reveals = remember { List(4) { Animatable(if (entering) 0f else 1f) } }
    LaunchedEffect(Unit) {
        onEntranceStarted()
        if (entering) coroutineScope {
            reveals.forEachIndexed { index, reveal ->
                launch {
                    // Frame-clock timing keeps the stagger coordinated with the
                    // springs and deterministic under the Compose test clock.
                    if (index > 0) {
                        val began = withFrameNanos { it }
                        var now = began
                        while (now - began < index * 55_000_000L) now = withFrameNanos { it }
                    }
                    reveal.animateTo(1f, spring(0.9f, 700f))
                }
            }
        }
    }
    val floating = rememberInfiniteTransition(label = "home_hover")
    val phase by floating.animateFloat(
        0f, (2.0 * PI).toFloat(),
        infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "home_hover_phase",
    )
    val capImage = ImageBitmap.imageResource(R.drawable.graduation_cap)
    val capPainter = remember(capImage) {
        // The supplied PNG already contains alpha. Only exclude empty margins at
        // display time; preserve the artwork and transparency byte for byte.
        BitmapPainter(capImage, IntOffset(69, 116), IntSize(1188, 1058), FilterQuality.High)
    }

    Column(
        modifier.testTag("home_page").verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 128.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
            val cardWidth = (maxWidth - 36.dp) / 2f
            val cardHeight = maxOf(160.dp, cardWidth * 1.08f)
            val secondRow = 8.dp + cardHeight + 32.dp
            val capWidth = minOf(280.dp, maxWidth * 0.82f)
            val capHeight = capWidth * (240f / 280f)
            val capTop = 8.dp + cardHeight * 2f + 32.dp + 24.dp
            val capLeft = (maxWidth - capWidth) / 2f
            val capBob = sin(phase) * 3f
            // After 180-degree rotation the opening lies above/left of the cap's
            // center. All four trajectories begin in that same physical opening.
            val originX = capLeft + capWidth * 0.36f
            val originY = capTop + capHeight * 0.36f + capBob.dp
            val lefts = listOf(0.dp, cardWidth + 36.dp, 0.dp, cardWidth + 36.dp)
            val tops = listOf(8.dp, 16.dp, secondRow + 5.dp, secondRow)
            val tilts = listOf(-6f, 5f, 4f, -7f)
            val tags = listOf("start_learning", "previous_sessions", "daily_challenge", "inventory")
            Box(Modifier.fillMaxWidth().height(capTop + capHeight).testTag("home_scene")) {
                repeat(4) { index ->
                    val progress = reveals[index].value.coerceIn(0f, 1f)
                    val hover = sin(phase + index * 0.85f) * 1.8f
                    Box(
                        Modifier.offset(lefts[index], tops[index]).size(cardWidth, cardHeight)
                            .testTag("home_slot_${tags[index]}"),
                    ) {
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                translationX = (originX - lefts[index] - cardWidth / 2f).toPx() * (1f - progress)
                                translationY = (originY - tops[index] - cardHeight / 2f).toPx() * (1f - progress) + hover.dp.toPx() * progress
                                scaleX = progress
                                scaleY = progress
                                rotationZ = tilts[index] * progress
                            },
                        ) {
                            when (index) {
                                0 -> StartLearningCard(onStartLearning, progress > 0.9f)
                                1 -> PreviousSessionsCard()
                                2 -> DailyChallengeCard()
                                else -> InventoryCard()
                            }
                        }
                    }
                }
                // The cap is drawn in front of the source positions, so cards
                // physically emerge past its rim rather than fading beside it.
                Box(
                    Modifier.offset(capLeft, capTop).size(capWidth, capHeight)
                        .graphicsLayer {
                            rotationZ = 180f
                            translationY = capBob.dp.toPx()
                        },
                ) {
                    Image(capPainter, null, Modifier.fillMaxSize().testTag("graduation_cap"))
                }
            }
        }
    }
}

@Composable
private fun StartLearningCard(onStartLearning: () -> Unit, enabled: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.985f else 1f,
        spring(GlassMotion.DampingRatio, GlassMotion.Stiffness),
        label = "start_learning_press",
    )
    val press by animateFloatAsState(
        if (pressed) 1f else 0f,
        spring(GlassMotion.DampingRatio, GlassMotion.Stiffness * 1.5f),
        label = "start_learning_depth",
    )
    Box(Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }) {
        Box(
            Modifier.matchParentSize().blur(16.dp, BlurredEdgeTreatment.Unbounded)
                .background(StartGlow.copy(alpha = 0.3f), DimensionalCardShape),
        )
        DimensionalCard(
            face = Brush.verticalGradient(listOf(StartFaceTop, StartFaceBottom)),
            edge = StartEdge,
            press = press,
            modifier = Modifier.fillMaxSize().testTag("start_learning")
                .clickable(enabled = enabled, interactionSource = interaction, indication = null, role = Role.Button, onClick = onStartLearning),
        ) {
            FloatingStudySymbols(Color(0xFF9AF26A).copy(alpha = 0.42f), Modifier.fillMaxSize())
            Text(
                stringResource(R.string.start_learning),
                Modifier.align(Alignment.Center).padding(12.dp),
                color = InkColor, fontSize = 27.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                style = TextStyle(shadow = TextShadow(StartEdge.copy(alpha = 0.7f), Offset(0f, 3f), 6f)),
            )
        }
    }
}

/** A stapled exercise book; its name label reads "Previous Sessions". */
@Composable
private fun PreviousSessionsCard() {
    DimensionalCard(
        face = SolidColor(BookCover),
        edge = BookPages,
        pageLines = true,
        gloss = false,
        modifier = Modifier.fillMaxSize().testTag("previous_sessions"),
    ) {
        Row(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxHeight().width(14.dp)) {
                drawRect(BookSpine)
                drawStaples()
            }
            Column(
                Modifier.fillMaxHeight().weight(1f).padding(start = 8.dp, end = 12.dp, top = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val labelShape = RoundedCornerShape(4.dp)
                val lineHeight = 21.sp
                Box(
                    Modifier.fillMaxWidth().background(Color.White, labelShape)
                        .border(1.dp, BookLabelLine, labelShape)
                        .drawBehind {
                            // Ruled name lines, aligned with the two handwritten lines.
                            val step = lineHeight.toPx()
                            for (row in 1..2) {
                                val y = 6.dp.toPx() + step * row - 2.dp.toPx()
                                drawLine(BookLabelLine, Offset(6.dp.toPx(), y), Offset(size.width - 6.dp.toPx(), y), 1.dp.toPx())
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    Text(
                        stringResource(R.string.previous_sessions),
                        Modifier.fillMaxWidth(),
                        color = BookInk, fontFamily = HandwrittenFont, fontWeight = FontWeight.Bold,
                        fontSize = 16.sp, lineHeight = lineHeight, textAlign = TextAlign.Center, minLines = 2, maxLines = 2,
                    )
                }
                Spacer(Modifier.weight(1f))
                // No resumable session exists yet; preserve the honest disabled action.
                Text(
                    stringResource(R.string.continue_session),
                    Modifier.align(Alignment.End).testTag("continue_session")
                        .clickable(enabled = false, role = Role.Button, onClick = {}).padding(vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * A tear-off calendar. Tapping throws the title page up like a sheet of paper:
 * it curls, wraps over the red binding and settles folded behind the calendar,
 * revealing the challenge on the next page. Tapping again brings it back.
 */
@Composable
private fun DailyChallengeCard() {
    var flipped by rememberSaveable { mutableStateOf(false) }
    val ringXs = listOf(0.27f, 0.73f)
    val pageState = stringResource(if (flipped) R.string.challenge_page_open else R.string.challenge_page_closed)
    BoxWithConstraints(
        Modifier.fillMaxSize().testTag("daily_challenge")
            .semantics { stateDescription = pageState }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = { flipped = !flipped },
            ),
    ) {
        val ringTop = 7.dp
        val depth = 6.dp
        val bindingHeight = (maxHeight - ringTop) * 0.24f
        val pageHeight = maxHeight - ringTop - depth - bindingHeight
        val pageShape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
        val density = LocalDensity.current
        val bodyDepth = with(density) { 10.dp.toPx() }
        val sheet = remember(maxWidth, maxHeight, density) {
            with(density) {
                PaperSheet(
                    hingeY = (ringTop + bindingHeight).toPx(),
                    // The fold rests just above the binding's top edge.
                    topY = (ringTop - 2.dp).toPx(),
                    depth = bodyDepth,
                    length = pageHeight.toPx(),
                    gravity = 2200.dp.toPx(),
                    flipped = flipped,
                )
            }
        }
        LaunchedEffect(sheet, flipped) {
            if (sheet.atRest && sheet.restingFlipped == flipped) return@LaunchedEffect
            sheet.beginDrive(flipped)
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 1f / 30f)
                last = now
                if (sheet.advance(dt, flipped)) break
            }
        }
        val frontTexture = rememberGraphicsLayer()
        val backTexture = rememberGraphicsLayer()
        val showTitle = !(sheet.atRest && sheet.restingFlipped)
        val showChallenge = !(sheet.atRest && !sheet.restingFlipped)
        // Sheet parts behind the calendar body, visible only above its top edge.
        Canvas(Modifier.fillMaxSize()) {
            drawPaperSheet(sheet, frontTexture, backTexture, inFront = false, depth = bodyDepth)
        }
        DimensionalCard(
            face = SolidColor(PaperColor),
            edge = PaperEdge,
            pageLines = true,
            gloss = false,
            depth = depth,
            modifier = Modifier.fillMaxSize().padding(top = ringTop),
        ) {
            Column(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxWidth().height(bindingHeight)) { drawCalendarBinding(ringXs) }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    // The plain back of the sheet, recorded only.
                    Box(
                        Modifier.fillMaxSize()
                            .drawWithContent { backTexture.record { this@drawWithContent.drawContent() } }
                            .background(PaperBack, pageShape),
                    )
                    if (showChallenge) ChallengePage(Modifier.fillMaxSize())
                    if (showTitle) {
                        // Laid out in place but only recorded: the sheet draws it, bent.
                        CalendarTitlePage(
                            Modifier.fillMaxSize().drawWithContent {
                                frontTexture.record { this@drawWithContent.drawContent() }
                            },
                        )
                    }
                }
            }
        }
        // Sheet parts in front of the face, drawn over the paper and the binding.
        Canvas(Modifier.fillMaxSize()) {
            drawPaperSheet(sheet, frontTexture, backTexture, inFront = true, depth = bodyDepth)
        }
        Canvas(Modifier.fillMaxWidth().height(ringTop + bindingHeight)) {
            drawCalendarRings(ringXs, ringTop.toPx(), bindingHeight.toPx())
        }
    }
}

@Composable
private fun CalendarTitlePage(modifier: Modifier = Modifier) {
    Column(
        modifier.background(PaperColor, RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) { drawSparks(Color(0xFFF5A623)) }
            Text(
                stringResource(R.string.daily_challenge),
                Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                color = PencilInk, fontFamily = HandwrittenFont, fontWeight = FontWeight.Bold,
                fontSize = 21.sp, lineHeight = 23.sp, textAlign = TextAlign.Center, maxLines = 2,
            )
        }
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.fillMaxWidth().height(6.dp)) { drawCrayonUnderline(Color(0xFFE5484D)) }
    }
}

@Composable
private fun ChallengePage(modifier: Modifier = Modifier) {
    Column(
        modifier.background(PaperColor).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.example_challenge),
                Modifier.weight(1f),
                color = PencilInk, fontFamily = HandwrittenFont,
                fontSize = 15.sp, lineHeight = 17.sp, maxLines = 3,
            )
            Canvas(Modifier.size(24.dp)) { drawPencilCheckbox(Color(0xFF4A4440)) }
        }
        Text(
            stringResource(R.string.challenge_reward),
            color = Color(0xFF857A6D), fontFamily = HandwrittenFont, fontSize = 12.sp, maxLines = 1,
        )
    }
}

@Composable
private fun InventoryCard() {
    DimensionalCard(
        face = SolidColor(ChestGold),
        edge = ChestEdge,
        gloss = false,
        modifier = Modifier.fillMaxSize().testTag("inventory"),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val lid = maxHeight * ChestLayout.LidFraction
            val frame = maxWidth * ChestLayout.FrameFraction
            val interiorShape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
            Canvas(Modifier.fillMaxSize()) { drawChestLid() }
            Box(
                Modifier.fillMaxSize().padding(start = frame, end = frame, top = lid, bottom = frame)
                    .clip(interiorShape)
                    .background(ChestInside)
                    .border(1.5.dp, ChestOutline, interiorShape),
            ) {
                // The existing moving slots, unchanged, tinted to sit inside the chest.
                InventoryPreviews(
                    Modifier.fillMaxSize(),
                    fill = Color(0xFFFFE2B8).copy(alpha = 0.075f),
                    stroke = Color(0xFFFFE2B8).copy(alpha = 0.16f),
                )
            }
            Canvas(Modifier.fillMaxSize()) { drawChestHardware() }
            Text(
                stringResource(R.string.inventory),
                Modifier.align(Alignment.TopCenter).padding(top = maxHeight * ChestLayout.TitleFraction),
                color = InkColor, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
            )
            Text(
                stringResource(R.string.no_items_yet),
                Modifier.align(Alignment.Center).padding(top = lid * 0.6f),
                color = Color(0xFFF6DDBE), fontSize = 12.sp,
            )
        }
    }
}

/** Identical empty slots repeat by one pitch, so wrapping produces no visible seam. */
@Composable
private fun InventoryPreviews(
    modifier: Modifier = Modifier,
    fill: Color = Color.White.copy(alpha = 0.045f),
    stroke: Color = Color.White.copy(alpha = 0.12f),
) {
    val transition = rememberInfiniteTransition(label = "inventory_background")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "inventory_slot_phase",
    )
    Canvas(modifier.testTag("inventory_previews").blur(0.8.dp)) {
        val slot = 36.dp.toPx()
        val pitch = 50.dp.toPx()
        var y = -pitch / 2f
        while (y < size.height) {
            var x = -pitch + phase * pitch
            while (x < size.width) {
                drawRoundRect(fill, Offset(x, y), Size(slot, slot), CornerRadius(3.dp.toPx()))
                drawRoundRect(stroke, Offset(x, y), Size(slot, slot), CornerRadius(3.dp.toPx()), style = Stroke(0.8.dp.toPx()))
                x += pitch
            }
            y += pitch
        }
    }
}
