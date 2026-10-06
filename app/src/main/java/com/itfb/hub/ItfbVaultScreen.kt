package com.itfb.hub

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VaultItem(
    val type: String, // "Link" या "Password"
    val title: String,
    val value1: String, // URL या Username
    val value2: String  // Category या Password
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItfbVaultScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Links, 1 = Passwords, 2 = AI Search
    
    // Links state
    var linkTitle by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }
    val linksList = remember { mutableStateListOf<VaultItem>() }

    // Passwords state
    var siteName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val passwordsList = remember { mutableStateListOf<VaultItem>() }

    // AI Search state
    var aiQuery by remember { mutableStateOf("") }
    var aiAnswer by remember { mutableStateOf("") }
    var isAiLoading by remember { mutableStateOf(false) }

    // AI से डेटा ढूंढने का फंक्शन
    fun askAiVault() {
        if (aiQuery.isBlank()) return
        isAiLoading = true
        aiAnswer = "Searching your vault..."

        coroutineScope.launch {
            try {
                val allData = buildString {
                    append("Saved Links:\n")
                    linksList.forEach { append("- ${it.title}: ${it.value1} (${it.value2})\n") }
                    append("\nSaved Passwords/Credentials:\n")
                    passwordsList.forEach { append("- Site: ${it.title}, User: ${it.value1}, Pass: ${it.value2}\n") }
                }

                val prompt = "You are ITFB Vault Assistant. The user is asking a question about their saved links and passwords. Answer based ONLY on the data provided below.\n\nData:\n$allData\n\nQuestion: $aiQuery"
                
                val model = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = Secrets.GEMINI_API_KEY,
                    systemInstruction = content { text("You are a secure vault assistant.") }
                )
                val response = model.generateContent(prompt)
                aiAnswer = response.text?.trim() ?: "No matching info found."
            } catch (e: Exception) {
                aiAnswer = "AI Error: ${e.message}"
            } finally {
                isAiLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ITFB Vault (Links & Passwords)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Link, contentDescription = null) },
                    label = { Text("Links") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    label = { Text("Passwords") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.SmartToy, contentDescription = null) },
                    label = { Text("AI Assistant") }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when (selectedTab) {
                // Tab 0: Links Manager
                0 -> {
                    Text("Save Links & Open in Native Apps", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = linkTitle, onValueChange = { linkTitle = it }, label = { Text("Title (e.g. YouTube Video)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(value = linkUrl, onValueChange = { linkUrl = it }, label = { Text("URL (https://...)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        if (linkTitle.isNotBlank() && linkUrl.isNotBlank()) {
                            linksList.add(VaultItem("Link", linkTitle.trim(), linkUrl.trim(), "General"))
                            linkTitle = ""
                            linkUrl = ""
                            Toast.makeText(context, "Link Saved!", Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("Save Link")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(linksList) { link ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(link.title, style = MaterialTheme.typography.titleMedium)
                                    Text(link.value1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.value1))
                                                context.startActivity(intent) // सीधे संबंधित ऐप में खुलेगा
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                                            }
                                        }) { Text("Open in App") }
                                        TextButton(onClick = { linksList.remove(link) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                    }
                                }
                            }
                        }
                    }
                }

                // Tab 1: Password Vault
                1 -> {
                    Text("Secure Password & Credential Vault", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = siteName, onValueChange = { siteName = it }, label = { Text("Site / App Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username / Email") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        if (siteName.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                            passwordsList.add(VaultItem("Password", siteName.trim(), username.trim(), password.trim()))
                            siteName = ""
                            username = ""
                            password = ""
                            Toast.makeText(context, "Credential Saved Securely!", Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Lock, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("Save Password")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(passwordsList) { cred ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Site: ${cred.title}", style = MaterialTheme.typography.titleMedium)
                                    Text("User: ${cred.value1}", style = MaterialTheme.typography.bodyMedium)
                                    Text("Pass: ${cred.value2}", style = MaterialTheme.typography.bodyMedium)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Password", cred.value2))
                                            Toast.makeText(context, "Password Copied!", Toast.LENGTH_SHORT).show()
                                        }) { Text("Copy Password") }
                                        TextButton(onClick = { passwordsList.remove(cred) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                    }
                                }
                            }
                        }
                    }
                }

                // Tab 2: AI Vault Assistant
                2 -> {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text("Ask ITFB AI about your Vault", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = aiQuery,
                            onValueChange = { aiQuery = it },
                            label = { Text("Ask (e.g. What is my Gmail password?)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { askAiVault() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            enabled = !isAiLoading
                        ) {
                            if (isAiLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("Ask AI Assistant")
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        if (aiAnswer.isNotBlank()) {
                            OutlinedTextField(
                                value = aiAnswer,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("AI Response") },
                                modifier = Modifier.fillMaxWidth().height(200.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
