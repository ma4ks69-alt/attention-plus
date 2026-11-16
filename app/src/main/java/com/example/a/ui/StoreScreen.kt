package com.example.a.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.example.a.data.AccountSession
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.*

internal data class ShopItem(val id:String,val name:String,val price:Int,val description:String)
@Composable internal fun rememberCatalog():List<ShopItem>{val context=LocalContext.current;return remember{val json=context.assets.open("items.json").bufferedReader().use{JSONObject(it.readText())};json.keys().asSequence().map{val d=json.getJSONObject(it);ShopItem(it,d.getString("name"),d.getInt("price"),d.getString("description"))}.toList()}}
/** Driven damped pendulum. Input is the actual horizontal scene position, not a canned swing. */
internal class SignPhysics {
    var angle=0f;private var velocity=0f;private var previousX=Float.NaN;private var previousVelocity=0f
    val atRest get()=abs(angle)<.001f && abs(velocity)<.001f
    fun reset(x:Float){angle=0f;velocity=0f;previousX=x;previousVelocity=0f}
    fun advance(x:Float,seconds:Float){
        val dt=seconds.coerceIn(.001f,.035f)
        if(previousX.isNaN()){previousX=x;return}
        val v=(x-previousX)/dt;val acceleration=((v-previousVelocity)/dt).coerceIn(-16000f,16000f)
        repeat(4){val h=dt/4;val force=-28f*sin(angle)-4.6f*velocity-acceleration/650f*cos(angle);velocity+=force*h;angle=(angle+velocity*h).coerceIn(-.36f,.36f)}
        previousX=x;previousVelocity=v
        if(abs(velocity)<.001f&&abs(angle)<.001f&&abs(v)<.1f){angle=0f;velocity=0f}
    }
}
@Composable internal fun StoreScreen(onBack:()->Unit,modifier:Modifier=Modifier,horizontalPosition:()->Float={0f},physics:SignPhysics=remember{SignPhysics()}) {
    val items=rememberCatalog();val repo=AccountSession.repository;val sync=repo?.state?.collectAsState()?.value
    val earned=remember(sync?.revision){repo?.serverRecords("stats")?.firstOrNull()?.optInt("points") ?: 0}
    val spent=remember(sync?.revision){repo?.serverRecords("wallet")?.firstOrNull()?.optInt("spent") ?: 0}
    var selected by remember{mutableStateOf<ShopItem?>(null)}
    var swing by remember{mutableFloatStateOf(physics.angle)}
    val position by rememberUpdatedState(horizontalPosition);val reduced=LocalReducedMotion.current
    val sceneX=horizontalPosition()
    LaunchedEffect(sceneX,reduced){
        if(reduced){physics.reset(sceneX*838f);swing=0f;return@LaunchedEffect}
        physics.advance(sceneX*838f,1f/60);swing=physics.angle
        var last=withFrameNanos{it}
        while(!physics.atRest){val time=withFrameNanos{it};physics.advance(position()*838f,(time-last)/1e9f);swing=physics.angle;last=time}
    }
    BoxWithConstraints(modifier.testTag("store_page").background(Color(0xFFAD552A))) {
        val boardFont=(maxWidth.value*.14f).sp
        val unit=maxWidth/838f
        val height=maxOf(maxHeight,unit*1510f)
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(height)) {
                Canvas(Modifier.fillMaxSize()){val scale=size.width/838f;scale(scale,scale,pivot=Offset.Zero){woodWall(size.height/scale);shelves();hangingSign(swing)}}
                BackButton(onBack,Modifier.padding(20.dp),background=Color(0xFF51210D),tag="menu_page_back")
                // Typography moves with the same board pose as its drawn rope attachments.
                val shift=sin(swing)*145f;val down=(1-cos(swing))*145f
                Box(Modifier.offset(x=unit*(185f+shift),y=unit*(178f+down)).width(unit*468f).height(unit*184f),contentAlignment=Alignment.Center){Text("Bolt",color=Color(0xFF411A0C),fontWeight=FontWeight.Black,fontFamily=androidx.compose.ui.text.font.FontFamily.Default,fontSize=boardFont)}
                Text("Pontjaid: ${(earned-spent).coerceAtLeast(0)}",Modifier.align(Alignment.TopCenter).padding(top=unit*405f).background(Color(0xFFFFE9C8),RoundedCornerShape(12.dp)).padding(horizontal=16.dp,vertical=7.dp),color=Color(0xFF51210D),fontWeight=FontWeight.Bold)
                repeat(6){index->
                    val x=95f+(index%3)*225f;val y=483f+(index/3)*304f
                    Box(Modifier.offset(unit*x,unit*y).size(unit*196f,unit*174f).background(Color(0xFFCEC4C0).copy(alpha=.5f),RoundedCornerShape(15.dp)).testTag("shop_slot_$index"),contentAlignment=Alignment.Center){
                        items.getOrNull(index)?.let{item->Column(Modifier.fillMaxSize().clickable(role=Role.Button){selected=item}.padding(5.dp).semantics{contentDescription="${item.name}, ${item.price} pont"},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceEvenly){
                            ItemIcon(item.id,Modifier.weight(1f).fillMaxWidth())
                            Text(item.name,color=Color(0xFF411A0C),fontWeight=FontWeight.Bold,fontSize=10.sp,lineHeight=11.sp,textAlign=TextAlign.Center,maxLines=2)
                            Text("${item.price} pont",color=Color(0xFF411A0C),fontWeight=FontWeight.Black,fontSize=10.sp)
                        }}
                    }
                }
            }
        }
    }
    selected?.let{item->ItemDialog(item,false,{selected=null})}
}
private val WoodInk=Color(0xFF4B210C)
private fun DrawScope.woodWall(height:Float){
    drawRect(Color(0xFFAD552A),size=Size(838f,height))
    var y=0f;var row=0
    while(y<height){drawRect(if(row%2==0)Color(0xFFAA5126) else Color(0xFFB15B2D),Offset(0f,y),Size(838f,112f));drawLine(WoodInk,Offset(0f,y+112),Offset(838f,y+112),7f)
        repeat(3){i->val start=((row*137+i*279)%850-100).toFloat();val path=Path().apply{moveTo(start,y+25+i*25);cubicTo(start+95,y+5+i*25,start+133,y+55+i*25,start+260,y+23+i*25)};drawPath(path,Color(0xFF94471F),style=Stroke(7f,cap=StrokeCap.Round))}
        if(row%4==0){val x=if(row%8==0)740f else 130f;drawOval(Color(0xFF94471F),Offset(x-40,y+40),Size(80f,42f),style=Stroke(7f));drawOval(Color(0xFF94471F),Offset(x-18,y+52),Size(36f,17f),style=Stroke(6f))}
        y+=114f;row++
    }
}
private fun DrawScope.shelves(){
    for(y in listOf(657f,961f)){
        for(x in listOf(32f,758f)){
            val inward=if(x<400)1 else -1
            val path=Path().apply{moveTo(x+24,y+107);lineTo(x+24+inward*93,y+23);lineTo(x+24+inward*127,y+23);lineTo(x+24,y+145);close()}
            drawPath(path,Color(0xFFFFB848));drawPath(path,WoodInk,style=Stroke(6f,join=StrokeJoin.Round))
            drawRoundRect(Color(0xFFFFB848),Offset(x,y-86),Size(48f,218f),CornerRadius(22f));drawRoundRect(WoodInk,Offset(x,y-86),Size(48f,218f),CornerRadius(22f),style=Stroke(6f))
            drawCircle(Color.White,9f,Offset(x+24,y-60));drawCircle(Color.White,9f,Offset(x+24,y+106))
        }
        drawRoundRect(Color(0xFFFFB23C),Offset(17f,y),Size(804f,44f),CornerRadius(20f));drawRoundRect(WoodInk,Offset(17f,y),Size(804f,44f),CornerRadius(20f),style=Stroke(6f))
    }
}
private fun DrawScope.hangingSign(angle:Float){
    val dx=sin(angle)*145f;val dy=(1-cos(angle))*145f
    val face=Color(0xFFFFEBC9)
    drawRoundRect(face,Offset(185+dx,178+dy),Size(468f,184f),CornerRadius(40f));drawRoundRect(WoodInk,Offset(185+dx,178+dy),Size(468f,184f),CornerRadius(40f),style=Stroke(8f))
    for(x in listOf(260f,576f)){
        drawCircle(Color(0xFFFFB848),25f,Offset(x+dx,190+dy));drawCircle(WoodInk,25f,Offset(x+dx,190+dy),style=Stroke(6f))
        drawLine(WoodInk,Offset(x,-10f),Offset(x+dx,188+dy),26f,StrokeCap.Round)
        drawLine(face,Offset(x,-10f),Offset(x+dx,188+dy),12f,StrokeCap.Round)
    }
}
@Composable internal fun ItemIcon(id:String,modifier:Modifier=Modifier){
    Box(modifier,contentAlignment=Alignment.Center){Canvas(Modifier.fillMaxSize().padding(4.dp)){
        val factor=min(size.width,size.height)/100f;translate((size.width-100*factor)/2,(size.height-100*factor)/2){scale(factor,factor,pivot=Offset.Zero){
            if(id=="xp_potion"){
                val bottle=Path().apply{moveTo(35f,22f);lineTo(35f,38f);cubicTo(1f,53f,13f,93f,30f,94f);lineTo(70f,94f);cubicTo(87f,93f,99f,53f,65f,38f);lineTo(65f,22f);close()}
                drawPath(bottle,Color(0xFFCEEFFF));drawPath(bottle,Color(0xFF352564),style=Stroke(5f,join=StrokeJoin.Round))
                drawRoundRect(Color(0xFF8C62E9),Offset(24f,54f),Size(52f,30f),CornerRadius(10f));drawRoundRect(Color(0xFFFFBC5B),Offset(33f,9f),Size(34f,17f),CornerRadius(4f));drawRoundRect(Color(0xFF352564),Offset(33f,9f),Size(34f,17f),CornerRadius(4f),style=Stroke(4f));drawCircle(Color(0xFFDCCBFF),4f,Offset(65f,60f))
            }else{
                val ice=Path().apply{moveTo(50f,4f);lineTo(84f,25f);lineTo(94f,65f);lineTo(51f,96f);lineTo(8f,69f);lineTo(17f,27f);close()};drawPath(ice,Color(0xFFA3ECFF));drawPath(ice,Color(0xFF156086),style=Stroke(5f,join=StrokeJoin.Round))
                repeat(6){i->rotate(i*60f,Offset(50f,52f)){drawLine(Color.White,Offset(50f,28f),Offset(50f,52f),5f,StrokeCap.Round);drawLine(Color.White,Offset(42f,34f),Offset(50f,41f),4f,StrokeCap.Round);drawLine(Color.White,Offset(58f,34f),Offset(50f,41f),4f,StrokeCap.Round)}}
            }
        }}
    }}
}
@Composable private fun ItemDialog(item:ShopItem,inventory:Boolean,onClose:()->Unit){
    val repo=AccountSession.repository;val scope=rememberCoroutineScope();var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest={if(!busy)onClose()},title={PageHeading(item.name,{if(!busy)onClose()})},text={Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){ItemIcon(item.id,Modifier.size(100.dp));Text(item.description);if(!inventory)Text("Ár: ${item.price} pont");if(error!=null)Text(error!!,color=MaterialTheme.colorScheme.error);if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())}},confirmButton={
        TextButton(modifier=Modifier.testTag("item_action"),enabled=!busy && (!inventory || item.id=="xp_potion"),onClick={scope.launch{busy=true;runCatching{checkNotNull(repo).shop(if(inventory)"activate" else "buy",item.id)}.onSuccess{onClose()}.onFailure{error=friendlyCloudError(it)};busy=false}}){Text(if(inventory)"Aktiválás" else "Megvásárlás")}
    })
}
@Composable internal fun InventoryScreen(onBack:()->Unit,modifier:Modifier=Modifier){
    val repo=AccountSession.repository;val sync=repo?.state?.collectAsState()?.value;val items=rememberCatalog();val inventory=remember(sync?.revision){repo?.inventory().orEmpty()};val boost=remember(sync?.revision){repo?.serverRecords("boosts")?.firstOrNull()}
    var now by remember{mutableLongStateOf(System.currentTimeMillis())};LaunchedEffect(Unit){while(true){now=System.currentTimeMillis();kotlinx.coroutines.delay(1000)}}
    var selected by remember{mutableStateOf<ShopItem?>(null)}
    Column(modifier.testTag("inventory_page").padding(horizontal=24.dp)){
        PageHeading("Eszköztár",onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        val remaining=((boost?.optLong("endsAt") ?: 0)-now)/1000
        if(remaining>0)Text("Dupla XP aktív · ${remaining/60}:${(remaining%60).toString().padStart(2,'0')}",color=Color(0xFFBFA4FF),fontWeight=FontWeight.Bold)
        items.forEach{item->val count=inventory.find{it.optString("id")==item.id}?.optInt("count") ?: 0
            Row(Modifier.fillMaxWidth().background(Color(0xFF353535),RoundedCornerShape(18.dp)).padding(16.dp),verticalAlignment=Alignment.CenterVertically){ItemIcon(item.id,Modifier.size(72.dp));Column(Modifier.weight(1f)){Text(item.name,color=InkColor,fontWeight=FontWeight.Bold);Text("$count darab",color=SecondaryInkColor);if(item.id=="xp_potion")TextButton(modifier=Modifier.testTag("activate_${item.id}"),onClick={selected=item},enabled=count>0 && remaining<=0){Text("Aktiválás")}else Text("Automatikus védelem",color=SecondaryInkColor,fontSize=12.sp)}}
        }
    }};selected?.let{ItemDialog(it,true){selected=null}}
}
