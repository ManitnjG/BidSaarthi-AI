package com.bidsaarthi.ai.data

import com.bidsaarthi.ai.model.Tender
import com.bidsaarthi.ai.model.Requirement
import com.bidsaarthi.ai.model.RequirementStatus

/**
 * Conservative parser for the public "Latest Tenders" area of NIC procurement home pages.
 * It deliberately does not submit CAPTCHA-protected search forms.
 */
object NicTenderParser {
 private val tags=Regex("<[^>]+>")
 private val spaces=Regex("\\s+")
 private val closing=Regex("""(\\d{2}-[A-Za-z]{3}-\\d{4}\\s+\\d{2}:\\d{2}\\s+(?:AM|PM))""")
 fun parse(source:TenderSource, html:String):List<Tender>{
  if(html.isBlank()) return emptyList()
  val text=html.replace("&nbsp;"," ").replace("&amp;","&")
   .replace(tags," ").replace(spaces," ").trim()
  // Public home pages change frequently. Extract only records with an explicit closing timestamp.
  val matches=closing.findAll(text).toList()
  return matches.take(20).mapIndexedNotNull { index,m ->
   val end=m.range.first
   val start=(end-240).coerceAtLeast(0)
   val prefix=text.substring(start,end).trim()
   val title=prefix.substringAfterLast("Latest Tenders",prefix)
    .replace(Regex("""^.*?\\d+\\.\\s*"""),"").trim()
   if(title.length<8) null else Tender(
    id="${source.id}-${m.value.hashCode()}-$index",
    title=title.take(180), department=source.name, location="India",
    value="Refer tender document", deadline=m.value, source=source.name,
    url=source.baseUrl, readiness=0,
    summary="Live public listing from ${source.name}. Open the official source to verify full bid details.",
    requirements=listOf(Requirement("Verify original tender document",RequirementStatus.VERIFY))
   )
  }.distinctBy{it.title+it.deadline}
 }
}
