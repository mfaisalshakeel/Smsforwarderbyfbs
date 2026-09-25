package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.util.SecretCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings live in two files: ordinary preferences, and a separate file holding only the
 * secrets, which is excluded from backup and encrypted with a Keystore key.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences =
        context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<ForwarderSettings> = _settingsFlow.asStateFlow()

    fun getSettings(): ForwarderSettings = _settingsFlow.value

    private fun loadSettings(): ForwarderSettings {
        val defaults = ForwarderSettings()
        return ForwarderSettings(
            isForwarderEnabled = prefs.getBoolean(KEY_ENABLED, defaults.isForwarderEnabled),
            notifyOnForward = prefs.getBoolean(KEY_NOTIFY, defaults.notifyOnForward),
            keepServiceAlive = prefs.getBoolean(KEY_KEEP_ALIVE, defaults.keepServiceAlive),
            authMethod = prefs.getString(KEY_AUTH_METHOD, defaults.authMethod) ?: defaults.authMethod,
            senderEmailAccount = prefs.getString(KEY_SENDER_EMAIL, "") ?: "",
            senderAppPassword = SecretCipher.decrypt(securePrefs.getString(KEY_APP_PASSWORD, "") ?: ""),
            senderDisplayName = prefs.getString(KEY_DISPLAY_NAME, defaults.senderDisplayName)
                ?: defaults.senderDisplayName,
            smtpHost = prefs.getString(KEY_SMTP_HOST, defaults.smtpHost) ?: defaults.smtpHost,
            smtpPort = prefs.getInt(KEY_SMTP_PORT, defaults.smtpPort),
            smtpUseTls = prefs.getBoolean(KEY_SMTP_TLS, defaults.smtpUseTls),
            dualSimEnabled = prefs.getBoolean(KEY_DUAL_SIM, defaults.dualSimEnabled),
            skipOngoingNotifications = prefs.getBoolean(KEY_SKIP_ONGOING, defaults.skipOngoingNotifications),
            skipGroupSummaries = prefs.getBoolean(KEY_SKIP_GROUP, defaults.skipGroupSummaries),
            duplicateWindowSeconds = prefs.getInt(KEY_DUPLICATE_WINDOW, defaults.duplicateWindowSeconds),
            maxRetryAttempts = prefs.getInt(KEY_MAX_RETRIES, defaults.maxRetryAttempts),
            retryOnlyOnWifi = prefs.getBoolean(KEY_RETRY_WIFI, defaults.retryOnlyOnWifi),
            quietHoursEnabled = prefs.getBoolean(KEY_QUIET_ENABLED, defaults.quietHoursEnabled),
            quietHoursStartMinute = prefs.getInt(KEY_QUIET_START, defaults.quietHoursStartMinute),
            quietHoursEndMinute = prefs.getInt(KEY_QUIET_END, defaults.quietHoursEndMinute),
            logRetentionDays = prefs.getInt(KEY_RETENTION_DAYS, defaults.logRetentionDays),
            themeMode = prefs.getString(KEY_THEME_MODE, defaults.themeMode) ?: defaults.themeMode,
            dynamicColorEnabled = prefs.getBoolean(KEY_DYNAMIC_COLOR, defaults.dynamicColorEnabled),
            includeBrandingFooter = prefs.getBoolean(KEY_BRANDING, defaults.includeBrandingFooter),
            appLockEnabled = prefs.getBoolean(KEY_APP_LOCK, defaults.appLockEnabled),
            onboardingCompleted = prefs.getBoolean(KEY_ONBOARDED, defaults.onboardingCompleted)
        )
    }

    fun updateSettings(newSettings: ForwarderSettings) {
        prefs.edit().apply {
            putBoolean(KEY_ENABLED, newSettings.isForwarderEnabled)
            putBoolean(KEY_NOTIFY, newSettings.notifyOnForward)
            putBoolean(KEY_KEEP_ALIVE, newSettings.keepServiceAlive)
            putString(KEY_AUTH_METHOD, newSettings.authMethod)
            putString(KEY_SENDER_EMAIL, newSettings.senderEmailAccount)
            putString(KEY_DISPLAY_NAME, newSettings.senderDisplayName)
            putString(KEY_SMTP_HOST, newSettings.smtpHost)
            putInt(KEY_SMTP_PORT, newSettings.smtpPort)
            putBoolean(KEY_SMTP_TLS, newSettings.smtpUseTls)
            putBoolean(KEY_DUAL_SIM, newSettings.dualSimEnabled)
            putBoolean(KEY_SKIP_ONGOING, newSettings.skipOngoingNotifications)
            putBoolean(KEY_SKIP_GROUP, newSettings.skipGroupSummaries)
            putInt(KEY_DUPLICATE_WINDOW, newSettings.duplicateWindowSeconds)
            putInt(KEY_MAX_RETRIES, newSettings.maxRetryAttempts)
            putBoolean(KEY_RETRY_WIFI, newSettings.retryOnlyOnWifi)
            putBoolean(KEY_QUIET_ENABLED, newSettings.quietHoursEnabled)
            putInt(KEY_QUIET_START, newSettings.quietHoursStartMinute)
            putInt(KEY_QUIET_END, newSettings.quietHoursEndMinute)
            putInt(KEY_RETENTION_DAYS, newSettings.logRetentionDays)
            putString(KEY_THEME_MODE, newSettings.themeMode)
            putBoolean(KEY_DYNAMIC_COLOR, newSettings.dynamicColorEnabled)
            putBoolean(KEY_BRANDING, newSettings.includeBrandingFooter)
            putBoolean(KEY_APP_LOCK, newSettings.appLockEnabled)
            putBoolean(KEY_ONBOARDED, newSettings.onboardingCompleted)
            apply()
        }
        securePrefs.edit()
            .putString(KEY_APP_PASSWORD, SecretCipher.encrypt(newSettings.senderAppPassword))
            .apply()

        _settingsFlow.value = newSettings
    }

    /** Re-reads from disk. Used after a settings restore. */
    fun reload() {
        _settingsFlow.value = loadSettings()
    }

    companion object {
        const val PREFS_NAME = "sms_forwarder_settings"
        const val SECURE_PREFS_NAME = "sms_forwarder_secure"

        private const val KEY_ENABLED = "is_forwarder_enabled"
        private const val KEY_NOTIFY = "notify_on_forward"
        private const val KEY_KEEP_ALIVE = "keep_service_alive"
        private const val KEY_AUTH_METHOD = "auth_method"
        private const val KEY_SENDER_EMAIL = "sender_email_account"
        private const val KEY_APP_PASSWORD = "sender_app_password"
        private const val KEY_DISPLAY_NAME = "sender_display_name"
        private const val KEY_SMTP_HOST = "smtp_host"
        private const val KEY_SMTP_PORT = "smtp_port"
        private const val KEY_SMTP_TLS = "smtp_use_tls"
        private const val KEY_DUAL_SIM = "dual_sim_enabled"
        private const val KEY_SKIP_ONGOING = "skip_ongoing_notifications"
        private const val KEY_SKIP_GROUP = "skip_group_summaries"
        private const val KEY_DUPLICATE_WINDOW = "duplicate_window_seconds"
        private const val KEY_MAX_RETRIES = "max_retry_attempts"
        private const val KEY_RETRY_WIFI = "retry_only_on_wifi"
        private const val KEY_QUIET_ENABLED = "quiet_hours_enabled"
        private const val KEY_QUIET_START = "quiet_hours_start"
        private const val KEY_QUIET_END = "quiet_hours_end"
        private const val KEY_RETENTION_DAYS = "log_retention_days"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color_enabled"
        private const val KEY_BRANDING = "include_branding_footer"
        private const val KEY_APP_LOCK = "app_lock_enabled"
        private const val KEY_ONBOARDED = "onboarding_completed"
    }
}
