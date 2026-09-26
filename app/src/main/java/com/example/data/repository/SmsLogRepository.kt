package com.example.data.repository

import com.example.data.local.dao.SmsLogDao
import com.example.data.local.entity.SmsLogEntity
import kotlinx.coroutines.flow.Flow

class SmsLogRepository(private val smsLogDao: SmsLogDao) {

    val recentLogs: Flow<List<SmsLogEntity>> = smsLogDao.getRecentLogs(10)
    val totalCount: Flow<Int> = smsLogDao.getTotalCount()
    val successCount: Flow<Int> = smsLogDao.getSuccessCount()
    val failedCount: Flow<Int> = smsLogDao.getFailedCount()
    val pendingCount: Flow<Int> = smsLogDao.getPendingCount()

    fun getFilteredLogs(query: String, status: String, source: String): Flow<List<SmsLogEntity>> =
        smsLogDao.getFilteredLogs(query = query, status = status, source = source)

    suspend fun getLogById(id: Long): SmsLogEntity? = smsLogDao.getLogById(id)

    suspend fun getDueForRetry(now: Long): List<SmsLogEntity> = smsLogDao.getDueForRetry(now)

    suspend fun countQueued(): Int = smsLogDao.countQueued()

    val batchedCount: Flow<Int> = smsLogDao.getBatchedCount()

    suspend fun getDueDigestEntries(now: Long): List<SmsLogEntity> = smsLogDao.getDueDigestEntries(now)

    suspend fun getOpenBatchDueAt(ruleId: Long): Long? = smsLogDao.getOpenBatchDueAt(ruleId)

    suspend fun getNextBatchDueAt(): Long? = smsLogDao.getNextBatchDueAt()

    suspend fun wasRecentlyForwarded(contentHash: String, since: Long): Boolean =
        contentHash.isNotEmpty() && smsLogDao.countRecentWithHash(contentHash, since) > 0

    suspend fun insertLog(log: SmsLogEntity): Long = smsLogDao.insertLog(log)

    suspend fun updateLog(log: SmsLogEntity) = smsLogDao.updateLog(log)

    suspend fun deleteLogById(id: Long) = smsLogDao.deleteLogById(id)

    suspend fun clearAllLogs() = smsLogDao.deleteAllLogs()

    suspend fun deleteOlderThan(cutoff: Long): Int = smsLogDao.deleteOlderThan(cutoff)

    suspend fun getAllForExport(): List<SmsLogEntity> = smsLogDao.getAllForExport()
}
