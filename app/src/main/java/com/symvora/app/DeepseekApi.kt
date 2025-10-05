package com.symvora.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object DeepseekApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val API_URL = "https://api.deepseek.com/chat/completions"
    private const val MODEL_NAME = "deepseek-chat"
    private var API_KEY: String? = null

    fun setApiKey(apiKey: String) {
        API_KEY = apiKey
    }

    fun getDiagnosis(symptoms: String): String {
        if (API_KEY == null) {
            throw IllegalStateException("Deepseek API Key not set. Call setApiKey() first.")
        }

        val json = JSONObject().apply {
            put("model", MODEL_NAME)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a helpful medical assistant. Provide a concise diagnosis and general advice based on the symptoms provided. Do not use any markdown formatting like bolding or italics. Always include a disclaimer that the information is for informational purposes only and not a substitute for professional medical advice.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Diagnose: $symptoms")
                })
            })
            put("stream", false)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(API_URL)
            .header("Authorization", "Bearer $API_KEY")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response")
                }
                val responseBody = response.body?.string() ?: ""
                val jsonResponse = JSONObject(responseBody)
                jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Error: ${e.message}"
        }
    }
}