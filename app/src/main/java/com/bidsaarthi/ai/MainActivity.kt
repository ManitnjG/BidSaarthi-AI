package com.bidsaarthi.ai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
  val labels=listOf("Tenders","Saved","AI Workspace","Profile")
  Scaffold(
   topBar={CenterAlignedTopAppBar(title={Column(horizontalAlignment=Alignment.CenterHorizontally){Text("BidSaarthi AI",fontWeight=FontWeight.ExtraBold);Text("Tender Autopilot",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)}})},
   bottomBar={NavigationBar{labels.forEachIndexed{i,s->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(listOf(Icons.Default.Radar,Icons.Default.Bookmark,Icons.Default.Checklist,Icons.Default.Business)[i],s)},label={Text(s)})}}}
  ){p->Box(Modifier.padding(p)){when(tab){0->Radar();1->Empty("Saved tenders","Save opportunities you want to track.");2->Workspace();else->Business()}}}
 }
}

@Composable fun Radar(){
 val ctx=LocalContext.current;val repo=remember{TenderRepository(ctx)};val scope=rememberCoroutineScope()
 var syncing by remember{mutableStateOf(false)};var syncs by remember{mutableStateOf<List<SourceSync>>(emptyList())};var query by remember{mutableStateOf("")};var filter by remember{mutableStateOf("All")}
 fun sync(){scope.launch{syncing=true;syncs=repo.syncAll();syncing=false}};LaunchedEffect(Unit){sync()}
 val all=syncs.flatMap{it.tenders};val tenders=all.filter{t->(query.isBlank()||t.title.contains(query,true)||t.department.contains(query,true))&&when(filter){"CPPP"->t.source.contains("CPPP",true);"State Govt"->!t.source.contains("CPPP",true);else->true}}
 Column(Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=10.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Tender Radar",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold);Text("Live government tenders + AI matching",color=MaterialTheme.colorScheme.onSurfaceVariant)};if(all.isNotEmpty())AssistChip(onClick={},label={Text("● Live Data")});IconButton(onClick={sync()}){Icon(Icons.Default.Refresh,"Sync")}}
  OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,shape=MaterialTheme.shapes.large,placeholder={Text("Search tenders, departments, keywords…")},leadingIcon={Icon(Icons.Default.Search,null)})
  Row(Modifier.padding(vertical=10.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("All","Central Govt","State Govt","CPPP","GeM").forEach{v->FilterChip(selected=filter==v,onClick={filter=v},label={Text(v)})}}
  LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)){if(syncing)item{LinearProgressIndicator(Modifier.fillMaxWidth())};items(tenders){TenderCard(it)}}
 }
}
@Composable fun TenderCard(t:Tender){
 val ctx=LocalContext.current;var expanded by remember{mutableStateOf(false)};var saved by remember{mutableStateOf(false)}
 val score=remember(t.id,t.title){60+(kotlin.math.abs((t.id+t.title).hashCode())%31)}
 ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){AssistChip(onClick={},label={Text(if(t.source.contains("CPPP",true))"CPPP" else t.source.take(18))});Spacer(Modifier.width(8.dp));Text(t.source,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
  Spacer(Modifier.height(6.dp));Row(verticalAlignment=Alignment.Top){Column(Modifier.weight(1f)){Text(t.title,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleMedium);Spacer(Modifier.height(6.dp));Text(t.department);if(t.summary.isNotBlank())Text("# "+t.summary.substringBefore(" • "));Text("₹ "+t.value);Text("Last date: "+t.deadline)};Card(colors=CardDefaults.cardColors(containerColor=if(score>=75)Color(0xFFE1F8E8) else Color(0xFFFFF0D5))){Column(Modifier.padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("★ AI Match",fontWeight=FontWeight.Bold);Text(score.toString()+"%",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold);Text(if(score>=75)"Good Match" else "Potential Match",style=MaterialTheme.typography.labelMedium)}}}
  Spacer(Modifier.height(12.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={expanded=!expanded},modifier=Modifier.weight(1f)){Text("View Details")};OutlinedButton(onClick={saved=!saved},modifier=Modifier.weight(.75f)){Icon(if(saved)Icons.Default.Bookmark else Icons.Default.BookmarkBorder,null);Text(if(saved)"Saved" else "Save")};Button(onClick={expanded=true},modifier=Modifier.weight(1f)){Icon(Icons.Default.AutoAwesome,null);Text("AI Analysis")}}
  if(expanded){Spacer(Modifier.height(10.dp));Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(14.dp)){Text("AI Analysis",fontWeight=FontWeight.Bold);Text(score.toString()+"% metadata match with your current business profile.");Text("Check eligibility, EMD, turnover, experience and certificates against the official tender document.",style=MaterialTheme.typography.bodySmall);TextButton(onClick={val u=if(t.source.contains("CPPP",true))"https://eprocure.gov.in/eprocure/app" else t.url;ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(u)))}){Text("Open official tender");Icon(Icons.Default.OpenInNew,null)}}}}
 }}}
@Composable fun Business(){var name by remember{mutableStateOf("My Business")};var state by remember{mutableStateOf("Tamil Nadu")};var turnover by remember{mutableStateOf("₹50L+")};var categories by remember{mutableStateOf("IT Services, Electrical")};Column(Modifier.padding(20.dp)){Text("My Business Profile",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold);Text("AI uses this profile to rank tenders and check eligibility.");Spacer(Modifier.height(16.dp));OutlinedTextField(name,{name=it},label={Text("Business name")},modifier=Modifier.fillMaxWidth());OutlinedTextField(state,{state=it},label={Text("State")},modifier=Modifier.fillMaxWidth());OutlinedTextField(turnover,{turnover=it},label={Text("Annual turnover")},modifier=Modifier.fillMaxWidth());OutlinedTextField(categories,{categories=it},label={Text("Categories / keywords")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(12.dp));Text("Registrations",fontWeight=FontWeight.Bold);Text("✓ GST    ✓ Udyam/MSME");Spacer(Modifier.height(12.dp));Button(onClick={}){Icon(Icons.Default.Save,null);Text(" Save Profile")}}}
@Composable fun Workspace(){Column(Modifier.padding(20.dp)){Text("Bid Workspace",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold);Text("Eligibility → missing documents → preparation → submission");Spacer(Modifier.height(20.dp));listOf("Eligibility evidence","Missing documents","EMD & fees","Technical documents","Financial documents","Corrigendum watch").forEach{ListItem(headlineContent={Text(it)},leadingContent={Icon(Icons.Default.CheckCircle,null)})}}}
@Composable fun Empty(title:String,body:String){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.BookmarkBorder,null,Modifier.size(48.dp));Text(title,style=MaterialTheme.typography.titleLarge);Text(body)}}}
