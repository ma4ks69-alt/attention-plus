package com.example.a.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/** Full-resolution native blur and AGSL lensing; the tiny fill never replaces refraction. */
internal fun Modifier.clearLiquidGlass(
    backdrop: Backdrop,
    shape: () -> Shape = { CircleShape },
    lensHeight: Dp = 24.dp,
    lensAmount: Dp = 34.dp,
    blurRadius: Dp = 1.5.dp,
    surfaceAlpha: Float = 0.025f,
    shadowRadius: Dp = 18.dp,
    exportedBackdrop: LayerBackdrop? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = shape,
    effects = {
        vibrancy()
        blur(blurRadius.toPx())
        lens(lensHeight.toPx(), lensAmount.toPx(), depthEffect = true, chromaticAberration = true)
    },
    exportedBackdrop = exportedBackdrop,
    highlight = {
        Highlight(
            width = 0.65.dp,
            blurRadius = 0.25.dp,
            alpha = 0.95f,
            style = HighlightStyle.Default(color = Color.White.copy(alpha = 0.65f), falloff = 1.6f),
        )
    },
    shadow = {
        Shadow(radius = shadowRadius, offset = DpOffset(0.dp, 4.dp), color = Color.Black.copy(alpha = 0.32f))
    },
    innerShadow = {
        InnerShadow(radius = 6.dp, offset = DpOffset(0.dp, 2.dp), color = Color.Black.copy(alpha = 0.18f))
    },
    layerBlock = layerBlock,
    onDrawSurface = { drawRect(Color.White.copy(alpha = surfaceAlpha)) },
)
