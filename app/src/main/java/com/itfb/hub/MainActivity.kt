package com.itfb.hub

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf("home") }
    var userName by remember { mutableStateOf<String?>(null) }
    var userEmail by remember { mutableStateOf<String?>(null) }

    when (currentScreen) {
        "home" -> ItfbHubApp(
            userName = userName,
            onNavigate = { screen -> currentScreen = screen },
            onProfileClick = { currentScreen = "profile" }
        )
        "itfb_ai" -> ItfbAiScreen(onBack = { currentScreen = "home" })
        "pdf_editor" -> AccessiblePdfEditorScreen(onBack = { currentScreen = "home" })
        "text_to_pdf" -> TextToPDFGeneratorScreen(onBack = { currentScreen = "home" })
        "itfb_studio" -> ItfbStudioScreen(onBack = { currentScreen = "home" })
        "audio_magic" -> ItfbAudioMagicScreen(onBack = { currentScreen = "home" })
        "vision_studio" -> ItfbVisionStudioScreen(onBack = { currentScreen = "home" })
        "pdf_to_text_extractor" -> PdfToTextExtractorScreen(onBack = { currentScreen = "home" })
        "text_analyzer" -> AdvancedTextAnalyzerScreen(onBack = { currentScreen = "home" })
        "itfb_vault" -> ItfbVaultScreen(onBack = { currentScreen = "home" })
        "profile" -> ProfileScreen(
            userName = userName,
            userEmail = userEmail,
            onSignOut = {
                userName = null
                userEmail = null
            },
            onBack = { currentScreen = "home" }
        )
        "settings" -> SettingsScreen(onBack = { currentScreen = "home" })
        "about" -> AboutScreen(onBack = { currentScreen = "home" })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItfbHubApp(
    userName: String?,
    onNavigate: (String) -> Unit,
    onProfileClick: () -> Unit
) {
    val context = LocalContext.current

    val toolsList = listOf(
        "ITFB AI",
        "Accessible PDF Editor",
        "Text to PDF Generator",
        "ITFB Studio",
        "AI Vocal & Instrumental Remover",
        "ITFB Vision Studio (OCR & AI Image)",
        "PDF to Text & AI Summarizer",
        "Advanced Text Analyzer",
        "ITFB Vault (Links & Passwords)"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (userName != null) "Hi, $userName" else "ITFB Hub") },
                actions = {
                    // Google Play Store / Drive स्टाइल क्लीन प्रोफाइल आइकॉन (Top-Right Corner)
                    IconButton(onClick = onProfileClick) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = if (userName != null) "Google Account Profile: Signed in as $userName" else "Sign In / Account Settings",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Available Tools:",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(toolsList) { toolName ->
                    ElevatedButton(
                        onClick = { 
                            when (toolName) {
                                "ITFB AI" -> onNavigate("itfb_ai")
                                "Accessible PDF Editor" -> onNavigate("pdf_editor")
                                "Text to PDF Generator" -> onNavigate("text_to_pdf")
                                "ITFB Studio" -> onNavigate("itfb_studio")
                                "AI Vocal & Instrumental Remover" -> onNavigate("audio_magic")
                                "ITFB Vision Studio (OCR & AI Image)" -> onNavigate("vision_studio")
                                "PDF to Text & AI Summarizer" -> onNavigate("pdf_to_text_extractor")
                                "Advanced Text Analyzer" -> onNavigate("text_analyzer")
                                "ITFB Vault (Links & Passwords)" -> onNavigate("itfb_vault")
                                else -> Toast.makeText(context, "$toolName is coming soon!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .height(64.dp)
                    ) {
                        Text(text = toolName, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
