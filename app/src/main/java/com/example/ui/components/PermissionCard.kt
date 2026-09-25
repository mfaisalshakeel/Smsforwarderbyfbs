package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.PermissionState

@Composable
fun PermissionBanner(
    permissionsState: PermissionState,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val needsPermission = !permissionsState.hasReceiveSms || !permissionsState.hasSendSms

    val isDark = isSystemInDarkTheme()
    val bannerBg = if (isDark) Color(0xFF451A03) else Color(0xFFFEF3C7)
    val bannerBorder = if (isDark) Color(0xFFB45309) else Color(0xFFF59E0B)
    val titleColor = if (isDark) Color(0xFFFDE68A) else Color(0xFF78350F)
    val bodyColor = if (isDark) Color(0xFFFDE68A).copy(alpha = 0.9f) else Color(0xFF92400E)
    val warningIconColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)

    AnimatedVisibility(
        visible = needsPermission,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = bannerBg
            ),
            border = BorderStroke(1.dp, bannerBorder.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(16.dp),
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .testTag("permission_banner_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Permission Alert",
                        tint = warningIconColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "SMS Permissions Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "To detect incoming messages in the background and forward them via SMS, Android requires SMS permissions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = bodyColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PermissionPill(
                        label = "Receive SMS",
                        granted = permissionsState.hasReceiveSms,
                        icon = Icons.Default.Sms
                    )
                    PermissionPill(
                        label = "Send SMS",
                        granted = permissionsState.hasSendSms,
                        icon = Icons.Default.Sms
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_permissions_button")
                ) {
                    Text(
                        text = "Grant Required Permissions",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionPill(
    label: String,
    granted: Boolean,
    icon: ImageVector
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) {
        if (granted) Color(0xFF064E3B) else Color(0xFF7F1D1D)
    } else {
        if (granted) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
    }
    val contentColor = if (isDark) {
        if (granted) Color(0xFF4ADE80) else Color(0xFFF87171)
    } else {
        if (granted) Color(0xFF16A34A) else Color(0xFFDC2626)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, contentColor.copy(alpha = 0.4f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

