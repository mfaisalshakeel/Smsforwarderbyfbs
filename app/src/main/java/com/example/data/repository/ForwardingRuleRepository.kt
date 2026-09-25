package com.example.data.repository

import com.example.data.local.dao.ForwardingRuleDao
import com.example.data.local.entity.ForwardingRuleEntity
import kotlinx.coroutines.flow.Flow

class ForwardingRuleRepository(private val ruleDao: ForwardingRuleDao) {

    val allRules: Flow<List<ForwardingRuleEntity>> = ruleDao.getAllRules()
    val activeRulesCount: Flow<Int> = ruleDao.getActiveRulesCount()

    suspend fun getEnabledRules(): List<ForwardingRuleEntity> {
        return ruleDao.getEnabledRules()
    }

    suspend fun getRuleById(id: Long): ForwardingRuleEntity? {
        return ruleDao.getRuleById(id)
    }

    suspend fun insertRule(rule: ForwardingRuleEntity): Long {
        return ruleDao.insertRule(rule)
    }

    suspend fun updateRule(rule: ForwardingRuleEntity) {
        ruleDao.updateRule(rule)
    }

    suspend fun deleteRuleById(id: Long) {
        ruleDao.deleteRuleById(id)
    }

    suspend fun setRuleEnabled(id: Long, isEnabled: Boolean) {
        ruleDao.setRuleEnabled(id, isEnabled)
    }
}
