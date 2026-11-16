package com.example.a.ui

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a.R
import com.example.a.auth.AccountAuth
import com.example.a.data.AccountSession
import com.example.a.data.awaitResult
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.*
import java.net.URL
import javax.net.ssl.HttpsURLConnection

@Composable
internal fun AccountImage(path: String, modifier: Modifier = Modifier, contentScale:ContentScale=ContentScale.Crop, unavailableText:String?=null) {
    var loading by remember(path){mutableStateOf(true)}
    val repository=AccountSession.repository
    val image by produceState<ImageBitmap?>(null,path,repository?.uid) {
        value=null;loading=true
        if(path.isNotBlank()) value=withContext(Dispatchers.IO) { runCatching {
            val bitmap=if(path.startsWith("users/")) {
                val file=checkNotNull(repository).imageFile(path)
                BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply{inSampleSize=2})
            } else {
                val url=URL(path)
                require(url.protocol=="https" && (url.host=="googleusercontent.com" || url.host.endsWith(".googleusercontent.com")))
                val connection=url.openConnection() as HttpsURLConnection
                connection.connectTimeout=10000;connection.readTimeout=10000;connection.instanceFollowRedirects=false
                try { connection.inputStream.use { BitmapFactory.decodeStream(it) } } finally { connection.disconnect() }
            }
            bitmap?.asImageBitmap()
        }.getOrNull() };loading=false
    }
    if(image!=null) Image(image!!,"Profilkép / jegyzet",modifier,contentScale=contentScale)
    else if(unavailableText!=null)Box(modifier,contentAlignment=androidx.compose.ui.Alignment.Center){if(loading)CircularProgressIndicator() else Text(unavailableText,color=SecondaryInkColor)}
    else Icon(painterResource(R.drawable.ic_profile),"Profilkép",modifier,tint=SecondaryInkColor)
}
@Composable
internal fun AccountAvatar(photo: String, modifier: Modifier=Modifier) { AccountImage(photo,modifier.clip(CircleShape)) }

@Composable
internal fun ProfileAccountActions() {
    val repository=AccountSession.repository ?: return
    val sync by repository.state.collectAsState()
    val profile=remember(sync.revision){repository.profile()}
    val scope=rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri!=null)scope.launch{saving=true;runCatching{repository.updateProfile(photo=repository.importImage(uri))}.onFailure{error=friendlyCloudError(it)};saving=false}
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        TextButton(onClick={name=profile.optString("displayName");username=profile.optString("username");editing=true},enabled=!saving,modifier=Modifier.testTag("edit_profile")){Text("Profil szerkesztése")}
        TextButton(onClick={gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!saving,modifier=Modifier.testTag("edit_avatar")){Text("Profilkép")}
    }
    if(saving) LinearProgressIndicator(Modifier.fillMaxWidth(),color=AccentColor)
    if(error!=null)Text(error!!,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
    if(editing)AlertDialog(onDismissRequest={if(!saving)editing=false},title={PageHeading("Profil szerkesztése",{if(!saving)editing=false})},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        OutlinedTextField(name,{name=it.take(80)},label={Text("Megjelenő név")},singleLine=true)
        OutlinedTextField(username,{username=it.take(24)},label={Text("Bejelentkezési név (opcionális)")},singleLine=true)
        Text("A bejelentkezési név 3–24 kisbetű, szám vagy aláhúzás lehet. Módosításához internet szükséges.",fontSize=12.sp)
        if(error!=null) Text(error!!,color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(enabled=!saving&&name.isNotBlank(),onClick={scope.launch{
        saving=true;error=null
        runCatching {
            if(username.isNotBlank() && username!=profile.optString("username")) FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("changeUsername").call(mapOf("username" to username)).awaitResult()
            repository.updateProfile(name=name);editing=false
        }.onFailure{error=friendlyCloudError(it)}
        saving=false
    }}){Text("Mentés")}},dismissButton={TextButton(onClick={editing=false},enabled=!saving){Text("Mégse")}})
}

@Composable
internal fun AccountSettings() {
    val repository=AccountSession.repository ?: return
    val state by repository.state.collectAsState()
    val scope=rememberCoroutineScope()
    val activity=checkNotNull(androidx.activity.compose.LocalActivity.current)
    Spacer(Modifier.height(28.dp))
    Text(when {state.error!=null->state.error!!;state.offline->"Offline · a változások helyben mentve";state.pending>0->"Szinkronizálás · ${state.pending} függő módosítás";state.awaitingReview>0->"Előzmények mentve · ${state.awaitingReview} tanulás jóváhagyásra vár";else->"Firebase · szinkronizálva"},color=SecondaryInkColor,fontSize=13.sp)
    if(state.error!=null)TextButton(onClick={repository.retry()}){Text("Szinkronizálás újrapróbálása")}
    Spacer(Modifier.height(20.dp))
    DepthButton("Kijelentkezés",AccentColor,{scope.launch{AccountAuth(activity).signOut()}},Modifier.fillMaxWidth().height(56.dp).testTag("sign_out"))
}
