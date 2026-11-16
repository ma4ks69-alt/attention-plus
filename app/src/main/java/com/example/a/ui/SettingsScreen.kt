package com.example.a.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import com.example.a.auth.AccountAuth
import com.example.a.data.*
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable internal fun SettingsScreen(onBack:()->Unit,modifier:Modifier=Modifier) {
    val context=LocalContext.current;val activity=checkNotNull(LocalActivity.current)
    val repository=AccountSession.repository
    val preferences=LocalPreferences.current
    val scope=rememberCoroutineScope();val auth=remember{AccountAuth(activity)}
    var message by remember{mutableStateOf<String?>(null)}
    var action by remember{mutableStateOf<String?>(null)}
    var value by remember{mutableStateOf("")};var password by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    fun save(key:String,v:Any){repository?.write("preferences","app",JSONObject().put(key,v))}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->save("notifications",granted);if(!granted)message="Az értesítésekhez engedély szükséges a telefon beállításaiban."}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)scope.launch{runCatching{context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use{it.write(checkNotNull(repository).exportJson())}}.onSuccess{message="Az adatexport elkészült."}.onFailure{message="Az export nem sikerült. Próbáld újra."}}}
    val version=remember{context.packageManager.getPackageInfo(context.packageName,PackageManager.PackageInfoFlags.of(0)).versionName.orEmpty()}
    BackHandler {onBack()}
    Column(modifier.testTag("settings_page")) {
        PageHeading("Beállítások",onBack,Modifier.padding(horizontal=24.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        SettingsSection("Általános")
        PreferenceSwitch("Értesítések",preferences.optBoolean("notifications")){if(it && androidx.core.content.ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.POST_NOTIFICATIONS) else save("notifications",it)}
        PreferenceSwitch("Hanghatások",preferences.optBoolean("sounds",true)){save("sounds",it)}
        PreferenceSwitch("Animációk csökkentése",preferences.optBoolean("reducedMotion")){save("reducedMotion",it)}
        if(context.getSystemService(android.os.VibratorManager::class.java).defaultVibrator.hasVibrator())PreferenceSwitch("Rezgés",preferences.optBoolean("vibration",true)){save("vibration",it)}
        SettingsSection("Tanulás")
        PreferenceSwitch("Értesítések némítása tanulás közben",preferences.optBoolean("studySilence",true)){save("studySilence",it)}
        Text("Tanulás és fókuszálás közben automatikusan bekapcsolja a Ne zavarjanak módot.",color=SecondaryInkColor,fontSize=12.sp)
        TextButton(onClick={runCatching{context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))}.onFailure{message="A Ne zavarjanak beállítás nem érhető el ezen az eszközön."}}){Text("Ne zavarjanak hozzáférés")}

        PreferenceChoices("Alapértelmezett tanulási idő",preferences.optInt("defaultStudyMinutes",25),listOf(5,10,15,25,30,45,60)){save("defaultStudyMinutes",it)}
        PreferenceChoices("Napi tanulási cél",preferences.optInt("dailyGoalMinutes",30),listOf(10,15,30,45,60,90,120)){save("dailyGoalMinutes",it)}
        PreferenceSwitch("Tanulási emlékeztetők",preferences.optBoolean("reminders")){save("reminders",it)}
        TextButton(onClick={value=preferences.optString("reminderTime","18:00");action="Emlékeztető időpontja"}){Text("Emlékeztető: ${preferences.optString("reminderTime","18:00")}")}
        Text("A telefon energiatakarékos működése késleltetheti az emlékeztetőt. Értesítési engedély is szükséges.",color=SecondaryInkColor,fontSize=12.sp)
        PreferenceSwitch("Automatikus szünetek",preferences.optBoolean("autoBreaks")){save("autoBreaks",it)}
        Text("25 perc tanulás után 5 perces szünet. A tanulási idő a szünet alatt nem fogy.",color=SecondaryInkColor,fontSize=12.sp)
        SettingsSection("Fiók és profil")
        ProfileAccountActions()
        TextButton(onClick={value="";password="";action="E-mail-cím módosítása"}){Text("E-mail-cím módosítása")}
        if(auth.auth.currentUser?.providerData?.any{it.providerId=="password"}==true)TextButton(onClick={value="";password="";action="Jelszó módosítása"}){Text("Jelszó módosítása")}
        AccountSettings()
        SettingsSection("Adatok és adatvédelem")
        TextButton(onClick={action="Mentett adatok"}){Text("Mentett adatok megtekintése")}
        TextButton(onClick={export.launch("attention-adatok.json")}){Text("Személyes adatok exportálása")}
        TextButton(onClick={action="Adatvédelem"}){Text("Adatvédelmi tájékoztató")}
        TextButton(onClick={password="";value="";action="Fiók végleges törlése"}){Text("Fiók és személyes adatok törlése",color=MaterialTheme.colorScheme.error)}
        SettingsSection("Alkalmazás")
        Text("Attention+ · $version",color=InkColor)
        TextButton(onClick={action="Névjegy"}){Text("Névjegy")}
        TextButton(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,"Attention+ hibabejelentés").putExtra(Intent.EXTRA_TEXT,"Attention+ $version · Android ${android.os.Build.VERSION.RELEASE}\n\nMi történt?\n\nHogyan ismételhető meg?\n"),"Hibabejelentés megosztása"))}){Text("Hibabejelentés")}
    }
    }
    if(message!=null)AlertDialog(onDismissRequest={message=null},title={PageHeading("Visszajelzés",{message=null})},text={Text(message!!)},confirmButton={TextButton(onClick={message=null}){Text("Rendben")}})
    val current=action
    if(current!=null)AlertDialog(onDismissRequest={if(!busy)action=null},title={PageHeading(current,{if(!busy)action=null})},text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        when(current) {
            "Mentett adatok"->Text(repository?.exportJson() ?: "Nincs bejelentkezett fiók.",fontSize=12.sp)
            "Adatvédelem"->Text("A fiókadataidat a Firebase Authentication, a profilodat, tanulásaidat és beállításaidat a Cloud Firestore tárolja. Saját képeid privát Cloud Storage tárhelyre kerülnek, ha a szolgáltatás elérhető; addig a készüléken várnak feltöltésre. Más fiók nem olvashatja személyes adataidat. Jelszavad nem kerül a tanulási adatbázisba. Az offline másolatok a készülék privát tárhelyén vannak. Adataidat exportálhatod és kérheted a teljes fióktörlést. A törlés után minimális technikai tiltójelzés marad a korábbi belépési tokenek kizárásához. Az AI-elemzés jelenleg helyi gyakorlófolyamat; a képeket nem küldjük AI-szolgáltatónak.")
            "Névjegy"->Text("Attention+\nTanulás, fókusz és személyes fejlődés.\nVerzió: $version\nA virtuális pontok nem válthatók pénzre. A napi széria UTC napok alapján számolódik.")
            else->{
                if(current=="Fiók végleges törlése")Text("Minden felhőben tárolt személyes adatod és fiókod véglegesen törlődik. A művelet nem vonható vissza. A megerősítéshez írd be: TÖRLÉS.")
                OutlinedTextField(value,{value=it},label={Text(when(current){"Emlékeztető időpontja"->"ÓÓ:PP";"Jelszó módosítása"->"Új jelszó (legalább 8 karakter)";"Fiók végleges törlése"->"TÖRLÉS";else->"Új e-mail-cím"})},singleLine=true,visualTransformation=if(current=="Jelszó módosítása")PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
                if(current!="Emlékeztető időpontja"){
                    if(auth.auth.currentUser?.providerData?.any{it.providerId=="password"}==true)OutlinedTextField(password,{password=it},label={Text("Jelenlegi jelszó")},singleLine=true,visualTransformation=PasswordVisualTransformation())
                    else Text("A művelet előtt a Google-fiókoddal újra meg kell erősítened személyazonosságodat.")
                }
            }
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    }},confirmButton={TextButton(enabled=!busy,onClick={
        if(current in listOf("Mentett adatok","Adatvédelem","Névjegy")){action=null;return@TextButton}
        scope.launch {busy=true;runCatching{
            if(current=="Emlékeztető időpontja"){val time=java.time.LocalTime.parse(value);save("reminderTime",time.toString());action=null}
            else {
                if(current=="Jelszó módosítása")require(value.length>=8){"A jelszó legalább 8 karakter legyen."}
                if(current=="E-mail-cím módosítása")require(android.util.Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches()){ "Érvényes e-mail-címet adj meg." }
                if(current=="Fiók végleges törlése")require(value=="TÖRLÉS"){"Írd be pontosan: TÖRLÉS."}
                auth.reauthenticate(password)
                val user=checkNotNull(auth.auth.currentUser);auth.auth.setLanguageCode("hu")
                when(current){
                    "Jelszó módosítása"->{user.updatePassword(value).awaitResult();message="A jelszó megváltozott."}
                    "E-mail-cím módosítása"->{user.verifyBeforeUpdateEmail(value.trim()).awaitResult();message="Elküldtük a megerősítő levelet az új címre. A benne lévő hivatkozással fejezd be a módosítást, majd jelentkezz be újra."}
                    "Fiók végleges törlése"->{
                        FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("deleteAccount").call().awaitResult()
                        auth.signOut();context.getSharedPreferences("account_${user.uid}",0).edit().clear().commit()
                        java.io.File(context.filesDir,"accounts/${user.uid}").deleteRecursively()
                        java.io.File(context.filesDir,"camera/${user.uid}").deleteRecursively()
                        context.getSharedPreferences("reminders_${user.uid}",0).edit().clear().commit()
                        if(context.getSharedPreferences("legacy_migration",0).getString("owner",null)==user.uid)context.getSharedPreferences("learning_progress",0).edit().clear().commit()
                    }
                };action=null
            }
        }.onFailure{message=friendlyCloudError(it)};password="";busy=false}
    }){Text(if(current=="Fiók végleges törlése")"Végleges törlés" else "Rendben")}})
}
@Composable private fun SettingsSection(text:String){Spacer(Modifier.height(14.dp));Text(text,color=InkColor,fontSize=19.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)}
@Composable private fun PreferenceSwitch(label:String,value:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),color=InkColor);Switch(value,onChange,Modifier.testTag("setting_$label"))}}
@Composable private fun PreferenceChoices(label:String,value:Int,choices:List<Int>,change:(Int)->Unit){var expanded by remember{mutableStateOf(false)};Column{Text(label,color=InkColor);Box{TextButton(onClick={expanded=true}){Text("$value perc")};DropdownMenu(expanded,{expanded=false}){choices.forEach{n->DropdownMenuItem(text={Text("$n perc")},onClick={change(n);expanded=false})}}}}}
internal fun friendlyCloudError(error:Throwable):String=when(error){
    is com.google.firebase.functions.FirebaseFunctionsException->when(error.code){
        com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND,com.google.firebase.functions.FirebaseFunctionsException.Code.UNAVAILABLE->"A művelethez szükséges felhőszolgáltatás jelenleg nem érhető el. A művelet eredménye nem igazolható. Újrapróbáláskor ugyanazt a kérést ellenőrizzük, így nincs dupla levonás."
        else->error.message?.takeUnless{it==it.uppercase()} ?: "A műveletet nem sikerült végrehajtani. Próbáld újra bejelentkezés után."
    }
    is IllegalArgumentException->error.message ?: "Ellenőrizd a megadott adatokat."
    is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException->"Hibás hitelesítési adatok."
    else->"A művelet nem sikerült. Ellenőrizd az internetkapcsolatot, majd próbáld újra."
}
