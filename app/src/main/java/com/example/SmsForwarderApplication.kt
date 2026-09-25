package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.preferences.SettingsRepository
import com.example.data.repository.ForwardingRuleRepository
import com.example.data.repository.SmsLogRepository
import com.example.forwarder.ForwardingManager
import com.example.util.NotificationHelper

class SmsForwarderApplication : Application() {

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
        instance = this

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
    }

    companion object {
        lateinit var instance: SmsForwarderApplication
            private set
    }
}
