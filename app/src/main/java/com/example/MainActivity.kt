package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AboutPenduCoderDialog
import com.example.ui.components.AutostartHelpDialog
import com.example.ui.components.SetupStep
import com.example.ui.components.StatusDot
import com.example.ui.components.Tone
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LockScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.RulesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SimulatorScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Spacing
import com.example.util.BackupManager
import com.example.util.BiometricHelper
import com.example.util.HealthAction
import com.example.util.PowerHelper
import com.example.ui.viewmodel.MainViewModel

enum class AppTab(
    val labelRes: Int,
    val titleRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    DASHBOARD(R.string.nav_dashboard, R.string.title_dashboard, Icons.Default.Dashboard),
    RULES(R.string.nav_rules, R.string.title_rules, Icons.Default.FilterList),
    LOGS(R.string.nav_logs, R.string.title_logs, Icons.Default.History),
    SETTINGS(R.string.nav_settings, R.string.title_settings, Icons.Default.Settings),
    SIMULATOR(R.string.nav_simulator, R.string.title_simulator, Icons.Default.Science)
}

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settingsState.collectAsStateWithLifecycle()

            MyApplicationTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColorEnabled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MainApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val activity = context as? FragmentActivity

    // Saved across rotation; the tab used to reset to Home on every configuration change.
    var currentTabIndex by rememberSaveable { mutableIntStateOf(AppTab.DASHBOARD.ordinal) }
    val currentTab = AppTab.entries[currentTabIndex]
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }

    // Deliberately not saveable: leaving the app must re-lock it, and a process restart
    // must not come back already unlocked.
    var isUnlocked by remember { mutableStateOf(false) }
    var lockError by remember { mutableStateOf<String?>(null) }
    var showAutostartHelp by rememberSaveable { mutableStateOf(false) }

    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val permissions by viewModel.permissionsState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val pendingConsent by viewModel.pendingGoogleConsent.collectAsStateWithLifecycle()

    // ---------------------------------------------------------------- permissions

    fun refreshPermissions() {
        viewModel.updatePermissionsState(
            hasReceive = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED,
            hasSend = ContextCompat.checkSelfPermission(
                context, Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED,
            hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
            hasPhoneState = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED,
            hasCallLog = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        refreshPermissions()
        // When the user has denied twice, Android stops showing the dialog. Send them to the
        // app's settings page instead of letting the button appear to do nothing.
        val stillMissing = granted.any { !it.value }
        val canAskAgain = activity?.shouldShowRequestPermissionRationale(
            Manifest.permission.RECEIVE_SMS
        ) ?: true
        if (stillMissing && !canAskAgain) {
            runCatching { context.startActivity(PowerHelper.appSettingsIntent(context)) }
        }
    }

    val genericLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { refreshPermissions() }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { viewModel.clearGoogleConsent() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_START -> refreshPermissions()
                // Re-lock as soon as the app leaves the foreground.
                Lifecycle.Event.ON_STOP -> isUnlocked = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val keyguardLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            isUnlocked = true
            lockError = null
        }
    }

    fun requestUnlock() {
        val host = activity ?: return
        lockError = null
        BiometricHelper.authenticate(
            activity = host,
            title = "Unlock SMS Forwarder",
            subtitle = "Your message history is protected",
            onSuccess = {
                isUnlocked = true
                lockError = null
            },
            onFailed = { reason ->
                // Older devices cannot offer the PIN inside the prompt; fall back to the keyguard.
                val fallback = BiometricHelper.deviceCredentialIntent(
                    host,
                    "Unlock SMS Forwarder",
                    "Enter your device PIN, pattern or password"
                )
                if (fallback != null) {
                    runCatching { keyguardLauncher.launch(fallback) }
                        .onFailure { lockError = reason }
                } else {
                    lockError = reason
                }
            },
            onCancelled = { lockError = null }
        )
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it.text)
            viewModel.consumeMessage()
        }
    }

    // ---------------------------------------------------------------- file pickers

    var pendingExport by remember { mutableStateOf<String?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> writePendingExport(context, uri, pendingExport) { pendingExport = null } }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> writePendingExport(context, uri, pendingExport) { pendingExport = null } }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val json = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (json.isNullOrBlank()) {
                Toast.makeText(context, "Could not read that file", Toast.LENGTH_LONG).show()
            } else {
                viewModel.restoreBackup(json)
            }
        }
    }

    // ---------------------------------------------------------------- setup steps

    val setupSteps = buildSetupSteps(
        hasSmsPermission = permissions.hasReceiveSms,
        hasNotificationPermission = permissions.hasNotificationPost,
        hasNotificationAccess = permissions.hasNotificationAccess,
        isBatteryOptimised = permissions.isBatteryOptimised,
        hasAccount = settings.isSenderAccountConfigured,
        onRequestPermissions = { requestCorePermissions(permissionLauncher) },
        onOpenNotificationAccess = {
            runCatching { genericLauncher.launch(PowerHelper.notificationAccessIntent()) }
        },
        onFixBattery = {
            val intent = PowerHelper.buildExemptionIntent(context) ?: PowerHelper.batterySettingsIntent()
            runCatching { genericLauncher.launch(intent) }
                .onFailure { runCatching { genericLauncher.launch(PowerHelper.batterySettingsIntent()) } }
        },
        onOpenSetup = { currentTabIndex = AppTab.SETTINGS.ordinal }
    )

    fun handleHealthAction(action: HealthAction) {
        when (action) {
            HealthAction.ENABLE_ENGINE -> viewModel.toggleMasterSwitch(true)
            HealthAction.RESTART_ENGINE -> viewModel.restartEngine()
            HealthAction.GRANT_SMS_PERMISSION -> requestCorePermissions(permissionLauncher)
            HealthAction.GRANT_CALL_LOG ->
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG))
            HealthAction.GRANT_NOTIFICATION_ACCESS ->
                runCatching { genericLauncher.launch(PowerHelper.notificationAccessIntent()) }
            HealthAction.FIX_BATTERY -> {
                val intent = PowerHelper.buildExemptionIntent(context)
                    ?: PowerHelper.batterySettingsIntent()
                runCatching { genericLauncher.launch(intent) }
                    .onFailure { runCatching { genericLauncher.launch(PowerHelper.batterySettingsIntent()) } }
            }
            HealthAction.ENABLE_APP_NOTIFICATIONS ->
                runCatching { genericLauncher.launch(PowerHelper.appNotificationSettingsIntent(context)) }
            HealthAction.OPEN_SETUP -> currentTabIndex = AppTab.SETTINGS.ordinal
            HealthAction.CREATE_RULE -> currentTabIndex = AppTab.RULES.ordinal
            HealthAction.OPEN_AUTOSTART_HELP -> showAutostartHelp = true
        }
    }

    // ---------------------------------------------------------------- onboarding

    if (!settings.onboardingCompleted) {
        OnboardingScreen(
            setupSteps = setupSteps,
            onFinish = viewModel::completeOnboarding
        )
        return
    }

    if (settings.appLockEnabled && !isUnlocked) {
        // Offer the prompt straight away rather than making the user tap twice.
        LaunchedEffect(Unit) { requestUnlock() }
        LockScreen(onUnlock = { requestUnlock() }, errorMessage = lockError)
        return
    }

    // Back returns to Home rather than leaving the app from a deep tab.
    BackHandler(enabled = currentTab != AppTab.DASHBOARD) {
        currentTabIndex = AppTab.DASHBOARD.ordinal
    }

    pendingConsent?.let { intent ->
        LaunchedEffect(intent) {
            runCatching { consentLauncher.launch(intent) }
                .onFailure { viewModel.clearGoogleConsent() }
        }
    }

    val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val useRail = windowSizeClass != null &&
        windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    val statusTone = when {
        !settings.isForwarderEnabled -> Tone.Neutral
        !settings.isSenderAccountConfigured -> Tone.Warning
        else -> Tone.Success
    }
    val statusDescription = when {
        !settings.isForwarderEnabled -> stringResource(R.string.status_paused)
        !settings.isSenderAccountConfigured -> stringResource(R.string.status_setup_needed)
        else -> stringResource(R.string.status_active)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(tone = statusTone, description = statusDescription)
                        Spacer(modifier = Modifier.size(Spacing.sm))
                        Text(
                            text = stringResource(currentTab.titleRes),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    IconButton(
                        onClick = { showAboutDialog = true },
                        modifier = Modifier.testTag("about_penducoder_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About this app",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (!useRail) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTabIndex = tab.ordinal },
                            icon = {
                                Icon(tab.icon, contentDescription = stringResource(tab.labelRes))
                            },
                            label = {
                                Text(
                                    text = stringResource(tab.labelRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            alwaysShowLabel = true,
                            modifier = Modifier.testTag("nav_item_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (useRail) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    AppTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = currentTab == tab,
                            onClick = { currentTabIndex = tab.ordinal },
                            icon = {
                                Icon(tab.icon, contentDescription = stringResource(tab.labelRes))
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                            modifier = Modifier.testTag("rail_item_${tab.name.lowercase()}")
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "ScreenTransition"
                ) { tab ->
                    when (tab) {
                        AppTab.DASHBOARD -> DashboardScreen(
                            viewModel = viewModel,
                            setupSteps = setupSteps,
                            onHealthAction = { handleHealthAction(it) },
                            onNavigateToRules = { currentTabIndex = AppTab.RULES.ordinal },
                            onNavigateToLogs = { currentTabIndex = AppTab.LOGS.ordinal },
                            onNavigateToSettings = { currentTabIndex = AppTab.SETTINGS.ordinal },
                            onNavigateToSimulator = { currentTabIndex = AppTab.SIMULATOR.ordinal }
                        )

                        AppTab.RULES -> RulesScreen(
                            viewModel = viewModel,
                            onNavigateToSettings = { currentTabIndex = AppTab.SETTINGS.ordinal }
                        )

                        AppTab.LOGS -> LogsScreen(
                            viewModel = viewModel,
                            onExportCsv = { csv ->
                                pendingExport = csv
                                runCatching { createCsvLauncher.launch(BackupManager.suggestedCsvName()) }
                            }
                        )

                        AppTab.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            onOpenNotificationAccess = {
                                runCatching { genericLauncher.launch(PowerHelper.notificationAccessIntent()) }
                            },
                            onOpenBatterySettings = {
                                val intent = PowerHelper.buildExemptionIntent(context)
                                    ?: PowerHelper.batterySettingsIntent()
                                runCatching { genericLauncher.launch(intent) }
                            },
                            onExportBackup = { json ->
                                pendingExport = json
                                runCatching {
                                    createBackupLauncher.launch(BackupManager.suggestedBackupName())
                                }
                            },
                            onImportBackup = {
                                runCatching {
                                    openBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                                }
                            }
                        )

                        AppTab.SIMULATOR -> SimulatorScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutPenduCoderDialog(onDismiss = { showAboutDialog = false })
    }

    if (showAutostartHelp) {
        AutostartHelpDialog(
            onOpenSettings = {
                runCatching { genericLauncher.launch(PowerHelper.appSettingsIntent(context)) }
                showAutostartHelp = false
            },
            onDismiss = { showAutostartHelp = false }
        )
    }
}

/** The permissions the engine cannot work without, requested as one batch. */
private fun requestCorePermissions(
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    launcher.launch(
        buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    )
}

/** Writes an export the user has just chosen a location for. */
private fun writePendingExport(
    context: android.content.Context,
    uri: Uri?,
    content: String?,
    onDone: () -> Unit
) {
    if (uri != null && content != null) {
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        }.isSuccess
        Toast.makeText(
            context,
            if (ok) "Saved" else "Could not write that file",
            Toast.LENGTH_SHORT
        ).show()
    }
    onDone()
}

@Composable
private fun buildSetupSteps(
    hasSmsPermission: Boolean,
    hasNotificationPermission: Boolean,
    hasNotificationAccess: Boolean,
    isBatteryOptimised: Boolean,
    hasAccount: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onFixBattery: () -> Unit,
    onOpenSetup: () -> Unit
): List<SetupStep> = listOf(
    SetupStep(
        title = stringResource(R.string.perm_sms_receive_title),
        description = stringResource(R.string.perm_sms_receive_desc),
        icon = Icons.Default.Dashboard,
        isComplete = hasSmsPermission,
        isRequired = true,
        actionLabel = stringResource(R.string.action_grant),
        onAction = onRequestPermissions
    ),
    SetupStep(
        title = "Connect a sending account",
        description = "Choose the Google account or mailbox that forwarded messages are sent from.",
        icon = Icons.Default.Settings,
        isComplete = hasAccount,
        isRequired = true,
        actionLabel = "Set up",
        onAction = onOpenSetup
    ),
    SetupStep(
        title = stringResource(R.string.perm_notifications_title),
        description = stringResource(R.string.perm_notifications_desc),
        icon = Icons.Default.Info,
        isComplete = hasNotificationPermission,
        isRequired = false,
        actionLabel = stringResource(R.string.action_grant),
        onAction = onRequestPermissions
    ),
    SetupStep(
        title = stringResource(R.string.perm_battery_title),
        description = stringResource(R.string.perm_battery_desc),
        icon = Icons.Default.Science,
        isComplete = !isBatteryOptimised,
        isRequired = false,
        actionLabel = "Allow",
        onAction = onFixBattery
    ),
    SetupStep(
        title = stringResource(R.string.perm_notification_access_title),
        description = stringResource(R.string.perm_notification_access_desc),
        icon = Icons.Default.History,
        isComplete = hasNotificationAccess,
        isRequired = false,
        actionLabel = stringResource(R.string.action_grant),
        onAction = onOpenNotificationAccess
    )
)
