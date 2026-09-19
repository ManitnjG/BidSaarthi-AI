package com.bidsaarthi.ai.data

import android.content.Context
import com.bidsaarthi.ai.model.Requirement
import com.bidsaarthi.ai.model.RequirementStatus
import com.bidsaarthi.ai.model.Tender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import okhttp3.OkHttpClient
import okhttp3.Request

data class SourceSync(val source:TenderSource,val tenders:List<Tender>,val error:String?=null)

class TenderRepository(private val context:Context) {\n private val githubFeed="https://raw.githubusercontent.com/ManitnjG/BidSaarthi-AI/main/app/src/main/assets/tenders.json"
 suspend fun syncAll():List<SourceSync> = withContext(Dispatchers.IO) {
  val raw=try { OkHttpClient().newCall(Request.Builder().url(githubFeed).build()).execute().use { r -> if(!r.isSuccessful) error("HTTP "+r.code); r.body?.string().orEmpty() } } catch(_:Exception) { context.assets.open("tenders.json").bufferedReader().use{it.readText()} }
  val a=JSONArray(raw)
  val grouped=mutableMapOf<String,MutableList<Tender>>()
  for(i in 0 until a.length()){
   try {
   val o=a.getJSONObject(i); val sid=o.optString("source_id")
   val source=TenderSources.all.firstOrNull{it.id==sid}?:continue
   grouped.getOrPut(sid){mutableListOf()}.add(Tender(
    id=o.optString("id"),title=o.optString("title"),department=o.optString("department",source.name),
    location=o.optString("location","India"),value=if(o.isNull("value")) "Refer official tender document" else o.optString("value","Refer official tender document"),
    deadline=o.optString("closes_at"),source=source.name,url=o.optString("source_url",source.baseUrl),readiness=0,
    summary=listOf(o.optString("reference_no"),"Official public listing").filter{it.isNotBlank()}.joinToString(" • "),
    requirements=listOf(Requirement("Verify original tender document",RequirementStatus.VERIFY))
   ))
   } catch(_:Exception) {}
  }
  TenderSources.all.map{s->
   val items=grouped[s.id].orEmpty()
   SourceSync(s,items,if(items.isEmpty()) "No public listings collected in latest sync" else null)
  }
 }
}
