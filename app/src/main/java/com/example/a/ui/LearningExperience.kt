package com.example.a.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.activity.compose.BackHandler
import com.example.a.R
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
internal fun LearningExperience(store: LearningStore, onClose: () -> Unit) {
    val reduced=LocalReducedMotion.current
    val preferences=LocalPreferences.current
    val context=androidx.compose.ui.platform.LocalContext.current
    var material by remember{mutableStateOf(false)}
    var state by remember { mutableStateOf(store.read()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val session=state.optJSONObject("session") ?: return
    val stage=session.getString("stage")
    val scrollState=rememberScrollState()
    LaunchedEffect(stage,session.optInt("question")){scrollState.scrollTo(0)}
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler { confirmExit=true }
    LaunchedEffect(stage) { while(true) { now=System.currentTimeMillis();store.autoBreak(now); if(stage=="analysis" && now>=session.optLong("analysisEnd")) store.analysisDone(); state=store.read(); delay(200) } }
    val remaining=if(session.optBoolean("paused")) session.optInt("remaining") else ((session.optLong("end")-now+999)/1000).toInt().coerceAtLeast(0)
    if(stage=="timer") {
        LearnSession(if(session.optLong("breakEnd")>now)((session.optLong("breakEnd")-now)/1000).toInt() else remaining,session.optBoolean("paused"),{store.pause(remaining);state=store.read()},{store.finish();state=store.read()})
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),horizontalArrangement=Arrangement.SpaceBetween){BackButton({confirmExit=true},tag="learning_back");TextButton(onClick={material=true}){Text("Tananyag")}}
        if(session.optLong("breakEnd")>now)Box(Modifier.fillMaxSize().padding(top=160.dp),contentAlignment=Alignment.TopCenter){Text("Szünet · a tanulási idő nem fogy",color=InkColor)}
    } else {
        Column(Modifier.fillMaxSize().background(PageColor).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(scrollState).padding(24.dp).testTag("learning_$stage"), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(20.dp)) {
            BackButton({confirmExit=true},Modifier.align(Alignment.Start),tag="learning_back")
            when(stage) {
                "analysis" -> {
                    Spacer(Modifier.height(160.dp))
                    CircularProgressIndicator(Modifier.size(120.dp), color=AccentColor, strokeWidth=8.dp)
                    Text(analysisLabel(session.optString("photos"),session.optString("notes")),color=InkColor,fontSize=25.sp,fontWeight=FontWeight.Bold)
                }
                "quiz" -> {
                    val index=session.optInt("question").coerceIn(0,9); val question=HistoryQuestions[index]; val answers=session.getJSONArray("answers")
                    val selected=if(answers.length()>index) answers.getInt(index) else -1
                    val feedback=remember(index){Animatable(0f)}
                    LaunchedEffect(index,selected){if(selected>=0)feedback.animateTo(1f,tween(if(reduced)0 else 220))}
                    val score=(0 until minOf(answers.length(),10)).count { answers.getInt(it)==HistoryQuestions[it].correct }*100
                    val displayed by animateIntAsState(score,tween(if(reduced)0 else 260),label="score")
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("${index+1}/10 kérdés",color=InkColor);Text("$displayed pont",color=AccentColor,fontWeight=FontWeight.Bold)}
                    LinearProgressIndicator(progress={ (index+1)/10f },modifier=Modifier.fillMaxWidth(),color=AccentColor)
                    Text(question.text,color=InkColor,fontSize=23.sp,fontWeight=FontWeight.Bold)
                    question.options.forEachIndexed { option, text ->
                        val color=when {selected<0 -> Color(0xFF343440);option==question.correct -> Color(0xFF237F52);option==selected -> Color(0xFFAE414C);else -> Color(0xFF343440)}
                        val scale by animateFloatAsState(if(selected==option) 0.98f else 1f,tween(if(reduced)0 else 180),label="answer")
                        Box(Modifier.fillMaxWidth().graphicsLayer{scaleX=scale;scaleY=scale}.background(color,RoundedCornerShape(16.dp)).clickable(enabled=selected<0){store.answer(option);studyFeedback(context,preferences);state=store.read()}.padding(18.dp).testTag("answer_$option")) {Text(text,color=Color.White,fontSize=17.sp)}
                    }
                    if(selected>=0) {
                        Text(if(selected==question.correct) "Helyes! +100 pont" else "Ez most nem sikerült. A helyes válasz: ${question.options[question.correct]}",modifier=Modifier.graphicsLayer{alpha=feedback.value;translationY=(1-feedback.value)*12},color=if(selected==question.correct) Color(0xFF76DEA1) else Color(0xFFFFA6AD))
                        Text(question.explanation,color=SecondaryInkColor)
                        if(index<9) DepthButton("Következő",AccentColor,{store.next();state=store.read()},Modifier.fillMaxWidth().height(60.dp).testTag("quiz_next"))
                        else LaunchedEffect(session.getString("id"),selected) {delay(1600);store.next();state=store.read()}
                    }
                }
                "results" -> ResultsContent(state) {store.close();onClose()}
            }
        }
    }
    if(material)AlertDialog(onDismissRequest={material=false},title={PageHeading("Tananyag",{material=false})},text={Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Text(session.optString("notes").ifBlank{"Ehhez az alkalomhoz képes jegyzetet adtál meg."})
        session.optString("photos").split('\n').filter{it.isNotBlank()}.forEach{AccountImage(it,Modifier.fillMaxWidth().height(300.dp),androidx.compose.ui.layout.ContentScale.Fit,"Az eredeti kép nem érhető el. Ellenőrizd a felhőmentést és a kapcsolatot.")}
    }},confirmButton={TextButton(onClick={material=false}){Text("Vissza a tanuláshoz")}})
    if(confirmExit) AlertDialog(onDismissRequest={confirmExit=false},title={PageHeading("Vissza a főmenübe?",{confirmExit=false})},text={Text("A folyamatot elmentjük, később folytathatod.")},confirmButton={TextButton(onClick=onClose){Text("Vissza")}},dismissButton={TextButton(onClick={confirmExit=false}){Text("Folytatás")}})
}

