package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SmsForwarderApplication
import com.example.data.local.entity.ForwardingRuleEntity
import com.example.data.local.entity.SmsLogEntity
import com.example.data.preferences.EngineState
import com.example.data.preferences.ForwarderSettings
import com.example.forwarder.ForwardOutcome
import com.example.forwarder.ForwardResult
import com.example.forwarder.GoogleAuthRecovery
import com.example.service.ForwarderForegroundService
import com.example.util.BackgroundHealth
import com.example.util.BackgroundHealthChecker
import com.example.util.BackupManager
import com.example.util.BiometricHelper
import com.example.util.LockAvailability
import com.example.util.PowerHelper
import com.example.util.SimHelper
import com.example.util.SimInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionState(
    val hasReceiveSms: Boolean = false,
    val hasSendSms: Boolean = false,
    val hasNotificationPost: Boolean = false,
    val hasNotificationAccess: Boolean = false,
    val hasReadPhoneState: Boolean = false,
    val hasReadCallLog: Boolean = false,
    val isBatteryOptimised: Boolean = true
) {
    /** The minimum needed for SMS forwarding to work at all. */
    val isCoreReady: Boolean get() = hasReceiveSms

    val outstandingCount: Int
        get() = listOf(
            hasReceiveSms,
            hasNotificationPost,
            !isBatteryOptimised
        ).count { !it }
}

data class DashboardStats(
    val totalCount: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val pendingCount: Int = 0,
    val activeRulesCount: Int = 0
)

