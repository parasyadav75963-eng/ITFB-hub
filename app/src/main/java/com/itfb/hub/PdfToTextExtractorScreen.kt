package com.itfb.hub

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToTextExtractorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var extractedText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Select a PDF to extract text & use AI.") }
    var tts: TextToSpeech? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        PDFBoxResourceLoader.init(context)
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
        }
    }

    DisposableEffect(Unit) {
        onDispose { tts?.stop(); tts?.shutdown() }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            statusText = "Extracting text from PDF... Please wait."
            extractedText = ""
            
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val doc = PDDocument.load(context.contentResolver.openInputStream(uri))
                    val stripper = PDFTextStripper()
                    val text = stripper.getText(doc)
                    doc.close()
                    
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        if (text.isNotBlank()) {
                            extractedText = text
                            statusText = "Text Extracted Successfully!"
                            tts?.speak("Text Extracted from PDF. Ready to read or analyze.", TextToSpeech.QUEUE_FLUSH, null, null)
                        } else {
                            statusText = "No readable text found in this PDF."
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        statusText = "Error extracting text: ${e.localizedMessage}"
                    }
                }
            }
        }
    }

    // ITFB AI के ज़रिए टेक्स्ट साफ़ करने या समरी बनाने का फंक्शन
    fun processWithAi(isSummary: Boolean) {
        if (extractedText.isBlank()) {
            Toast.makeText(context, "No text available to process.", Toast.LENGTH_SHORT).show()
            return
        }
        isLoading = true
        statusText = if (isSummary) "ITFB AI is generating summary..." else "ITFB AI is cleaning text..."

        coroutineScope.launch {
            try {
                val instruction = if (isSummary) {
                    "You are ITFB AI. Read the following text extracted from a PDF and provide a comprehensive, clear summary of key points."
                } else {
                    "You are ITFB AI. Fix any formatting, spelling, or structural errors in the following extracted PDF text and make it clean and professional."
                }
                
                val model = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = Secrets.GEMINI_API_KEY,
                    systemInstruction = content { text(instruction) }
                )
                val response = model.generateContent(extractedText)
                extractedText = response.text?.trim() ?: extractedText
                statusText = if (isSummary) "Summary Generated Successfully!" else "Text Cleaned Successfully!"
                tts?.speak(if (isSummary) "Summary generated." else "Text cleaned.", TextToSpeech.QUEUE_FLUSH, null, null)
            } catch (e: Exception) {
                Toast.makeText(context, "AI Error: ${e.message}", Toast.LENGTH_SHORT).show()
                statusText = "AI Processing Failed."
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF to Text & ITFB AI") },
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

            ElevatedButton(
                onClick = { pdfPicker.launch("application/pdf") },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select PDF File", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (extractedText.isNotBlank()) {
                // AI Action Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ElevatedButton(
                        onClick = { processWithAi(false) },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clean Text (AI)")
                    }
                    ElevatedButton(
                        onClick = { processWithAi(true) },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    ) {
                        Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Get Summary (AI)")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = extractedText,
                    onValueChange = { extractedText = it },
                    label = { Text("Extracted / AI Processed Text") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 250.dp, max = 400.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ElevatedButton(
                        onClick = { tts?.speak(extractedText, TextToSpeech.QUEUE_FLUSH, null, null) },
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("Read")
                    }
                    
                    ElevatedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("PDF Text", extractedText))
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
                                putExtra(Intent.EXTRA_TEXT, extractedText)
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
