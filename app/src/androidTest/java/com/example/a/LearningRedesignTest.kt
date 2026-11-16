package com.example.a

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.example.a.ui.*
import org.junit.*
import org.junit.Assert.*
import org.json.JSONObject
import java.time.LocalDate

class LearningRedesignTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var store:LearningStore
    private var original:String?=null
    @Before fun setup(){val p=compose.activity.getSharedPreferences("learning_progress",0);original=p.getString("state",null);p.edit().clear().commit();store=LearningStore(compose.activity)}
    @After fun restore(){compose.activity.getSharedPreferences("learning_progress",0).edit().clear().apply{original?.let{putString("state",it)}}.commit()}
    private fun quiz(){store.begin(1);val s=store.read();s.getJSONObject("session").put("stage","quiz");compose.activity.getSharedPreferences("learning_progress",0).edit().putString("state",s.toString()).commit()}
    private fun recreateLearning() {
        compose.activityRule.scenario.recreate()
        // This legacy-flow fixture bypasses auth; the account suite covers the real startup gate.
        store=LearningStore(compose.activity)
        compose.runOnUiThread { compose.activity.setContent { APlusTheme { LearningExperience(store){} } } }
    }
    @Test fun rewardsAreAtomicIdempotentAndConsecutiveDaily(){
        val day=LocalDate.of(2026,11,15)
        fun complete(date:LocalDate){quiz();HistoryQuestions.forEach{store.answer(it.correct);store.answer((it.correct+1)%4);store.next(date)}}
        complete(day);assertEquals(1000,store.read().getInt("xp"));assertEquals(1,store.read().getInt("streak"));repeat(4){store.next(day)};assertEquals(1000,store.read().getInt("xp"))
        complete(day);assertEquals(2000,store.read().getInt("xp"));assertEquals(1,store.read().getInt("streak"));assertFalse(store.read().getJSONObject("session").getBoolean("streakAdded"))
        complete(day.plusDays(1));assertEquals(2,store.read().getInt("streak"))
        complete(day.plusDays(3));assertEquals(1,store.read().getInt("streak"));assertEquals(4,store.read().getJSONArray("completed").length())
        assertEquals(4000,LearningStore(compose.activity).read().getInt("xp"))
    }
    @Test fun timerAnalysisQuizResultsWorksEndToEnd(){
        store.begin(120)
        compose.runOnUiThread{compose.activity.setContent{APlusTheme{LearningExperience(store){}}}}
        compose.waitUntil(5000){compose.onAllNodesWithTag("session_finish").fetchSemanticsNodes().isNotEmpty()}
        assertTrue(store.read().getJSONObject("session").getLong("end") > System.currentTimeMillis() + 60000)
        compose.onNodeWithTag("session_finish").assertIsEnabled()
        compose.onNodeWithTag("session_finish").performClick()
        compose.onNodeWithText("AI kép elemzés").assertIsDisplayed()
        val end=store.read().getJSONObject("session").getLong("analysisEnd")
        assertTrue(end-System.currentTimeMillis()>24000)
        recreateLearning()
        compose.waitUntil(32000){store.read().getJSONObject("session").getString("stage")=="quiz"}
        HistoryQuestions.forEachIndexed{index,q ->
            compose.onNodeWithTag("answer_${if(index==0)(q.correct+1)%4 else q.correct}").performClick()
            if(index<9)compose.onNodeWithTag("quiz_next").performScrollTo().performClick()
        }
        compose.waitUntil(6000){store.read().getJSONObject("session").getString("stage")=="results"}
        compose.onRoot().captureToImage().asAndroidBitmap().let { image ->
            File(compose.activity.externalCacheDir,"nov-results.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        assertEquals(900,store.read().getInt("xp"));assertEquals(900,store.read().getInt("points"))
        recreateLearning();assertEquals(900,store.read().getInt("xp"))
        compose.onNodeWithText("9 / 10 helyes válasz\n1 hibás válasz · 90% pontosság").assertExists()
    }
    @Test fun pausedSessionCanFinishEarlyWithoutRestartingAnalysis() {
        store.begin(300)
        compose.runOnUiThread{compose.activity.setContent{APlusTheme{LearningExperience(store){}}}}
        compose.onNodeWithTag("session_stop").performClick()
        assertTrue(store.read().getJSONObject("session").getBoolean("paused"))
        compose.onNodeWithTag("session_finish").assertIsEnabled().performClick()
        compose.onNodeWithText("AI kép elemzés").assertIsDisplayed()
        val session = store.read().getJSONObject("session")
        assertEquals("analysis", session.getString("stage"))
        assertTrue(session.getInt("remaining") > 0)
        store.finish()
        assertEquals(session.getLong("analysisEnd"), store.read().getJSONObject("session").getLong("analysisEnd"))
    }
    @Test fun flatNavigationAndSubjectGridRetainDestinations(){
        compose.runOnUiThread{compose.activity.setContent{APlusTheme{APlusScreen()}}}
        compose.onNodeWithTag("top_store").performClick();compose.onNodeWithTag("top_settings").performClick()
        compose.onNodeWithTag("tab_0").performClick();compose.onNodeWithTag("start_learning").performClick()
        compose.onNodeWithTag("learn_previous_sessions").assertDoesNotExist()
        compose.onNodeWithTag("learn_back").performClick()
        compose.onNodeWithTag("home_page").assertIsDisplayed()
        compose.onNodeWithText("Tanulás indítása").assertExists()
        compose.onNodeWithText("Ma is egy lépéssel előrébb").assertDoesNotExist()
        compose.onNodeWithTag("previous_sessions").performClick()
        compose.onNodeWithText("Még nincs befejezett tanulásod. Az első alkalom után itt találod az eredményeidet.").assertIsDisplayed()
        compose.onNodeWithTag("notebook_back").performClick()
        compose.onNodeWithTag("start_learning").performClick()
        compose.onRoot().captureToImage().asAndroidBitmap().let { image ->
            File(compose.activity.externalCacheDir,"restored-subjects.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        val math=compose.onNodeWithTag("learn_subject_math").fetchSemanticsNode().boundsInRoot
        val literature=compose.onNodeWithTag("learn_subject_literature").fetchSemanticsNode().boundsInRoot
        val english=compose.onNodeWithTag("learn_subject_english").fetchSemanticsNode().boundsInRoot
        assertTrue(math.right<literature.left);assertEquals(math.top,literature.top,1f);assertTrue(english.top>math.bottom)
        compose.onNodeWithTag("learn_subject_math").performClick();compose.onNodeWithTag("learn_next").assertIsEnabled()
        compose.onNodeWithTag("learn_up_3").performClick();compose.onNodeWithTag("learn_next").performClick()
        compose.onNodeWithTag("learn_start").assertIsNotEnabled();compose.onNodeWithTag("learn_paste").performClick()
        compose.onNodeWithTag("learn_paste_field").performTextInput("Történelem jegyzet")
        compose.onNodeWithTag("learn_paste_save").performClick();compose.onNodeWithTag("learn_start").performClick()
        compose.onNodeWithTag("learn_session").assertIsDisplayed()
    }

    @Test fun automaticBreakPausesStudyTimeAndResumesWithoutCountingTheBreak(){
        store.begin(3600);val state=store.read();state.getJSONObject("session").put("autoBreaks",true)
        compose.activity.getSharedPreferences("learning_progress",0).edit().putString("state",state.toString()).commit()
        val end=state.getJSONObject("session").getLong("end");val at=end-2100_000
        store.autoBreak(at);val paused=store.read().getJSONObject("session");assertTrue(paused.getBoolean("paused"));assertEquals(2100,paused.getInt("remaining"));assertEquals(1,paused.getInt("breakCount"))
        store.autoBreak(at+300_000);val resumed=store.read().getJSONObject("session");assertFalse(resumed.getBoolean("paused"));assertEquals(at+300_000+2100_000,resumed.getLong("end"))
    }
}
