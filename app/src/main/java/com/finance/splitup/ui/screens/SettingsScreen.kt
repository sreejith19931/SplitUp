package com.finance.splitup.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.data.Currencies
import com.finance.splitup.ui.theme.ThemeMode
import com.finance.splitup.viewmodel.AuthViewModel
import com.finance.splitup.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: FinanceViewModel,
    authViewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToSecurity: () -> Unit = {},
    onNavigateToDataManagement: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentUser = try {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    } catch (_: Exception) {
        null
    }
    val userEmail = currentUser?.email ?: "user@splitup.app"
    val userName = currentUser?.displayName ?: userEmail.substringBefore("@")
    val userInitials = userName.take(2).uppercase()

    var showSignOutDialog by remember { mutableStateOf(false) }
    var showProfileQrDialog by remember { mutableStateOf(false) }
    var hasUnsyncedChanges by remember { mutableStateOf(false) }
    var isCheckingUnsynced by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(showSignOutDialog) {
        if (showSignOutDialog) {
            isCheckingUnsynced = true
            hasUnsyncedChanges = viewModel.hasUnsyncedChanges()
            isCheckingUnsynced = false
        }
    }

    LaunchedEffect(Unit) {
        try {
            com.finance.splitup.data.CurrencyApiService.updateLiveRates()
        } catch (_: Exception) {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Profile", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // User Profile Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
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
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = userName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(text = userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = onNavigateToEditProfile,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Text("Edit Profile", fontSize = 13.sp)
                                        }
                                    }
                                    Button(
                                        onClick = { showProfileQrDialog = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary,
                                            contentColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Rounded.QrCode, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                            Text("My QR Code", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    // Preferences & General Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("Preferences & General", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)

                            // Currency Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Rounded.CurrencyExchange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Default Currency", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                                var expandedCurrencyDropdown by remember { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(
                                        onClick = { expandedCurrencyDropdown = true },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("${Currencies.getFlag(viewModel.defaultCurrency)} ${viewModel.defaultCurrency}")
                                    }
                                    DropdownMenu(
                                        expanded = expandedCurrencyDropdown,
                                        onDismissRequest = { expandedCurrencyDropdown = false }
                                    ) {
                                        Currencies.all.forEach { currency ->
                                            DropdownMenuItem(
                                                text = { Text("${Currencies.getFlag(currency)} $currency") },
                                                onClick = {
                                                    viewModel.updateDefaultCurrency(currency)
                                                    expandedCurrencyDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Theme Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("App Theme", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                                SingleChoiceSegmentedButtonRow {
                                    SegmentedButton(
                                        selected = viewModel.themeMode == ThemeMode.SYSTEM,
                                        onClick = { viewModel.updateThemeMode(ThemeMode.SYSTEM) },
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                        colors = SegmentedButtonDefaults.colors(
                                            activeContainerColor = MaterialTheme.colorScheme.primary,
                                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                            inactiveContentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) { Text("System", fontSize = 12.sp) }
                                    SegmentedButton(
                                        selected = viewModel.themeMode == ThemeMode.LIGHT,
                                        onClick = { viewModel.updateThemeMode(ThemeMode.LIGHT) },
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                        colors = SegmentedButtonDefaults.colors(
                                            activeContainerColor = MaterialTheme.colorScheme.primary,
                                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                            inactiveContentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) { Text("Light", fontSize = 12.sp) }
                                    SegmentedButton(
                                        selected = viewModel.themeMode == ThemeMode.DARK,
                                        onClick = { viewModel.updateThemeMode(ThemeMode.DARK) },
                                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                        colors = SegmentedButtonDefaults.colors(
                                            activeContainerColor = MaterialTheme.colorScheme.primary,
                                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                            inactiveContentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) { Text("Dark", fontSize = 12.sp) }
                                }
                            }

                            HorizontalDivider()

                            // Subscriptions Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onNavigateToSubscriptions)
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Rounded.Subscriptions, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text("Subscriptions & Recurring Bills", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("Manage automated bills & reminders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                item {
                    // Security & Protection Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToSecurity),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("Security & App Protection", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Biometric lock, PIN & payment notification detector", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                item {
                    // Data Management & Backups Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToDataManagement),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("Data Management, Reports & Backups", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("PDF/CSV exports, JSON backups & danger zone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                item {
                    // About Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToAbout),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("About SplitUp", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                item {
                    // Sign Out Button
                    OutlinedButton(
                        onClick = { showSignOutDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out", fontWeight = FontWeight.Bold)
                    }
                }
            }



            if (showSignOutDialog) {
                AlertDialog(
                    onDismissRequest = { showSignOutDialog = false },
                    title = { Text("Sign Out", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Are you sure you want to sign out of SplitUp?")
                            if (isCheckingUnsynced) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            } else if (hasUnsyncedChanges) {
                                Text("⚠️ Warning: You have unsynced offline changes. Signing out now may result in data loss if you don't sync first.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    },
                    confirmButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (hasUnsyncedChanges) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val database = com.finance.splitup.data.db.AppDatabase.getDatabase(context)
                                                val repository = com.finance.splitup.data.repository.OfflineFirstFinanceRepository(database.financeDao())
                                                com.finance.splitup.data.CloudSyncManager.performCloudSync(repository, context)
                                                Toast.makeText(context, "Sync completed successfully!", Toast.LENGTH_SHORT).show()
                                                hasUnsyncedChanges = false
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Sync failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Text("Sync Now")
                                }
                            }
                            Button(
                                onClick = {
                                    authViewModel.signOut()
                                    showSignOutDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                            ) {
                                Text("Sign Out")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSignOutDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showProfileQrDialog) {
                ProfileQRCodeDialog(
                    name = userName,
                    email = userEmail,
                    onDismiss = { showProfileQrDialog = false }
                )
            }
        }
    }
}
