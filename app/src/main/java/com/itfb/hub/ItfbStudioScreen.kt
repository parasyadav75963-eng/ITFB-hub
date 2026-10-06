package com.itfb.hub

import android.content.Intent
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
fun ItfbStudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var generatedFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Ready. Select a video to begin.") }

    var selectedAction by remember { mutableStateOf("Cut Video") }
    val actions = listOf("Cut Video", "Add BGM / Sound Effect", "Remove Noise (Clean Voice)", "Mute Video", "Extract Audio")

    var startTime by remember { mutableStateOf("0") }
    var endTime by remember { mutableStateOf("10") }
    var videoVolume by remember { mutableStateOf(1.0f) }
    var audioVolume by remember { mutableStateOf(0.5f) } 

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedVideoUri = uri
        generatedFile = null
        if (uri != null) statusText = "Video Selected!"
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedAudioUri = uri
        if (uri != null) statusText = "Audio/BGM Selected!"
    }

    val saveDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
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

    fun processMedia() {
        if (selectedVideoUri == null) {
            Toast.makeText(context, "Please select a video first.", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true
        statusText = "Processing... Please wait (This may take a few minutes)."
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val inputPath = FFmpegKitConfig.getSafParameterForRead(context, selectedVideoUri)
                val isAudioExtract = selectedAction == "Extract Audio"
                val ext = if (isAudioExtract) "mp3" else "mp4"
                
                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val outFileName = "ITFB_Studio_${System.currentTimeMillis()}.$ext"
                val outFile = File(downloadsDir, outFileName)
                val outputPath = outFile.absolutePath

                var command = ""
                // -crf 28 File size को छोटा रखने के लिए है (Quality बरकरार रखते हुए)
                val compressionArgs = "-vcodec libx264 -crf 28 -preset ultrafast"

                when (selectedAction) {
                    "Cut Video" -> {
                        command = "-ss $startTime -i \"$inputPath\" -to $endTime -c copy \"$outputPath\""
                    }
                    "Mute Video" -> {
                        command = "-i \"$inputPath\" -an -vcodec copy \"$outputPath\""
                    }
                    "Extract Audio" -> {
                        command = "-i \"$inputPath\" -vn -acodec libmp3lame -q:a 2 \"$outputPath\""
                    }
                    "Remove Noise (Clean Voice)" -> {
                        command = "-i \"$inputPath\" -af \"afftdn\" $compressionArgs \"$outputPath\""
                    }
                    "Add BGM / Sound Effect" -> {
                        if (selectedAudioUri == null) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Please select BGM Audio first!", Toast.LENGTH_SHORT).show()
                                isLoading = false
                            }
                            return@launch
                        }
                        val audioPath = FFmpegKitConfig.getSafParameterForRead(context, selectedAudioUri)
                        // BGM Mixing with Fade in/out & Volume control
                        command = "-i \"$inputPath\" -i \"$audioPath\" -filter_complex \"[0:a]volume=${videoVolume}[a1];[1:a]volume=${audioVolume},afade=t=in:st=0:d=2,afade=t=out:st=10:d=2[a2];[a1][a2]amix=inputs=2:duration=first:dropout_transition=2\" $compressionArgs \"$outputPath\""
                    }
                }

                val session = FFmpegKit.execute(command)
                if (session.returnCode.isValueSuccess) {
                    withContext(Dispatchers.Main) {
                        generatedFile = outFile
                        statusText = "Processing Completed Successfully!"
                        Toast.makeText(context, "Done!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        statusText = "Error during processing."
                        Toast.makeText(context, "Failed. Try checking values.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { statusText = "Error: ${e.message}" }
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ITFB Studio") }, // नया प्रोफेशनल नाम
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
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

            // 1. Select Video
            ElevatedButton(onClick = { videoPicker.launch("video/*") }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Icon(Icons.Default.VideoFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("1. Select Video", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // 2. Select Tool
            Text("2. Choose Tool:", style = MaterialTheme.typography.titleMedium)
            actions.forEach { action ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedAction == action, onClick = { selectedAction = action })
                    Text(action, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Tool Specific Controls
            if (selectedAction == "Cut Video") {
                Text("Set Time (in seconds) - TalkBack Friendly", color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text("Start Time (s)") }, modifier = Modifier.weight(1f).padding(end = 4.dp))
                    OutlinedTextField(value = endTime, onValueChange = { endTime = it }, label = { Text("End Time (s)") }, modifier = Modifier.weight(1f).padding(start = 4.dp))
                }
            }

            if (selectedAction == "Add BGM / Sound Effect") {
                ElevatedButton(onClick = { audioPicker.launch("audio/*") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Icon(Icons.Default.AudioFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Background Music / SFX")
                }
                Text("Original Video Volume: ${(videoVolume * 100).toInt()}%")
                Slider(value = videoVolume, onValueChange = { videoVolume = it }, valueRange = 0f..1f)
                Text("BGM / SFX Volume: ${(audioVolume * 100).toInt()}%")
                Slider(value = audioVolume, onValueChange = { audioVolume = it }, valueRange = 0f..1f)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Process Button
            Button(
                onClick = { processMedia() },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                enabled = !isLoading && selectedVideoUri != null
            ) {
                if (isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Process & Save", style = MaterialTheme.typography.titleLarge)
                }
            }

            // Share & Save to Phone Dialog
            if (generatedFile != null && !isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("File is Ready!", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ElevatedButton(onClick = {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", generatedFile!!)
                                val type = if (selectedAction == "Extract Audio") "audio/mp3" else "video/mp4"
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    this.type = type
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
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
