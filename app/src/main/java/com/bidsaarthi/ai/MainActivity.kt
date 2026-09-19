package com.bidsaarthi.ai

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bidsaarthi.ai.data.TenderRepository
import com.bidsaarthi.ai.model.*

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{BidSaarthiApp()}}
}

@Composable fun BidSaarthiApp(){
 MaterialTheme(colorScheme=lightColorScheme()){
  var tab by remember{mutableIntStateOf(0)}
  val labels=listOf("Radar","Saved","Workspace","Business")
  Scaffold(
   topBar={TopAppBar(title={Column{Text("BidSaarthi AI",fontWeight=FontWeight.Bold);Text("Tender Autopilot",style=MaterialTheme.typography.labelSmall)}})},
   bottomBar={NavigationBar{labels.forEachIndexed{i,s->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(listOf(Icons.Default.Radar,Icons.Default.Bookmark,Icons.Default.Checklist,Icons.Default.Business)[i],s)},label={Text(s)})}}}
  ){p->Box(Modifier.padding(p)){when(tab){0->Radar();1->Empty("Saved tenders","Save opportunities you want to track.");2->Workspace();else->Business()}}}
 }
}

@Composable fun Radar(){
 val jobs=remember{TenderRepository().starterTenders()}
 Column(Modifier.fillMaxSize().padding(16.dp)){
  Text("Opportunity Radar",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
  Text("Relevant opportunities based on your Business DNA")
  Spacer(Modifier.height(16.dp))
  OutlinedTextField("",{},Modifier.fillMaxWidth(),placeholder={Text("Search tender, department or category")},leadingIcon={Icon(Icons.Default.Search,null)})
  Spacer(Modifier.height(16.dp))
  LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)){items(jobs){TenderCard(it)}}
 }
}
@Composable fun TenderCard(t:Tender){ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){AssistChip(onClick={},label={Text(t.source)});Text(t.title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge);Text(t.department);Spacer(Modifier.height(8.dp));Text(t.summary);Spacer(Modifier.height(12.dp));LinearProgressIndicator(progress={t.readiness/100f},Modifier.fillMaxWidth());Text(if(t.readiness==0)"Source setup required" else "${t.readiness}% bid ready",fontWeight=FontWeight.SemiBold)}}}
@Composable fun Business(){val p=remember{BusinessProfile()};Column(Modifier.padding(20.dp)){Text("Business DNA",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("Set up once. BidSaarthi uses this profile to filter opportunities.");Spacer(Modifier.height(20.dp));ElevatedCard{Column(Modifier.padding(18.dp)){Text(p.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("${p.state} • ${p.turnover}");Spacer(Modifier.height(12.dp));Text("Readiness ${p.readiness}%");LinearProgressIndicator(progress={p.readiness/100f},Modifier.fillMaxWidth());Spacer(Modifier.height(12.dp));Text("✓ GST   ✓ Udyam");Text("Categories: ${p.categories.joinToString()}")}};Spacer(Modifier.height(16.dp));Button({},Modifier.fillMaxWidth()){Icon(Icons.Default.UploadFile,null);Spacer(Modifier.width(8.dp));Text("Import business documents")}}}
@Composable fun Workspace(){Column(Modifier.padding(20.dp)){Text("Bid Workspace",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("Eligibility → missing documents → preparation → submission");Spacer(Modifier.height(20.dp));listOf("Eligibility evidence","Missing documents","EMD & fees","Technical documents","Financial documents","Corrigendum watch").forEach{ListItem(headlineContent={Text(it)},leadingContent={Icon(Icons.Default.CheckCircle,null)})}}}
@Composable fun Empty(title:String,body:String){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.BookmarkBorder,null,Modifier.size(48.dp));Text(title,style=MaterialTheme.typography.titleLarge);Text(body)}}}
