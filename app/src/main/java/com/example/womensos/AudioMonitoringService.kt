package com.example.womensos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// ---------------------------------------------------------------------------
// Shared result data class — used by MainActivity to drive UI
// ---------------------------------------------------------------------------
data class PredictionResult(
    val prediction: String,
    val confidence: Double,
    val distressConfidence: Double,
    val whisperText: String,
    val keywordFound: Boolean,
    val triggerEmergency: Boolean,
    val timestamp: String,
    val error: String = ""
)

// ---------------------------------------------------------------------------
// AudioMonitoringService
// Runs as a foreground service.
// Records audio in 10-second windows, POSTs each window to the backend,
// and triggers an alarm beep + notification if an emergency is confirmed.
// ---------------------------------------------------------------------------
class AudioMonitoringService : Service() {

    companion object {
        const val ACTION_START      = "com.example.womensos.ACTION_START_MONITORING"
        const val ACTION_STOP       = "com.example.womensos.ACTION_STOP_MONITORING"
        const val EXTRA_BACKEND_URL = "BACKEND_URL"

        const val CHANNEL_ID           = "womensos_audio_monitoring"
        const val EMERGENCY_CHANNEL_ID = "womensos_emergency"
        const val NOTIFICATION_ID      = 1001
        const val EMERGENCY_NOTIF_ID   = 1002

        private const val TAG            = "WomenSOSAudio"
        private const val SAMPLE_RATE    = 16000        // Hz
        private const val WINDOW_SECONDS = 10           // seconds per analysis window

        // Observed by MainActivity
        val lastResult = MutableStateFlow<PredictionResult?>(null)
        val isRunning  = MutableStateFlow(false)
    }

