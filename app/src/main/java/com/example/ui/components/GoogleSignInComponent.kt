package com.example.ui.components

import android.app.Activity
import android.accounts.AccountManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.Shapes
import com.example.ui.theme.Spacing
import com.example.ui.theme.appColors
import com.example.util.GoogleAccountHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

/**
 * Connects the Google account that mail is sent from.
 *
 * The sign-in request asks for the `gmail.send` scope up front, so the background forwarder
 * usually never has to interrupt the user later.
 */
@Composable
fun GoogleSignInButton(
    onAccountSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    buttonText: String = "Connect Google account"
) {
    val context = LocalContext.current
    var showAccountChooser by remember { mutableStateOf(false) }
    var detectedAccounts by remember { mutableStateOf<List<String>>(emptyList()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val signInOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/gmail.send"))
            .build()
    }
    val signInClient = remember(context) { GoogleSignIn.getClient(context, signInOptions) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) return@rememberLauncherForActivityResult
        val email = runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)?.email
        }.getOrNull()
            ?: result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)

        if (!email.isNullOrBlank()) {
            errorText = null
            onAccountSelected(email)
        } else {
            errorText = "Google did not return an email address. Try the account picker instead."
        }
    }

    val systemPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.takeIf { it.isNotBlank() }
                ?.let {
                    errorText = null
                    onAccountSelected(it)
                }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Button(
            onClick = {
                errorText = null
                runCatching { signInLauncher.launch(signInClient.signInIntent) }
                    .onFailure {
                        val accounts = GoogleAccountHelper.getDeviceGoogleAccounts(context)
                        if (accounts.isNotEmpty()) {
                            detectedAccounts = accounts
                            showAccountChooser = true
                        } else {
                            runCatching {
                                systemPickerLauncher.launch(GoogleAccountHelper.createGoogleAccountPickerIntent())
                            }.onFailure {
                                errorText = "No Google account is available on this device."
                            }
                        }
                    }
            },
            shape = Shapes.button,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.appColors.googleSurface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("continue_with_google_button")
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_google_logo),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(Spacing.md))
            Text(text = buttonText, style = MaterialTheme.typography.labelLarge)
        }

        errorText?.let {
            Spacer(modifier = Modifier.size(Spacing.sm))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    if (showAccountChooser) {
        GoogleAccountsDialog(
            accounts = detectedAccounts,
            onSelectAccount = {
                showAccountChooser = false
                onAccountSelected(it)
            },
            onLaunchSystemPicker = {
                showAccountChooser = false
                runCatching {
                    systemPickerLauncher.launch(GoogleAccountHelper.createGoogleAccountPickerIntent())
                }
            },
            onDismiss = { showAccountChooser = false }
        )
    }
}

/** Shows the account that is currently linked, with a way to change it. */
@Composable
fun ConnectedAccountCard(
    email: String,
    onChangeAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier, tone = Tone.Success, filled = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.appColors.success.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.appColors.success,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.size(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Sending from", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = email,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onChangeAccount) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(Spacing.xs))
                Text("Change")
            }
        }
    }
}

@Composable
fun GoogleAccountsDialog(
    accounts: List<String>,
    onSelectAccount: (String) -> Unit,
    onLaunchSystemPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Shapes.dialog,
        title = { Text("Choose an account") },
        text = {
            Column {
                if (accounts.isEmpty()) {
                    Text(
                        text = "No Google accounts were found on this device. " +
                            "Add one in Android settings, then try again.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    accounts.forEach { account ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Spacing.minTouchTarget)
                                .clickable { onSelectAccount(account) },
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.size(Spacing.md))
                                Text(
                                    text = account,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onLaunchSystemPicker) { Text("Use system picker") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
