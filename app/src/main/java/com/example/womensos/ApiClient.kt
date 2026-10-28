package com.example.womensos

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * API boundary for your teammate's backend.
 * Replace BASE_URL and request/response fields with the agreed API contract.
 * Never put secret API keys in the Android app.
 */
object ApiClient {
    // TODO: Replace with your deployed HTTPS backend base URL.
    private const val BASE_URL = "https://YOUR_BACKEND_HOST"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class Prediction(val label: String, val confidence: Double, val riskLevel: String)

    fun predictAudioJson(audioMetadataJson: JSONObject): Result<Prediction> = runCatching {
        val body = audioMetadataJson.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/predict-audio")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Prediction API returned HTTP ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            Prediction(
                label = json.optString("prediction", "unknown"),
                confidence = json.optDouble("confidence", 0.0),
                riskLevel = json.optString("risk_level", "unknown")
            )
        }
    }

    fun requestEmergencyAlert(payload: JSONObject): Result<String> = runCatching {
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/emergency-alert")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Emergency API returned HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }
}
