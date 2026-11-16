package com.example.a

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.a.auth.*
import com.example.a.data.*
import com.example.a.ui.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.io.File
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap

class FirebaseIntegrationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val auth get()=FirebaseAuth.getInstance()
    private val db get()=FirebaseFirestore.getInstance()
    private val context get()=compose.activity
    private var legacyOriginal:String?=null
    private var migrationOriginal:Map<String,*> = emptyMap<String,String>()
    @Before fun setup() {
        check(context.getSharedPreferences("developer",0).getBoolean("firebaseEmulators",false)) { "Only run this suite against local Firebase emulators." }
        AccountSession.close();auth.signOut()
        context.getSharedPreferences("device_preferences",0).edit().putBoolean("reducedMotion",false).commit()
        val prefs=context.getSharedPreferences("learning_progress",0);legacyOriginal=prefs.getString("state",null);prefs.edit().clear().commit()
        val migration=context.getSharedPreferences("legacy_migration",0);migrationOriginal=migration.all;migration.edit().clear().commit()
        compose.runOnUiThread { compose.activity.setContent { APlusTheme { } } };compose.waitForIdle()
    }
    @After fun restore() {
        context.getSharedPreferences("device_preferences",0).edit().putBoolean("reducedMotion",false).commit()
        AccountSession.close()
        if(InstrumentationRegistry.getArguments().getString("keepAccountForRestart")!="true") auth.signOut()
        context.getSharedPreferences("learning_progress",0).edit().clear().apply{legacyOriginal?.let{putString("state",it)}}.commit()
        context.getSharedPreferences("legacy_migration",0).edit().clear().apply{migrationOriginal.forEach{(k,v)->when(v){is String->putString(k,v);is Boolean->putBoolean(k,v)}}}.commit()
        runBlocking { db.enableNetwork().awaitResult() }
    }
    private suspend fun account(): AccountRepository {
        auth.createUserWithEmailAndPassword("android-${UUID.randomUUID()}@example.test","StrongPassword42!").awaitResult()
        AccountAuth(context).ensureProfile("2000-01-01")
        val repo=AccountSession.open(context,auth.currentUser!!.uid)
        withTimeout(20000){while(!repo.state.value.ready)delay(100)}
        return repo
    }
    @Test fun registrationUiAndFluidSuccessReachHomeAndRestoreAuth() {
        compose.runOnUiThread { compose.activity.setContent { APlusTheme { AccountGate() } } };compose.waitForIdle()
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        compose.onRoot().captureToImage().asAndroidBitmap().let{image->File(context.externalCacheDir,"firebase-login.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}}
        compose.onNodeWithTag("auth_switch").performScrollTo().performClick()
        compose.onNodeWithTag("auth_identity").performScrollTo().performTextInput("ui-${UUID.randomUUID()}@example.test")
        compose.onNodeWithTag("auth_password").performScrollTo().performTextInput("StrongPassword42!")
        compose.onNodeWithTag("auth_confirm").performScrollTo().performTextInput("StrongPassword42!")
        compose.onNodeWithTag("auth_birthday").performScrollTo().performTextInput("2000-01-01")
        compose.onNodeWithTag("auth_submit").performScrollTo().performClick()
        compose.waitUntil(30000){compose.onAllNodesWithTag("home_page").fetchSemanticsNodes().isNotEmpty()}
        // The cap floats continuously; scrolling to a moving layout target never settles.
        compose.onNodeWithTag("graduation_cap").assertExists()
        val uid=auth.currentUser!!.uid
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000){compose.onAllNodesWithTag("home_page").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(uid,auth.currentUser!!.uid)
        runBlocking { assertEquals("2000-01-01",db.document("users/$uid/profile/main").get(Source.SERVER).awaitResult().getString("birthday")) }
    }
    @Test fun signOutDuringGateStartupReturnsToLoginWithoutOpeningTheOldAccount() {
        runBlocking { account() };AccountSession.close()
        compose.runOnUiThread { compose.activity.setContent { APlusTheme {
            AccountGate()
            // Auth changes after composition, before its pending startup coroutine resumes.
            SideEffect { if(auth.currentUser!=null) auth.signOut() }
        } } }
        compose.waitUntil(10000){compose.onAllNodesWithTag("auth_screen").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("auth_screen").assertIsDisplayed()
        assertNull(AccountSession.repository)
    }
    @Test fun durableOfflineDraftReplayAndAccountIsolation()= runBlocking {
        val first=account();val uid=first.uid
        db.disableNetwork().awaitResult()
        first.write("drafts","learning",JSONObject().put("subject","Math").put("seconds",120).put("text","Offline preserved note"))
        first.write("preferences","app",JSONObject().put("focusSeconds",60))
        AccountSession.close()
        val restored=AccountSession.open(context,uid)
        assertEquals("Offline preserved note",restored.record("drafts","learning").getString("text"))
        db.enableNetwork().awaitResult();withTimeout(25000){restored.flush()}
        assertTrue(db.collection("users/$uid/changes").get(Source.SERVER).awaitResult().size()>=2)
        // Independent Firebase client acts as another device; immutable changes reach the first live listener.
        val otherApp=com.google.firebase.FirebaseApp.initializeApp(context,com.google.firebase.FirebaseApp.getInstance().options,"second-${UUID.randomUUID()}")!!
        val otherDb=FirebaseFirestore.getInstance(otherApp);otherDb.useEmulator(context.getSharedPreferences("developer",0).getString("emulatorHost","10.0.2.2")!!,8080)
        val otherAuth=FirebaseAuth.getInstance(otherApp);otherAuth.useEmulator(context.getSharedPreferences("developer",0).getString("emulatorHost","10.0.2.2")!!,9099)
        otherAuth.signInWithEmailAndPassword(auth.currentUser!!.email!!,"StrongPassword42!").awaitResult()
        val id=UUID.randomUUID().toString()
        otherDb.document("users/$uid/changes/$id").set(mapOf("id" to id,"bucket" to "preferences","entity" to "app","clock" to 100L,"patch" to mapOf("profileSubject" to "Math"))).awaitResult()
        withTimeout(15000){while(restored.record("preferences","app").optString("profileSubject")!="Math")delay(100)}
        otherDb.terminate().awaitResult();otherApp.delete()
        AccountSession.close();auth.signOut()
        val second=account()
        assertNotEquals(uid,second.uid);assertEquals(0,second.record("drafts","learning").length())
        val denied=runCatching { db.collection("users/$uid/changes").get(Source.SERVER).awaitResult() }.exceptionOrNull()
        assertNotNull(denied)
    }
    @Test fun legacyMigrationKeepsSourceAndOnlyImportsIntoOneAccount()= runBlocking {
        val original=JSONObject().put("xp",400).put("points",400).put("completed",org.json.JSONArray()).toString()
        context.getSharedPreferences("learning_progress",0).edit().putString("state",original).commit()
        val first=account();withTimeout(25000){first.flush()}
        val migration=context.getSharedPreferences("legacy_migration",0)
        assertTrue(migration.getBoolean("confirmed",false));assertEquals(original,context.getSharedPreferences("learning_progress",0).getString("state",null))
        assertEquals(original,db.document("users/${first.uid}/legacy/${migration.getString("id","")}").get(Source.SERVER).awaitResult().getString("json"))
        AccountSession.close();auth.signOut();val second=account();withTimeout(25000){second.flush()}
        assertEquals(0,db.collection("users/${second.uid}/legacy").get(Source.SERVER).awaitResult().size())
    }
    @Test fun quizCompletionGetsOneServerReceiptAndNoDoubleXp()=runBlocking {
        val repo=account();repo.write("drafts","learning",JSONObject().put("subject","Math").put("text","Saved study notes"))
        val store=LearningStore(context);store.begin(120);store.finish()
        val id=store.read().getJSONObject("session").getString("id")
        repo.write("sessions",id,JSONObject().put("analysisEnd",System.currentTimeMillis()-1));store.analysisDone()
        HistoryQuestions.forEach{store.answer(it.correct);store.next()}
        withTimeout(25000){repo.flush()}
        withTimeout(20000){while(!db.document("users/${repo.uid}/receipts/$id").get(Source.SERVER).awaitResult().exists())delay(150)}
        repo.flush();delay(250)
        assertEquals(1000,store.read().getInt("xp"))
        assertEquals(1000L,db.document("users/${repo.uid}/stats/summary").get(Source.SERVER).awaitResult().getLong("xp"))
        AccountSession.close();val reopened=AccountSession.open(context,repo.uid)
        withTimeout(10000){while(!reopened.state.value.ready)delay(100)}
        assertEquals(1000,reopened.learningState().getInt("xp"))
    }
    @Test fun profilePhotoAndFocusHistorySurviveRepositoryRestart()=runBlocking {
        val repo=account()
        val fixture=File(context.cacheDir,"firebase-photo-test.jpg")
        val bitmap=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888)
        fixture.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)};bitmap.recycle()
        val path=repo.importImage(android.net.Uri.fromFile(fixture));repo.updateProfile(name="Edited Profile",photo=path)
        val id=UUID.randomUUID().toString();val now=System.currentTimeMillis()
        val countUp=UUID.randomUUID().toString()
        repo.write("focus",countUp,JSONObject().put("id",countUp).put("kind","focus").put("subject","Focus").put("startedAt",now).put("duration",0).put("limit",5999).put("end",now+5999000L).put("completed",false).put("running",true))
        repo.write("focus",id,JSONObject().put("id",id).put("kind","focus").put("subject","Focus").put("startedAt",now-5000).put("finishedAt",now).put("duration",5).put("studiedSeconds",5).put("completed",true).put("running",false))
        withTimeout(25000){repo.flush()}
        assertTrue(repo.record("focus",countUp).getBoolean("running"))
        assertFalse(db.document("users/${repo.uid}/completions/$countUp").get(Source.SERVER).awaitResult().exists())
        val profile=db.document("users/${repo.uid}/profile/main").get(Source.SERVER).awaitResult()
        assertEquals("Edited Profile",profile.getString("displayName"));assertEquals(path,profile.getString("photo"))
        withTimeout(15000){while(!db.document("users/${repo.uid}/receipts/$id").get(Source.SERVER).awaitResult().exists())delay(100)}
        AccountSession.close();val reopened=AccountSession.open(context,repo.uid)
        assertTrue(reopened.record("focus",id).getBoolean("completed"))
        assertTrue(reopened.record("focus",countUp).getBoolean("running"))
        assertTrue(reopened.imageFile(path).exists());assertTrue(reopened.learningState().getInt("totalStudySeconds")>=5)
        fixture.delete(); Unit
    }
    @Test fun buttonMorphIsContinuousAndSuccessTextWaitsForExpansion() {
        val phase=mutableStateOf(SubmitPhase.Idle)
        compose.runOnUiThread { compose.activity.setContent { APlusTheme {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().padding(24.dp)) {
                MorphAuthButton("Bejelentkezés",phase.value,phase.value!=SubmitPhase.Idle,false,"morph"){}
            }
        } } };compose.waitForIdle();compose.mainClock.autoAdvance=false
        val initial=compose.onNodeWithTag("morph_shape").fetchSemanticsNode().boundsInRoot.width
        compose.runOnIdle{phase.value=SubmitPhase.Loading};compose.mainClock.advanceTimeBy(650)
        val ring=compose.onNodeWithTag("morph_shape").fetchSemanticsNode().boundsInRoot
        assertTrue(ring.width<initial/2);assertEquals(ring.width,ring.height,3f)
        compose.runOnIdle{phase.value=SubmitPhase.Ball};compose.mainClock.advanceTimeBy(350)
        assertTrue(compose.onNodeWithTag("morph_shape").fetchSemanticsNode().boundsInRoot.width<ring.width)
        compose.runOnIdle{phase.value=SubmitPhase.Success};compose.mainClock.advanceTimeBy(1000)
        assertEquals(initial,compose.onNodeWithTag("morph_shape").fetchSemanticsNode().boundsInRoot.width,3f)
        compose.onNodeWithText("Sikeres bejelentkezés!").assertIsDisplayed()
    }
    @Test fun projectionPreservesConcurrentFieldsAndNeverRegressesQuiz() {
        fun event(id:String,clock:Int,patch:JSONObject)=JSONObject().put("id",id).put("bucket","sessions").put("entity","s").put("clock",clock).put("patch",patch)
        val records=RecordProjection.reduce(listOf(event("b",2,JSONObject().put("notes","remote").put("stage","quiz").put("question",3)),event("a",1,JSONObject().put("subject","Math").put("stage","timer")),event("c",3,JSONObject().put("stage","timer").put("question",1))))
        val session=records.getJSONObject("sessions").getJSONObject("s")
        assertEquals("Math",session.getString("subject"));assertEquals("remote",session.getString("notes"));assertEquals("quiz",session.getString("stage"));assertEquals(3,session.getInt("question"))
        val focus=RecordProjection.reduce(listOf(event("a",1,JSONObject().put("completed",true)).put("bucket","focus"),event("b",2,JSONObject().put("completed",false).put("running",true)).put("bucket","focus"))).getJSONObject("focus").getJSONObject("s")
        assertTrue(focus.getBoolean("completed"));assertFalse(focus.getBoolean("running"))
    }

    @Test fun notebookRestoresOriginalCloudPhotosIntoANewSessionAndSettingsPersist()=runBlocking {
        val repo=account()
        val fixture=File(context.cacheDir,"notebook-original.png")
        Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.BLUE);fixture.outputStream().use{compress(Bitmap.CompressFormat.PNG,100,it)};recycle()}
        val path=repo.importImage(android.net.Uri.fromFile(fixture))
        repo.write("drafts","learning",JSONObject().put("subject","Math").put("text","Eredeti jegyzet").put("photos",path).put("topic","Az eredeti témakör"))
        val store=LearningStore(context);store.begin(300);store.finish()
        val id=store.read().getJSONObject("session").getString("id")
        repo.write("sessions",id,JSONObject().put("analysisEnd",System.currentTimeMillis()-1));store.analysisDone()
        HistoryQuestions.forEach{store.answer(it.correct);store.next()};store.close()
        withTimeout(25000){repo.flush()}
        val original=repo.record("sessions",id);assertEquals(10,original.getJSONArray("questions").length());assertEquals(path,original.getJSONArray("photoMetadata").getJSONObject(0).getString("path"))
        val cached=repo.imageFile(path);assertTrue(cached.delete())
        compose.runOnUiThread{compose.activity.setContent{APlusTheme{APlusScreen()}}}
        compose.onNodeWithTag("previous_sessions").performClick()
        compose.onNodeWithTag("history_$id").performClick()
        compose.onNodeWithText("Az eredeti témakör").assertExists()
        compose.onRoot().captureToImage().asAndroidBitmap().let{image->File(context.externalCacheDir,"ui-notebook.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}}
        compose.onNodeWithTag("review_material").performScrollTo().performClick()
        compose.waitUntil(25000){store.read().optJSONObject("session")?.optString("id")?.let{it!=id}==true}
        val fresh=store.read().getJSONObject("session");assertNotEquals(id,fresh.getString("id"));assertEquals(300,fresh.getInt("duration"));assertEquals(0,fresh.getJSONArray("answers").length());assertEquals("Eredeti jegyzet",fresh.getString("notes"));assertTrue(repo.imageFile(path).readBytes().contentEquals(fixture.readBytes()));assertEquals("results",repo.record("sessions",id).getString("stage"))
        val missing=JSONObject(original.toString()).put("photos","users/${repo.uid}/media/${UUID.randomUUID()}")
        val failure=runCatching{withTimeout(25000){store.restart(missing)}}.exceptionOrNull();assertNotNull(failure);assertTrue(failure!!.message.orEmpty().contains("eredeti kép"));assertEquals(fresh.getString("id"),store.read().getJSONObject("session").getString("id"))
        compose.onNodeWithTag("learning_back").performClick();compose.onNodeWithText("Vissza",substring=false).performClick()
        compose.onNodeWithTag("top_settings").performClick()
        compose.onNodeWithTag("setting_Animációk csökkentése").performClick()
        compose.onNodeWithTag("setting_Hanghatások").performClick()
        compose.onNodeWithTag("setting_Automatikus szünetek").performScrollTo().performClick()
        compose.onRoot().captureToImage().asAndroidBitmap().let{image->File(context.externalCacheDir,"ui-settings.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}}
        withTimeout(20000){repo.flush()};assertTrue(repo.record("preferences","app").getBoolean("reducedMotion"));assertFalse(repo.record("preferences","app").getBoolean("sounds"));assertTrue(repo.record("preferences","app").getBoolean("autoBreaks"))
        // Returning through the same route and rapidly switching tabs must not corrupt the back stack.
        compose.onNodeWithTag("tab_0").performClick();compose.onNodeWithTag("top_store").performClick()
        compose.onNodeWithTag("shop_slot_5").assertExists()
        compose.onNodeWithContentDescription("Dupla XP Potion, 300 pont").performClick()
        compose.onNodeWithText("Megvásárlás").performClick()
        compose.waitUntil(15000){repo.inventory().any{it.optString("id")=="xp_potion" && it.optInt("count")==1}}
        compose.waitUntil(5000){compose.onAllNodesWithText("Megvásárlás").fetchSemanticsNodes().isEmpty()}
        compose.onRoot().captureToImage().asAndroidBitmap().let{image->File(context.externalCacheDir,"ui-store.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}}
        compose.onNodeWithTag("top_settings").performClick();compose.onNodeWithTag("menu_page_back").performClick();compose.onNodeWithTag("store_page").assertIsDisplayed()
        compose.onNodeWithTag("tab_0").performClick();compose.onNodeWithTag("inventory",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("activate_xp_potion").performClick()
        compose.onNodeWithTag("item_action").performClick()
        compose.waitUntil(15000){repo.serverRecords("boosts").any{it.optLong("endsAt")>System.currentTimeMillis()}}
        assertEquals(300,repo.serverRecords("wallet").first().getInt("spent"))
        val exported=JSONObject(repo.exportJson());assertTrue(exported.getJSONObject("records").has("sessions"));assertTrue(exported.getJSONObject("server").has("boosts"))
        repo.write("preferences","app",JSONObject().put("reducedMotion",false))
    }
}
