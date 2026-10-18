package com.example.a.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.a.R
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.abs
import kotlin.math.roundToInt

private val TabIcons = listOf(R.drawable.ic_home, R.drawable.ic_timer, R.drawable.ic_profile)
private val TabLabels = listOf(R.string.tab_home, R.string.tab_focus, R.string.tab_profile)

@Composable
internal fun GlassBottomNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val barBackdrop = rememberLayerBackdrop()
    val iconsBackdrop = rememberLayerBackdrop()
    val lensBackdrop = rememberCombinedBackdrop(backdrop, barBackdrop)
    val position = remember {
        Animatable(selectedTab.toFloat()).apply { updateBounds(0f, (TabIcons.size - 1).toFloat()) }
    }
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(selectedTab.toFloat()) }
    var fingerPressed by remember { mutableStateOf(false) }
    var dragSpeed by remember { mutableFloatStateOf(0f) }
    var releasePosition by remember { mutableStateOf<Float?>(null) }
    var releaseVelocity by remember { mutableFloatStateOf(0f) }
    val currentSelection by rememberUpdatedState(selectedTab)
    val selectTab by rememberUpdatedState(onTabSelected)
    val dragTracker = remember { VelocityTracker() }
    val press by animateFloatAsState(
        if (fingerPressed) 1f else 0f,
        spring(GlassMotion.DampingRatio, GlassMotion.Stiffness),
        label = "navigation_glass_press",
    )

    val stretch by animateFloatAsState(
        dragSpeed, spring(0.95f, 1200f), label = "navigation_drag_stretch",
    )

    LaunchedEffect(selectedTab, dragging, fingerPressed) {
        if (dragging || fingerPressed) return@LaunchedEffect
        // Retain the finger's last position until the animator has taken over. A
        // release to the already-selected tab also runs this spring, without a jump.
        releasePosition?.let {
            position.snapTo(it)
            releasePosition = null
        }
        position.animateTo(
            targetValue = selectedTab.toFloat(),
            animationSpec = spring(
                dampingRatio = GlassMotion.DampingRatio,
                stiffness = GlassMotion.Stiffness,
            ),
            initialVelocity = releaseVelocity,
        )
        releaseVelocity = 0f
    }

    BoxWithConstraints(
        modifier.widthIn(max = 360.dp).fillMaxWidth().height(72.dp),
    ) {
        val inset = 6.dp
        val tabWidth = (maxWidth - inset * 2) / TabIcons.size
        val tabWidthPx = with(LocalDensity.current) { tabWidth.toPx() }
        val insetPx = with(LocalDensity.current) { inset.toPx() }
        val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val dragState = stringResource(if (dragging) R.string.nav_dragging else R.string.nav_resting)

        fun finishDrag(cancelled: Boolean) {
            if (!dragging) return
            releasePosition = dragPosition
            releaseVelocity = if (cancelled) 0f else {
                (dragTracker.calculateVelocity().x / tabWidthPx * if (ltr) 1f else -1f).coerceIn(-4f, 4f)
            }
            val closest = if (cancelled) currentSelection else dragPosition.roundToInt().coerceIn(0, 2)
            selectTab(closest)
            dragging = false
            dragSpeed = 0f
        }

        // Export only the glass surface. The moving lens never captures itself.
        Box(
            Modifier.fillMaxSize().testTag("glass_navigation").clearLiquidGlass(
                backdrop = backdrop,
                exportedBackdrop = barBackdrop,
            ),
        )

        // Record the actual icon pixels before the lens draws. The foreground lens
        // refracts this capture, rather than placing flat icons above the glass.
        Row(
            Modifier.fillMaxSize()
                .pointerInput(tabWidthPx, ltr) {
                    // One pointer owner handles both taps and holds. There are no
                    // child click handlers to consume movement or cancel the hold.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val value = (releasePosition ?: position.value).coerceIn(0f, 2f)
                        val physicalSlot = if (ltr) value else 2f - value
                        val left = insetPx + physicalSlot * tabWidthPx
                        val onLens = down.position.x in left..(left + tabWidthPx) &&
                            down.position.y in insetPx..(size.height - insetPx)
                        val grabOffset = down.position.x - left
                        val tappedSlot = ((down.position.x - insetPx) / tabWidthPx).toInt().coerceIn(0, 2)
                        var latest = down
                        var moved = false
                        fingerPressed = onLens
                        try {
                            val held = onLens && withTimeoutOrNull(GlassMotion.HoldMillis) {
                                while (latest.pressed) {
                                    latest = awaitPointerEvent().changes.first { it.id == down.id }
                                    moved = moved || (latest.position - down.position).getDistance() > viewConfiguration.touchSlop
                                    latest.consume()
                                }
                                false
                            } == null
                            if (held && latest.pressed) {
                                dragPosition = value
                                dragTracker.resetTracking()
                                dragTracker.addPosition(down.uptimeMillis, down.position)
                                dragging = true
                                fun followFinger() {
                                    dragTracker.addPosition(latest.uptimeMillis, latest.position)
                                    val slot = (latest.position.x - grabOffset - insetPx) / tabWidthPx
                                    dragPosition = (if (ltr) slot else 2f - slot).coerceIn(0f, 2f)
                                    dragSpeed = (abs(dragTracker.calculateVelocity().x) / tabWidthPx / 10f).coerceIn(0f, 1f)
                                }
                                followFinger()
                                while (latest.pressed) {
                                    latest = awaitPointerEvent().changes.first { it.id == down.id }
                                    latest.consume()
                                    followFinger()
                                }
                                finishDrag(cancelled = false)
                            } else {
                                // Unselected tabs remain ordinary tap targets. A quick
                                // swipe is not accidentally interpreted as a tab click.
                                while (latest.pressed) {
                                    latest = awaitPointerEvent().changes.first { it.id == down.id }
                                    moved = moved || (latest.position - down.position).getDistance() > viewConfiguration.touchSlop
                                    latest.consume()
                                }
                                if (!moved) selectTab(if (ltr) tappedSlot else 2 - tappedSlot)
                            }
                        } finally {
                            fingerPressed = false
                            if (dragging) finishDrag(cancelled = true)
                        }
                    }
                }
                .padding(inset).layerBackdrop(iconsBackdrop).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabIcons.forEachIndexed { index, icon ->
                val tint by animateColorAsState(
                    if (selectedTab == index) AccentColor else SecondaryInkColor,
                    animationSpec = spring(
                        dampingRatio = GlassMotion.DampingRatio,
                        stiffness = GlassMotion.Stiffness,
                    ),
                    label = "tab_icon_tint",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .semantics(mergeDescendants = true) {
                            selected = selectedTab == index
                            role = Role.Tab
                            onClick { selectTab(index); true }
                        }
                        .onKeyEvent {
                            if (it.type == KeyEventType.KeyUp && (it.key == Key.Enter || it.key == Key.Spacebar)) {
                                selectTab(index)
                                true
                            } else false
                        }
                        .focusable(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = stringResource(TabLabels[index]),
                        tint = tint,
                        modifier = Modifier.size(27.dp),
                    )
                }
            }
        }

        // Clip the complete moving lens to the inner capsule, including its
        // highlights and deformation. The outer bar's shadow remains unclipped.
        Box(Modifier.fillMaxSize().padding(inset).clip(CircleShape)) {
            // One persistent lens, physically placed at the animated position. offset is
            // layout-aware so the shader samples its current location, including in RTL.
            Box(
                Modifier
                    .offset {
                        val value = if (dragging) dragPosition else releasePosition ?: position.value
                        IntOffset((value.coerceIn(0f, 2f) * tabWidthPx).roundToInt(), 0)
                    }
                    .width(tabWidth)
                    .height(60.dp)
                    .testTag("selection_lens")
                    .semantics { stateDescription = dragState }
                    .clearLiquidGlass(
                        backdrop = lensBackdrop,
                        lensHeight = 20.dp,
                        lensAmount = (32f + 2f * press).dp,
                        blurRadius = 0.6.dp,
                        surfaceAlpha = 0.035f,
                        shadowRadius = 6.dp,
                        layerBlock = {
                            // A restrained stretch while traveling, settling back to a capsule.
                            val motion = if (dragging) stretch else (abs(position.velocity) / 12f).coerceIn(0f, 1f)
                            val value = (if (dragging) dragPosition else releasePosition ?: position.value).coerceIn(0f, 2f)
                            val edgeRoom = minOf(value, 2f - value)
                            scaleX = minOf(1f + 0.045f * motion + 0.025f * press, 1f + 2f * edgeRoom)
                            scaleY = 1f - 0.025f * motion - 0.035f * press
                        },
                    ),
            ) {
                // Icons sit at a shallower optical depth than the page scenery.
                // This preserves true shader deformation/dispersion without copying
                // distant icon strokes onto the strong background lens's rims.
                Box(
                    Modifier.fillMaxSize().drawPlainBackdrop(
                        backdrop = iconsBackdrop,
                        shape = { CircleShape },
                        effects = {
                            lens(18.dp.toPx(), (12f + 4f * press).dp.toPx(), depthEffect = true, chromaticAberration = true)
                        },
                    ),
                )
            }
        }
    }
}
