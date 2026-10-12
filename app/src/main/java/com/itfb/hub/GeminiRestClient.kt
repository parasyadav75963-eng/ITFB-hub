package com.itfb.hub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiRestClient {
    suspend fun generateContent(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val apiKey = Secrets.GEMINI_API_KEY
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val jsonBody = JSONObject().apply {
                put("contents", org.json.JSONArray().put(
                    JSONObject().put("parts", org.json.JSONArray().put(
                        JSONObject().put("text", prompt)
                    ))
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
        } catch (e: Exception) {
            return@withContext "Crash Prevented! Error: ${e.localizedMessage}"
        }
    }
}
