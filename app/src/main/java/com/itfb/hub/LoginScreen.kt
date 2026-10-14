package com.itfb.hub

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

@Composable
fun LoginScreen(onSignInSuccess: () -> Unit) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isLoginMode) "Log in to ITFB Hub" else "Create New Account",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (!isLoginMode) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name (आपका पूरा नाम)") },
                placeholder = { Text("उदाहरण: Rahul Kumar") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email ID (ईमेल आईडी)") },
            placeholder = { Text("उदाहरण: rahul123@gmail.com") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (पासवर्ड बनाएँ)") },
            placeholder = { Text("कम से कम 8 अक्षर। लगातार 3 एक जैसे नंबर या अक्षर न डालें, जैसे aaa या 111") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "कृपया सभी बॉक्स भरें", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (password.length < 8) {
                        Toast.makeText(context, "पासवर्ड कम से कम 8 अक्षरों का होना चाहिए", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    // यह कोड चेक करेगा कि लगातार 3 अक्षर एक जैसे तो नहीं हैं (जैसे 111 या aaa)
                    if (Regex("(.)\\1{2,}").containsMatchIn(password)) {
                        Toast.makeText(context, "पासवर्ड में लगातार 3 एक जैसे अक्षर या नंबर (जैसे aaa या 111) नहीं होने चाहिए", Toast.LENGTH_LONG).show()
                        return@Button
                    }

                    isLoading = true

                    if (isLoginMode) {
                        // लॉग इन कोडिंग
                        auth.signInWithEmailAndPassword(email.trim(), password)
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) {
                                    Toast.makeText(context, "लॉग इन सफल!", Toast.LENGTH_SHORT).show()
                                    onSignInSuccess()
                                } else {
                                    Toast.makeText(context, "Error: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        // नया अकाउंट बनाने की कोडिंग
                        if (name.isBlank()) {
                            isLoading = false
                            Toast.makeText(context, "कृपया अपना नाम लिखें", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        auth.createUserWithEmailAndPassword(email.trim(), password)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val user = auth.currentUser
                                    val profileUpdates = UserProfileChangeRequest.Builder()
                                        .setDisplayName(name.trim())
                                        .build()

                                    user?.updateProfile(profileUpdates)?.addOnCompleteListener { updateTask ->
                                        isLoading = false
                                        if (updateTask.isSuccessful) {
                                            Toast.makeText(context, "अकाउंट बन गया!", Toast.LENGTH_SHORT).show()
                                            onSignInSuccess()
                                        }
                                    }
                                } else {
                                    isLoading = false
                                    Toast.makeText(context, "Error: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(if (isLoginMode) "Log In (लॉग इन करें)" else "Create Account (अकाउंट बनाएँ)")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { isLoginMode = !isLoginMode }) {
                Text(if (isLoginMode) "नया अकाउंट बनाना है? यहाँ क्लिक करें" else "पहले से अकाउंट है? लॉग इन करें")
            }
        }
    }
}
