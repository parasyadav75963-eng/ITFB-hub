package com.itfb.hub

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
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
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItfbAudioMagicScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var generatedFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Ready. Select an audio file.") }

    var selectedAction by remember { mutableStateOf("Vocal Remover (Karaoke)") }
    val actions = listOf(
        "Vocal Remover (Karaoke)", 
        "Isolate Bass & Beats", 
        "Make Voice Female (High Pitch)", 
        "Make Voice Male (Deep Pitch)", 
        "8D Audio Effect (Surround)"
    )

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    // जब भी हम स्क्रीन से बाहर जाएं, प्लेयर बंद हो जाए
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedAudioUri = uri
        generatedFile = null
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        if (uri != null) statusText = "Audio Selected!"
    }

    val saveDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mp3")) { uri ->
        if (uri != null && generatedFile != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        generatedFile!!.inputStream().use { inp -> inp.copyTo(out) }
                    }
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Saved Successfully!", Toast.LENGTH_LONG).show() }
                } catch (e: Exception) {}
            }
        }
    }

    fun processAudio() {
        if (selectedAudioUri == null) {
            Toast.makeText(context, "Please select an audio file.", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true
        statusText = "Processing Audio... Please wait."
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        generatedFile = null

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val inputPath = FFmpegKitConfig.getSafParameterForRead(context, selectedAudioUri)
                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                
                // ITFB Branding File Name
                val cleanActionName = selectedAction.replace(" ", "").replace("(", "").replace(")", "")
                val outFileName = "ITFB_Magic_${cleanActionName}_${System.currentTimeMillis()}.mp3"
                val outFile = File(downloadsDir, outFileName)
                val outputPath = outFile.absolutePath

                var filter = ""
                when (selectedAction) {
                    "Vocal Remover (Karaoke)" -> {
                        // Phase cancellation technique
                        filter = "pan=stereo|c0=c0-c1|c1=c1-c0"
                    }
                    "Isolate Bass & Beats" -> {
                        // Lowpass filter keeps heavy beats, removes thin vocals
                        filter = "lowpass=f=200"
                    }
                    "Make Voice Female (High Pitch)" -> {
                        // Pitch shift up
                        filter = "asetrate=44100*1.2,atempo=1/1.2"
                    }
                    "Make Voice Male (Deep Pitch)" -> {
                        // Pitch shift down
                        filter = "asetrate=44100*0.8,atempo=1/0.8"
                    }
                    "8D Audio Effect (Surround)" -> {
                        // Moving sound effect (apulsator)
                        filter = "apulsator=hz=0.125"
                    }
                }

                // Execute FFmpeg
                val command = "-i \"$inputPath\" -af \"$filter\" -vn -c:a libmp3lame -q:a 2 \"$outputPath\""
                val session = FFmpegKit.execute(command)

                if (session.returnCode.isValueSuccess) {
                    withContext(Dispatchers.Main) {
                        generatedFile = outFile
                        statusText = "Magic Applied! Ready to play or save."
                        Toast.makeText(context, "Audio Processed!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        statusText = "Error during processing. Try a different file."
                        Toast.makeText(context, "Processing failed.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { statusText = "Error: ${e.message}" }
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    fun playPreview() {
        if (generatedFile != null) {
            if (isPlaying) {
                mediaPlayer?.pause()
                isPlaying = false
            } else {
                if (mediaPlayer == null) {
                    mediaPlayer = MediaPlayer.create(context, Uri.fromFile(generatedFile))
                    mediaPlayer?.setOnCompletionListener {
                        isPlaying = false
                    }
                }
                mediaPlayer?.start()
                isPlaying = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Vocal & Audio Magic") },
                navigationIcon = { 
                    IconButton(onClick = { 
                        mediaPlayer?.release()
                        onBack() 
                    }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } 
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

            // 1. Select Audio
            ElevatedButton(onClick = { audioPicker.launch("audio/*") }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Icon(Icons.Default.AudioFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("1. Select Audio / Song", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // 2. Select Tool
            Text("2. Choose Magic Effect:", style = MaterialTheme.typography.titleMedium)
            actions.forEach { action ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedAction == action, onClick = { selectedAction = action })
                    Text(action, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // 3. Process Button
            Button(
                onClick = { processAudio() },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                enabled = !isLoading && selectedAudioUri != null
            ) {
                if (isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apply Magic", style = MaterialTheme.typography.titleLarge)
                }
            }

            // Preview, Share & Save
            if (generatedFile != null && !isLoading) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(), 
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Output Ready: ${generatedFile!!.name}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Listen Preview Button
                        ElevatedButton(
                            onClick = { playPreview() },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isPlaying) "Pause Preview" else "Listen Preview", style = MaterialTheme.typography.titleMedium)
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ElevatedButton(onClick = {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", generatedFile!!)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "audio/mp3"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Audio"))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Share")
                            }
                            ElevatedButton(onClick = { saveDocumentLauncher.launch(generatedFile!!.name) }) {
                                Icon(Icons.Default.Save, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }
}
