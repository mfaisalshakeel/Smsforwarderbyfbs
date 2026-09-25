package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.AppCard
import com.example.ui.components.ContentContainer
import com.example.ui.components.SetupChecklistCard
import com.example.ui.components.SetupStep
import com.example.ui.theme.Shapes
import com.example.ui.theme.Spacing

/**
 * First-run flow. Before this existed a new user landed on a dashboard full of warnings and had
 * to work out for themselves which of four unrelated screens still needed attention.
 */
@Composable
fun OnboardingScreen(
    setupSteps: List<SetupStep>,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val requiredDone = setupSteps.filter { it.isRequired }.all { it.isComplete }

    ContentContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Spacer(modifier = Modifier.size(Spacing.xl))

            Text(
                text = "Welcome",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Forward your text messages and app notifications to email, another " +
                    "phone, Telegram or a webhook — automatically.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.size(Spacing.sm))

            AppCard {
                FeatureRow(
                    icon = Icons.Default.MarkEmailRead,
                    title = "Never miss an OTP",
                    description = "Bank codes and verification messages reach you wherever you are."
                )
                Spacer(modifier = Modifier.size(Spacing.md))
                FeatureRow(
                    icon = Icons.Default.Bolt,
                    title = "Keeps trying",
                    description = "If the internet is down, messages are queued and sent later."
                )
                Spacer(modifier = Modifier.size(Spacing.md))
                FeatureRow(
                    icon = Icons.Default.Lock,
                    title = "Stays on your phone",
                    description = "No account, no analytics, nothing uploaded to the developer."
                )
            }

            SetupChecklistCard(steps = setupSteps)

            Button(
                onClick = onFinish,
                enabled = requiredDone,
                shape = Shapes.button,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_continue_button")
            ) {
                Text(if (requiredDone) "Get started" else "Grant the required steps first")
                if (requiredDone) {
                    Spacer(modifier = Modifier.size(Spacing.sm))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            TextButton(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip for now")
            }

            Spacer(modifier = Modifier.size(Spacing.xxl))
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.size(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
    }
}
