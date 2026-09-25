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

    @Query("SELECT * FROM sms_logs ORDER BY receivedAt DESC")
    fun getAllLogs(): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs ORDER BY receivedAt DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 10): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs WHERE status = :status ORDER BY receivedAt DESC")
    fun getLogsByStatus(status: String): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs WHERE destinationType = :destinationType ORDER BY receivedAt DESC")
    fun getLogsByDestination(destinationType: String): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: Long): SmsLogEntity?

    @Query("SELECT COUNT(*) FROM sms_logs")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status = 'SUCCESS'")
    fun getSuccessCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status = 'FAILED'")
    fun getFailedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SmsLogEntity): Long

    @Update
    suspend fun updateLog(log: SmsLogEntity)

    @Query("DELETE FROM sms_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    @Query("DELETE FROM sms_logs")
    suspend fun deleteAllLogs()
}
