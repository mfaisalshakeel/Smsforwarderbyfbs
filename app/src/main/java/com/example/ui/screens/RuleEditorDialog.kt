package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.DestinationType
import com.example.data.local.entity.ForwardingRuleEntity
import com.example.data.local.entity.MatchType
import com.example.forwarder.MessageTemplate
import com.example.ui.components.AppPickerDialog
import com.example.ui.components.FieldLabel
import com.example.ui.components.InfoBanner
import com.example.ui.components.SettingSwitchRow
import com.example.ui.components.StepIndicator
import com.example.ui.components.Tone
import com.example.ui.theme.Shapes
import com.example.ui.theme.Spacing

private const val TOTAL_STEPS = 3

/**
 * Three-step rule editor.
 *
 * Every field is held in [rememberSaveable], so rotating the device or switching to another app
 * mid-edit no longer wipes the form.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleEditorDialog(
    initialRule: ForwardingRuleEntity?,
    availableSimCount: Int,
    onDismiss: () -> Unit,
    onSave: (ForwardingRuleEntity) -> Unit
) {
    var step by rememberSaveable { mutableIntStateOf(1) }

    var name by rememberSaveable { mutableStateOf(initialRule?.name.orEmpty()) }
    var destinationType by rememberSaveable {
        mutableStateOf(initialRule?.destinationType ?: DestinationType.EMAIL)
    }
    var emailTargets by rememberSaveable { mutableStateOf(initialRule?.recipientEmail.orEmpty()) }
    var otherTarget by rememberSaveable { mutableStateOf(initialRule?.destinationTarget.orEmpty()) }
    var webhookFormat by rememberSaveable { mutableStateOf(initialRule?.webhookFormat ?: "JSON") }
    var webhookHeaders by rememberSaveable { mutableStateOf(initialRule?.webhookHeaders.orEmpty()) }
    var telegramToken by rememberSaveable { mutableStateOf(initialRule?.telegramBotToken.orEmpty()) }

    var forwardSms by rememberSaveable { mutableStateOf(initialRule?.forwardSms ?: true) }
    var forwardNotifications by rememberSaveable {
        mutableStateOf(initialRule?.forwardNotifications ?: false)
    }
    var appPackages by rememberSaveable { mutableStateOf(initialRule?.appPackages.orEmpty()) }
    var simSlot by rememberSaveable { mutableIntStateOf(initialRule?.simSlot ?: 0) }

    var senderFilterType by rememberSaveable {
        mutableStateOf(initialRule?.senderFilterType ?: MatchType.ANY)
    }
    var senderFilterValue by rememberSaveable { mutableStateOf(initialRule?.senderFilterValue.orEmpty()) }
    var senderExclude by rememberSaveable { mutableStateOf(initialRule?.senderExcludeValue.orEmpty()) }
    var contentFilterType by rememberSaveable {
        mutableStateOf(initialRule?.contentFilterType ?: MatchType.ANY)
    }
    var contentFilterValue by rememberSaveable { mutableStateOf(initialRule?.contentFilterValue.orEmpty()) }
    var contentExclude by rememberSaveable { mutableStateOf(initialRule?.contentExcludeValue.orEmpty()) }

    var scheduleEnabled by rememberSaveable { mutableStateOf(initialRule?.scheduleEnabled ?: false) }
    var scheduleStart by rememberSaveable { mutableIntStateOf(initialRule?.scheduleStartMinute ?: 0) }
    var scheduleEnd by rememberSaveable { mutableIntStateOf(initialRule?.scheduleEndMinute ?: 1439) }
    var scheduleDays by rememberSaveable {
        mutableStateOf(initialRule?.scheduleDays ?: "1,2,3,4,5,6,7")
    }

    var useCustomTemplate by rememberSaveable { mutableStateOf(initialRule?.useCustomTemplate ?: false) }
    var subjectTemplate by rememberSaveable { mutableStateOf(initialRule?.subjectTemplate.orEmpty()) }
    var bodyTemplate by rememberSaveable { mutableStateOf(initialRule?.bodyTemplate.orEmpty()) }

    var showAppPicker by rememberSaveable { mutableStateOf(false) }
    var editingStartTime by rememberSaveable { mutableStateOf(false) }
    var editingEndTime by rememberSaveable { mutableStateOf(false) }

    val targetValue = if (destinationType == DestinationType.EMAIL) emailTargets else otherTarget
    val canContinueFromStep1 = targetValue.isNotBlank() &&
        (destinationType != DestinationType.TELEGRAM || telegramToken.isNotBlank())
    val canContinueFromStep2 = forwardSms || forwardNotifications

    Dialog(
        onDismissRequest = onDismiss,
        // Without this the dialog is stuck at the narrow platform default and the wizard
        // buttons fall below the fold on small screens.
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = Shapes.dialog,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = Spacing.xl)
                .imePadding()
        ) {
            Column(modifier = Modifier.padding(Spacing.xl)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (initialRule == null) "New rule" else "Edit rule",
                        style = MaterialTheme.typography.titleLarge
                    )
                    StepIndicator(current = step, total = TOTAL_STEPS)
                }

                Spacer(modifier = Modifier.size(Spacing.lg))

                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (step) {
                        1 -> {
                            FieldLabel(
                                text = "Where should messages go?",
                                helper = "Pick a destination and enter the address."
                            )
                            Spacer(modifier = Modifier.size(Spacing.sm))

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                DestinationChip("Email", DestinationType.EMAIL, destinationType) {
                                    destinationType = it
                                }
                                DestinationChip("SMS", DestinationType.SMS, destinationType) {
                                    destinationType = it
                                }
                                DestinationChip("Telegram", DestinationType.TELEGRAM, destinationType) {
                                    destinationType = it
                                }
                                DestinationChip("Webhook", DestinationType.WEBHOOK, destinationType) {
                                    destinationType = it
                                }
                            }

                            Spacer(modifier = Modifier.size(Spacing.md))

                            when (destinationType) {
                                DestinationType.EMAIL -> OutlinedTextField(
                                    value = emailTargets,
                                    onValueChange = { emailTargets = it },
                                    label = { Text("Recipient email") },
                                    placeholder = { Text("you@gmail.com, team@work.com") },
                                    supportingText = { Text("Separate several addresses with commas") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    shape = Shapes.field,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("rule_recipient_input")
                                )

                                DestinationType.SMS -> OutlinedTextField(
                                    value = otherTarget,
                                    onValueChange = { otherTarget = it },
                                    label = { Text("Phone number") },
                                    placeholder = { Text("+923001234567") },
                                    supportingText = { Text("Include the country code") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    shape = Shapes.field,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                DestinationType.TELEGRAM -> Column {
                                    OutlinedTextField(
                                        value = telegramToken,
                                        onValueChange = { telegramToken = it },
                                        label = { Text("Bot token") },
                                        supportingText = { Text("Create a bot with @BotFather to get this") },
                                        shape = Shapes.field,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.size(Spacing.sm))
                                    OutlinedTextField(
                                        value = otherTarget,
                                        onValueChange = { otherTarget = it },
                                        label = { Text("Chat ID") },
                                        supportingText = { Text("Message your bot, then ask @userinfobot for your ID") },
                                        shape = Shapes.field,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                else -> Column {
                                    OutlinedTextField(
                                        value = otherTarget,
                                        onValueChange = { otherTarget = it },
                                        label = { Text("Webhook URL") },
                                        placeholder = { Text("https://example.com/hook") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                        shape = Shapes.field,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.size(Spacing.sm))
                                    FieldLabel(text = "Payload format")
                                    Spacer(modifier = Modifier.size(Spacing.xs))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        listOf("JSON", "DISCORD", "SLACK").forEach { format ->
                                            FilterChip(
                                                selected = webhookFormat == format,
                                                onClick = { webhookFormat = format },
                                                label = { Text(format.lowercase().replaceFirstChar { it.uppercase() }) }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.size(Spacing.sm))
                                    OutlinedTextField(
                                        value = webhookHeaders,
                                        onValueChange = { webhookHeaders = it },
                                        label = { Text("Custom headers (optional)") },
                                        placeholder = { Text("Authorization: Bearer abc123") },
                                        supportingText = { Text("One header per line") },
                                        shape = Shapes.field,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.size(Spacing.md))

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Rule name (optional)") },
                                placeholder = { Text("Bank alerts, Work email…") },
                                singleLine = true,
                                shape = Shapes.field,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        2 -> {
                            FieldLabel(
                                text = "What should this rule watch?",
                                helper = "Choose at least one source."
                            )
                            Spacer(modifier = Modifier.size(Spacing.sm))

                            SettingSwitchRow(
                                title = "Incoming text messages",
                                subtitle = "Forward SMS as they arrive",
                                checked = forwardSms,
                                onCheckedChange = { forwardSms = it }
                            )
                            SettingSwitchRow(
                                title = "App notifications",
                                subtitle = "Forward notifications from the apps you choose",
                                checked = forwardNotifications,
                                onCheckedChange = { forwardNotifications = it }
                            )

                            if (forwardNotifications) {
                                Spacer(modifier = Modifier.size(Spacing.sm))
                                val selectedCount = appPackages.split(",").filter { it.isNotBlank() }.size
                                OutlinedButton(
                                    onClick = { showAppPicker = true },
                                    shape = Shapes.button,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("choose_apps_button")
                                ) {
                                    Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(Spacing.sm))
                                    Text(
                                        if (selectedCount == 0) {
                                            "Choose apps (currently: all apps)"
                                        } else {
                                            "Choose apps ($selectedCount selected)"
                                        }
                                    )
                                }
                            }

                            if (forwardSms && availableSimCount > 1) {
                                Spacer(modifier = Modifier.size(Spacing.lg))
                                FieldLabel(text = "Which SIM?")
                                Spacer(modifier = Modifier.size(Spacing.xs))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    FilterChip(
                                        selected = simSlot == 0,
                                        onClick = { simSlot = 0 },
                                        label = { Text("Any SIM") }
                                    )
                                    (1..availableSimCount).forEach { slot ->
                                        FilterChip(
                                            selected = simSlot == slot,
                                            onClick = { simSlot = slot },
                                            label = { Text("SIM $slot") }
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            FieldLabel(
                                text = "Narrow it down",
                                helper = "Leave everything as-is to forward every message."
                            )
                            Spacer(modifier = Modifier.size(Spacing.md))

                            MatchTypeSelector(
                                label = "Sender",
                                types = listOf(
                                    MatchType.ANY to "Any",
                                    MatchType.CONTAINS to "Contains",
                                    MatchType.EXACT to "Exactly",
                                    MatchType.STARTS_WITH to "Starts with",
                                    MatchType.REGEX to "Pattern"
                                ),
                                selected = senderFilterType,
                                onSelect = { senderFilterType = it }
                            )
                            if (senderFilterType != MatchType.ANY) {
                                Spacer(modifier = Modifier.size(Spacing.sm))
                                OutlinedTextField(
                                    value = senderFilterValue,
                                    onValueChange = { senderFilterValue = it },
                                    label = { Text("Only these senders") },
                                    placeholder = { Text("HBL, +923001234567") },
                                    supportingText = { Text("Separate several with commas") },
                                    shape = Shapes.field,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("rule_sender_filter_input")
                                )
                            }
                            Spacer(modifier = Modifier.size(Spacing.sm))
                            OutlinedTextField(
                                value = senderExclude,
                                onValueChange = { senderExclude = it },
                                label = { Text("Never from these senders") },
                                placeholder = { Text("spam, promo") },
                                shape = Shapes.field,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.size(Spacing.lg))

                            MatchTypeSelector(
                                label = "Message text",
                                types = listOf(
                                    MatchType.ANY to "Any",
                                    MatchType.CONTAINS to "Contains",
                                    MatchType.REGEX to "Pattern"
                                ),
                                selected = contentFilterType,
                                onSelect = { contentFilterType = it }
                            )
                            if (contentFilterType != MatchType.ANY) {
                                Spacer(modifier = Modifier.size(Spacing.sm))
                                OutlinedTextField(
                                    value = contentFilterValue,
                                    onValueChange = { contentFilterValue = it },
                                    label = { Text("Must contain") },
                                    placeholder = { Text("OTP, code, verification") },
                                    shape = Shapes.field,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("rule_content_filter_input")
                                )
                            }
                            Spacer(modifier = Modifier.size(Spacing.sm))
                            OutlinedTextField(
                                value = contentExclude,
                                onValueChange = { contentExclude = it },
                                label = { Text("Must not contain") },
                                placeholder = { Text("advertisement, offer") },
                                shape = Shapes.field,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.size(Spacing.lg))

                            SettingSwitchRow(
                                title = "Only at certain times",
                                subtitle = "Forward only inside a time window",
                                checked = scheduleEnabled,
                                onCheckedChange = { scheduleEnabled = it }
                            )
                            if (scheduleEnabled) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    OutlinedButton(
                                        onClick = { editingStartTime = true },
                                        shape = Shapes.button,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("From ${formatMinute(scheduleStart)}")
                                    }
                                    OutlinedButton(
                                        onClick = { editingEndTime = true },
                                        shape = Shapes.button,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("To ${formatMinute(scheduleEnd)}")
                                    }
                                }
                                Spacer(modifier = Modifier.size(Spacing.sm))
                                DayOfWeekSelector(
                                    selectedDays = scheduleDays,
                                    onChange = { scheduleDays = it }
                                )
                            }

                            Spacer(modifier = Modifier.size(Spacing.lg))

                            SettingSwitchRow(
                                title = "Custom message format",
                                subtitle = "Write your own subject and body",
                                checked = useCustomTemplate,
                                onCheckedChange = { useCustomTemplate = it }
                            )
                            if (useCustomTemplate) {
                                Spacer(modifier = Modifier.size(Spacing.sm))
                                OutlinedTextField(
                                    value = subjectTemplate,
                                    onValueChange = { subjectTemplate = it },
                                    label = { Text("Subject") },
                                    placeholder = { Text(MessageTemplate.DEFAULT_SMS_SUBJECT) },
                                    shape = Shapes.field,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.size(Spacing.sm))
                                OutlinedTextField(
                                    value = bodyTemplate,
                                    onValueChange = { bodyTemplate = it },
                                    label = { Text("Body") },
                                    placeholder = { Text(MessageTemplate.DEFAULT_SHORT_BODY) },
                                    minLines = 3,
                                    shape = Shapes.field,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.size(Spacing.sm))
                                InfoBanner(
                                    title = "Available placeholders",
                                    message = MessageTemplate.PLACEHOLDERS.joinToString("  "),
                                    tone = Tone.Info
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.size(Spacing.lg))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { if (step == 1) onDismiss() else step-- }) {
                        Text(if (step == 1) "Cancel" else "Back")
                    }

                    if (step < TOTAL_STEPS) {
                        Button(
                            onClick = { step++ },
                            enabled = if (step == 1) canContinueFromStep1 else canContinueFromStep2,
                            shape = Shapes.button,
                            modifier = Modifier.testTag("rule_wizard_next_button")
                        ) {
                            Text("Next")
                            Spacer(modifier = Modifier.size(Spacing.xs))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                val resolvedName = name.ifBlank {
                                    "Forward to ${targetValue.split(",").first().trim()}"
                                }
                                onSave(
                                    ForwardingRuleEntity(
                                        id = initialRule?.id ?: 0L,
                                        name = resolvedName,
                                        isEnabled = initialRule?.isEnabled ?: true,
                                        destinationType = destinationType,
                                        recipientEmail = if (destinationType == DestinationType.EMAIL) {
                                            emailTargets.trim()
                                        } else {
                                            ""
                                        },
                                        destinationTarget = if (destinationType == DestinationType.EMAIL) {
                                            ""
                                        } else {
                                            otherTarget.trim()
                                        },
                                        webhookFormat = webhookFormat,
                                        webhookHeaders = webhookHeaders.trim(),
                                        telegramBotToken = telegramToken.trim(),
                                        forwardSms = forwardSms,
                                        forwardNotifications = forwardNotifications,
                                        appPackages = appPackages,
                                        simSlot = simSlot,
                                        senderFilterType = senderFilterType,
                                        senderFilterValue = senderFilterValue.trim(),
                                        senderExcludeValue = senderExclude.trim(),
                                        contentFilterType = contentFilterType,
                                        contentFilterValue = contentFilterValue.trim(),
                                        contentExcludeValue = contentExclude.trim(),
                                        scheduleEnabled = scheduleEnabled,
                                        scheduleStartMinute = scheduleStart,
                                        scheduleEndMinute = scheduleEnd,
                                        scheduleDays = scheduleDays,
                                        useCustomTemplate = useCustomTemplate,
                                        subjectTemplate = subjectTemplate.trim(),
                                        bodyTemplate = bodyTemplate.trim(),
                                        createdAt = initialRule?.createdAt ?: System.currentTimeMillis()
                                    )
                                )
                            },
                            shape = Shapes.button,
                            modifier = Modifier.testTag("rule_wizard_save_button")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(Spacing.xs))
                            Text("Save rule")
                        }
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            initiallySelected = appPackages.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            onConfirm = {
                appPackages = it.joinToString(",")
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false }
        )
    }

    if (editingStartTime) {
        TimePickerDialog(
            initialMinuteOfDay = scheduleStart,
            title = "Start time",
            onConfirm = {
                scheduleStart = it
                editingStartTime = false
            },
            onDismiss = { editingStartTime = false }
        )
    }
    if (editingEndTime) {
        TimePickerDialog(
            initialMinuteOfDay = scheduleEnd,
            title = "End time",
            onConfirm = {
                scheduleEnd = it
                editingEndTime = false
            },
            onDismiss = { editingEndTime = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchTypeSelector(
    label: String,
    types: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column {
        FieldLabel(text = label)
        Spacer(modifier = Modifier.size(Spacing.xs))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            types.forEach { (value, display) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(display) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayOfWeekSelector(
    selectedDays: String,
    onChange: (String) -> Unit
) {
    val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val selected = selectedDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        labels.forEachIndexed { index, label ->
            val day = index + 1
            FilterChip(
                selected = selected.contains(day),
                onClick = {
                    val updated = if (selected.contains(day)) selected - day else selected + day
                    // Never allow an empty selection; a rule with no days would never fire.
                    onChange(updated.ifEmpty { setOf(day) }.sorted().joinToString(","))
                },
                label = { Text(label) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinuteOfDay: Int,
    title: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Shapes.dialog,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DestinationChip(
    label: String,
    value: String,
    selected: String,
    onSelect: (String) -> Unit
) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(label) }
    )
}

internal fun formatMinute(minuteOfDay: Int): String {
    val hour = (minuteOfDay / 60).coerceIn(0, 23)
    val minute = (minuteOfDay % 60).coerceIn(0, 59)
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute)
}
