package com.example.a.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
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
fun APlusScreen(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var menuPage by rememberSaveable { mutableIntStateOf(NoMenuPage) }
    val page = if (menuPage != NoMenuPage) menuPage else selectedTab
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
    var focusSetSeconds by rememberSaveable { mutableIntStateOf(0) }
    var focusRunning by rememberSaveable { mutableStateOf(false) }
    var focusTicking by rememberSaveable { mutableStateOf(false) }
    var focusElapsed by rememberSaveable { mutableIntStateOf(0) }
    val spread = remember { Animatable(if (focusRunning) 1f else 0f) }
    LaunchedEffect(focusRunning) {
        if (focusRunning) {
            spread.animateTo(1f, tween(FocusSpreadMillis, easing = FastOutSlowInEasing))
            focusTicking = true
        } else {
            focusTicking = false
            spread.animateTo(0f, tween(FocusSpreadMillis, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(focusTicking) {
        while (focusTicking) {
            delay(1000)
            focusElapsed++
            // A countdown ends itself; a timer started at 00:00 counts up to its limit.
            val limit = if (focusSetSeconds > 0) focusSetSeconds else FocusMaxSeconds
            if (focusElapsed >= limit) focusRunning = false
        }
    }
    val focusDisplay = if (focusSetSeconds > 0) {
        (focusSetSeconds - focusElapsed).coerceAtLeast(0)
    } else {
        focusElapsed.coerceAtMost(FocusMaxSeconds)
    }
    // Study session started from Start Learning: covers everything, counts down.
    var learnActive by rememberSaveable { mutableStateOf(false) }
    var learnTicking by rememberSaveable { mutableStateOf(false) }
    var learnPaused by rememberSaveable { mutableStateOf(false) }
    var learnRemaining by rememberSaveable { mutableIntStateOf(0) }
    val learnCover = remember { Animatable(if (learnActive) 1f else 0f) }
    LaunchedEffect(learnActive) {
        if (learnActive) {
            learnCover.animateTo(1f, tween(FocusFadeMillis, easing = LinearEasing))
            learnTicking = true
        } else {
            learnTicking = false
            learnCover.animateTo(0f, tween(FocusFadeMillis, easing = LinearEasing))
        }
    }
    LaunchedEffect(learnTicking, learnPaused) {
        while (learnTicking && !learnPaused && learnRemaining > 0) {
            delay(1000)
            learnRemaining--
        }
    }

    fun exitFocus() {
        focusRunning = false
        focusElapsed = 0
    }
    BackHandler(enabled = focusRunning) { exitFocus() }
    BackHandler(enabled = menuPage != NoMenuPage && !focusRunning) { menuPage = NoMenuPage }

    // Entering Focus fades the previous page out while the dark surface fades in;
    // every other change of page is immediate.
    var lastPage by rememberSaveable { mutableIntStateOf(if (page == FocusPage) 0 else page) }
    if (page != FocusPage) SideEffect { lastPage = page }
    val focusFade = remember { Animatable(if (page == FocusPage) 1f else 0f) }
    LaunchedEffect(page) {
        if (page == FocusPage) {
            if (focusFade.value < 1f) focusFade.animateTo(1f, tween(FocusFadeMillis, easing = LinearEasing))
        } else {
            focusFade.snapTo(0f)
        }
    }

    @Composable
    fun Page(target: Int) {
        pageState.SaveableStateProvider(target) {
            when (target) {
                StorePage -> {
                    FrostedPageBackground(sceneryBackdrop)
                    StoreScreen(onBack = { menuPage = NoMenuPage }, modifier = pageModifier)
                }
                LearnPage -> LearnScreen(
                    onStartSession = {
                        learnRemaining = it
                        learnPaused = false
                        learnActive = true
                    },
                    modifier = pageModifier,
                )
                SettingsPage -> {
                    FrostedPageBackground(sceneryBackdrop)
                    SettingsScreen(onBack = { menuPage = NoMenuPage }, modifier = pageModifier)
                }
                2 -> {
                    FrostedPageBackground(sceneryBackdrop, Modifier.testTag("profile_frost"))
                    ProfileScreen(modifier = pageModifier)
                }
                FocusPage -> FocusScreen(
                    setSeconds = focusSetSeconds,
                    displaySeconds = focusDisplay,
                    running = focusRunning,
                    onSetSecondsChange = { focusSetSeconds = it },
                    onStart = {
                        focusElapsed = 0
                        focusRunning = true
                    },
                )
                else -> HomeScreen(
                    onStartLearning = {
                        // Each visit starts a fresh flow at subject selection.
                        pageState.removeState(LearnPage)
                        menuPage = LearnPage
                    },
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
            if (page != FocusPage) {
                Page(page)
            } else if (focusFade.value < 1f) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - focusFade.value }) { Page(lastPage) }
                Box(Modifier.fillMaxSize().drawBehind { drawRect(FocusBlack.copy(alpha = focusFade.value)) })
            } else {
                Page(FocusPage)
            }
        }

        Box(Modifier.align(Alignment.TopStart)) {
            AppHeader(
                onMenuItemSelected = {
                    menuPage = when (it) {
                        GlassMenuItem.Store -> StorePage
                        GlassMenuItem.Settings -> SettingsPage
                    }
                },
            )
            if (focusRunning) TouchBlocker(Modifier.matchParentSize())
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp, vertical = 16.dp),
        ) {
            GlassBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = {
                    menuPage = NoMenuPage
                    selectedTab = it
                },
                backdrop = pageBackdrop,
                modifier = Modifier.graphicsLayer { alpha = 1f - spread.value },
            )
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
            visible = focusTicking && focusElapsed >= 5,
            onExit = { exitFocus() },
            modifier = Modifier.align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 28.dp, top = 28.dp),
        )

        // The study session fades in over the whole screen, header and bar included.
        if (learnCover.value > 0f || learnActive) {
            // Composed last, so Back ends the session before any page handles it.
            BackHandler(enabled = learnActive) { learnActive = false }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = learnCover.value }) {
                TouchBlocker(Modifier.fillMaxSize())
                LearnSession(
                    seconds = learnRemaining,
                    paused = learnPaused,
                    onStopToggle = { learnPaused = !learnPaused },
                    // Finishing a session is not built yet.
                    onFinish = {},
                )
            }
        }
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
            .padding(top = HeaderHeight).profileFrost(backdrop).then(modifier),
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
