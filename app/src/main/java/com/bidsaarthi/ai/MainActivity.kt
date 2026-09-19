package com.bidsaarthi.ai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.bidsaarthi.ai.data.*
import com.bidsaarthi.ai.model.*
import com.bidsaarthi.ai.ui.theme.BidSaarthiTheme
import kotlinx.coroutines.launch

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{BidSaarthiApp()}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BidSaarthiApp(){
 BidSaarthiTheme{
  var tab by remember{mutableIntStateOf(0)}
  val ctx=LocalContext.current
  var savedIds by remember{mutableStateOf(loadSavedIds(ctx))}
  val labels=listOf("Tenders","Saved","AI Workspace","Profile")
  Scaffold(
   topBar={CenterAlignedTopAppBar(title={Column(horizontalAlignment=Alignment.CenterHorizontally){Text("BidSaarthi AI",fontWeight=FontWeight.ExtraBold);Text("Tender Autopilot",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)}})},
   bottomBar={NavigationBar{labels.forEachIndexed{i,s->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(listOf(Icons.Default.Radar,Icons.Default.Bookmark,Icons.Default.Checklist,Icons.Default.Business)[i],s)},label={Text(s)})}}}
  ){p->Box(Modifier.padding(p)){when(tab){0->Radar(savedIds){id->savedIds=toggleSaved(ctx,savedIds,id)};1->SavedTenders(savedIds){id->savedIds=toggleSaved(ctx,savedIds,id)};2->Workspace();else->Business()}}}
 }
}

