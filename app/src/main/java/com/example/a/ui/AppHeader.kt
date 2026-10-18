package com.example.a.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.a.R
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

internal val HeaderHeight = 88.dp

@Composable
internal fun AppHeader(
    modifier: Modifier = Modifier,
    onMenuItemSelected: (GlassMenuItem) -> Unit = {},
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val logoOpacity by animateFloatAsState(
        if (expanded) 0f else 1f,
        spring(0.9f, if (expanded) 1800f else GlassMotion.Stiffness),
        label = "header_logo_opacity",
    )
    val headerBackdrop = rememberLayerBackdrop()
    val topInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    val logo = ImageBitmap.imageResource(R.drawable.attention_logo)
    val logoPainter = remember(logo) {
        // Preserve the exact supplied PNG. Display only the artwork region with an
        // eight-pixel margin; its near-black background blends into the black header.
        BitmapPainter(logo, IntOffset(48, 412), IntSize(1175, 335), FilterQuality.High)
    }

    BoxWithConstraints(modifier.fillMaxWidth().background(Color.Black).testTag("app_header")) {
        // True horizontal centering, with room for the original circle on small screens.
        val logoWidth = minOf(244.dp, (maxWidth - 184.dp).coerceAtLeast(80.dp))
        Box(
            Modifier.fillMaxWidth().windowInsetsPadding(topInsets).height(HeaderHeight)
                .layerBackdrop(headerBackdrop).background(Color.Black),
        ) {
            Image(
                logoPainter,
                stringResource(R.string.attention_logo),
                Modifier.align(Alignment.Center).width(logoWidth).height(72.dp)
                    .testTag("header_logo").graphicsLayer { alpha = logoOpacity.coerceIn(0f, 1f) },
            )
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp)
                    .background(Color(0xFF292929)).testTag("header_divider"),
            )
        }
        ExpandableGlassMenu(
            backdrop = headerBackdrop,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            onItemSelected = {
                // Collapse with the usual reveal and logo-fade springs, then navigate.
                expanded = false
                onMenuItemSelected(it)
            },
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(topInsets)
                .padding(start = 24.dp, end = 24.dp, top = 16.dp),
        )
    }
}
