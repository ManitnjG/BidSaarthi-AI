package com.bidsaarthi.ai.data

import android.content.Context
import com.bidsaarthi.ai.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("bidsaarthi", Context.MODE_PRIVATE)
    fun profile(): JSONObject = runCatching { JSONObject(prefs.getString("profile", "{}") ?: "{}") }.getOrDefault(JSONObject())
    fun saveProfile(value: JSONObject) { prefs.edit().putString("profile", value.toString()).apply() }
    fun refreshed(source: String) = prefs.getLong("refresh_$source", 0)
    fun setRefreshed(source: String) { prefs.edit().putLong("refresh_$source", System.currentTimeMillis()).apply() }
    fun savedIds(): Set<String> = prefs.getStringSet("saved_tenders", emptySet())?.toSet() ?: emptySet()
    fun toggle(t: Tender) { val ids = savedIds().toMutableSet(); if(!ids.add(t.id)) ids.remove(t.id)
        saveTenders(tenders() + t); prefs.edit().putStringSet("saved_tenders", ids).apply() }
    fun saveTenders(items: List<Tender>) {
        val keep = (tenders().filter { it.id in savedIds() } + items).associateBy { it.id }.values
        prefs.edit().putString("tender_cache", JSONArray().apply { keep.forEach { put(encode(it)) } }.toString()).apply()
    }
    fun tenders(): List<Tender> = runCatching {
        val a = JSONArray(prefs.getString("tender_cache", "[]")); (0 until a.length()).map { decode(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())
    fun encode(t: Tender) = JSONObject().put("id",t.id).put("title",t.title).put("department",t.department)
        .put("location",t.location).put("value",t.value).put("deadline",t.deadline).put("source",t.source)
        .put("url",t.url).put("summary",t.summary)
    private fun decode(o: JSONObject) = Tender(o.getString("id"),o.getString("title"),o.optString("department"),
        o.optString("location"),o.optString("value"),o.optString("deadline"),o.optString("source"),o.optString("url"),0,o.optString("summary"),emptyList())
    fun checklist(id: String): Set<String> = prefs.getStringSet("check_$id",emptySet())?.toSet() ?: emptySet()
    fun setChecklist(id:String, value:Set<String>) { prefs.edit().putStringSet("check_$id",value).apply() }
}
fun deadlineMillis(value: String): Long? {
    for (pattern in listOf("dd-MMM-yyyy hh:mm a", "dd-MMM-yyyy HH:mm", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd")) {
        val result = runCatching { SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient=false; timeZone=TimeZone.getTimeZone("Asia/Kolkata") }.parse(value)?.time }.getOrNull()
        if(result != null) return result
    }
    return null
}
