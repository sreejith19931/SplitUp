package com.finance.splitup.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.data.SampleContacts
import com.finance.splitup.viewmodel.FinanceViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun FindFriendsScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val contactPermissionState = rememberPermissionState(permission = android.Manifest.permission.READ_CONTACTS)
    val registeredEmails by viewModel.registeredUserEmails.collectAsState()
    val registeredCloudUsers by viewModel.registeredUsers.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    val allContacts = remember(contactPermissionState.status.isGranted, registeredCloudUsers, searchQuery) {
        val deviceList = if (contactPermissionState.status.isGranted) {
            SampleContacts.loadDeviceContacts(context)
        } else {
            emptyList()
        }
        val deviceEmails = deviceList.map { it.email.lowercase() }.toSet()
        val visibleCloudUsers = if (searchQuery.isNotBlank()) {
            registeredCloudUsers.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.email.contains(searchQuery, ignoreCase = true)
            }
        } else {
            registeredCloudUsers.filter { it.email.lowercase() in deviceEmails }
        }
        val deviceListFiltered = if (searchQuery.isNotBlank()) {
            deviceList.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.email.contains(searchQuery, ignoreCase = true)
            }
        } else {
            deviceList
        }
        (deviceListFiltered + visibleCloudUsers).distinctBy { it.email.lowercase() }
    }

    val contactsWithStatus = remember(allContacts, registeredEmails) {
        allContacts.map { contact ->
            val isRegistered = contact.email.lowercase() in registeredEmails || contact.email.endsWith("@splitup.app") || contact.email.endsWith("@splitup.finance") || contact.email.contains("splitup")
            contact.copy(isSplitUpMember = isRegistered)
        }
    }

    val filteredContacts = remember(searchQuery, contactsWithStatus) {
        if (searchQuery.isBlank()) {
            contactsWithStatus
        } else {
            contactsWithStatus.filter {
                it.name.contains(searchQuery, ignoreCase = true) || it.email.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val onSplitUp = filteredContacts.filter { it.isSplitUpMember }
    val inviteOthers = filteredContacts.filter { !it.isSplitUpMember }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find Friends on SplitUp", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!contactPermissionState.status.isGranted) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Contacts Prominent Disclosure", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(
                            "SplitUp accesses your contact list strictly locally on your device to discover which of your friends are already registered on SplitUp and allow adding them to shared expense groups. Your contacts are processed in memory and are never uploaded, stored remotely, or sold to 3rd party servers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Button(
                            onClick = { contactPermissionState.launchPermissionRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) {
                            Text("I Understand & Grant Permission")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search contacts by name or email...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onSplitUp.isNotEmpty()) {
                    item {
                        Text("On SplitUp (${onSplitUp.size}) 🎉", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    items(onSplitUp, key = { it.email }) { contact ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    MemberAvatar(name = contact.name, size = 40.dp)
                                    Column {
                                        Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text(contact.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        "On SplitUp",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                if (inviteOthers.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Invite to SplitUp (${inviteOthers.size}) 🔗", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(inviteOthers, key = { it.email }) { contact ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    MemberAvatar(name = contact.name, size = 40.dp)
                                    Column {
                                        Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text(contact.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        val inviteUrl = "splitup://invite"
                                        val inviteText = "Hey ${contact.name}! Join me on SplitUp to easily split bills & expenses: $inviteUrl"
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, inviteText)
                                        }
                                        try {
                                            context.startActivity(Intent.createChooser(intent, "Invite via"))
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Could not open share sheet", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Invite", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                if (filteredContacts.isEmpty()) {
                    item {
                        com.finance.splitup.ui.components.EmptyStateComponent(
                            icon = Icons.Rounded.PersonSearch,
                            title = "No Contacts Found",
                            description = if (searchQuery.isNotBlank()) "No contacts match '$searchQuery'." else "No contacts loaded. Make sure contacts permission is granted."
                        )
                    }
                }
            }
        }
    }
}
