package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.ForwarderSettings
import com.example.ui.components.AppSwitch
import com.example.ui.components.ConnectedGoogleAccountBadge
import com.example.ui.components.GoogleSignInButton
import com.example.ui.components.PenduCoderFooter
import com.example.ui.components.PenduCoderPromoCard
import com.example.ui.theme.ErrorColorDark
import com.example.ui.theme.ErrorColorLight
import com.example.ui.theme.ErrorContainerDark
import com.example.ui.theme.ErrorContainerLight
import com.example.ui.theme.SuccessColorDark
import com.example.ui.theme.SuccessColorLight
import com.example.ui.theme.SuccessContainerDark
import com.example.ui.theme.SuccessContainerLight
import com.example.ui.theme.WarningColorDark
import com.example.ui.theme.WarningColorLight
import com.example.ui.theme.WarningContainerDark
import com.example.ui.theme.WarningContainerLight
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentSettings by viewModel.settingsState.collectAsStateWithLifecycle()
    val permissions by viewModel.permissionsState.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTestingAccount.collectAsStateWithLifecycle()
    val testResult by viewModel.accountTestResult.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()

    val successBg = if (isDark) SuccessContainerDark else SuccessContainerLight
    val successText = if (isDark) SuccessColorDark else SuccessColorLight
    val errorBg = if (isDark) ErrorContainerDark else ErrorContainerLight
    val errorText = if (isDark) ErrorColorDark else ErrorColorLight
    val warningBg = if (isDark) WarningContainerDark else WarningContainerLight
    val warningText = if (isDark) WarningColorDark else WarningColorLight

    var authMethod by remember(currentSettings) { mutableStateOf(currentSettings.authMethod) }
    var senderEmail by remember(currentSettings) { mutableStateOf(currentSettings.senderEmailAccount) }
    var appPassword by remember(currentSettings) { mutableStateOf(currentSettings.senderAppPassword) }
    var displayName by remember(currentSettings) { mutableStateOf(currentSettings.senderDisplayName) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var isForwarderEnabled by remember(currentSettings) { mutableStateOf(currentSettings.isForwarderEnabled) }
    var notifyOnForward by remember(currentSettings) { mutableStateOf(currentSettings.notifyOnForward) }
    var dualSimEnabled by remember(currentSettings) { mutableStateOf(currentSettings.dualSimEnabled) }

    var showAdvancedSmtp by remember { mutableStateOf(false) }
    var smtpHost by remember(currentSettings) { mutableStateOf(currentSettings.smtpHost) }
    var smtpPortText by remember(currentSettings) { mutableStateOf(currentSettings.smtpPort.toString()) }
    var smtpUseTls by remember(currentSettings) { mutableStateOf(currentSettings.smtpUseTls) }

    fun saveAll(customEmail: String? = null, customAuthMethod: String? = null) {
        val emailToSave = (customEmail ?: senderEmail).trim()
        val methodToSave = customAuthMethod ?: authMethod
        val port = smtpPortText.toIntOrNull() ?: 587
        val updated = currentSettings.copy(
            isForwarderEnabled = isForwarderEnabled,
            notifyOnForward = notifyOnForward,
            authMethod = methodToSave,
            senderEmailAccount = emailToSave,
            senderAppPassword = appPassword.trim(),
            senderDisplayName = displayName.trim(),
            smtpHost = smtpHost.trim(),
            smtpPort = port,
            smtpUseTls = smtpUseTls,
            dualSimEnabled = dualSimEnabled
        )
        viewModel.saveSettings(updated)
        scope.launch {
            snackbarHostState.showSnackbar("Settings saved successfully!")
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Google Account Link Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("google_account_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Sender Account",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Account used to send outgoing emails",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Status Badge
                        val isConfigured = currentSettings.isSenderAccountConfigured
                        Surface(
                            color = if (isConfigured) successBg else warningBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.5.dp, if (isConfigured) successText.copy(alpha = 0.4f) else warningText.copy(alpha = 0.4f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isConfigured) successText else warningText,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isConfigured) "Active" else "Not Setup",
                                    color = if (isConfigured) successText else warningText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Setup Mode Selector (1-Click Google OAuth vs Manual App Password)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            onClick = {
                                authMethod = "GOOGLE_OAUTH"
                                saveAll(customAuthMethod = "GOOGLE_OAUTH")
                            },
                            shape = RoundedCornerShape(9.dp),
                            color = if (authMethod == "GOOGLE_OAUTH") MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = "⚡ 1-Click Google",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (authMethod == "GOOGLE_OAUTH") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                authMethod = "SMTP"
                                saveAll(customAuthMethod = "SMTP")
                            },
                            shape = RoundedCornerShape(9.dp),
                            color = if (authMethod == "SMTP") MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = "⚙️ App Password",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (authMethod == "SMTP") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (authMethod == "GOOGLE_OAUTH") {
                        // 1-Click Google OAuth Section
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "⚡ Easy 1-2 Click Setup (No App Password)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap 'Connect with Google', select your Gmail account and tap 'Allow'. Incoming SMS will be sent from this account automatically!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // "Connect with Google" Button
                        GoogleSignInButton(
                            onAccountSelected = { pickedEmail ->
                                senderEmail = pickedEmail
                                authMethod = "GOOGLE_OAUTH"
                                if (displayName.isBlank() || displayName == "SMS & Notification Forwarder") {
                                    displayName = pickedEmail.substringBefore("@")
                                }
                                saveAll(customEmail = pickedEmail, customAuthMethod = "GOOGLE_OAUTH")
                            },
                            buttonText = if (senderEmail.isNotBlank()) "Change Connected Google Account" else "Connect with Google (1-Click)"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (senderEmail.isNotBlank()) {
                            ConnectedGoogleAccountBadge(
                                email = senderEmail,
                                hasAppPassword = false,
                                authMethod = "GOOGLE_OAUTH",
                                onChangeClick = {
                                    senderEmail = ""
                                    saveAll(customEmail = "")
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Sender Display Name") },
                            placeholder = { Text("SMS & Notification Forwarder") },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // 1-Click Test Button
                        Button(
                            onClick = {
                                saveAll()
                                viewModel.testSenderAccount(senderEmail)
                            },
                            enabled = !isTesting && senderEmail.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("test_account_connection_button")
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sending Test Email via Gmail API...")
                            } else {
                                Icon(imageVector = Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send 1-Click Test Email", fontWeight = FontWeight.Bold)
                            }
                        }

                    } else {
                        // Manual SMTP / App Password Section
                        OutlinedTextField(
                            value = senderEmail,
                            onValueChange = { senderEmail = it },
                            label = { Text("Google Account / Gmail Address") },
                            placeholder = { Text("e.g. user@gmail.com") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Email, contentDescription = null)
                            },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sender_google_email_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = appPassword,
                            onValueChange = { appPassword = it },
                            label = { Text("Google App Password (16 letters)") },
                            placeholder = { Text("xxxx xxxx xxxx xxxx") },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Security, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sender_app_password_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Sender Display Name") },
                            placeholder = { Text("SMS & Notification Forwarder") },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Guide card on how to get App Password with 1-tap browser link
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.HelpOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Google App Password Guide:",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "1. Enable 2-Step Verification in your Google Account\n" +
                                        "2. Go to 'App passwords' in Google Security\n" +
                                        "3. Create a password named 'SMS Forwarder' and paste the 16 letters above.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://myaccount.google.com/apppasswords")
                                            ).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Google App Passwords Page", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Test Connection Button
                        OutlinedButton(
                            onClick = {
                                saveAll()
                                viewModel.testSenderAccount(senderEmail)
                            },
                            enabled = !isTesting && senderEmail.isNotBlank() && appPassword.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("test_account_connection_button")
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sending Test Email...")
                            } else {
                                Text("Test SMTP Connection")
                            }
                        }
                    }

                    // Inline test result
                    testResult?.let { result ->
                        Spacer(modifier = Modifier.height(10.dp))
                        val bannerBg = if (result.success) successBg else errorBg
                        val bannerText = if (result.success) successText else errorText
                        Surface(
                            color = bannerBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, bannerText.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (result.success) "Connection verified! Test email delivered to $senderEmail." else "Failed: ${result.errorMessage}",
                                    color = bannerText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.clearAccountTestResult() }) {
                                    Text("Dismiss", color = bannerText, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Notification Access & Permissions
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notification Forwarding Access",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "To allow forwarding of notifications from apps like WhatsApp, Telegram, or Banks, Android requires Notification Listener Access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hasAccess = permissions.hasNotificationAccess
                        Text(
                            text = if (hasAccess) "Access Granted" else "Access Not Granted",
                            fontWeight = FontWeight.Bold,
                            color = if (hasAccess) successText else warningText,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("open_notification_access_button")
                        ) {
                            Text(if (hasAccess) "Open Settings" else "Grant Access")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Section 3: Dual SIM & Engine Controls
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Forwarding Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Forwarding Engine",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Global master switch for forwarding",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AppSwitch(
                            checked = isForwarderEnabled,
                            onCheckedChange = {
                                isForwarderEnabled = it
                                saveAll()
                            },
                            modifier = Modifier.testTag("settings_forwarder_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dual SIM Support",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Identify SIM 1 and SIM 2 in forwarded messages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AppSwitch(
                            checked = dualSimEnabled,
                            onCheckedChange = {
                                dualSimEnabled = it
                                saveAll()
                            },
                            modifier = Modifier.testTag("dual_sim_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Forwarding Alerts",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Display notification when a message is forwarded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AppSwitch(
                            checked = notifyOnForward,
                            onCheckedChange = {
                                notifyOnForward = it
                                saveAll()
                            },
                            modifier = Modifier.testTag("notify_on_forward_switch")
                        )
                    }
                }
            }

            // Section 4: Advanced SMTP (Accordion)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Custom SMTP Server",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "For Outlook, Yahoo, or private mail servers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { showAdvancedSmtp = !showAdvancedSmtp }) {
                            Text(if (showAdvancedSmtp) "Hide" else "Show")
                        }
                    }

                    AnimatedVisibility(visible = showAdvancedSmtp) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = smtpHost,
                                    onValueChange = { smtpHost = it },
                                    label = { Text("SMTP Host") },
                                    modifier = Modifier.weight(2f),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = smtpPortText,
                                    onValueChange = { smtpPortText = it },
                                    label = { Text("Port") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Use TLS Encryption", style = MaterialTheme.typography.bodyMedium)
                                AppSwitch(
                                    checked = smtpUseTls,
                                    onCheckedChange = { smtpUseTls = it },
                                    modifier = Modifier.testTag("smtp_tls_switch")
                                )
                            }
                        }
                    }
                }
            }

            // Save All Button
            Button(
                onClick = { saveAll() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_all_settings_button")
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save All Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Developer Attribution & Promotion Section
            PenduCoderPromoCard(
                compact = false
            )

            PenduCoderFooter()

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
