package com.example.a.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.a.R
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.testTag

private const val FocusPage = 1
private const val StorePage = 3
private const val SettingsPage = 4
private const val LearnPage = 5
private const val NoMenuPage = -1

/** Duration of the page fade into Focus, and of the session spread. */
private const val FocusFadeMillis = 600
private const val FocusSpreadMillis = 1000

@Composable
fun APlusScreen(modifier: Modifier = Modifier) { AppPreferencesHost { APlusContent(modifier) } }

@Composable
private fun APlusContent(modifier: Modifier = Modifier) {
    val reduced=LocalReducedMotion.current
    val context = LocalContext.current
    val learningStore = remember { LearningStore(context) }
    val repository=com.example.a.data.AccountSession.repository
    val syncState=repository?.state?.collectAsState()?.value
    val savedProfile=remember(syncState?.revision){repository?.profile() ?: org.json.JSONObject()}
    val savedLearning=remember(syncState?.revision){learningStore.read()}
    val savedFocus=remember { repository?.records("focus")?.filter { it.optBoolean("running") && !it.optBoolean("completed") }?.maxByOrNull { it.optLong("startedAt") } ?: org.json.JSONObject() }
    var focusId by rememberSaveable { mutableStateOf(savedFocus.optString("id")) }
    var focusStartedAt by rememberSaveable { mutableStateOf(savedFocus.optLong("startedAt")) }
    fun persistFocus(completed: Boolean, seconds: Int) {
        if(focusId.isNotBlank()) repository?.write("focus",focusId,org.json.JSONObject().put("id",focusId).put("kind","focus").put("subject","Focus")
            .put("startedAt",minOf(focusStartedAt,System.currentTimeMillis())).put("finishedAt",System.currentTimeMillis()).put("studiedSeconds",seconds)
            .put("running",!completed).put("completed",completed))
    }
    var showLearning by rememberSaveable { mutableStateOf(learningStore.read().has("session")) }
    var selectedTab by rememberSaveable { mutableIntStateOf(if(savedFocus.optBoolean("running") && !savedFocus.optBoolean("completed")) FocusPage else 0) }
    var menuPage by rememberSaveable { mutableIntStateOf(NoMenuPage) }
    val page = if (menuPage != NoMenuPage) menuPage else selectedTab
    var stack by rememberSaveable { mutableStateOf(listOf<Int>()) }
    fun navigate(target:Int){if(page!=target){stack=stack+page;menuPage=target}}
    fun goBack(){val previous=stack.lastOrNull() ?: selectedTab;stack=stack.dropLast(1);if(previous in 0..2){selectedTab=previous;menuPage=NoMenuPage}else menuPage=previous}
    var notebookOrigin by remember{mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)}
    var shown by remember{mutableIntStateOf(page)}
    val signPhysics=remember{SignPhysics()}
    var slideTo by remember{mutableIntStateOf(page)}
    var from by remember{mutableIntStateOf(page)}
    var sliding by remember{mutableStateOf(false)}
    var slideDirection by remember{mutableIntStateOf(1)}
    val travel=remember{Animatable(1f)}
    LaunchedEffect(page,reduced){
        val old=shown
        if(!reduced && old!=page && (old==StorePage || page==StorePage)){
            if(page==StorePage)signPhysics.reset(-838f)
            from=old;slideTo=page;slideDirection=if(page==StorePage)1 else -1;travel.snapTo(0f);sliding=true
            if(page==StorePage)travel.animateTo(-.045f,tween(115,easing=FastOutSlowInEasing))
            travel.animateTo(1f,androidx.compose.animation.core.spring(.94f,180f))
        }
        shown=page;sliding=false
    }
    // Claim the first Home presentation immediately, including an interrupted
    // entrance, so navigation/recreation never launches the cards a second time.
    var homePresented by rememberSaveable { mutableStateOf(false) }
    val pageState = rememberSaveableStateHolder()
    val sceneryBackdrop = rememberLayerBackdrop()
    // Capture the actual page including its background. Future content in this Box
    // will automatically appear through the glass, without capturing navigation itself.
    val pageBackdrop = rememberLayerBackdrop {
        drawRect(PageColor)
        drawContent()
    }
    val pageModifier = Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(top = HeaderHeight + 20.dp)

    // Focus session: the set time, whether the immersive session runs, and how
    // long it has been ticking. All survive recreation.
    var focusSetSeconds by rememberSaveable { mutableIntStateOf(savedFocus.optInt("duration", repository?.record("preferences","app")?.optInt("focusSeconds") ?: 0)) }
    var focusRunning by rememberSaveable { mutableStateOf(savedFocus.optBoolean("running") && !savedFocus.optBoolean("completed")) }
    StudySilence(focusRunning,focusStartedAt+(if(focusSetSeconds>0)focusSetSeconds else FocusMaxSeconds)*1000L)
    var focusTicking by rememberSaveable { mutableStateOf(false) }
    var focusElapsed by rememberSaveable { mutableIntStateOf(0) }
    val spread = remember { Animatable(if (focusRunning) 1f else 0f) }
    LaunchedEffect(focusRunning) {
        if (focusRunning) {
            spread.animateTo(1f, tween(if(reduced)0 else FocusSpreadMillis, easing = FastOutSlowInEasing))
            focusTicking = true
        } else {
            focusTicking = false
            spread.animateTo(0f, tween(if(reduced)0 else FocusSpreadMillis, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(focusTicking) {
        while (focusTicking) {
            delay(1000)
            focusElapsed = if(repository==null) focusElapsed+1 else ((System.currentTimeMillis()-focusStartedAt)/1000).toInt().coerceAtLeast(0)
            // A countdown ends itself; a timer started at 00:00 counts up to its limit.
            val limit = if (focusSetSeconds > 0) focusSetSeconds else FocusMaxSeconds
            if (focusElapsed >= limit) { studyFeedback(context,repository?.record("preferences","app") ?: org.json.JSONObject()); focusElapsed=limit; persistFocus(true,limit); focusRunning = false }
        }
    }
    val focusDisplay = if (focusSetSeconds > 0) {
        (focusSetSeconds - focusElapsed).coerceAtLeast(0)
    } else {
        focusElapsed.coerceAtMost(FocusMaxSeconds)
    }

    fun exitFocus() {
        persistFocus(true,focusElapsed)
        focusRunning = false
        focusElapsed = 0
    }
    BackHandler(enabled = focusRunning) { exitFocus() }
    BackHandler(enabled = menuPage != NoMenuPage && !focusRunning) { goBack() }

    LaunchedEffect(syncState?.revision,page) {
        if(focusId.isNotEmpty() && repository?.record("focus",focusId)?.optBoolean("completed")==true) focusRunning=false
        if(page==FocusPage && !focusRunning) {
            repository?.records("focus")?.filter { it.optBoolean("running") && !it.optBoolean("completed") }
                ?.maxByOrNull { it.optLong("startedAt") }?.let { active ->
                    focusId=active.getString("id");focusStartedAt=active.optLong("startedAt")
                    focusSetSeconds=active.optInt("duration")
                    focusElapsed=((System.currentTimeMillis()-focusStartedAt)/1000).toInt().coerceAtLeast(0)
                    focusRunning=true
                }
        }
    }

    // Entering Focus fades the previous page out while the dark surface fades in;
    // every other change of page is immediate.
    var lastPage by rememberSaveable { mutableIntStateOf(if (page == FocusPage) 0 else page) }
    if (page != FocusPage) SideEffect { lastPage = page }
    val focusFade = remember { Animatable(if (page == FocusPage) 1f else 0f) }
    LaunchedEffect(page) {
        if (page == FocusPage) {
            if (focusFade.value < 1f) focusFade.animateTo(1f, tween(if(reduced)0 else FocusFadeMillis, easing = LinearEasing))
        } else {
            focusFade.snapTo(0f)
        }
    }

    @Composable
    fun Page(target: Int, storePosition:()->Float={0f}) {
        pageState.SaveableStateProvider(target) {
            when (target) {
                StorePage -> {
                    FrostedPageBackground(sceneryBackdrop)
                    StoreScreen(onBack = { goBack() }, horizontalPosition=storePosition, physics=signPhysics, modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(top=HeaderHeight,bottom=72.dp))
                }
                6 -> NotebookScreen(learningStore,notebookOrigin,{goBack()},{showLearning=true},Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(top=HeaderHeight,bottom=72.dp))
                7 -> InventoryScreen({goBack()},pageModifier.padding(bottom=72.dp))
                LearnPage -> LearnScreen(
                    onStartSession = { learningStore.begin(it); showLearning = true },
                    onBack = { goBack() },
                    modifier = pageModifier,
                )
                SettingsPage -> {
                    FrostedPageBackground(sceneryBackdrop)
                    SettingsScreen(onBack = { goBack() }, modifier = pageModifier.padding(bottom=72.dp))
                }
                2 -> {
                    FrostedPageBackground(sceneryBackdrop, Modifier.testTag("profile_frost"))
                    val history=savedLearning.optJSONArray("completed") ?: org.json.JSONArray()
                    val subjects=Subject.entries.map { subject ->
                        val entries=(0 until history.length()).map{history.getJSONObject(it)}.filter{it.optString("subject")==subject.name}
                        ProfileSubject(subject.name,androidx.compose.ui.res.stringResource(subject.label),entries.sumOf{it.optInt("score")},entries.sumOf{it.optInt("durationSeconds")}/60,
                            (6 downTo 0).map { day -> entries.filter{it.optString("date")==java.time.LocalDate.now().minusDays(day.toLong()).toString()}.sumOf{it.optInt("score")} })
                    }
                    ProfileScreen(modifier=pageModifier,profile=ProfileSummary(savedProfile.optString("displayName","Felhasználó"),savedProfile.optString("username").takeIf{it.isNotBlank()}?.let{"@$it"} ?: savedProfile.optString("email",""),savedLearning.optInt("points"),savedLearning.optInt("streak"),subjects,savedProfile.optString("photo")))
                }
                FocusPage -> FocusScreen(
                    setSeconds = focusSetSeconds,
                    displaySeconds = focusDisplay,
                    running = focusRunning,
                    onSetSecondsChange = { focusSetSeconds = it; repository?.write("preferences","app",org.json.JSONObject().put("focusSeconds",it)) },
                    onStart = {
                        focusElapsed = 0
                        focusId=java.util.UUID.randomUUID().toString()
                        focusStartedAt=System.currentTimeMillis()+(if(reduced)0 else FocusSpreadMillis)
                        val limit=if(focusSetSeconds>0)focusSetSeconds else FocusMaxSeconds
                        repository?.write("focus",focusId,org.json.JSONObject().put("id",focusId).put("kind","focus").put("subject","Focus").put("startedAt",focusStartedAt).put("duration",focusSetSeconds).put("limit",limit).put("end",focusStartedAt+limit*1000L).put("running",true).put("completed",false))
                        focusRunning = true
                    },
                )
                else -> HomeScreen(
                    onStartLearning = {
                        // Each visit starts a fresh flow at subject selection.
                        if (learningStore.read().has("session")) showLearning = true else { pageState.removeState(LearnPage); navigate(LearnPage) }
                    },
                    onHistory={navigate(6)},onInventory={navigate(7)},onNotebookBounds={notebookOrigin=it},
                    playEntrance = !homePresented,
                    onEntranceStarted = { homePresented = true },
                    modifier = pageModifier,
                )
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().layerBackdrop(pageBackdrop).background(PageColor),
        ) {
            AppBackground(Modifier.fillMaxSize().layerBackdrop(sceneryBackdrop))
            if (sliding) {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
                    val width=with(LocalDensity.current){maxWidth.toPx()}
                    Box(Modifier.fillMaxSize().graphicsLayer{translationX=travel.value*width*slideDirection}){Page(from){travel.value*slideDirection}}
                    Box(Modifier.fillMaxSize().graphicsLayer{translationX=(travel.value-1)*width*slideDirection}){Page(slideTo){(travel.value-1)*slideDirection}}
                    TouchBlocker(Modifier.fillMaxSize())
                }
            } else if (page != FocusPage) {
                if(shown==6)Page(0)
                Page(shown)
            } else if (focusFade.value < 1f) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - focusFade.value }) { Page(lastPage) }
                Box(Modifier.fillMaxSize().drawBehind { drawRect(FocusBlack.copy(alpha = focusFade.value)) })
            } else {
                Page(FocusPage)
            }
        }

        Box(Modifier.align(Alignment.TopStart)) {
            AppHeader(
                active = when(menuPage) { StorePage -> GlassMenuItem.Store; SettingsPage -> GlassMenuItem.Settings; else -> null },
                onMenuItemSelected = {
                    navigate(when (it) {
                        GlassMenuItem.Store -> StorePage
                        GlassMenuItem.Settings -> SettingsPage
                    })
                },
            )
            if (focusRunning || sliding) TouchBlocker(Modifier.matchParentSize())
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                ,
        ) {
            FlatBottomNavigation(selectedTab, { stack=emptyList(); menuPage = NoMenuPage; selectedTab = it },
                Modifier.graphicsLayer { alpha = 1f - spread.value })
            if (focusRunning || spread.value > 0f) TouchBlocker(Modifier.matchParentSize())
        }

        // During a session the dark surface spreads over the header, leaving only
        // the timer and, after five seconds, a quiet way out.
        val density = LocalDensity.current
        val headerBottom = with(density) {
            WindowInsets.safeDrawing.getTop(this) + HeaderHeight.toPx()
        }
        FocusSpread({ spread.value }, headerBottom, Modifier.fillMaxSize())
        FocusExit(
            visible = focusRunning,
            onExit = { exitFocus() },
            modifier = Modifier.align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 28.dp, top = 28.dp),
        )

        if (showLearning) androidx.compose.runtime.key(savedLearning.optJSONObject("session")?.optString("id")) { LearningExperience(learningStore) { showLearning = false; menuPage = NoMenuPage; selectedTab = 0 } }

    }
}

/** Swallows touches so covered or faded controls cannot be used. */
@Composable
private fun TouchBlocker(modifier: Modifier = Modifier) {
    Box(
        modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent().changes.forEach { it.consume() }
            }
        },
    )
}

/** The continuous darker frost below the header, shared by Profile and menu pages. */
@Composable
private fun FrostedPageBackground(backdrop: Backdrop, modifier: Modifier = Modifier) {
    Box(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(top = HeaderHeight).background(PageColor).then(modifier),
    )
}

/** The original SVG paths stay vector-sharp, scaled uniformly to cover every page. */
@Composable
internal fun AppBackground(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.blob_scene_haikei),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = 0.5f,
        modifier = modifier.background(PageColor).testTag("app_background"),
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun APlusScreenPreview() {
    APlusTheme { APlusScreen() }
}
