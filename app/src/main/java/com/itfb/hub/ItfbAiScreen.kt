package com.itfb.hub

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.launch
import java.util.Locale

data class ChatMessage(val isUser: Boolean, val text: String, val imageUri: Uri? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItfbAiScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var prompt by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf("gemini-1.5-flash") }
    var showMenu by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    // वॉइस टाइपिंग (Mic) का लॉन्चर
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                prompt += results[0]
            }
        }
    }

    // फोटो चुनने (Attachment) का लॉन्चर
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedImageUri = uri
        if (uri != null) Toast.makeText(context, "Image Attached", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ITFB AI") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Home")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (selectedModel == "gemini-1.5-flash") "Switch to Smart (Pro)" else "Switch to Fast (Flash)") },
                            onClick = {
                                selectedModel = if (selectedModel == "gemini-1.5-flash") "gemini-1.5-pro" else "gemini-1.5-flash"
                                showMenu = false
                                Toast.makeText(context, "Model changed to $selectedModel", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear Chat") },
                            onClick = {
                                chatHistory = emptyList()
                                showMenu = false
                                Toast.makeText(context, "Chat Cleared", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Recent History") },
                            onClick = {
                                Toast.makeText(context, "Recent history will be shown here", Toast.LENGTH_SHORT).show()
                                showMenu = false
                            }
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
        ) {
            // चैट हिस्ट्री लिस्ट
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                if (chatHistory.isEmpty()) {
                    item {
                        Text(
                            "Welcome! I am ITFB AI. How can I help you today?",
                            modifier = Modifier.padding(vertical = 16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                items(chatHistory) { message ->
                    MessageBubble(message, context)
                }
                if (isLoading) {
                    item {
                        Text("ITFB AI is thinking...", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // फोटो अटैच होने का संकेत (TalkBack के लिए)
            if (selectedImageUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Attached Image")
                    Text(" Image Attached", modifier = Modifier.weight(1f))
                    IconButton(onClick = { selectedImageUri = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove Attachment")
                    }
                }
            }

            // इनपुट बार (Input Bar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach File or Image")
                }
                
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message ITFB AI...") },
                    maxLines = 4
                )

                if (prompt.isEmpty() && selectedImageUri == null) {
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        }
                        try {
                            speechLauncher.launch(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Voice search not available", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice Typing")
                    }
                } else {
                    IconButton(
                        onClick = {
                            val userText = prompt
                            val userImage = selectedImageUri
                            if (userText.isNotBlank() || userImage != null) {
                                chatHistory = chatHistory + ChatMessage(isUser = true, text = userText, imageUri = userImage)
                                prompt = ""
                                selectedImageUri = null
                                isLoading = true
                                
                                coroutineScope.launch {
                                    try {
                                        val generativeModel = GenerativeModel(
                                            modelName = selectedModel,
                                            apiKey = Secrets.GEMINI_API_KEY,
                                            systemInstruction = content { text("You are ITFB AI, an intelligent and helpful assistant. Never mention that you are Gemini, Google, or an AI language model. Always introduce yourself as ITFB AI.") }
                                        )
                                        
                                        val inputContent = content {
                                            if (userImage != null) {
                                                val inputStream = context.contentResolver.openInputStream(userImage)
                                                val bitmap = BitmapFactory.decodeStream(inputStream)
                                                if (bitmap != null) image(bitmap)
                                            }
                                            if (userText.isNotBlank()) text(userText)
                                        }
                                        
                                        val response = generativeModel.generateContent(inputContent)
                                        chatHistory = chatHistory + ChatMessage(isUser = false, text = response.text ?: "No response generated.")
                                    } catch (e: Exception) {
                                        chatHistory = chatHistory + ChatMessage(isUser = false, text = "Error: Could not connect to server.")
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send Message")
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, context: Context) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .background(bgColor, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column {
                if (message.imageUri != null) {
                    Text("[Image Attached]", style = MaterialTheme.typography.bodySmall)
                }
                if (message.text.isNotBlank()) {
                    Text(message.text, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        // AI के जवाब के नीचे एक्शन्स (Copy, Share, Like, Dislike)
        if (!message.isUser) {
            Row(modifier = Modifier.padding(top = 4.dp)) {
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("ITFB AI Response", message.text)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Text", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, message.text)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Response"))
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Share Text", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = {
                    Toast.makeText(context, "Feedback: Good Response", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ThumbUp, contentDescription = "Good Response", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = {
                    Toast.makeText(context, "Feedback: Bad Response", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ThumbDown, contentDescription = "Bad Response", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
