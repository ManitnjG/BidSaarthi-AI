package com.bidsaarthi.ai.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bidsaarthi.ai.data.FreeAiKeyStore

@Composable fun FreeAiSettings() {
 val context=LocalContext.current;val store=remember { FreeAiKeyStore(context) }
 var configured by remember { mutableStateOf(!store.read().isNullOrBlank()) }
 var input by remember { mutableStateOf("") };var message by remember { mutableStateOf("") }
 OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text("Free AI",fontWeight=FontWeight.Bold)
  Text(if(configured) "OpenRouter key saved on this device" else "Connect OpenRouter to use free generative AI.")
  Text("Uses free models only. An account and API key are required; free quotas and availability vary. Offline basic checks need no key.",style=MaterialTheme.typography.bodySmall)
  TextButton(onClick={runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://openrouter.ai/keys"))) }.onFailure { message="Open openrouter.ai/keys in your browser." }}) { Text("Get an OpenRouter key") }
  OutlinedTextField(input,{input=it},Modifier.fillMaxWidth(),label={Text(if(configured) "Replace key" else "Paste API key")},singleLine=true,visualTransformation=PasswordVisualTransformation())
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   Button(enabled=input.isNotBlank(),onClick={runCatching { store.save(input.trim());input="";configured=true;message="Saved securely. Open a tender and tap Try free AI analysis." }.onFailure { message=it.message ?: "Could not save key" }}) { Text("Save key") }
   if(configured) TextButton(onClick={store.clear();input="";configured=false;message="Key removed."}) { Text("Remove key") }
  }
  if(message.isNotBlank()) Text(message,style=MaterialTheme.typography.bodySmall)
  Text("The saved key is encrypted with Android Keystore. Listing text and your business keywords go directly to OpenRouter when you request AI analysis.",style=MaterialTheme.typography.labelSmall)
 } }
}
