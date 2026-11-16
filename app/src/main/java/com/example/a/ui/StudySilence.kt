package com.example.a.ui

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/** Own rule only: never changes or restores the user's global DND filter. */
@Composable internal fun StudySilence(focusRunning:Boolean,focusEnd:Long) {
    val context=LocalContext.current
    val preferences=LocalPreferences.current
    val enabled=preferences.optBoolean("studySilence",true)
    val manager=remember{context.getSystemService(NotificationManager::class.java)}
    val local=remember{context.getSharedPreferences("study_silence",0)}
    var explain by remember{mutableStateOf(false)}
    var failure by remember{mutableStateOf(false)}
    var scheduledEnd by remember{mutableLongStateOf(0L)}
    var informed by remember{mutableStateOf(false)}
    fun release(){
        val id=local.getString("rule",null) ?: return
        if(runCatching{manager.removeAutomaticZenRule(id)}.getOrDefault(false))local.edit().remove("rule").commit()
    }
    DisposableEffect(Unit){onDispose{release()}}
    LaunchedEffect(enabled,focusRunning,focusEnd){
        while(true){
            val session=LearningStore(context).read().optJSONObject("session")
            val learning=session?.optString("stage")=="timer" && (session.optBoolean("paused") || session.optLong("end")>System.currentTimeMillis())
            val active=enabled && (focusRunning || learning)
            if(!active){release();informed=false}
            else if(!manager.isNotificationPolicyAccessGranted){if(!informed){explain=true;informed=true}}
            else if(local.getString("rule",null)==null){
                runCatching {
                    val uri=Uri.parse("condition://${context.packageName}/study")
                    val policy=ZenPolicy.Builder().disallowAllSounds().allowAlarms(true).allowMedia(true).allowSystem(true).build()
                    val rule=AutomaticZenRule("Attention+ tanulás",null,ComponentName(context,com.example.a.MainActivity::class.java),uri,policy,NotificationManager.INTERRUPTION_FILTER_PRIORITY,true)
                    val id=manager.addAutomaticZenRule(rule)
                    local.edit().putString("rule",id).commit()
                    manager.setAutomaticZenRuleState(id,Condition(uri,"Tanulás",Condition.STATE_TRUE))
                }.onFailure{failure=true}
            }
            val end=maxOf(if(focusRunning)focusEnd else 0L,if(learning && !session!!.optBoolean("paused"))session.optLong("end") else 0L)
            if(active && end>System.currentTimeMillis() && end!=scheduledEnd){
                val pending=android.app.PendingIntent.getBroadcast(context,81,Intent(context,SilenceExpiry::class.java),android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                context.getSystemService(android.app.AlarmManager::class.java).setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP,end,pending)
                scheduledEnd=end
            }
            delay(500)
        }
    }
    if(explain)AlertDialog(onDismissRequest={explain=false},title={Text("Zavartalan tanulás")},text={Text("Az automatikus némításhoz engedélyezd az Attention+ Ne zavarjanak hozzáférését. Az ébresztők megmaradnak. Engedély nélkül a tanulás folytatható, de az értesítéseket nem némítjuk.")},confirmButton={TextButton(onClick={explain=false;runCatching{context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))}.onFailure{failure=true}}){Text("Rendszerbeállítások")}},dismissButton={TextButton(onClick={explain=false}){Text("Most nem")}})
    if(failure)AlertDialog(onDismissRequest={failure=false},title={Text("A némítás nem sikerült")},text={Text("Ellenőrizd a Ne zavarjanak hozzáférést a rendszerbeállításokban. A tanulást folytathatod.")},confirmButton={TextButton(onClick={failure=false}){Text("Rendben")}})
}

/** Also releases our rule if the process was reclaimed while the timer ran. */
class SilenceExpiry:android.content.BroadcastReceiver(){
    override fun onReceive(context:android.content.Context,intent:Intent){
        val prefs=context.getSharedPreferences("study_silence",0)
        val id=prefs.getString("rule",null) ?: return
        runCatching{context.getSystemService(NotificationManager::class.java).removeAutomaticZenRule(id)}.onSuccess{prefs.edit().remove("rule").commit()}
    }
}
