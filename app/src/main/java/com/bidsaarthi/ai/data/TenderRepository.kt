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
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

data class SourceSync(
    val source: TenderSource,
    val tenders: List<Tender>,
    val error: String? = null,
    val refreshedAt: Long = 0
)

class TenderRepository(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun loadLocal(): List<SourceSync> {
        val grouped = mutableMapOf<String, MutableList<Tender>>()
        try {
            val raw = context.assets.open("tenders.json").bufferedReader().use { it.readText() }
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id")
                if (id.isBlank()) continue

                var sourceId = obj.optString("source_id", "cppp")
                val exactUrl = obj.optString("source_url")
                val refNo = obj.optString("reference_no")
                val title = obj.optString("title")
                val dept = obj.optString("department")

                if (sourceId.isBlank()) sourceId = if (dept.contains("State", true)) "state" else "cppp"
                val source = TenderSources.all.firstOrNull { it.id == sourceId } ?: TenderSources.all.first()

                val loc = obj.optString("location").ifBlank { "Pan India / Central" }
                val valueStr = if (obj.isNull("value")) "Refer official NIT / BOQ" else obj.optString("value", "Refer official NIT / BOQ")
                val deadline = obj.optString("closes_at", "Refer portal")

                grouped.getOrPut(source.id) { mutableListOf() }.add(
                    Tender(
                        id = id,
                        title = title,
                        department = dept.ifBlank { source.name },
                        location = loc,
                        value = valueStr,
                        deadline = deadline,
                        source = source.name,
                        url = exactUrl.ifBlank { source.baseUrl },
                        readiness = 0,
                        summary = listOf(refNo, "Official public listing").filter { it.isNotBlank() }.joinToString(" • "),
                        requirements = listOf(
                            Requirement("Verify original tender document", RequirementStatus.VERIFY)
                        )
                    )
                )
            }
        } catch (_: Exception) {
        }

        val cached = LocalStore(context).tenders()
        for (t in cached) {
            val source = TenderSources.all.firstOrNull { it.name == t.source } ?: continue
            val bucket = grouped.getOrPut(source.id) { mutableListOf() }
            bucket.removeAll { it.id == t.id }; bucket.add(t)
        }
        return TenderSources.all.map { source ->
            SourceSync(source, grouped[source.id].orEmpty(), "Saved data — refresh to verify", LocalStore(context).refreshed(source.id))
        }
    }

    suspend fun syncAll(): List<SourceSync> = withContext(Dispatchers.IO) {
        val localSyncs = loadLocal()
        val grouped = mutableMapOf<String, MutableList<Tender>>()
        val seenIds = mutableSetOf<String>()
        val errors = mutableMapOf<String, String>()
        val store = LocalStore(context)

        for (sync in localSyncs) {
            for (t in sync.tenders) {
                if (t.id.isNotBlank() && seenIds.add(t.id)) {
                    grouped.getOrPut(sync.source.id) { mutableListOf() }.add(t)
                }
            }
        }

        // Fetch live updates from CPPP and State portals
        val liveSources = listOf(
            Triple("cppp", "https://eprocure.gov.in/cppp/latestactivetendersnew/cpppdata", false),
            Triple("state", "https://eprocure.gov.in/cppp/latestactivetendersnew/mmpdata", true)
        )

        for ((sourceId, url, isState) in liveSources) {
            try {
                val liveTenders = fetchLivePortals(url, isState)
                if (liveTenders.isEmpty()) errors[sourceId] = "No listings returned; showing saved data"
                else {
                    val bucket = grouped.getOrPut(sourceId) { mutableListOf() }
                    liveTenders.forEach { t -> bucket.removeAll { it.id == t.id }; bucket.add(0, t) }
                    store.setRefreshed(sourceId)
                }
            } catch (_: Exception) {
                errors[sourceId] = "Refresh failed; showing saved data"
            }
        }

        store.saveTenders(grouped.values.flatten())
        TenderSources.all.map { source ->
            val items = grouped[source.id].orEmpty()
            SourceSync(
                source,
                items,
                errors[source.id] ?: if (source.id !in listOf("cppp", "state")) "Bundled/saved listings; not refreshed on device" else null,
                store.refreshed(source.id)
            )
        }
    }

    private fun fetchLivePortals(url: String, isState: Boolean): List<Tender> {
        val results = mutableListOf<Tender>()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()
        val html = client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }; response.body?.string().orEmpty()
        }
        val doc = Jsoup.parse(html, url)
        val rows = doc.select("table#table.list_table tr, table.list_table tr, table tr")

        for (row in rows) {
            val cols = row.select("td")
            if (cols.size >= 6) {
                val pubDate = cols[1].text().trim()
                val closingDate = cols[2].text().trim()
                val titleCell = cols[4]
                val linkEl = titleCell.selectFirst("a")
                val rawLink = linkEl?.attr("abs:href") ?: linkEl?.attr("href") ?: ""
                val tenderLink = if (rawLink.startsWith("http")) rawLink else if (rawLink.isNotBlank()) "https://eprocure.gov.in$rawLink" else url

                val rawTitle = titleCell.text().trim()
                if (rawTitle.isBlank() || rawTitle.contains("Title/Ref.No./Tender Id", ignoreCase = true)) {
                    continue
                }

                val lastSlash = rawTitle.lastIndexOf('/')
                val tenderId = if (lastSlash != -1 && lastSlash < rawTitle.length - 1) {
                    rawTitle.substring(lastSlash + 1).trim()
                } else {
                    "TND-" + java.security.MessageDigest.getInstance("SHA-256").digest((sourceNameForId(isState) + rawTitle).toByteArray()).take(12).joinToString("") { "%02x".format(it) }
                }
                val beforeId = if (lastSlash != -1) rawTitle.substring(0, lastSlash).trim() else rawTitle
                val secondSlash = beforeId.lastIndexOf('/')
                val refNo = if (secondSlash != -1 && secondSlash < beforeId.length - 1) {
                    beforeId.substring(secondSlash + 1).trim()
                } else {
                    tenderId
                }
                val cleanTitle = if (secondSlash != -1) beforeId.substring(0, secondSlash).trim() else beforeId

                val org = cols[5].text().trim()
                val sourceName = if (isState) "State eProcurement (MMP)" else "CPPP / Central eProcurement"
                val location = if (isState) org else extractLocation(org, cleanTitle)

                results.add(
                    Tender(
                        id = tenderId.ifBlank { "TND-${results.size + 1}" },
                        title = cleanTitle.ifBlank { rawTitle },
                        department = org.ifBlank { if (isState) "State Government" else "Central Government" },
                        location = location,
                        value = "Refer official NIT / BOQ",
                        deadline = closingDate.ifBlank { pubDate },
                        source = sourceName,
                        url = tenderLink,
                        readiness = 0,
                        summary = "$refNo • Official public listing",
                        requirements = listOf(
                            Requirement("Verify original tender document", RequirementStatus.VERIFY)
                        )
                    )
                )
            }
        }
        return results
    }

    private fun sourceNameForId(isState: Boolean) = if(isState) "state:" else "cppp:"

    private fun extractLocation(authority: String, title: String): String {
        val text = "$authority $title".lowercase()
        return when {
            text.contains("delhi") -> "Delhi"
            text.contains("mumbai") || text.contains("maharashtra") || text.contains("pune") || text.contains("nagpur") -> "Maharashtra"
            text.contains("lucknow") || text.contains("uttar pradesh") || text.contains("noida") -> "Uttar Pradesh"
            text.contains("bengaluru") || text.contains("bangalore") || text.contains("karnataka") -> "Karnataka"
            text.contains("chennai") || text.contains("tamil nadu") -> "Tamil Nadu"
            text.contains("hyderabad") || text.contains("telangana") -> "Telangana"
            text.contains("kolkata") || text.contains("bengal") -> "West Bengal"
            text.contains("jaipur") || text.contains("rajasthan") -> "Rajasthan"
            text.contains("ahmedabad") || text.contains("gujarat") -> "Gujarat"
            text.contains("bhopal") || text.contains("madhya pradesh") -> "Madhya Pradesh"
            text.contains("kerala") || text.contains("kochi") -> "Kerala"
            text.contains("chandigarh") || text.contains("punjab") || text.contains("haryana") -> "Punjab / Haryana"
            text.contains("jammu") || text.contains("kashmir") -> "Jammu & Kashmir"
            text.contains("patna") || text.contains("bihar") -> "Bihar"
            text.contains("odisha") || text.contains("bhubaneswar") -> "Odisha"
            text.contains("assam") || text.contains("guwahati") -> "Assam"
            else -> "Pan India / Central"
        }
    }
}

