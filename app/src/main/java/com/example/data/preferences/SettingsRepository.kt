package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sms_forwarder_settings", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<ForwarderSettings> = _settingsFlow.asStateFlow()

    fun getSettings(): ForwarderSettings {
        return _settingsFlow.value
    }

    private fun loadSettings(): ForwarderSettings {
        return ForwarderSettings(
            isForwarderEnabled = prefs.getBoolean("is_forwarder_enabled", true),
            notifyOnForward = prefs.getBoolean("notify_on_forward", true),
            authMethod = prefs.getString("auth_method", "GOOGLE_OAUTH") ?: "GOOGLE_OAUTH",
            senderEmailAccount = prefs.getString("sender_email_account", "") ?: "",
            senderAppPassword = prefs.getString("sender_app_password", "") ?: "",
            senderDisplayName = prefs.getString("sender_display_name", "SMS & Notification Forwarder")
                ?: "SMS & Notification Forwarder",
            smtpHost = prefs.getString("smtp_host", "smtp.gmail.com") ?: "smtp.gmail.com",
            smtpPort = prefs.getInt("smtp_port", 587),
            smtpUseTls = prefs.getBoolean("smtp_use_tls", true),
            dualSimEnabled = prefs.getBoolean("dual_sim_enabled", true)
        )
    }

    fun updateSettings(newSettings: ForwarderSettings) {
        prefs.edit().apply {
            putBoolean("is_forwarder_enabled", newSettings.isForwarderEnabled)
            putBoolean("notify_on_forward", newSettings.notifyOnForward)
            putString("auth_method", newSettings.authMethod)
            putString("sender_email_account", newSettings.senderEmailAccount)
            putString("sender_app_password", newSettings.senderAppPassword)
            putString("sender_display_name", newSettings.senderDisplayName)
            putString("smtp_host", newSettings.smtpHost)
            putInt("smtp_port", newSettings.smtpPort)
            putBoolean("smtp_use_tls", newSettings.smtpUseTls)
            putBoolean("dual_sim_enabled", newSettings.dualSimEnabled)
            apply()
        }
        _settingsFlow.value = newSettings
    }
}
