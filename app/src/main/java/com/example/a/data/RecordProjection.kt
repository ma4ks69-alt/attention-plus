package com.example.a.data

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.copyJson() = JSONObject(toString())
internal fun JSONObject.objects(): List<JSONObject> = keys().asSequence().mapNotNull { optJSONObject(it) }.toList()
internal fun JSONObject.toMapValue(): Map<String, Any?> = keys().asSequence().associateWith { jsonValue(get(it)) }
private fun jsonValue(value: Any?): Any? = when (value) {
    null, JSONObject.NULL -> null
    is JSONObject -> value.toMapValue()
    is JSONArray -> (0 until value.length()).map { jsonValue(value.get(it)) }
    else -> value
}

/** Immutable field patches merge deterministically; unrelated edits never replace whole records.
 * All revisions remain in Firestore so concurrent edits can be recovered/audited. */
internal object RecordProjection {
    private val stages = listOf("timer", "analysis", "quiz", "results")
    fun reduce(events: Collection<JSONObject>): JSONObject {
        val result = JSONObject()
        events.sortedWith(compareBy<JSONObject> { it.optLong("clock") }.thenBy { it.getString("id") }).forEach { event ->
            val bucket = event.getString("bucket")
            val group = result.optJSONObject(bucket) ?: JSONObject().also { result.put(bucket, it) }
            val id = event.getString("entity")
            val target = group.optJSONObject(id) ?: JSONObject().also { group.put(id, it) }
            val patch = event.getJSONObject("patch")
            patch.keys().forEach { key ->
                val incoming = patch.get(key)
                when {
                    bucket == "sessions" && key == "stage" -> if (stages.indexOf(incoming) >= stages.indexOf(target.optString(key))) target.put(key, incoming)
                    bucket == "sessions" && key == "question" -> target.put(key, maxOf(target.optInt(key), patch.optInt(key)))
                    bucket == "sessions" && key == "closed" -> target.put(key, target.optBoolean(key) || patch.optBoolean(key))
                    bucket == "focus" && key == "completed" -> target.put(key, target.optBoolean(key) || patch.optBoolean(key))
                    bucket == "sessions" && key == "answers" -> {
                        val answers = target.optJSONArray(key) ?: JSONArray()
                        val added = patch.getJSONArray(key)
                        for (index in answers.length() until added.length()) answers.put(added.get(index))
                        target.put(key, answers)
                    }
                    else -> target.put(key, incoming)
                }
            }
            if(bucket=="focus" && target.optBoolean("completed")) target.put("running",false)
        }
        return result
    }
}