/** A transient message shown in a snackbar. */
data class UiMessage(val text: String, val isError: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SmsForwarderApplication
    private val smsLogRepo = app.smsLogRepository
    private val ruleRepo = app.ruleRepository
    private val settingsRepo = app.settingsRepository
    private val forwardingManager = app.forwardingManager
    private val engineStateStore = app.engineStateStore

    val settingsState: StateFlow<ForwarderSettings> = settingsRepo.settingsFlow

    val rulesState: StateFlow<List<ForwardingRuleEntity>> = ruleRepo.allRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentLogs: StateFlow<List<SmsLogEntity>> = smsLogRepo.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Log filtering ------------------------------------------------------
    val searchQuery = MutableStateFlow("")
    val filterStatus = MutableStateFlow(FILTER_ALL)
    val filterSource = MutableStateFlow(FILTER_ALL)

    /**
     * Filtering happens in SQL. The previous implementation streamed every row into memory and
     * filtered in Kotlin, which janked once the history grew.
     */
    val logsState: StateFlow<List<SmsLogEntity>> =
        combine(searchQuery, filterStatus, filterSource) { query, status, source ->
            Triple(query, status, source)
        }.flatMapLatest { (query, status, source) ->
            smsLogRepo.getFilteredLogs(query = query.trim(), status = status, source = source)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val statsState: StateFlow<DashboardStats> = combine(
        smsLogRepo.totalCount,
        smsLogRepo.successCount,
        smsLogRepo.failedCount,
        smsLogRepo.pendingCount,
        ruleRepo.activeRulesCount
    ) { total, success, failed, pending, activeRules ->
        DashboardStats(total, success, failed, pending, activeRules)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardStats())

    // ---- Device / permission state -----------------------------------------
    private val _permissionsState = MutableStateFlow(PermissionState())
    val permissionsState: StateFlow<PermissionState> = _permissionsState.asStateFlow()

    private val _availableSims = MutableStateFlow<List<SimInfo>>(emptyList())
    val availableSims: StateFlow<List<SimInfo>> = _availableSims.asStateFlow()

    private val _lockAvailability = MutableStateFlow(LockAvailability.UNSUPPORTED)
    val lockAvailability: StateFlow<LockAvailability> = _lockAvailability.asStateFlow()

    /** Bumped on resume and every minute so the health card's ages stay accurate. */
    private val _clock = MutableStateFlow(System.currentTimeMillis())
    val clock: StateFlow<Long> = _clock.asStateFlow()

    val engineState: StateFlow<EngineState> = engineStateStore.state

    /**
     * The honest answer to "is this working in the background right now?". Recomputed whenever
     * any input changes, so the dashboard never shows a stale all-clear.
     */
    val backgroundHealth: StateFlow<BackgroundHealth> = combine(
        settingsRepo.settingsFlow,
        _permissionsState,
        engineStateStore.state,
        ruleRepo.allRules,
        combine(smsLogRepo.pendingCount, _clock) { pending, now -> pending to now }
    ) { settings, permissions, engine, rules, pendingAndNow ->
        val (pending, now) = pendingAndNow
        val enabledRules = rules.filter { it.isEnabled }
        BackgroundHealthChecker.evaluate(
            now = now,
            engineEnabled = settings.isForwarderEnabled,
            keepServiceAlive = settings.keepServiceAlive,
            serviceRunning = engine.serviceRunning,
            lastHeartbeatAt = engine.lastHeartbeatAt,
            heartbeatStale = engine.isHeartbeatStale(now),
            lastEventAt = engine.lastEventAt,
            lastDeliveryAt = engine.lastDeliveryAt,
            lastBootRestartAt = engine.lastBootRestartAt,
            hasSmsPermission = permissions.hasReceiveSms,
            hasNotificationAccess = permissions.hasNotificationAccess,
            hasCallLogPermission = permissions.hasReadCallLog,
            appNotificationsEnabled = permissions.hasNotificationPost,
            isBatteryOptimised = permissions.isBatteryOptimised,
            manufacturerNeedsAutostart = PowerHelper.manufacturerNeedsAutostart(),
            senderAccountConfigured = settings.isSenderAccountConfigured,
            enabledRuleCount = enabledRules.size,
            rulesNeedingNotifications = enabledRules.count { it.forwardNotifications },
            rulesNeedingCallLog = enabledRules.count { it.forwardMissedCalls },
            queuedCount = pending
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BackgroundHealthChecker.evaluate(
            now = System.currentTimeMillis(),
            engineEnabled = true, keepServiceAlive = true, serviceRunning = true,
            lastHeartbeatAt = 0L, heartbeatStale = false, lastEventAt = 0L,
            lastDeliveryAt = 0L, lastBootRestartAt = 0L,
            hasSmsPermission = true, hasNotificationAccess = true, hasCallLogPermission = true,
            appNotificationsEnabled = true, isBatteryOptimised = false,
            manufacturerNeedsAutostart = false, senderAccountConfigured = true,
            enabledRuleCount = 1, rulesNeedingNotifications = 0, rulesNeedingCallLog = 0,
            queuedCount = 0
        )
    )

    init {
        // Keeps "last seen 3 minutes ago" honest without the user having to reopen the app.
        viewModelScope.launch {
            while (true) {
                delay(CLOCK_TICK_MILLIS)
                _clock.value = System.currentTimeMillis()
            }
        }
    }

    fun refreshClock() {
        _clock.value = System.currentTimeMillis()
    }

    /** Restarts the background service after the user taps Restart on the health card. */
    fun restartEngine() {
        val context = getApplication<Application>()
        val settings = settingsRepo.getSettings()
        if (!settings.isForwarderEnabled) {
            settingsRepo.updateSettings(settings.copy(isForwarderEnabled = true))
        }
        if (!settings.keepServiceAlive) {
            settingsRepo.updateSettings(settingsRepo.getSettings().copy(keepServiceAlive = true))
        }
        ForwarderForegroundService.start(context)
        refreshClock()
        _message.value = UiMessage("Background service restarted")
    }

    /** Consent screen parked by the background forwarder when Gmail needs re-authorising. */
    val pendingGoogleConsent: StateFlow<Intent?> = GoogleAuthRecovery.pendingConsent

    // ---- Transient UI state -------------------------------------------------
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _accountTestResult = MutableStateFlow<ForwardResult?>(null)
    val accountTestResult: StateFlow<ForwardResult?> = _accountTestResult.asStateFlow()

    private val _lastSimulatedLogs = MutableStateFlow<List<SmsLogEntity>>(emptyList())
    val lastSimulatedLogs: StateFlow<List<SmsLogEntity>> = _lastSimulatedLogs.asStateFlow()

    private val _simulationSummary = MutableStateFlow<String?>(null)
    val simulationSummary: StateFlow<String?> = _simulationSummary.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun clearGoogleConsent() = GoogleAuthRecovery.clear()

    // ---- Permissions --------------------------------------------------------

    fun updatePermissionsState(
        hasReceive: Boolean,
        hasSend: Boolean,
        hasNotif: Boolean,
        hasPhoneState: Boolean,
        hasCallLog: Boolean = false
    ) {
        val context = getApplication<Application>()
        val hasAccess = runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        }.getOrDefault(false)

        _permissionsState.value = PermissionState(
            hasReceiveSms = hasReceive,
            hasSendSms = hasSend,
            hasNotificationPost = hasNotif,
            hasNotificationAccess = hasAccess,
            hasReadPhoneState = hasPhoneState,
            hasReadCallLog = hasCallLog,
            isBatteryOptimised = !PowerHelper.isIgnoringBatteryOptimizations(context)
        )
        _lockAvailability.value = BiometricHelper.availability(context)
        refreshClock()

        if (hasPhoneState) {
            _availableSims.value = SimHelper.activeSims(context)
        }
    }

    // ---- Settings -----------------------------------------------------------

    fun toggleMasterSwitch(enabled: Boolean) {
        val updated = settingsRepo.getSettings().copy(isForwarderEnabled = enabled)
        settingsRepo.updateSettings(updated)
        syncForegroundService(updated)
        _message.value = UiMessage(if (enabled) "Forwarding resumed" else "Forwarding paused")
    }

    fun saveSettings(settings: ForwarderSettings) {
        settingsRepo.updateSettings(settings)
        syncForegroundService(settings)
    }

    fun completeOnboarding() {
        saveSettings(settingsRepo.getSettings().copy(onboardingCompleted = true))
    }

    private fun syncForegroundService(settings: ForwarderSettings) {
        val context = getApplication<Application>()
        if (settings.isForwarderEnabled && settings.keepServiceAlive) {
            ForwarderForegroundService.start(context)
        } else {
            ForwarderForegroundService.stop(context)
        }
    }

    // ---- Rules --------------------------------------------------------------

    fun saveRule(rule: ForwardingRuleEntity) {
        viewModelScope.launch {
            if (rule.id == 0L) {
                ruleRepo.insertRule(rule)
                _message.value = UiMessage("Rule created")
            } else {
                ruleRepo.updateRule(rule)
                _message.value = UiMessage("Rule updated")
            }
        }
    }

    fun toggleRule(ruleId: Long, enabled: Boolean) {
        viewModelScope.launch { ruleRepo.setRuleEnabled(ruleId, enabled) }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch {
            ruleRepo.deleteRuleById(ruleId)
            _message.value = UiMessage("Rule deleted")
        }
    }

    fun testRule(rule: ForwardingRuleEntity) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = forwardingManager.testRule(rule)
            _isBusy.value = false
            _message.value = if (result.success) {
                UiMessage("Test sent to ${rule.targets.firstOrNull().orEmpty()}")
            } else {
                UiMessage(result.errorMessage ?: "Test failed", isError = true)
            }
        }
    }

    // ---- Account test -------------------------------------------------------

    fun testSenderAccount(testRecipient: String) {
        viewModelScope.launch {
            val current = settingsRepo.getSettings()
            if (!current.isSenderAccountConfigured) {
                _accountTestResult.value = ForwardResult(
                    success = false,
                    errorMessage = "Connect your Google account first, or enter an app password."
                )
                return@launch
            }
            _isBusy.value = true
            _accountTestResult.value = forwardingManager.testSenderAccount(testRecipient)
            _isBusy.value = false
        }
    }

    fun clearAccountTestResult() {
        _accountTestResult.value = null
    }

    // ---- Simulator ----------------------------------------------------------

    fun simulateIncomingSms(sender: String, body: String, simSlot: Int = 0) {
        viewModelScope.launch {
            _isBusy.value = true
            val outcome = forwardingManager.forwardIncomingSms(
                sender = sender,
                body = body,
                simSlot = simSlot,
                simName = _availableSims.value.firstOrNull { it.slot == simSlot }?.displayName.orEmpty()
            )
            _lastSimulatedLogs.value = outcome.logs
            _simulationSummary.value = describe(outcome)
            _isBusy.value = false
        }
    }

    fun simulateIncomingMms(sender: String, body: String, attachmentCount: Int = 1) {
        viewModelScope.launch {
            _isBusy.value = true
            val outcome = forwardingManager.forwardIncomingMms(
                sender = sender,
                body = body,
                attachmentCount = attachmentCount
            )
            _lastSimulatedLogs.value = outcome.logs
            _simulationSummary.value = describe(outcome)
            _isBusy.value = false
        }
    }

    fun simulateMissedCall(number: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val outcome = forwardingManager.forwardMissedCall(
                number = number,
                displayName = number.ifBlank { "Unknown number" }
            )
            _lastSimulatedLogs.value = outcome.logs
            _simulationSummary.value = describe(outcome)
            _isBusy.value = false
        }
    }

    fun simulateIncomingNotification(appName: String, packageName: String, title: String, text: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val outcome = forwardingManager.forwardIncomingNotification(
                appName = appName,
                packageName = packageName.ifBlank { "com.example.sample" },
                title = title,
                text = text
            )
            _lastSimulatedLogs.value = outcome.logs
            _simulationSummary.value = describe(outcome)
            _isBusy.value = false
        }
    }

    private fun describe(outcome: ForwardOutcome): String = when {
        outcome.skippedReason != null -> "Not forwarded: ${outcome.skippedReason}"
        outcome.attempted == 0 -> "No rule matched this message."
        else -> buildList {
            if (outcome.delivered > 0) add("${outcome.delivered} delivered")
            if (outcome.batched > 0) add("${outcome.batched} added to a digest")
            if (outcome.queued > 0) add("${outcome.queued} queued for retry")
            if (outcome.failed > 0) add("${outcome.failed} failed")
        }.joinToString(", ")
    }

    // ---- Logs ---------------------------------------------------------------

    fun retryLog(logId: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = forwardingManager.retryForwarding(logId)
            _isBusy.value = false
            _message.value = if (result.success) {
                UiMessage("Message resent")
            } else {
                UiMessage(result.errorMessage ?: "Retry failed", isError = true)
            }
            onComplete()
        }
    }

    fun deleteLog(logId: Long) {
        viewModelScope.launch { smsLogRepo.deleteLogById(logId) }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            smsLogRepo.clearAllLogs()
            _message.value = UiMessage("History cleared")
        }
    }

    // ---- Backup / export ----------------------------------------------------

    fun buildBackupJson(onReady: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val json = BackupManager.exportToJson(ruleRepo.getAllForExport(), settingsRepo.getSettings())
            _isBusy.value = false
            onReady(json)
        }
    }

    fun buildLogsCsv(onReady: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val csv = BackupManager.exportLogsToCsv(smsLogRepo.getAllForExport())
            _isBusy.value = false
            onReady(csv)
        }
    }

    fun restoreBackup(json: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = BackupManager.importFromJson(json, settingsRepo.getSettings())
            if (result.error != null) {
                _message.value = UiMessage(result.error, isError = true)
            } else {
                result.rules.forEach { ruleRepo.insertRule(it) }
                result.settings?.let { settingsRepo.updateSettings(it) }
                _message.value = UiMessage("Restored ${result.rules.size} rule(s)")
            }
            _isBusy.value = false
        }
    }

    companion object {
        const val FILTER_ALL = "ALL"
        private const val CLOCK_TICK_MILLIS = 60_000L
    }
}
