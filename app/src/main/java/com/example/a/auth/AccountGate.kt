package com.example.a.auth

import android.app.Activity
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.example.a.R
import com.example.a.data.AccountSession
import com.example.a.data.AccountRepository
import com.example.a.ui.APlusScreen
import com.example.a.ui.AccentColor
import com.example.a.ui.InkColor
import com.example.a.ui.PageColor
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*

@Composable
internal fun AccountGate() {
    val activity=checkNotNull(androidx.activity.compose.LocalActivity.current)
    val controller=remember { AccountAuth(activity) }
    var user by remember { mutableStateOf(controller.auth.currentUser) }
    var submitting by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val listener=FirebaseAuth.AuthStateListener { user=it.currentUser }
        controller.auth.addAuthStateListener(listener)
        onDispose { controller.auth.removeAuthStateListener(listener) }
    }
    val activeUser=user
    if(activeUser == null || submitting) {
        AuthScreen(controller,onBusy={submitting=it})
    } else key(activeUser.uid) {
        val scope=rememberCoroutineScope()
        var repository by remember { mutableStateOf<AccountRepository?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var attempt by remember { mutableIntStateOf(0) }
        LaunchedEffect(attempt) {
            error=null
            if(controller.auth.currentUser?.uid!=activeUser.uid) return@LaunchedEffect
            // Returning users can use their account cache without a network connection.
            val cached=activity.getSharedPreferences("account_${activeUser.uid}",0).getBoolean("initialized",false)
            if(!cached) runCatching { withTimeout(20_000) { controller.ensureProfile(null) } }.onFailure { error="A profil nem tölthető be. Ellenőrizd a hálózatot, majd próbáld újra."; return@LaunchedEffect }
            if(controller.auth.currentUser?.uid!=activeUser.uid) return@LaunchedEffect
            repository=AccountSession.open(activity,activeUser.uid)
        }
        DisposableEffect(Unit) { onDispose { if(AccountSession.repository?.uid==activeUser.uid) AccountSession.close() } }
        val repo=repository
        if(repo == null) ConnectionScreen(error, {attempt++}, {scope.launch{controller.signOut()}})
        else {
            val sync by repo.state.collectAsState()
            if(!sync.ready) ConnectionScreen(sync.error, { repo.retry(); attempt++ }, {scope.launch{controller.signOut()}})
            else APlusScreen()
        }
    }
}

@Composable
private fun ConnectionScreen(error: String?, retry: () -> Unit, signOut: () -> Unit) {
    Column(Modifier.fillMaxSize().background(PageColor).safeDrawingPadding().padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
        if(error==null) CircularProgressIndicator(color=AccentColor)
        Text(error ?: "Fiókadatok betöltése…",color=InkColor,textAlign=TextAlign.Center)
        if(error!=null) Button(onClick=retry){Text("Újrapróbálás")}
        TextButton(onClick=signOut){Text("Vissza a bejelentkezéshez")}
    }
}

