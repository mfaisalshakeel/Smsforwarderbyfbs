package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.LogStatus
import com.example.data.local.entity.SmsLogEntity
import com.example.ui.components.AppCard
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.ContentContainer
import com.example.ui.components.EmptyState
import com.example.ui.components.LogDetailDialog
import com.example.ui.components.LogItemCard
import com.example.ui.theme.Shapes
import com.example.ui.theme.Spacing
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogsScreen(
    viewModel: MainViewModel,
    onExportCsv: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val logs by viewModel.logsState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val status by viewModel.filterStatus.collectAsStateWithLifecycle()
    val source by viewModel.filterSource.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val isTransferring by viewModel.isTransferring.collectAsStateWithLifecycle()

    var selectedLog by remember { mutableStateOf<SmsLogEntity?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val hasFilters = query.isNotBlank() ||
        status != MainViewModel.FILTER_ALL ||
        source != MainViewModel.FILTER_ALL

    ContentContainer(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = Spacing.gutter)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text("Search sender, message or rule") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = Shapes.field,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("logs_search_input")
                )

                Spacer(modifier = Modifier.size(Spacing.sm))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    StatusFilterChip("All", MainViewModel.FILTER_ALL, status) {
                        viewModel.filterStatus.value = it
                    }
                    StatusFilterChip("Delivered", LogStatus.SUCCESS, status) {
                        viewModel.filterStatus.value = it
                    }
                    StatusFilterChip("Queued", LogStatus.PENDING, status) {
                        viewModel.filterStatus.value = it
                    }
                    StatusFilterChip("Failed", LogStatus.FAILED, status) {
                        viewModel.filterStatus.value = it
                    }
                    StatusFilterChip("Skipped", LogStatus.SKIPPED, status) {
                        viewModel.filterStatus.value = it
                    }
                }

                Spacer(modifier = Modifier.size(Spacing.xs))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.weight(1f)
                    ) {
                        StatusFilterChip("Any source", MainViewModel.FILTER_ALL, source) {
                            viewModel.filterSource.value = it
                        }
                        StatusFilterChip("SMS", "SMS", source) {
                            viewModel.filterSource.value = it
                        }
                        StatusFilterChip("Notifications", "NOTIFICATION", source) {
                            viewModel.filterSource.value = it
                        }
                    }
                    IconButton(
                        onClick = { viewModel.buildLogsCsv(onExportCsv) },
                        enabled = !isTransferring && logs.isNotEmpty()
                    ) {
                        if (isTransferring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Download, contentDescription = "Export history as CSV")
                        }
                    }
                    IconButton(onClick = { confirmClear = true }, enabled = logs.isNotEmpty()) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear history",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.size(Spacing.sm))
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (logs.isEmpty()) {
                    Column(modifier = Modifier.padding(horizontal = Spacing.gutter)) {
                        AppCard {
                            EmptyState(
                                icon = Icons.Default.History,
                                title = if (hasFilters) "Nothing matches" else "No history yet",
                                message = if (hasFilters) {
                                    "Try clearing the filters above."
                                } else {
                                    "Every forwarded message will be listed here with its status."
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Spacing.gutter),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        contentPadding = PaddingValues(bottom = Spacing.xxl)
                    ) {
                        items(logs, key = { it.id }) { log ->
                            LogItemCard(log = log, onClick = { selectedLog = log })
                        }
                        item {
                            Spacer(modifier = Modifier.size(Spacing.lg))
                            Text(
                                text = "Showing the most recent ${logs.size} entries",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    selectedLog?.let { log ->
        LogDetailDialog(
            log = log,
            isRetrying = isBusy,
            onDismiss = { selectedLog = null },
            onRetry = { viewModel.retryLog(log.id) { selectedLog = null } },
            onDelete = {
                viewModel.deleteLog(log.id)
                selectedLog = null
            }
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear all history?",
            message = "Every entry will be removed. Your rules and settings are not affected.",
            confirmLabel = "Clear",
            onConfirm = {
                viewModel.clearAllLogs()
                confirmClear = false
            },
            onDismiss = { confirmClear = false }
        )
    }
}

@Composable
private fun StatusFilterChip(
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
