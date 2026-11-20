package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Query("SELECT * FROM capture_entries WHERE dateKey = :dateKey ORDER BY timestamp DESC")
    fun observeCapturesForDate(dateKey: String): Flow<List<CaptureEntryEntity>>

    @Query("SELECT * FROM capture_entries WHERE dateKey = :dateKey ORDER BY timestamp ASC")
    suspend fun getCapturesForDateOnce(dateKey: String): List<CaptureEntryEntity>

    @Query(
        """
        SELECT COUNT(*) FROM capture_entries 
        WHERE dateKey = :dateKey AND includedInSummary = 0 
        AND (transcriptText != '' OR type = 'TEXT_NOTE')
        """
    )
    fun observeNewForSummaryCount(dateKey: String): Flow<Int>

    @Query("UPDATE capture_entries SET includedInSummary = 1 WHERE id IN (:ids)")
    suspend fun markCapturesAsSummarized(ids: List<Long>)

    @Query("UPDATE capture_entries SET includedInSummary = 0 WHERE dateKey = :dateKey")
    suspend fun resetSummarizedForDate(dateKey: String)

    @Query("SELECT * FROM capture_entries WHERE processingStatus IN ('QUEUED_OFFLINE', 'FAILED') ORDER BY timestamp ASC")
    suspend fun getPendingCapturesOnce(): List<CaptureEntryEntity>

    @Query("UPDATE capture_entries SET processingStatus = 'QUEUED_OFFLINE' WHERE processingStatus = 'TRANSCRIBING'")
    suspend fun resetStuckTranscribingToQueued()

    @Query("SELECT COUNT(*) FROM capture_entries WHERE processingStatus IN ('QUEUED_OFFLINE', 'TRANSCRIBING')")
    fun observeQueuedCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM capture_entries 
        WHERE (:query = '' OR transcriptText LIKE '%' || :query || '%' OR aiCaption LIKE '%' || :query || '%')
        ORDER BY timestamp DESC
        """
    )
    fun searchCaptures(query: String): Flow<List<CaptureEntryEntity>>

    @Query("SELECT DISTINCT dateKey FROM capture_entries ORDER BY dateKey DESC")
    fun observeDistinctDateKeys(): Flow<List<String>>

    @Query("SELECT * FROM capture_entries WHERE id = :id")
    suspend fun getById(id: Long): CaptureEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CaptureEntryEntity): Long

    @Update
    suspend fun update(entry: CaptureEntryEntity)

    @Query("DELETE FROM capture_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface SummaryDao {
    @Query("SELECT * FROM daily_summaries WHERE dateKey = :dateKey")
    fun observeSummaryForDate(dateKey: String): Flow<DailySummaryEntity?>

    @Query("SELECT * FROM daily_summaries WHERE dateKey = :dateKey")
    suspend fun getSummaryForDateOnce(dateKey: String): DailySummaryEntity?

    @Query("SELECT * FROM daily_summaries ORDER BY dateKey DESC")
    fun observeAllSummaries(): Flow<List<DailySummaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: DailySummaryEntity)
}

@Dao
interface SmartActionDao {
    @Query("SELECT * FROM smart_actions WHERE dateKey = :dateKey ORDER BY id ASC")
    fun observeActionsForDate(dateKey: String): Flow<List<SmartActionEntity>>

    @Query("SELECT * FROM smart_actions WHERE dateKey = :dateKey ORDER BY id ASC")
    suspend fun getActionsForDateOnce(dateKey: String): List<SmartActionEntity>

    @Query("SELECT * FROM smart_actions WHERE reviewStatus = 'ACCEPTED' ORDER BY isCompleted ASC, suggestedTimeMillis ASC, createdAt DESC")
    fun observeAllAcceptedActions(): Flow<List<SmartActionEntity>>

    @Query("SELECT COUNT(*) FROM smart_actions WHERE dateKey = :dateKey AND reviewStatus = 'PENDING_REVIEW'")
    fun observePendingReviewCount(dateKey: String): Flow<Int>

    @Query("SELECT * FROM smart_actions WHERE id = :id")
    suspend fun getById(id: Long): SmartActionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(actions: List<SmartActionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: SmartActionEntity): Long

    @Update
    suspend fun update(action: SmartActionEntity)

    @Query("DELETE FROM smart_actions WHERE dateKey = :dateKey AND reviewStatus = 'PENDING_REVIEW'")
    suspend fun clearPendingForDate(dateKey: String)

    @Query("DELETE FROM smart_actions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE dateKey = :dateKey ORDER BY timestamp ASC, id ASC")
    fun observeMessagesForDate(dateKey: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE dateKey = :dateKey ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesForDateOnce(dateKey: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE dateKey = :dateKey")
    suspend fun clearForDate(dateKey: String)
}
