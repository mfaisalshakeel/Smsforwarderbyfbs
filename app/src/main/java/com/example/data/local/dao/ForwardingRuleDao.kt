package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ForwardingRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ForwardingRuleDao {

    @Query("SELECT * FROM forwarding_rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<ForwardingRuleEntity>>

    @Query("SELECT * FROM forwarding_rules WHERE isEnabled = 1")
    suspend fun getEnabledRules(): List<ForwardingRuleEntity>

    @Query("SELECT COUNT(*) FROM forwarding_rules WHERE isEnabled = 1")
    fun getActiveRulesCount(): Flow<Int>

    @Query("SELECT * FROM forwarding_rules WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: Long): ForwardingRuleEntity?

    @Query("SELECT * FROM forwarding_rules ORDER BY createdAt ASC")
    suspend fun getAllForExport(): List<ForwardingRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: ForwardingRuleEntity): Long

    @Update
    suspend fun updateRule(rule: ForwardingRuleEntity)

    @Query("DELETE FROM forwarding_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)

    @Query("UPDATE forwarding_rules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setRuleEnabled(id: Long, isEnabled: Boolean)
}
