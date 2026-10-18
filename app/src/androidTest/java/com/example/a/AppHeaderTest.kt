package com.example.a

import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.a.ui.APlusScreen
import com.example.a.ui.APlusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.max

class AppHeaderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun blackHeaderCentersLargeLogoKeepsCircleInsideAndUsesQuietDivider() {
        fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val header = bounds("app_header")
        val logo = bounds("header_logo")
        val circle = bounds("expandable_glass")
        val divider = bounds("header_divider")
        val density = compose.activity.resources.displayMetrics.density
        assertEquals(header.center.x, logo.center.x, 1f)
        assertTrue("Logo must be the main header focus", logo.width > 200f * density)
        assertTrue(circle.top > header.top && circle.bottom < divider.top)
        assertTrue("Collapsed circle must not cover the centered logo", circle.right < logo.left)
        assertEquals(header.width, divider.width, 1f)
        assertEquals(density, divider.height, 1f)
        assertTrue(bounds("start_learning").top > divider.bottom)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val headerBlack = pixels[8, (divider.top / 2f).toInt()]
        assertEquals(0f, headerBlack.red, 1f / 255f)
        assertEquals(0f, headerBlack.green, 1f / 255f)
        assertEquals(0f, headerBlack.blue, 1f / 255f)
        val line = pixels[8, divider.center.y.toInt()].red
        val page = pixels[8, pixels.height * 3 / 4].red
        assertTrue("Divider must only be slightly lighter than the dark background", line > page && line - page < 14f / 255f)
        val start = bounds("start_learning")
        val green = pixels[start.center.x.toInt(), (start.center.y - start.height * 0.3f).toInt()]
        assertTrue("Primary action must be distinctly green", green.green > green.red * 2f && green.green > green.blue * 1.5f)
    }

    @Test
    fun logoFadesContinuouslyWithMenuAndReversesFromItsCurrentOpacity() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { compose.activity.setContent { APlusTheme { APlusScreen() } } }
        compose.mainClock.advanceTimeBy(64)
        val logo = compose.onNodeWithTag("header_logo").fetchSemanticsNode().boundsInRoot
        // The outermost logo band sits beyond the expanded capsule on this device,
        // so actual rendered artwork pixels can verify fading without glass overlays.
        fun brightness(): Float {
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            var total = 0f
            var samples = 0
            for (x in (logo.right - logo.width * 0.1f).toInt() until logo.right.toInt()) {
                for (y in logo.top.toInt() until logo.bottom.toInt()) {
                    val c = pixels[x, y]
                    total += max(c.red, max(c.green, c.blue))
                    samples++
                }
            }
            return total / samples
        }
        fun width() = compose.onNodeWithTag("expandable_glass").fetchSemanticsNode().boundsInRoot.width
        val closed = brightness()
        val circle = width()
        assertTrue(closed > 0.05f)
        compose.onNodeWithTag("glass_menu_toggle").performClick()
        assertEquals("Opening must not instantly hide the artwork", closed, brightness(), 0.015f)
        compose.mainClock.advanceTimeBy(64)
        val opening = brightness()
        assertTrue("Opening must progressively dim the artwork: closed=$closed opening=$opening", opening > closed * 0.05f && opening < closed * 0.95f)
        assertTrue(width() > circle)
        compose.onNodeWithTag("glass_menu_toggle").performClick()
        // A semantic click renders another frame. Existing spring velocity first
        // carries the fade forward, then reverses; it must not snap to either end.
        val reversed = brightness()
        assertTrue("Interrupted close must retain opacity and its existing inertia: opening=$opening reversed=$reversed", reversed > opening * 0.3f && reversed <= opening + 0.005f)
        compose.mainClock.advanceTimeBy(600)
        assertEquals(closed, brightness(), 0.015f)
        assertEquals(circle, width(), 1f)
        compose.onNodeWithTag("glass_menu_toggle").performClick()
        compose.mainClock.advanceTimeBy(128)
        assertTrue("Logo must clear the opening menu quickly", brightness() < closed * 0.15f)
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("Logo fades before the slower capsule finishes expanding", width() < 264f * density - 2f)
        compose.mainClock.advanceTimeBy(600)
        assertTrue("Open menu must leave its logo region visually clear", brightness() < closed * 0.03f)
        compose.onNodeWithTag("glass_menu_toggle").performClick()
        compose.mainClock.advanceTimeBy(64)
        assertTrue("Closing must fade back progressively", brightness() > 0.01f && brightness() < closed * 0.95f)
        compose.mainClock.advanceTimeBy(600)
        assertEquals(closed, brightness(), 0.015f)
    }
}
