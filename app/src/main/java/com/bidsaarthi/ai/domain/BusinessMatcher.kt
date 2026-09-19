package com.bidsaarthi.ai.domain
import com.bidsaarthi.ai.model.*
data class Match(val score:Int,val reasons:List<String>,val missing:List<String>)
object BusinessMatcher{fun match(t:Tender,p:BusinessProfile):Match{val hay=(t.title+" "+t.department+" "+t.location).lowercase();var score=0;val reasons=mutableListOf<String>();val missing=mutableListOf<String>();val hits=p.categories.filter{hay.contains(it.lowercase())};if(hits.isNotEmpty()){score+=55;reasons+="Category match: "+hits.joinToString()};if(hay.contains(p.state.lowercase())){score+=20;reasons+="Location match"};if(p.hasGst)score+=10 else missing+="GST not confirmed";if(p.hasUdyam)score+=10 else missing+="Udyam/MSME not confirmed";return Match(score.coerceAtMost(100),reasons,missing)}}
