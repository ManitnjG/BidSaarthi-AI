package com.bidsaarthi.ai.data

import com.bidsaarthi.ai.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Production foundation: only official/public endpoints should be connected here.
 * No CAPTCHA bypass, credential scraping or fabricated tender records.
 */
class TenderRepository {
    private val client = OkHttpClient()
    suspend fun fetchPublicPage(url:String):String = withContext(Dispatchers.IO) {
        val request=Request.Builder().url(url).header("User-Agent","BidSaarthiAI/0.1").build()
        client.newCall(request).execute().use { if(it.isSuccessful) it.body?.string().orEmpty() else "" }
    }
    fun starterTenders() = listOf(
        Tender("welcome","Connect official tender sources","BidSaarthi Source Center","India","—","—","Setup","",
            0,"Connect permitted CPPP, GeM and state procurement sources. BidSaarthi never invents live tenders.",
            listOf(Requirement("Official source connector",RequirementStatus.VERIFY)))
    )
}
