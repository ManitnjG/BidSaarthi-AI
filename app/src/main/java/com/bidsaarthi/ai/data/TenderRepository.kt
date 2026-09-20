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
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

data class SourceSync(
    val source: TenderSource,
    val tenders: List<Tender>,
    val error: String? = null,
    val refreshedAt: Long = 0,
    val coverage: String = "Coverage not checked",
    val portalTotal: Int? = null,
    val checkedAt: String = ""
)

class TenderRepository(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun loadLocal(snapshot: String? = null): List<SourceSync> {
        val grouped = mutableMapOf<String, MutableList<Tender>>()
        try {
            val raw = snapshot ?: context.assets.open("tenders.json").bufferedReader().use { it.readText() }
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id")
                if (id.isBlank()) continue

                var sourceId = obj.optString("source_id", "cppp")
                val exactUrl = obj.optString("source_url")
                // Historic bundled entries without captured evidence cannot be verified.
                if (obj.optJSONObject("evidence")?.optString("listing").isNullOrBlank()) continue
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
                        evidence = obj.optJSONObject("evidence")?.optString("listing").orEmpty(),
                        summary = listOf(refNo, "Official public listing").filter { it.isNotBlank() }.joinToString(" • "),
                        requirements = listOf(
                            Requirement("Verify original tender document", RequirementStatus.VERIFY)
                        )
                    )
                )
            }
        } catch (_: Exception) {
        }

        val cached = if(snapshot == null) LocalStore(context).tenders() else emptyList()
        for (t in cached) {
            val source = TenderSources.all.firstOrNull { it.name == t.source } ?: continue
            val bucket = grouped.getOrPut(source.id) { mutableListOf() }
            bucket.removeAll { it.id == t.id }; bucket.add(t)
        }
        return TenderSources.all.map { source ->
            status(source, grouped[source.id].orEmpty())
        }
    }

    private fun metadata(): Map<String, JSONObject> = runCatching {
        val prefs=context.getSharedPreferences("bidsaarthi",Context.MODE_PRIVATE)
        val raw=prefs.getString("source_status",null) ?: context.assets.open("source_status.json").bufferedReader().use { it.readText() }
        val array=JSONArray(raw)
        (0 until array.length()).map { array.getJSONObject(it) }.associateBy { it.optString("source") }
    }.getOrDefault(emptyMap())

    private fun status(source:TenderSource,items:List<Tender>,failure:String?=null):SourceSync {
        val meta=metadata()[source.id]
        val label=when {
            source.kind==SourceKind.EXTERNAL_LINK -> "Portal only — not collected"
            failure!=null -> failure
            meta==null -> "Coverage not checked"
            else -> meta.optString("status") + " · " + items.size + " stored · " + meta.optInt("pages",0) + " pages checked in last run"
        }
        return SourceSync(source,items,label,0,meta?.optString("coverage").orEmpty(),
            if(meta!=null && !meta.isNull("portal_total")) meta.optInt("portal_total") else null,meta?.optString("checked_at").orEmpty())
    }

    suspend fun syncAll(): List<SourceSync> = withContext(Dispatchers.IO) {
        val store=LocalStore(context)
        val previous=loadLocal().flatMap { it.tenders }.associateBy { it.id }.toMutableMap()
        var failure:String?=null
        try {
            val root="https://raw.githubusercontent.com/ManitnjG/BidSaarthi-AI/main/app/src/main/assets/"
            fun fetch(file:String):String=client.newCall(Request.Builder().url(root+file).build()).execute().use { r ->
                check(r.isSuccessful) { "Feed HTTP ${r.code}" };r.body?.string() ?: error("Empty feed")
            }
            val snapshot=fetch("tenders.json")
            require(JSONArray(snapshot).length()>0) { "No verified listings" }
            loadLocal(snapshot).flatMap { it.tenders }.forEach { previous[it.id]=it }
            runCatching {
                val raw=fetch("source_status.json");JSONArray(raw)
                context.getSharedPreferences("bidsaarthi",Context.MODE_PRIVATE).edit().putString("source_status",raw).apply()
            }
        } catch (_:Exception) { failure="Refresh unavailable — showing stored listings" }
        val savedIds=store.savedIds()
        val retained=previous.values.filter { t -> t.id in savedIds || (deadlineMillis(t.deadline)?.let { it>System.currentTimeMillis()-172800000L } ?: true) }
        store.saveTenders(retained)
        TenderSources.all.map { source -> status(source,retained.filter { it.source==source.name },failure) }
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

                val anchorTitle = linkEl?.text()?.trim().orEmpty()
                val suffix = if(anchorTitle.isNotBlank() && rawTitle.startsWith(anchorTitle)) rawTitle.removePrefix(anchorTitle).trimStart(' ', '/') else ""
                val refNo = if(suffix.contains('/')) suffix.substringBeforeLast('/').trim() else suffix.ifBlank { rawTitle }
                val cleanTitle = anchorTitle.ifBlank { rawTitle }
                val sourceId = if(isState) "state" else "cppp"
                val tenderId = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((sourceId + "|" + refNo).toByteArray()).take(12).joinToString("") { "%02x".format(it) }

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
                        evidence = row.text().take(4000),
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

