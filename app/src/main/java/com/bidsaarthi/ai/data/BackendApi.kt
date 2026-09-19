package com.bidsaarthi.ai.data
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
class BackendApi(private val baseUrl:String,private val client:OkHttpClient=OkHttpClient()){
 suspend fun tendersJson(query:String=""):String=withContext(Dispatchers.IO){
  val url=baseUrl.trimEnd('/')+"/tenders?q="+java.net.URLEncoder.encode(query,"UTF-8")
  client.newCall(Request.Builder().url(url).build()).execute().use{r->if(!r.isSuccessful) error("Backend HTTP "+r.code);r.body?.string().orEmpty()}
 }
 suspend fun analyzeJson(tenderId:String,businessJson:String="{}"):String=withContext(Dispatchers.IO){
  val body=businessJson.toRequestBody("application/json".toMediaType())
  val req=Request.Builder().url(baseUrl.trimEnd('/')+"/analyze/"+tenderId).post(body).build()
  client.newCall(req).execute().use{r->if(!r.isSuccessful) error("Analysis HTTP "+r.code);r.body?.string().orEmpty()}
 }
}
