package com.finance.splitup.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.data.PaymentNotificationListenerService
import com.finance.splitup.viewmodel.FinanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    var isNotificationListenerActive by remember {
        mutableStateOf(PaymentNotificationListenerService.isNotificationListenerEnabled(context))
    }

    var showPinDialog by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var pendingBiometricEnable by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security & App Protection", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Security & App Protection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Biometric & PIN Lock", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Secure app access on launch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Biometric Security Lock", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Require unlock on app launch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = viewModel.isBiometricLockEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val fragActivity = activity as? androidx.fragment.app.FragmentActivity
                                    if (fragActivity != null) {
                                        val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
                                        val biometricPrompt = androidx.biometric.BiometricPrompt(
                                            fragActivity,
                                            executor,
                                            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                                    super.onAuthenticationSucceeded(result)
                                                    Toast.makeText(context, "Biometric verified! Now set your fallback PIN.", Toast.LENGTH_SHORT).show()
                                                    pendingBiometricEnable = true
                                                    newPinInput = ""
                                                    showPinDialog = true
                                                }
                                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                                    super.onAuthenticationError(errorCode, errString)
                                                    Toast.makeText(context, "Authentication failed: $errString", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                            .setTitle("Biometric Authentication")
                                            .setSubtitle("Confirm your fingerprint to enable biometric lock")
                                            .setNegativeButtonText("Cancel")
                                            .build()
                                        biometricPrompt.authenticate(promptInfo)
                                    } else {
                                        pendingBiometricEnable = true
                                        newPinInput = ""
                                        showPinDialog = true
                                    }
                                } else {
                                    viewModel.toggleBiometricLock(false)
                                    Toast.makeText(context, "Biometric lock disabled", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    if (viewModel.isBiometricLockEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Lock Timeout Delay", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val timeouts = listOf(0 to "Immediately", 1 to "1 Min", 5 to "5 Mins", 15 to "15 Mins")
                                timeouts.forEach { (mins, label) ->
                                    FilterChip(
                                        selected = viewModel.biometricLockTimeoutMins == mins,
                                        onClick = { viewModel.updateBiometricLockTimeout(mins) },
                                        label = { Text(label, fontSize = 11.sp) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("4-Digit Security PIN", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Fallback PIN for unlocking", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { pendingBiometricEnable = false; newPinInput = ""; showPinDialog = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Change PIN")
                        }
                    }
                }
            }

            // Payment Notification Detector Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Payment Auto-Detector", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Detect bank/UPI payment notifications", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "Prominent Disclosure: SplitUp's Payment Auto-Detector reads system notification alerts strictly locally on your device to automatically detect monetary transactions from bank and wallet apps. Notification content is processed entirely in memory, is never stored on external servers, and is never transmitted off your device.\n\nNote for Android 13+: If Notification Access is greyed out in settings, open Phone Settings > Apps > SplitUp, tap the three dots menu at the top right, select 'Allow restricted settings', and then grant access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )

                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            } catch (_: Exception) {
                                Toast.makeText(context, "Unable to open Notification Access settings", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (isNotificationListenerActive) "Notification Access Enabled (Tap to Manage)" else "Grant Notification Access")
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { 
                showPinDialog = false 
                pendingBiometricEnable = false
            },
            title = { Text(if (pendingBiometricEnable) "Set Fallback PIN to Enable Biometrics" else "Set 4-Digit Security PIN", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (pendingBiometricEnable) "Please enter a 4-digit numeric PIN as a fallback unlock method before biometric lock is activated:" else "Enter a 4-digit numeric PIN for fallback unlocking:")
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) newPinInput = it },
                        label = { Text("4-Digit PIN") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinInput.length == 4) {
                            val hashed = com.finance.splitup.hashSecurityPin(newPinInput)
                            val prefs = com.finance.splitup.data.EncryptedPrefs.getPreferences(context, "splitup_prefs")
                            prefs.edit().putString("security_pin_hash", hashed).apply()
                            
                            if (pendingBiometricEnable) {
                                viewModel.toggleBiometricLock(true)
                                pendingBiometricEnable = false
                                Toast.makeText(context, "Biometric lock & fallback PIN activated successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Security PIN updated & hashed successfully!", Toast.LENGTH_SHORT).show()
                            }
                            showPinDialog = false
                        } else {
                            Toast.makeText(context, "PIN must be exactly 4 digits", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showPinDialog = false 
                        pendingBiometricEnable = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
