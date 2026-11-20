package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.privacyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "daypulse_privacy_prefs"
)

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Midnight"),
    LIGHT("Daylight")
}

data class PrivacyPreferences(
    val autoDeleteAudioAfterTranscribe: Boolean = true,
    val segmentDurationMinutes: Int = 5,
    val eveningReviewHour: Int = 20,
    val eveningReviewMinute: Int = 0,
    val wifiOnlyProcessing: Boolean = false,
    val eveningReminderEnabled: Boolean = true,
    val themeMode: String = AppThemeMode.SYSTEM.name,
    val glassEffectsEnabled: Boolean = true
) {
    fun resolvedThemeMode(): AppThemeMode =
        AppThemeMode.entries.firstOrNull { it.name == themeMode } ?: AppThemeMode.SYSTEM
}

class PrivacyPreferencesStore(private val context: Context) {
    private object Keys {
        val AUTO_DELETE_AUDIO = booleanPreferencesKey("auto_delete_audio")
        val SEGMENT_DURATION_MINS = intPreferencesKey("segment_duration_mins")
        val EVENING_HOUR = intPreferencesKey("evening_hour")
        val EVENING_MINUTE = intPreferencesKey("evening_minute")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val EVENING_REMINDER_ENABLED = booleanPreferencesKey("evening_reminder_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val GLASS_EFFECTS_ENABLED = booleanPreferencesKey("glass_effects_enabled")
    }

    val preferencesFlow: Flow<PrivacyPreferences> = context.privacyDataStore.data.map { prefs ->
        PrivacyPreferences(
            autoDeleteAudioAfterTranscribe = prefs[Keys.AUTO_DELETE_AUDIO] ?: true,
            segmentDurationMinutes = prefs[Keys.SEGMENT_DURATION_MINS] ?: 5,
            eveningReviewHour = prefs[Keys.EVENING_HOUR] ?: 20,
            eveningReviewMinute = prefs[Keys.EVENING_MINUTE] ?: 0,
            wifiOnlyProcessing = prefs[Keys.WIFI_ONLY] ?: false,
            eveningReminderEnabled = prefs[Keys.EVENING_REMINDER_ENABLED] ?: true,
            themeMode = prefs[Keys.THEME_MODE] ?: AppThemeMode.SYSTEM.name,
            glassEffectsEnabled = prefs[Keys.GLASS_EFFECTS_ENABLED] ?: true
        )
    }

    suspend fun setAutoDeleteAudio(enabled: Boolean) {
        context.privacyDataStore.edit { it[Keys.AUTO_DELETE_AUDIO] = enabled }
    }

    suspend fun setSegmentDurationMinutes(minutes: Int) {
        context.privacyDataStore.edit { it[Keys.SEGMENT_DURATION_MINS] = minutes.coerceIn(1, 15) }
    }

    suspend fun setWifiOnlyProcessing(enabled: Boolean) {
        context.privacyDataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    suspend fun setEveningReminder(enabled: Boolean, hour: Int, minute: Int) {
        context.privacyDataStore.edit {
            it[Keys.EVENING_REMINDER_ENABLED] = enabled
            it[Keys.EVENING_HOUR] = hour.coerceIn(16, 23)
            it[Keys.EVENING_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.privacyDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setGlassEffectsEnabled(enabled: Boolean) {
        context.privacyDataStore.edit { it[Keys.GLASS_EFFECTS_ENABLED] = enabled }
    }
}
