package com.example.womensos

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared OkHttp client instance.
 *
 * Actual HTTP request logic lives in:
 *   - AudioMonitoringService  (live 10-second microphone windows)
 *   - MainActivity            (demo audio file upload)
 *
 * Never put secret API keys in an Android app.
 * The backend URL is configured by the user at runtime from the Home screen.
 */
object ApiClient {

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
