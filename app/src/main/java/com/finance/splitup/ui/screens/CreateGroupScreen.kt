package com.finance.splitup.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
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
import com.finance.splitup.data.AppContact
import com.finance.splitup.data.SampleContacts
import com.finance.splitup.viewmodel.FinanceViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalPermissionsApi::class)
@Composable
fun CreateGroupScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit,
    onGroupCreated: (String) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var groupType by remember { mutableStateOf("Trip") }
    val emojis = listOf("✈️", "🏠", "🍕", "💼", "🎮", "🎁", "🌴", "☕", "🚗", "🏖️")
    var selectedEmoji by remember { mutableStateOf("✈️") }
    var selectedColorHex by remember { mutableStateOf("#3B82F6") }
    var memberSearchQuery by remember { mutableStateOf("") }
    var selectedMembers by remember { mutableStateOf(emptyList<String>()) }
    var selectedMemberEmails by remember { mutableStateOf(emptyList<String>()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var memberForWhatsAppInvite by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val contactPermissionState = rememberPermissionState(permission = android.Manifest.permission.READ_CONTACTS)
    val registeredCloudUsers by viewModel.registeredUsers.collectAsState()
    val registeredUserEmails by viewModel.registeredUserEmails.collectAsState()

    LaunchedEffect(Unit) {
        if (!contactPermissionState.status.isGranted) {
            contactPermissionState.launchPermissionRequest()
        }
    }

    val allContacts = remember(contactPermissionState.status.isGranted, registeredCloudUsers, registeredUserEmails) {
        val deviceList = if (contactPermissionState.status.isGranted) {
            SampleContacts.loadDeviceContacts(context)
        } else {
            emptyList()
        }
        val registeredSet = registeredUserEmails + registeredCloudUsers.map { it.email.lowercase() }
        val combined = (registeredCloudUsers + deviceList).distinctBy { it.email.lowercase() }
        combined.map { contact ->
            val isRegistered = contact.isSplitUpMember ||
                    registeredSet.contains(contact.email.lowercase()) ||
                    contact.email.endsWith("@splitup.app") ||
                    contact.email.endsWith("@splitup.finance") ||
                    contact.email.contains("splitup")
            contact.copy(isSplitUpMember = isRegistered)
        }
    }

    val types = listOf("Trip", "Roommates", "Dinner", "Other")

    val selfName = viewModel.currentUserName.lowercase()
    val selfEmail = viewModel.currentUserEmail.lowercase()
    val creatorNames = remember(selfName, selfEmail) { listOf("you", selfName, selfEmail) }

    val filteredContacts = remember(memberSearchQuery, selectedMembers, selectedMemberEmails, allContacts, selfName, selfEmail) {
        val existing = (selectedMembers.map { it.lowercase() } + creatorNames).distinct()
        val existingEmails = (selectedMemberEmails.map { it.lowercase() } + listOf(selfEmail)).distinct()

        val matches = if (memberSearchQuery.isBlank()) {
            allContacts
        } else {
            allContacts.filter { contact ->
                contact.name.contains(memberSearchQuery, ignoreCase = true) ||
                        contact.email.contains(memberSearchQuery, ignoreCase = true)
            }
        }

        matches.sortedWith(
            compareByDescending<AppContact> { contact ->
                existing.contains(contact.name.lowercase()) || existingEmails.contains(contact.email.lowercase())
            }.thenByDescending { contact ->
                contact.isSplitUpMember
            }.thenBy { contact ->
                contact.name.lowercase()
            }
        )
    }

    val isExactMatch = filteredContacts.any { it.name.equals(memberSearchQuery.trim(), ignoreCase = true) || it.email.equals(memberSearchQuery.trim(), ignoreCase = true) }
    val isCustomSelfOrExisting = memberSearchQuery.isNotBlank() && (
        selectedMembers.map { it.lowercase() }.contains(memberSearchQuery.trim().lowercase()) ||
        creatorNames.contains(memberSearchQuery.trim().lowercase()) ||
        (memberSearchQuery.contains("@") && memberSearchQuery.trim().lowercase() == selfEmail)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create New Group", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
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
            OutlinedTextField(
                value = groupName,
                onValueChange = {
                    groupName = it
                    errorMessage = null
                },
                label = { Text("Group Name (e.g. Summer Vacation)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                isError = errorMessage != null && groupName.isBlank()
            )

            Text("Group Type", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                types.forEach { type ->
                    FilterChip(
                        selected = groupType == type,
                        onClick = { groupType = type },
                        label = { Text(type) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Text("Group Icon / Emoji", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(emojis) { emoji ->
                    Surface(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .clickable { selectedEmoji = emoji },
                        shape = CircleShape,
                        color = if (selectedEmoji == emoji) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (selectedEmoji == emoji) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }
            }

            Text("Add Members", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text("You (Creator)") },
                    shape = RoundedCornerShape(12.dp)
                )
                selectedMembers.forEach { member ->
                    FilterChip(
                        selected = true,
                        onClick = {
                            selectedMembers = selectedMembers.filter { it != member }
                            // Optionally remove email if needed
                        },
                        label = { Text(member) },
                        trailingIcon = {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = memberSearchQuery,
                onValueChange = { memberSearchQuery = it },
                label = { Text("Search by Name or Email...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                supportingText = { Text("Search by name/email or type a custom name") }
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filteredContacts.take(5).forEach { contact ->
                    val isCreator = creatorNames.contains(contact.name.lowercase()) || creatorNames.contains(contact.email.lowercase())
                    val isAlreadyAdded = isCreator || selectedMembers.contains(contact.name) ||
                            (contact.email.contains("@") && selectedMemberEmails.contains(contact.email))

                    val backgroundColor = when {
                        isAlreadyAdded -> MaterialTheme.colorScheme.secondaryContainer
                        contact.isSplitUpMember -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!isAlreadyAdded) {
                                    selectedMembers = selectedMembers + contact.name
                                    if (contact.email.contains("@")) {
                                        selectedMemberEmails = selectedMemberEmails + contact.email
                                    }
                                    if (!contact.isSplitUpMember) {
                                        memberForWhatsAppInvite = contact.name
                                    }
                                } else if (!isCreator) {
                                    selectedMembers = selectedMembers.filter { it != contact.name }
                                    selectedMemberEmails = selectedMemberEmails.filter { it != contact.email }
                                }
                                memberSearchQuery = ""
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = backgroundColor,
                        border = if (contact.isSplitUpMember && !isAlreadyAdded) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        } else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (isCreator) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                "You (Creator)",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else if (isAlreadyAdded) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = com.finance.splitup.ui.theme.EmeraldSuccess.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                "Added",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = com.finance.splitup.ui.theme.EmeraldSuccess,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else if (contact.isSplitUpMember) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                "On SplitUp ⚡",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                val sub = if (isCreator) "Group Creator" else if (contact.isSplitUpMember) "SplitUp Member • Directly Added" else "${contact.email} • Not on SplitUp"
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = if (contact.isSplitUpMember && !isAlreadyAdded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                if (!isAlreadyAdded) {
                                    selectedMembers = selectedMembers + contact.name
                                    if (contact.email.contains("@")) {
                                        selectedMemberEmails = selectedMemberEmails + contact.email
                                    }
                                    if (!contact.isSplitUpMember) {
                                        memberForWhatsAppInvite = contact.name
                                    }
                                } else if (!isCreator) {
                                    selectedMembers = selectedMembers.filter { it != contact.name }
                                    selectedMemberEmails = selectedMemberEmails.filter { it != contact.email }
                                }
                                memberSearchQuery = ""
                            }) {
                                if (isCreator) {
                                    Icon(Icons.Rounded.Person, contentDescription = "Creator", tint = MaterialTheme.colorScheme.primary)
                                } else if (isAlreadyAdded) {
                                    Icon(Icons.Rounded.Person, contentDescription = "Added", tint = com.finance.splitup.ui.theme.EmeraldSuccess)
                                } else if (contact.isSplitUpMember) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    Icon(Icons.Rounded.Add, contentDescription = "Add")
                                }
                            }
                        }
                    }
                }

                if (memberSearchQuery.isNotBlank() && !isExactMatch) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!isCustomSelfOrExisting) {
                                    val custom = memberSearchQuery.trim()
                                    if (!selectedMembers.contains(custom)) {
                                        selectedMembers = selectedMembers + custom
                                        if (custom.contains("@")) {
                                            selectedMemberEmails = selectedMemberEmails + custom
                                        }
                                        memberForWhatsAppInvite = custom
                                    }
                                }
                                memberSearchQuery = ""
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCustomSelfOrExisting) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Add '${memberSearchQuery.trim()}'", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(if (isCustomSelfOrExisting) "You (Creator) or member already in group" else "Custom member • Generates WhatsApp invite link", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            }
                            if (!isCustomSelfOrExisting) {
                                IconButton(onClick = {
                                    val custom = memberSearchQuery.trim()
                                    if (!selectedMembers.contains(custom)) {
                                        selectedMembers = selectedMembers + custom
                                        if (custom.contains("@")) {
                                            selectedMemberEmails = selectedMemberEmails + custom
                                        }
                                        memberForWhatsAppInvite = custom
                                    }
                                    memberSearchQuery = ""
                                }) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Add Custom")
                                }
                            }
                        }
                    }
                }
            }

            if (memberForWhatsAppInvite != null) {
                WhatsAppInviteDialog(
                    memberName = memberForWhatsAppInvite!!,
                    groupName = groupName.ifBlank { "New Group" },
                    groupId = "new_group",
                    onDismiss = { memberForWhatsAppInvite = null }
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (groupName.isBlank()) {
                        errorMessage = "Please enter a group name."
                    } else {
                        val newGroupId = viewModel.addGroup(
                            name = groupName,
                            type = groupType,
                            members = selectedMembers,
                            memberEmails = selectedMemberEmails,
                            emoji = selectedEmoji,
                            colorHex = selectedColorHex
                        )
                        onGroupCreated(newGroupId)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.GroupAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Group", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
