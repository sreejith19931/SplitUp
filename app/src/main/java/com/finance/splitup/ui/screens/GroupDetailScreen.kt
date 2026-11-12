package com.finance.splitup.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.data.Currencies
import com.finance.splitup.data.ExpenseCategories
import com.finance.splitup.data.Group
import com.finance.splitup.data.PaymentDeepLinkHelper
import com.finance.splitup.data.SampleContacts
import com.finance.splitup.data.Settlement
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.finance.splitup.ui.theme.*
import com.finance.splitup.viewmodel.FinanceViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    viewModel: FinanceViewModel,
    onBack: () -> Unit,
    onNavigateToAddExpense: (String) -> Unit,
    onNavigateToEditGroupExpense: (String, String) -> Unit = { _, _ -> },
    onNavigateToReceiptScan: (String) -> Unit
) {
    val groups by viewModel.groups.collectAsState()
    val group = groups.find { it.id == groupId }

    if (group == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Group Not Found") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This group no longer exists.")
            }
        }
        return
    }

    GroupDetailContent(
        group = group,
        viewModel = viewModel,
        onAddExpense = { onNavigateToAddExpense(group.id) },
        onEditGroupExpense = { gId, expId -> onNavigateToEditGroupExpense(gId, expId) },
        onScanReceipt = { onNavigateToReceiptScan(group.id) },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailContent(
    group: Group,
    viewModel: FinanceViewModel,
    onAddExpense: () -> Unit,
    onEditGroupExpense: (String, String) -> Unit = { _, _ -> },
    onScanReceipt: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val selfName = viewModel.currentUserName
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showEditGroupDialog by remember { mutableStateOf(false) }
    var showDeleteGroupConfirmDialog by remember { mutableStateOf(false) }
    var editingGroupExpense by remember { mutableStateOf<com.finance.splitup.data.GroupExpense?>(null) }
    var memberToRemove by remember { mutableStateOf<String?>(null) }
    var selectedSettlementForPayment by remember { mutableStateOf<Settlement?>(null) }
    var customMemberForWhatsAppInvite by remember { mutableStateOf<String?>(null) }
    var showExportPreview by remember { mutableStateOf(false) }
    var expandedExpenseId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var showQrDialog by remember { mutableStateOf(false) }
    var selectedGroupTab by remember { mutableStateOf(0) }
    val userProfile by viewModel.userProfile.collectAsState()
    val activityLogs by viewModel.getActivityLogs(group.id).collectAsState(initial = emptyList())

    val groupCurrency = group.expenses.firstOrNull()?.currency ?: viewModel.defaultCurrency
    val netBalances = viewModel.calculateNetBalances(group, groupCurrency)
    val settlements = viewModel.calculateSettlements(group, groupCurrency)
    val totalSpent = group.expenses
        .filter { it.category != ExpenseCategories.SETTLEMENT && !it.title.contains("Settlement", ignoreCase = true) }
        .sumOf { 
            val amt = Currencies.convert(it.amount, it.currency, groupCurrency)
            if (it.category == ExpenseCategories.INCOME || it.amount < 0) -kotlin.math.abs(amt) else kotlin.math.abs(amt)
        }
    val currencySymbol = Currencies.getSymbol(groupCurrency)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(group.name, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.Person, contentDescription = "Members", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            val selfName = viewModel.currentUserName
                            val creatorText = if (group.createdBy.isNotBlank()) {
                                if (group.createdBy.equals(selfName, ignoreCase = true) || group.createdBy == "You") "Created by You" else "Created by ${group.createdBy}"
                            } else null
                            val subtitleText = listOfNotNull("${group.members.size} members", creatorText, group.type).joinToString(" • ")
                            Text(subtitleText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = "QR Invite", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showEditGroupDialog = true }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit Group", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val hasUnsettledBalances = netBalances.any { kotlin.math.abs(it.value) > 0.01 }
                    if (group.isArchived) {
                        OutlinedButton(
                            onClick = {
                                viewModel.unarchiveGroup(group.id)
                                android.widget.Toast.makeText(context, "Group '${group.name}' unarchived", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Rounded.Unarchive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Unarchive", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (!hasUnsettledBalances) {
                        OutlinedButton(
                            onClick = {
                                viewModel.archiveGroup(group.id)
                                android.widget.Toast.makeText(context, "Group '${group.name}' archived", android.widget.Toast.LENGTH_SHORT).show()
                                onBack?.invoke()
                            },
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Archive", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = { showDeleteGroupConfirmDialog = true }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete Group", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddExpense,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Expense")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp)
                    ) {
                        Text("Total Group Expenses", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        AnimatedAmountText(
                            targetAmount = totalSpent,
                            currencySymbol = currencySymbol,
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(14.dp))
                        val userBal = viewModel.getUserNetBalance(group)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    userBal > 0.01 -> EmeraldSuccess.copy(alpha = 0.2f)
                                    userBal < -0.01 -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> EmeraldSuccess.copy(alpha = 0.2f)
                                }
                            ) {
                                Text(
                                    text = when {
                                        userBal > 0.01 -> "Overall: You are owed $currencySymbol${String.format(Locale.US, "%.2f", userBal)}"
                                        userBal < -0.01 -> "Overall: You owe $currencySymbol${String.format(Locale.US, "%.2f", -userBal)}"
                                        else -> "✨ All Square — You are fully settled up!"
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        userBal > 0.01 -> EmeraldSuccess
                                        userBal < -0.01 -> CrimsonDanger
                                        else -> EmeraldSuccess
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Member Balances Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Net Status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        netBalances.forEach { (member, bal) ->
                            val isSelf = member == "You" || member.equals(selfName, ignoreCase = true)
                            val displayName = if (isSelf) "You" else formatNameToFirstAndLastInitial(member, selfName)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MemberAvatar(name = member, size = 28.dp)
                                    Text(displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when {
                                        bal > 0.01 -> EmeraldSuccess.copy(alpha = 0.15f)
                                        bal < -0.01 -> CrimsonDanger.copy(alpha = 0.15f)
                                        else -> EmeraldSuccess.copy(alpha = 0.15f)
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            bal > 0.01 -> "+$currencySymbol${String.format(Locale.US, "%.2f", bal)} (is owed)"
                                            bal < -0.01 -> "-$currencySymbol${String.format(Locale.US, "%.2f", -bal)} (owes)"
                                            else -> "${currencySymbol}0.00 (Fully Settled)"
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = when {
                                            bal > 0.01 -> EmeraldSuccess
                                            bal < -0.01 -> CrimsonDanger
                                            else -> EmeraldSuccess
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onScanReceipt,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan Receipt", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            showExportPreview = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export PDF", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Members (${group.members.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    TextButton(onClick = { showAddMemberDialog = true }) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Member")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val inviteText = "Join my SplitUp group '${group.name}'! Use invite code: ${group.id}"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                setPackage("com.whatsapp")
                                putExtra(Intent.EXTRA_TEXT, inviteText)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, inviteText)
                                }
                                context.startActivity(Intent.createChooser(fallbackIntent, "Share Group Invite"))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share WhatsApp", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val inviteText = "Join my SplitUp group '${group.name}'! Invite link: splitup://group/${group.id}"
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Group Invite", inviteText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Invite link copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Link", fontSize = 12.sp)
                    }
                }
            }

            item {
                val selfName = viewModel.currentUserName
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    group.members.forEach { member ->
                        val isSelf = member == "You" || member.equals(selfName, ignoreCase = true)
                        val isCreator = group.createdBy.isNotBlank() && (member.equals(group.createdBy, ignoreCase = true) || (isSelf && group.createdBy == "You"))
                        val chipLabel = when {
                            isSelf && isCreator -> "You (Creator)"
                            isSelf -> if (member == "You") "You" else "$member (You)"
                            isCreator -> "$member (Creator)"
                            else -> member
                        }
                        AssistChip(
                            onClick = {
                                if (!isSelf) {
                                    memberToRemove = member
                                }
                            },
                            label = { Text(chipLabel, fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                MemberAvatar(name = member, size = 24.dp)
                            },
                            trailingIcon = if (!isSelf) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove Member",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                memberToRemove = member
                                            },
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else null,
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                    group.pendingMembers.forEach { member ->
                        AssistChip(
                            onClick = {
                                memberToRemove = member
                            },
                            label = { Text("$member (Pending)", fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)) },
                            leadingIcon = {
                                MemberAvatar(name = member, size = 24.dp, backgroundColor = MaterialTheme.colorScheme.surfaceVariant)
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove Member",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            memberToRemove = member
                                        },
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        )
                    }
                }
            }

            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    SegmentedButton(
                        selected = selectedGroupTab == 0,
                        onClick = { selectedGroupTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Expenses & Settlements")
                    }
                    SegmentedButton(
                        selected = selectedGroupTab == 1,
                        onClick = { selectedGroupTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Activity Feed (${activityLogs.size})")
                    }
                }
            }

            if (selectedGroupTab == 0) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Settlement Suggestions (\"Who Owes Whom\")", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                if (settlements.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = EmeraldSuccess.copy(alpha = 0.15f))
                        ) {
                            Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("✨ All Members Are Fully Settled Up! 🎉", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = EmeraldSuccess)
                            }
                        }
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    selectedSettlementForPayment = settlements.firstOrNull()
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Rounded.Handshake, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Settle Up", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    val first = group.allMembers.firstOrNull() ?: "You"
                                    val second = group.allMembers.getOrNull(1) ?: first
                                    selectedSettlementForPayment = Settlement(from = first, to = second, amount = 0.0)
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pay Member", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    items(settlements, key = { "${it.from}_${it.to}_${it.amount}" }) { settlement ->
                        val isMyDebt = settlement.from == "You" || settlement.from.equals(selfName, ignoreCase = true)
                        val isOwedToMe = settlement.to == "You" || settlement.to.equals(selfName, ignoreCase = true)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isMyDebt -> MaterialTheme.colorScheme.surface
                                    isOwedToMe -> MaterialTheme.colorScheme.surface
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isMyDebt -> CrimsonDanger.copy(alpha = 0.5f)
                                    isOwedToMe -> EmeraldSuccess.copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        MemberAvatar(name = settlement.from, size = 38.dp)
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                                contentDescription = null,
                                                tint = if (isMyDebt) CrimsonDanger else if (isOwedToMe) EmeraldSuccess else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isMyDebt) CrimsonDanger.copy(alpha = 0.12f) else if (isOwedToMe) EmeraldSuccess.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = "$currencySymbol${String.format(Locale.US, "%.2f", settlement.amount)}",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = if (isMyDebt) CrimsonDanger else if (isOwedToMe) EmeraldSuccess else MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        MemberAvatar(name = settlement.to, size = 38.dp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${formatNameToFirstAndLastInitial(settlement.from, selfName)} → ${formatNameToFirstAndLastInitial(settlement.to, selfName)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when {
                                                isMyDebt -> CrimsonDanger.copy(alpha = 0.12f)
                                                isOwedToMe -> EmeraldSuccess.copy(alpha = 0.12f)
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        ) {
                                            Text(
                                                text = when {
                                                    isMyDebt -> "You Owe"
                                                    isOwedToMe -> "Owed to You"
                                                    else -> "Peer Transfer"
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    isMyDebt -> CrimsonDanger
                                                    isOwedToMe -> EmeraldSuccess
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                }
                                if (isMyDebt) {
                                    Button(
                                        onClick = {
                                            selectedSettlementForPayment = settlement
                                        },
                                        modifier = Modifier.fillMaxWidth().height(42.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Icon(Icons.Rounded.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Settle Up Now", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else if (isOwedToMe) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = EmeraldSuccess.copy(alpha = 0.15f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = EmeraldSuccess)
                                            Text(
                                                text = "Pending payment from ${formatNameToFirstAndLastInitial(settlement.from, selfName)} • Owed to you",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = EmeraldSuccess
                                            )
                                        }
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = "Transfer between members • No action required from you",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Expense History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                if (group.expenses.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No shared expenses yet. Tap + or Scan Receipt to add one!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(group.expenses, key = { it.id }) { expense ->
                        val detectedCat = detectCategoryFromTitle(expense.title)
                        val categoryIcon = when (detectedCat) {
                            com.finance.splitup.data.ExpenseCategories.FOOD -> Icons.Rounded.Restaurant
                            com.finance.splitup.data.ExpenseCategories.TRANSPORT -> Icons.Rounded.DirectionsCar
                            com.finance.splitup.data.ExpenseCategories.SHOPPING -> Icons.Rounded.ShoppingBag
                            com.finance.splitup.data.ExpenseCategories.BILLS -> Icons.AutoMirrored.Rounded.ReceiptLong
                            com.finance.splitup.data.ExpenseCategories.ENTERTAINMENT -> Icons.Rounded.Movie
                            else -> Icons.Rounded.Category
                        }

                        val isExpanded = expandedExpenseId == expense.id
                        val isCustomSplit = expense.splitMode == "CUSTOM"
                        val isSettlement = expense.category == ExpenseCategories.SETTLEMENT || expense.title.contains("Settlement", ignoreCase = true)

                        val isPayer = expense.paidBy == "You" || expense.paidBy.equals("You", ignoreCase = true) || (selfName.isNotBlank() && expense.paidBy.equals(selfName, ignoreCase = true))

                        val myShare: Double = if (isCustomSplit && expense.memberSplits.isNotEmpty()) {
                            expense.memberSplits.entries.firstOrNull { 
                                it.key == "You" || it.key.equals("You", ignoreCase = true) || (selfName.isNotBlank() && it.key.equals(selfName, ignoreCase = true)) 
                            }?.value ?: 0.0
                        } else {
                            val isInSplit = expense.splitAmong.any { 
                                it == "You" || it.equals("You", ignoreCase = true) || (selfName.isNotBlank() && it.equals(selfName, ignoreCase = true)) 
                            }
                            if (isInSplit && expense.splitAmong.isNotEmpty()) expense.amount / expense.splitAmong.size else 0.0
                        }

                        val currencySym = Currencies.getSymbol(expense.currency)

                        SwipeableItemContainer(
                            onDelete = { viewModel.deleteGroupExpense(group.id, expense.id) },
                            onEdit = { onEditGroupExpense(group.id, expense.id) }
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedExpenseId = if (isExpanded) null else expense.id
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(imageVector = categoryIcon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                                }
                                            }
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(expense.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                    if (isCustomSplit) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                                        ) {
                                                            Text(
                                                                text = "Custom",
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                val formattedPaidBy = formatNameToFirstAndLastInitial(expense.paidBy, selfName)
                                                val formattedSplit = expense.splitAmong.map { formatNameToFirstAndLastInitial(it, selfName) }.joinToString(", ")
                                                Text(
                                                    text = "Paid by $formattedPaidBy • Split: $formattedSplit",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                val displayDate = com.finance.splitup.data.DateUtils.formatDisplayDate(expense.date)
                                                Text(
                                                    text = displayDate,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "$currencySym${String.format(Locale.US, "%.2f", expense.amount)}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = when {
                                                    expense.category == ExpenseCategories.INCOME -> EmeraldSuccess
                                                    isSettlement -> MaterialTheme.colorScheme.primary
                                                    else -> CrimsonDanger
                                                }
                                            )
                                            if (!isSettlement) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isPayer) EmeraldSuccess.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                                ) {
                                                    Text(
                                                        text = if (isPayer) "You paid $currencySym${String.format(Locale.US, "%.2f", expense.amount)}" else "Your share: $currencySym${String.format(Locale.US, "%.2f", myShare)}",
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = if (isPayer) EmeraldSuccess else MaterialTheme.colorScheme.onSecondaryContainer
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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.clickable {
                                                expandedExpenseId = if (isExpanded) null else expense.id
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = if (isExpanded) "Hide breakdown" else "View itemized split",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Row {
                                            IconButton(onClick = { onEditGroupExpense(group.id, expense.id) }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(onClick = { viewModel.deleteGroupExpense(group.id, expense.id) }, modifier = Modifier.size(32.dp)) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (isExpanded) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                        Text("Itemized Member Split Breakdown:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        
                                        val memberShares: Map<String, Double> = if (isCustomSplit && expense.memberSplits.isNotEmpty()) {
                                            expense.memberSplits
                                        } else {
                                            val perPerson = if (expense.splitAmong.isNotEmpty()) expense.amount / expense.splitAmong.size else 0.0
                                            expense.splitAmong.associateWith { perPerson }
                                        }

                                        memberShares.forEach { (member, share) ->
                                            val isGuest = !group.members.any { it.equals(member, ignoreCase = true) || (member == "You" && it.equals(selfName, ignoreCase = true)) }
                                            val isMe = member == "You" || member.equals(selfName, ignoreCase = true)
                                            val displayName = if (isMe) "You" else formatNameToFirstAndLastInitial(member, selfName)
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    MemberAvatar(name = member, size = 22.dp)
                                                    Text(displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal)
                                                    if (isGuest) {
                                                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                                            Text("Guest", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = "$currencySym${String.format(Locale.US, "%.2f", share)}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Group Activity Stream", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                if (activityLogs.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("No activity recorded yet for this group.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(activityLogs, key = { it.id }) { log ->
                        val logIcon = when (log.actionType) {
                            "MEMBER_ADDED" -> Icons.Rounded.PersonAdd
                            "MEMBER_REMOVED" -> Icons.Rounded.PersonRemove
                            "EXPENSE_ADDED" -> Icons.Rounded.AddCircle
                            "EXPENSE_DELETED" -> Icons.Rounded.Delete
                            "EXPENSE_UPDATED" -> Icons.Rounded.Edit
                            else -> Icons.Rounded.History
                        }
                        val timeFormatted = remember(log.timestamp) {
                            try {
                                val sdf = java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.US)
                                sdf.format(java.util.Date(log.timestamp))
                            } catch (_: Exception) {
                                "Recently"
                            }
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(imageVector = logIcon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = log.description, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(text = "By ${log.actorName} • $timeFormatted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

                                    val canUndo = log.actionType == "EXPENSE_ADDED" && log.referenceId.isNotBlank() && group.expenses.any { it.id == log.referenceId }
                                    if (canUndo) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.deleteGroupExpense(group.id, log.referenceId)
                                                android.widget.Toast.makeText(context, "Expense action undone", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.height(30.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Undo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditGroupDialog) {
        var editGroupName by remember { mutableStateOf(group.name) }
        var editGroupType by remember { mutableStateOf(group.type) }
        var editMembersInput by remember { mutableStateOf(group.members.joinToString(", ")) }

        AlertDialog(
            onDismissRequest = { showEditGroupDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = when (editGroupType) {
                            "Trip" -> Icons.Rounded.Flight
                            "Roommates" -> Icons.Rounded.Home
                            "Dinner" -> Icons.Rounded.Restaurant
                            else -> Icons.Rounded.Category
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Edit Group", fontWeight = FontWeight.SemiBold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editGroupName,
                        onValueChange = { editGroupName = it },
                        label = { Text("Group Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text("Group Type", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Trip", "Roommates", "Dinner", "Other").forEach { type ->
                            FilterChip(
                                selected = editGroupType == type,
                                onClick = { editGroupType = type },
                                label = { Text(type, fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (type) {
                                            "Trip" -> Icons.Rounded.Flight
                                            "Roommates" -> Icons.Rounded.Home
                                            "Dinner" -> Icons.Rounded.Restaurant
                                            else -> Icons.Rounded.Category
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editMembersInput,
                        onValueChange = { editMembersInput = it },
                        label = { Text("Members (comma separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (editGroupName.isNotBlank()) {
                        val membersList = editMembersInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        viewModel.updateGroup(group.id, editGroupName, editGroupType, membersList)
                        showEditGroupDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditGroupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (editingGroupExpense != null) {
        val exp = editingGroupExpense!!
        var editTitle by remember { mutableStateOf(exp.title) }
        var editAmount by remember { mutableStateOf(formatInputNumber(exp.amount)) }
        var editPaidBy by remember { mutableStateOf(exp.paidBy) }

        AlertDialog(
            onDismissRequest = { editingGroupExpense = null },
            title = { Text("Edit Shared Expense", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = editAmount,
                        onValueChange = { editAmount = it },
                        label = { Text("Amount") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = editPaidBy,
                        onValueChange = { editPaidBy = it },
                        label = { Text("Paid By") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = editAmount.parseAmount()
                    if (editTitle.isNotBlank() && amt > 0.0) {
                        val updated = exp.copy(
                            title = editTitle,
                            amount = amt,
                            paidBy = editPaidBy.ifBlank { "You" }
                        )
                        viewModel.updateGroupExpense(group.id, updated)
                        editingGroupExpense = null
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingGroupExpense = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showQrDialog) {
        GroupQrCodeDialog(
            group = group,
            onDismiss = { showQrDialog = false }
        )
    }

    if (showAddMemberDialog) {
        MemberSearchAndAddDialog(
            group = group,
            viewModel = viewModel,
            onDismiss = { showAddMemberDialog = false },
            onAddMember = { memberName, memberEmail, isPending ->
                viewModel.addGroupMember(group.id, memberName, memberEmail = memberEmail, isPending = isPending)
                showAddMemberDialog = false
                if (isPending) {
                    customMemberForWhatsAppInvite = memberName
                }
            }
        )
    }

    if (showDeleteGroupConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteGroupConfirmDialog = false },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            },
            title = {
                Text("Delete Group '${group.name}'?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to delete '${group.name}'? All shared expenses and member balances will be removed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteGroupConfirmDialog = false
                        viewModel.deleteGroup(group.id)
                        onBack?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Delete Group")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (customMemberForWhatsAppInvite != null) {
        WhatsAppInviteDialog(
            memberName = customMemberForWhatsAppInvite!!,
            groupName = group.name,
            groupId = group.id,
            onDismiss = { customMemberForWhatsAppInvite = null }
        )
    }

    if (selectedSettlementForPayment != null) {
        val s = selectedSettlementForPayment!!
        var customPayer by remember(s) { mutableStateOf(s.from) }
        var customPayee by remember(s) { mutableStateOf(s.to) }
        var cashAmountInput by remember(s) { mutableStateOf(formatInputNumber(s.amount)) }

        AlertDialog(
            onDismissRequest = { selectedSettlementForPayment = null },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Payment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Payment: $customPayer → $customPayee",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Payer Selection
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Payer (Who pays):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            group.allMembers.forEach { member ->
                                FilterChip(
                                    selected = customPayer == member,
                                    onClick = { customPayer = member },
                                    label = { Text(member, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    // Payee Selection
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Payee (Who gets paid):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            group.allMembers.forEach { member ->
                                FilterChip(
                                    selected = customPayee == member,
                                    onClick = { customPayee = member },
                                    label = { Text(member, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Payment Amount", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = cashAmountInput,
                                onValueChange = { cashAmountInput = it },
                                label = { Text("Amount ($currencySymbol)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                    }

                    // Option 1: Confirm Cash Payment / Custom Payment
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val paidAmt = cashAmountInput.parseAmount()
                                if (paidAmt > 0.0) {
                                    viewModel.addGroupExpense(
                                        groupId = group.id,
                                        title = "Payment: $customPayer to $customPayee",
                                        amount = paidAmt,
                                        paidBy = customPayer,
                                        splitAmong = listOf(customPayee),
                                        date = "Today",
                                        currency = group.expenses.firstOrNull()?.currency ?: viewModel.defaultCurrency,
                                        splitMode = "EQUAL",
                                        memberSplits = emptyMap(),
                                        category = ExpenseCategories.SETTLEMENT
                                    )
                                    Toast.makeText(context, "Payment of $currencySymbol${String.format(Locale.US, "%.2f", paidAmt)} from $customPayer to $customPayee recorded!", Toast.LENGTH_SHORT).show()
                                    selectedSettlementForPayment = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Rounded.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Confirm Payment", fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider()

                    // Option 2: Payment via App (PayPal, Venmo, Revolut, UPI)
                    // Target the payee member (customPayee / s.to), NOT the logged-in debtor's own userProfile
                    val registeredUsersList by viewModel.registeredUsers.collectAsState()
                    val payeeContact = registeredUsersList.find { it.name.equals(customPayee, ignoreCase = true) }
                    val isSelfPayee = customPayee.equals(viewModel.currentUserName, ignoreCase = true) || customPayee == "You"

                    val paypalHandle = if (isSelfPayee) userProfile.paypalHandle.trim().removePrefix("@") else payeeContact?.paypalHandle?.trim()?.removePrefix("@") ?: ""
                    val venmoHandle = if (isSelfPayee) userProfile.venmoHandle.trim().removePrefix("@") else payeeContact?.venmoHandle?.trim()?.removePrefix("@") ?: ""
                    val revolutHandle = if (isSelfPayee) userProfile.revolutHandle.trim().removePrefix("@") else payeeContact?.revolutHandle?.trim()?.removePrefix("@") ?: ""
                    val upiVpa = if (isSelfPayee) userProfile.upiVpa.trim() else payeeContact?.upiVpa?.trim() ?: ""
                    val groupCurr = group.expenses.firstOrNull()?.currency ?: viewModel.defaultCurrency
                    val upiCurr = if (groupCurr.contains("INR") || groupCurr.contains("₹")) "INR" else "EUR"

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("2. Direct App Payment to $customPayee", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Launch payment app for $customPayee:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Button(
                            onClick = {
                                val paidAmt = cashAmountInput.parseAmount()
                                val launched = PaymentDeepLinkHelper.launchPaypalPayment(
                                    context = context,
                                    paypalHandle = paypalHandle,
                                    amount = paidAmt,
                                    payeeName = customPayee,
                                    currencySymbol = groupCurr
                                )
                                if (launched && paidAmt > 0.0) {
                                    viewModel.addGroupExpense(
                                        groupId = group.id,
                                        title = "Payment: $customPayer to $customPayee",
                                        amount = paidAmt,
                                        paidBy = customPayer,
                                        splitAmong = listOf(customPayee),
                                        date = "Today",
                                        currency = groupCurr,
                                        splitMode = "EQUAL",
                                        memberSplits = emptyMap(),
                                        category = ExpenseCategories.SETTLEMENT
                                    )
                                    selectedSettlementForPayment = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) {
                            Icon(Icons.Rounded.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pay with PayPal", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val paidAmt = cashAmountInput.parseAmount()
                                val launched = PaymentDeepLinkHelper.launchVenmoPayment(
                                    context = context,
                                    venmoHandle = venmoHandle,
                                    amount = paidAmt,
                                    payeeName = customPayee,
                                    note = "SplitUp Settlement"
                                )
                                if (launched && paidAmt > 0.0) {
                                    viewModel.addGroupExpense(
                                        groupId = group.id,
                                        title = "Payment: $customPayer to $customPayee",
                                        amount = paidAmt,
                                        paidBy = customPayer,
                                        splitAmong = listOf(customPayee),
                                        date = "Today",
                                        currency = groupCurr,
                                        splitMode = "EQUAL",
                                        memberSplits = emptyMap(),
                                        category = ExpenseCategories.SETTLEMENT
                                    )
                                    selectedSettlementForPayment = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) {
                            Icon(Icons.Rounded.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pay with Venmo", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val paidAmt = cashAmountInput.parseAmount()
                                val launched = PaymentDeepLinkHelper.launchRevolutPayment(
                                    context = context,
                                    revolutTag = revolutHandle,
                                    payeeName = customPayee
                                )
                                if (launched && paidAmt > 0.0) {
                                    viewModel.addGroupExpense(
                                        groupId = group.id,
                                        title = "Payment: $customPayer to $customPayee",
                                        amount = paidAmt,
                                        paidBy = customPayer,
                                        splitAmong = listOf(customPayee),
                                        date = "Today",
                                        currency = groupCurr,
                                        splitMode = "EQUAL",
                                        memberSplits = emptyMap(),
                                        category = ExpenseCategories.SETTLEMENT
                                    )
                                    selectedSettlementForPayment = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) {
                            Icon(Icons.Rounded.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pay with Revolut", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val paidAmt = cashAmountInput.parseAmount()
                                val launched = PaymentDeepLinkHelper.launchUpiPayment(
                                    context = context,
                                    vpa = upiVpa,
                                    payeeName = customPayee,
                                    amount = paidAmt,
                                    currency = upiCurr
                                )
                                if (launched && paidAmt > 0.0) {
                                    viewModel.addGroupExpense(
                                        groupId = group.id,
                                        title = "Payment: $customPayer to $customPayee",
                                        amount = paidAmt,
                                        paidBy = customPayer,
                                        splitAmong = listOf(customPayee),
                                        date = "Today",
                                        currency = groupCurr,
                                        splitMode = "EQUAL",
                                        memberSplits = emptyMap(),
                                        category = ExpenseCategories.SETTLEMENT
                                    )
                                    selectedSettlementForPayment = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Icon(Icons.Rounded.Smartphone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pay via UPI / Banking App", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedSettlementForPayment = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (memberToRemove != null) {
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Remove Member", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove '${memberToRemove}' from ${group.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeGroupMember(group.id, memberToRemove!!)
                        memberToRemove = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExportPreview) {
        ExportPdfPreviewDialog(
            group = group,
            netBalances = netBalances,
            settlements = settlements,
            onDismiss = { showExportPreview = false },
            onConfirmExport = {
                try {
                    val pdfFile = com.finance.splitup.data.PdfGenerator.generateGroupStatementPdf(context, group, netBalances, settlements)
                    if (pdfFile != null) {
                        viewModel.postSystemNotification(
                            "PDF Statement Generated 📄",
                            "Group statement for '${group.name}' is ready to view.",
                            com.finance.splitup.viewmodel.FinanceViewModel.CHANNEL_EXPORTS_INSIGHTS
                        )
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_SUBJECT, "Group Statement PDF - ${group.name}")
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Export Group PDF Statement"))
                    } else {
                        Toast.makeText(context, "Failed to generate group statement PDF", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "PDF export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MemberSearchAndAddDialog(
    group: Group,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    onAddMember: (String, String, Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val contactPermissionState = rememberPermissionState(permission = android.Manifest.permission.READ_CONTACTS)
    val recentFriends by viewModel.recentFriends.collectAsState()
    var cloudSearchedContact by remember { mutableStateOf<com.finance.splitup.data.AppContact?>(null) }
    var isSearchingCloud by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.contains("@") && searchQuery.length > 3) {
            isSearchingCloud = true
            val cloudUser = viewModel.searchCloudUserByEmail(searchQuery)
            cloudSearchedContact = cloudUser
            isSearchingCloud = false
        } else {
            cloudSearchedContact = null
        }
    }

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

    val selfName = viewModel.currentUserName.lowercase()
    val selfEmail = viewModel.currentUserEmail.lowercase()
    val existingMembers = remember(group, selfName, selfEmail) {
        (group.members + group.pendingMembers + listOf("you", selfName)).map { it.lowercase() } + group.memberEmails.map { it.lowercase() } + listOf(selfEmail)
    }

    val recentFiltered = remember(recentFriends, existingMembers) {
        recentFriends.filter { !existingMembers.contains(it.name.lowercase()) && !existingMembers.contains(it.email.lowercase()) }
    }

    val filteredContacts = remember(searchQuery, existingMembers, allContacts) {
        val matches = if (searchQuery.isBlank()) {
            allContacts
        } else {
            allContacts.filter { contact ->
                contact.name.contains(searchQuery, ignoreCase = true) ||
                        contact.email.contains(searchQuery, ignoreCase = true)
            }
        }
        matches.sortedWith(
            compareByDescending<com.finance.splitup.data.AppContact> { contact ->
                existingMembers.contains(contact.name.lowercase()) || existingMembers.contains(contact.email.lowercase())
            }.thenByDescending { contact ->
                contact.isSplitUpMember
            }.thenBy { contact ->
                contact.name.lowercase()
            }
        )
    }

    val isExactMatch = filteredContacts.any { it.name.equals(searchQuery.trim(), ignoreCase = true) || it.email.equals(searchQuery.trim(), ignoreCase = true) }
    val isCustomSelfOrExisting = searchQuery.isNotBlank() && (
        existingMembers.contains(searchQuery.trim().lowercase()) ||
        searchQuery.trim().equals("you", ignoreCase = true) ||
        searchQuery.trim().equals(selfName, ignoreCase = true) ||
        (searchQuery.contains("@") && searchQuery.trim().lowercase() == selfEmail)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Group Member", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search by Name or Email...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                            }
                        }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (searchQuery.isBlank() && recentFiltered.isNotEmpty()) {
                        item {
                            Text("Recent Friends ⏱️", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        items(recentFiltered, key = { "recent_${it.email}" }) { contact ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addRecentFriend(contact.name, contact.email)
                                        onAddMember(contact.name, contact.email, false)
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("${contact.email} • Recent Friend", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.addRecentFriend(contact.name, contact.email)
                                            onAddMember(contact.name, contact.email, false)
                                        },
                                        modifier = Modifier.height(36.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Add", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Device Contacts 📱", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (cloudSearchedContact != null) {
                        val isCloudUserSelfOrExisting = existingMembers.contains(cloudSearchedContact!!.name.lowercase()) || existingMembers.contains(cloudSearchedContact!!.email.lowercase())
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!isCloudUserSelfOrExisting) {
                                            viewModel.addRecentFriend(cloudSearchedContact!!.name, cloudSearchedContact!!.email)
                                            onAddMember(cloudSearchedContact!!.name, cloudSearchedContact!!.email, false)
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("✨ Found on SplitUp: ${cloudSearchedContact!!.name}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text(cloudSearchedContact!!.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                    if (isCloudUserSelfOrExisting) {
                                        Text("Already in Group", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    } else {
                                        Button(
                                            onClick = {
                                                viewModel.addRecentFriend(cloudSearchedContact!!.name, cloudSearchedContact!!.email)
                                                onAddMember(cloudSearchedContact!!.name, cloudSearchedContact!!.email, false)
                                            },
                                            modifier = Modifier.height(36.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Add", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(filteredContacts, key = { it.email }) { contact ->
                        val isAlreadyMember = existingMembers.contains(contact.name.lowercase()) || existingMembers.contains(contact.email.lowercase())

                        val backgroundColor = when {
                            isAlreadyMember -> MaterialTheme.colorScheme.secondaryContainer
                            contact.isSplitUpMember -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isAlreadyMember) {
                                        if (contact.isSplitUpMember) {
                                            viewModel.addRecentFriend(contact.name, contact.email)
                                        }
                                        onAddMember(contact.name, contact.email, !contact.isSplitUpMember)
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = backgroundColor,
                            border = if (contact.isSplitUpMember && !isAlreadyMember) {
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
                                        Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        if (isAlreadyMember) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = com.finance.splitup.ui.theme.EmeraldSuccess.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    "Already in Group",
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
                                    val subText = if (contact.isSplitUpMember) "SplitUp Member • Directly Added" else "${contact.email} • Not on SplitUp (WhatsApp Invite)"
                                    Text(subText, style = MaterialTheme.typography.bodySmall, color = if (contact.isSplitUpMember && !isAlreadyMember) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isAlreadyMember) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = com.finance.splitup.ui.theme.EmeraldSuccess.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            "Member",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = com.finance.splitup.ui.theme.EmeraldSuccess,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (contact.isSplitUpMember) {
                                                viewModel.addRecentFriend(contact.name, contact.email)
                                            }
                                            onAddMember(contact.name, contact.email, !contact.isSplitUpMember)
                                        },
                                        modifier = Modifier.height(36.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (contact.isSplitUpMember) "Add" else "Invite", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (searchQuery.isNotBlank() && !isExactMatch) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!isCustomSelfOrExisting) {
                                            val customEmail = if (searchQuery.contains("@")) searchQuery.trim() else ""
                                            onAddMember(searchQuery.trim(), customEmail, true)
                                        }
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
                                        Text("Add '${searchQuery.trim()}'", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Text(if (isCustomSelfOrExisting) "Already in group / Cannot add yourself" else "Custom Member • Generates WhatsApp Invite", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                    }
                                    if (!isCustomSelfOrExisting) {
                                        Button(
                                            onClick = {
                                                val customEmail = if (searchQuery.contains("@")) searchQuery.trim() else ""
                                                onAddMember(searchQuery.trim(), customEmail, true)
                                            },
                                            modifier = Modifier.height(36.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Add & Invite", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GroupQrCodeDialog(
    group: Group,
    onDismiss: () -> Unit
) {
    val inviteUrl = "splitup://invite?groupId=${group.id}"
    val qrBitmap = remember(inviteUrl) {
        try {
            val writer = QRCodeWriter()
            val hints = mapOf<EncodeHintType, Any>(EncodeHintType.MARGIN to 1)
            val bitMatrix = writer.encode(inviteUrl, BarcodeFormat.QR_CODE, 256, 256, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    val isSet = bitMatrix.get(x, y)
                    bmp.setPixel(x, y, if (isSet) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Group Invite QR Code", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Scan with Google Lens or Camera to join '${group.name}' in SplitUp:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(200.dp).padding(8.dp),
                    shadowElevation = 4.dp
                ) {
                    if (qrBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Group QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Failed to generate QR", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = inviteUrl,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ExportPdfPreviewDialog(
    group: Group,
    netBalances: Map<String, Double>,
    settlements: List<Settlement>,
    onDismiss: () -> Unit,
    onConfirmExport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("PDF Statement Preview", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Live Preview Summary for '${group.name}':", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Active Members:", fontWeight = FontWeight.Medium)
                            Text("${group.allMembers.size} members", fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Pending Settlements:", fontWeight = FontWeight.Medium)
                            Text("${settlements.size} transfers", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Text("This will generate a formatted, printable PDF statement containing all expense breakdowns, balances, and optimized debt settlements.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onConfirmExport()
                }
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Generate & Share PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatNameToFirstAndLastInitial(name: String, currentUserName: String = ""): String {
    if (name.isBlank()) return name
    if (name.equals("You", ignoreCase = true) || (currentUserName.isNotBlank() && name.equals(currentUserName, ignoreCase = true))) {
        return "You"
    }
    val parts = name.trim().split("\\s+".toRegex())
    if (parts.size == 1) return parts[0]
    val firstName = parts[0]
    val lastInitial = parts.last().first().uppercaseChar()
    return "$firstName $lastInitial."
}


