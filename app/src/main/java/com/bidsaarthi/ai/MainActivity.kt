package com.bidsaarthi.ai

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bidsaarthi.ai.data.*
import com.bidsaarthi.ai.model.Tender
import com.bidsaarthi.ai.ui.theme.BidSaarthiTheme
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat

class MainActivity: ComponentActivity() {
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); setContent { BidSaarthiApp() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BidSaarthiApp() {
 val ctx=LocalContext.current
 val store=remember { LocalStore(ctx) }; val repo=remember { TenderRepository(ctx) }; val scope=rememberCoroutineScope()
 var tab by remember { mutableIntStateOf(0) }
 var syncs by remember { mutableStateOf(repo.loadLocal()) }
 var saved by remember { mutableStateOf(store.savedIds()) }
 var syncing by remember { mutableStateOf(false) }
 var message by remember { mutableStateOf<String?>(null) }
 var profile by remember { mutableStateOf(store.profile()) }
 var selected by remember { mutableStateOf<Tender?>(null) }
 fun sync() { if(syncing)return; scope.launch { syncing=true
   try { syncs=repo.syncAll(); message="Open source status for coverage and freshness. Full India coverage is not yet verified." }
   catch(e:Exception) { message="Unable to refresh. Saved listings remain available." }
   finally { syncing=false }
 } }
 LaunchedEffect(Unit) { sync() }
 val all=(syncs.flatMap { it.tenders } + store.tenders().filter { it.id in saved }).associateBy { it.id }.values.toList()
 BidSaarthiTheme {
  Scaffold(
   topBar = {
    TopAppBar(
     title = { Column {
      Text("BidSaarthi", fontWeight=FontWeight.Bold)
      Text("Find opportunities. Prepare with evidence.", style=MaterialTheme.typography.labelSmall)
     } },
     actions = { IconButton(onClick={sync()}, enabled=!syncing) { Icon(Icons.Default.Refresh,"Refresh tenders") } }
    )
   },
   bottomBar = {
    NavigationBar {
     listOf("Discover","Saved","Workspace","Business").forEachIndexed { i,label ->
      NavigationBarItem(
       selected=tab==i, onClick={tab=i},
       icon={Icon(listOf(Icons.Default.Search,Icons.Default.Bookmark,Icons.Default.Checklist,Icons.Default.Business)[i],label)},
       label={Text(label)}
      )
     }
    }
   }
  ) { padding -> Column(Modifier.padding(padding).fillMaxSize()) {
   if(syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
   when(tab) {
    0,1 -> TenderList(all, saved, tab==1, syncs, message, profile, {selected=it}, {t->store.toggle(t);saved=store.savedIds()})
    2 -> Workspace(all.filter { it.id in saved },store) { selected=it }
    else -> Business(profile) { profile=it;store.saveProfile(it) }
   }
  } }
  selected?.let { t -> TenderDetails(t,profile,store,onDismiss={selected=null}) }
 }
}

@Composable fun TenderList(all:List<Tender>,saved:Set<String>,onlySaved:Boolean,syncs:List<SourceSync>,message:String?,profile:JSONObject,onOpen:(Tender)->Unit,onSave:(Tender)->Unit) {
 var query by remember { mutableStateOf("") }; var source by remember { mutableStateOf("All") }
 var region by remember { mutableStateOf("All India") };var regionMenu by remember { mutableStateOf(false) }
 var activeOnly by remember { mutableStateOf(true) }; var soonest by remember { mutableStateOf(true) }
 var showSources by remember { mutableStateOf(false) }
 val now=System.currentTimeMillis()
 val filtered=all.filter { t -> (!onlySaved || t.id in saved) && (source=="All" || t.source==source) &&
  (region=="All India" || t.location.contains(region,true) || TenderSources.all.any { it.name==t.source && it.region==region }) &&
  (query.isBlank() || "${t.title} ${t.department} ${t.location} ${t.summary}".contains(query,true)) &&
  (!activeOnly || onlySaved || (deadlineMillis(t.deadline)?.let { it>=now } ?: true)) }
 val rows=if(soonest) filtered.sortedBy { deadlineMillis(it.deadline) ?: Long.MAX_VALUE } else filtered.sortedBy { it.title }
 LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=20.dp)) {
  item { Spacer(Modifier.height(6.dp)); Text(if(onlySaved) "Your shortlist" else "Tender opportunities",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
   Text("${rows.size} listings · Verify status on the official notice",style=MaterialTheme.typography.bodySmall)
  }
  item { OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("Search work, department or location")},leadingIcon={Icon(Icons.Default.Search,null)}) }
  item { Box {
   OutlinedButton(onClick={regionMenu=true}) { Text(region+" ▾") }
   DropdownMenu(expanded=regionMenu,onDismissRequest={regionMenu=false},modifier=Modifier.heightIn(max=360.dp)) {
    (listOf("All India")+TenderSources.regions).forEach { name -> DropdownMenuItem(text={Text(name)},onClick={region=name;regionMenu=false}) }
   }
  } }
  item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   (listOf("All")+all.map { it.source }.distinct()).forEach { label -> FilterChip(selected=source==label,onClick={source=label},label={Text(label)}) }
  } }
  item { Row(verticalAlignment=Alignment.CenterVertically) {
   if(!onlySaved) { FilterChip(selected=activeOnly,onClick={activeOnly=!activeOnly},label={Text("Hide expired")}); Spacer(Modifier.width(8.dp)) }
   FilterChip(selected=soonest,onClick={soonest=!soonest},label={Text(if(soonest) "Deadline order" else "Title order")})
   Spacer(Modifier.weight(1f)); IconButton(onClick={showSources=!showSources}){Icon(Icons.Default.Info,"Source status")}
  } }
  if(message!=null) item { Text(message,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
  if(showSources) item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   Text("Source freshness",fontWeight=FontWeight.Bold)
   syncs.filter { region=="All India" || it.source.region==region || it.source.region=="India" }.forEach { sourceSync ->
    Text(sourceSync.source.name,fontWeight=FontWeight.SemiBold)
    Text(sourceSync.error.orEmpty(),style=MaterialTheme.typography.bodySmall)
    if(sourceSync.portalTotal!=null) Text("Portal reports ${sourceSync.portalTotal} tenders; this is not a claim that all have been collected.",style=MaterialTheme.typography.labelSmall)
    Text(sourceSync.coverage+" · "+sourceSync.checkedAt.ifBlank { "Check time unknown" },style=MaterialTheme.typography.labelSmall)
    val context=LocalContext.current
    TextButton(onClick={runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(sourceSync.source.baseUrl))) }}) { Text("Open source") }
   }
  } } }
  if(rows.isEmpty()) item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp)) { Text(if(onlySaved) "No saved tenders yet" else "No matching listings",fontWeight=FontWeight.Bold);Text(if(onlySaved) "Save a tender to keep its details available offline." else "No indexed listings match. Open source status to visit the official portal, or refresh for updates.") } } }
  items(rows,key={it.id}) { t -> TenderCard(t,t.id in saved,profile,{onOpen(t)},{onSave(t)}) }
 }
}

