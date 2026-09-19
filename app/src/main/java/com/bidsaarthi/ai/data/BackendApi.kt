package com.bidsaarthi.ai.data
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
class BackendApi(private val baseUrl:String,private val client:OkHttpClient=OkHttpClient()){suspend fun tendersJson(query:String=""):String=withContext(Dispatchers.IO){val url=baseUrl.trimEnd('/')+"/tenders?q="+java.net.URLEncoder.encode(query,"UTF-8");client.newCall(Request.Builder().url(url).build()).execute().use{resp->if(!resp.isSuccessful) error("Backend HTTP "+resp.code);resp.body?.string().orEmpty()}}}
