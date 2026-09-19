package com.bidsaarthi.ai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
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
  val labels=listOf("Tenders","Saved","AI Workspace","Profile")
  Scaffold(
   topBar={CenterAlignedTopAppBar(title={Column(horizontalAlignment=Alignment.CenterHorizontally){Text("BidSaarthi AI",fontWeight=FontWeight.ExtraBold);Text("Tender Autopilot",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)}})},
   bottomBar={NavigationBar{labels.forEachIndexed{i,s->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(listOf(Icons.Default.Radar,Icons.Default.Bookmark,Icons.Default.Checklist,Icons.Default.Business)[i],s)},label={Text(s)})}}}
  ){p->Box(Modifier.padding(p)){when(tab){0->Radar();1->Empty("Saved tenders","Save opportunities you want to track.");2->Workspace();else->Business()}}}
 }
}

@Composable fun Radar(){
 val ctx=LocalContext.current; val repo=remember{TenderRepository(ctx)}; val scope=rememberCoroutineScope()
 var syncing by remember{mutableStateOf(false)}; var syncs by remember{mutableStateOf<List<SourceSync>>(emptyList())}
 var query by remember{mutableStateOf("")}
 fun sync(){scope.launch{syncing=true;syncs=repo.syncAll();syncing=false}}
 LaunchedEffect(Unit){sync()}
 val tenders=syncs.flatMap{it.tenders}.filter{query.isBlank()||it.title.contains(query,true)||it.department.contains(query,true)}
 Column(Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=12.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Tender Radar",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold);Text("Live government tenders + AI matching",color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton(onClick={sync()}){Icon(Icons.Default.Refresh,"Sync")}}
  OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,shape=MaterialTheme.shapes.large,placeholder={Text("Search tenders or departments")},leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={ if(query.isNotEmpty()) IconButton(onClick={query=""}){Icon(Icons.Default.Close,"Clear")} })
  Spacer(Modifier.height(12.dp))
  LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){
   item{ElevatedCard(Modifier.fillMaxWidth()){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(10.dp));Column{Text("${tenders.size} tenders available",fontWeight=FontWeight.Bold);Text("Open any tender and tap AI Analysis")}}}}
   item{SourcePanel(syncs,syncing)}
   if(tenders.isEmpty()&&!syncing) item{Text("No structured public listings received. Use source buttons below to verify directly; CAPTCHA-protected search is never bypassed.")}
   items(tenders){TenderCard(it)}
  }
 }
}

@Composable fun SourcePanel(syncs:List<SourceSync>,syncing:Boolean){
 val ctx=LocalContext.current
 ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){
  Text(if(syncing)"Syncing official sources…" else "Official source status",fontWeight=FontWeight.Bold)
  TenderSources.all.forEach { s ->
   val st=syncs.firstOrNull{it.source.id==s.id}
   ListItem(headlineContent={Text(s.name)},supportingContent={Text(when{syncing->"Checking…";st==null->"Waiting";st.tenders.isNotEmpty()->"${st.tenders.size} live public listings";else->st.error?:"Connected • no parsed listings"})},
    trailingContent={IconButton(onClick={ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(s.baseUrl)))}){Icon(Icons.Default.OpenInNew,"Official source")}})
  }
 }}
}

@Composable fun TenderCard(t:Tender){val ctx=LocalContext.current;var expanded by remember{mutableStateOf(false)};ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){AssistChip(onClick={},label={Text(t.source)});Text(t.title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Text(t.department);Text("Closes: ${t.deadline}");Spacer(Modifier.height(6.dp));Text(t.summary)
Spacer(Modifier.height(8.dp))
OutlinedButton(onClick={expanded=!expanded}){Icon(Icons.Default.AutoAwesome,null);Text(if(expanded)" Hide AI Analysis" else " AI Analysis")}
if(expanded){ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("AI Analysis",fontWeight=FontWeight.Bold);Text("AI status: Evidence analysis requires the live BidSaarthi backend.");Text("This APK will not invent an eligibility verdict or score.");Text("Tender reference: ${t.summary}");Text("Open the official tender document for requirements until the AI backend is reachable.",style=MaterialTheme.typography.bodySmall)}}}
Button(onClick={
 val sourceHome=when {
  t.source.contains("CPPP",true)->"https://eprocure.gov.in/eprocure/app"
  t.source.contains("Tamil Nadu",true)->"https://tntenders.gov.in/nicgep/app"
  t.source.contains("Maharashtra",true)->"https://mahatenders.gov.in/nicgep/app"
  t.source.contains("Kerala",true)->"https://etenders.kerala.gov.in/nicgep/app"
  else->t.url
 }
 ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(sourceHome)))
}){Text("Open official portal");Icon(Icons.Default.OpenInNew,null)}
Text("Tender details are shown here in BidSaarthi. Official portal links open a fresh session because government detail URLs can expire.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}}}
@Composable fun Business(){var name by remember{mutableStateOf("My Business")};var state by remember{mutableStateOf("Tamil Nadu")};var turnover by remember{mutableStateOf("₹50L+")};var categories by remember{mutableStateOf("IT Services, Electrical")};Column(Modifier.padding(20.dp)){Text("My Business Profile",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold);Text("AI uses this profile to rank tenders and check eligibility.");Spacer(Modifier.height(16.dp));OutlinedTextField(name,{name=it},label={Text("Business name")},modifier=Modifier.fillMaxWidth());OutlinedTextField(state,{state=it},label={Text("State")},modifier=Modifier.fillMaxWidth());OutlinedTextField(turnover,{turnover=it},label={Text("Annual turnover")},modifier=Modifier.fillMaxWidth());OutlinedTextField(categories,{categories=it},label={Text("Categories / keywords")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(12.dp));Text("Registrations",fontWeight=FontWeight.Bold);Text("✓ GST    ✓ Udyam/MSME");Spacer(Modifier.height(12.dp));Button(onClick={}){Icon(Icons.Default.Save,null);Text(" Save Profile")}}}
@Composable fun Workspace(){Column(Modifier.padding(20.dp)){Text("Bid Workspace",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold);Text("Eligibility → missing documents → preparation → submission");Spacer(Modifier.height(20.dp));listOf("Eligibility evidence","Missing documents","EMD & fees","Technical documents","Financial documents","Corrigendum watch").forEach{ListItem(headlineContent={Text(it)},leadingContent={Icon(Icons.Default.CheckCircle,null)})}}}
@Composable fun Empty(title:String,body:String){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.BookmarkBorder,null,Modifier.size(48.dp));Text(title,style=MaterialTheme.typography.titleLarge);Text(body)}}}
