package com.bidsaarthi.ai.data
import com.bidsaarthi.ai.model.Tender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
class BackendApi(private val baseUrl:String) {
 private val client=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(75,TimeUnit.SECONDS).build()
 suspend fun analyze(t:Tender, profile:JSONObject):JSONObject=withContext(Dispatchers.IO) {
  val tender=JSONObject().put("id",t.id).put("source_id",t.source).put("source_url",t.url).put("title",t.title)
   .put("department",t.department).put("location",t.location).put("closes_at",t.deadline)
   .put("evidence",JSONObject().put("listing", "Title: ${t.title} | Department: ${t.department} | Location: ${t.location} | Closing: ${t.deadline} | ${t.summary}"))
  val body=JSONObject().put("tender",tender).put("business",profile).toString().toRequestBody("application/json".toMediaType())
  client.newCall(Request.Builder().url(baseUrl.trimEnd('/')+"/analyze-listing").post(body).build()).execute().use { r ->
   if(!r.isSuccessful) error(when(r.code){404->"Backend update required. Analysis endpoint is unavailable.";429->"Analysis is busy. Please try again shortly.";else->"Analysis service returned HTTP ${r.code}. Try again."})
   JSONObject(r.body?.string() ?: error("Empty analysis response"))
  }
 }
}
