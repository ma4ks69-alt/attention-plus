package com.example.a.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur

/** One continuous edge-to-edge frost, without an enclosing card or border. */
internal fun Modifier.profileFrost(backdrop: Backdrop): Modifier = drawPlainBackdrop(
    backdrop = backdrop,
    shape = { RectangleShape },
    effects = {
        // This scenery is monochrome. Starting with clamped native blur keeps
        // full-screen edges opaque instead of blurring transparent shader padding.
        blur(24.dp.toPx())
    },
    onDrawSurface = {
        drawRect(Color.Black.copy(alpha = 0.2f))
        drawRect(Color.White.copy(alpha = 0.008f))
    },
)
