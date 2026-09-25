package com.example.data.repository

import com.example.data.local.dao.SmsLogDao
import com.example.data.local.entity.SmsLogEntity
import kotlinx.coroutines.flow.Flow

class SmsLogRepository(private val smsLogDao: SmsLogDao) {

    val allLogs: Flow<List<SmsLogEntity>> = smsLogDao.getAllLogs()
    val recentLogs: Flow<List<SmsLogEntity>> = smsLogDao.getRecentLogs(10)
    val totalCount: Flow<Int> = smsLogDao.getTotalCount()
    val successCount: Flow<Int> = smsLogDao.getSuccessCount()
    val failedCount: Flow<Int> = smsLogDao.getFailedCount()

    fun getLogsByStatus(status: String): Flow<List<SmsLogEntity>> {
        return smsLogDao.getLogsByStatus(status)
    }

    fun getLogsByDestination(dest: String): Flow<List<SmsLogEntity>> {
        return smsLogDao.getLogsByDestination(dest)
    }

    suspend fun getLogById(id: Long): SmsLogEntity? {
        return smsLogDao.getLogById(id)
    }

    suspend fun insertLog(log: SmsLogEntity): Long {
        return smsLogDao.insertLog(log)
    }

    suspend fun updateLog(log: SmsLogEntity) {
        smsLogDao.updateLog(log)
    }

    suspend fun deleteLogById(id: Long) {
        smsLogDao.deleteLogById(id)
    }

    suspend fun deleteAllLogs() {
        smsLogDao.deleteAllLogs()
    }

    suspend fun clearAllLogs() {
        smsLogDao.deleteAllLogs()
    }
}