    // ── Service state ──────────────────────────────────────────────────────
    private val isMonitoring = AtomicBoolean(false)
    private var audioRecord  : AudioRecord? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var backendUrl   = "http://10.0.2.2:8000"   // default (emulator localhost)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                backendUrl = intent.getStringExtra(EXTRA_BACKEND_URL) ?: backendUrl
                if (!isMonitoring.get()) {
                    startForegroundSafely()
                    startMonitoringLoop()
                }
            }
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Foreground notification ────────────────────────────────────────────

    private fun startForegroundSafely() {
        val notification = buildMonitoringNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ── Recording + upload loop ────────────────────────────────────────────

    private fun startMonitoringLoop() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission not granted.")
            stopSelf()
            return
        }

        val samplesPerWindow = SAMPLE_RATE * WINDOW_SECONDS
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBuf <= 0) {
            Log.e(TAG, "Cannot determine AudioRecord buffer size.")
            stopSelf()
            return
        }

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf * 4, samplesPerWindow * 2)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not create AudioRecord.", e)
            stopSelf()
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            Log.e(TAG, "AudioRecord failed to initialise.")
            stopSelf()
            return
        }

        audioRecord = recorder
        isMonitoring.set(true)
        isRunning.value = true
        Log.i(TAG, "Monitoring started — backend: $backendUrl")

        serviceScope.launch {
            try {
                recorder.startRecording()

                val windowBuf = ShortArray(samplesPerWindow)
                val readBuf   = ShortArray(2048)
                var windowPos = 0

                while (isMonitoring.get()) {
                    val read = recorder.read(readBuf, 0, readBuf.size)
                    if (read <= 0) continue

                    // Fill the 10-second window buffer from the read block
                    var src = 0
                    while (src < read && isMonitoring.get()) {
                        val toCopy = minOf(read - src, samplesPerWindow - windowPos)
                        readBuf.copyInto(windowBuf, windowPos, src, src + toCopy)
                        windowPos += toCopy
                        src       += toCopy

                        if (windowPos >= samplesPerWindow) {
                            // Window full — convert to WAV and upload
                            val wav = pcmToWav(windowBuf.copyOf(), SAMPLE_RATE)
                            sendToBackend(wav)
                            windowPos = 0
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recording loop error.", e)
            } finally {
                try { recorder.stop() }   catch (_: Exception) {}
                try { recorder.release() } catch (_: Exception) {}
                audioRecord  = null
                isRunning.value = false
                Log.i(TAG, "Recording stopped.")
            }
        }
    }

    // ── HTTP upload ────────────────────────────────────────────────────────

    private suspend fun sendToBackend(wavBytes: ByteArray) {
        val url = "${backendUrl.trimEnd('/')}/predict"
        Log.i(TAG, "Uploading ${wavBytes.size / 1024} KB to $url")

        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "audio", "live_window.wav",
                    wavBytes.toRequestBody("audio/wav".toMediaType())
                )
                .build()

            val request = Request.Builder().url(url).post(body).build()

            val responseJson = withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { it.body?.string() ?: "" }
            }

            val json   = JSONObject(responseJson)
            val result = PredictionResult(
                prediction         = json.optString("prediction", "unknown"),
                confidence         = json.optDouble("confidence", 0.0),
                distressConfidence = json.optDouble("distress_confidence", 0.0),
                whisperText        = json.optString("whisper_text", ""),
                keywordFound       = json.optBoolean("keyword_found", false),
                triggerEmergency   = json.optBoolean("trigger_emergency", false),
                timestamp          = json.optString("timestamp", "")
            )

            lastResult.value = result
            Log.i(TAG, "Result: ${result.prediction} conf=${result.distressConfidence} emergency=${result.triggerEmergency}")

            if (result.triggerEmergency) {
                withContext(Dispatchers.Main) {
                    playEmergencyBeep()
                    showEmergencyNotification(result)
                }
            }

        } catch (e: IOException) {
            Log.w(TAG, "Network error: ${e.message}")
            lastResult.value = PredictionResult(
                prediction = "network_error", confidence = 0.0, distressConfidence = 0.0,
                whisperText = "", keywordFound = false, triggerEmergency = false,
                timestamp = "", error = "Cannot reach backend — check IP & server."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Upload error: ${e.message}", e)
        }
    }

    // ── Emergency beep ────────────────────────────────────────────────────

    private fun playEmergencyBeep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            // Play a loud, repeating alert tone for 4 seconds
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 4000)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 5000)
            Log.i(TAG, "Emergency beep played.")
        } catch (e: Exception) {
            Log.e(TAG, "Beep failed: ${e.message}")
        }
    }

    // ── Emergency notification ─────────────────────────────────────────────

    private fun showEmergencyNotification(result: PredictionResult) {
        val nm  = getSystemService(NotificationManager::class.java)
        val txt = when {
            result.whisperText.isNotBlank() ->
                "Keyword detected: \"${result.whisperText.take(60)}\""
            else ->
                "Distress confidence: ${(result.distressConfidence * 100).toInt()}%"
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, EMERGENCY_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        nm.notify(
            EMERGENCY_NOTIF_ID,
            builder
                .setContentTitle("🚨 KAVALAN — EMERGENCY ALERT")
                .setContentText(txt)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setAutoCancel(false)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .build()
        )
    }

    // ── Stop monitoring ────────────────────────────────────────────────────

    private fun stopMonitoring() {
        isMonitoring.set(false)
        isRunning.value = false
        try { audioRecord?.stop() }    catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        Log.i(TAG, "Monitoring stopped.")
    }

    // ── Notification channels ─────────────────────────────────────────────

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Kavalan — Sound Monitoring",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Active while Kavalan monitors background audio."
                    setShowBadge(false)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    EMERGENCY_CHANNEL_ID,
                    "Kavalan — Emergency Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Fires when a confirmed distress signal is detected."
                }
            )
        }
    }

    private fun buildMonitoringNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setContentTitle("Kavalan — Women SOS")
            .setContentText("Listening for distress signals…")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    // ── PCM → WAV conversion ──────────────────────────────────────────────

    private fun pcmToWav(pcm: ShortArray, sampleRate: Int): ByteArray {
        val dataSize  = pcm.size * 2           // 16-bit = 2 bytes per sample
        val totalSize = dataSize + 44           // WAV header is 44 bytes
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF chunk
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(totalSize - 8)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        // fmt sub-chunk
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)              // sub-chunk size
        buf.putShort(1)             // PCM = 1
        buf.putShort(1)             // mono
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * 2)  // byte rate (16-bit mono)
        buf.putShort(2)             // block align
        buf.putShort(16)            // bits per sample
        // data sub-chunk
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        for (s in pcm) buf.putShort(s)
        return buf.array()
    }
}