package com.bidsaarthi.ai.model

data class Tender(
    val id:String, val title:String, val department:String, val location:String,
    val value:String, val deadline:String, val source:String, val url:String,
    val readiness:Int, val summary:String, val requirements:List<Requirement>, val evidence:String=""
)
data class Requirement(val name:String,val status:RequirementStatus,val evidence:String="")
enum class RequirementStatus { READY, MISSING, VERIFY }
data class BusinessProfile(
    val name:String="Your Business", val categories:List<String> = emptyList(),
    val state:String="Tamil Nadu", val hasGst:Boolean=false, val hasUdyam:Boolean=false,
    val turnover:String="", val readiness:Int=0
)