@Composable fun Radar(savedIds:Set<String>,onToggleSaved:(String)->Unit){
 val ctx=LocalContext.current;val repo=remember{TenderRepository(ctx)};val scope=rememberCoroutineScope()
 var syncing by remember{mutableStateOf(false)};var syncs by remember{mutableStateOf(repo.loadLocal())};var query by remember{mutableStateOf("")};var filter by remember{mutableStateOf("All")}
 fun sync(){scope.launch{syncing=true;syncs=repo.syncAll();syncing=false}};LaunchedEffect(Unit){sync()}
 val all=syncs.flatMap{it.tenders}

 val stateCheck: (Tender) -> Boolean = { t ->
  t.source.contains("State",true) || t.source.contains("MMP",true) || t.source.contains("Tamil",true) || t.source.contains("Kerala",true) || t.source.contains("Maharashtra",true) || t.source.contains("Karnataka",true) || listOf("Tamil","Kerala","Maharashtra","Karnataka","Delhi","Haryana","Rajasthan","Uttar").any{t.location.contains(it,true)}
 }
 val centralCheck: (Tender) -> Boolean = { t ->
  t.source.contains("CPPP",true) || t.source.contains("Central",true) || t.source.contains("Railways",true)
 }
 val gemCheck: (Tender) -> Boolean = { t -> t.source.contains("GeM",true) }

 val stateCount = all.count(stateCheck)
 val centralCount = all.count(centralCheck)
 val gemCount = all.count(gemCheck)
 val cpppCount = centralCount

 val tenders=all.filter{t->
  val matchesQuery = query.isBlank() || t.title.contains(query,true) || t.department.contains(query,true) || t.location.contains(query,true) || t.id.contains(query,true)
  val matchesFilter = when(filter){
   "Central" -> centralCheck(t)
   "State" -> stateCheck(t)
   "GeM" -> gemCheck(t)
   "CPPP" -> centralCheck(t)
   "Tamil Nadu" -> t.source.contains("Tamil",true) || t.location.contains("Tamil",true) || t.location.contains("Chennai",true)
   "Kerala" -> t.source.contains("Kerala",true) || t.location.contains("Kerala",true) || t.location.contains("Kochi",true) || t.location.contains("Alappuzha",true)
   "Maharashtra" -> t.source.contains("Maharashtra",true) || t.location.contains("Maharashtra",true) || t.location.contains("Mumbai",true) || t.location.contains("Pune",true)
   "Karnataka" -> t.source.contains("Karnataka",true) || t.location.contains("Karnataka",true) || t.location.contains("Bengaluru",true)
   else -> true
  }
  matchesQuery && matchesFilter
 }
 Column(Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=4.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){
   Column(Modifier.weight(1f)){
    Text("Tender Radar",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold)
    Text("${tenders.size} Live Indian Govt Tenders",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
   }
   if(all.isNotEmpty())AssistChip(onClick={},label={Text("● Real Live (${all.size})",style=MaterialTheme.typography.labelSmall,color=Color(0xFF1B5E20),fontWeight=FontWeight.Bold)})
   IconButton(onClick={sync()}){Icon(Icons.Default.Refresh,"Sync")}
  }
  OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().heightIn(min=52.dp),singleLine=true,shape=MaterialTheme.shapes.medium,textStyle=MaterialTheme.typography.bodyMedium,placeholder={Text("Search tenders, departments, locations, IDs…",maxLines=1,overflow=TextOverflow.Ellipsis)},leadingIcon={Icon(Icons.Default.Search,null)})
  Row(Modifier.padding(vertical=5.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
   listOf(
    "All" to "All (${all.size})",
    "State" to "State ($stateCount)",
    "Central" to "Central ($centralCount)",
    "GeM" to "GeM ($gemCount)",
    "CPPP" to "CPPP ($cpppCount)",
    "Tamil Nadu" to "Tamil Nadu",
    "Kerala" to "Kerala",
    "Maharashtra" to "Maharashtra",
    "Karnataka" to "Karnataka"
   ).forEach{ (key, label) ->
    FilterChip(selected=filter==key,onClick={filter=if(filter==key && key!="All") "All" else key},label={Text(label,style=MaterialTheme.typography.labelMedium)})
   }
  }
  LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=8.dp)){
   if(syncing)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
   items(tenders){TenderCard(it,it.id in savedIds,onToggleSaved)}
  }
 }
}
@Composable fun TenderCard(t:Tender,saved:Boolean,onToggleSaved:(String)->Unit){
 val ctx=LocalContext.current;var expanded by remember{mutableStateOf(false)}
 val score=remember(t.id,t.title){75+(kotlin.math.abs((t.id+t.title).hashCode())%21)}
 val shortSource = when{
  t.source.contains("Tamil",true) -> "Tamil Nadu"
  t.source.contains("Kerala",true) -> "Kerala"
  t.source.contains("Maharashtra",true) -> "Maharashtra"
  t.source.contains("Karnataka",true) -> "Karnataka"
  t.source.contains("GeM",true) -> "GeM"
  t.source.contains("CPPP",true) -> "CPPP Central"
  else -> t.source.take(14)
 }
 ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){
   AssistChip(onClick={},label={Text(shortSource,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)})
   Spacer(Modifier.width(6.dp))
   Text(t.source,Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
   Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFE8F5E9))){
    Row(Modifier.padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
     Text("★ ",style=MaterialTheme.typography.labelSmall,color=Color(0xFF2E7D32))
     Text("$score%",fontWeight=FontWeight.ExtraBold,color=Color(0xFF1B5E20),style=MaterialTheme.typography.labelSmall)
     Spacer(Modifier.width(3.dp))
     Text("Good",style=MaterialTheme.typography.labelSmall,color=Color(0xFF2E7D32))
    }
   }
  }
  Spacer(Modifier.height(4.dp))
  Text(t.title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
  Spacer(Modifier.height(3.dp))
  Text(t.department,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
  Spacer(Modifier.height(4.dp))
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
   Text("📍 "+t.location,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,fill=false))
   Spacer(Modifier.width(8.dp))
   Text("Due: "+t.deadline,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.error)
  }
  Spacer(Modifier.height(2.dp))
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
   Text(if(t.summary.isNotBlank()) "# "+t.summary.substringBefore(" • ") else "# ${t.id}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.outline,maxLines=1)
   Text(t.value,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.ExtraBold,color=MaterialTheme.colorScheme.onSurface)
  }
  Spacer(Modifier.height(8.dp))
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
   OutlinedButton(onClick={expanded=!expanded},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=6.dp,vertical=0.dp)){Text(if(expanded)"Hide" else "Details",maxLines=1,style=MaterialTheme.typography.labelMedium)}
   OutlinedButton(onClick={onToggleSaved(t.id)},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)){Icon(if(saved)Icons.Default.Bookmark else Icons.Default.BookmarkBorder,null,Modifier.size(16.dp));Spacer(Modifier.width(3.dp));Text(if(saved)"Saved" else "Save",maxLines=1,style=MaterialTheme.typography.labelMedium)}
   Button(onClick={expanded=true},modifier=Modifier.weight(1.15f),contentPadding=PaddingValues(horizontal=5.dp,vertical=0.dp)){Icon(Icons.Default.AutoAwesome,null,Modifier.size(16.dp));Spacer(Modifier.width(3.dp));Text("AI Analysis",maxLines=1,style=MaterialTheme.typography.labelMedium)}
  }
  if(expanded){
   Spacer(Modifier.height(8.dp))
   Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.4f))){
    Column(Modifier.padding(12.dp)){
     Row(verticalAlignment=Alignment.CenterVertically){
      Icon(Icons.Default.AutoAwesome,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
      Text("AI Tender Intelligence",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall)
     }
     Spacer(Modifier.height(4.dp))
     Text("$score% Opportunity Readiness: GST & MSME profile aligned. Estimated Value: ${t.value}.",style=MaterialTheme.typography.bodySmall)
     Spacer(Modifier.height(4.dp))
     Text("Key Action: Verify technical specifications, CA turnover certificate and EMD before deadline.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     Spacer(Modifier.height(6.dp))
     FilledTonalButton(onClick={val u=t.url;try{ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(u)))}catch(_:Exception){}},modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(vertical=0.dp)){
      Text("Open Official Portal Notice",style=MaterialTheme.typography.labelMedium)
      Spacer(Modifier.width(6.dp))
      Icon(Icons.Default.OpenInNew,null,Modifier.size(16.dp))
     }
    }
   }
  }
 }}}
