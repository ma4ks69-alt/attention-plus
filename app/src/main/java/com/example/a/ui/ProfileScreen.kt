package com.example.a.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a.R

internal data class ProfileSubject(
    val id: String,
    val name: String,
    val points: Int = 0,
    val minutes: Int = 0,
    val dailyPoints: List<Int> = emptyList(),
)

internal data class ProfileSummary(
    val username: String,
    val handle: String,
    val allTimePoints: Int,
    val streakDays: Int,
    val subjects: List<ProfileSubject>,
    val photo: String = "",
)

// Empty preview/isolated-test data; production receives the current account projection.
private val EmptyProfile = ProfileSummary(
    "Felhasználó", "", 0, 0,
    listOf(ProfileSubject("1", "Matematika"), ProfileSubject("2", "Irodalom"), ProfileSubject("3", "Angol")),
)

@Composable
internal fun ProfileScreen(
    modifier: Modifier = Modifier,
    profile: ProfileSummary = EmptyProfile,
) {
    val repository=com.example.a.data.AccountSession.repository
    var selectedId by rememberSaveable { mutableStateOf(repository?.record("preferences","app")?.optString("profileSubject")?.takeIf { it.isNotEmpty() } ?: profile.subjects.firstOrNull()?.id) }
    var dropdownOpen by rememberSaveable { mutableStateOf(false) }
    val sync=repository?.state?.collectAsState()?.value
    LaunchedEffect(sync?.revision) {
        val saved=repository?.record("preferences","app")?.optString("profileSubject")
        if(!saved.isNullOrBlank() && profile.subjects.any{it.id==saved}) selectedId=saved
    }
    val subject = profile.subjects.find { it.id == selectedId } ?: profile.subjects.firstOrNull()
    Column(
        modifier.testTag("profile_page").verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 128.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(88.dp).border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    AccountAvatar(profile.photo,Modifier.size(80.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.username, color = InkColor, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(profile.handle, color = SecondaryInkColor, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileMetric(profile.allTimePoints.toString(), stringResource(R.string.all_time_points), Modifier.weight(1f))
                        ProfileMetric(profile.streakDays.toString(), stringResource(R.string.current_streak), Modifier.weight(1f))
                    }
                }
            }
            ProfileAccountActions()
            Spacer(Modifier.height(32.dp))
            Column(
                Modifier.fillMaxWidth().testTag("subject_summary"),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1.2f)) {
                        Column(
                            Modifier.fillMaxWidth().testTag("subject_selector")
                                .clickable(role = Role.Button, enabled = profile.subjects.isNotEmpty()) { dropdownOpen = !dropdownOpen }
                                .padding(vertical = 4.dp),
                        ) {
                            Text(stringResource(R.string.subject_label), color = SecondaryInkColor, fontSize = 11.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(subject?.name ?: "—", Modifier.weight(1f), color = InkColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Canvas(Modifier.size(12.dp)) {
                                    drawLine(InkColor, Offset(2.dp.toPx(), 4.dp.toPx()), Offset(6.dp.toPx(), 8.dp.toPx()), 1.2.dp.toPx(), StrokeCap.Round)
                                    drawLine(InkColor, Offset(6.dp.toPx(), 8.dp.toPx()), Offset(10.dp.toPx(), 4.dp.toPx()), 1.2.dp.toPx(), StrokeCap.Round)
                                }
                            }
                        }
                        DropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false }, containerColor = PageColor) {
                            profile.subjects.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.name, color = InkColor) },
                                    modifier = Modifier.testTag("subject_option_${item.id}"),
                                    onClick = { selectedId = item.id; dropdownOpen = false; repository?.write("preferences","app",org.json.JSONObject().put("profileSubject",item.id)) },
                                )
                            }
                        }
                    }
                    ProfileMetric((subject?.points ?: 0).toString(), stringResource(R.string.subject_points), Modifier.weight(1f).padding(top = 4.dp))
                    ProfileMetric(stringResource(R.string.minutes_value, subject?.minutes ?: 0), stringResource(R.string.time_learning), Modifier.weight(1f).padding(top = 4.dp))
                }
                Spacer(Modifier.height(24.dp))
                DailyPointsGraph(subject)
            }
        }
    }
}

@Composable
private fun ProfileMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = InkColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = SecondaryInkColor, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun DailyPointsGraph(subject: ProfileSubject?) {
    val description = stringResource(R.string.daily_points_for, subject?.name ?: "—")
    val points = subject?.dailyPoints.orEmpty()
    Column(
        Modifier.fillMaxWidth().testTag("daily_points_graph")
            .semantics { contentDescription = description }
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(4.dp)).padding(16.dp),
    ) {
        Text(stringResource(R.string.daily_points), color = InkColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(subject?.name ?: "—", color = SecondaryInkColor, fontSize = 12.sp)
        Box(Modifier.fillMaxWidth().height(220.dp).padding(top = 20.dp, bottom = 12.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                repeat(5) { index ->
                    val y = size.height * index / 4f
                    drawLine(Color.White.copy(alpha = 0.11f), Offset(0f, y), Offset(size.width, y), 0.6.dp.toPx())
                }
                if (points.isNotEmpty()) {
                    val maximum = points.maxOrNull()?.coerceAtLeast(1) ?: 1
                    val path = Path()
                    points.forEachIndexed { index, value ->
                        val x = size.width * index / (points.size - 1).coerceAtLeast(1)
                        val y = size.height * (1f - value.coerceAtLeast(0).toFloat() / maximum)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        drawCircle(AccentColor, 2.5.dp.toPx(), Offset(x, y))
                    }
                    drawPath(path, AccentColor, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            if (points.isEmpty()) {
                Text(stringResource(R.string.no_points_yet), Modifier.align(Alignment.Center), color = SecondaryInkColor, fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (1..7).forEach { day -> Text(day.toString(), color = SecondaryInkColor, fontSize = 10.sp) }
        }
        Text(stringResource(R.string.day_label), Modifier.align(Alignment.CenterHorizontally), color = SecondaryInkColor, fontSize = 10.sp)
    }
}
