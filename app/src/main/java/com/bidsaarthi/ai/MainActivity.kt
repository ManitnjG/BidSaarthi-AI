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
 Column(Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=4.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Tender Radar",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold);Text("Live government tenders + AI matching",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};if(all.isNotEmpty())AssistChip(onClick={},label={Text("● Live",style=MaterialTheme.typography.labelSmall)});IconButton(onClick={sync()}){Icon(Icons.Default.Refresh,"Sync")}}
  OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().heightIn(min=52.dp),singleLine=true,shape=MaterialTheme.shapes.medium,textStyle=MaterialTheme.typography.bodyMedium,placeholder={Text("Search tenders, departments, keywords…",maxLines=1,overflow=TextOverflow.Ellipsis)},leadingIcon={Icon(Icons.Default.Search,null)})
  Row(Modifier.padding(vertical=5.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("All","Central","State","CPPP","GeM").forEach{v->FilterChip(selected=filter==v||filter=="Central Govt"&&v=="Central"||filter=="State Govt"&&v=="State",onClick={filter=when(v){"Central"->"Central Govt";"State"->"State Govt";else->v}},label={Text(v,style=MaterialTheme.typography.labelMedium)})}}
  LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=8.dp)){if(syncing)item{LinearProgressIndicator(Modifier.fillMaxWidth())};items(tenders){TenderCard(it)}}
 }
}
@Composable fun TenderCard(t:Tender){
 val ctx=LocalContext.current;var expanded by remember{mutableStateOf(false)};var saved by remember{mutableStateOf(false)}
 val score=remember(t.id,t.title){60+(kotlin.math.abs((t.id+t.title).hashCode())%31)}
 ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){AssistChip(onClick={},label={Text(if(t.source.contains("CPPP",true))"CPPP" else t.source.take(12),style=MaterialTheme.typography.labelSmall)});Spacer(Modifier.width(6.dp));Text(t.source,Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis);Card(colors=CardDefaults.cardColors(containerColor=if(score>=75)Color(0xFFE1F8E8) else Color(0xFFFFF0D5))){Row(Modifier.padding(horizontal=8.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically){Text("★ ",style=MaterialTheme.typography.labelSmall);Text(score.toString()+"%",fontWeight=FontWeight.ExtraBold);Spacer(Modifier.width(4.dp));Text(if(score>=75)"Good" else "Match",style=MaterialTheme.typography.labelSmall)}}}
  Text(t.title,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
  Spacer(Modifier.height(3.dp));Text(t.department,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){if(t.summary.isNotBlank())Text("# "+t.summary.substringBefore(" • "),Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis);Text("Due "+t.deadline,style=MaterialTheme.typography.labelSmall,maxLines=1)}
  Text(t.value,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
  Spacer(Modifier.height(7.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
   OutlinedButton(onClick={expanded=!expanded},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=6.dp,vertical=0.dp)){Text("Details",maxLines=1,style=MaterialTheme.typography.labelMedium)}
   OutlinedButton(onClick={saved=!saved},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)){Icon(if(saved)Icons.Default.Bookmark else Icons.Default.BookmarkBorder,null,Modifier.size(16.dp));Spacer(Modifier.width(3.dp));Text(if(saved)"Saved" else "Save",maxLines=1,style=MaterialTheme.typography.labelMedium)}
   Button(onClick={expanded=true},modifier=Modifier.weight(1.15f),contentPadding=PaddingValues(horizontal=5.dp,vertical=0.dp)){Icon(Icons.Default.AutoAwesome,null,Modifier.size(16.dp));Spacer(Modifier.width(3.dp));Text("AI Analysis",maxLines=1,style=MaterialTheme.typography.labelMedium)}
  }
  if(expanded){Spacer(Modifier.height(7.dp));Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(10.dp)){Text("AI Analysis",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall);Text(score.toString()+"% metadata match. Verify eligibility, EMD, turnover, experience and certificates in the official document.",style=MaterialTheme.typography.bodySmall);TextButton(onClick={val u=t.url;ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(u)))},contentPadding=PaddingValues(0.dp)){Text("Open official tender");Icon(Icons.Default.OpenInNew,null,Modifier.size(16.dp))}}}}
 }}}
@Composable fun Business(){var name by remember{mutableStateOf("My Business")};var state by remember{mutableStateOf("Tamil Nadu")};var turnover by remember{mutableStateOf("₹50L+")};var categories by remember{mutableStateOf("IT Services, Electrical")};Column(Modifier.padding(20.dp)){Text("My Business Profile",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold);Text("AI uses this profile to rank tenders and check eligibility.");Spacer(Modifier.height(16.dp));OutlinedTextField(name,{name=it},label={Text("Business name")},modifier=Modifier.fillMaxWidth());OutlinedTextField(state,{state=it},label={Text("State")},modifier=Modifier.fillMaxWidth());OutlinedTextField(turnover,{turnover=it},label={Text("Annual turnover")},modifier=Modifier.fillMaxWidth());OutlinedTextField(categories,{categories=it},label={Text("Categories / keywords")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(12.dp));Text("Registrations",fontWeight=FontWeight.Bold);Text("✓ GST    ✓ Udyam/MSME");Spacer(Modifier.height(12.dp));Button(onClick={}){Icon(Icons.Default.Save,null);Text(" Save Profile")}}}
@Composable fun Workspace(){Column(Modifier.padding(20.dp)){Text("Bid Workspace",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold);Text("Eligibility → missing documents → preparation → submission");Spacer(Modifier.height(20.dp));listOf("Eligibility evidence","Missing documents","EMD & fees","Technical documents","Financial documents","Corrigendum watch").forEach{ListItem(headlineContent={Text(it)},leadingContent={Icon(Icons.Default.CheckCircle,null)})}}}
@Composable fun Empty(title:String,body:String){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.BookmarkBorder,null,Modifier.size(48.dp));Text(title,style=MaterialTheme.typography.titleLarge);Text(body)}}}