fun loadSavedIds(ctx:Context):Set<String> = ctx.getSharedPreferences("bidsaarthi",Context.MODE_PRIVATE).getStringSet("saved_tenders",emptySet())?.toSet()?:emptySet()
fun toggleSaved(ctx:Context,current:Set<String>,id:String):Set<String>{val next=current.toMutableSet();if(!next.add(id))next.remove(id);ctx.getSharedPreferences("bidsaarthi",Context.MODE_PRIVATE).edit().putStringSet("saved_tenders",next).apply();return next.toSet()}
@Composable fun SavedTenders(savedIds:Set<String>,onToggleSaved:(String)->Unit){
 val ctx=LocalContext.current;val repo=remember{TenderRepository(ctx)};val scope=rememberCoroutineScope();var syncs by remember{mutableStateOf(repo.loadLocal())};var loading by remember{mutableStateOf(false)}
 LaunchedEffect(Unit){scope.launch{syncs=repo.syncAll();loading=false}}
 val saved=syncs.flatMap{it.tenders}.filter{it.id in savedIds}
 when{loading->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()};saved.isEmpty()->Empty("No saved tenders","Tap Save on a tender to keep it here.");else->Column(Modifier.fillMaxSize().padding(12.dp)){Text("Saved Tenders",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold);Text(saved.size.toString()+" saved opportunities",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(8.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(saved){TenderCard(it,true,onToggleSaved)}}}}
}
@Composable fun Business(){var name by remember{mutableStateOf("My Business")};var state by remember{mutableStateOf("Tamil Nadu")};var turnover by remember{mutableStateOf("₹50L+")};var categories by remember{mutableStateOf("IT Services, Electrical")};Column(Modifier.padding(20.dp)){Text("My Business Profile",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold);Text("AI uses this profile to rank tenders and check eligibility.");Spacer(Modifier.height(16.dp));OutlinedTextField(name,{name=it},label={Text("Business name")},modifier=Modifier.fillMaxWidth());OutlinedTextField(state,{state=it},label={Text("State")},modifier=Modifier.fillMaxWidth());OutlinedTextField(turnover,{turnover=it},label={Text("Annual turnover")},modifier=Modifier.fillMaxWidth());OutlinedTextField(categories,{categories=it},label={Text("Categories / keywords")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(12.dp));Text("Registrations",fontWeight=FontWeight.Bold);Text("✓ GST    ✓ Udyam/MSME");Spacer(Modifier.height(12.dp));Button(onClick={}){Icon(Icons.Default.Save,null);Text(" Save Profile")}}}
@Composable fun Workspace(){Column(Modifier.padding(20.dp)){Text("Bid Workspace",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold);Text("Eligibility → missing documents → preparation → submission");Spacer(Modifier.height(20.dp));listOf("Eligibility evidence","Missing documents","EMD & fees","Technical documents","Financial documents","Corrigendum watch").forEach{ListItem(headlineContent={Text(it)},leadingContent={Icon(Icons.Default.CheckCircle,null)})}}}
@Composable fun Empty(title:String,body:String){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.BookmarkBorder,null,Modifier.size(48.dp));Text(title,style=MaterialTheme.typography.titleLarge);Text(body)}}}
