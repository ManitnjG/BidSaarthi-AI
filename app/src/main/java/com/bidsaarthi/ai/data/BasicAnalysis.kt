package com.bidsaarthi.ai.data

import com.bidsaarthi.ai.model.Tender
import org.json.JSONArray
import org.json.JSONObject

/** Free offline checks, intentionally never presented as generative AI. */
fun basicAnalysis(t:Tender,profile:JSONObject,reason:String=""):JSONObject {
 val categories=profile.optJSONArray("categories") ?: JSONArray()
 val hay="${t.title} ${t.department} ${t.evidence}"
 val hits=(0 until categories.length()).map { categories.optString(it) }.filter { it.isNotBlank() && hay.contains(it,true) }
 val reasons=JSONArray().put("Basic rule-based check; no AI model was used.")
 reasons.put(if(hits.isEmpty()) "No business keywords matched the available listing." else "Matching keywords: ${hits.distinct().joinToString()}")
 if(reason.isNotBlank()) reasons.put(reason)
 val risks=JSONArray().put("Verify the full tender, fees, exemptions and amendments on the official portal.")
 val remaining=deadlineMillis(t.deadline)?.minus(System.currentTimeMillis())
 if(remaining!=null && remaining<0) risks.put("The listed deadline has passed.")
 else if(remaining!=null && remaining<=172800000L) risks.put("The listed deadline is within 48 hours.")
 return JSONObject().put("analysis_mode","RULE_BASED").put("provider","Offline basic checks").put("eligibility","UNKNOWN")
  .put("summary",t.title).put("confidence",0).put("eligibility_reasons",reasons).put("risks",risks)
  .put("missing_documents",JSONArray().put("Full eligibility clauses and document requirements need verification."))
  .put("evidence",JSONObject().apply { if(t.evidence.isNotBlank()) put("listing",t.evidence.take(600)) })
}
