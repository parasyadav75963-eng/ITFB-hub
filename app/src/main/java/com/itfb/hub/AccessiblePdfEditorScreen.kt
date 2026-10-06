package com.itfb.hub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.tomroush.pdfbox.android.PDFBoxResourceLoader
import com.tomroush.pdfbox.multipdf.PDFMergerUtility
import com.tomroush.pdfbox.pdmodel.PDDocument
import com.tomroush.pdfbox.pdmodel.PDPageContentStream
import com.tomroush.pdfbox.pdmodel.encryption.AccessPermission
import com.tomroush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tomroush.pdfbox.pdmodel.font.PDType1Font
import com.tomroush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessiblePdfEditorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Select a PDF tool below:") }
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var processedFile by remember { mutableStateOf<File?>(null) } // तैयार हुई फाइल यहाँ सेव होगी
    
    LaunchedEffect(Unit) {
        PDFBoxResourceLoader.init(context)
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
        }
    }

    // "Save to Phone" लॉन्चर (यूज़र से लोकेशन पूछने के लिए)
    val saveDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && processedFile != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        processedFile!!.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Saved Successfully!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error saving file.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // 1. Read PDF
    val readPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            statusText = "Extracting text..."
            processedFile = null
            coroutineScope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        val doc = PDDocument.load(context.contentResolver.openInputStream(uri))
                        val extracted = PDFTextStripper().getText(doc)
                        doc.close()
                        extracted
                    }
                    if (text.isNotBlank()) {
                        statusText = "Reading aloud..."
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                    } else statusText = "No text found."
                } catch (e: Exception) { statusText = "Error reading PDF." } 
                finally { isLoading = false }
            }
        }
    }

    // Helper फंक्शन फाइल बनाने के लिए
    fun getTempFile(prefix: String): File {
        return File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "${prefix}_${System.currentTimeMillis()}.pdf")
    }

    // 2. Merge PDF
    val mergePdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.size >= 2) {
            isLoading = true
            statusText = "Merging PDFs..."
            processedFile = null
            coroutineScope.launch {
                try {
                    val out = getTempFile("ITFB_Merged")
                    withContext(Dispatchers.IO) {
                        val merger = PDFMergerUtility()
                        merger.destinationFileName = out.absolutePath
                        for (uri in uris) merger.addSource(context.contentResolver.openInputStream(uri))
                        merger.mergeDocuments(null)
                    }
                    processedFile = out
                    statusText = "PDF Merged Successfully!"
                } catch (e: Exception) { statusText = "Error merging." }
                finally { isLoading = false }
            }
        }
    }

    // 3. Protect PDF
    val protectPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            statusText = "Encrypting PDF..."
            processedFile = null
            coroutineScope.launch {
                try {
                    val out = getTempFile("ITFB_Protected")
                    withContext(Dispatchers.IO) {
                        val doc = PDDocument.load(context.contentResolver.openInputStream(uri))
                        val spp = StandardProtectionPolicy("itfb", "itfb", AccessPermission())
                        spp.encryptionKeyLength = 128
                        doc.protect(spp)
                        doc.save(out)
                        doc.close()
                    }
                    processedFile = out
                    statusText = "Protected! Password is 'itfb'."
                } catch (e: Exception) { statusText = "Error protecting." }
                finally { isLoading = false }
            }
        }
    }

    // 4. Rotate PDF
    val rotatePdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            statusText = "Rotating pages..."
            processedFile = null
            coroutineScope.launch {
                try {
                    val out = getTempFile("ITFB_Rotated")
                    withContext(Dispatchers.IO) {
                        val doc = PDDocument.load(context.contentResolver.openInputStream(uri))
                        for (page in doc.pages) page.rotation = (page.rotation + 90) % 360
                        doc.save(out)
                        doc.close()
                    }
                    processedFile = out
                    statusText = "Rotated Successfully!"
                } catch (e: Exception) { statusText = "Error rotating." }
                finally { isLoading = false }
            }
        }
    }

    // 5. Add Text
    val addTextPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            statusText = "Adding text..."
            processedFile = null
            coroutineScope.launch {
                try {
                    val out = getTempFile("ITFB_TextAdded")
                    withContext(Dispatchers.IO) {
                        val doc = PDDocument.load(context.contentResolver.openInputStream(uri))
                        val contentStream = PDPageContentStream(doc, doc.getPage(0), PDPageContentStream.AppendMode.APPEND, true, true)
                        contentStream.beginText()
                        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 24f)
                        contentStream.newLineAtOffset(50f, 750f)
                        contentStream.showText("Edited by ITFB Hub")
                        contentStream.endText()
                        contentStream.close()
                        doc.save(out)
                        doc.close()
                    }
                    processedFile = out
                    statusText = "Text Added Successfully!"
                } catch (e: Exception) { statusText = "Error adding text." }
                finally { isLoading = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accessible PDF Editor") },
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = if (isLoading) "Processing..." else statusText,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // --- नया शेयर और सेव बॉक्स (जब फाइल तैयार हो जाए) ---
            if (processedFile != null && !isLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("File is Ready!", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ElevatedButton(onClick = {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", processedFile!!)
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
                            ElevatedButton(onClick = {
                                saveDocumentLauncher.launch(processedFile!!.name)
                            }) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save")
                            }
                        }
                    }
                }
            }

            val buttonModifier = Modifier.fillMaxWidth().height(72.dp).padding(bottom = 12.dp)

            ElevatedButton(onClick = { readPdfLauncher.launch("application/pdf") }, modifier = buttonModifier, enabled = !isLoading) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Read PDF Aloud", style = MaterialTheme.typography.titleMedium)
            }
            ElevatedButton(onClick = { rotatePdfLauncher.launch("application/pdf") }, modifier = buttonModifier, enabled = !isLoading) {
                Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Rotate PDF (90°)", style = MaterialTheme.typography.titleMedium)
            }
            ElevatedButton(onClick = { addTextPdfLauncher.launch("application/pdf") }, modifier = buttonModifier, enabled = !isLoading) {
                Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Add Title/Text to PDF", style = MaterialTheme.typography.titleMedium)
            }
            ElevatedButton(onClick = { mergePdfLauncher.launch("application/pdf") }, modifier = buttonModifier, enabled = !isLoading) {
                Icon(Icons.Default.Merge, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Merge Multiple PDFs", style = MaterialTheme.typography.titleMedium)
            }
            ElevatedButton(onClick = { protectPdfLauncher.launch("application/pdf") }, modifier = buttonModifier, enabled = !isLoading) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Protect with Password", style = MaterialTheme.typography.titleMedium)
            }
            
            if (isLoading) CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }
    }
}
