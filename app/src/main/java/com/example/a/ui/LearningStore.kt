package com.example.a.ui

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate
import java.util.UUID

internal data class HistoryQuestion(val text: String, val options: List<String>, val correct: Int, val explanation: String)
internal val HistoryQuestions = listOf(
    HistoryQuestion("Melyik békeszerződés rögzítette Magyarország első világháború utáni határait?", listOf("Versailles-i", "Trianoni", "Brest-litovszki", "Lausanne-i"), 1, "A trianoni békét 1920. június 4-én írták alá."),
    HistoryQuestion("Mi volt a Marshall-terv elsődleges gazdasági célja?", listOf("A gyarmati rendszer helyreállítása", "A szovjet nehézipar fejlesztése", "Európa háború utáni újjáépítése", "Az aranyalap visszaállítása"), 2, "Az amerikai támogatás az európai gazdaságok helyreállítását szolgálta."),
    HistoryQuestion("Melyik évben alakult meg a NATO?", listOf("1945", "1955", "1961", "1949"), 3, "Az Észak-atlanti Szerződést 1949. április 4-én írták alá."),
    HistoryQuestion("Mivel válaszoltak a nyugati szövetségesek az 1948–1949-es berlini blokádra?", listOf("Légihíddal látták el Nyugat-Berlint", "Feladták Nyugat-Berlint", "Lerombolták a berlini falat", "Kiléptek az ENSZ-ből"), 0, "A légihíd a szovjet szárazföldi blokád ellenére biztosította az ellátást."),
    HistoryQuestion("Ki jelentette be Magyarország semlegességét 1956 novemberében?", listOf("Kádár János", "Nagy Imre", "Rákosi Mátyás", "Horthy Miklós"), 1, "Nagy Imre kormánya semlegességet és a Varsói Szerződésből való kilépést jelentett be."),
    HistoryQuestion("Melyik esemény történt 1989. november 9-én?", listOf("A Szovjetunió felbomlása", "A NATO megalakulása", "A berlini fal megnyitása", "A magyar forradalom kitörése"), 2, "A határátkelők megnyitása a hidegháborús megosztottság megszűnésének jelképe lett."),
    HistoryQuestion("Mit jelentett a hidegháború kétpólusú világrendje?", listOf("Két európai monarchia együttműködését", "A gyarmatok két csoportra osztását", "Kizárólag gazdasági versenyt", "Az USA és a Szovjetunió vezette tömbök szembenállását"), 3, "A két nagyhatalom politikai, katonai és ideológiai tömbök központja lett."),
    HistoryQuestion("Melyik megállapodás szüntette meg a közepes hatótávolságú, szárazföldi telepítésű amerikai és szovjet nukleáris rakéták egy kategóriáját?", listOf("Az 1987-es INF-szerződés", "A trianoni béke", "A müncheni egyezmény", "A római szerződés"), 0, "Az INF-szerződés jelentős fegyverzetkorlátozási lépés volt a hidegháború végén."),
    HistoryQuestion("Melyik jelenség kapcsolódik a Kádár-korszak úgynevezett gulyáskommunizmusához?", listOf("Többpárti parlamenti demokrácia", "A fogyasztás és az életszínvonal szerepének növekedése", "A magántulajdon teljes visszaállítása", "Csatlakozás a NATO-hoz"), 1, "Az életszínvonal-politika a pártállami rendszer fennmaradása mellett kapott nagyobb szerepet."),
    HistoryQuestion("Milyen történelmi folyamathoz kapcsolódott az 1989-es páneurópai piknik?", listOf("A berlini blokádhoz", "A Varsói Szerződés létrehozásához", "A vasfüggöny lebontásához", "Az első világháború lezárásához"), 2, "A magyar–osztrák határnál tartott esemény a kelet-európai rendszerváltozások része volt."),
)

