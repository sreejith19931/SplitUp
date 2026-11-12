package com.finance.splitup.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.finance.splitup.viewmodel.AuthViewModel
import com.finance.splitup.viewmodel.FinanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    viewModel: FinanceViewModel,
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentUser = try {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    } catch (_: Exception) {
        null
    }
    val userEmail = currentUser?.email ?: "user@splitup.app"
    var displayName by remember { mutableStateOf(currentUser?.displayName ?: userEmail.substringBefore("@")) }

    val isGoogleUser = remember(currentUser) {
        currentUser?.providerData?.any { it.providerId == "google.com" } == true
    }

    val userProfile by viewModel.userProfile.collectAsState()
    var upiVpa by remember(userProfile) { mutableStateOf(userProfile.upiVpa) }
    var paypalHandle by remember(userProfile) { mutableStateOf(userProfile.paypalHandle) }
    var venmoHandle by remember(userProfile) { mutableStateOf(userProfile.venmoHandle) }
    var revolutHandle by remember(userProfile) { mutableStateOf(userProfile.revolutHandle) }

    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var sendEmailOnPasswordChange by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }

    val userInitials = remember(displayName) { getInitials(displayName) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Avatar & QR button
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = userInitials,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = displayName.ifBlank { "User" }, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(text = userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Text("Account Details", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            OutlinedTextField(
                value = userEmail,
                onValueChange = {},
                enabled = false,
                label = { Text("Email Address (Cannot be changed)") },
                leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            if (isGoogleUser) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "Signed in with Google. Password and authentication are managed securely through your Google Account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text("Change Password", fontWeight = FontWeight.Bold, fontSize = 18.sp)

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm New Password") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { sendEmailOnPasswordChange = !sendEmailOnPasswordChange }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.MarkEmailRead, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text("Send confirmation email on password change", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = sendEmailOnPasswordChange,
                        onCheckedChange = { sendEmailOnPasswordChange = it }
                    )
                }
            }

            Text("Payment Handles for 'Settle Up'", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            OutlinedTextField(
                value = upiVpa,
                onValueChange = { upiVpa = it },
                label = { Text("UPI ID / VPA (e.g. name@oksbi)") },
                leadingIcon = { Icon(Icons.Rounded.Payment, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            OutlinedTextField(
                value = paypalHandle,
                onValueChange = { paypalHandle = it },
                label = { Text("PayPal Username / Email") },
                leadingIcon = { Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            OutlinedTextField(
                value = venmoHandle,
                onValueChange = { venmoHandle = it },
                label = { Text("Venmo Handle") },
                leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            OutlinedTextField(
                value = revolutHandle,
                onValueChange = { revolutHandle = it },
                label = { Text("Revolut Tag / Handle") },
                leadingIcon = { Icon(Icons.Rounded.CreditCard, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    isLoading = true
                    viewModel.updateUserProfile(upiVpa, paypalHandle, venmoHandle, revolutHandle)
                    
                    if (displayName.isNotBlank() && displayName != currentUser?.displayName) {
                        authViewModel.updateDisplayName(
                            newName = displayName,
                            onSuccess = {
                                if (!isGoogleUser && newPassword.isNotBlank()) {
                                    if (newPassword == confirmPassword) {
                                        authViewModel.updatePassword(
                                            newPassword = newPassword,
                                            sendEmailNotification = sendEmailOnPasswordChange,
                                            onSuccess = {
                                                isLoading = false
                                                viewModel.postSystemNotification(
                                                    "Security Alert 🔐",
                                                    "Your SplitUp password was changed.${if (sendEmailOnPasswordChange) " Confirmation email sent." else ""}",
                                                    FinanceViewModel.CHANNEL_SECURITY_ALERTS
                                                )
                                                Toast.makeText(context, "Profile and password updated successfully!", Toast.LENGTH_SHORT).show()
                                                onBack()
                                            },
                                            onError = { err ->
                                                isLoading = false
                                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    } else {
                                        isLoading = false
                                        Toast.makeText(context, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    isLoading = false
                                    Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                            },
                            onError = { err ->
                                isLoading = false
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    } else if (!isGoogleUser && newPassword.isNotBlank()) {
                        if (newPassword == confirmPassword) {
                            authViewModel.updatePassword(
                                newPassword = newPassword,
                                sendEmailNotification = sendEmailOnPasswordChange,
                                onSuccess = {
                                    isLoading = false
                                    viewModel.postSystemNotification(
                                        "Security Alert 🔐",
                                        "Your SplitUp password was changed.${if (sendEmailOnPasswordChange) " Confirmation email sent." else ""}",
                                        FinanceViewModel.CHANNEL_SECURITY_ALERTS
                                    )
                                    Toast.makeText(context, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                                    onBack()
                                },
                                onError = { err ->
                                    isLoading = false
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        } else {
                            isLoading = false
                            Toast.makeText(context, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        isLoading = false
                        Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null)
                        Text("Save Changes", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }


}
