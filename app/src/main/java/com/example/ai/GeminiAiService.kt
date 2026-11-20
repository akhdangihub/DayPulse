package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.local.ActionCategory
import com.example.data.local.ActionPriority
import com.example.data.local.CaptureEntryEntity
import com.example.data.local.DailySummaryEntity
import com.example.data.local.DateKeyUtil
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiAiService"
private const val MODEL_GENERAL = "gemini-3.8-flash"
private const val MODEL_FAST = "gemini-3.5-flash-lite"
private const val AI_TIMEOUT_MS = 60_000L

data class AudioTranscriptionResult(
    val transcript: String,
    val shortTitle: String
)

data class PhotoAnalysisResult(
    val extractedNotes: String,
    val shortTitle: String
)

data class ExtractedActionSuggestion(
    val category: ActionCategory,
    val title: String,
    val notes: String,
    val suggestedTimeMillis: Long?,
    val endTimeMillis: Long?,
    val priority: ActionPriority,
    val sourceExcerpt: String
)

data class QuickReviewSynthesisResult(
    val narrativeSummary: String,
    val moodTag: String,
    val highlights: List<String>,
    val openLoops: List<String>,
    val suggestedActions: List<ExtractedActionSuggestion>
)

class GeminiAiService {

    private val photoSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "shortTitle" to Schema.string(description = "3 to 6 word title describing the image content."),
                "extractedNotes" to Schema.string(description = "Detailed summary, transcribed handwriting/text, dates, prices, or action items visible in the image.")
            )
        )
    }

    private val quickReviewSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "narrativeSummary" to Schema.string(description = "A concise, clear cumulative summary of the day so far, integrating any new captures smoothly without repeating prior points verbatim."),
                "moodTag" to Schema.string(description = "A concise 1-2 word mood or focus theme, e.g. 'Deep Focus', 'Productive', 'Collaborative'."),
                "highlights" to Schema.array(
                    items = Schema.string(),
                    description = "New key moments or accomplishments from the newly captured entries only."
                ),
                "openLoops" to Schema.array(
                    items = Schema.string(),
                    description = "New unfinished thoughts or follow-ups from the newly captured entries."
                ),
                "suggestedActions" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "category" to Schema.enumeration(
                                values = listOf("REMINDER", "TODO", "CALENDAR_EVENT"),
                                description = "REMINDER for alerts, TODO for Google Tasks checklist items, CALENDAR_EVENT for scheduled meetings/events."
                            ),
                            "title" to Schema.string(description = "Concise actionable title under 60 characters."),
                            "notes" to Schema.string(description = "Brief helpful context from the capture."),
                            "dayOffset" to Schema.integer(description = "0 for today, 1 for tomorrow, 2 for two days out, etc."),
                            "suggestedHour24" to Schema.integer(description = "Suggested hour 0-23."),
                            "suggestedMinute" to Schema.integer(description = "Suggested minute 0-59."),
                            "durationMinutes" to Schema.integer(description = "Duration in minutes for calendar events (default 30)."),
                            "priority" to Schema.enumeration(
                                values = listOf("LOW", "MEDIUM", "HIGH"),
                                description = "Importance level."
                            ),
                            "sourceExcerpt" to Schema.string(description = "Short quote from the new capture that triggered this item.")
                        ),
                        optionalProperties = listOf("notes", "durationMinutes", "sourceExcerpt")
                    ),
                    description = "ONLY NEW smart reminders, to-dos, and calendar events from the newly captured entries that do NOT duplicate any existing actions."
                )
            )
        )
    }

    private val transcriptionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST,
            generationConfig = generationConfig {
                temperature = 0.2f
            }
        )
    }

    private val photoModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = photoSchema
                temperature = 0.2f
            }
        )
    }

    private val chunkActionsSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "suggestedActions" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "category" to Schema.enumeration(
                                values = listOf("REMINDER", "TODO", "CALENDAR_EVENT"),
                                description = "REMINDER for alerts, TODO for checklist/Google Tasks items, CALENDAR_EVENT for scheduled meetings/appointments."
                            ),
                            "title" to Schema.string(description = "Concise actionable title under 60 characters."),
                            "notes" to Schema.string(description = "Brief context from the chunk."),
                            "dayOffset" to Schema.integer(description = "0 for today, 1 for tomorrow, 2 for two days out, etc."),
                            "suggestedHour24" to Schema.integer(description = "Suggested hour 0-23."),
                            "suggestedMinute" to Schema.integer(description = "Suggested minute 0-59."),
                            "durationMinutes" to Schema.integer(description = "Duration in minutes for calendar events (default 30)."),
                            "priority" to Schema.enumeration(
                                values = listOf("LOW", "MEDIUM", "HIGH"),
                                description = "Priority scale: HIGH (urgent/deadline), MEDIUM (standard task/meeting), LOW (optional/later)."
                            ),
                            "sourceExcerpt" to Schema.string(description = "Short quote from the chunk.")
                        ),
                        optionalProperties = listOf("notes", "durationMinutes", "sourceExcerpt")
                    ),
                    description = "Empty array [] if no tasks, reminders, or schedules appear in this chunk."
                )
            )
        )
    }

    private val quickReviewModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = quickReviewSchema
                temperature = 0.25f
            }
        )
    }

    private val chunkActionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = chunkActionsSchema
                temperature = 0.15f
            }
        )
    }

    suspend fun transcribeAudio(audioBytes: ByteArray): Result<AudioTranscriptionResult> {
        if (audioBytes.isEmpty()) {
            return Result.failure(IllegalStateException("Audio recording is empty."))
        }
        Log.d(TAG, "Starting Gemini audio transcription (${audioBytes.size} bytes) with $MODEL_FAST")
        return try {
            val accumulated = StringBuilder()
            withTimeout(AI_TIMEOUT_MS) {
                transcriptionModel.generateContentStream(
                    content {
                        inlineData(audioBytes, "audio/mp4")
                        text(
                            "Transcribe this personal audio segment verbatim with clear punctuation in the spoken language. " +
                                "On the first line write 'Title: <3 to 6 word summary>', then on the next lines write only the transcription. " +
                                "If only silence or ambient noise is present, write 'Title: Ambient Audio' and '[No speech detected]'."
                        )
                    }
                ).collect { chunk ->
                    accumulated.append(chunk.text.orEmpty())
                }
            }
            val raw = accumulated.toString().trim()
            if (raw.isBlank()) {
                return Result.failure(IllegalStateException("Gemini returned an empty transcription."))
            }
            val lines = raw.lines()
            val firstLine = lines.firstOrNull()?.trim().orEmpty()
            val (title, transcript) = if (firstLine.startsWith("Title:", ignoreCase = true)) {
                val parsedTitle = firstLine.substringAfter(":").trim().ifBlank { "Voice Segment" }
                val body = lines.drop(1).joinToString("\n").trim().ifBlank { "[No speech detected]" }
                parsedTitle to body
            } else {
                "Voice Segment" to raw
            }
            Result.success(
                AudioTranscriptionResult(
                    transcript = transcript,
                    shortTitle = title
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Audio transcription timed out", e)
            Result.failure(IllegalStateException("Transcription timed out after 60s. Tap Retry to try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Audio transcription failed", e)
            val msg = e.localizedMessage ?: e.javaClass.simpleName
            val friendly = if (msg.contains("403") || msg.contains("App Check", ignoreCase = true)) {
                "Firebase App Check rejected request (403): $msg"
            } else {
                msg
            }
            Result.failure(IllegalStateException(friendly, e))
        }
    }

    suspend fun analyzePhoto(bitmap: Bitmap): Result<PhotoAnalysisResult> {
        return try {
            val accumulated = StringBuilder()
            withTimeout(AI_TIMEOUT_MS) {
                photoModel.generateContentStream(
                    content {
                        image(bitmap)
                        text(
                            "Analyze this photo captured during the user's day (e.g., whiteboard, handwritten note, receipt, flyer, or scene). " +
                                "Extract any text, action items, dates, and key visual context clearly."
                        )
                    }
                ).collect { chunk ->
                    accumulated.append(chunk.text.orEmpty())
                }
            }
            val raw = accumulated.toString()
            if (raw.isBlank()) return Result.failure(IllegalStateException("Gemini returned an empty photo analysis."))
            val json = JSONObject(raw)
            Result.success(
                PhotoAnalysisResult(
                    extractedNotes = json.optString("extractedNotes", "No readable details extracted."),
                    shortTitle = json.optString("shortTitle", "Photo Capture").ifBlank { "Photo Capture" }
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Photo analysis timed out", e)
            Result.failure(IllegalStateException("Photo analysis timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Photo analysis failed", e)
            Result.failure(e)
        }
    }

    suspend fun synthesizeQuickReview(
        dateKey: String,
        newCaptures: List<CaptureEntryEntity>,
        existingSummary: DailySummaryEntity?,
        existingActionTitles: List<String>
    ): Result<QuickReviewSynthesisResult> {
        if (newCaptures.isEmpty()) {
            return Result.failure(IllegalStateException("No new captures to summarize for $dateKey."))
        }

        val ledgerBuilder = StringBuilder()
        ledgerBuilder.appendLine("Date: $dateKey (${DateKeyUtil.formatDateKeyLabel(dateKey)})")
        if (existingSummary != null) {
            ledgerBuilder.appendLine("\n=== PREVIOUS SUMMARY EARLIER TODAY (DO NOT DUPLICATE) ===")
            ledgerBuilder.appendLine(existingSummary.narrativeSummary)
        }
        if (existingActionTitles.isNotEmpty()) {
            ledgerBuilder.appendLine("\n=== ALREADY EXTRACTED/APPROVED TASKS & SCHEDULES (DO NOT SUGGEST AGAIN) ===")
            existingActionTitles.forEach { title -> ledgerBuilder.appendLine("- $title") }
        }
        ledgerBuilder.appendLine("\n=== NEW CAPTURES TO PROCESS NOW ===")
        newCaptures.sortedBy { it.timestamp }.forEachIndexed { idx, entry ->
            val timeStr = DateKeyUtil.formatClockTime(entry.timestamp)
            val body = entry.transcriptText.ifBlank { entry.aiCaption }
            if (body.isNotBlank()) {
                ledgerBuilder.appendLine("${idx + 1}. [$timeStr | ${entry.type}] ${entry.aiCaption}: $body")
            }
        }

        return try {
            val accumulated = StringBuilder()
            withTimeout(AI_TIMEOUT_MS) {
                quickReviewModel.generateContentStream(
                    content {
                        text(
                            """
                            You are DayPulse's fast Quick Review synthesizer. Match the language of the user's captures (e.g., Bahasa Indonesia or English).
                            Rules:
                            1. If a previous summary from earlier today exists, update the narrativeSummary to cleanly integrate the NEW captures with earlier events without repeating points verbatim.
                            2. Extract ONLY new highlights, new openLoops, and genuinely NEW suggestedActions (Reminders, To-Dos, Calendar Events) found in the NEW CAPTURES.
                            3. NEVER suggest any task, reminder, or schedule that already exists in ALREADY EXTRACTED/APPROVED TASKS & SCHEDULES.
                            
                            $ledgerBuilder
                            """.trimIndent()
                        )
                    }
                ).collect { chunk ->
                    accumulated.append(chunk.text.orEmpty())
                }
            }
            val raw = accumulated.toString()
            if (raw.isBlank()) {
                return Result.failure(IllegalStateException("Gemini returned an empty Quick Review."))
            }
            val json = JSONObject(raw)

            val highlights = mutableListOf<String>()
            val hlArr = json.optJSONArray("highlights")
            if (hlArr != null) {
                for (i in 0 until hlArr.length()) {
                    val item = hlArr.optString(i).trim()
                    if (item.isNotBlank()) highlights.add(item)
                }
            }

            val openLoops = mutableListOf<String>()
            val olArr = json.optJSONArray("openLoops")
            if (olArr != null) {
                for (i in 0 until olArr.length()) {
                    val item = olArr.optString(i).trim()
                    if (item.isNotBlank()) openLoops.add(item)
                }
            }

            val actions = parseSuggestedActions(json.optJSONArray("suggestedActions"))

            Result.success(
                QuickReviewSynthesisResult(
                    narrativeSummary = json.optString("narrativeSummary", "Summary updated."),
                    moodTag = json.optString("moodTag", "Focused"),
                    highlights = highlights,
                    openLoops = openLoops,
                    suggestedActions = actions
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Quick Review synthesis timed out", e)
            Result.failure(IllegalStateException("Quick Review timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Quick Review synthesis failed", e)
            Result.failure(e)
        }
    }

    suspend fun extractChunkActions(
        dateKey: String,
        chunkText: String,
        existingActionTitles: List<String>
    ): Result<List<ExtractedActionSuggestion>> {
        val cleanText = chunkText.trim()
        if (cleanText.isBlank() || cleanText.equals("[No speech detected]", ignoreCase = true)) {
            return Result.success(emptyList())
        }

        val prompt = buildString {
            appendLine("Date: $dateKey (${DateKeyUtil.formatDateKeyLabel(dateKey)})")
            if (existingActionTitles.isNotEmpty()) {
                appendLine("\nALREADY TRACKED TITLES (DO NOT REPEAT):")
                existingActionTitles.forEach { appendLine("- $it") }
            }
            appendLine("\nNEW CHUNK TRANSCRIPT:")
            appendLine(cleanText)
            appendLine(
                """
                
                Rules:
                1. Extract ONLY genuinely NEW actionable tasks (TODO), reminders (REMINDER), or calendar events/appointments (CALENDAR_EVENT) present in this chunk.
                2. If this chunk has NO tasks, deadlines, reminders, or scheduled events (e.g. casual talk or ambient notes), return an EMPTY array [].
                3. Preserve the LOW / MEDIUM / HIGH priority scale carefully based on urgency.
                4. Match the spoken language (e.g., Bahasa Indonesia or English) for titles and notes.
                """.trimIndent()
            )
        }

        return try {
            val accumulated = StringBuilder()
            withTimeout(AI_TIMEOUT_MS) {
                chunkActionModel.generateContentStream(
                    content { text(prompt) }
                ).collect { chunk ->
                    accumulated.append(chunk.text.orEmpty())
                }
            }
            val raw = accumulated.toString()
            if (raw.isBlank()) return Result.success(emptyList())
            val json = JSONObject(raw)
            Result.success(parseSuggestedActions(json.optJSONArray("suggestedActions")))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Chunk action extraction skipped/failed: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    private fun parseSuggestedActions(actArr: org.json.JSONArray?): List<ExtractedActionSuggestion> {
        val actions = mutableListOf<ExtractedActionSuggestion>()
        if (actArr == null) return actions
        for (i in 0 until actArr.length()) {
            val obj = actArr.optJSONObject(i) ?: continue
            val category = runCatching {
                ActionCategory.valueOf(obj.optString("category", "TODO"))
            }.getOrDefault(ActionCategory.TODO)
            val priority = runCatching {
                ActionPriority.valueOf(obj.optString("priority", "MEDIUM"))
            }.getOrDefault(ActionPriority.MEDIUM)
            val dayOffset = obj.optInt("dayOffset", 0)
            val hour24 = obj.optInt("suggestedHour24", 9)
            val minute = obj.optInt("suggestedMinute", 0)
            val durationMins = obj.optInt("durationMinutes", 30).coerceIn(15, 240)
            val startMillis = DateKeyUtil.buildSuggestedMillis(dayOffset, hour24, minute)
            val endMillis = if (category == ActionCategory.CALENDAR_EVENT) {
                startMillis + durationMins * 60_000L
            } else null

            val title = obj.optString("title", "").trim()
            if (title.isNotBlank()) {
                actions.add(
                    ExtractedActionSuggestion(
                        category = category,
                        title = title,
                        notes = obj.optString("notes", "").trim(),
                        suggestedTimeMillis = startMillis,
                        endTimeMillis = endMillis,
                        priority = priority,
                        sourceExcerpt = obj.optString("sourceExcerpt", "").trim()
                    )
                )
            }
        }
        return actions
    }

    fun streamCompanionChat(
        dateKey: String,
        dayContext: String,
        priorHistory: List<Pair<String, String>>,
        userMessage: String
    ): Flow<String> = flow {
        val chatModel = Firebase.ai.generativeModel(
            modelName = MODEL_FAST,
            generationConfig = generationConfig {
                temperature = 0.4f
            },
            systemInstruction = content {
                text(
                    """
                    You are DayPulse's fast Day Companion for $dateKey. Reply in the user's language (Bahasa Indonesia or English).
                    Be concise, practical, and strictly grounded in the user's captures and summary below.
                    
                    === DAY CONTEXT ===
                    $dayContext
                    """.trimIndent()
                )
            }
        )

        val historyContent: List<Content> = priorHistory.takeLast(12).map { (role, text) ->
            content(role = role) { text(text) }
        }
        val chat = chatModel.startChat(history = historyContent)
        val accumulated = StringBuilder()

        withTimeout(AI_TIMEOUT_MS) {
            chat.sendMessageStream(userMessage).collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                if (chunkText.isNotEmpty()) {
                    accumulated.append(chunkText)
                    emit(accumulated.toString())
                }
            }
        }
    }
}
