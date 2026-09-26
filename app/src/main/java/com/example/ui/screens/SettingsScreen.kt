package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.AuthMethod
import com.example.data.preferences.ThemeMode
import com.example.ui.components.AppCard
import com.example.ui.components.BackgroundDiagnosticsCard
import com.example.ui.components.ConnectedAccountCard
import com.example.ui.components.ContentContainer
import com.example.ui.components.FieldLabel
import com.example.ui.components.GoogleSignInButton
import com.example.ui.components.InfoBanner
import com.example.ui.components.LoadingRow
import com.example.ui.components.PenduCoderFooter
import com.example.ui.components.PenduCoderPromoCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SettingNavRow
import com.example.ui.components.SettingSwitchRow
import com.example.ui.components.Tone
import com.example.ui.theme.Shapes
import com.example.ui.theme.Spacing
import com.example.ui.viewmodel.MainViewModel
import com.example.util.LockAvailability
import com.example.util.PowerHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenNotificationAccess: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onExportBackup: (String) -> Unit,
    onImportBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val permissions by viewModel.permissionsState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val isConnectingAccount by viewModel.isConnectingAccount.collectAsStateWithLifecycle()
    val isTransferring by viewModel.isTransferring.collectAsStateWithLifecycle()
    val testResult by viewModel.accountTestResult.collectAsStateWithLifecycle()
    val health by viewModel.backgroundHealth.collectAsStateWithLifecycle()
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val now by viewModel.clock.collectAsStateWithLifecycle()
    val lockAvailability by viewModel.lockAvailability.collectAsStateWithLifecycle()

    var testRecipient by rememberSaveable { mutableStateOf("") }
    // Deliberately `remember`, not `rememberSaveable`: the password must not be written into
    // the saved-instance-state Bundle.
    var appPassword by remember(settings.senderAppPassword) {
        mutableStateOf(settings.senderAppPassword)
    }
    var smtpHost by rememberSaveable(settings.smtpHost) { mutableStateOf(settings.smtpHost) }
    var smtpPort by rememberSaveable(settings.smtpPort) { mutableStateOf(settings.smtpPort.toString()) }
    var displayName by rememberSaveable(settings.senderDisplayName) {
        mutableStateOf(settings.senderDisplayName)
    }

    LaunchedEffect(settings.senderEmailAccount) {
        if (testRecipient.isBlank()) testRecipient = settings.senderEmailAccount
    }

    ContentContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // ---------------------------------------------------------- Sender account
            SectionHeader(
                title = "Sending account",
                subtitle = "The mailbox your forwarded messages are sent from"
            )

            AppCard {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    FilterChip(
                        selected = settings.authMethod == AuthMethod.GOOGLE_OAUTH,
                        onClick = {
                            viewModel.saveSettings(settings.copy(authMethod = AuthMethod.GOOGLE_OAUTH))
                        },
                        label = { Text("Google (recommended)") }
                    )
                    FilterChip(
                        selected = settings.authMethod == AuthMethod.SMTP,
                        onClick = { viewModel.saveSettings(settings.copy(authMethod = AuthMethod.SMTP)) },
                        label = { Text("App password") }
                    )
                }

                Spacer(modifier = Modifier.size(Spacing.md))

                if (settings.authMethod == AuthMethod.GOOGLE_OAUTH) {
                    InfoBanner(
                        title = "Needs Google Cloud setup",
                        message = "One-tap Google sending only works once this app has an OAuth " +
                            "client registered in Google Cloud for its package name and signing " +
                            "certificate. Until that is done, use App password — it works right " +
                            "now and sends through the same mailbox.",
                        tone = Tone.Info,
                        action = {
                            TextButton(
                                onClick = { viewModel.saveSettings(settings.copy(authMethod = AuthMethod.SMTP)) }
                            ) {
                                Text("Switch to App password")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.size(Spacing.md))

                    if (settings.senderEmailAccount.isBlank()) {
                        GoogleSignInButton(
                            isConnecting = isConnectingAccount,
                            onAccountSelected = viewModel::connectGoogleAccount
                        )
                    } else {
                        ConnectedAccountCard(
                            email = settings.senderEmailAccount,
                            onChangeAccount = viewModel::disconnectGoogleAccount
                        )
                        if (isConnectingAccount) {
                            Spacer(modifier = Modifier.size(Spacing.sm))
                            LoadingRow(text = "Checking with Google…")
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = settings.senderEmailAccount,
                        onValueChange = { viewModel.saveSettings(settings.copy(senderEmailAccount = it)) },
                        label = { Text("Email address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        shape = Shapes.field,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.size(Spacing.sm))
                    OutlinedTextField(
                        value = appPassword,
                        onValueChange = { appPassword = it },
                        label = { Text("App password") },
                        supportingText = {
                            Text("Gmail needs a 16-character app password, not your normal password.")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = Shapes.field,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_password_input")
                    )
                    Spacer(modifier = Modifier.size(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedTextField(
                            value = smtpHost,
                            onValueChange = { smtpHost = it },
                            label = { Text("SMTP host") },
                            singleLine = true,
                            shape = Shapes.field,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = smtpPort,
                            onValueChange = { smtpPort = it.filter { ch -> ch.isDigit() }.take(5) },
                            label = { Text("Port") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = Shapes.field,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.size(Spacing.sm))
                    SettingSwitchRow(
                        title = "Use STARTTLS",
                        subtitle = "Leave on for port 587. Turn off for port 465.",
                        checked = settings.smtpUseTls,
                        onCheckedChange = { viewModel.saveSettings(settings.copy(smtpUseTls = it)) }
                    )
                    Spacer(modifier = Modifier.size(Spacing.sm))
                    Button(
                        onClick = {
                            viewModel.saveSettings(
                                settings.copy(
                                    senderAppPassword = appPassword,
                                    smtpHost = smtpHost.trim(),
                                    smtpPort = smtpPort.toIntOrNull() ?: 587
                                )
                            )
                        },
                        shape = Shapes.button,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save mail server details")
                    }
                }

                Spacer(modifier = Modifier.size(Spacing.md))

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Sender name") },
                    supportingText = { Text("Shown as the \"From\" name on forwarded email") },
                    singleLine = true,
                    shape = Shapes.field,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.size(Spacing.sm))
                TextButton(
                    onClick = { viewModel.saveSettings(settings.copy(senderDisplayName = displayName)) }
                ) {
                    Text("Save sender name")
                }
            }

            // ---------------------------------------------------------- Test
            AppCard {
                FieldLabel(
                    text = "Send a test message",
                    helper = "Confirms the account really can send before you rely on it."
                )
                Spacer(modifier = Modifier.size(Spacing.sm))
                OutlinedTextField(
                    value = testRecipient,
                    onValueChange = { testRecipient = it },
                    label = { Text("Send test to") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    shape = Shapes.field,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.size(Spacing.sm))
                if (isBusy) {
                    LoadingRow(text = "Sending…")
                } else {
                    Button(
                        onClick = { viewModel.testSenderAccount(testRecipient) },
                        enabled = settings.isSenderAccountConfigured,
                        shape = Shapes.button,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("send_test_button")
                    ) {
                        Text("Send test message")
                    }
                }

                testResult?.let { result ->
                    Spacer(modifier = Modifier.size(Spacing.md))
                    InfoBanner(
                        title = if (result.success) "Test delivered" else "Test failed",
                        message = result.errorMessage ?: result.responseDetails.orEmpty(),
                        tone = if (result.success) Tone.Success else Tone.Danger,
                        icon = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                        action = {
                            TextButton(onClick = viewModel::clearAccountTestResult) { Text("Dismiss") }
                        }
                    )
                }
            }

            // ---------------------------------------------------------- Reliability
            SectionHeader(
                title = "Reliability",
                subtitle = "Keep the app running so messages are never missed"
            )

            BackgroundDiagnosticsCard(
                health = health,
                now = now,
                serviceRunning = engineState.serviceRunning,
                lastStopReason = engineState.lastStopReason
            )

            if (PowerHelper.manufacturerNeedsAutostart()) {
                InfoBanner(
                    title = "This phone needs autostart enabled",
                    message = PowerHelper.autostartInstructions(),
                    tone = Tone.Warning,
                    icon = Icons.Default.BatteryAlert
                )
            }

            AppCard {
                SettingSwitchRow(
                    title = "Keep running in the background",
                    subtitle = "Shows a quiet notification so Android does not shut the app down",
                    checked = settings.keepServiceAlive,
                    onCheckedChange = { viewModel.saveSettings(settings.copy(keepServiceAlive = it)) }
                )
                if (permissions.isBatteryOptimised) {
                    Spacer(modifier = Modifier.size(Spacing.sm))
                    InfoBanner(
                        title = "Battery optimisation is on",
                        message = "Android may freeze the app and you will miss messages. " +
                            "Allow unrestricted battery use to prevent this.",
                        tone = Tone.Warning,
                        icon = Icons.Default.BatteryAlert,
                        action = {
                            Button(onClick = onOpenBatterySettings, shape = Shapes.button) {
                                Text("Fix this")
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.size(Spacing.sm))
                SettingSwitchRow(
                    title = "Retry only on Wi-Fi",
                    subtitle = "Queued messages wait for Wi-Fi instead of using mobile data",
                    checked = settings.retryOnlyOnWifi,
                    onCheckedChange = { viewModel.saveSettings(settings.copy(retryOnlyOnWifi = it)) }
                )
                SettingSwitchRow(
                    title = "Notify me when a message is forwarded",
                    checked = settings.notifyOnForward,
                    onCheckedChange = { viewModel.saveSettings(settings.copy(notifyOnForward = it)) }
                )
            }

            // ---------------------------------------------------------- Notifications capture
            SectionHeader(
                title = "Notification capture",
                subtitle = "Controls which app notifications are eligible for forwarding"
            )

            AppCard {
                SettingNavRow(
                    title = "Notification access",
                    subtitle = if (permissions.hasNotificationAccess) {
                        "Granted"
                    } else {
                        "Required to forward app notifications"
                    },
                    leadingIcon = Icons.Default.NotificationsActive,
                    trailingText = if (permissions.hasNotificationAccess) null else "Grant",
                    onClick = onOpenNotificationAccess
                )
                SettingSwitchRow(
                    title = "Ignore ongoing notifications",
                    subtitle = "Skips music players, downloads and navigation",
                    checked = settings.skipOngoingNotifications,
                    onCheckedChange = {
                        viewModel.saveSettings(settings.copy(skipOngoingNotifications = it))
                    }
                )
                SettingSwitchRow(
                    title = "Ignore grouped summaries",
                    subtitle = "Skips the \"3 new messages\" header above a bundle",
                    checked = settings.skipGroupSummaries,
                    onCheckedChange = { viewModel.saveSettings(settings.copy(skipGroupSummaries = it)) }
                )
                Spacer(modifier = Modifier.size(Spacing.sm))
                FieldLabel(
                    text = "Ignore repeats within",
                    helper = "Stops the same notification being forwarded again and again."
                )
                Spacer(modifier = Modifier.size(Spacing.xs))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    listOf(0 to "Off", 30 to "30s", 60 to "1 min", 300 to "5 min").forEach { (value, label) ->
                        FilterChip(
                            selected = settings.duplicateWindowSeconds == value,
                            onClick = {
                                viewModel.saveSettings(settings.copy(duplicateWindowSeconds = value))
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // ---------------------------------------------------------- Quiet hours
            SectionHeader(title = "Quiet hours")
            AppCard {
                SettingSwitchRow(
                    title = "Pause forwarding at night",
                    subtitle = if (settings.quietHoursEnabled) {
                        "Paused from ${formatMinute(settings.quietHoursStartMinute)} " +
                            "to ${formatMinute(settings.quietHoursEndMinute)}"
                    } else {
                        "Applies to every rule that has no schedule of its own"
                    },
                    checked = settings.quietHoursEnabled,
                    onCheckedChange = { viewModel.saveSettings(settings.copy(quietHoursEnabled = it)) }
                )
            }

            // ---------------------------------------------------------- Appearance
            SectionHeader(title = "Appearance")
            AppCard {
                FieldLabel(text = "Theme")
                Spacer(modifier = Modifier.size(Spacing.xs))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    listOf(
                        ThemeMode.SYSTEM to "System",
                        ThemeMode.LIGHT to "Light",
                        ThemeMode.DARK to "Dark"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.saveSettings(settings.copy(themeMode = mode)) },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.size(Spacing.sm))
                SettingSwitchRow(
                    title = "Use my wallpaper colours",
                    subtitle = "Material You, on Android 12 and newer",
                    checked = settings.dynamicColorEnabled,
                    onCheckedChange = { viewModel.saveSettings(settings.copy(dynamicColorEnabled = it)) }
                )
                SettingSwitchRow(
                    title = "Add developer credit to messages",
                    subtitle = "Appends a short line at the end of each forwarded message",
                    checked = settings.includeBrandingFooter,
                    onCheckedChange = { viewModel.saveSettings(settings.copy(includeBrandingFooter = it)) }
                )
            }

            // ---------------------------------------------------------- History
            SectionHeader(title = "History")
            AppCard {
                FieldLabel(
                    text = "Keep history for",
                    helper = "Older entries are removed automatically."
                )
                Spacer(modifier = Modifier.size(Spacing.xs))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    listOf(
                        7 to "7 days",
                        30 to "30 days",
                        90 to "90 days",
                        365 to "1 year",
                        0 to "Forever"
                    ).forEach { (days, label) ->
                        FilterChip(
                            selected = settings.logRetentionDays == days,
                            onClick = { viewModel.saveSettings(settings.copy(logRetentionDays = days)) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // ---------------------------------------------------------- Backup
            SectionHeader(
                title = "Backup",
                subtitle = "Move your rules to another phone"
            )
            AppCard {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(
                        onClick = { viewModel.buildBackupJson(onExportBackup) },
                        enabled = !isTransferring,
                        shape = Shapes.button,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(Spacing.xs))
                        Text("Export")
                    }
                    OutlinedButton(
                        onClick = onImportBackup,
                        enabled = !isTransferring,
                        shape = Shapes.button,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.RestorePage, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(Spacing.xs))
                        Text("Import")
                    }
                }
                if (isTransferring) {
                    Spacer(modifier = Modifier.size(Spacing.md))
                    LoadingRow(text = "Working…")
                }
                Spacer(modifier = Modifier.size(Spacing.sm))
                Text(
                    text = "Passwords and bot tokens are never written to the backup file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------------------------------------------------------- Privacy
            SectionHeader(title = "Privacy")

            AppCard {
                SettingSwitchRow(
                    title = "Lock the app",
                    subtitle = when (lockAvailability) {
                        LockAvailability.AVAILABLE ->
                            "Ask for your fingerprint, face or PIN before showing your messages"
                        LockAvailability.NOT_ENROLLED ->
                            "Set a screen lock or fingerprint on this phone first"
                        LockAvailability.UNSUPPORTED ->
                            "This phone has no screen lock, so the app cannot be locked"
                    },
                    checked = settings.appLockEnabled,
                    onCheckedChange = { viewModel.saveSettings(settings.copy(appLockEnabled = it)) },
                    enabled = lockAvailability == LockAvailability.AVAILABLE
                )
            }

            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(Spacing.md))
                    Text(
                        text = "Everything stays on this device",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(modifier = Modifier.size(Spacing.sm))
                Text(
                    text = "Messages are read on your phone and sent straight to the destinations " +
                        "you configure. Nothing is uploaded to the developer, there is no analytics " +
                        "and no account. Your mail password is encrypted with a key held in this " +
                        "device's hardware keystore, and is excluded from cloud backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.size(Spacing.sm))
            PenduCoderPromoCard(compact = true)
            PenduCoderFooter()
            Spacer(modifier = Modifier.size(Spacing.xxl))
        }
    }
}
