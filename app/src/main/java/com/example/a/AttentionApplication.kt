package com.example.a

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage

class AttentionApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        val developer=getSharedPreferences("developer",0)
        val local=BuildConfig.DEBUG && developer.getBoolean("firebaseEmulators",false)
        if(!local) installAppCheck()
        // Explicit local developer switch; ignored in release APKs. Never substitutes fake authentication.
        if(local) {
            val host=developer.getString("emulatorHost","10.0.2.2")!!
            FirebaseAuth.getInstance().useEmulator(host,9099)
            FirebaseFirestore.getInstance().useEmulator(host,8080)
            FirebaseStorage.getInstance().useEmulator(host,9199)
            FirebaseFunctions.getInstance("europe-west1").useEmulator(host,5001)
        }
    }
}
