package com.example.a.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a.R

/** Destinations offered by the top-left glass menu. */
internal enum class GlassMenuItem { Store, Settings }

/** Outline shopping bag, matching the stroke weight of the navigation icons. */
@Composable
internal fun StoreIcon(modifier: Modifier = Modifier, color: Color = InkColor) {
    Canvas(modifier) {
        val s = size.minDimension
        val stroke = Stroke(s * 0.09f, cap = StrokeCap.Round)
        drawRoundRect(color, Offset(s * 0.16f, s * 0.32f), Size(s * 0.68f, s * 0.58f), CornerRadius(s * 0.1f), style = stroke)
        drawArc(color, 180f, 180f, false, Offset(s * 0.34f, s * 0.1f), Size(s * 0.32f, s * 0.44f), style = stroke)
    }
}

/** Outline gear with eight teeth. */
@Composable
internal fun SettingsIcon(modifier: Modifier = Modifier, color: Color = InkColor) {
    Canvas(modifier) {
        val s = size.minDimension
        val c = center
        val width = s * 0.09f
        drawCircle(color, s * 0.27f, c, style = Stroke(width))
        drawCircle(color, s * 0.1f, c, style = Stroke(width))
        repeat(8) { i ->
            rotate(i * 45f, c) {
                drawLine(color, Offset(c.x, c.y - s * 0.31f), Offset(c.x, c.y - s * 0.45f), s * 0.13f, StrokeCap.Butt)
            }
        }
    }
}

@Composable
private fun BackChevron(modifier: Modifier = Modifier, color: Color = InkColor) {
    Canvas(modifier) {
        val stroke = 1.8.dp.toPx()
        val tip = Offset(size.width * 0.35f, size.height * 0.5f)
        drawLine(color, Offset(size.width * 0.65f, size.height * 0.2f), tip, stroke, StrokeCap.Round)
        drawLine(color, tip, Offset(size.width * 0.65f, size.height * 0.8f), stroke, StrokeCap.Round)
    }
}

/** Title row shared by menu destinations, with a circular back target. */
@Composable
private fun MenuPageHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).background(Color.White.copy(alpha = 0.07f), CircleShape)
                .border(0.8.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .testTag("menu_page_back")
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.back), onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            BackChevron(Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, color = InkColor, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}
