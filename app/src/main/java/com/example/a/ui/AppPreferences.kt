package com.example.a.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.ToneGenerator
import android.media.AudioManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.a.MainActivity
import com.example.a.R
import com.example.a.data.AccountSession
import com.example.a.data.AccountRepository
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

internal val LocalPreferences=staticCompositionLocalOf { JSONObject() }
internal val LocalReducedMotion=staticCompositionLocalOf { false }
@Composable internal fun AppPreferencesHost(content:@Composable ()->Unit) {
    val context=LocalContext.current
    val repository=AccountSession.repository
    val sync=repository?.state?.collectAsState()?.value
    val preferences=remember(sync?.revision){repository?.record("preferences","app") ?: JSONObject()}
    val reduced=preferences.optBoolean("reducedMotion",false)
    LaunchedEffect(preferences.toString()) {
        context.getSharedPreferences("device_preferences",0).edit().putBoolean("reducedMotion",reduced).apply()
        repository?.let { StudyReminder.schedule(context,it.uid,preferences.optBoolean("notifications")&&preferences.optBoolean("reminders")) }
    }
    CompositionLocalProvider(LocalPreferences provides preferences,LocalReducedMotion provides reduced,content=content)
}
internal fun studyFeedback(context:Context,preferences:JSONObject) {
    if(preferences.optBoolean("sounds",true)) {
        val tone=ToneGenerator(AudioManager.STREAM_NOTIFICATION,45)
        tone.startTone(ToneGenerator.TONE_PROP_ACK,130)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({tone.release()},250)
    }
    if(preferences.optBoolean("vibration",true)) {
        val vibrator=context.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
        if(vibrator.hasVibrator()) vibrator.vibrate(android.os.VibrationEffect.createOneShot(45,android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
class StudyReminder(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val uid=inputData.getString("uid") ?: return Result.success()
        if(FirebaseAuth.getInstance().currentUser?.uid!=uid)return Result.success()
        val repo=AccountRepository(applicationContext,uid)
        try {
            val p=repo.record("preferences","app")
            if(!p.optBoolean("notifications")||!p.optBoolean("reminders"))return Result.success()
            val time=runCatching{LocalTime.parse(p.optString("reminderTime","18:00"))}.getOrDefault(LocalTime.of(18,0))
            val today=LocalDate.now().toString();val local=applicationContext.getSharedPreferences("reminders_$uid",0)
            if(LocalTime.now()<time || local.getString("lastDay","")==today)return Result.success()
            val history=repo.learningState().optJSONArray("completed")
            val minutes=(0 until (history?.length() ?: 0)).map{history!!.getJSONObject(it)}.filter{it.optString("date")==today}.sumOf{it.optInt("durationSeconds")}/60
            if(minutes>=p.optInt("dailyGoalMinutes",30))return Result.success()
            val manager=applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("study_reminders","Tanulási emlékeztetők",NotificationManager.IMPORTANCE_DEFAULT))
            if(androidx.core.content.ContextCompat.checkSelfPermission(applicationContext,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return Result.success()
            val intent=PendingIntent.getActivity(applicationContext,0,Intent(applicationContext,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationManagerCompat.from(applicationContext).notify(210,NotificationCompat.Builder(applicationContext,"study_reminders").setSmallIcon(R.drawable.ic_timer).setContentTitle("Ideje tanulni").setContentText("A mai célod még ${p.optInt("dailyGoalMinutes",30)-minutes} perc tanulás.").setContentIntent(intent).setAutoCancel(true).build())
            local.edit().putString("lastDay",today).commit()
            return Result.success()
        }finally {repo.close()}
    }
    companion object {
        fun schedule(context:Context,uid:String,enabled:Boolean) {
            val manager=WorkManager.getInstance(context);val name="study-reminder-$uid"
            if(!enabled){manager.cancelUniqueWork(name);NotificationManagerCompat.from(context).cancel(210);return}
            manager.enqueueUniquePeriodicWork(name,ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<StudyReminder>(15,TimeUnit.MINUTES).setInputData(workDataOf("uid" to uid)).build())
        }
    }
}
