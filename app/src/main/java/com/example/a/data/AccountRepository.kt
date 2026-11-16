package com.example.a.data

import android.content.Context
import android.net.Uri
import androidx.work.*
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { if(c.isActive) c.resume(it) }
    addOnFailureListener { if(c.isActive) c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}

internal data class SyncState(val ready: Boolean = false, val pending: Int = 0, val awaitingReview: Int = 0, val offline: Boolean = false, val error: String? = null, val revision: Long = 0)

/** One account-scoped durable journal, a Firestore live projection, and a retryable attachment outbox. */
internal class AccountRepository(val context: Context, val uid: String) {
    private val prefs = context.getSharedPreferences("account_$uid", Context.MODE_PRIVATE)
    private val lock = locks.getOrPut(uid) { Any() }
    private val flushLock = flushLocks.getOrPut(uid) { Mutex() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db get() = FirebaseFirestore.getInstance()
    private val root get() = db.collection("users").document(uid)
    private val listeners = mutableListOf<ListenerRegistration>()
    private val events = JSONObject(prefs.getString("events", "{}")!!)
    private var projection = RecordProjection.reduce(events.objects())
    private var profile = JSONObject(prefs.getString("profile", "{}")!!)
    private var receipts = JSONObject(prefs.getString("receipts", "{}")!!)
    private var inventory = JSONObject(prefs.getString("inventory", "{}")!!)
    private var challenges = JSONObject(prefs.getString("challenges", "{}")!!)
    private var unlocks = JSONObject(prefs.getString("unlocks", "{}")!!)
    private var rejected = JSONObject(prefs.getString("rejectedCompletions", "{}")!!)
    private var server = JSONObject(prefs.getString("server", "{}")!!)
    private var legacy = JSONObject(prefs.getString("legacy", "{}")!!)
    private val mutableState = MutableStateFlow(SyncState(ready = prefs.getBoolean("initialized", false)))
    val state = mutableState.asStateFlow()
    @Volatile private var closed = false
    private fun authorized() = !closed && FirebaseAuth.getInstance().currentUser?.uid == uid
    private fun signal(error: String? = mutableState.value.error) {
        val unconfirmed=(records("sessions").filter { it.optString("stage")=="results" && !it.optBoolean("legacy") } + records("focus").filter { it.optBoolean("completed") })
            .count { !receipts.has(it.optString("id")) && !rejected.has(it.optString("id")) }
        mutableState.value = mutableState.value.copy(pending = pending().length() + uploads().length() + JSONObject(prefs.getString("profilePending","{}")!!).length(), awaitingReview=unconfirmed, error = error, revision = mutableState.value.revision + 1)
    }
    private fun pending() = JSONObject(prefs.getString("pending", "{}")!!)
    private fun uploads() = JSONObject(prefs.getString("uploads", "{}")!!)
    private fun commit(vararg fields: Pair<String, String>) {
        check(prefs.edit().apply { fields.forEach { putString(it.first, it.second) } }.commit()) { "Nem sikerült a helyi mentés. Próbáld újra." }
    }
    fun record(bucket: String, id: String): JSONObject = synchronized(lock) { if(authorized()) projection.optJSONObject(bucket)?.optJSONObject(id)?.copyJson() ?: JSONObject() else JSONObject() }
    fun records(bucket: String): List<JSONObject> = synchronized(lock) { if(authorized()) projection.optJSONObject(bucket)?.objects()?.map { it.copyJson() }.orEmpty() else emptyList() }
    fun profile(): JSONObject = synchronized(lock) { if(authorized()) profile.copyJson() else JSONObject() }
    fun inventory(): List<JSONObject> = synchronized(lock) { if(authorized()) inventory.objects().map { it.copyJson() } else emptyList() }
    fun challenges(): List<JSONObject> = synchronized(lock) { if(authorized()) challenges.objects().map { it.copyJson() } else emptyList() }
    fun serverRecords(collection: String): List<JSONObject> = synchronized(lock) { if(authorized()) server.optJSONObject(collection)?.objects()?.map{it.copyJson()}.orEmpty() else emptyList() }
    fun unlocks(): List<JSONObject> = synchronized(lock) { if(authorized()) unlocks.objects().map { it.copyJson() } else emptyList() }

    fun write(bucket: String, id: String, value: JSONObject) = synchronized(lock) {
        check(authorized()) { "Jelentkezz be újra a mentéshez." }
        require(bucket in setOf("sessions", "drafts", "focus", "preferences", "challenges"))
        require(id.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        val current = record(bucket, id)
        val patch = JSONObject()
        value.keys().forEach { key -> if(current.opt(key)?.toString() != value.opt(key)?.toString()) patch.put(key, value.get(key)) }
        if (patch.length() == 0) return@synchronized
        require(patch.toString().length <= 100_000) { "A jegyzet túl hosszú (legfeljebb 50 000 karakter)." }
        val clock = maxOf(prefs.getLong("clock", 0), events.objects().maxOfOrNull { it.optLong("clock") } ?: 0) + 1
        val eventId = UUID.randomUUID().toString()
        val event = JSONObject().put("id", eventId).put("bucket", bucket).put("entity", id).put("clock", clock).put("patch", patch)
        val nextEvents = events.copyJson().put(eventId, event)
        val nextPending = pending().put(eventId, event)
        commit("events" to nextEvents.toString(), "pending" to nextPending.toString())
        events.put(eventId, event)
        projection = RecordProjection.reduce(events.objects())
        signal()
        schedule()
    }
    fun start() {
        check(authorized())
        listeners += root.collection("changes").addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if(!authorized()) return@addSnapshotListener
            synchronized(lock) {
                if(error != null) { signal("Felhőszinkronizálási hiba. A helyi adatok megmaradtak."); return@addSnapshotListener }
                snap ?: return@addSnapshotListener
                snap.documents.forEach { doc -> doc.data?.let { events.put(doc.id, cloudJson(it)) } }
                commit("events" to events.toString())
                projection = RecordProjection.reduce(events.objects())
                val initialized = !snap.metadata.isFromCache || prefs.getBoolean("initialized", false)
                if(initialized) prefs.edit().putBoolean("initialized", true).commit()
                mutableState.value = mutableState.value.copy(ready = initialized, offline = snap.metadata.isFromCache)
                signal()
            }
        }
        listeners += root.collection("profile").document("main").addSnapshotListener { doc, error ->
            if(!authorized()) return@addSnapshotListener
            synchronized(lock) {
                if(error != null) signal("A profil nem tölthető be. Próbáld újra a bejelentkezést.")
                else if(doc?.exists() == true) { profile = cloudJson(doc.data!!); val local=JSONObject(prefs.getString("profilePending","{}")!!); local.keys().forEach { profile.put(it,local.get(it)) }; commit("profile" to profile.toString()); signal() }
            }
        }
        listOf("receipts", "inventory", "unlocks", "legacy", "challenges", "rejectedCompletions", "wallet", "boosts", "stats", "operations", "consumptions", "boostHistory").forEach { collection ->
            listeners += root.collection(collection).addSnapshotListener { snap, error ->
                if(!authorized()) return@addSnapshotListener
                synchronized(lock) {
                    if(error != null) signal("Egyes adatok szinkronizálása nem sikerült. Próbáld újra.")
                    else if(snap != null) {
                        val data = JSONObject(); snap.documents.forEach { d -> d.data?.let { data.put(d.id, cloudJson(it)) } }
                        when(collection) { "receipts" -> receipts = data; "inventory" -> inventory = data; "unlocks" -> unlocks = data; "challenges" -> challenges = data; "rejectedCompletions" -> rejected = data; "legacy" -> legacy = data; else -> {server.put(collection,data);commit("server" to server.toString())} }
                        commit(collection to data.toString()); signal(if(rejected.length()>0) "Egy tanulás jutalmát a szerver elutasította. Az előzmény megmaradt." else mutableState.value.error)
                    }
                }
            }
        }
        scope.launch { runCatching { flush() }.onFailure { synchronized(lock) { signal("A felhőszinkronizálás nem sikerült. A helyi mentés megmaradt.") } } }
        scope.launch { runCatching { com.google.firebase.functions.FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("refreshStreak").call().awaitResult() } }
        schedule()
    }
    fun close() { closed = true; listeners.forEach { it.remove() }; listeners.clear(); scope.cancel() }
    fun retry() { mutableState.value=mutableState.value.copy(error=null); schedule(); scope.launch { runCatching { flush() }.onFailure { synchronized(lock) { signal("A felhőszinkronizálás nem sikerült. A helyi mentés megmaradt.") } } } }
    private fun schedule() {
        val request = OneTimeWorkRequestBuilder<AccountSyncWorker>().setInputData(workDataOf("uid" to uid))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("account-sync-$uid", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        scope.launch { runCatching { flush() }.onFailure { synchronized(lock) { signal("A mentés helyben megvan; a felhőszinkronizálás újrapróbálható.") } } }
    }
    suspend fun flush() = flushLock.withLock {
        if(!authorized()) return@withLock
        records("focus").filter { it.optBoolean("running") && !it.optBoolean("completed") && it.optLong("end")>0 && System.currentTimeMillis()>=it.optLong("end") }.forEach { focus ->
            write("focus",focus.getString("id"),JSONObject().put("completed",true).put("running",false).put("finishedAt",focus.optLong("end")).put("studiedSeconds",focus.optInt("limit",focus.optInt("duration"))))
        }
        val profilePatch=synchronized(lock){JSONObject(prefs.getString("profilePending","{}")!!)}
        if(profilePatch.length()>0) {
            if(!authorized()) return@withLock
            root.collection("profile").document("main").set(profilePatch.toMapValue(),SetOptions.merge()).awaitResult()
            synchronized(lock) {
                val remaining=JSONObject(prefs.getString("profilePending","{}")!!)
                profilePatch.keys().forEach { if(remaining.opt(it)?.toString()==profilePatch.opt(it)?.toString()) remaining.remove(it) }
                commit("profilePending" to remaining.toString()); signal()
            }
        }
        val queued = synchronized(lock) { pending().objects().map { it.copyJson() } }
        for(event in queued) {
            if(!authorized()) return@withLock
            val ref = root.collection("changes").document(event.getString("id"))
            try { ref.set(event.toMapValue()).awaitResult() }
            catch(e: Exception) {
                // A retry after a process death may find an already-committed immutable event.
                val remote = ref.get(Source.SERVER).awaitResult()
                if(!remote.exists() || JSONObject(remote.data!!).toMapValue() != event.toMapValue()) throw e
            }
            synchronized(lock) { val remaining = pending(); remaining.remove(event.getString("id")); commit("pending" to remaining.toString()); signal() }
        }
        // Completion IDs are session IDs, not random retry IDs. The server awards each only once.
        (records("sessions").filter { it.optString("stage") == "results" && !it.optBoolean("legacy") } + records("focus").filter { it.optBoolean("completed") }).forEach { session ->
            if(!authorized()) return@withLock
            val id = session.getString("id")
            if(!prefs.getBoolean("submitted_$id", false)) {
                val focus = session.optString("kind") == "focus"
                val completion = JSONObject().put("kind", if(focus) "focus" else "study")
                    .put("subject", session.optString("subject", "History"))
                    .put("startedAt", session.optLong("startedAt"))
                    .put("finishedAt", session.optLong("finishedAt"))
                    .put("durationSeconds", session.optInt("studiedSeconds").coerceIn(0, 86400))
                    .put("answers", if(focus) JSONArray() else session.optJSONArray("answers") ?: JSONArray())
                    .put("quizVersion", "history-v1")
                val ref = root.collection("completions").document(id)
                try { ref.set(completion.toMapValue()).awaitResult() }
                catch(e: Exception) { if(!ref.get(Source.SERVER).awaitResult().exists()) throw e }
                prefs.edit().putBoolean("submitted_$id", true).commit()
            }
        }
        migrateLegacy()
        // A Storage outage must not block notes, timers, history or migration in Firestore.
        val queuedUploads = synchronized(lock) { uploads().copyJson() }
        var uploadError: Exception? = null
        queuedUploads.keys().forEach { id ->
            if(!authorized()) return@withLock
            try {
                val item = queuedUploads.getJSONObject(id)
                val file = File(context.filesDir, "accounts/$uid/media/$id")
                check(file.exists()) { "Hiányzó helyi melléklet: $id" }
                val task = FirebaseStorage.getInstance().reference.child("users/$uid/media/$id")
                    .putFile(Uri.fromFile(file), com.google.firebase.storage.StorageMetadata.Builder().setContentType(item.getString("mime")).build())
                try { withTimeout(25_000) { task.awaitResult() } }
                catch(e: Exception) { task.cancel(); throw e }
                synchronized(lock) { val remaining = uploads(); remaining.remove(id); commit("uploads" to remaining.toString()); signal() }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { uploadError = e }
        }
        uploadError?.let { throw it }
    }
    /** Import to a private unverified archive. Never convert untrusted legacy XP into spendable rewards. */
    private suspend fun migrateLegacy() {
        if(!authorized()) return
        val original = context.getSharedPreferences("learning_progress", 0).getString("state", null) ?: return
        val migration = context.getSharedPreferences("legacy_migration", 0)
        synchronized(migrationLock) {
            val owner = migration.getString("owner", null)
            if(owner != null && owner != uid) return
            if(owner == null) check(migration.edit().putString("owner", uid).commit())
        }
        val id = migration.getString("id", null) ?: java.security.MessageDigest.getInstance("SHA-256").digest(original.toByteArray()).joinToString(""){"%02x".format(it)}.also { check(migration.edit().putString("id", it).commit()) }
        if(migration.getBoolean("confirmed", false)) return
        val payload = mapOf("source" to "local-v1", "json" to original)
        val ref = root.collection("legacy").document(id)
        try { ref.set(payload).awaitResult() } catch(e: Exception) { if(!ref.get(Source.SERVER).awaitResult().exists()) throw e }
        val confirmed = ref.get(Source.SERVER).awaitResult().getString("json") == original
        if(confirmed) {
            // Keep the source forever; only mark acknowledgement after a server read.
            val oldSession = JSONObject(original).optJSONObject("session")
            if(oldSession != null && record("sessions", oldSession.getString("id")).length() == 0) {
                oldSession.put("startedAt", System.currentTimeMillis()-oldSession.optInt("duration",oldSession.optInt("remaining")).coerceIn(0,86400)*1000L).put("subject", "History")
                if(oldSession.optString("stage")=="results") oldSession.put("legacy",true).put("closed",true)
                write("sessions", oldSession.getString("id"), oldSession)
            }
            check(migration.edit().putBoolean("confirmed", true).commit())
        }
    }
    suspend fun importImage(uri: Uri): String = withContext(Dispatchers.IO) {
        check(authorized())
        val id = UUID.randomUUID().toString()
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        require(mime in setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")) { "Nem támogatott képtípus." }
        val file = File(context.filesDir, "accounts/$uid/media/$id").also { it.parentFile!!.mkdirs() }
        try {
            context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(8192); var total = 0
                while(true) { val n = input.read(buffer); if(n < 0) break; total += n; require(total <= 20 * 1024 * 1024) { "A kép legfeljebb 20 MB lehet." }; output.write(buffer, 0, n) }
            } }
            synchronized(lock) { val outbox = uploads().put(id, JSONObject().put("mime", mime).put("bytes",file.length())); commit("uploads" to outbox.toString(),"mediaMetadata" to JSONObject(prefs.getString("mediaMetadata","{}")!!).put(id,outbox.getJSONObject(id)).toString()) }
            schedule()
            "users/$uid/media/$id"
        } catch(e: Exception) { file.delete(); throw e }
    }
    fun imageMetadata(paths:String):JSONArray = synchronized(lock) {
        JSONArray().apply{paths.split('\n').filter{it.startsWith("users/$uid/media/")}.forEach{path->
            val id=path.substringAfterLast('/')
            val metadata=JSONObject(prefs.getString("mediaMetadata","{}")!!).optJSONObject(id) ?: JSONObject()
            put(JSONObject().put("path",path).put("mime",metadata.optString("mime","image/*")).put("bytes",metadata.optLong("bytes")))
        }}
    }
    suspend fun imageFile(path: String): File = withContext(Dispatchers.IO) {
        check(authorized())
        require(path.startsWith("users/$uid/media/") && path.substringAfterLast('/').matches(Regex("[a-f0-9-]{36}")))
        val file = File(context.filesDir, "accounts/$uid/media/${path.substringAfterLast('/')}")
        if(!file.exists()) {
            file.parentFile!!.mkdirs()
            val temporary=File(file.parentFile,"${file.name}.${UUID.randomUUID()}.download")
            try {
                FirebaseStorage.getInstance().reference.child(path).getFile(temporary).awaitResult()
                check(authorized())
                if(!file.exists()) check(temporary.renameTo(file))
            } finally { temporary.delete() }
        }
        check(authorized())
        file
    }
    suspend fun updateProfile(name: String? = null, photo: String? = null) {
        check(authorized())
        val patch = mutableMapOf<String, Any>()
        name?.let { require(it.trim().length in 1..80); patch["displayName"] = it.trim() }
        photo?.let { require(it.startsWith("users/$uid/media/")); patch["photo"] = it }
        synchronized(lock) {
            val pending=JSONObject(prefs.getString("profilePending","{}")!!)
            patch.forEach { (k,v) -> profile.put(k,v); pending.put(k,v) }
            commit("profile" to profile.toString(),"profilePending" to pending.toString()); signal()
        }
        schedule()
    }
    fun learningState(): JSONObject = synchronized(lock) {
        if(!authorized()) return@synchronized JSONObject()
        val completed = JSONArray()
        val seen = mutableSetOf<String>()
        var xp = 0; var points = 0; var seconds = 0
        val dates = mutableSetOf<LocalDate>()
        receipts.keys().forEach { id -> val receipt = receipts.getJSONObject(id); seen += id; points += receipt.optInt("score"); xp += receipt.optInt("xp",receipt.optInt("score")); seconds += receipt.optInt("durationSeconds"); completed.put((record(if(receipt.optString("kind")=="focus") "focus" else "sessions",id)).apply{receipt.keys().forEach{key->receipt.opt(key)?.let{put(key,it)}}}.put("id",id)); runCatching { dates += LocalDate.parse(receipt.getString("date")) } }
        records("sessions").filter { it.optString("stage") == "results" && it.optString("id") !in seen && !it.optBoolean("legacy") && !rejected.has(it.optString("id")) }.forEach { s ->
            val a=s.optJSONArray("answers") ?: JSONArray(); val correct=listOf(1,2,3,0,1,2,3,0,1,2)
            val score=(0 until minOf(a.length(),10)).count { a.optInt(it)==correct[it] }*100
            xp += score; points += score; seconds += s.optInt("studiedSeconds")
            val day=s.optString("date",LocalDate.now().toString()); runCatching { dates += LocalDate.parse(day) }
            completed.put(s.copyJson().put("id",s.getString("id")).put("subject",s.optString("subject")).put("date",day).put("score",score).put("pending",true).put("durationSeconds",s.optInt("studiedSeconds")))
        }
        records("focus").filter { it.optBoolean("completed") && it.optString("id") !in seen && !rejected.has(it.optString("id")) }.forEach { f ->
            seconds += f.optInt("studiedSeconds")
            val day=java.time.Instant.ofEpochMilli(f.optLong("finishedAt")).atZone(java.time.ZoneOffset.UTC).toLocalDate()
            dates += day
            completed.put(JSONObject().put("id",f.getString("id")).put("kind","focus").put("subject","Focus").put("date",day.toString()).put("score",0).put("pending",true).put("durationSeconds",f.optInt("studiedSeconds")))
        }
        var legacyXp = 0
        legacy.objects().forEach { archive -> runCatching {
            val old=JSONObject(archive.getString("json")); legacyXp += old.optInt("xp")
            old.optJSONArray("completed")?.let { history -> for(i in 0 until history.length()) {
                val entry=history.getJSONObject(i).copyJson().put("legacy",true)
                if(!entry.has("subject")) entry.put("subject","History")
                completed.put(entry);seconds+=entry.optInt("durationSeconds")
                runCatching { dates+=LocalDate.parse(entry.getString("date")) }
            } }
        } }
        val last=dates.maxOrNull(); var streak=0; var day=last
        while(day != null && day in dates) { streak++; day=day.minusDays(1) }
        val result=JSONObject().put("xp",xp+legacyXp).put("points",points+legacyXp).put("legacyXp",legacyXp).put("streak",serverRecords("stats").firstOrNull()?.optInt("streak",streak) ?: streak).put("lastDay",last?.toString().orEmpty()).put("completed",completed).put("totalStudySeconds",seconds)
        val activeId=record("preferences","app").optString("activeLearningId")
        val sessions=records("sessions").filter { !it.optBoolean("closed") }
        (sessions.find { it.optString("id")==activeId } ?: sessions.maxByOrNull { it.optLong("startedAt") })?.let { result.put("session",it) }
        result
    }

    fun economyOperationId(action: String, item: String): String = synchronized(lock) {
        val key="shop_${action}_$item"
        prefs.getString(key,null) ?: UUID.randomUUID().toString().also { check(prefs.edit().putString(key,it).commit()) }
    }
    suspend fun shop(action: String, item: String) {
        check(authorized())
        val id=economyOperationId(action,item)
        com.google.firebase.functions.FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("shopAction")
            .call(mapOf("operationId" to id,"action" to action,"item" to item)).awaitResult()
        synchronized(lock){check(prefs.edit().remove("shop_${action}_$item").commit())}
    }
    fun exportJson(): String = synchronized(lock) {
        check(authorized())
        JSONObject().put("uid",uid).put("profile",profile.copyJson()).put("records",projection.copyJson())
            .put("receipts",receipts.copyJson()).put("inventory",inventory.copyJson()).put("unlocks",unlocks.copyJson())
            .put("challenges",challenges.copyJson()).put("server",server.copyJson()).put("legacy",legacy.copyJson())
            .put("changes",events.copyJson()).put("pendingImages",uploads()).toString(2)
    }
    companion object {
        private val migrationLock=Any()
        private val locks=java.util.concurrent.ConcurrentHashMap<String,Any>()
        private val flushLocks=java.util.concurrent.ConcurrentHashMap<String,Mutex>()
    }
}

internal object AccountSession {
    @Volatile var repository: AccountRepository? = null
        private set
    fun open(context: Context, uid: String): AccountRepository {
        repository?.close()
        return AccountRepository(context.applicationContext, uid).also { repository = it; it.start() }
    }
    fun close() { repository?.close(); repository = null }
}

class AccountSyncWorker(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid=inputData.getString("uid") ?: return Result.failure()
        if(FirebaseAuth.getInstance().currentUser?.uid != uid) return Result.success()
        val active=AccountSession.repository?.takeIf { it.uid == uid }
        val repo=active ?: AccountRepository(applicationContext,uid)
        return try { withTimeout(90_000) { repo.flush() }; Result.success() } catch(e: Exception) { Result.retry() } finally { if(active == null) repo.close() }
    }
}

// Firebase timestamps are not JSON primitives. Normalize them before persistence/export.
private fun cloudJson(values:Map<String,Any?>):JSONObject {
    fun value(v:Any?):Any=when(v){
        null->JSONObject.NULL
        is com.google.firebase.Timestamp->v.toDate().time
        is Map<*,*>->cloudJson(v.entries.associate{it.key.toString() to it.value})
        is List<*>->JSONArray(v.map{value(it)})
        is com.google.firebase.firestore.DocumentReference->v.path
        else->v
    }
    return JSONObject().apply{values.forEach{(key,v)->put(key,value(v))}}
}
