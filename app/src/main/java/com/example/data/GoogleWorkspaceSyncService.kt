package com.example.data

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.example.data.local.ActionCategory
import com.example.data.local.SmartActionEntity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GoogleWorkspaceSyncService {
    private const val TAG = "GoogleWorkspaceSync"

    private val requestedScopes = listOf(
        Scope("https://www.googleapis.com/auth/calendar.events"),
        Scope("https://www.googleapis.com/auth/tasks")
    )

    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun buildAuthorizationRequest(): AuthorizationRequest {
        return AuthorizationRequest.Builder()
            .setRequestedScopes(requestedScopes)
            .build()
    }

    fun handleAuthorizationResult(result: AuthorizationResult) {
        val token = result.accessToken
        if (!token.isNullOrBlank()) {
            _accessToken.value = token
            _isConnected.value = true
        }
    }

    fun handleActivityResult(activity: Activity, data: Intent?): Boolean {
        return try {
            val authResult = Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(data)
            handleAuthorizationResult(authResult)
            !authResult.accessToken.isNullOrBlank()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse authorization intent result", e)
            false
        }
    }

    fun disconnect() {
        _accessToken.value = null
        _isConnected.value = false
    }

    suspend fun syncAction(action: SmartActionEntity): Result<String> = withContext(Dispatchers.IO) {
        val token = _accessToken.value
            ?: return@withContext Result.failure(IllegalStateException("Google Workspace is not connected yet. Tap Connect Google first."))

        return@withContext if (action.category == ActionCategory.CALENDAR_EVENT.name) {
            createCalendarEvent(token, action)
        } else {
            createGoogleTask(token, action)
        }
    }

    private fun createCalendarEvent(token: String, action: SmartActionEntity): Result<String> {
        val startMillis = action.suggestedTimeMillis ?: (System.currentTimeMillis() + 3600_000L)
        val endMillis = action.endTimeMillis ?: (startMillis + 30 * 60_000L)

        val body = JSONObject().apply {
            put("summary", action.title)
            val desc = buildString {
                if (action.notes.isNotBlank()) append(action.notes)
                if (action.sourceExcerpt.isNotBlank()) {
                    if (isNotEmpty()) append("\n\n")
                    append("Source: \"${action.sourceExcerpt}\"")
                }
            }
            if (desc.isNotBlank()) put("description", desc)
            put("start", JSONObject().apply {
                put("dateTime", toRfc3339Utc(startMillis))
            })
            put("end", JSONObject().apply {
                put("dateTime", toRfc3339Utc(endMillis))
            })
        }

        return postJson(
            endpoint = "https://www.googleapis.com/calendar/v3/calendars/primary/events",
            token = token,
            payload = body,
            successLabel = "Synced to Google Calendar"
        )
    }

    private fun createGoogleTask(token: String, action: SmartActionEntity): Result<String> {
        val body = JSONObject().apply {
            put("title", action.title)
            val taskNotes = buildString {
                if (action.notes.isNotBlank()) append(action.notes)
                if (action.sourceExcerpt.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append("From DayPulse: \"${action.sourceExcerpt}\"")
                }
            }
            if (taskNotes.isNotBlank()) put("notes", taskNotes)
            action.suggestedTimeMillis?.let { dueMillis ->
                put("due", toRfc3339Utc(dueMillis))
            }
        }

        return postJson(
            endpoint = "https://tasks.googleapis.com/tasks/v1/lists/@default/tasks",
            token = token,
            payload = body,
            successLabel = "Synced to Google Tasks"
        )
    }

    private fun postJson(
        endpoint: String,
        token: String,
        payload: JSONObject,
        successLabel: String
    ): Result<String> {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(endpoint)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }
            val code = conn.responseCode
            if (code in 200..299) {
                Result.success(successLabel)
            } else {
                val errBody = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
                if (code == 401) {
                    _accessToken.value = null
                    _isConnected.value = false
                }
                Log.e(TAG, "Google API HTTP $code: $errBody")
                Result.failure(IllegalStateException("Google sync failed (HTTP $code). Reconnect Google or use local Calendar app."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google API request failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Network error syncing with Google."))
        } finally {
            conn?.disconnect()
        }
    }

    private fun toRfc3339Utc(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }
}