/** Keeps the existing learning flow API over the account journal; preferences are legacy-only. */
internal class LearningStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("learning_progress", Context.MODE_PRIVATE)
    private val cloud = com.example.a.data.AccountSession.repository
    fun read(): JSONObject = cloud?.learningState() ?: JSONObject(prefs.getString("state", "{}")!!)
    private fun save(s: JSONObject) {
        if(cloud != null) {
            val session=s.optJSONObject("session")
            if(session!=null) cloud.write("sessions",session.getString("id"),session)
        } else check(prefs.edit().putString("state", s.toString()).commit()) { "A mentés nem sikerült" }
    }
    fun begin(seconds: Int) = synchronized(lock) {
        val s = read(); val draft=cloud?.record("drafts","learning") ?: JSONObject()
        s.put("session", JSONObject().put("subject",draft.optString("subject","History"))
            .put("notes",draft.optString("text")).put("photos",draft.optString("photos"))
            .put("topic",draft.optString("topic").ifBlank{draft.optString("text").lineSequence().firstOrNull().orEmpty().take(100)})
            .put("questions",JSONArray().apply{HistoryQuestions.forEach{put(JSONObject().put("text",it.text).put("options",JSONArray(it.options)).put("correct",it.correct).put("explanation",it.explanation))}})
            .put("photoMetadata",cloud?.imageMetadata(draft.optString("photos")) ?: JSONArray())
            .put("autoBreaks",cloud?.record("preferences","app")?.optBoolean("autoBreaks") ?: false).put("breakCount",0)
            .put("startedAt",System.currentTimeMillis()).put("duration",seconds).put("studiedSeconds",0).put("id", UUID.randomUUID().toString()).put("stage", "timer").put("end", System.currentTimeMillis() + seconds * 1000L).put("remaining", seconds).put("paused", false).put("answers", JSONArray()).put("question", 0)); save(s); cloud?.write("preferences","app",JSONObject().put("activeLearningId",s.getJSONObject("session").getString("id")))
        cloud?.write("drafts","learning",JSONObject().put("step",0))
    }
    fun pause(remaining: Int) = synchronized(lock) { val s=read(); val a=s.getJSONObject("session"); val paused=!a.optBoolean("paused"); a.put("breakEnd",0).put("paused",paused).put("remaining",remaining).put("end",System.currentTimeMillis()+remaining*1000L);save(s) }
    fun finish() = synchronized(lock) { val s=read();val a=s.getJSONObject("session"); if(a.optString("stage")=="timer") { val remaining=if(a.optBoolean("paused")) a.optInt("remaining") else ((a.optLong("end")-System.currentTimeMillis()+999)/1000).toInt().coerceAtLeast(0)
            a.put("studiedSeconds",(a.optInt("duration")-remaining).coerceAtLeast(0)).put("studyFinishedAt",System.currentTimeMillis())
            a.put("stage","analysis").put("analysisEnd",System.currentTimeMillis()+26000);save(s) } }
    fun analysisDone() = synchronized(lock) { val s=read();val a=s.getJSONObject("session");if(a.optString("stage")=="analysis" && System.currentTimeMillis()>=a.optLong("analysisEnd")){a.put("stage","quiz");save(s)} }
    fun answer(option: Int) = synchronized(lock) { val s=read();val a=s.getJSONObject("session");val q=a.optInt("question");val answers=a.getJSONArray("answers");if(a.optString("stage")=="quiz" && q in 0..9 && option in 0..3 && answers.length()==q){answers.put(option);save(s)} }
    fun next(today: LocalDate = LocalDate.now()) = synchronized(lock) {
        val s=read();val a=s.getJSONObject("session");val q=a.optInt("question");val answers=a.getJSONArray("answers")
        if(a.optString("stage")!="quiz" || answers.length()!=q+1) return@synchronized
        if(q<9) a.put("question",q+1) else {
            val score=(0..9).count{ answers.getInt(it)==HistoryQuestions[it].correct }*100
            val previous=s.optString("lastDay");val fresh=previous!=today.toString() && (previous.isEmpty() || LocalDate.parse(previous).isBefore(today))
            val streak=if(fresh) { if(previous==today.minusDays(1).toString()) s.optInt("streak")+1 else 1 } else s.optInt("streak")
            val oldXp=s.optInt("xp"); a.put("oldXp",oldXp).put("score",score).put("streakAdded",fresh).put("stage","results").put("finishedAt",System.currentTimeMillis()).put("date",today.toString())
            s.put("xp",oldXp+score).put("points",s.optInt("points")+score).put("streak",streak)
            if(fresh)s.put("lastDay",today.toString())
            val completed=s.optJSONArray("completed")?:JSONArray();completed.put(JSONObject(a.toString()).put("durationSeconds",a.optInt("studiedSeconds")));s.put("completed",completed)
        };save(s)
    }
    fun close() = synchronized(lock) { val s=read(); val a=s.optJSONObject("session")
        if(cloud!=null && a!=null) cloud.write("sessions",a.getString("id"),JSONObject().put("closed",true))
        else { s.remove("session");save(s) } }
    suspend fun restart(original: JSONObject) {
        require(!original.optBoolean("legacy") && original.has("duration")) { "Ez a régi tanulás nem tartalmaz visszaállítható tananyagot." }
        val photos=original.optString("photos").split('\n').filter{it.isNotBlank()}
        for(path in photos) {
            require(path.startsWith("users/")) { "Az eredeti kép csak a régi készüléken volt elérhető, nem állítható vissza." }
            try { kotlinx.coroutines.withTimeout(20000){checkNotNull(cloud).imageFile(path)} }
            catch(e:Exception){throw IllegalStateException("Az eredeti kép nem érhető el. Ellenőrizd a kapcsolatot és a képek felhőmentését. Másik képpel nem helyettesítettük.",e)}
        }
        checkNotNull(cloud).write("drafts","learning",JSONObject().put("subject",original.optString("subject")).put("topic",original.optString("topic"))
            .put("text",original.optString("notes")).put("photos",original.optString("photos")).put("seconds",original.optInt("duration")).put("step",2))
        begin(original.optInt("duration").coerceAtLeast(1))
        val fresh=read().getJSONObject("session")
        cloud.write("sessions",fresh.getString("id"),JSONObject().put("autoBreaks",original.optBoolean("autoBreaks")).put("repeatedFrom",original.optString("id")))
    }
    fun autoBreak(now:Long=System.currentTimeMillis()) = synchronized(lock) {
        val s=read();val a=s.optJSONObject("session") ?: return@synchronized
        if(a.optString("stage")!="timer" || !a.optBoolean("autoBreaks"))return@synchronized
        val breakEnd=a.optLong("breakEnd")
        if(breakEnd>0){if(now>=breakEnd){a.put("breakEnd",0).put("paused",false).put("end",now+a.optInt("remaining")*1000L);save(s)};return@synchronized}
        if(a.optBoolean("paused"))return@synchronized
        val remaining=((a.optLong("end")-now+999)/1000).toInt().coerceAtLeast(0)
        val studied=a.optInt("duration")-remaining
        if(remaining>0 && studied >= (a.optInt("breakCount")+1)*25*60){
            a.put("paused",true).put("remaining",remaining).put("breakEnd",now+5*60*1000).put("breakCount",a.optInt("breakCount")+1);save(s)
        }
    }
    companion object { private val lock=Any() }

}
