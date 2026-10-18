package com.example.a

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import com.example.a.ui.APlusTheme
import com.example.a.ui.APlusScreen
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.kyant.backdrop.isRuntimeShaderSupported
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class GlassNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun threeTabsMoveOneLensThroughIntermediatePositions() {
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertCountEquals(3)
        compose.onNodeWithContentDescription("Home").assertIsSelected()
        compose.onNodeWithContentDescription("Focus / Timer").assertIsNotSelected()
        compose.onNodeWithContentDescription("Profile").assertIsNotSelected()
        compose.mainClock.autoAdvance = false

        val start = lensCenterX()
        val target = compose.onNodeWithContentDescription("Profile")
            .fetchSemanticsNode().boundsInRoot.center.x
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.mainClock.advanceTimeBy(96)
        val duringSlide = lensCenterX()
        assertTrue("Lens must physically travel between tabs", duringSlide > start)
        assertTrue("Lens must not jump straight to the destination", duringSlide < target)
        compose.onAllNodesWithTag("selection_lens").assertCountEquals(1)

        compose.mainClock.advanceTimeBy(1200)
        assertEquals(target, lensCenterX(), 2f)
        compose.onNodeWithContentDescription("Profile").assertIsSelected()

        // Interrupt a return slide with another selection and check the final location.
        compose.onNodeWithContentDescription("Home").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithContentDescription("Focus / Timer").performClick()
        compose.mainClock.advanceTimeBy(1200)
        val focusCenter = compose.onNodeWithContentDescription("Focus / Timer")
            .fetchSemanticsNode().boundsInRoot.center.x
        assertEquals(focusCenter, lensCenterX(), 2f)
        compose.onNodeWithContentDescription("Focus / Timer").assertIsSelected()
        compose.onAllNodesWithTag("selection_lens").assertCountEquals(1)
    }

    @Test
    fun selectionSurvivesActivityRecreation() {
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Profile").assertIsSelected()
        val target = compose.onNodeWithContentDescription("Profile")
            .fetchSemanticsNode().boundsInRoot.center.x
        assertEquals(target, lensCenterX(), 2f)
    }

    @Test
    fun menuGrowsFromCircleAndRetractsWithoutMovingItsAnchor() {
        val circle = menuBounds()
        assertEquals(circle.height, circle.width, 1f)
        compose.onNodeWithText("Store").assertDoesNotExist()
        compose.onNodeWithText("A+").assertDoesNotExist()
        val density = compose.activity.resources.displayMetrics.density
        var safeTop = 0
        compose.runOnIdle { safeTop = compose.activity.window.decorView.rootWindowInsets.getInsets(android.view.WindowInsets.Type.systemBars()).top }
        assertTrue("Menu must respect the status bar", circle.top >= safeTop)
        assertTrue("Circle must remain in the first safe header row", circle.top < safeTop + 24f * density)
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag("glass_menu_toggle").performClick()
        compose.mainClock.advanceTimeBy(96)
        val opening = menuBounds()
        assertEquals(circle.left, opening.left, 1f)
        assertTrue("Menu must grow continuously out of its circle", opening.width > circle.width)
        compose.mainClock.advanceTimeBy(800)
        val open = menuBounds()
        assertTrue("Opening must pass through intermediate widths", opening.width < open.width)
        compose.onNodeWithText("Store").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Settings").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("glass_menu_toggle")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        compose.onAllNodesWithTag("expandable_glass").assertCountEquals(1)

        compose.onNodeWithTag("glass_menu_toggle").performClick()
        compose.mainClock.advanceTimeBy(96)
        val closing = menuBounds()
        assertTrue("Menu must retract toward the circle", closing.width < open.width)
        assertTrue("Retraction must not jump to the circle", closing.width > circle.width)
        assertEquals(circle.left, closing.left, 1f)
        compose.mainClock.advanceTimeBy(800)
        assertEquals(circle.width, menuBounds().width, 1f)
        compose.onNodeWithText("Store").assertDoesNotExist()
        compose.onNodeWithTag("glass_menu_toggle")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
    }

    @Test
    fun interruptedMenuAndNavigationAnimationsSettleTogether() {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("glass_menu_toggle").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithTag("glass_menu_toggle").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("glass_menu_toggle").performClick()
        compose.onNodeWithContentDescription("Focus / Timer").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Store").assertIsDisplayed().assertIsEnabled()
        val target = compose.onNodeWithContentDescription("Focus / Timer")
            .fetchSemanticsNode().boundsInRoot.center.x
        assertEquals(target, lensCenterX(), 2f)
        compose.onAllNodesWithTag("selection_lens").assertCountEquals(1)
        compose.onAllNodesWithTag("expandable_glass").assertCountEquals(1)
    }

    @Test
    fun expandedMenuSurvivesActivityRecreation() {
        compose.onNodeWithTag("glass_menu_toggle").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("glass_menu_toggle")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        compose.onNodeWithText("Store").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun pressingCompressesTheGlassAndReleaseRestoresItsSize() {
        assertTrue("Supported devices must use the AGSL path", isRuntimeShaderSupported())
        compose.runOnIdle {
            assertTrue("Glass must render through the hardware pipeline", compose.activity.window.decorView.isHardwareAccelerated)
        }
        val toggle = compose.onNodeWithTag("glass_menu_toggle")
        val resting = toggle.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        toggle.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        val compressed = toggle.fetchSemanticsNode().boundsInRoot
        assertTrue("The actual glass object must compress on press", compressed.height < resting.height)
        toggle.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1000)
        val released = toggle.fetchSemanticsNode().boundsInRoot
        assertEquals(resting.height, released.height, 1f)
        assertEquals(resting.width, released.width, 1f)
        compose.onNodeWithText("Store").assertIsDisplayed()
    }

    @Test
    fun longPressDragIsContinuousClampedAndSelectsOnlyOnRelease() {
        val nav = compose.onNodeWithTag("glass_navigation")
        val bounds = nav.fetchSemanticsNode().boundsInRoot
        val home = compose.onNodeWithContentDescription("Home").fetchSemanticsNode().boundsInRoot.center.x
        val focus = compose.onNodeWithContentDescription("Focus / Timer").fetchSemanticsNode().boundsInRoot.center.x
        val slot = focus - home
        nav.performTouchInput {
            down(Offset(home - bounds.left, bounds.height / 2f))
            advanceEventTime(700)
            moveBy(Offset(slot * 0.37f, 0f))
        }
        compose.onNodeWithTag("selection_lens")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Dragging"))
        assertEquals(home + slot * 0.37f, lensCenterX(), 3f)
        compose.onNodeWithContentDescription("Home").assertIsSelected()

        // Move outside each side of the bar while keeping the same pointer down.
        nav.performTouchInput { advanceEventTime(32); moveTo(Offset(bounds.width + 20f, bounds.height / 2f)) }
        assertLensInsideNavigation()
        compose.onNodeWithContentDescription("Home").assertIsSelected()
        nav.performTouchInput { advanceEventTime(32); moveTo(Offset(-20f, bounds.height / 2f)) }
        assertLensInsideNavigation()

        nav.performTouchInput {
            advanceEventTime(32)
            moveTo(Offset(focus - bounds.left + slot * 0.3f, bounds.height / 2f))
        }
        val drop = lensCenterX()
        compose.mainClock.autoAdvance = false
        nav.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(32)
        assertTrue("Release must spring from the finger instead of jumping to another position", abs(lensCenterX() - drop) < slot * 0.2f)
        compose.mainClock.advanceTimeBy(900)
        assertEquals(focus, lensCenterX(), 2f)
        compose.onNodeWithContentDescription("Focus / Timer").assertIsSelected()
        compose.onNodeWithTag("focus_page").assertIsDisplayed()
        assertLensInsideNavigation()
    }

    @Test
    fun shortHoldReceivesMovementBeforeSystemLongPressTimeout() {
        val nav = compose.onNodeWithTag("glass_navigation")
        val bounds = nav.fetchSemanticsNode().boundsInRoot
        val home = lensCenterX()
        val focus = compose.onNodeWithContentDescription("Focus / Timer").fetchSemanticsNode().boundsInRoot.center.x
        val slot = focus - home
        nav.performTouchInput {
            down(Offset(home - bounds.left, bounds.height / 2f))
            advanceEventTime(220)
            moveBy(Offset(slot * 0.42f, 0f))
        }
        compose.onNodeWithTag("selection_lens")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Dragging"))
        assertEquals(home + slot * 0.42f, lensCenterX(), 3f)
        compose.onNodeWithContentDescription("Home").assertIsSelected()
        nav.performTouchInput {
            advanceEventTime(32)
            moveTo(Offset(focus - bounds.left + slot * 0.22f, bounds.height / 2f))
            up()
        }
        compose.onNodeWithContentDescription("Focus / Timer").assertIsSelected()
        assertEquals(focus, lensCenterX(), 2f)
    }

    @Test
    fun draggingToPersonOpensProfileAndReturningToSameTabStillSnaps() {
        val nav = compose.onNodeWithTag("glass_navigation")
        val bounds = nav.fetchSemanticsNode().boundsInRoot
        val home = lensCenterX()
        val profile = compose.onNodeWithContentDescription("Profile").fetchSemanticsNode().boundsInRoot.center.x
        nav.performTouchInput {
            down(Offset(home - bounds.left, bounds.height / 2f))
            advanceEventTime(700)
            moveTo(Offset(profile - bounds.left - 16f, bounds.height / 2f))
            up()
        }
        compose.onNodeWithContentDescription("Profile").assertIsSelected()
        compose.onNodeWithTag("profile_page").assertIsDisplayed()
        assertEquals(profile, lensCenterX(), 2f)
        nav.performTouchInput {
            down(Offset(profile - bounds.left, bounds.height / 2f))
            advanceEventTime(700)
            moveBy(Offset(-18f, 0f))
            up()
        }
        assertEquals(profile, lensCenterX(), 2f)
        assertLensInsideNavigation()
    }

    @Test
    fun profileLayoutAndSubjectSelectionSurviveNavigationAndRecreation() {
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithTag("profile_page").assertIsDisplayed()
        compose.onNodeWithContentDescription("Profile picture").assertIsDisplayed()
        compose.onNodeWithText("Username").assertIsDisplayed()
        compose.onNodeWithText("@handle").assertIsDisplayed()
        compose.onNodeWithText("All-time points").assertIsDisplayed()
        compose.onNodeWithText("Current streak").assertIsDisplayed()
        compose.onNodeWithTag("subject_selector").performClick()
        compose.onNodeWithTag("subject_option_2").performClick()
        compose.onNodeWithContentDescription("Daily points for Subject 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithTag("home_page").assertIsDisplayed()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithContentDescription("Daily points for Subject 2").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Daily points for Subject 2").assertIsDisplayed()
    }

    @Test
    fun quickPointerTapsDoNotEnterDragMode() {
        compose.onNodeWithContentDescription("Focus / Timer").performTouchInput { down(center); advanceEventTime(64); up() }
        compose.onNodeWithContentDescription("Focus / Timer").assertIsSelected()
        compose.onNodeWithTag("focus_page").assertIsDisplayed()
        compose.onNodeWithContentDescription("Profile").performTouchInput { down(center); advanceEventTime(64); up() }
        compose.onNodeWithContentDescription("Profile").assertIsSelected()
        compose.onNodeWithTag("profile_page").assertIsDisplayed()
        compose.onNodeWithTag("selection_lens")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "At rest"))
    }

    @Test
    fun backgroundHasHalfOpacitySvgAndNavigationUsesBlueAccent() {
        var primary = Color.Unspecified
        compose.runOnUiThread {
            compose.activity.setContent {
                APlusTheme {
                    val color = MaterialTheme.colorScheme.primary
                    SideEffect { primary = color }
                    APlusScreen()
                }
            }
        }
        compose.runOnIdle { assertEquals(0xFF1A8CFF.toInt(), primary.toArgb()) }
        val page = compose.onRoot().captureToImage().toPixelMap()
        // The black header covers the status-bar area; the new SVG remains below it.
        val corner = page[8, 8]
        assertEquals(0f, corner.red, 1f / 255f)
        assertEquals(0f, corner.green, 1f / 255f)
        assertEquals(0f, corner.blue, 1f / 255f)
        assertEquals(31f / 255f, page[8, page.height * 3 / 4].red, 1.5f / 255f)
        compose.onNodeWithText("A+").assertDoesNotExist()
        val icon = compose.onNodeWithContentDescription("Home").captureToImage().toPixelMap()
        var bluePixels = 0
        for (x in 0 until icon.width) for (y in 0 until icon.height) {
            val pixel = icon[x, y]
            if (pixel.blue > 0.6f && pixel.blue > pixel.red * 2f && pixel.blue > pixel.green * 1.3f) bluePixels++
        }
        assertTrue("Refracted selected icon must retain the blue accent", bluePixels > 20)
    }

    @Test
    fun profileUsesContinuousDarkerFrostWithoutEnclosingProfileOrSubjectFrames() {
        // Home shows the plain scenery at the left edge; Focus is intentionally dark.
        val scenery = compose.onRoot().captureToImage().toPixelMap()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithText("A+").assertDoesNotExist()
        val density = compose.activity.resources.displayMetrics.density
        val avatar = compose.onNodeWithContentDescription("Profile picture").fetchSemanticsNode().boundsInRoot
        val divider = compose.onNodeWithTag("header_divider").fetchSemanticsNode().boundsInRoot
        assertTrue("Profile content must begin below the dedicated header", avatar.top > divider.bottom)
        val subject = compose.onNodeWithTag("subject_summary").fetchSemanticsNode().boundsInRoot
        val graph = compose.onNodeWithTag("daily_points_graph").fetchSemanticsNode().boundsInRoot
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val frost = compose.onNodeWithTag("profile_frost").fetchSemanticsNode().boundsInRoot
        assertEquals(0f, frost.left, 1f)
        assertEquals(pixels.width.toFloat(), frost.right, 1f)
        assertEquals(divider.bottom, frost.top, 1f)
        assertEquals(pixels.height.toFloat(), frost.bottom, 1f)
        for (fraction in listOf(0.3f, 0.5f, 0.7f, 0.9f)) {
            val y = (pixels.height * fraction).roundToInt()
            assertTrue("Frost must darken the entire page, including its outer edges", pixels[8, y].red < scenery[8, y].red - 2f / 255f)
        }
        // The Subject summary shares the full-page frost, without a separate rim.
        for (x in (subject.left + 2f * density).toInt()..(subject.right - 2f * density).toInt() step 7) {
            assertEquals("No enclosing Subject glass or border", pixels[x, (subject.top - density).toInt()].red, pixels[x, (subject.top + density).toInt()].red, 2f / 255f)
        }
        assertTrue("Only the graph retains its intended frame", graph.top > subject.top + 40f * density)
    }

    @Test
    fun realAndroidPointerStreamControlsLensAfterAShortHold() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val nav = compose.onNodeWithTag("glass_navigation").fetchSemanticsNode().boundsInRoot
        val home = lensCenterX()
        val focus = compose.onNodeWithContentDescription("Focus / Timer").fetchSemanticsNode().boundsInRoot.center.x
        val slot = focus - home
        val y = nav.center.y
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, home)
        SystemClock.sleep(220)
        try {
            for (fraction in listOf(0.2f, 0.43f, 0.75f, 1.24f)) {
                send(MotionEvent.ACTION_MOVE, home + slot * fraction)
                compose.waitForIdle()
                compose.onNodeWithTag("selection_lens")
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Dragging"))
                assertEquals("Native pointer movement must reach the lens", home + slot * fraction, lensCenterX(), 4f)
                compose.onNodeWithContentDescription("Home").assertIsSelected()
                assertLensInsideNavigation()
            }
        } finally {
            send(MotionEvent.ACTION_UP, home + slot * 1.24f)
        }
        compose.onNodeWithContentDescription("Focus / Timer").assertIsSelected()
        compose.onNodeWithTag("focus_page").assertIsDisplayed()
        assertEquals(focus, lensCenterX(), 2f)
    }

    @Test
    fun grabbingAnInFlightLensStopsTheSpringWithoutJumping() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val nav = compose.onNodeWithTag("glass_navigation").fetchSemanticsNode().boundsInRoot
        val home = lensCenterX()
        val focus = compose.onNodeWithContentDescription("Focus / Timer").fetchSemanticsNode().boundsInRoot.center.x
        val slot = focus - home
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.mainClock.advanceTimeBy(64)
        val grabbed = lensCenterX()
        assertTrue(grabbed > home && grabbed < home + 2f * slot)
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, nav.center.y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, grabbed)
        SystemClock.sleep(220)
        compose.mainClock.advanceTimeBy(240)
        try {
            assertEquals("Hold must stop the existing travel at the grabbed position", grabbed, lensCenterX(), 4f)
            send(MotionEvent.ACTION_MOVE, grabbed + 0.2f * slot)
            compose.mainClock.advanceTimeByFrame()
            assertEquals("Finger takes over without returning to an old tab position", grabbed + 0.2f * slot, lensCenterX(), 4f)
            assertLensInsideNavigation()
        } finally {
            send(MotionEvent.ACTION_UP, grabbed + 0.2f * slot)
        }
        compose.mainClock.advanceTimeBy(1000)
        val nearest = ((grabbed + 0.2f * slot - home) / slot).roundToInt().coerceIn(0, 2)
        assertEquals(home + slot * nearest, lensCenterX(), 2f)
    }

    private fun assertLensInsideNavigation() {
        val bar = compose.onNodeWithTag("glass_navigation").fetchSemanticsNode().boundsInRoot
        val lens = compose.onNodeWithTag("selection_lens").fetchSemanticsNode().boundsInRoot
        assertTrue("Lens must remain inside the bar horizontally", lens.left >= bar.left && lens.right <= bar.right)
        assertTrue("Lens must remain inside the bar vertically", lens.top >= bar.top && lens.bottom <= bar.bottom)
    }

    private fun menuBounds() =
        compose.onNodeWithTag("expandable_glass").fetchSemanticsNode().boundsInRoot

    private fun lensCenterX(): Float =
        compose.onNodeWithTag("selection_lens").fetchSemanticsNode().boundsInRoot.center.x
}
