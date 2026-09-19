package com.bidsaarthi.ai.data

import com.bidsaarthi.ai.model.Requirement
import com.bidsaarthi.ai.model.RequirementStatus
import com.bidsaarthi.ai.model.Tender
import org.jsoup.Jsoup

object NicTenderParser {
 private val date = Regex("""\\b\\d{2}-[A-Za-z]{3}-\\d{4}\\s+\\d{1,2}:\\d{2}\\s+(?:AM|PM)\\b""", RegexOption.IGNORE_CASE)

 fun parse(source:TenderSource, html:String):List<Tender> {
  if (html.isBlank()) return emptyList()
  val doc=Jsoup.parse(html,source.baseUrl)
  val out=mutableListOf<Tender>()
  for (row in doc.select("tr")) {
   val cells=row.select("td")
   if (cells.size < 3) continue
   val values=cells.map { it.text().replace(Regex("""\\s+""")," ").trim() }
   val dates=values.flatMap { v -> date.findAll(v).map { it.value }.toList() }
   if (dates.isEmpty()) continue
   val links=row.select("a[href]").filter {
    val t=it.text().trim()
    t.length >= 8 && !t.equals("More",true) && !t.contains("corrigendum",true)
   }
   val titleLink=links.maxByOrNull { it.text().trim().length }
   var title=titleLink?.text()?.trim().orEmpty()
   if (title.length < 8) {
    title=values.filterNot { date.containsMatchIn(it) }
     .filter { it.length >= 8 && !it.matches(Regex("""\\d+\\.?""")) }
     .maxByOrNull { it.length }.orEmpty()
   }
   if (title.length < 8) continue
   val href=titleLink?.attr("abs:href")?.takeIf { it.startsWith("http") } ?: source.baseUrl
   val closing = if(source.id=="cppp" && values.size>=3 && date.containsMatchIn(values[2]))
    date.find(values[2])!!.value else dates.first()
   val organisation = if(source.id=="cppp" && values.size>=6) values[5] else source.name
   val reference = if(source.id=="cppp" && values.size>=5)
    values[4].removePrefix(title).trim().trimStart('/')
   else values.firstOrNull { it != title && !date.containsMatchIn(it) && it.length in 4..140 && !it.matches(Regex("""\\d+\\.?""")) }.orEmpty()
   val id=(source.id+"|"+title+"|"+reference+"|"+closing).hashCode().toUInt().toString(16)
   out += Tender(
    id=source.id+"-"+id, title=title.take(300),
    department=organisation.ifBlank { source.name },
    location=if(source.id=="tn") "Tamil Nadu" else "India",
    value="Refer official tender document", deadline=closing, source=source.name, url=href,
    readiness=0,
    summary=if(reference.isBlank()) "Live listing from "+source.name+"." else "Ref: "+reference,
    requirements=listOf(Requirement("Verify original tender document",RequirementStatus.VERIFY))
   )
  }
  return out.distinctBy { it.id }.take(100)
 }
}
