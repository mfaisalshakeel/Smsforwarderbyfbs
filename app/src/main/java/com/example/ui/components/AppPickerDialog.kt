package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.Shapes
import com.example.ui.theme.Spacing
import com.example.util.AppInfoHelper
import com.example.util.InstalledApp

/**
 * Lets the user pick which apps a notification rule listens to.
 *
 * This replaces a free-text box that asked people to type an app's display label from memory —
 * the reason notification rules almost never matched anything.
 */
@Composable
fun AppPickerDialog(
    initiallySelected: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showSystemApps by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val selected: SnapshotStateList<String> = remember(initiallySelected) {
        initiallySelected.toMutableStateList()
    }

    LaunchedEffect(showSystemApps) {
        isLoading = true
        apps = AppInfoHelper.loadInstalledApps(context, includeSystemApps = showSystemApps)
        isLoading = false
    }

    val visibleApps = remember(apps, query) {
        if (query.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        // Without this the dialog is pinned to the narrow platform default width.
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
                Text(
                    text = "Choose apps",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Only notifications from the apps you tick will be forwarded. " +
                        "Tick nothing to forward every app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.size(Spacing.lg))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = Shapes.field,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_picker_search")
                )

                Spacer(modifier = Modifier.size(Spacing.sm))

                SettingSwitchRow(
                    title = "Show system apps",
                    subtitle = "Includes Messages, Phone and other built-in apps",
                    checked = showSystemApps,
                    onCheckedChange = { showSystemApps = it }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Box(modifier = Modifier.heightIn(min = 180.dp, max = 400.dp)) {
                    when {
                        isLoading -> Box(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.xxl),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }

                        visibleApps.isEmpty() -> Box(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.xxl),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No apps match \"$query\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(visibleApps, key = { it.packageName }) { app ->
                                AppRow(
                                    app = app,
                                    checked = selected.contains(app.packageName),
                                    onToggle = { isChecked ->
                                        if (isChecked) {
                                            if (!selected.contains(app.packageName)) {
                                                selected.add(app.packageName)
                                            }
                                        } else {
                                            selected.remove(app.packageName)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.size(Spacing.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (selected.isEmpty()) {
                            "All apps"
                        } else {
                            "${selected.size} selected"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(modifier = Modifier.size(Spacing.sm))
                        Button(
                            onClick = { onConfirm(selected.toList()) },
                            shape = Shapes.button,
                            modifier = Modifier.testTag("app_picker_confirm")
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .clickable { onToggle(!checked) }
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val icon = app.icon
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.size(Spacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Checkbox(checked = checked, onCheckedChange = onToggle)
    }
}