@Composable fun TenderCard(t:Tender,saved:Boolean,profile:JSONObject,onOpen:()->Unit,onSave:()->Unit) {
 val due=deadlineMillis(t.deadline);val remaining=due?.minus(System.currentTimeMillis())
 val keywords=profile.optJSONArray("categories") ?: JSONArray()
 val matches=(0 until keywords.length()).map { keywords.optString(it) }.filter { it.isNotBlank() && "${t.title} ${t.department}".contains(it,true) }
 ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text(t.source,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
  Text(t.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
  Text(t.department,style=MaterialTheme.typography.bodySmall)
  Text(t.location+" · "+t.value,style=MaterialTheme.typography.bodySmall)
  Text(when { remaining==null -> "Deadline: ${t.deadline} — verify";remaining<0 -> "Deadline passed · ${t.deadline}";remaining<=172800000L -> "Due within 48 hours · ${t.deadline}";else -> "Due ${t.deadline}" },style=MaterialTheme.typography.labelMedium,color=if(remaining!=null && remaining<172800000L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
  if(matches.isNotEmpty()) Text("Keyword match: ${matches.joinToString()} · eligibility not assessed",style=MaterialTheme.typography.labelSmall)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   FilledTonalButton(onClick=onOpen,modifier=Modifier.weight(1f)){Text("Details & analysis")}
   IconToggleButton(checked=saved,onCheckedChange={onSave()}){Icon(if(saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,if(saved) "Remove saved tender" else "Save tender")}
  }
 } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TenderDetails(t:Tender,profile:JSONObject,store:LocalStore,onDismiss:()->Unit) {
 val ctx=LocalContext.current;val scope=rememberCoroutineScope()
 var result by remember(t.id) { mutableStateOf<JSONObject?>(null) };var loading by remember { mutableStateOf(false) };var error by remember { mutableStateOf<String?>(null) }
 ModalBottomSheet(onDismissRequest=onDismiss) {
  LazyColumn(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=36.dp)) {
   item { Text(t.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(t.department);Text("Deadline: ${t.deadline}") }
   item { Text("Reference: ${t.summary}",style=MaterialTheme.typography.bodySmall)
    Button(onClick={runCatching { val uri=Uri.parse(t.url);require(uri.scheme=="https" || uri.scheme=="http");ctx.startActivity(Intent(Intent.ACTION_VIEW,uri)) }.onFailure { error="Could not open the official link." }},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.OpenInNew,null);Spacer(Modifier.width(8.dp));Text(if(t.url.contains("tendersfullview",true) || t.url.contains("directlink",true) || t.url.contains("/tender/",true)) "Open official notice" else "Open source portal") }
   }
   item {
    Text("Some government notice links expire. If a link is invalid, open the source portal and search using the reference.",style=MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     TextButton(onClick={
      val clipboard=ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      clipboard.setPrimaryClip(ClipData.newPlainText("Tender reference",t.summary.substringBefore(" • ").ifBlank { t.id }))
     }) { Text("Copy reference") }
     TextButton(onClick={
      val url=TenderSources.all.firstOrNull { it.name==t.source }?.baseUrl ?: "https://eprocure.gov.in/cppp/latestactivetendersnew/cpppdata"
      runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }.onFailure { error="Unable to open the source portal." }
     }) { Text("Source portal") }
    }
   }
   item { HorizontalDivider();Text("Free tender checks & AI",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
    Text("Analysis uses available listing text. Full tender documents must be checked for eligibility, EMD, exemptions and amendments.",style=MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick={error=null;result=basicAnalysis(t,profile)},modifier=Modifier.fillMaxWidth()){Text("Free offline check · no key needed")}
    FilledTonalButton(enabled=!loading,onClick={scope.launch { loading=true;error=null
     try {
      result=BackendApi(BuildConfig.BACKEND_URL).analyze(t,profile)
     }
     catch(e:Exception) { result=basicAnalysis(t,profile,"Online AI is unavailable. Showing offline checks.");error=e.message ?: "Analysis failed. Please retry." }
     finally { loading=false }
    }},modifier=Modifier.fillMaxWidth()){Text(if(loading) "Analyzing listing…" else "Try free AI analysis")}
    if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
   }
   result?.let { r ->
    item { Text(if(r.optString("analysis_mode")=="RULE_BASED") "Free basic check · not AI" else if(r.optString("analysis_mode")=="AI") "AI analysis · verify full documents" else "Analysis unavailable",fontWeight=FontWeight.Bold)
     Text(r.optString("summary")); Text("Eligibility: not confirmed",style=MaterialTheme.typography.labelMedium); }
    for(key in listOf("eligibility_reasons","missing_documents","risks")) {
     val a=r.optJSONArray(key) ?: JSONArray()
     if(a.length()>0) item { Text(key.replace('_',' ').replaceFirstChar { it.uppercase() },fontWeight=FontWeight.Bold);for(i in 0 until a.length()) Text("• "+a.optString(i)) }
    }
    val evidence=r.optJSONObject("evidence")
    if(evidence!=null && evidence.length()>0) item { Text("Supporting excerpts",fontWeight=FontWeight.Bold);evidence.keys().forEach { key -> Text("${key.replace('_',' ')}: ${evidence.optString(key)}",style=MaterialTheme.typography.bodySmall) } }
   }
   item { Text("Preparation checklist",fontWeight=FontWeight.Bold);PreparationChecklist(t.id,store) }
  }
 }
}

@Composable fun PreparationChecklist(id:String,store:LocalStore) {
 var checked by remember(id) { mutableStateOf(store.checklist(id)) }
 listOf("Read official tender and amendments","Verify eligibility and exemptions","Confirm EMD, fees and deadline","Prepare technical documents","Review financial bid").forEach { label ->
  Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(checked=label in checked,onCheckedChange={yes->checked=if(yes) checked+label else checked-label;store.setChecklist(id,checked)});Text(label,style=MaterialTheme.typography.bodyMedium) }
 }
}
@Composable fun Workspace(tenders:List<Tender>,store:LocalStore,onOpen:(Tender)->Unit) {
 LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  item { Text("Bid workspace",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Your saved opportunities and preparation progress.") }
  if(tenders.isEmpty()) item { Text("Save a tender from Discover to start preparing.") }
  items(tenders,key={it.id}) { t -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(t.title,fontWeight=FontWeight.Bold);PreparationChecklist(t.id,store);TextButton(onClick={onOpen(t)}){Text("Review notice & analysis")} } } }
 }
}
@Composable fun Business(initial:JSONObject,onSave:(JSONObject)->Unit) {
 var name by remember { mutableStateOf(initial.optString("name")) };var state by remember { mutableStateOf(initial.optJSONArray("states")?.optString(0).orEmpty()) }
 var turnover by remember { mutableStateOf(if(initial.isNull("turnover")) "" else initial.optString("turnover")) }
 var categories by remember { mutableStateOf(initial.optJSONArray("categories")?.let { a->(0 until a.length()).joinToString(", "){a.optString(it)} }.orEmpty()) }
 var gst by remember { mutableStateOf(initial.optBoolean("has_gst",false)) };var udyam by remember { mutableStateOf(initial.optBoolean("has_udyam",false)) }
 var message by remember { mutableStateOf("") }
 LazyColumn(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  item { Text("Your business",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Enter accurate details for tender relevance and analysis.") }
  item { OutlinedTextField(name,{name=it;message=""},Modifier.fillMaxWidth(),label={Text("Business name")},singleLine=true) }
  item { OutlinedTextField(state,{state=it;message=""},Modifier.fillMaxWidth(),label={Text("State")},singleLine=true) }
  item { OutlinedTextField(turnover,{turnover=it;message=""},Modifier.fillMaxWidth(),label={Text("Annual turnover in rupees (optional)")},supportingText={Text("Example: 5000000 for ₹50 lakh")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true) }
  item { OutlinedTextField(categories,{categories=it;message=""},Modifier.fillMaxWidth(),label={Text("Work keywords, separated by commas")},supportingText={Text("Example: electrical, solar, housekeeping")}) }
  item { Row(verticalAlignment=Alignment.CenterVertically){Checkbox(gst,{gst=it;message=""});Text("GST registered")};Row(verticalAlignment=Alignment.CenterVertically){Checkbox(udyam,{udyam=it;message=""});Text("Udyam / MSME registered")} }
  item { Button(onClick={
   val amount=turnover.replace(",", "").trim().toDoubleOrNull()
   if(turnover.isNotBlank() && (amount==null || !amount.isFinite() || amount<0)) message="Enter a valid turnover amount in rupees."
   else { onSave(JSONObject().put("name",name.trim()).put("states",JSONArray(listOf(state.trim()).filter { it.isNotBlank() })).put("turnover",amount ?: JSONObject.NULL).put("categories",JSONArray(categories.split(',').map { it.trim() }.filter { it.isNotBlank() })).put("has_gst",gst).put("has_udyam",udyam));message="Profile saved on this device." }
  },modifier=Modifier.fillMaxWidth()){Text("Save business profile")};Text(message,style=MaterialTheme.typography.bodySmall)
  Text("AI analysis is provided securely by the BidSaarthi backend. No API key is required in the app.",style=MaterialTheme.typography.bodySmall) }
 }
}
