package com.itfb.hub

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.layout.*
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedTextAnalyzerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var inputText by remember { mutableStateOf("") }
    var analysisResult by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Type or paste text to analyze.") }
    var tts: TextToSpeech? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
        }
    }

    DisposableEffect(Unit) {
        onDispose { tts?.stop(); tts?.shutdown() }
    }

    // बेसिक स्टैटिस्टिक्स गणना (ऑफलाइन)
    val wordCount = if (inputText.isBlank()) 0 else inputText.trim().split("\\s+".toRegex()).size
    val charCount = inputText.length
    val paragraphCount = if (inputText.isBlank()) 0 else inputText.lines().filter { it.isNotBlank() }.size
    val readingTimeSeconds = (wordCount / 3) // औसत गति 200 शब्द प्रति मिनट के हिसाब से

    // ITFB AI के ज़रिए डीप एनालिसिस
    fun performAiAnalysis() {
        if (inputText.isBlank()) {
            Toast.makeText(context, "Please enter some text to analyze.", Toast.LENGTH_SHORT).show()
            return
        }
        isLoading = true
        statusText = "ITFB AI is analyzing text..."

        coroutineScope.launch {
            try {
                val prompt = "Analyze the following text. Provide: 1. Sentiment (Positive/Negative/Neutral), 2. Core Theme/Summary, 3. Tone, and 4. Key Takeaways. Make it structured and clear.\n\nText: $inputText"
                val model = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = Secrets.GEMINI_API_KEY,
                    systemInstruction = content { text("You are ITFB Text Analysis Expert AI.") }
                )
                val response = model.generateContent(prompt)
                analysisResult = response.text?.trim() ?: "Could not analyze."
                statusText = "AI Analysis Completed!"
                tts?.speak("AI Analysis completed. Ready to read.", TextToSpeech.QUEUE_FLUSH, null, null)
            } catch (e: Exception) {
                Toast.makeText(context, "AI Error: ${e.message}", Toast.LENGTH_SHORT).show()
                statusText = "AI Analysis Failed."
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced Text Analyzer") },
                navigationIcon = {
                    IconButton(onClick = { tts?.stop(); onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(text = statusText, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Enter or Paste Text Here") },
                modifier = Modifier.fillMaxWidth().height(180.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // बेसिक स्टैटिस्टिक्स कार्ड
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Quick Statistics (Offline):", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Words: $wordCount | Characters: $charCount")
                    Text("• Paragraphs: $paragraphCount | Est. Reading Time: ~${readingTimeSeconds}s")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { performAiAnalysis() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isLoading && inputText.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Analytics, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run ITFB AI Deep Analysis", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (analysisResult.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = analysisResult,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("AI Analysis Report") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ElevatedButton(
                        onClick = { tts?.speak(analysisResult, TextToSpeech.QUEUE_FLUSH, null, null) },
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("Read")
                    }
                    
                    ElevatedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Analysis Report", analysisResult))
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("Copy")
                    }

                    ElevatedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, analysisResult)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share"))
                        },
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("Share")
                    }
                }
            }
        }
    }
}
