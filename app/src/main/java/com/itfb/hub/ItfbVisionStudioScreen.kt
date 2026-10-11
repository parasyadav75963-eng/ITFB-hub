package com.itfb.hub

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItfbVisionStudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editedFile by remember { mutableStateOf<File?>(null) }
    var resultText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Select an image to begin.") }
    var tts: TextToSpeech? by remember { mutableStateOf(null) }

    // एडिटिंग के लिए
    var customWatermarkText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { tts?.stop(); tts?.shutdown() }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri).copy(Bitmap.Config.ARGB_8888, true)
                }
                selectedBitmap = bitmap
                statusText = "Image Selected Successfully! Choose an action."
                resultText = ""
                editedFile = null
            } catch (e: Exception) {
                statusText = "Error loading image."
            }
        }
    }

    val saveImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null && editedFile != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        editedFile!!.inputStream().use { inp -> inp.copyTo(out) }
                    }
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Image Saved!", Toast.LENGTH_LONG).show() }
                } catch (e: Exception) {}
            }
        }
    }

    // 1. Offline OCR
    fun extractTextOffline() {
        if (selectedBitmap == null) return
        isLoading = true
        statusText = "Extracting text offline..."
        
        val image = InputImage.fromBitmap(selectedBitmap!!, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                isLoading = false
                if (visionText.text.isNotBlank()) {
                    resultText = "OCR Result:\n" + visionText.text
                    statusText = "Text Extracted Successfully!"
                    tts?.speak("Text Extracted. ${visionText.text}", TextToSpeech.QUEUE_FLUSH, null, null)
                } else {
                    resultText = "No text found in this image."
                    statusText = "No text detected."
                }
            }
            .addOnFailureListener {
                isLoading = false
                statusText = "Error extracting text."
            }
    }

    // 2. Online ITFB AI (Gemini Vision)
    fun describeWithAI() {
        if (selectedBitmap == null) return
        isLoading = true
        statusText = "ITFB AI is analyzing the image..."
        
        coroutineScope.launch {
            try {
                val model = GenerativeModel("gemini-1.5-flash", Secrets.GEMINI_API_KEY)
                val response = model.generateContent(content {
                    image(selectedBitmap!!)
                    text("Describe this image in detail. Tell me what objects are visible, what the background is like, and read any text if present. Make the description very helpful and accessible for a visually impaired user.")
                })
                
                resultText = "ITFB AI Description:\n" + (response.text ?: "Could not describe.")
                statusText = "AI Description Ready!"
                tts?.speak(response.text ?: "", TextToSpeech.QUEUE_FLUSH, null, null)
            } catch (e: Exception) {
                statusText = "AI Error. Check internet connection."
            } finally {
                isLoading = false
            }
        }
    }

    // 3. Edit & Save Image (Background/Watermark)
    fun applyEditAndSave() {
        if (selectedBitmap == null) return
        isLoading = true
        statusText = "Applying edits & saving..."

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val workingBitmap = selectedBitmap!!.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(workingBitmap)
                
                // Add custom background/watermark text if provided
                if (customWatermarkText.isNotBlank()) {
                    val paint = Paint().apply {
                        color = Color.YELLOW
                        textSize = 60f
                        isFakeBoldText = true
                        style = Paint.Style.FILL
                        setShadowLayer(5f, 2f, 2f, Color.BLACK)
                    }
                    // Draw a background box for the text
                    val bgPaint = Paint().apply { color = Color.parseColor("#80000000") } // Semi-transparent black
                    canvas.drawRect(0f, 0f, canvas.width.toFloat(), 120f, bgPaint)
                    canvas.drawText(customWatermarkText, 20f, 80f, paint)
                }

                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                val outFileName = "ITFB_Vision_${System.currentTimeMillis()}.png"
                val outFile = File(downloadsDir, outFileName)
                
                val outStream = FileOutputStream(outFile)
                workingBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                outStream.flush()
                outStream.close()

                withContext(Dispatchers.Main) {
                    editedFile = outFile
                    statusText = "Image Saved Successfully!"
                    Toast.makeText(context, "Image Ready!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { statusText = "Error saving image." }
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ITFB Vision Studio") },
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
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(text = statusText, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            }

            // 1. Select Image
            ElevatedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth().height(60.dp), enabled = !isLoading) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Image from Gallery", style = MaterialTheme.typography.titleMedium)
            }

            if (selectedBitmap != null) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // 2. Action Buttons (Offline OCR vs Online AI)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ElevatedButton(
                        onClick = { extractTextOffline() },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f).padding(end = 4.dp).height(56.dp)
                    ) {
                        Icon(Icons.Default.TextFields, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("Read Text (Offline)")
                    }
                    ElevatedButton(
                        onClick = { describeWithAI() },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f).padding(start = 4.dp).height(56.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("Describe (ITFB AI)")
                    }
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                if (resultText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = resultText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Result / Description") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp)
                    )

                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ElevatedButton(onClick = { tts?.speak(resultText, TextToSpeech.QUEUE_FLUSH, null, null) }) { Text("Read Aloud") }
                        ElevatedButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("ITFB Result", resultText))
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        }) { Text("Copy") }
                        ElevatedButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, resultText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Text"))
                        }) { Text("Share") }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // 3. Image Editor Section
                Text("Image Editor (Add Frame/Text)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customWatermarkText,
                    onValueChange = { customWatermarkText = it },
                    label = { Text("Add Custom Text to Image (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { applyEditAndSave() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.Save, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Apply & Create Image")
                }

                // Share & Save Image Dialog
                if (editedFile != null && !isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("New Image is Ready!", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                ElevatedButton(onClick = {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", editedFile!!)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Share Image")
                                }
                                ElevatedButton(onClick = { saveImageLauncher.launch(editedFile!!.name) }) {
                                    Icon(Icons.Default.Save, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Save to Phone")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
