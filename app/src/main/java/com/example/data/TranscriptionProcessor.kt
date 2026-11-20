package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.ai.GeminiAiService
import com.example.data.local.AppDatabase
import com.example.data.local.CaptureEntryEntity
import com.example.data.local.CaptureType
import com.example.data.local.ProcessingStatus
import com.example.data.prefs.PrivacyPreferencesStore
import com.example.widget.WidgetSyncHelper
import com.example.worker.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Coordinates immediate in-process transcription/photo analysis and WorkManager fallback.
 * Uses a Mutex so concurrent triggers (Service stop, NetworkCallback, WorkManager, manual retry)
 * never cancel or duplicate work, and recovers rows left in TRANSCRIBING if interrupted.
 */
object TranscriptionProcessor {
    private const val TAG = "TranscriptionProcessor"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val aiService by lazy { GeminiAiService() }

    fun isNetworkAvailable(context: Context, wifiOnly: Boolean = false): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!hasInternet) return false
        return if (wifiOnly) {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            true
        }
    }

    fun recoverStuckAndTrigger(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            if (!mutex.isLocked) {
                AppDatabase.getInstance(appContext).captureDao().resetStuckTranscribingToQueued()
            }
            processPendingOnce(appContext, includeFailed = false)
        }
    }

    fun triggerAsync(context: Context, includeFailed: Boolean = true) {
        val appContext = context.applicationContext
        scope.launch {
            processPendingOnce(appContext, includeFailed = includeFailed)
        }
    }

    fun triggerSingleAsync(context: Context, entryId: Long) {
        val appContext = context.applicationContext
        scope.launch {
            val db = AppDatabase.getInstance(appContext)
            val captureDao = db.captureDao()
            mutex.withLock {
                val entry = captureDao.getById(entryId) ?: return@withLock
                val prefs = PrivacyPreferencesStore(appContext).preferencesFlow.first()
                if (!isNetworkAvailable(appContext, wifiOnly = false)) {
                    captureDao.update(
                        entry.copy(
                            processingStatus = ProcessingStatus.QUEUED_OFFLINE.name,
                            errorMessage = "Device is offline. Queued in SQLite."
                        )
                    )
                    return@withLock
                }
                processSingleEntry(appContext, entry, prefs.autoDeleteAudioAfterTranscribe)
            }
        }
    }

    /**
     * Processes all pending items. Returns true if all completed or none pending,
     * false if any failed due to transient network/API errors (for WorkManager retry).
     */
    suspend fun processPendingOnce(context: Context, includeFailed: Boolean = true): Boolean {
        val appContext = context.applicationContext
        return mutex.withLock {
            val db = AppDatabase.getInstance(appContext)
            val captureDao = db.captureDao()
            val prefs = PrivacyPreferencesStore(appContext).preferencesFlow.first()

            // Recover any rows stranded in TRANSCRIBING before acquiring the lock
            captureDao.resetStuckTranscribingToQueued()

            if (!isNetworkAvailable(appContext, prefs.wifiOnlyProcessing)) {
                return@withLock false
            }

            val pending = captureDao.getPendingCapturesOnce().filter {
                includeFailed || it.processingStatus == ProcessingStatus.QUEUED_OFFLINE.name
            }
            if (pending.isEmpty()) return@withLock true

            var allSucceeded = true
            for (entry in pending) {
                val ok = processSingleEntry(appContext, entry, prefs.autoDeleteAudioAfterTranscribe)
                if (!ok) allSucceeded = false
            }
            allSucceeded
        }
    }

    private suspend fun processSingleEntry(
        appContext: Context,
        entry: CaptureEntryEntity,
        autoDeleteAudio: Boolean
    ): Boolean {
        val captureDao = AppDatabase.getInstance(appContext).captureDao()

        return when (entry.type) {
            CaptureType.AUDIO.name -> {
                val path = entry.localMediaPath
                val file = path?.let { File(it) }
                if (file == null || !file.exists()) {
                    captureDao.update(
                        entry.copy(
                            processingStatus = ProcessingStatus.FAILED.name,
                            errorMessage = "Local audio file not found on device."
                        )
                    )
                    return false
                }

                if (file.length() > 19L * 1024L * 1024L) {
                    captureDao.update(
                        entry.copy(
                            processingStatus = ProcessingStatus.FAILED.name,
                            errorMessage = "Audio chunk exceeds 19 MB limit."
                        )
                    )
                    return false
                }

                captureDao.update(
                    entry.copy(
                        processingStatus = ProcessingStatus.TRANSCRIBING.name,
                        errorMessage = null
                    )
                )

                val bytes = file.readBytes()
                val result = aiService.transcribeAudio(bytes)
                result.fold(
                    onSuccess = { transcribed ->
                        var deleted = entry.audioDeleted
                        if (autoDeleteAudio) {
                            deleted = file.delete()
                        }
                        captureDao.update(
                            entry.copy(
                                transcriptText = transcribed.transcript,
                                aiCaption = transcribed.shortTitle,
                                processingStatus = ProcessingStatus.COMPLETED.name,
                                errorMessage = null,
                                audioDeleted = deleted
                            )
                        )
                        inspectChunkAndNotify(appContext, entry.dateKey, transcribed.transcript)
                        true
                    },
                    onFailure = { err ->
                        Log.e(TAG, "Audio transcription failed for ${entry.id}", err)
                        captureDao.update(
                            entry.copy(
                                processingStatus = ProcessingStatus.FAILED.name,
                                errorMessage = err.localizedMessage ?: "Transcription failed."
                            )
                        )
                        false
                    }
                )
            }

            CaptureType.PHOTO.name -> {
                val path = entry.localMediaPath
                val file = path?.let { File(it) }
                if (file == null || !file.exists()) {
                    captureDao.update(
                        entry.copy(
                            processingStatus = ProcessingStatus.FAILED.name,
                            errorMessage = "Local photo file not found."
                        )
                    )
                    return false
                }

                captureDao.update(
                    entry.copy(
                        processingStatus = ProcessingStatus.TRANSCRIBING.name,
                        errorMessage = null
                    )
                )

                val bitmap = decodeScaledBitmap(file.absolutePath, 1600)
                if (bitmap == null) {
                    captureDao.update(
                        entry.copy(
                            processingStatus = ProcessingStatus.FAILED.name,
                            errorMessage = "Could not decode image."
                        )
                    )
                    return false
                }

                val result = aiService.analyzePhoto(bitmap)
                bitmap.recycle()
                result.fold(
                    onSuccess = { analysis ->
                        captureDao.update(
                            entry.copy(
                                transcriptText = analysis.extractedNotes,
                                aiCaption = analysis.shortTitle,
                                processingStatus = ProcessingStatus.COMPLETED.name,
                                errorMessage = null
                            )
                        )
                        inspectChunkAndNotify(appContext, entry.dateKey, analysis.extractedNotes)
                        true
                    },
                    onFailure = { err ->
                        Log.e(TAG, "Photo analysis failed for ${entry.id}", err)
                        captureDao.update(
                            entry.copy(
                                processingStatus = ProcessingStatus.FAILED.name,
                                errorMessage = err.localizedMessage ?: "Photo analysis failed."
                            )
                        )
                        false
                    }
                )
            }

            else -> {
                captureDao.update(entry.copy(processingStatus = ProcessingStatus.COMPLETED.name))
                WidgetSyncHelper.updateAllWidgets(appContext)
                true
            }
        }
    }

    /**
     * Checks a freshly processed chunk for new tasks/reminders/schedules.
     * If 0 items are detected, stays completely silent (no push notification, no widget approval prompt).
     * If >= 1 new item is detected, pushes a notification and updates home widgets for approval.
     */
    private suspend fun inspectChunkAndNotify(
        appContext: Context,
        dateKey: String,
        chunkText: String
    ) {
        runCatching {
            val db = AppDatabase.getInstance(appContext)
            val existingTitles = db.smartActionDao().getActionsForDateOnce(dateKey).map { it.title }
            val extracted = aiService.extractChunkActions(dateKey, chunkText, existingTitles)
                .getOrDefault(emptyList())

            if (extracted.isNotEmpty()) {
                val inserted = JournalRepository(appContext).deduplicateAndInsertActions(dateKey, extracted)
                if (inserted.isNotEmpty()) {
                    NotificationHelper.postChunkTasksDetected(appContext, inserted)
                }
            }
        }.onFailure { err ->
            Log.w(TAG, "Chunk task inspection skipped: ${err.localizedMessage}")
        }
        // Always refresh widget capture count/status quietly
        runCatching { WidgetSyncHelper.updateAllWidgets(appContext) }
    }

    private fun decodeScaledBitmap(path: String, maxDimension: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        val maxOrig = maxOf(options.outWidth, options.outHeight)
        var sampleSize = 1
        while (maxOrig / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(path, decodeOptions)
    }
}
