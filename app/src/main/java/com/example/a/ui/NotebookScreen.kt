package com.example.a.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.a.data.AccountSession
import kotlinx.coroutines.*
import org.json.JSONObject
import kotlin.math.*

private val Paper = Color(0xFFFFF7E4)
private val Pen = Color(0xFF263B59)

/** An inextensible curved sheet, rendered as 32 textured strips, including its reverse. */
private fun DrawScope.curledPage(front:GraphicsLayer,back:GraphicsLayer,progress:Float) {
    val count=32;val step=size.width/count;val angle=progress.coerceIn(0f,1f)*PI.toFloat()
    val x=FloatArray(count+1);val z=FloatArray(count+1)
    for(i in 1..count){
        val lag=sin(angle)*.55f*sin((i-.5f)/count*PI.toFloat())
        x[i]=x[i-1]+step*cos(angle-lag);z[i]=z[i-1]+step*sin(angle-lag)
    }
    val matrix=android.graphics.Matrix();val camera=size.width*6f
    for(i in 0 until count){
        val src=floatArrayOf(i*step-.4f,0f,(i+1)*step+.4f,0f,(i+1)*step+.4f,size.height,i*step-.4f,size.height)
        val dst=FloatArray(8)
        fun project(index:Int,bottom:Boolean,at:Int){val k=camera/(camera-z[index]);dst[at]=x[index]*k;dst[at+1]=size.height/2+(if(bottom)size.height/2 else -size.height/2)*k}
        project(i,false,0);project(i+1,false,2);project(i+1,true,4);project(i,true,6)
        if(abs(dst[2]-dst[0])<.1f || !matrix.setPolyToPoly(src,0,dst,0,4))continue
        drawIntoCanvas{canvas->canvas.nativeCanvas.save();canvas.nativeCanvas.concat(matrix);canvas.nativeCanvas.clipRect(src[0],0f,src[2],size.height);drawLayer(if(dst[2]>dst[0])front else back);canvas.nativeCanvas.restore()}
    }
}

@Composable private fun PaperFace(modifier:Modifier=Modifier,content:@Composable BoxScope.()->Unit={}) {
    Box(modifier.background(Paper).drawBehind{
        val line=30.dp.toPx();var y=28.dp.toPx()
        while(y<size.height){drawLine(Color(0xFFAFCEE2),Offset(0f,y),Offset(size.width,y),.7.dp.toPx());y+=line}
        drawLine(Color(0xFFD78D86),Offset(28.dp.toPx(),0f),Offset(28.dp.toPx(),size.height),1.dp.toPx())
    },content=content)
}

@Composable private fun TurningSheet(progress:()->Float,cover:Boolean=false,content:@Composable ()->Unit={}) {
    val front=rememberGraphicsLayer();val back=rememberGraphicsLayer()
    Box(Modifier.fillMaxSize().drawWithContent{drawContent();curledPage(front,back,progress())}) {
        PaperFace(Modifier.fillMaxSize().drawWithContent{back.record{this@drawWithContent.drawContent()}})
        Box(Modifier.fillMaxSize().drawWithContent{front.record{this@drawWithContent.drawContent()}}){
            if(cover)Box(Modifier.fillMaxSize().background(BookCover).padding(20.dp),contentAlignment=Alignment.TopCenter){
                Text("Tanulási\nnapló",Modifier.fillMaxWidth().padding(top=35.dp).background(Color.White).padding(12.dp),color=BookInk,fontFamily=HandwrittenFont,fontSize=26.sp)
            } else PaperFace(Modifier.fillMaxSize()){content()}
        }
    }
}

