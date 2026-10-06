package com.itfb.hub

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToPdfGeneratorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var pdfTitle by remember { mutableStateOf("") }
    var pdfContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loadingStatus by remember { mutableStateOf("") }
    var generatedFile by remember { mutableStateOf<File?>(null) }
    
    // AI Chat Dialog के लिए स्टेट
    var showAiChatDialog by remember { mutableStateOf(false) }
    var aiUserQuestion by remember { mutableStateOf("") }
    var aiChatResponse by remember { mutableStateOf("") }
    var isAiThinking by remember { mutableStateOf(false) }

    // Save PDF Launcher
    val saveDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && generatedFile != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        generatedFile!!.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    withContext(Dispatchers.Main) { Toast.makeText(context, "PDF Saved Successfully!", Toast.LENGTH_LONG).show() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Error saving file.", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    // PDF बनाने का फंक्शन
    fun generatePdf() {
        if (pdfTitle.isBlank() || pdfContent.isBlank()) {
            Toast.makeText(context, "Please enter both Title and Content.", Toast.LENGTH_SHORT).show()
            return
        }
        isLoading = true
        loadingStatus = "Generating PDF..."
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Title Paint
                val titlePaint = Paint().apply {
                    textSize = 24f
                    isFakeBoldText = true
                    color = Color.BLACK
                }
                canvas.drawText(pdfTitle, 50f, 60f, titlePaint)

                // Content Paint & Wrapping
                val textPaint = TextPaint(Paint().apply {
                    textSize = 14f
                    color = Color.BLACK
                })
                val staticLayout = StaticLayout.Builder.obtain(pdfContent, 0, pdfContent.length, textPaint, 495)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(1f, 1f)
                    .setIncludePad(false)
                    .build()

                canvas.save()
                canvas.translate(50f, 100f) // Title के नीचे से शुरू
                staticLayout.draw(canvas)
                canvas.restore()
                
                pdfDocument.finishPage(page)

                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, "ITFB_${pdfTitle.replace(" ", "_")}_${System.currentTimeMillis()}.pdf")
                pdfDocument.writeTo(FileOutputStream(file))
                pdfDocument.close()
                
                withContext(Dispatchers.Main) {
                    generatedFile = file
                    isLoading = false
                    Toast.makeText(context, "PDF Created!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    Toast.makeText(context, "Error creating PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // AI Chat Dialog
    if (showAiChatDialog) {
        AlertDialog(
            onDismissRequest = { showAiChatDialog = false },
            title = { Text("Ask ITFB AI about your text") },
            text = {
                Column {
                    OutlinedTextField(
                        value = aiUserQuestion,
                        onValueChange = { aiUserQuestion = it },
                        label = { Text("What do you want to ask?") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isAiThinking) {
                        Text("ITFB AI is thinking...", color = MaterialTheme.colorScheme.primary)
                    } else if (aiChatResponse.isNotBlank()) {
                        Text(aiChatResponse, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 150.dp))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (aiUserQuestion.isNotBlank()) {
                        isAiThinking = true
                        coroutineScope.launch {
                            try {
                                val model = GenerativeModel("gemini-1.5-flash", Secrets.GEMINI_API_KEY, systemInstruction = content { text("You are ITFB AI. Answer the question based ONLY on the provided text.") })
                                val prompt = "Text: '$pdfContent'\n\nQuestion: $aiUserQuestion"
                                val response = model.generateContent(prompt)
                                aiChatResponse = response.text ?: "No response."
                            } catch (e: Exception) {
                                aiChatResponse = "Error connecting to AI."
                            } finally {
                                isAiThinking = false
                            }
                        }
                    }
                }) { Text("Ask") }
            },
            dismissButton = {
                TextButton(onClick = { showAiChatDialog = false }) { Text("Close") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Text to PDF Generator") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
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
            // Title Input
            OutlinedTextField(
                value = pdfTitle,
                onValueChange = { pdfTitle = it },
                label = { Text("Document Title") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            // Text Box
            OutlinedTextField(
                value = pdfContent,
                onValueChange = { pdfContent = it },
                label = { Text("Type or Paste Content here...") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(12.dp))

            // AI Action Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Enhance Text Button
                ElevatedButton(
                    onClick = {
                        if (pdfContent.isNotBlank()) {
                            isLoading = true
                            loadingStatus = "Enhancing text with ITFB AI..."
                            coroutineScope.launch {
                                try {
                                    val model = GenerativeModel("gemini-1.5-flash", Secrets.GEMINI_API_KEY, systemInstruction = content { text("You are ITFB AI. Fix any grammar, spelling, or structural errors in the provided text and make it professional. Output ONLY the corrected text.") })
                                    val response = model.generateContent(pdfContent)
                                    pdfContent = response.text?.trim() ?: pdfContent
                                    Toast.makeText(context, "Text Enhanced!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to connect to AI.", Toast.LENGTH_SHORT).show()
                                } finally { isLoading = false }
                            }
                        } else Toast.makeText(context, "Please enter some text first.", Toast.LENGTH_SHORT).show()
                    },
                    enabled = !isLoading && pdfContent.isNotBlank(),
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Enhance Text")
                }

                // Ask AI Button
                ElevatedButton(
                    onClick = { showAiChatDialog = true },
                    enabled = !isLoading && pdfContent.isNotBlank(),
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ask ITFB AI")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Generate Button
            Button(
                onClick = { generatePdf() },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                enabled = !isLoading && pdfTitle.isNotBlank() && pdfContent.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(loadingStatus)
                } else {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create PDF", style = MaterialTheme.typography.titleMedium)
                }
            }

            // Share & Save Actions (दिखाई देगा जब PDF बन जाएगा)
            if (generatedFile != null && !isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PDF is Ready!", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ElevatedButton(onClick = {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", generatedFile!!)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Share")
                            }
                            ElevatedButton(onClick = { saveDocumentLauncher.launch(generatedFile!!.name) }) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save to Phone")
                            }
                        }
                    }
                }
            }
        }
    }
}
