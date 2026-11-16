package com.example.a.ui

import android.content.ClipboardManager
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.a.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The learning subjects offered for a session, each with its tile colour. */
internal enum class Subject(val label: Int, val color: Color) {
    Math(R.string.subject_math, Color(0xFF1A8CFF)),
    Literature(R.string.subject_literature, Color(0xFFA259FF)),
    English(R.string.subject_english, Color(0xFFFF5A5F)),
    Biology(R.string.subject_biology, Color(0xFF3DB212)),
    Science(R.string.subject_science, Color(0xFFFF9F1C)),
    ForeignLanguage(R.string.subject_foreign_language, Color(0xFF14B8A6)),
}

private const val SubjectStep = 0
private const val TimeStep = 1
private const val NotesStep = 2

/**
 * Start Learning: pick a subject, set the study time, then add notes as photos
 * or pasted text. [onStartSession] receives the chosen time in seconds.
 */
@Composable
internal fun LearnScreen(
    onStartSession: (Int) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val repository=com.example.a.data.AccountSession.repository
    val draft=androidx.compose.runtime.remember { repository?.record("drafts","learning") ?: org.json.JSONObject() }
    val scope=androidx.compose.runtime.rememberCoroutineScope()
    var importError by rememberSaveable { mutableStateOf<String?>(null) }
    var step by rememberSaveable { mutableIntStateOf(draft.optInt("step",SubjectStep)) }
    var subject by rememberSaveable { mutableStateOf<Subject?>(Subject.entries.find { it.name==draft.optString("subject") }) }
    var seconds by rememberSaveable { mutableIntStateOf(draft.optInt("seconds",(repository?.record("preferences","app")?.optInt("defaultStudyMinutes",25) ?: 25)*60)) }
    // Photo URIs, newline separated so the list survives recreation.
    var photos by rememberSaveable { mutableStateOf(draft.optString("photos")) }
    var pastedText by rememberSaveable { mutableStateOf(draft.optString("text")) }
    val sync=repository?.state?.collectAsState()?.value
    LaunchedEffect(sync?.revision) {
        val saved=repository?.record("drafts","learning") ?: return@LaunchedEffect
        if(saved.has("step")) step=saved.optInt("step").coerceIn(SubjectStep,NotesStep)
        if(saved.has("subject")) subject=Subject.entries.find { it.name==saved.optString("subject") }
        if(saved.has("seconds")) seconds=saved.optInt("seconds")
        if(saved.has("photos")) photos=saved.optString("photos")
        if(saved.has("text")) pastedText=saved.optString("text")
    }
    fun saveDraft(vararg fields: String) {
        val all=org.json.JSONObject().put("step",step).put("subject",subject?.name ?: "History").put("seconds",seconds).put("photos",photos).put("text",pastedText.take(50000))
        val patch=org.json.JSONObject(); fields.forEach { patch.put(it,all.get(it)) }; repository?.write("drafts","learning",patch)
    }
    if(importError!=null) AlertDialog(onDismissRequest={importError=null},title={Text("A kép mentése nem sikerült")},text={Text(importError!!)},confirmButton={TextButton(onClick={importError=null}){Text("Rendben")}})
    BackHandler(enabled = step > SubjectStep) { step--; saveDraft("step") }

    Crossfade(step, modifier.testTag("learn_page"), animationSpec = tween(if(LocalReducedMotion.current)0 else 220), label = "learn_step") { shown ->
        when (shown) {
            SubjectStep -> SubjectStepPage(onBack = onBack, onSelect = {
                subject = it
                step = TimeStep; saveDraft("subject","step")
            })
            TimeStep -> TimeStepPage(
                onBack={step--;saveDraft("step")},
                subject = subject,
                seconds = seconds,
                onSecondsChange = { seconds = it; saveDraft("seconds") },
                onNext = { step = NotesStep; saveDraft("step") },
            )
            else -> NotesStepPage(
                onBack={step--;saveDraft("step")},
                photos = photos.split('\n').filter { it.isNotEmpty() },
                pastedText = pastedText,
                onPhotosAdded = { added ->
                    scope.launch {
                        try {
                            val stored=added.map { repository?.importImage(it) ?: it.toString() }
                            photos=(photos.split('\n')+stored).filter{it.isNotEmpty()}.take(10).joinToString("\n"); saveDraft("photos")
                        } catch(e: Exception) { importError="A kép nem menthető. Ellenőrizd a fájlt és a szabad tárhelyet." }
                    }
                },
                onTextPasted = { pastedText = it.take(50000); saveDraft("text") },
                onStart = { onStartSession(seconds) },
            )
        }
    }
}

