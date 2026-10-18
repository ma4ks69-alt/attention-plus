package com.example.a

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.toPixelMap
import com.example.a.ui.APlusScreen
import com.example.a.ui.APlusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.geometry.Offset
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class HomeMotionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val tags = listOf("start_learning", "previous_sessions", "daily_challenge", "inventory")
    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun startWithManualClock() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent { APlusTheme { APlusScreen() } } }
    }

    @Test
    fun cardsEmergeSequentiallyFromTheUpsideDownCapThenHoverWithoutOverlap() {
        startWithManualClock()
        compose.mainClock.advanceTimeBy(32)
        val cap = bounds("graduation_cap")
        var first = bounds(tags[0])
        // Initial zero-scale semantics have an empty rectangle. Observe the very
        // first visible frame rather than treating that empty rectangle as (0, 0).
        repeat(4) {
            if (first.width == 0f) {
                compose.mainClock.advanceTimeBy(16)
                first = bounds(tags[0])
            }
        }
        assertTrue("First visible card must originate inside the cap opening: first=$first cap=$cap", first.center.y > cap.top && first.center.y < cap.bottom)
        assertTrue(first.width < bounds("home_slot_start_learning").width * 0.25f)
        compose.mainClock.advanceTimeBy(80)
        val stagger = tags.map { bounds(it).width }
        assertTrue("First card leads the second", stagger[0] > stagger[1])
        assertTrue("Second emerges before the lower row", stagger[1] > stagger[2])
        assertTrue(bounds(tags[0]).center.y < first.center.y)
        compose.mainClock.advanceTimeBy(64)
        assertTrue("Third emerges before the fourth", bounds(tags[2]).width > bounds(tags[3]).width)
        compose.mainClock.advanceTimeBy(1000)
        tags.forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        val settled = tags.map { bounds(it) }
        for (i in settled.indices) for (j in i + 1 until settled.size) assertTrue(!settled[i].overlaps(settled[j]))
        val capBefore = bounds("graduation_cap")
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        var topGold = 0
        var bottomGold = 0
        for (x in capBefore.left.toInt() until capBefore.right.toInt() step 2) {
            for (y in capBefore.top.toInt() until capBefore.bottom.toInt() step 2) {
                val c = pixels[x, y]
                if (c.red > 0.6f && c.green > 0.3f && c.blue < 0.3f) {
                    if (y < capBefore.center.y) topGold++ else bottomGold++
                }
            }
        }
        assertTrue("Rotated cap must put the supplied tassel above its brim", topGold > 50 && topGold > bottomGold * 2)
        compose.mainClock.advanceTimeBy(1500)
        assertTrue("Cap must gently bob", abs(bounds("graduation_cap").center.y - capBefore.center.y) > 1f)
        assertTrue("Each card must hover", tags.indices.all { abs(bounds(tags[it]).center.y - settled[it].center.y) > 0.5f })
        val hovered = tags.map { bounds(it) }
        for (i in hovered.indices) for (j in i + 1 until hovered.size) assertTrue(!hovered[i].overlaps(hovered[j]))
    }

    @Test
    fun returningHomeAndRecreatingActivityNeverReplayTheEntrance() {
        // Exercise the Activity's own content so saved-state keys remain identical
        // across recreation; replacing setContent creates a different call site.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1200)
        // Unclipped, transform-aware width: returning pages slide in from off-screen,
        // where clipped bounds would shrink, but an entrance replay changes scale.
        fun scaledWidth(tag: String): Float {
            val coordinates = compose.onNodeWithTag(tag).fetchSemanticsNode().layoutInfo.coordinates
            return (coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), 0f)) - coordinates.localToRoot(Offset.Zero)).getDistance()
        }
        val widths = tags.map { scaledWidth(it) }
        compose.onNodeWithContentDescription("Focus / Timer").performClick()
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithContentDescription("Home").performClick()
        compose.mainClock.advanceTimeBy(32)
        tags.indices.forEach { assertEquals("Returned cards must already be full size", widths[it], scaledWidth(tags[it]), 1f) }
        compose.mainClock.advanceTimeBy(96)
        tags.indices.forEach { assertEquals(widths[it], scaledWidth(tags[it]), 1f) }
        compose.activityRule.scenario.recreate()
        compose.mainClock.advanceTimeBy(32)
        tags.indices.forEach { assertEquals("Recreation must keep the session entrance flag", widths[it], scaledWidth(tags[it]), 1f) }
    }
}
