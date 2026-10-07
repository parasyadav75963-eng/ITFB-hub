package com.itfb.hub

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TextToPDFGeneratorScreen(onBack: () -> Unit = {}) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onBack) { Text("Back") }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Text to PDF Generator Screen (Coming Soon...)")
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit = {}) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onBack) { Text("Back") }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Settings Screen (Coming Soon...)")
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit = {}) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onBack) { Text("Back") }
        Spacer(modifier = Modifier.height(16.dp))
        Text("About Screen (Coming Soon...)")
    }
}
