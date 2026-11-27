package com.finance.splitup.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.data.Currencies
import com.finance.splitup.data.ExpenseCategories
import com.finance.splitup.viewmodel.FinanceViewModel
import com.finance.splitup.data.UiMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingSetupScreen(
    viewModel: FinanceViewModel,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { com.finance.splitup.data.EncryptedPrefs.getPreferences(context, "splitup_prefs") }
    val currentUserId = remember {
        try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "guest" } catch (_: Exception) { "guest" }
    }

    // Financial Profile & Budgets State
    var selectedCurrency by remember { mutableStateOf(Currencies.EUR) }
    val currencySymbol = remember(selectedCurrency) { Currencies.getSymbol(selectedCurrency) }
    var currencySearchQuery by remember { mutableStateOf("") }
    var foodBudget by remember { mutableStateOf("400") }
    var transportBudget by remember { mutableStateOf("200") }
    var shoppingBudget by remember { mutableStateOf("300") }
    var billsBudget by remember { mutableStateOf("250") }
    var entertainmentBudget by remember { mutableStateOf("150") }

    var upiVpa by remember { mutableStateOf("") }
    var paypalHandle by remember { mutableStateOf("") }
    var showPayPalLoginDialog by remember { mutableStateOf(false) }
    var enableBiometrics by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    var expandedCurrency by remember { mutableStateOf(false) }

    fun completeOnboarding() {
        viewModel.updateDefaultCurrency(selectedCurrency)
        viewModel.updateBudget(ExpenseCategories.FOOD, foodBudget.toDoubleOrNull() ?: 400.0)
        viewModel.updateBudget(ExpenseCategories.TRANSPORT, transportBudget.toDoubleOrNull() ?: 200.0)
        viewModel.updateBudget(ExpenseCategories.SHOPPING, shoppingBudget.toDoubleOrNull() ?: 300.0)
        viewModel.updateBudget(ExpenseCategories.BILLS, billsBudget.toDoubleOrNull() ?: 250.0)
        viewModel.updateBudget(ExpenseCategories.ENTERTAINMENT, entertainmentBudget.toDoubleOrNull() ?: 150.0)
        viewModel.updateUserProfile(upiVpa, paypalHandle, "", "")
        viewModel.toggleBiometricLock(enableBiometrics)
        viewModel.setOnboardingCompleted(true)

        prefs.edit()
            .putBoolean("has_completed_onboarding_$currentUserId", true)
            .putBoolean("has_completed_onboarding", true)
            .apply()
        UiMessage.show("Setup complete! Welcome to SplitUp.")
        onSetupComplete()
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

            // Header Indicator
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
                                text = "✨",
                                fontSize = 12.sp
                            )
                        }
                    }
                    Text(
                        text = "Quick Setup",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    completeOnboarding()
                }) {
                    Text("Skip Setup", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { 1.0f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = com.finance.splitup.ui.theme.AppLogoBlue,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Main Setup Form
            Column(
                modifier = Modifier
                    .weight(1f)
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
                    text = "Personalize your default currency, monthly budgets, and security preferences.",
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
                                onValueChange = { foodBudget = it.filter { c -> c.isDigit() } },
                                label = { Text("Food & Dining") },
                                leadingIcon = { Text("🍔", fontSize = 16.sp) },
                                prefix = { Text(currencySymbol) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = transportBudget,
                                onValueChange = { transportBudget = it.filter { c -> c.isDigit() } },
                                label = { Text("Transport") },
                                leadingIcon = { Text("🚗", fontSize = 16.sp) },
                                prefix = { Text(currencySymbol) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = shoppingBudget,
                                onValueChange = { shoppingBudget = it.filter { c -> c.isDigit() } },
                                label = { Text("Shopping") },
                                leadingIcon = { Text("🛒", fontSize = 16.sp) },
                                prefix = { Text(currencySymbol) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = billsBudget,
                                onValueChange = { billsBudget = it.filter { c -> c.isDigit() } },
                                label = { Text("Bills") },
                                leadingIcon = { Text("⚡", fontSize = 16.sp) },
                                prefix = { Text(currencySymbol) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                        OutlinedTextField(
                            value = entertainmentBudget,
                            onValueChange = { entertainmentBudget = it.filter { c -> c.isDigit() } },
                            label = { Text("Entertainment") },
                            leadingIcon = { Text("🎬", fontSize = 16.sp) },
                            prefix = { Text(currencySymbol) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = paypalHandle,
                                onValueChange = { paypalHandle = it },
                                label = { Text("PayPal Username / Handle") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedButton(
                                onClick = { showPayPalLoginDialog = true },
                                modifier = Modifier.height(56.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (showPayPalLoginDialog) {
                            PayPalLoginDialog(
                                onDismiss = { showPayPalLoginDialog = false },
                                onPayPalHandleRetrieved = { handle ->
                                    paypalHandle = handle
                                }
                            )
                        }
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
                            onCheckedChange = { checked ->
                                if (checked) {
                                    showPinDialog = true
                                    newPinInput = ""
                                } else {
                                    enableBiometrics = false
                                }
                            }
                        )
                    }
                }

                if (showPinDialog) {
                    AlertDialog(
                        onDismissRequest = { 
                            showPinDialog = false
                            newPinInput = ""
                            confirmPinInput = ""
                            pinError = null
                        },
                        title = { Text("Set Fallback 4-Digit PIN", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Please enter and confirm your 4-digit numeric fallback PIN:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(
                                    value = newPinInput,
                                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPinInput = it },
                                    label = { Text("4-Digit PIN") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                                )
                                OutlinedTextField(
                                    value = confirmPinInput,
                                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPinInput = it },
                                    label = { Text("Confirm 4-Digit PIN") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                                )
                                if (pinError != null) {
                                    Text(pinError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (newPinInput.length == 4 && confirmPinInput.length == 4) {
                                        if (newPinInput == confirmPinInput) {
                                            val hashed = com.finance.splitup.data.PinSecurity.hash(newPinInput)
                                                ?: com.finance.splitup.data.PinSecurity.legacyHash(newPinInput)
                                            val sp = com.finance.splitup.data.EncryptedPrefs.getPreferences(context, "splitup_prefs")
                                            sp.edit().putString("security_pin_hash", hashed).apply()
                                            enableBiometrics = true
                                            showPinDialog = false
                                            newPinInput = ""
                                            confirmPinInput = ""
                                            pinError = null
                                            UiMessage.show("Fallback PIN set & biometrics enabled!")
                                        } else {
                                            pinError = "PINs do not match. Please try again."
                                        }
                                    } else {
                                        pinError = "PIN must be exactly 4 digits."
                                    }
                                }
                            ) {
                                Text("Set PIN & Enable")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { 
                                showPinDialog = false
                                newPinInput = ""
                                confirmPinInput = ""
                                pinError = null
                            }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        completeOnboarding()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Complete Setup & Get Started", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Icon(Icons.Rounded.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
