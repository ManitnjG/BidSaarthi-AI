package com.bidsaarthi.ai.data

import com.bidsaarthi.ai.model.Tender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SourceSync(val source:TenderSource,val tenders:List<Tender>,val error:String?=null)

class TenderRepository {
 private val client=OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(18,TimeUnit.SECONDS).build()

 suspend fun syncAll():List<SourceSync> = coroutineScope {
  TenderSources.all.map { source -> async {
   if(source.kind!=SourceKind.NIC_PUBLIC_HOME)
    SourceSync(source,emptyList(),"Portal connected for direct access; structured feed not available.")
   else runCatching {
    val html=fetchPublicPage(source.baseUrl)
    SourceSync(source,NicTenderParser.parse(source,html))
   }.getOrElse { SourceSync(source,emptyList(),it.message?:"Sync failed") }
  }}.awaitAll()
 }

 suspend fun fetchPublicPage(url:String):String=withContext(Dispatchers.IO){
  val req=Request.Builder().url(url).header("User-Agent","Mozilla/5.0 (Android) BidSaarthiAI/0.2").build()
  client.newCall(req).execute().use { r ->
   if(!r.isSuccessful) error("HTTP ${r.code}")
   r.body?.string().orEmpty()
  }
 }
}