@Composable
private fun ResultsContent(state: JSONObject, onClose: () -> Unit) {
    val reduced=LocalReducedMotion.current
    val result=state.getJSONObject("session");val score=result.optInt("score");val old=result.optInt("oldXp")
    val history=state.optJSONArray("completed")
    val receipt=(0 until (history?.length() ?: 0)).map{history!!.getJSONObject(it)}.find{it.optString("id")==result.optString("id")}
    val awardedXp=receipt?.optInt("xp",score) ?: score
    val pending=receipt?.optBoolean("pending") ?: false
    val progress=remember { Animatable(0f) }
    LaunchedEffect(Unit){progress.animateTo(1f,tween(if(reduced)0 else 1400, easing=FastOutSlowInEasing))}
    val xp=old+(awardedXp*progress.value).toInt();val correct=score/100
    Column(Modifier.graphicsLayer{alpha=(progress.value*5).coerceIn(0f,1f);translationY=(1-progress.value)*20},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
        AccountAvatar(com.example.a.data.AccountSession.repository?.profile()?.optString("photo").orEmpty(),Modifier.size(80.dp).graphicsLayer{scaleX=0.9f+progress.value*0.1f;scaleY=scaleX})
        Text("${xp/1000+1}. szint · $xp XP",color=InkColor,fontWeight=FontWeight.Bold)
        LinearProgressIndicator(progress={xp%1000/1000f},modifier=Modifier.fillMaxWidth().height(10.dp),color=Color(0xFFB88AFF))
        Text(if(correct==10) "Tökéletes! ★" else "Szép munka!",color=InkColor,fontSize=30.sp,fontWeight=FontWeight.Bold)
        Text("+${(score*progress.value).toInt()} pont · +${(awardedXp*progress.value).toInt()} XP",color=Color(0xFF76DEA1),fontSize=28.sp)
        if(pending)Text("A jutalom szerveres jóváhagyásra vár.",color=SecondaryInkColor)
        Text("$correct / 10 helyes válasz\n${10-correct} hibás válasz · ${correct*10}% pontosság",color=InkColor,textAlign=TextAlign.Center,fontSize=19.sp)
        if(progress.value>0.7f) Text("🔥 ${state.optInt("streak")} nap"+if(result.optBoolean("streakAdded")) " · Széria +1 nap" else " · Mai sorozat már jóváírva",modifier=Modifier.graphicsLayer{val reveal=((progress.value-0.7f)/0.3f).coerceIn(0f,1f);alpha=reveal;scaleX=0.9f+reveal*0.1f;scaleY=scaleX},color=Color(0xFFFFBF69),textAlign=TextAlign.Center)
        DepthButton("Vissza a főmenübe",AccentColor,onClose,Modifier.fillMaxWidth().height(60.dp).testTag("results_home"),enabled=progress.value>=1f)
    }
}

internal fun analysisLabel(photos:String,notes:String):String = when {
    photos.isNotBlank() && notes.isNotBlank() -> "AI kép- és szövegelemzés"
    photos.isNotBlank() -> "AI kép elemzés"
    else -> "AI szöveg elemzés"
}
