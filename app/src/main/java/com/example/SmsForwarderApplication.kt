package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.preferences.SettingsRepository
import com.example.data.repository.ForwardingRuleRepository
import com.example.data.repository.SmsLogRepository
import com.example.forwarder.ForwardScheduler
import com.example.forwarder.ForwardingManager
import com.example.service.ForwarderForegroundService
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsForwarderApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: AppDatabase
        private set

    lateinit var smsLogRepository: SmsLogRepository
        private set

    lateinit var ruleRepository: ForwardingRuleRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var notificationHelper: NotificationHelper
        private set

    lateinit var forwardingManager: ForwardingManager
        private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getDatabase(this)
        smsLogRepository = SmsLogRepository(database.smsLogDao())
        ruleRepository = ForwardingRuleRepository(database.forwardingRuleDao())
        settingsRepository = SettingsRepository(this)
        notificationHelper = NotificationHelper(this)
        forwardingManager = ForwardingManager(
            context = this,
            settingsRepository = settingsRepository,
            ruleRepository = ruleRepository,
            smsLogRepository = smsLogRepository,
            notificationHelper = notificationHelper
        )

        val settings = settingsRepository.getSettings()
        if (settings.isForwarderEnabled && settings.keepServiceAlive) {
            ForwarderForegroundService.start(this)
        }

        applicationScope.launch {
            // Anything left in the queue when the app was last killed goes out now.
            runCatching {
                ForwardScheduler.enqueueImmediateDrain(
                    context = this@SmsForwarderApplication,
                    requireUnmeteredNetwork = settings.retryOnlyOnWifi
                )
            }
            runCatching { forwardingManager.applyRetention() }
        }
    }
}
