package com.finance.splitup.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class DisclosureType {
    data object NotificationAccess : DisclosureType()
    data object ContactsAccess : DisclosureType()
}

@Composable
fun ProminentDisclosureDialog(
    type: DisclosureType,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val (title, description, icon) = when (type) {
        is DisclosureType.NotificationAccess -> Triple(
            "Optional Payment Notification Access",
            "SplitUp can optionally read incoming bank and payment notifications to automatically suggest and record expenses for you. This feature is 100% optional, safe, runs locally on your device, and you can disable or revoke permission at any time in settings without affecting core app functionality.",
            Icons.Outlined.Notifications
        )
        is DisclosureType.ContactsAccess -> Triple(
            "Optional Contacts Access",
            "SplitUp can optionally access your contacts to help you easily add friends and split bills with them. This feature is 100% optional, and contact information is only used locally to match group members.",
            Icons.Outlined.Person
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Continue / Enable")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Not Now (Optional)")
            }
        }
    )
}
