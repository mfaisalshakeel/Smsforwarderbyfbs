package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.example.ui.components.AppSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.entity.ForwardingRuleEntity
import com.example.data.preferences.ForwarderSettings
import com.example.ui.components.GoogleAccountsDialog
import com.example.ui.components.PenduCoderFooter
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
import com.example.util.GoogleAccountHelper

@Composable
fun RulesScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rules by viewModel.rulesState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()

    val warningBg = if (isDark) WarningContainerDark else WarningContainerLight
    val warningText = if (isDark) WarningColorDark else WarningColorLight

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<ForwardingRuleEntity?>(null) }
    var ruleToDelete by remember { mutableStateOf<ForwardingRuleEntity?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))

                // Account unlinked warning banner
                if (!settings.isSenderAccountConfigured) {
                    val alertBg = if (isDark) Color(0xFF451A03) else Color(0xFFFEF3C7)
                    val alertBorder = if (isDark) Color(0xFFB45309) else Color(0xFFF59E0B)
                    val alertTitle = if (isDark) Color(0xFFFDE68A) else Color(0xFF78350F)
                    val alertBody = if (isDark) Color(0xFFFDE68A).copy(alpha = 0.85f) else Color(0xFF92400E)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = alertBg),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, alertBorder.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_warning_banner")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = alertTitle,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Google Account Not Linked",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = alertTitle
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "To forward SMS and notifications to email, link your Google Account in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = alertBody
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onNavigateToSettings,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("link_account_banner_button")
                            ) {
                                Text("Link Google Account Now")
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Rules Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Forwarding Filters (${rules.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap + to add filter",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (rules.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.size(60.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "No Forwarding Filters Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Set up your first filter to forward incoming SMS or notifications to your email address, filter by sender, keywords, or SIM card.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showCreateDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("create_first_rule_button")
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create First Filter")
                            }
                        }
                    }
                }
            } else {
                items(rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onToggle = { isEnabled ->
                            viewModel.toggleRule(rule.id, isEnabled)
                        },
                        onEdit = { editingRule = rule },
                        onDelete = { ruleToDelete = rule }
                    )
                }
            }

            item {
                PenduCoderFooter()
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Floating Action Button to add rule
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_rule_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Filter")
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Filter", fontWeight = FontWeight.Bold)
            }
        }
    }

    // Create Rule Dialog
    if (showCreateDialog) {
        RuleEditorDialog(
            initialRule = null,
            settings = settings,
            onNavigateToSettings = onNavigateToSettings,
            onDismiss = { showCreateDialog = false },
            onSave = { name, recipient, forwardSms, forwardNotifs, simSlot, senderType, senderVal, contentType, contentVal ->
                viewModel.createRule(
                    name = name,
                    recipientEmail = recipient,
                    forwardSms = forwardSms,
                    forwardNotifications = forwardNotifs,
                    simSlot = simSlot,
                    senderFilterType = senderType,
                    senderFilterValue = senderVal,
                    contentFilterType = contentType,
                    contentFilterValue = contentVal
                )
                showCreateDialog = false
            }
        )
    }

    // Edit Rule Dialog
    editingRule?.let { rule ->
        RuleEditorDialog(
            initialRule = rule,
            settings = settings,
            onNavigateToSettings = onNavigateToSettings,
            onDismiss = { editingRule = null },
            onSave = { name, recipient, forwardSms, forwardNotifs, simSlot, senderType, senderVal, contentType, contentVal ->
                viewModel.updateRule(
                    rule.copy(
                        name = name,
                        recipientEmail = recipient,
                        forwardSms = forwardSms,
                        forwardNotifications = forwardNotifs,
                        simSlot = simSlot,
                        senderFilterType = senderType,
                        senderFilterValue = senderVal,
                        contentFilterType = contentType,
                        contentFilterValue = contentVal
                    )
                )
                editingRule = null
            }
        )
    }

    // Delete Confirmation Dialog
    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("Delete Filter Rule?") },
            text = { Text("Are you sure you want to delete '${rule.name}'? Forwarding to ${rule.recipientEmail} will stop.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRule(rule.id)
                        ruleToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (isDark) ErrorColorDark else ErrorColorLight
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleCard(
    rule: ForwardingRuleEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val errorColor = if (isDark) ErrorColorDark else ErrorColorLight

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rule_card_${rule.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = rule.recipientEmail,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                AppSwitch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("rule_toggle_${rule.id}")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Badges for active sources and filters
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (rule.forwardSms) {
                    SourceBadge(label = "SMS", icon = Icons.Default.PhoneAndroid, color = MaterialTheme.colorScheme.primary)
                }
                if (rule.forwardNotifications) {
                    SourceBadge(label = "Notifications", icon = Icons.Default.Notifications, color = MaterialTheme.colorScheme.secondary)
                }

                val simText = when (rule.simSlot) {
                    1 -> "SIM 1 Only"
                    2 -> "SIM 2 Only"
                    else -> "All SIMs"
                }
                SourceBadge(label = simText, icon = Icons.Default.SimCard, color = MaterialTheme.colorScheme.tertiary)

                if (rule.senderFilterType != "ANY" && rule.senderFilterValue.isNotBlank()) {
                    SourceBadge(label = "Sender: ${rule.senderFilterValue}", icon = Icons.Default.FilterList, color = MaterialTheme.colorScheme.outline)
                }

                if (rule.contentFilterType != "ANY" && rule.contentFilterValue.isNotBlank()) {
                    SourceBadge(label = "Keyword: ${rule.contentFilterValue}", icon = Icons.Default.FilterList, color = MaterialTheme.colorScheme.outline)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Filter",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Filter",
                        tint = errorColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceBadge(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditorDialog(
    initialRule: ForwardingRuleEntity?,
    settings: ForwarderSettings,
    onNavigateToSettings: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        recipient: String,
        forwardSms: Boolean,
        forwardNotifs: Boolean,
        simSlot: Int,
        senderType: String,
        senderVal: String,
        contentType: String,
        contentVal: String
    ) -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    var step by remember { mutableIntStateOf(1) }

    var ruleName by remember { mutableStateOf(initialRule?.name ?: "") }
    var recipientEmail by remember { mutableStateOf(initialRule?.recipientEmail ?: "") }

    var forwardSms by remember { mutableStateOf(initialRule?.forwardSms ?: true) }
    var forwardNotifications by remember { mutableStateOf(initialRule?.forwardNotifications ?: false) }
    var simSlot by remember { mutableIntStateOf(initialRule?.simSlot ?: 0) }

    var senderFilterType by remember { mutableStateOf(initialRule?.senderFilterType ?: "ANY") }
    var senderFilterValue by remember { mutableStateOf(initialRule?.senderFilterValue ?: "") }

    var contentFilterType by remember { mutableStateOf(initialRule?.contentFilterType ?: "ANY") }
    var contentFilterValue by remember { mutableStateOf(initialRule?.contentFilterValue ?: "") }

    var showGoogleAccountPicker by remember { mutableStateOf(false) }
    var detectedAccounts by remember { mutableStateOf<List<String>>(emptyList()) }

    val warningBg = if (isDark) WarningContainerDark else WarningContainerLight
    val warningText = if (isDark) WarningColorDark else WarningColorLight

    val systemAccountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val accountName = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrBlank()) {
                recipientEmail = accountName
                Toast.makeText(context, "Recipient set to $accountName", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialRule == null) "Create Forwarding Filter" else "Edit Filter",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Step $step / 2",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (step == 1) {
                    // STEP 1: Recipient Email
                    Text(
                        text = "1. Recipient Email Address",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Enter where forwarded messages should be sent:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = recipientEmail,
                        onValueChange = { recipientEmail = it },
                        label = { Text("Recipient Email") },
                        placeholder = { Text("e.g. user@gmail.com") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rule_recipient_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Select from Phone Google Accounts
                    OutlinedButton(
                        onClick = {
                            val accounts = GoogleAccountHelper.getDeviceGoogleAccounts(context)
                            if (accounts.isNotEmpty()) {
                                detectedAccounts = accounts
                                showGoogleAccountPicker = true
                            } else {
                                try {
                                    val intent = GoogleAccountHelper.createGoogleAccountPickerIntent()
                                    systemAccountPickerLauncher.launch(intent)
                                } catch (e: Exception) {
                                    showGoogleAccountPicker = true
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google_logo),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Recipient from Phone's Google Accounts", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = ruleName,
                        onValueChange = { ruleName = it },
                        label = { Text("Filter Name (Optional)") },
                        placeholder = { Text("e.g. Work Email, Bank Alerts") },
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "2. Message Sources",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = forwardSms,
                            onCheckedChange = { forwardSms = it },
                            modifier = Modifier.testTag("rule_checkbox_sms")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Forward Incoming SMS Messages", style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = forwardNotifications,
                            onCheckedChange = { forwardNotifications = it },
                            modifier = Modifier.testTag("rule_checkbox_notifications")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Forward App Notifications", style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "3. Dual SIM Filter",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = simSlot == 0,
                            onClick = { simSlot = 0 },
                            label = { Text("All SIMs") }
                        )
                        FilterChip(
                            selected = simSlot == 1,
                            onClick = { simSlot = 1 },
                            label = { Text("SIM 1 Only") }
                        )
                        FilterChip(
                            selected = simSlot == 2,
                            onClick = { simSlot = 2 },
                            label = { Text("SIM 2 Only") }
                        )
                    }
                } else {
                    // STEP 2: Filters (Sender & Text)
                    Text(
                        text = "Sender Filter",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Choose which senders or apps should trigger this rule:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = senderFilterType == "ANY",
                            onClick = { senderFilterType = "ANY" },
                            label = { Text("Any Sender") }
                        )
                        FilterChip(
                            selected = senderFilterType == "CONTAINS",
                            onClick = { senderFilterType = "CONTAINS" },
                            label = { Text("Specific Number/Name") }
                        )
                    }

                    if (senderFilterType != "ANY") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = senderFilterValue,
                            onValueChange = { senderFilterValue = it },
                            label = { Text("Sender Numbers or App Names") },
                            placeholder = { Text("+1234567890, BANK_ALERT, WhatsApp") },
                            supportingText = { Text("Separate multiple with commas") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("rule_sender_filter_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Message Content / Keyword Filter",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Only forward messages containing specific text (e.g. OTP, security code):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = contentFilterType == "ANY",
                            onClick = { contentFilterType = "ANY" },
                            label = { Text("All Messages") }
                        )
                        FilterChip(
                            selected = contentFilterType == "CONTAINS",
                            onClick = { contentFilterType = "CONTAINS" },
                            label = { Text("Contains Keywords") }
                        )
                    }

                    if (contentFilterType != "ANY") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = contentFilterValue,
                            onValueChange = { contentFilterValue = it },
                            label = { Text("Keywords to Match") },
                            placeholder = { Text("OTP, verification, code, urgent") },
                            supportingText = { Text("Separate multiple keywords with commas") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("rule_content_filter_input")
                        )
                    }

                    // Notice if sender Google Account is not configured yet
                    if (!settings.isSenderAccountConfigured) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            color = warningBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, warningText.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = warningText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Google Account linking needed",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "After creating this rule, remember to link your Google account in Settings so emails can be sent.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Navigation buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step == 1) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { step = 2 },
                            enabled = recipientEmail.isNotBlank() && (forwardSms || forwardNotifications),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("rule_wizard_next_button")
                        ) {
                            Text("Next: Filters")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        TextButton(onClick = { step = 1 }) {
                            Text("Back")
                        }
                        Button(
                            onClick = {
                                val name = if (ruleName.isNotBlank()) ruleName else "Forward to $recipientEmail"
                                onSave(
                                    name,
                                    recipientEmail,
                                    forwardSms,
                                    forwardNotifications,
                                    simSlot,
                                    senderFilterType,
                                    senderFilterValue,
                                    contentFilterType,
                                    contentFilterValue
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("rule_wizard_save_button")
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Filter")
                        }
                    }
                }
            }
        }
    }

    if (showGoogleAccountPicker) {
        GoogleAccountsDialog(
            accounts = detectedAccounts,
            onSelectAccount = { selected ->
                showGoogleAccountPicker = false
                recipientEmail = selected
            },
            onLaunchSystemPicker = {
                showGoogleAccountPicker = false
                try {
                    val intent = GoogleAccountHelper.createGoogleAccountPickerIntent()
                    systemAccountPickerLauncher.launch(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "System account picker not available", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showGoogleAccountPicker = false }
        )
    }
}
