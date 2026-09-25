package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.SmsLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsLogDao {

    @Query("SELECT * FROM sms_logs ORDER BY receivedAt DESC LIMIT :limit")
    fun getAllLogs(limit: Int = 500): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs ORDER BY receivedAt DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 10): Flow<List<SmsLogEntity>>

    /**
     * Filtered log query. Passing "ALL" for [status]/[source] disables that filter, and a blank
     * [query] disables the text search. Filtering in SQL keeps the whole table out of memory.
     */
    @Query(
        """
        SELECT * FROM sms_logs
        WHERE (:status = 'ALL' OR status = :status)
          AND (:source = 'ALL' OR source = :source)
          AND (
            :query = '' OR
            sender LIKE '%' || :query || '%' OR
            body LIKE '%' || :query || '%' OR
            destinationTarget LIKE '%' || :query || '%' OR
            IFNULL(ruleName, '') LIKE '%' || :query || '%'
          )
        ORDER BY receivedAt DESC
        LIMIT :limit
        """
    )
    fun getFilteredLogs(
        query: String,
        status: String,
        source: String,
        limit: Int = 500
    ): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: Long): SmsLogEntity?

    @Query("SELECT COUNT(*) FROM sms_logs")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status = 'SUCCESS'")
    fun getSuccessCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status = 'FAILED'")
    fun getFailedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    /** Entries the retry worker should pick up. */
    @Query(
        """
        SELECT * FROM sms_logs
        WHERE status IN ('PENDING', 'FAILED')
          AND nextAttemptAt IS NOT NULL
          AND nextAttemptAt <= :now
        ORDER BY receivedAt ASC
        LIMIT :limit
        """
    )
    suspend fun getDueForRetry(now: Long, limit: Int = 50): List<SmsLogEntity>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status IN ('PENDING', 'FAILED') AND nextAttemptAt IS NOT NULL")
    suspend fun countQueued(): Int

    /** Used to suppress a notification that was already forwarded moments ago. */
    @Query(
        """
        SELECT COUNT(*) FROM sms_logs
        WHERE contentHash = :contentHash
          AND contentHash != ''
          AND receivedAt >= :since
        """
    )
    suspend fun countRecentWithHash(contentHash: String, since: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SmsLogEntity): Long

    @Update
    suspend fun updateLog(log: SmsLogEntity)

    @Query("DELETE FROM sms_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    @Query("DELETE FROM sms_logs")
    suspend fun deleteAllLogs()

    /** Retention clean-up: drops settled entries older than [cutoff]. */
    @Query("DELETE FROM sms_logs WHERE receivedAt < :cutoff AND nextAttemptAt IS NULL")
    suspend fun deleteOlderThan(cutoff: Long): Int

    /** Everything, oldest first — used by the CSV export. */
    @Query("SELECT * FROM sms_logs ORDER BY receivedAt ASC")
    suspend fun getAllForExport(): List<SmsLogEntity>
}
