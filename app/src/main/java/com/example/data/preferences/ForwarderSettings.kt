package com.example.data.preferences

data class ForwarderSettings(
    val isForwarderEnabled: Boolean = true,
    val notifyOnForward: Boolean = true,
    
    // Auth Method: "GOOGLE_OAUTH" (1-Click Google Account) or "SMTP" (Manual App Password)
    val authMethod: String = "GOOGLE_OAUTH",

    // Linked Sender Email Account (Google Account / SMTP)
    val senderEmailAccount: String = "",
    val senderAppPassword: String = "",
    val senderDisplayName: String = "SMS & Notification Forwarder",
    val smtpHost: String = "smtp.gmail.com",
    val smtpPort: Int = 587,
    val smtpUseTls: Boolean = true,

    // Dual SIM detection enabled
    val dualSimEnabled: Boolean = true
) {
    val isSenderAccountConfigured: Boolean
        get() = if (authMethod == "GOOGLE_OAUTH") {
            senderEmailAccount.isNotBlank()
        } else {
            senderEmailAccount.isNotBlank() && senderAppPassword.isNotBlank()
        }
}
