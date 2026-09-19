package com.bidsaarthi.ai.data

data class TenderSource(
 val id:String, val name:String, val baseUrl:String, val kind:SourceKind,
 val enabled:Boolean=true, val note:String=""
)
enum class SourceKind { NIC_PUBLIC_HOME, PUBLIC_PORTAL, EXTERNAL_LINK }

object TenderSources {
 val all=listOf(
  TenderSource("cppp","CPPP / Central eProcurement","https://eprocure.gov.in/eprocure/app",SourceKind.NIC_PUBLIC_HOME),
  TenderSource("tn","Tamil Nadu eProcurement","https://tntenders.gov.in/nicgep/app",SourceKind.NIC_PUBLIC_HOME),
  TenderSource("gem","GeM","https://gem.gov.in",SourceKind.PUBLIC_PORTAL,
   note="Public portal connector; never bypass login/CAPTCHA."),
  TenderSource("maha","Maharashtra eTender","https://mahatenders.gov.in/nicgep/app",SourceKind.NIC_PUBLIC_HOME),
  TenderSource("kerala","Kerala eTenders","https://etenders.kerala.gov.in/nicgep/app",SourceKind.NIC_PUBLIC_HOME),
  TenderSource("karnataka","Karnataka procurement","https://kppp.karnataka.gov.in",SourceKind.PUBLIC_PORTAL)
 )
}