/** Large rounded headline in the same bold, cartoon style as Start Learning. */
@Composable
private fun LearnTitle(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth(),
        color = InkColor,
        fontSize = 28.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,

    )
}

@Composable
private fun SubjectStepPage(onBack: () -> Unit, onSelect: (Subject) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 180.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onBack,tag="learn_back")
                Text(stringResource(R.string.learn_select_subject), Modifier.weight(1f), color = InkColor,
                    fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Subject.entries.chunked(2).forEach { subjects ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        subjects.forEach { item -> SubjectCard(item, { onSelect(item) }, Modifier.weight(1f)) }
                    }
                }
            }

        }
    }
}

@Composable
private fun TimeStepPage(
    onBack:()->Unit,
    subject: Subject?,
    seconds: Int,
    onSecondsChange: (Int) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 140.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageHeading(stringResource(R.string.learn_select_time),onBack)
        if (subject != null) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(subject.label), color = subject.color, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(40.dp))
        DigitTimer(
            seconds = seconds,
            digitColor = AccentColor,
            accent = AccentColor,
            glow = 0f,
            controls = 1f,
            enabled = true,
            onChange = onSecondsChange,
            tagPrefix = "learn",
        )
        Spacer(Modifier.height(40.dp))
        DepthButton(
            text = stringResource(R.string.learn_next),
            color = AccentColor,
            onClick = onNext,
            enabled = seconds > 0,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(60.dp).testTag("learn_next"),
        )
    }
}