@Composable internal fun NotebookScreen(store:LearningStore,origin:Rect,onBack:()->Unit,onRestart:()->Unit,modifier:Modifier=Modifier) {
    val repo=AccountSession.repository;val sync=repo?.state?.collectAsState()?.value
    val sessions=remember(sync?.revision){val a=store.read().optJSONArray("completed");(0 until (a?.length() ?: 0)).map{a!!.getJSONObject(it)}.sortedWith(compareByDescending<JSONObject>{it.optString("date")}.thenByDescending{it.optLong("finishedAt",it.optLong("startedAt"))})}
    val scope=rememberCoroutineScope();val reduced=LocalReducedMotion.current
    val zoom=remember{Animatable(if(reduced)1f else 0f)}
    val leaves=remember{List(4){Animatable(if(reduced)1f else 0f)}}
    var entering by remember{mutableStateOf(!reduced)};var closing by remember{mutableStateOf(false)}
    var rootOffset by remember{mutableStateOf(Offset.Zero)}
    var index by remember{mutableIntStateOf(0)};var detail by remember{mutableStateOf<JSONObject?>(null)}
    var previous by remember{mutableStateOf<Pair<Int,JSONObject?>?>(null)}
    val flip=remember{Animatable(1f)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    fun turn(page:Int,session:JSONObject?){if(busy||entering||closing)return;scope.launch{busy=true;previous=index to detail;flip.snapTo(0f);index=page;detail=session;if(!reduced)flip.animateTo(1f,spring(.93f,145f)) else flip.snapTo(1f);previous=null;busy=false}}
    fun back(){if(closing)return;if(detail!=null){turn(index,null);return};scope.launch{closing=true;if(!reduced)coroutineScope{launch{zoom.animateTo(0f,spring(.96f,230f))};leaves.forEachIndexed{i,a->launch{delay((3-i)*35L);a.animateTo(0f,spring(.95f,230f))}}};onBack()}}
    BackHandler{back()}
    LaunchedEffect(Unit){if(!reduced)coroutineScope{launch{zoom.animateTo(1f,spring(.95f,95f))};leaves.forEachIndexed{i,a->launch{delay(i*75L);a.animateTo(1f,spring(.9f,125f))}}};entering=false}
    BoxWithConstraints(modifier.testTag("notebook_page").onGloballyPositioned{rootOffset=it.positionInRoot()}.clipToBounds()) {
        val density=LocalDensity.current;val width=with(density){maxWidth.toPx()};val height=with(density){maxHeight.toPx()}
        val source=if(origin.width>0)origin else Rect(width*.53f,rootOffset.y+16f,width*.95f,rootOffset.y+height*.25f)
        Box(Modifier.fillMaxSize().graphicsLayer{
            val p=zoom.value.coerceIn(0f,1f);transformOrigin=TransformOrigin(0f,0f)
            scaleX=source.width/width+(1-source.width/width)*p;scaleY=source.height/height+(1-source.height/height)*p
            translationX=(source.left-rootOffset.x)*(1-p);translationY=(source.top-rootOffset.y)*(1-p);rotationZ=5f*(1-p)
        }) {
            @Composable fun Content(page:Int,selected:JSONObject?,interactive:Boolean){
                Column(Modifier.fillMaxSize().padding(start=38.dp,end=18.dp,top=12.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically){BackButton({if(interactive)back()},background=Color(0xFFE6D8BB),color=Pen,tag="notebook_back");Spacer(Modifier.width(8.dp));Text(if(selected==null)"Tanulási napló" else subjectName(selected.optString("subject")),color=Pen,fontFamily=HandwrittenFont,fontSize=23.sp,fontWeight=FontWeight.Bold)}
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)){
                    if(selected==null){
                        if(sessions.isEmpty())Text("Még nincs befejezett tanulásod. Az első alkalom után itt találod az eredményeidet.",color=Pen,fontFamily=HandwrittenFont,fontSize=18.sp)
                        sessions.drop(page*4).take(4).forEach{session->
                            Column(Modifier.fillMaxWidth().clickable(enabled=interactive&&!busy){turn(page,session)}.padding(vertical=10.dp).testTag("history_${session.optString("id")}"),verticalArrangement=Arrangement.spacedBy(5.dp)){
                                Text(subjectName(session.optString("subject")),color=Pen,fontFamily=HandwrittenFont,fontWeight=FontWeight.Bold,fontSize=21.sp)
                                SessionSummary(session)
                            }
                            HorizontalDivider(color=Color(0xFFAFCEE2))
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick={turn(page-1,null)},enabled=interactive&&!busy&&page>0){Text("Előző lap")};Text("${page+1}. lap",color=Pen);TextButton(onClick={turn(page+1,null)},enabled=interactive&&!busy&&(page+1)*4<sessions.size){Text("Következő lap")}}
                    }else{
                        SessionSummary(selected)
                        val questions=selected.optJSONArray("questions");val answers=selected.optJSONArray("answers")
                        if(questions==null||questions.length()==0)Text("Ehhez a korábbi alkalomhoz nem áll rendelkezésre elmentett kérdéssor.",color=Pen,fontFamily=HandwrittenFont)
                        else repeat(questions.length()){qIndex->
                            val q=questions.getJSONObject(qIndex);val choices=q.optJSONArray("options");val answer=answers?.optInt(qIndex,-1) ?: -1;val correct=q.optInt("correct",-1)
                            Text("${qIndex+1}. ${q.optString("text")}",color=Pen,fontFamily=HandwrittenFont,fontSize=19.sp,fontWeight=FontWeight.Bold)
                            Text("Válaszod: ${choices?.optString(answer)?.takeIf{it.isNotEmpty()} ?: "Nincs válasz"}",color=if(answer==correct)Color(0xFF237447) else Color(0xFFA02F3A),fontFamily=HandwrittenFont,fontSize=18.sp)
                            Text("Helyes válasz: ${choices?.optString(correct).orEmpty()}",color=Color(0xFF237447),fontFamily=HandwrittenFont,fontSize=18.sp)
                        }
                        if(selected.optString("notes").isNotBlank())Text(selected.optString("notes"),color=Pen,fontFamily=HandwrittenFont,fontSize=18.sp)
                        if(error!=null)Text(error!!,color=Color(0xFFA02F3A))
                        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                        Button(enabled=interactive&&!busy&&selected.has("duration"),onClick={scope.launch{busy=true;error=null;try{store.restart(selected);onRestart()}catch(e:Exception){error=e.message ?: "A tananyag nem tölthető vissza."}finally{busy=false}}},modifier=Modifier.fillMaxWidth().testTag("review_material")){Text("Tananyag újbóli áttekintése")}
                    }
                }
            }
            }
            PaperFace(Modifier.fillMaxSize()){key(index,detail?.optString("id")){Content(index,detail,!entering&&!closing&&previous==null)}}
            previous?.let{(page,selected)->TurningSheet({flip.value}){Content(page,selected,false)}}
            if(entering||closing){for(i in 3 downTo 0)TurningSheet({leaves[i].value},cover=i==0)}
        }
    }
}

