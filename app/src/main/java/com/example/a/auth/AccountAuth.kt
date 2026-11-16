package com.example.a.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.example.a.data.AccountSession
import com.example.a.R
import com.example.a.data.awaitResult
import java.time.LocalDate

internal class AccountAuth(private val activity: Activity) {
    val auth = FirebaseAuth.getInstance()
    suspend fun signIn(identity: String, password: String) {
        require(identity.isNotBlank() && password.isNotBlank()) { "Add meg az e-mail-címed/felhasználóneved és a jelszavad." }
        if(identity.contains('@')) auth.signInWithEmailAndPassword(identity.trim(),password).awaitResult()
        else {
            val result=FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("loginWithUsername")
                .call(mapOf("username" to identity.trim(),"password" to password)).awaitResult().data as Map<*,*>
            auth.signInWithCustomToken(result["token"] as String).awaitResult()
        }
        ensureProfile(null)
    }
    suspend fun register(email: String, password: String, confirmation: String, birthday: String) {
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Adj meg érvényes e-mail-címet." }
        require(password.length >= 8) { "A jelszó legalább 8 karakter legyen." }
        require(password == confirmation) { "A két jelszó nem egyezik." }
        val date=runCatching { LocalDate.parse(birthday) }.getOrNull()
        require(date != null && !date.isAfter(LocalDate.now()) && date.year >= 1900) { "Érvényes születési dátum szükséges (ÉÉÉÉ-HH-NN)." }
        auth.createUserWithEmailAndPassword(email.trim(),password).awaitResult()
        activity.getSharedPreferences("registration_${auth.currentUser!!.uid}",0).edit().putString("birthday",birthday).commit()
        ensureProfile(birthday)
    }
    suspend fun google() {
        val option=GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()
        val result=try { CredentialManager.create(activity).getCredential(activity,GetCredentialRequest.Builder().addCredentialOption(option).build()) }
        catch(e: NoCredentialException) { throw IllegalStateException("Nem található használható Google-fiók. Adj hozzá egyet a telefon beállításaiban, vagy lépj be e-maillel.",e) }
        val credential=GoogleIdTokenCredential.createFrom(result.credential.data)
        auth.signInWithCredential(GoogleAuthProvider.getCredential(credential.idToken,null)).awaitResult()
        ensureProfile(null)
    }
    suspend fun ensureProfile(birthday: String?) {
        val user=checkNotNull(auth.currentUser)
        val ref=FirebaseFirestore.getInstance().collection("users").document(user.uid).collection("profile").document("main")
        FirebaseFirestore.getInstance().runTransaction { transaction ->
            val existing=transaction.get(ref)
            if(existing.exists() && existing.getString("email")!=user.email) transaction.update(ref,"email",user.email.orEmpty())
            if(!existing.exists()) transaction.set(ref,mapOf(
                "email" to user.email.orEmpty(), "displayName" to user.email.orEmpty(),
                "photo" to user.photoUrl?.toString().orEmpty(), "birthday" to (birthday ?: activity.getSharedPreferences("registration_${user.uid}",0).getString("birthday","")).orEmpty(),
                "username" to "", "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            ))
        }.awaitResult()
    }
    suspend fun reauthenticate(password:String) {
        val user=checkNotNull(auth.currentUser)
        if(user.providerData.any{it.providerId=="password"}) {
            require(password.isNotBlank()){ "Add meg a jelenlegi jelszavad." }
            user.reauthenticate(com.google.firebase.auth.EmailAuthProvider.getCredential(user.email!!,password)).awaitResult()
        } else {
            val option=GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()
            val response=CredentialManager.create(activity).getCredential(activity,GetCredentialRequest.Builder().addCredentialOption(option).build())
            val token=GoogleIdTokenCredential.createFrom(response.credential.data).idToken
            user.reauthenticate(GoogleAuthProvider.getCredential(token,null)).awaitResult()
        }
    }
    suspend fun signOut() {
        auth.currentUser?.let { com.example.a.ui.StudyReminder.schedule(activity,it.uid,false) }

        AccountSession.close()
        auth.signOut()
        runCatching { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) }
    }
}
