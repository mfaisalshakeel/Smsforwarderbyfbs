package com.example.ui.viewmodel

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SmsForwarderApplication
import com.example.data.local.entity.ForwardingRuleEntity
import com.example.data.local.entity.SmsLogEntity
import com.example.data.preferences.ForwarderSettings
import com.example.forwarder.ForwardResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionState(
    val hasReceiveSms: Boolean = false,
    val hasSendSms: Boolean = false,
    val hasNotificationPost: Boolean = false,
    val hasNotificationAccess: Boolean = false,
    val hasReadPhoneState: Boolean = false
)

data class DashboardStats(
    val totalCount: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val activeRulesCount: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SmsForwarderApplication
    private val smsLogRepo = app.smsLogRepository
    private val ruleRepo = app.ruleRepository
    private val settingsRepo = app.settingsRepository
    private val forwardingManager = app.forwardingManager

    // Settings
    val settingsState: StateFlow<ForwarderSettings> = settingsRepo.settingsFlow

    // Forwarding Rules
    val rulesState: StateFlow<List<ForwardingRuleEntity>> = ruleRepo.allRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeRulesCount: StateFlow<Int> = ruleRepo.activeRulesCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Logs
    val recentLogs: StateFlow<List<SmsLogEntity>> = smsLogRepo.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchQuery = MutableStateFlow("")
    val filterStatus = MutableStateFlow("ALL")
    val filterDestination = MutableStateFlow("ALL")
    val filterSource = MutableStateFlow("ALL") // "ALL", "SMS", "NOTIFICATION"

    val logsState: StateFlow<List<SmsLogEntity>> = combine(
        smsLogRepo.allLogs,
        searchQuery,
        filterStatus,
        filterDestination
    ) { logs, query, status, dest ->
        logs.filter { log ->
            val matchQuery = query.isBlank() ||
                log.sender.contains(query, ignoreCase = true) ||
                log.body.contains(query, ignoreCase = true) ||
                log.destinationTarget.contains(query, ignoreCase = true) ||
                (log.ruleName ?: "").contains(query, ignoreCase = true)

            val matchStatus = status == "ALL" || log.status.equals(status, ignoreCase = true)
            val matchDest = dest == "ALL" ||
                log.destinationType.equals(dest, ignoreCase = true) ||
                log.source.equals(dest, ignoreCase = true)

            matchQuery && matchStatus && matchDest
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Permissions
    private val _permissionsState = MutableStateFlow(PermissionState())
    val permissionsState: StateFlow<PermissionState> = _permissionsState.asStateFlow()

    // Overall stats
    val statsState: StateFlow<DashboardStats> = combine(
        smsLogRepo.allLogs,
        ruleRepo.activeRulesCount
    ) { logs, activeRules ->
        DashboardStats(
            totalCount = logs.size,
            successCount = logs.count { it.status == "SUCCESS" },
            failedCount = logs.count { it.status == "FAILED" },
            activeRulesCount = activeRules
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    // Test account state
    private val _isTestingAccount = MutableStateFlow(false)
    val isTestingAccount: StateFlow<Boolean> = _isTestingAccount.asStateFlow()

    private val _accountTestResult = MutableStateFlow<ForwardResult?>(null)
    val accountTestResult: StateFlow<ForwardResult?> = _accountTestResult.asStateFlow()

    // Simulation state
    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    private val _lastSimulatedLogs = MutableStateFlow<List<SmsLogEntity>>(emptyList())
    val lastSimulatedLogs: StateFlow<List<SmsLogEntity>> = _lastSimulatedLogs.asStateFlow()

    fun updatePermissionsState(
        hasReceive: Boolean,
        hasSend: Boolean,
        hasNotif: Boolean,
        hasPhoneState: Boolean
    ) {
        val hasAccess = NotificationManagerCompat.getEnabledListenerPackages(getApplication())
            .contains(getApplication<Application>().packageName)

        _permissionsState.value = PermissionState(
            hasReceiveSms = hasReceive,
            hasSendSms = hasSend,
            hasNotificationPost = hasNotif,
            hasNotificationAccess = hasAccess,
            hasReadPhoneState = hasPhoneState
        )
    }

    fun toggleMasterSwitch(enabled: Boolean) {
        val current = settingsRepo.getSettings()
        settingsRepo.updateSettings(current.copy(isForwarderEnabled = enabled))
    }

    fun saveSettings(settings: ForwarderSettings) {
        settingsRepo.updateSettings(settings)
    }

    // Rules Management
    fun createRule(
        name: String,
        recipientEmail: String,
        forwardSms: Boolean,
        forwardNotifications: Boolean,
        simSlot: Int,
        senderFilterType: String,
        senderFilterValue: String,
        contentFilterType: String,
        contentFilterValue: String
    ) {
        viewModelScope.launch {
            val rule = ForwardingRuleEntity(
                name = name.ifBlank { "Forward to $recipientEmail" },
                recipientEmail = recipientEmail.trim(),
                forwardSms = forwardSms,
                forwardNotifications = forwardNotifications,
                simSlot = simSlot,
                senderFilterType = senderFilterType,
                senderFilterValue = senderFilterValue.trim(),
                contentFilterType = contentFilterType,
                contentFilterValue = contentFilterValue.trim(),
                isEnabled = true
            )
            ruleRepo.insertRule(rule)
        }
    }

    fun updateRule(rule: ForwardingRuleEntity) {
        viewModelScope.launch {
            ruleRepo.updateRule(rule)
        }
    }

    fun toggleRule(ruleId: Long, enabled: Boolean) {
        viewModelScope.launch {
            ruleRepo.setRuleEnabled(ruleId, enabled)
        }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch {
            ruleRepo.deleteRuleById(ruleId)
        }
    }

    // Account Test
    fun testSenderAccount(testRecipient: String) {
        viewModelScope.launch {
            val current = settingsRepo.getSettings()
            if (!current.isSenderAccountConfigured) {
                val errorMsg = if (current.authMethod == "GOOGLE_OAUTH") {
                    "Please connect your Google Account with 1 click first."
                } else {
                    "Please enter your sender Google Email and 16-character App Password first."
                }
                _accountTestResult.value = ForwardResult(
                    success = false,
                    errorMessage = errorMsg
                )
                return@launch
            }

            _isTestingAccount.value = true
            val target = testRecipient.ifBlank { current.senderEmailAccount }
            val result = forwardingManager.testSenderAccount(
                emailAccount = current.senderEmailAccount,
                appPassword = current.senderAppPassword,
                host = current.smtpHost,
                port = current.smtpPort,
                tls = current.smtpUseTls,
                testRecipient = target,
                authMethod = current.authMethod
            )
            _accountTestResult.value = result
            _isTestingAccount.value = false
        }
    }

    fun clearAccountTestResult() {
        _accountTestResult.value = null
    }

    // Simulate SMS
    fun simulateIncomingSms(sender: String, body: String, simSlot: Int = 0) {
        viewModelScope.launch {
            _isSimulating.value = true
            val results = forwardingManager.forwardIncomingSms(
                sender = sender,
                body = body,
                timestamp = System.currentTimeMillis(),
                simSlot = simSlot
            )
            _lastSimulatedLogs.value = results
            _isSimulating.value = false
        }
    }

    // Simulate Notification
    fun simulateIncomingNotification(appName: String, title: String, text: String) {
        viewModelScope.launch {
            _isSimulating.value = true
            val results = forwardingManager.forwardIncomingNotification(
                appName = appName,
                packageName = "com.sample.app",
                title = title,
                text = text,
                timestamp = System.currentTimeMillis()
            )
            _lastSimulatedLogs.value = results
            _isSimulating.value = false
        }
    }

    fun retryLog(logId: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            forwardingManager.retryForwarding(logId)
            onComplete()
        }
    }

    fun deleteLog(logId: Long) {
        viewModelScope.launch {
            smsLogRepo.deleteLogById(logId)
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            smsLogRepo.clearAllLogs()
        }
    }
}