@Composable private fun SessionSummary(s:JSONObject){
    val questions=s.optJSONArray("questions");val answers=s.optJSONArray("answers")
    val correct=if(questions!=null)(0 until questions.length()).count{answers?.optInt(it,-1)==questions.getJSONObject(it).optInt("correct",-2)} else s.optInt("score")/100
    val count=questions?.length() ?: answers?.length() ?: 0
    Text(s.optString("topic").ifBlank{if(s.optString("kind")=="focus")"Fókuszálás" else "Tanulási alkalom"},color=Pen,fontFamily=HandwrittenFont,fontSize=18.sp)
    Text("${s.optString("date")} · ${s.optInt("durationSeconds",s.optInt("studiedSeconds"))/60} perc\n${s.optInt("score")} pont · $correct/$count helyes válasz"+if(s.optBoolean("pending"))"\nJutalom jóváhagyásra vár" else "",color=Pen,fontFamily=HandwrittenFont,fontSize=16.sp,lineHeight=23.sp)
}
internal fun subjectName(id:String)=when(id){"Math"->"Matematika";"Literature"->"Irodalom";"English"->"Angol";"Biology"->"Biológia";"Science"->"Természettudomány";"ForeignLanguage"->"Idegen nyelv";"History"->"Történelem";"Focus"->"Fókusz";else->"Tanulás"}
