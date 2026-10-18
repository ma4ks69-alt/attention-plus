package com.example.a

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.a.ui.APlusTheme
import com.example.a.ui.ExpandableGlassMenu
import com.example.a.ui.GlassBottomNavigation
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** A diagnostic pattern distinguishes real content warping from alpha/edge lighting. */
class GlassRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun movingLensRefractsIconPixelsWithoutChangingSelection() {
        compose.runOnUiThread {
            compose.activity.setContent {
                APlusTheme {
                    val backdrop = rememberLayerBackdrop()
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color(0xFF262626)))
                        GlassBottomNavigation(0, {}, backdrop, Modifier.align(Alignment.Center).width(340.dp))
                    }
                }
            }
        }
        val density = compose.activity.resources.displayMetrics.density
        val nav = compose.onNodeWithTag("glass_navigation")
        val navBounds = nav.fetchSemanticsNode().boundsInRoot
        val home = compose.onNodeWithContentDescription("Home").fetchSemanticsNode().boundsInRoot.center
        val focus = compose.onNodeWithContentDescription("Focus / Timer").fetchSemanticsNode().boundsInRoot.center
        val restingImage = compose.onRoot().captureToImage()
        val resting = restingImage.toPixelMap()
        nav.performTouchInput {
            down(Offset(home.x - navBounds.left, navBounds.height / 2f))
            advanceEventTime(220)
            moveBy(Offset((focus.x - home.x) * 0.38f, 0f))
        }
        compose.onNodeWithContentDescription("Home").assertIsSelected()
        val movingImage = compose.onRoot().captureToImage()
        val moving = movingImage.toPixelMap()
        File(compose.activity.externalCacheDir, "icons-under-lens-resting.png").outputStream().use {
            restingImage.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        File(compose.activity.externalCacheDir, "icons-under-lens-moving.png").outputStream().use {
            movingImage.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        fun isIcon(pixel: Color) = pixel.blue > 0.55f && pixel.blue > pixel.red * 2f && pixel.blue > pixel.green * 1.3f
        var changedIconPixels = 0
        for (x in (home.x - 22f * density).toInt()..(home.x + 22f * density).toInt()) {
            for (y in (home.y - 20f * density).toInt()..(home.y + 20f * density).toInt()) {
                if (isIcon(resting[x, y]) != isIcon(moving[x, y])) changedIconPixels++
            }
        }
        assertTrue("Actual icon silhouette must change under the moving lens; flat foreground icons would stay identical ($changedIconPixels changed pixels)", changedIconPixels >= 20)
        nav.performTouchInput { up() }
    }

    @Test
    fun productionGlassWarpsRecognizableContentInsteadOfCoveringIt() {
        val showGlass = mutableStateOf(false)
        val density = compose.activity.resources.displayMetrics.density
        val stripe = 16f * density
        compose.runOnUiThread {
            compose.activity.setContent {
                APlusTheme {
                    val backdrop = rememberLayerBackdrop()
                    Box(Modifier.fillMaxSize()) {
                        Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                            var x = 0f
                            var index = 0
                            while (x < size.width) {
                                drawRect(if (index % 2 == 0) Color(0xFF606060) else Color(0xFFD0D0D0), Offset(x, 0f), Size(stripe, size.height))
                                x += stripe
                                index++
                            }
                        }
                        if (showGlass.value) {
                            ExpandableGlassMenu(backdrop, Modifier.padding(start = 24.dp, top = 120.dp).width(264.dp))
                            GlassBottomNavigation(1, {}, backdrop, Modifier.align(Alignment.Center).width(340.dp))
                        }
                    }
                }
            }
        }
        val plainImage = compose.onRoot().captureToImage()
        val plain = plainImage.toPixelMap()
        compose.runOnIdle { showGlass.value = true }
        val lensBounds = compose.onNodeWithTag("selection_lens").fetchSemanticsNode().boundsInRoot
        val menuBounds = compose.onNodeWithTag("expandable_glass").fetchSemanticsNode().boundsInRoot
        val rendered = compose.onRoot().captureToImage()
        val glass = rendered.toPixelMap()
        File(compose.activity.externalCacheDir, "clear-refraction-test.png").outputStream().use {
            rendered.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        File(compose.activity.externalCacheDir, "clear-refraction-baseline.png").outputStream().use {
            plainImage.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        fun assertWarp(bounds: Rect, label: String, pixels: PixelMap = glass) {
            var displaced = 0
            var samples = 0
            var maximumDifference = 0f
            // Ignore rims, icons, and stripe transitions. Blur or a 2–4% tint alone
            // cannot replace a flat stripe's interior with the neighboring stripe.
            val leftBand = (bounds.left + 4f * density).toInt()..(bounds.left + 20f * density).toInt()
            val rightBand = (bounds.right - 20f * density).toInt()..(bounds.right - 4f * density).toInt()
            for (x in (leftBand step 2) + (rightBand step 2)) {
                val phase = x % stripe
                if (phase < 4f * density || phase > stripe - 4f * density) continue
                for (y in (bounds.center.y - 22f * density).toInt()..(bounds.center.y + 22f * density).toInt() step 3) {
                    val radius = bounds.height / 2f
                    val cornerCenter = if (x < bounds.center.x) bounds.left + radius else bounds.right - radius
                    val dx = x - cornerCenter
                    val dy = y - bounds.center.y
                    // Measure inside the curved lens border, where the SDF shader
                    // actually refracts. A central flat stripe can remain unchanged.
                    if (radius - sqrt(dx * dx + dy * dy) < 3f * density) continue
                    samples++
                    val difference = abs(pixels[x, y].red - plain[x, y].red)
                    maximumDifference = maxOf(maximumDifference, difference)
                    if (difference > 0.22f) displaced++
                }
            }
            assertTrue("$label must visibly warp underlying stripe interiors ($displaced/$samples samples; max difference $maximumDifference; bounds $bounds)", displaced >= 8)
        }
        assertWarp(lensBounds, "Selected glass lens")
        assertWarp(menuBounds, "Circular glass menu")

        val nav = compose.onNodeWithTag("glass_navigation")
        val navBounds = nav.fetchSemanticsNode().boundsInRoot
        nav.performTouchInput {
            down(center)
            advanceEventTime(220)
            moveBy(Offset(navBounds.width / 3f * 0.28f, 0f))
        }
        val movingLens = compose.onNodeWithTag("selection_lens").fetchSemanticsNode().boundsInRoot
        assertWarp(movingLens, "Held and deformed glass lens", compose.onRoot().captureToImage().toPixelMap())
        nav.performTouchInput { up() }

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("glass_menu_toggle").performClick()
        compose.mainClock.advanceTimeBy(96)
        val growing = compose.onNodeWithTag("expandable_glass").fetchSemanticsNode().boundsInRoot
        assertTrue("Opening must preserve continuous intermediate geometry", growing.width > menuBounds.width && growing.width < 264f * density)
        assertWarp(growing, "Growing menu lens", compose.onRoot().captureToImage().toPixelMap())
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("glass_menu_toggle").performClick()
        compose.mainClock.advanceTimeBy(96)
        val retracting = compose.onNodeWithTag("expandable_glass").fetchSemanticsNode().boundsInRoot
        assertWarp(retracting, "Retracting menu lens", compose.onRoot().captureToImage().toPixelMap())
        compose.mainClock.advanceTimeBy(700)
    }
}
