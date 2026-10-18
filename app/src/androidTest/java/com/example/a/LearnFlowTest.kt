package com.example.a

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test

class LearnFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun digit(prefix: String, index: Int, value: Int): SemanticsNodeInteraction =
        compose.onNode(hasAnyAncestor(hasTestTag("${prefix}_digit_$index")) and hasText("$value"), useUnmergedTree = true)

    private fun openTimeStep() {
        compose.onNodeWithTag("start_learning").performClick()
        compose.onNodeWithTag("learn_subject_biology").performClick()
        compose.onNodeWithText("Select Time").assertIsDisplayed()
    }

    @Test
    fun subjectPageOffersSixSubjectsAndAPreviousSessionsLink() {
        compose.onNodeWithTag("start_learning").performClick()
        listOf("math", "literature", "english", "biology", "science", "foreignlanguage").forEach {
            compose.onNodeWithTag("learn_subject_$it").assertIsDisplayed()
        }
        // The Previous Sessions page is not built yet, so the link stays here.
        compose.onNodeWithTag("learn_previous_sessions").assertIsDisplayed().performClick()
        compose.onNodeWithText("Select Subject").assertIsDisplayed()
    }

    @Test
    fun timeNeedsAValueAndBackReturnsToSubjects() {
        openTimeStep()
        compose.onNodeWithText("Biology").assertIsDisplayed()
        compose.onNodeWithTag("learn_next").assertIsNotEnabled()
        compose.onNodeWithTag("learn_up_1").performClick()
        digit("learn", 1, 1).assertExists()
        compose.onNodeWithTag("learn_next").assertIsEnabled()
        Espresso.pressBack()
        compose.onNodeWithText("Select Subject").assertIsDisplayed()
    }

    @Test
    fun notesUnlockStartAndTheSessionCountsDownOverEverything() {
        openTimeStep()
        compose.onNodeWithTag("learn_up_3").performClick()
        compose.onNodeWithTag("learn_up_3").performClick()
        compose.onNodeWithTag("learn_next").performClick()
        compose.onNodeWithText("Upload Your Notes").assertIsDisplayed()
        compose.onNodeWithTag("learn_start").assertIsNotEnabled()

        compose.onNodeWithTag("learn_add_photos").performClick()
        compose.onNodeWithTag("learn_choose_gallery").assertIsDisplayed()
        compose.onNodeWithTag("learn_take_photo").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithTag("learn_paste").performClick()
        compose.onNodeWithTag("learn_paste_field").performTextReplacement("Cells divide by mitosis")
        compose.onNodeWithTag("learn_paste_save").performClick()
        compose.onNodeWithTag("learn_pasted_text").assertIsDisplayed()
        compose.onNodeWithTag("learn_start").assertIsEnabled()

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("learn_start").performClick()
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("learn_session").assertIsDisplayed()
        digit("session", 3, 2).assertExists()
        compose.mainClock.advanceTimeBy(1050)
        digit("session", 3, 1).assertExists()

        // Stop holds the current time; the session covers the navigation.
        compose.onNodeWithTag("session_stop").performClick()
        compose.mainClock.advanceTimeBy(2500)
        digit("session", 3, 1).assertExists()
        compose.onNodeWithText("Resume").assertIsDisplayed()
        compose.onNodeWithTag("session_finish").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("learn_session").assertIsDisplayed()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("learn_session").assertIsDisplayed()

        Espresso.pressBack()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("learn_session").assertDoesNotExist()
        compose.onNodeWithText("Upload Your Notes").assertIsDisplayed()
    }
}
