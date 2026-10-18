package com.example.a

import androidx.activity.compose.setContent
import com.example.a.ui.APlusScreen
import com.example.a.ui.APlusTheme
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.geometry.Rect
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.roundToInt

class HomeScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeDefaultsToFirstTabAndMatchesSketchHierarchy() {
        compose.onNodeWithContentDescription("Home").assertIsSelected()
        compose.onNodeWithTag("home_page").assertIsDisplayed()
        fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val home = compose.onNodeWithContentDescription("Home").fetchSemanticsNode().boundsInRoot
        val focus = compose.onNodeWithContentDescription("Focus / Timer").fetchSemanticsNode().boundsInRoot
        val profile = compose.onNodeWithContentDescription("Profile").fetchSemanticsNode().boundsInRoot
        assertTrue(home.center.x < focus.center.x && focus.center.x < profile.center.x)
        val names = listOf("start_learning", "previous_sessions", "daily_challenge", "inventory")
        val slots = names.map { bounds("home_slot_$it") }
        val cards = names.map { bounds(it) }
        val density = compose.activity.resources.displayMetrics.density
        assertTrue(slots[0].center.x < slots[1].center.x)
        assertEquals(slots[0].center.x, slots[2].center.x, 1f)
        assertEquals(slots[1].center.x, slots[3].center.x, 1f)
        assertTrue(slots[0].bottom < slots[2].top && slots[1].bottom < slots[3].top)
        slots.forEach {
            assertEquals(slots[0].width, it.width, 1f)
            assertEquals(slots[0].height, it.height, 1f)
            assertTrue("Cards must stay compact", it.width <= 192f * density + 1f)
            assertEquals(1.08f, it.height / it.width, 0.01f)
        }
        for (i in cards.indices) for (j in i + 1 until cards.size) {
            assertTrue("Tilted cards must stay clearly separated", !cards[i].overlaps(cards[j]))
        }
        val cap = bounds("graduation_cap")
        assertTrue(cap.top > cards.maxOf { it.bottom })
        assertTrue(cap.bottom < bounds("glass_navigation").top)
        val label = compose.onNodeWithText("Previous Sessions").fetchSemanticsNode().boundsInRoot
        val action = bounds("continue_session")
        assertTrue("Continue must occupy the right side of the row", action.center.x > label.center.x)
        compose.onNodeWithTag("continue_session").assertIsNotEnabled()
        compose.onNodeWithTag("glass_menu_toggle").assertIsDisplayed()
    }

    @Test
    fun startLearningOpensSubjectSelection() {
        compose.onNodeWithText("Start Learning").assertIsDisplayed()
        compose.onNodeWithTag("start_learning").performClick()
        compose.onNodeWithTag("learn_page").assertIsDisplayed()
        compose.onNodeWithText("Select Subject").assertIsDisplayed()
        compose.onNodeWithTag("focus_page").assertDoesNotExist()
    }

    @Test
    fun dailyChallengeFlipsItsTitlePageToRevealTheChallengeAndBack() {
        compose.onNodeWithText("Daily Challenge").assertIsDisplayed()
        compose.onNodeWithText("example challenge").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("daily_challenge").performClick()
        compose.mainClock.advanceTimeBy(160)
        // Mid-flip both pages are present: the title page lifts off the challenge.
        compose.onNodeWithText("Daily Challenge").assertExists()
        compose.onNodeWithText("example challenge").assertExists()
        compose.mainClock.advanceTimeBy(4000)
        compose.onNodeWithText("example challenge").assertIsDisplayed()
        compose.onNodeWithText("Daily Challenge").assertDoesNotExist()
        compose.onNodeWithTag("daily_challenge").performClick()
        compose.mainClock.advanceTimeBy(4000)
        compose.onNodeWithText("Daily Challenge").assertIsDisplayed()
        compose.onNodeWithText("example challenge").assertDoesNotExist()
    }

    @Test
    fun suppliedDarkBackgroundPersistsBelowTheBlackHeaderAcrossDestinations() {
        for (label in listOf("Home", "Focus / Timer", "Profile")) {
            compose.onNodeWithContentDescription(label).performClick()
            compose.onNodeWithTag("app_background").assertIsDisplayed()
            val image = compose.onRoot().captureToImage().toPixelMap()
            assertEquals(0f, image[8, 8].red, 1f / 255f)
            val middle = image[8, image.height * 3 / 4]
            // New SVG's #171717 over #262626 at half opacity; Profile adds dark frost;
            // Focus covers the page with #0F0F0F.
            val expected = when (label) { "Profile" -> 26f; "Focus / Timer" -> 15f; else -> 31f }
            assertEquals(expected / 255f, middle.red, 1.5f / 255f)
            assertEquals(middle.red, middle.green, 1f / 255f)
            assertEquals(middle.red, middle.blue, 1f / 255f)
        }
    }

    @Test
    fun faintInventorySlotsMoveRightAndLoopSeamlesslyInsideCard() {
        // Auto-advancing Compose tests cancel infinite animations to reach idle.
        // Start this composition with a manual clock so its loop really runs.
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent { APlusTheme { APlusScreen() } } }
        compose.mainClock.advanceTimeBy(1200)
        val density = compose.activity.resources.displayMetrics.density
        val slot = compose.onNodeWithTag("home_slot_inventory").fetchSemanticsNode().boundsInRoot
        fun visualBounds() = compose.onNodeWithTag("inventory").fetchSemanticsNode().boundsInRoot
        fun capture() = compose.onRoot().captureToImage().toPixelMap()
        val beforeBounds = visualBounds()
        val before = capture()
        compose.mainClock.advanceTimeBy(1000, ignoreFrameDuration = true)
        val afterBounds = visualBounds()
        val after = capture()
        // Sample in the actual card's local axes, accounting for its -7 degree
        // tilt and independently measured hover position in each rendered frame.
        val angle = Math.toRadians(-7.0)
        fun point(x: Float, y: Float, visual: Rect): Pair<Int, Int> {
            val dx = x - slot.width / 2f
            val dy = y - slot.height / 2f
            return Pair(
                (visual.center.x + dx * cos(angle) - dy * sin(angle)).roundToInt(),
                (visual.center.y + dx * sin(angle) + dy * cos(angle)).roundToInt(),
            )
        }
        // Sample rows inside the chest interior, clear of the lid, hasp, labels,
        // interior outline and corner brackets: static artwork around the slots.
        val left = (slot.width * 0.25f).roundToInt()
        val right = (slot.width * 0.75f).roundToInt()
        val expectedShift = (50f * density / 6f).roundToInt()
        val shifts = -expectedShift * 2..expectedShift * 2
        for (rowFraction in listOf(0.66f, 0.72f, 0.78f)) {
            val rowY = slot.height * rowFraction
            val best = shifts.minBy { shift ->
                var difference = 0f
                for (x in left..right) {
                    val p = point(x.toFloat(), rowY, beforeBounds)
                    val q = point((x + shift).toFloat(), rowY, afterBounds)
                    difference += abs(before[p.first, p.second].red - after[q.first, q.second].red)
                }
                difference
            }
            assertEquals("All inventory rows retain their rightward flow", expectedShift.toFloat(), best.toFloat(), 3f)
        }
        compose.mainClock.advanceTimeBy(5000, ignoreFrameDuration = true)
        val loopBounds = visualBounds()
        val loop = capture()
        var difference = 0f
        var samples = 0
        for (x in 4 until slot.width.toInt() - 4 step 3) {
            for (y in 4 until slot.height.toInt() - 4 step 3) {
                val p = point(x.toFloat(), y.toFloat(), beforeBounds)
                val q = point(x.toFloat(), y.toFloat(), loopBounds)
                difference += abs(before[p.first, p.second].red - loop[q.first, q.second].red)
                samples++
            }
        }
        assertTrue("Slots and hovering must both loop without a seam", difference / samples < 0.008f)
        val outsideX = maxOf(beforeBounds.right, afterBounds.right).toInt() + 8
        val outsideY = beforeBounds.center.y.toInt()
        assertEquals("Slots must remain clipped inside their tilted card", before[outsideX, outsideY].red, after[outsideX, outsideY].red, 0.005f)
        compose.onNodeWithText("Inventory").assertIsDisplayed()
        compose.onNodeWithText("No items yet").assertIsDisplayed()
    }
}
