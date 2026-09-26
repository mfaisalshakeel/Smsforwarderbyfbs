package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AppCard
import com.example.ui.components.ContentContainer
import com.example.ui.components.EmptyState
import com.example.ui.components.FieldLabel
import com.example.ui.components.InfoBanner
import com.example.ui.components.LoadingRow
import com.example.ui.components.LogItemCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.Tone
import com.example.ui.theme.Shapes
import com.example.ui.theme.Spacing
import com.example.ui.viewmodel.MainViewModel

/**
 * Runs a made-up message through the real rule pipeline so a user can prove their setup works
 * without waiting for a bank to text them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimulatorScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val results by viewModel.lastSimulatedLogs.collectAsStateWithLifecycle()
    val summary by viewModel.simulationSummary.collectAsStateWithLifecycle()
    val availableSims by viewModel.availableSims.collectAsStateWithLifecycle()

    var mode by rememberSaveable { mutableStateOf(MODE_SMS) }
    var sender by rememberSaveable { mutableStateOf("HBL-Bank") }
    var body by rememberSaveable { mutableStateOf("Your one-time code is 483921. Do not share it with anyone.") }
    var appName by rememberSaveable { mutableStateOf("WhatsApp") }
    var packageName by rememberSaveable { mutableStateOf("com.whatsapp") }
    var title by rememberSaveable { mutableStateOf("Ahmed") }
    var simSlot by rememberSaveable { mutableIntStateOf(0) }

    ContentContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            InfoBanner(
                title = "This really sends",
                message = "The test message goes through your live rules, so it will be delivered " +
                    "to the destinations you set up and will appear in your history.",
                tone = Tone.Info,
                icon = Icons.Default.Science
            )

            AppCard {
                FieldLabel(text = "What should we pretend arrived?")
                Spacer(modifier = Modifier.size(Spacing.sm))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    FilterChip(
                        selected = mode == MODE_SMS,
                        onClick = { mode = MODE_SMS },
                        label = { Text("Text message") }
                    )
                    FilterChip(
                        selected = mode == MODE_MMS,
                        onClick = { mode = MODE_MMS },
                        label = { Text("Picture message") }
                    )
                    FilterChip(
                        selected = mode == MODE_CALL,
                        onClick = { mode = MODE_CALL },
                        label = { Text("Missed call") }
                    )
                    FilterChip(
                        selected = mode == MODE_NOTIFICATION,
                        onClick = { mode = MODE_NOTIFICATION },
                        label = { Text("App notification") }
                    )
                }

                Spacer(modifier = Modifier.size(Spacing.md))

                if (mode == MODE_SMS || mode == MODE_MMS || mode == MODE_CALL) {
                    OutlinedTextField(
                        value = sender,
                        onValueChange = { sender = it },
                        label = { Text(if (mode == MODE_CALL) "Caller" else "From") },
                        singleLine = true,
                        shape = Shapes.field,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("simulator_sender_input")
                    )
                    if (mode == MODE_SMS && availableSims.size > 1) {
                        Spacer(modifier = Modifier.size(Spacing.sm))
                        FieldLabel(text = "Arrived on")
                        Spacer(modifier = Modifier.size(Spacing.xs))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            FilterChip(
                                selected = simSlot == 0,
                                onClick = { simSlot = 0 },
                                label = { Text("Unknown SIM") }
                            )
                            availableSims.forEach { sim ->
                                FilterChip(
                                    selected = simSlot == sim.slot,
                                    onClick = { simSlot = sim.slot },
                                    label = { Text(sim.displayName) }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = appName,
                        onValueChange = { appName = it },
                        label = { Text("App name") },
                        singleLine = true,
                        shape = Shapes.field,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.size(Spacing.sm))
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        label = { Text("Package name") },
                        supportingText = { Text("Must match a package selected in your rule") },
                        singleLine = true,
                        shape = Shapes.field,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.size(Spacing.sm))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Notification title") },
                        singleLine = true,
                        shape = Shapes.field,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.size(Spacing.sm))

                if (mode != MODE_CALL) {
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Message text") },
                        minLines = 3,
                        shape = Shapes.field,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("simulator_body_input")
                    )
                }

                Spacer(modifier = Modifier.size(Spacing.md))

                if (isBusy) {
                    LoadingRow(text = "Running through your rules…")
                } else {
                    Button(
                        onClick = {
                            when (mode) {
                                MODE_SMS -> viewModel.simulateIncomingSms(sender, body, simSlot)
                                MODE_MMS -> viewModel.simulateIncomingMms(sender, body)
                                MODE_CALL -> viewModel.simulateMissedCall(sender)
                                else -> viewModel.simulateIncomingNotification(
                                    appName, packageName, title, body
                                )
                            }
                        },
                        enabled = if (mode == MODE_CALL) sender.isNotBlank() else body.isNotBlank(),
                        shape = Shapes.button,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("simulator_run_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(Spacing.xs))
                        Text("Run test")
                    }
                }
            }

            summary?.let {
                InfoBanner(
                    title = "Result",
                    message = it,
                    tone = if (it.startsWith("Not forwarded") || it.contains("No rule")) {
                        Tone.Warning
                    } else {
                        Tone.Success
                    }
                )
            }

            if (results.isNotEmpty()) {
                SectionHeader(title = "What happened")
                results.forEach { log ->
                    LogItemCard(log = log, onClick = {})
                }
            } else if (summary == null) {
                AppCard {
                    EmptyState(
                        icon = Icons.Default.Science,
                        title = "No test run yet",
                        message = "Fill in a message above and tap Run test to check your rules."
                    )
                }
            }

            Spacer(modifier = Modifier.size(Spacing.xxl))
        }
    }
}

private const val MODE_SMS = "SMS"
private const val MODE_MMS = "MMS"
private const val MODE_CALL = "CALL"
private const val MODE_NOTIFICATION = "NOTIFICATION"
