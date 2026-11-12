package com.finance.splitup.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.finance.splitup.data.Currencies
import com.finance.splitup.data.ExpenseCategories
import com.finance.splitup.data.PaymentNotificationListenerService
import com.finance.splitup.viewmodel.FinanceViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun OnboardingSetupScreen(
    viewModel: FinanceViewModel,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { com.finance.splitup.data.EncryptedPrefs.getPreferences(context, "splitup_prefs") }

    var currentStep by remember { mutableIntStateOf(1) } // 1 = Financial Profile, 2 = Permissions Setup

    // Step 1 State: Financial Profile & Budgets
    var selectedCurrency by remember { mutableStateOf(Currencies.EUR) }
    var currencySearchQuery by remember { mutableStateOf("") }
    var foodBudget by remember { mutableStateOf("400") }
    var transportBudget by remember { mutableStateOf("200") }
    var shoppingBudget by remember { mutableStateOf("300") }
    var billsBudget by remember { mutableStateOf("250") }
    var entertainmentBudget by remember { mutableStateOf("150") }

    var upiVpa by remember { mutableStateOf("") }
    var paypalHandle by remember { mutableStateOf("") }
    var enableBiometrics by remember { mutableStateOf(false) }

    var expandedCurrency by remember { mutableStateOf(false) }

    // Step 2 State: Permissions
    val contactsPermissionState = rememberPermissionState(permission = android.Manifest.permission.READ_CONTACTS)
    val cameraPermissionState = rememberPermissionState(permission = android.Manifest.permission.CAMERA)
    val notificationsPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(permission = android.Manifest.permission.POST_NOTIFICATIONS)
    } else null

    var isNotificationListenerActive by remember {
        mutableStateOf(PaymentNotificationListenerService.isNotificationListenerEnabled(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNotificationListenerActive = PaymentNotificationListenerService.isNotificationListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Stepper Progress Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$currentStep",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Text(
                        text = if (currentStep == 1) "Step 1 of 2: Financial Setup" else "Step 2 of 2: App Permissions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (currentStep == 1) 24.dp else 12.dp)
                            .clip(CircleShape)
                            .background(if (currentStep == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (currentStep == 2) 24.dp else 12.dp)
                            .clip(CircleShape)
                            .background(if (currentStep == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            val animatedProgress by animateFloatAsState(
                targetValue = if (currentStep == 1) 0.5f else 1.0f,
                animationSpec = tween(400),
                label = "onboardingProgress"
            )
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = com.finance.splitup.ui.theme.AppLogoBlue,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally(tween(300)) { it } + fadeIn() togetherWith slideOutHorizontally(tween(300)) { -it } + fadeOut()
                    } else {
                        slideInHorizontally(tween(300)) { -it } + fadeIn() togetherWith slideOutHorizontally(tween(300)) { it } + fadeOut()
                    }
                },
                modifier = Modifier.weight(1f),
                label = "onboardingStepTransition"
            ) { step ->
                when (step) {
                    1 -> {
                        // STEP 1: Financial Profile & Budgets
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .imePadding(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = com.finance.splitup.ui.theme.AppLogoBlue,
                                modifier = Modifier.size(64.dp),
                                shadowElevation = 4.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.AccountBalanceWallet,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                            Text(
                                text = "Welcome to SplitUp! 🎉",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Let's personalize your currency, category budget limits, and settlement details.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Currency Selection Card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("1. Default Currency", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    ExposedDropdownMenuBox(
                                        expanded = expandedCurrency,
                                        onExpandedChange = { expandedCurrency = it }
                                    ) {
                                        OutlinedTextField(
                                            value = selectedCurrency,
                                            onValueChange = {},
                                            readOnly = true,
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCurrency) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        ExposedDropdownMenu(
                                            expanded = expandedCurrency,
                                            onDismissRequest = {
                                                expandedCurrency = false
                                                currencySearchQuery = ""
                                            }
                                        ) {
                                            OutlinedTextField(
                                                value = currencySearchQuery,
                                                onValueChange = { currencySearchQuery = it },
                                                placeholder = { Text("Search currency...") },
                                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            val filteredCurrencies = Currencies.all.filter { it.contains(currencySearchQuery, ignoreCase = true) }
                                            if (filteredCurrencies.isEmpty()) {
                                                DropdownMenuItem(
                                                    text = { Text("No currencies found", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                                    onClick = {}
                                                )
                                            } else {
                                                filteredCurrencies.forEach { currency ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Text(Currencies.getFlag(currency), fontSize = 16.sp)
                                                                Text(currency)
                                                            }
                                                        },
                                                        onClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            selectedCurrency = currency
                                                            expandedCurrency = false
                                                            currencySearchQuery = ""
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Monthly Budgets Card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("2. Set Monthly Category Budgets", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = foodBudget,
                                            onValueChange = { foodBudget = it },
                                            label = { Text("Food & Dining") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        OutlinedTextField(
                                            value = transportBudget,
                                            onValueChange = { transportBudget = it },
                                            label = { Text("Transport") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = shoppingBudget,
                                            onValueChange = { shoppingBudget = it },
                                            label = { Text("Shopping") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        OutlinedTextField(
                                            value = billsBudget,
                                            onValueChange = { billsBudget = it },
                                            label = { Text("Bills") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                    OutlinedTextField(
                                        value = entertainmentBudget,
                                        onValueChange = { entertainmentBudget = it },
                                        label = { Text("Entertainment") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }

                            // Payment Handles Card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("3. Settlement Handles (Optional)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    OutlinedTextField(
                                        value = upiVpa,
                                        onValueChange = { upiVpa = it },
                                        label = { Text("UPI VPA (e.g. name@upi)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    OutlinedTextField(
                                        value = paypalHandle,
                                        onValueChange = { paypalHandle = it },
                                        label = { Text("PayPal Username / Handle") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }

                            // Security & Biometric Lock Card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("4. Biometric / Security Lock", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Require biometrics or device PIN when opening app",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = enableBiometrics,
                                        onCheckedChange = { enableBiometrics = it }
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentStep = 2
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Next: Permissions & Features", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    2 -> {
                        // STEP 2: Permissions & Features Screen
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .imePadding(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = com.finance.splitup.ui.theme.AppLogoBlue,
                                modifier = Modifier.size(64.dp),
                                shadowElevation = 4.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Security,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                            Text(
                                text = "App Permissions & Features 🔐",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Enable optional permissions to unlock smart features like contact discovery, OCR receipt scanning, and budget alerts.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // 1. Contacts Access
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Column {
                                            Text("1. Contacts Access", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text(
                                                "Find friends already on SplitUp and easily add group members.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { contactsPermissionState.launchPermissionRequest() },
                                        enabled = !contactsPermissionState.status.isGranted,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(if (contactsPermissionState.status.isGranted) "Granted ✓" else "Grant")
                                    }
                                }
                            }

                            // 2. Push Notifications Permission
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Column {
                                            Text("2. Push Notifications", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text(
                                                "Receive real-time budget threshold warnings and settlement updates.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { notificationsPermissionState?.launchPermissionRequest() },
                                        enabled = notificationsPermissionState?.status?.isGranted != true,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(if (notificationsPermissionState?.status?.isGranted == true || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) "Granted ✓" else "Grant")
                                    }
                                }
                            }

                            // 3. Camera Access
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Column {
                                            Text("3. Camera Access", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text(
                                                "Scan physical receipts with OCR and scan group QR invite codes.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { cameraPermissionState.launchPermissionRequest() },
                                        enabled = !cameraPermissionState.status.isGranted,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(if (cameraPermissionState.status.isGranted) "Granted ✓" else "Grant")
                                    }
                                }
                            }

                            // 4. Payment Notification Auto-Detector
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Column {
                                                Text("4. Payment Notification Auto-Detector", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                Text(
                                                    "Detect bank & payment app notifications (Google Wallet, GPay, Paytm) for 1-tap review.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "Unable to open Notification Access settings", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isNotificationListenerActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                                            contentColor = if (isNotificationListenerActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Icon(
                                            if (isNotificationListenerActive) Icons.Rounded.CheckCircle else Icons.Rounded.Notifications,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            if (isNotificationListenerActive) "Access Granted ✓" else "Enable Access in Settings",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        currentStep = 1
                                    },
                                    modifier = Modifier
                                        .weight(0.35f)
                                        .height(50.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Back", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        viewModel.updateDefaultCurrency(selectedCurrency)
                                        viewModel.updateBudget(ExpenseCategories.FOOD, foodBudget.toDoubleOrNull() ?: 400.0)
                                        viewModel.updateBudget(ExpenseCategories.TRANSPORT, transportBudget.toDoubleOrNull() ?: 200.0)
                                        viewModel.updateBudget(ExpenseCategories.SHOPPING, shoppingBudget.toDoubleOrNull() ?: 300.0)
                                        viewModel.updateBudget(ExpenseCategories.BILLS, billsBudget.toDoubleOrNull() ?: 250.0)
                                        viewModel.updateBudget(ExpenseCategories.ENTERTAINMENT, entertainmentBudget.toDoubleOrNull() ?: 150.0)
                                        viewModel.updateUserProfile(upiVpa, paypalHandle, "", "")
                                        viewModel.toggleBiometricLock(enableBiometrics)

                                        prefs.edit().putBoolean("has_completed_onboarding", true).apply()
                                        Toast.makeText(context, "Setup complete! Welcome to SplitUp.", Toast.LENGTH_SHORT).show()
                                        onSetupComplete()
                                    },
                                    modifier = Modifier
                                        .weight(0.65f)
                                        .height(50.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("Get Started 🚀", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}