@Composable
private fun AuthScreen(controller: AccountAuth, onBusy: (Boolean) -> Unit) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var identity by rememberSaveable { mutableStateOf("") }
    // Passwords are never placed in saved instance state, preferences or Firestore.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var birthday by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var phase by remember { mutableStateOf(SubmitPhase.Idle) }
    var google by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    val busy=phase!=SubmitPhase.Idle
    fun submit(useGoogle: Boolean) {
        if(busy) return
        scope.launch {
            google=useGoogle; error=null; onBusy(true); phase=SubmitPhase.Loading
            try {
                // Let the continuous morph reach its ring before resolving a fast request.
                coroutineScope {
                    val check=async { if(useGoogle) controller.google() else if(registering) controller.register(identity,password,confirmation,birthday) else controller.signIn(identity,password) }
                    delay(450); check.await()
                }
                phase=SubmitPhase.Ball; delay(260)
                phase=SubmitPhase.Success; delay(1250)
                onBusy(false)
            } catch(e: CancellationException) { onBusy(false); throw e }
            catch(e: Exception) { error=authError(e); phase=SubmitPhase.Idle; onBusy(false) }
        }
    }
    Column(Modifier.fillMaxSize().background(PageColor).safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=28.dp,vertical=32.dp).testTag("auth_screen"),horizontalAlignment=Alignment.CenterHorizontally) {
        if(registering) { androidx.activity.compose.BackHandler { registering=false }; com.example.a.ui.BackButton({registering=false},Modifier.align(Alignment.Start)); Spacer(Modifier.height(16.dp)) }
        Image(androidx.compose.ui.res.painterResource(R.drawable.attention_logo_transparent),"Attention+",Modifier.fillMaxWidth().aspectRatio(2159f/728f).padding(vertical=12.dp))
        Text(if(registering) "Regisztráció" else "Bejelentkezés",color=InkColor,fontSize=28.sp,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        AuthField(identity,{identity=it},if(registering) "E-mail-cím" else "Felhasználónév / e-mail-cím","auth_identity",busy)
        Spacer(Modifier.height(12.dp))
        AuthField(password,{password=it},"Jelszó","auth_password",busy,true)
        if(registering) {
            Spacer(Modifier.height(12.dp)); AuthField(confirmation,{confirmation=it},"Jelszó megerősítése","auth_confirm",busy,true)
            Spacer(Modifier.height(12.dp)); AuthField(birthday,{birthday=it},"Születési dátum (ÉÉÉÉ-HH-NN)","auth_birthday",busy)
        }
        Spacer(Modifier.height(24.dp))
        MorphAuthButton(if(registering) "Regisztráció" else "Bejelentkezés",if(!google)phase else SubmitPhase.Idle,busy,false,"auth_submit"){submit(false)}
        if(error!=null) Text(error!!,Modifier.padding(top=16.dp).testTag("auth_error"),color=Color(0xFFFFABAB),textAlign=TextAlign.Center)
        TextButton(onClick={registering=!registering; error=null; password=""; confirmation=""},enabled=!busy,modifier=Modifier.testTag("auth_switch")) {
            Text(if(registering) "Van már fiókja? Jelentkezzen be" else "Nincs még fiókja? Regisztráljon",fontSize=13.sp,textAlign=TextAlign.Center)
        }
        Text("Vagy",Modifier.padding(12.dp),color=Color(0xFFB9BAC4))
        MorphAuthButton("Folytatás Google-fiókkal",if(google)phase else SubmitPhase.Idle,busy,true,"auth_google"){submit(true)}
    }
}
private fun authError(e: Exception): String = when(e) {
    is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Hibás e-mail-cím/felhasználónév vagy jelszó."
    is com.google.firebase.auth.FirebaseAuthUserCollisionException -> "Ez az e-mail-cím már használatban van. Jelentkezz be."
    is com.google.firebase.FirebaseNetworkException -> "Nincs hálózati kapcsolat. A belépéshez internet szükséges."
    is androidx.credentials.exceptions.GetCredentialCancellationException -> "A Google-belépés megszakítva."
    is com.google.firebase.functions.FirebaseFunctionsException -> if(e.code==com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND || e.code==com.google.firebase.functions.FirebaseFunctionsException.Code.UNAVAILABLE) "A felhasználónéves belépés jelenleg nem érhető el. Használd az e-mail-címedet." else e.localizedMessage ?: "A bejelentkezés nem sikerült."
    is IllegalArgumentException -> e.localizedMessage ?: "Ellenőrizd a megadott adatokat."
    else -> "A művelet nem sikerült. Ellenőrizd az adatokat és a kapcsolatot, majd próbáld újra."
}
@Composable
private fun AuthField(value: String, change: (String)->Unit, label: String, tag: String, busy: Boolean, password: Boolean=false) {
    OutlinedTextField(value,change,Modifier.fillMaxWidth().testTag(tag),enabled=!busy,label={Text(label)},singleLine=true,
        visualTransformation=if(password)PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions=KeyboardOptions(keyboardType=if(password)KeyboardType.Password else KeyboardType.Text),
        colors=OutlinedTextFieldDefaults.colors(focusedTextColor=InkColor,unfocusedTextColor=InkColor,focusedBorderColor=AccentColor,unfocusedLabelColor=Color(0xFFB9BAC4)),shape=RoundedCornerShape(16.dp))
}
internal enum class SubmitPhase { Idle, Loading, Ball, Success }

