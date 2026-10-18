package com.example.a

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test

class PageTransitionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun digit(index: Int, value: Int): SemanticsNodeInteraction =
        compose.onNode(hasAnyAncestor(hasTestTag("focus_digit_$index")) and hasText("$value"), useUnmergedTree = true)

    @Test
    fun ordinaryPagesSwitchImmediately() {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("profile_page").assertIsDisplayed()
        compose.onNodeWithTag("home_page").assertDoesNotExist()
        compose.onNodeWithContentDescription("Home").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("home_page").assertIsDisplayed()
        compose.onNodeWithTag("profile_page").assertDoesNotExist()
        // Returning Home shows fully emerged cards rather than replaying the entrance.
        compose.onNodeWithTag("start_learning").assertIsDisplayed()
    }

    @Test
    fun focusFadesInFromTheCurrentPageThenTheTimerPopsUp() {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Focus / Timer").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("home_page").assertExists()
        compose.onNodeWithTag("focus_page").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("home_page").assertDoesNotExist()
        compose.onNodeWithTag("focus_page").assertIsDisplayed()
        // The timer pops up on its own; its controls wait for it to settle.
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("focus_timer").assertIsDisplayed()
        compose.onNodeWithTag("focus_up_0").assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(1500)
        digit(0, 0).assertExists()
        compose.onNodeWithTag("focus_up_0").assertIsEnabled()
        // A session needs a time above 00:00.
        compose.onNodeWithTag("focus_start").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Focus").assertIsDisplayed()
        compose.onNodeWithTag("focus_up_3").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("focus_start").assertIsEnabled()
    }

    @Test
    fun focusSessionRunsImmersivelyAndExitRestoresTheSetup() {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Focus / Timer").performClick()
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithTag("focus_up_2").performClick()
        compose.mainClock.advanceTimeBy(400)
        digit(2, 1).assertExists()
        compose.onNodeWithTag("focus_down_3").performClick()
        compose.mainClock.advanceTimeBy(400)
        digit(3, 9).assertExists()
        compose.onNodeWithTag("focus_up_3").performClick()
        compose.mainClock.advanceTimeBy(400)

        compose.onNodeWithTag("focus_start").performClick()
        compose.mainClock.advanceTimeBy(1100)
        compose.onNodeWithTag("focus_start").assertIsNotEnabled()
        compose.onNodeWithTag("focus_exit").assertDoesNotExist()
        // Covered header and faded navigation ignore touches during the session.
        compose.onNodeWithContentDescription("Home").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("focus_page").assertIsDisplayed()

        compose.mainClock.advanceTimeBy(5100)
        digit(2, 0).assertExists()
        digit(3, 5).assertExists()
        compose.onNodeWithTag("focus_exit").assertExists().performClick()
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithTag("focus_start").assertIsEnabled()
        compose.onNodeWithTag("focus_exit").assertDoesNotExist()
        digit(2, 1).assertExists()
        digit(3, 0).assertExists()
        compose.onNodeWithContentDescription("Home").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("home_page").assertIsDisplayed()
    }

    @Test
    fun countdownEndsTheSessionByItself() {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Focus / Timer").performClick()
        compose.mainClock.advanceTimeBy(2000)
        repeat(3) {
            compose.onNodeWithTag("focus_up_3").performClick()
            compose.mainClock.advanceTimeBy(300)
        }
        compose.onNodeWithTag("focus_start").performClick()
        compose.mainClock.advanceTimeBy(1100 + 3000 + 1500)
        compose.onNodeWithTag("focus_start").assertIsEnabled()
        digit(3, 3).assertExists()
    }

    @Test
    fun menuOpensStoreAndSettingsAndBackReturnsToTheTab() {
        compose.onNodeWithTag("glass_menu_toggle").performClick()
        compose.onNodeWithText("Store").performClick()
        compose.onNodeWithTag("store_page").assertIsDisplayed()
        compose.onNodeWithText("Coming soon").assertIsDisplayed()
        compose.onNodeWithTag("home_page").assertDoesNotExist()
        compose.onNodeWithText("Settings").assertDoesNotExist() // Menu collapsed again.
        compose.onNodeWithTag("menu_page_back").performClick()
        compose.onNodeWithTag("home_page").assertIsDisplayed()

        compose.onNodeWithTag("glass_menu_toggle").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag("settings_page").assertIsDisplayed()
        compose.onNodeWithText("Version").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithTag("home_page").assertIsDisplayed()

        // A tab tap also leaves a menu page, landing on that tab.
        compose.onNodeWithTag("glass_menu_toggle").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithTag("profile_page").assertIsDisplayed()
        compose.onNodeWithContentDescription("Profile").assertIsSelected()
        compose.onNodeWithTag("settings_page").assertDoesNotExist()
    }
}
