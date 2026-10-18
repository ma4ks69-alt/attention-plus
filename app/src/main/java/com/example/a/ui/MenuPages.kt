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

/** Placeholder until the gem-based Store exists. No purchases are offered. */
@Composable
internal fun StoreScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.testTag("store_page").verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 128.dp),
    ) {
        MenuPageHeader(stringResource(R.string.menu_store), onBack)
        Spacer(Modifier.height(56.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(104.dp)
                    .background(Brush.verticalGradient(listOf(AccentColor.copy(alpha = 0.28f), AccentColor.copy(alpha = 0.1f))), CircleShape)
                    .border(1.dp, AccentColor.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                StoreIcon(Modifier.size(48.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.store_coming_soon), color = InkColor, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.store_description),
                Modifier.width(280.dp),
                color = SecondaryInkColor, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
            )
        }
    }
}

/** Basic settings page, ready for options as features arrive. */
@Composable
internal fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName.orEmpty()
    }
    val sectionShape = RoundedCornerShape(16.dp)
    Column(
        modifier.testTag("settings_page").verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 128.dp),
    ) {
        MenuPageHeader(stringResource(R.string.menu_settings), onBack)
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.settings_about), color = SecondaryInkColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), sectionShape)
                .border(0.8.dp, Color.White.copy(alpha = 0.09f), sectionShape)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_version), color = InkColor, fontSize = 15.sp)
            Text(version, color = SecondaryInkColor, fontSize = 15.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.settings_more), color = SecondaryInkColor.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 18.sp)
    }
}