@Composable
internal fun MorphAuthButton(text: String, phase: SubmitPhase, busy: Boolean, google: Boolean, tag: String, onClick: ()->Unit) {
    val reduced=androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("device_preferences",0).getBoolean("reducedMotion",false)
    val transition=updateTransition(phase,label="auth_morph")
    val width by transition.animateFloat(transitionSpec={if(reduced)snap() else spring(0.9f,550f)},label="width") { if(it==SubmitPhase.Loading)0f else if(it==SubmitPhase.Ball)-0.06f else 1f }
    val hole by transition.animateFloat(transitionSpec={if(reduced)snap() else spring(1f,650f)},label="hole") { if(it==SubmitPhase.Loading)1f else 0f }
    val color by transition.animateColor(transitionSpec={tween(if(reduced)0 else 500,easing=FastOutSlowInEasing)},label="color") { if(it==SubmitPhase.Success)Color(0xFF23A566) else if(google && it==SubmitPhase.Idle)Color.White else AccentColor }
    val rotation by rememberInfiniteTransition(label="spinner").animateFloat(0f,360f,infiniteRepeatable(tween(850,easing=LinearEasing)),label="angle")
    BoxWithConstraints(Modifier.fillMaxWidth().height(60.dp).testTag(tag),contentAlignment=Alignment.Center) {
        val currentWidth by transition.animateDp(transitionSpec={if(reduced)snap() else spring(0.9f,550f)},label="shape_width") { when(it){SubmitPhase.Loading->56.dp;SubmitPhase.Ball->32.dp;else->maxWidth} }
        val currentHeight by transition.animateDp(transitionSpec={if(reduced)snap() else spring(0.95f,600f)},label="shape_height") { if(it==SubmitPhase.Ball)32.dp else 56.dp }
        val labelAlpha by animateFloatAsState(if(phase==SubmitPhase.Idle || (phase==SubmitPhase.Success && currentWidth>=maxWidth-1.dp))1f else 0f,tween(if(reduced)0 else 180),label="label_after_morph")
        val successLabel=if(phase==SubmitPhase.Success) "Sikeres bejelentkezés!" else text
        Box(Modifier.width(currentWidth.coerceAtLeast(30.dp)).height(currentHeight).testTag("${tag}_shape")
            .clickable(enabled=!busy,role=androidx.compose.ui.semantics.Role.Button,onClick=onClick),contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val radius=16.dp.toPx()+(size.minDimension/2-16.dp.toPx())*(1-width.coerceIn(0f,1f))
                drawRoundRect(color.copy(alpha=1-hole),cornerRadius=CornerRadius(radius))
                if(hole>0f) drawArc(color.copy(alpha=hole),if(reduced)0f else rotation,285f,false,Offset(3.dp.toPx(),3.dp.toPx()),Size(size.width-6.dp.toPx(),size.height-6.dp.toPx()),style=Stroke(3.dp.toPx(),cap=StrokeCap.Round))
            }
            Row(Modifier.graphicsLayer{alpha=labelAlpha},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(google && phase==SubmitPhase.Idle) GoogleMark(Modifier.size(22.dp))
                Text(successLabel,color=if(google && phase==SubmitPhase.Idle) Color(0xFF303030) else Color.White,fontWeight=FontWeight.SemiBold,fontSize=16.sp,maxLines=1)
            }
        }
    }
}
@Composable
private fun GoogleMark(modifier: Modifier) {
    Image(androidx.compose.ui.res.painterResource(R.drawable.google_g),"Google",modifier)
}
