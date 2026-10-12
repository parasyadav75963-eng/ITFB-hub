package com.itfb.hub

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object GeminiRestClient {
    suspend fun generateContent(prompt: String, image: Bitmap? = null): String = withContext(Dispatchers.IO) {
        try {
            val apiKey = Secrets.GEMINI_API_KEY
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val partsArray = JSONArray()
            partsArray.put(JSONObject().put("text", prompt.ifBlank { "Describe this image" }))

            if (image != null) {
                val outputStream = ByteArrayOutputStream()
                image.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val base64String = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                
                val inlineData = JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", base64String)
                }
                partsArray.put(JSONObject().put("inlineData", inlineData))
            }

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", partsArray)
                ))
            }.toString()

            connection.outputStream.use { it.write(jsonBody.toByteArray()) }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                return@withContext json.getJSONArray("candidates")
                    .getJSONObject(0).getJSONObject("content")
                    .getJSONArray("parts").getJSONObject(0).getString("text")
            } else {
                val errorMsg = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                return@withContext "API Error ${connection.responseCode}: $errorMsg"
            }
        } catch (t: Throwable) {
            return@withContext "Crash Prevented! Reason: ${t.localizedMessage}"
        }
    }
}
