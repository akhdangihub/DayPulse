package com.example.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.R
import com.example.data.TranscriptionProcessor
import com.example.data.local.AppDatabase
import com.example.data.local.CaptureEntryEntity
import com.example.data.local.CaptureType
import com.example.data.local.DateKeyUtil
import com.example.data.local.ProcessingStatus
import com.example.data.prefs.PrivacyPreferencesStore
import com.example.widget.WidgetSyncHelper
import com.example.worker.NotificationHelper
import com.example.worker.TranscriptionWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

enum class CaptureServiceStatus {
    IDLE,
    RECORDING,
    PAUSED
}

data class RecordingServiceState(
    val status: CaptureServiceStatus = CaptureServiceStatus.IDLE,
    val segmentElapsedSeconds: Int = 0,
    val amplitude: Float = 0f,
    val completedSegmentsCount: Int = 0,
    val segmentDurationMinutes: Int = 5,
    val isSavingChunk: Boolean = false,
    val errorMessage: String? = null
)

class AudioCaptureService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var segmentStartTimeMillis: Long = 0L
    private var activeResumeMillis: Long = 0L
    private var accumulatedActiveMillis: Long = 0L
    private var tickerJob: Job? = null
    private var maxSegmentSeconds: Int = 300

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture()
            ACTION_PAUSE -> pauseCapture()
            ACTION_RESUME -> resumeCapture()
            ACTION_SPLIT_SEGMENT -> splitAndContinueSegment()
            ACTION_STOP -> stopCaptureAndService()
        }
        return START_NOT_STICKY
    }

    private fun startCapture() {
        if (_state.value.status == CaptureServiceStatus.RECORDING) return
        serviceScope.launch {
            val prefs = PrivacyPreferencesStore(applicationContext).preferencesFlow.first()
            maxSegmentSeconds = (prefs.segmentDurationMinutes.coerceIn(1, 15)) * 60
            _state.value = _state.value.copy(
                segmentDurationMinutes = prefs.segmentDurationMinutes,
                errorMessage = null
            )

            promoteToForeground(isPaused = false)
            if (beginNewSegment()) {
                startTicker()
            } else {
                stopSelf()
            }
        }
    }

    private fun beginNewSegment(): Boolean {
        return try {
            val recordingsDir = File(filesDir, "recordings").apply { mkdirs() }
            val file = File(recordingsDir, "seg_${System.currentTimeMillis()}.m4a")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(24000)
                setAudioEncodingBitRate(64000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = mediaRecorder
            currentFile = file
            val now = System.currentTimeMillis()
            segmentStartTimeMillis = now
            activeResumeMillis = now
            accumulatedActiveMillis = 0L

            _state.value = _state.value.copy(
                status = CaptureServiceStatus.RECORDING,
                segmentElapsedSeconds = 0,
                amplitude = 0f,
                errorMessage = null
            )
            WidgetSyncHelper.updateAllWidgetsAsync(applicationContext)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaRecorder", e)
            _state.value = _state.value.copy(
                status = CaptureServiceStatus.IDLE,
                errorMessage = e.localizedMessage ?: "Unable to access microphone."
            )
            false
        }
    }

    private fun pauseCapture() {
        if (_state.value.status != CaptureServiceStatus.RECORDING) return
        try {
            recorder?.pause()
            if (activeResumeMillis > 0L) {
                accumulatedActiveMillis += (System.currentTimeMillis() - activeResumeMillis).coerceAtLeast(0L)
                activeResumeMillis = 0L
            }
            _state.value = _state.value.copy(
                status = CaptureServiceStatus.PAUSED,
                amplitude = 0f
            )
            promoteToForeground(isPaused = true)
            WidgetSyncHelper.updateAllWidgetsAsync(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause MediaRecorder", e)
        }
    }

    private fun resumeCapture() {
        if (_state.value.status != CaptureServiceStatus.PAUSED) return
        try {
            recorder?.resume()
            activeResumeMillis = System.currentTimeMillis()
            _state.value = _state.value.copy(status = CaptureServiceStatus.RECORDING)
            promoteToForeground(isPaused = false)
            WidgetSyncHelper.updateAllWidgetsAsync(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume MediaRecorder", e)
        }
    }

    private fun splitAndContinueSegment() {
        if (_state.value.status == CaptureServiceStatus.IDLE) return
        serviceScope.launch {
            _state.value = _state.value.copy(isSavingChunk = true)
            finalizeCurrentSegment()
            if (beginNewSegment()) {
                _state.value = _state.value.copy(isSavingChunk = false)
                promoteToForeground(isPaused = false)
            } else {
                _state.value = _state.value.copy(isSavingChunk = false)
                stopCaptureAndService()
            }
        }
    }

    private fun stopCaptureAndService() {
        tickerJob?.cancel()
        tickerJob = null
        serviceScope.launch {
            _state.value = _state.value.copy(isSavingChunk = true)
            val saved = finalizeCurrentSegment()
            _state.value = RecordingServiceState(
                status = CaptureServiceStatus.IDLE,
                completedSegmentsCount = _state.value.completedSegmentsCount,
                segmentDurationMinutes = _state.value.segmentDurationMinutes,
                isSavingChunk = false,
                errorMessage = if (!saved) "Recording was too short (< 1s) to save." else null
            )
            WidgetSyncHelper.updateAllWidgetsAsync(applicationContext)
            ServiceCompat.stopForeground(this@AudioCaptureService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun currentActiveElapsedSeconds(): Int {
        val ongoing = if (activeResumeMillis > 0L) {
            (System.currentTimeMillis() - activeResumeMillis).coerceAtLeast(0L)
        } else 0L
        return ((accumulatedActiveMillis + ongoing) / 1000L).toInt()
    }

    private suspend fun finalizeCurrentSegment(): Boolean {
        val activeRecorder = recorder
        val file = currentFile
        val elapsed = currentActiveElapsedSeconds().coerceAtLeast(_state.value.segmentElapsedSeconds)
        val timestamp = segmentStartTimeMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        recorder = null
        currentFile = null
        activeResumeMillis = 0L
        accumulatedActiveMillis = 0L

        if (activeRecorder != null) {
            try {
                activeRecorder.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Recorder stop threw (likely very short segment)", e)
            } finally {
                runCatching { activeRecorder.release() }
            }
        }

        if (file != null && file.exists() && file.length() > 512L && elapsed >= 1) {
            val db = AppDatabase.getInstance(applicationContext)
            val entry = CaptureEntryEntity(
                dateKey = DateKeyUtil.dateKeyForMillis(timestamp),
                timestamp = timestamp,
                type = CaptureType.AUDIO.name,
                localMediaPath = file.absolutePath,
                durationSeconds = elapsed,
                aiCaption = "Voice segment (${DateKeyUtil.formatDuration(elapsed)})",
                processingStatus = ProcessingStatus.QUEUED_OFFLINE.name
            )
            db.captureDao().insert(entry)
            _state.value = _state.value.copy(
                completedSegmentsCount = _state.value.completedSegmentsCount + 1
            )
            val prefs = PrivacyPreferencesStore(applicationContext).preferencesFlow.first()
            // Start transcription immediately in app-scoped TranscriptionProcessor, plus WorkManager fallback
            if (TranscriptionProcessor.isNetworkAvailable(applicationContext, prefs.wifiOnlyProcessing)) {
                TranscriptionProcessor.triggerAsync(applicationContext, includeFailed = false)
            }
            TranscriptionWorker.enqueue(applicationContext, prefs.wifiOnlyProcessing)
            return true
        } else {
            file?.delete()
            return false
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (true) {
                delay(150L)
                val current = _state.value
                if (current.status == CaptureServiceStatus.RECORDING) {
                    val rawAmp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    val normalizedAmp = (rawAmp / 22000f).coerceIn(0.04f, 1f)
                    val newElapsed = currentActiveElapsedSeconds()
                    _state.value = current.copy(
                        amplitude = normalizedAmp,
                        segmentElapsedSeconds = newElapsed
                    )
                    if (newElapsed >= maxSegmentSeconds) {
                        splitAndContinueSegment()
                    }
                }
            }
        }
    }

    private fun promoteToForeground(isPaused: Boolean) {
        val pauseResumeIntent = PendingIntent.getService(
            this,
            10,
            Intent(this, AudioCaptureService::class.java).apply {
                action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, AudioCaptureService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isPaused) {
            getString(R.string.fgs_notif_title_paused)
        } else {
            getString(R.string.fgs_notif_title_recording)
        }

        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_CAPTURE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(getString(R.string.capture_privacy_badge))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(NotificationHelper.launchAppPendingIntent(this))
            .addAction(
                0,
                if (isPaused) getString(R.string.capture_resume) else getString(R.string.capture_pause),
                pauseResumeIntent
            )
            .addAction(0, getString(R.string.capture_stop), stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.FGS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NotificationHelper.FGS_NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        runCatching { recorder?.release() }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AudioCaptureService"
        const val ACTION_START = "com.example.action.CAPTURE_START"
        const val ACTION_PAUSE = "com.example.action.CAPTURE_PAUSE"
        const val ACTION_RESUME = "com.example.action.CAPTURE_RESUME"
        const val ACTION_SPLIT_SEGMENT = "com.example.action.CAPTURE_SPLIT"
        const val ACTION_STOP = "com.example.action.CAPTURE_STOP"

        private val _state = MutableStateFlow(RecordingServiceState())
        val state: StateFlow<RecordingServiceState> = _state.asStateFlow()

        fun sendCommand(context: Context, action: String) {
            val intent = Intent(context, AudioCaptureService::class.java).apply {
                this.action = action
            }
            if (action == ACTION_START && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
