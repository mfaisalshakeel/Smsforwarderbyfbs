package com.example.data.preferences

object AuthMethod {
    /** One-tap Google account, sends through the Gmail REST API. */
    const val GOOGLE_OAUTH = "GOOGLE_OAUTH"
    /** Classic SMTP with a 16-character app password. */
    const val SMTP = "SMTP"
}

object ThemeMode {
    const val SYSTEM = "SYSTEM"
    const val LIGHT = "LIGHT"
    const val DARK = "DARK"
}

data class ForwarderSettings(
    val isForwarderEnabled: Boolean = true,
    val notifyOnForward: Boolean = true,
    val keepServiceAlive: Boolean = true,

    val authMethod: String = AuthMethod.GOOGLE_OAUTH,

    // Linked sender account (Google account or SMTP login)
    val senderEmailAccount: String = "",
    val senderAppPassword: String = "",
    val senderDisplayName: String = "SMS & Notification Forwarder",
    val smtpHost: String = "smtp.gmail.com",
    val smtpPort: Int = 587,
    val smtpUseTls: Boolean = true,

    val dualSimEnabled: Boolean = true,

    // Notification capture
    /** Drop music players, download bars and other persistent notifications. */
    val skipOngoingNotifications: Boolean = true,
    /** Drop the collapsed "3 new messages" summary that sits above a bundle. */
    val skipGroupSummaries: Boolean = true,
    /** Suppress a repeat of the same app + title + text within this window. */
    val duplicateWindowSeconds: Int = 60,

    // Delivery
    val maxRetryAttempts: Int = 5,
    val retryOnlyOnWifi: Boolean = false,

    // Quiet hours (applies to every rule that does not define its own schedule)
    val quietHoursEnabled: Boolean = false,
    val quietHoursStartMinute: Int = 22 * 60,
    val quietHoursEndMinute: Int = 7 * 60,

    // Housekeeping
    /** Days of history to keep. 0 means keep everything. */
    val logRetentionDays: Int = 90,

    // Presentation
    val themeMode: String = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    /** Appends the developer credit line to every forwarded message. */
    val includeBrandingFooter: Boolean = true,

    // Privacy
    val appLockEnabled: Boolean = false,

    /** Set once the user has been through the setup flow. */
    val onboardingCompleted: Boolean = false
) {
    val isSenderAccountConfigured: Boolean
        get() = if (authMethod == AuthMethod.GOOGLE_OAUTH) {
            senderEmailAccount.isNotBlank()
        } else {
            senderEmailAccount.isNotBlank() && senderAppPassword.isNotBlank()
        }
}
