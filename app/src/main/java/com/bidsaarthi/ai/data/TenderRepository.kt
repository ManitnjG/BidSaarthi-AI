package com.bidsaarthi.ai.data

import android.content.Context
import com.bidsaarthi.ai.model.Requirement
import com.bidsaarthi.ai.model.RequirementStatus
import com.bidsaarthi.ai.model.Tender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

data class SourceSync(
    val source: TenderSource,
    val tenders: List<Tender>,
    val error: String? = null
)

class TenderRepository(private val context: Context) {
    private val client = OkHttpClient()
    private val githubFeed = "https://raw.githubusercontent.com/ManitnjG/BidSaarthi-AI/main/app/src/main/assets/tenders.json"

    suspend fun syncAll(): List<SourceSync> = withContext(Dispatchers.IO) {
        val raw = try {
            client.newCall(Request.Builder().url(githubFeed).build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP " + response.code)
                response.body?.string().orEmpty()
            }
        } catch (_: Exception) {
            context.assets.open("tenders.json").bufferedReader().use { it.readText() }
        }

        val array = JSONArray(raw)
        val grouped = mutableMapOf<String, MutableList<Tender>>()

        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                val sourceId = obj.optString("source_id")
                val exactUrl = obj.optString("source_url")
                val refNo = obj.optString("reference_no")
                val title = obj.optString("title")
                if (title.isBlank() || refNo.isBlank() || exactUrl.isBlank() || exactUrl == source.baseUrl) continue
                val source = TenderSources.all.firstOrNull { it.id == sourceId } ?: continue
                grouped.getOrPut(sourceId) { mutableListOf() }.add(
                    Tender(
                        id = obj.optString("id"),
                        title = title,
                        department = obj.optString("department", source.name),
                        location = obj.optString("location", "India"),
                        value = if (obj.isNull("value")) "Refer official tender document" else obj.optString("value", "Refer official tender document"),
                        deadline = obj.optString("closes_at"),
                        source = source.name,
                        url = exactUrl,
                        readiness = 0,
                        summary = listOf(refNo, "Official public listing")
                            .filter { it.isNotBlank() }
                            .joinToString(" • "),
                        requirements = listOf(
                            Requirement("Verify original tender document", RequirementStatus.VERIFY)
                        )
                    )
                )
            } catch (_: Exception) {
            }
        }

        TenderSources.all.map { source ->
            val items = grouped[source.id].orEmpty()
            SourceSync(
                source,
                items,
                if (items.isEmpty()) "No public listings collected in latest sync" else null
            )
        }
    }
}