@Composable
private fun NotesStepPage(
    onBack:()->Unit,
    photos: List<String>,
    pastedText: String,
    onPhotosAdded: (List<Uri>) -> Unit,
    onTextPasted: (String) -> Unit,
    onStart: () -> Unit,
) {
    val context = LocalContext.current
    var choosing by rememberSaveable { mutableStateOf(false) }
    var pasting by rememberSaveable { mutableStateOf(false) }
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) onPhotosAdded(uris)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingPhoto
        if (saved && uri != null) onPhotosAdded(listOf(Uri.parse(uri)))
        pendingPhoto = null
    }
    val hasNotes = photos.isNotEmpty() || pastedText.isNotBlank()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 140.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageHeading(stringResource(R.string.learn_upload_notes),onBack)
        Spacer(Modifier.height(32.dp))
        Row(
            Modifier.background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            NoteSourceTile(
                label = stringResource(R.string.learn_add_photos),
                tag = "learn_add_photos",
                onClick = { choosing = true },
            ) { drawPlusIcon() }
            NoteSourceTile(
                label = stringResource(R.string.learn_paste_text),
                tag = "learn_paste",
                onClick = { pasting = true },
            ) { drawPasteIcon() }
        }
        if (photos.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                photos.take(4).forEach { if(it.startsWith("users/")) AccountImage(it,Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))) else NoteThumbnail(Uri.parse(it)) }
                if (photos.size > 4) {
                    Text("+${photos.size - 4}", color = SecondaryInkColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (pastedText.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                pastedText,
                Modifier.widthIn(max = 320.dp).fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("learn_pasted_text"),
                color = SecondaryInkColor, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 3,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(36.dp))
        DepthButton(
            text = stringResource(R.string.learn_start),
            color = AccentColor,
            onClick = onStart,
            enabled = hasNotes,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(60.dp).testTag("learn_start"),
        )
    }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { PageHeading(stringResource(R.string.learn_add_photos),{choosing=false}) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    DepthButton(
                        text = stringResource(R.string.learn_choose_gallery),
                        color = AccentColor,
                        fontSize = 16.sp,
                        onClick = {
                            choosing = false
                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("learn_choose_gallery"),
                    )
                    DepthButton(
                        text = stringResource(R.string.learn_take_photo),
                        color = AccentColor,
                        fontSize = 16.sp,
                        onClick = {
                            choosing = false
                            val dir = File(context.filesDir, "camera/${com.example.a.data.AccountSession.repository?.uid ?: "local"}").apply { mkdirs() }
                            val file = File.createTempFile("note_", ".jpg", dir)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            pendingPhoto = uri.toString()
                            camera.launch(uri)
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("learn_take_photo"),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.learn_cancel)) }
            },
        )
    }
    if (pasting) {
        var draft by rememberSaveable {
            val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
            mutableStateOf(
                if (pastedText.isNotBlank()) pastedText
                else clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty(),
            )
        }
        AlertDialog(
            onDismissRequest = { pasting = false },
            title = { PageHeading(stringResource(R.string.learn_paste_text),{pasting=false}) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(stringResource(R.string.learn_paste_hint)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag("learn_paste_field"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTextPasted(draft.trim())
                        pasting = false
                    },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.testTag("learn_paste_save"),
                ) { Text(stringResource(R.string.learn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { pasting = false }) { Text(stringResource(R.string.learn_cancel)) }
            },
        )
    }
}

/** A raised square source button with a drawn icon and a label beneath. */
@Composable
private fun NoteSourceTile(
    label: String,
    tag: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(116.dp)) {
            DepthButton(
                text = "",
                color = AccentColor,
                onClick = onClick,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxSize().testTag(tag),
            )
            Canvas(Modifier.size(44.dp).align(Alignment.Center).padding(bottom = 0.dp)) { icon() }
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = InkColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlusIcon() {
    val stroke = size.minDimension * 0.16f
    val c = center.copy(y = center.y - 2.dp.toPx())
    val half = size.minDimension * 0.38f
    drawLine(Color.White, Offset(c.x - half, c.y), Offset(c.x + half, c.y), stroke, StrokeCap.Round)
    drawLine(Color.White, Offset(c.x, c.y - half), Offset(c.x, c.y + half), stroke, StrokeCap.Round)
}

/** A clipboard: board, clip and two text lines. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPasteIcon() {
    val s = size.minDimension
    val stroke = Stroke(s * 0.09f, cap = StrokeCap.Round)
    val top = -2.dp.toPx()
    drawRoundRect(Color.White, Offset(s * 0.16f, s * 0.14f + top), Size(s * 0.68f, s * 0.8f), CornerRadius(s * 0.1f), style = stroke)
    drawRoundRect(Color.White, Offset(s * 0.34f, s * 0.04f + top), Size(s * 0.32f, s * 0.18f), CornerRadius(s * 0.06f))
    drawLine(Color.White, Offset(s * 0.32f, s * 0.48f + top), Offset(s * 0.68f, s * 0.48f + top), s * 0.09f, StrokeCap.Round)
    drawLine(Color.White, Offset(s * 0.32f, s * 0.68f + top), Offset(s * 0.58f, s * 0.68f + top), s * 0.09f, StrokeCap.Round)
}

/** A small preview of one note photo, decoded off the main thread at thumbnail size. */
@Composable
private fun NoteThumbnail(uri: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val scale = 192f / maxOf(info.size.width, info.size.height)
                    if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }.asImageBitmap()
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(64.dp).clip(shape).background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape),
    ) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/**
 * The study session: everything fades to near black and the blue timer counts
 * down. Stop pauses at the current time (and resumes); Finish does nothing yet.
 */
@Composable
internal fun LearnSession(
    seconds: Int,
    paused: Boolean,
    onStopToggle: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(FocusBlack).testTag("learn_session"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DigitTimer(
                seconds = seconds,
                digitColor = AccentColor,
                accent = AccentColor,
                glow = 0f,
                controls = 0f,
                enabled = false,
                onChange = {},
                tagPrefix = "session",
            )
            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                DepthButton(
                    text = stringResource(if (paused) R.string.learn_resume else R.string.learn_stop),
                    color = AccentColor,
                    onClick = onStopToggle,
                    enabled = seconds > 0,
                    shape = CircleShape,
                    fontSize = 18.sp,
                    modifier = Modifier.width(140.dp).height(60.dp).testTag("session_stop"),
                )
                DepthButton(
                    text = stringResource(R.string.learn_finish),
                    color = AccentColor,
                    onClick = onFinish,
                    shape = CircleShape,
                    fontSize = 18.sp,
                    modifier = Modifier.width(140.dp).height(60.dp).testTag("session_finish"),
                )
            }
        }
    }
}
