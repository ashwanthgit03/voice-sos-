package com.example.womensos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.sqrt

class AudioMonitoringService : Service() {

    companion object {
        const val ACTION_START =
            "com.example.womensos.ACTION_START_MONITORING"

        const val ACTION_STOP =
            "com.example.womensos.ACTION_STOP_MONITORING"

        const val CHANNEL_ID = "womensos_audio_monitoring"
        const val NOTIFICATION_ID = 1001

        private const val TAG = "WomenSOSAudio"
        private const val SAMPLE_RATE = 16000
    }

    private val isMonitoring = AtomicBoolean(false)

    @Volatile
    private var audioRecord: AudioRecord? = null

    private var audioThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                if (!isMonitoring.get()) {
                    startForegroundSafely()
                    startMonitoring()
                }
            }

            else -> {
                // Do not start microphone capture without an explicit
                // ACTION_START request from the visible app.
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundSafely() {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startMonitoring() {

        if (checkSelfPermission(
                android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Microphone permission has not been granted.")
            stopSelf()
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufferSize <= 0) {
            Log.e(TAG, "Unable to determine microphone buffer size.")
            stopSelf()
            return
        }

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBufferSize * 2, SAMPLE_RATE)
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission error.", e)
            stopSelf()
            return
        } catch (e: Exception) {
            Log.e(TAG, "Could not create AudioRecord.", e)
            stopSelf()
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            Log.e(TAG, "AudioRecord initialization failed.")
            stopSelf()
            return
        }

        audioRecord = recorder

        try {
            recorder.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Could not start microphone capture.", e)
            recorder.release()
            audioRecord = null
            stopSelf()
            return
        }

        isMonitoring.set(true)

        audioThread = thread(
            start = true,
            name = "WomenSOSAudioThread"
        ) {
            captureAudio(recorder)
        }

        Log.i(TAG, "Microphone monitoring started.")
    }

    private fun captureAudio(recorder: AudioRecord) {

        val buffer = ShortArray(2048)

        while (isMonitoring.get()) {

            val samplesRead = try {
                recorder.read(
                    buffer,
                    0,
                    buffer.size,
                    AudioRecord.READ_BLOCKING
                )
            } catch (e: Exception) {
                Log.e(TAG, "Microphone read failed.", e)
                break
            }

            if (samplesRead <= 0) {
                if (isMonitoring.get()) {
                    Log.w(TAG, "No audio samples received: $samplesRead")
                }
                continue
            }

            // Calculate RMS sound level for this audio block.
            // This is only a basic sound-intensity measurement,
            // NOT an AI abnormal-sound classifier.
            var sumSquares = 0.0

            for (i in 0 until samplesRead) {
                val sample = buffer[i].toDouble()
                sumSquares += sample * sample
            }

            val rms = sqrt(sumSquares / samplesRead)
            val normalizedLevel = rms / Short.MAX_VALUE

            Log.d(
                TAG,
                "Audio block received; normalized level=$normalizedLevel"
            )

            // AI MODEL INTEGRATION POINT:
            // Send short audio windows to your trained sound classifier
            // here, or extract features for a model running on-device.
            //
            // Do not treat a high sound level alone as proof of danger.
            // Connect a trained classifier before triggering alerts.
        }

        Log.i(TAG, "Microphone capture loop ended.")
    }

    private fun stopMonitoring() {

        isMonitoring.set(false)

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Audio recorder was already stopped.", e)
        }

        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Could not release audio recorder.", e)
        }

        audioRecord = null
        audioThread = null

        Log.i(TAG, "Microphone monitoring stopped.")
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Women SOS Sound Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Shows when Women SOS microphone monitoring is active."
                setShowBadge(false)
            }

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

        return builder
            .setContentTitle("Women SOS")
            .setContentText("Sound monitoring is active.")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}