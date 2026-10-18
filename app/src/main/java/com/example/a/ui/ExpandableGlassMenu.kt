package com.example.a.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a.R
import com.kyant.backdrop.Backdrop
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun ExpandableGlassMenu(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onItemSelected: (GlassMenuItem) -> Unit = {},
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExpandableGlassMenu(backdrop, expanded, { expanded = it }, modifier, onItemSelected)
}

@Composable
internal fun ExpandableGlassMenu(
    backdrop: Backdrop,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onItemSelected: (GlassMenuItem) -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reveal by animateFloatAsState(
        if (expanded) 1f else 0f,
        spring(GlassMotion.DampingRatio, GlassMotion.Stiffness),
        label = "glass_menu_reveal",
    )
    val turn by animateFloatAsState(
        if (expanded) 1f else 0f,
        spring(0.9f, GlassMotion.ChevronStiffness),
        label = "glass_menu_turn",
    )
    val compression by animateFloatAsState(
        if (pressed) 1f else 0f,
        spring(GlassMotion.DampingRatio, GlassMotion.Stiffness * 1.5f),
        label = "glass_menu_press",
    )
    val menuDescription = stringResource(R.string.glass_menu)
    val menuState = stringResource(if (expanded) R.string.menu_expanded else R.string.menu_collapsed)

    BoxWithConstraints(modifier) {
        val diameter = 56.dp
        val expandedWidth = minOf(264.dp, maxWidth)
        // The reference stretches before settling into its final outline. Preserve
        // one lens and its left anchor, with a small coordinated vertical swell.
        val progress = reveal.coerceIn(0f, 1f)
        val menuWidth = (diameter + (expandedWidth - diameter) * reveal).coerceIn(diameter, maxWidth)
        val swell = sin(PI.toFloat() * progress) * 4f
        val menuHeight = diameter + swell.dp
        Box(
            Modifier
                .width(menuWidth)
                .height(menuHeight)
                .offset(y = (-swell / 2f).dp)
                .testTag("expandable_glass")
                .clearLiquidGlass(
                    backdrop = backdrop,
                    lensHeight = 18.dp,
                    lensAmount = (30f + 2f * compression).dp,
                    blurRadius = 1.dp,
                    layerBlock = {
                        // Compress the complete lens about the original circle. This
                        // transforms sampled glass and content together on each press.
                        transformOrigin = TransformOrigin(28.dp.toPx() / size.width, 0.5f)
                        scaleX = 1f - 0.035f * compression
                        scaleY = 1f - 0.06f * compression
                    },
                )
                .clip(CircleShape),
        ) {
            if (progress > 0f) {
                // Targets emerge from the original circle in the same direction
                // as the advancing glass edge; clipping reveals them without a fade.
                GlassMenuItem.entries.forEachIndexed { index, item ->
                    val slotWidth = (expandedWidth - diameter) / 2
                    val targetX = diameter + slotWidth * index
                    val x = diameter / 2 + (targetX - diameter / 2) * progress
                    Row(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = x)
                            .width(slotWidth)
                            .height(diameter)
                            .testTag("menu_${item.name.lowercase()}")
                            .clickable(
                                enabled = expanded && progress > 0.95f,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = { onItemSelected(item) },
                            ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (item) {
                            GlassMenuItem.Store -> StoreIcon(Modifier.size(18.dp))
                            GlassMenuItem.Settings -> SettingsIcon(Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(if (item == GlassMenuItem.Store) R.string.menu_store else R.string.menu_settings),
                            color = InkColor, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                        )
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(diameter)
                    .testTag("glass_menu_toggle")
                    .semantics {
                        contentDescription = menuDescription
                        stateDescription = menuState
                    }
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = { onExpandedChange(!expanded) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    Modifier.size(20.dp).graphicsLayer { rotationZ = -90f * turn },
                ) {
                    val left = Offset(size.width * 0.2f, size.height * 0.35f)
                    val tip = Offset(size.width * 0.5f, size.height * 0.65f)
                    val right = Offset(size.width * 0.8f, size.height * 0.35f)
                    drawLine(InkColor, left, tip, 1.8.dp.toPx(), StrokeCap.Round)
                    drawLine(InkColor, tip, right, 1.8.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}
