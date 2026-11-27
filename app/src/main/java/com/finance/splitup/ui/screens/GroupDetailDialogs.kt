package com.finance.splitup.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.data.Group
import com.finance.splitup.data.GroupExpense
import com.finance.splitup.data.PaymentDeepLinkHelper
import com.finance.splitup.data.Settlement
import com.finance.splitup.data.UiMessage
import com.finance.splitup.ui.theme.SplitUpType
import com.finance.splitup.viewmodel.FinanceViewModel
import java.util.Locale

/**
 * Dialogs extracted from GroupDetailScreen to keep that file focused on the
 * group detail layout itself. Behavior is moved verbatim — only state that
 * lived in the screen (dialog visibility flags) is parameterized here.
 */

@Composable
fun EditGroupDetailsDialog(
    group: Group,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    var editGroupName by remember { mutableStateOf(group.name) }
    var editGroupType by remember { mutableStateOf(group.type) }
    var editMembersInput by remember { mutableStateOf(group.members.joinToString(", ")) }
    var editRequireApproval by remember { mutableStateOf(group.requireApproval) }
    val isCreator = viewModel.isGroupOwner(group)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
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

                Text("Group Type", style = SplitUpType.ItemTitleMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Trip", "Roommates", "Dinner", "Other").forEach { type ->
                        FilterChip(
                            selected = editGroupType == type,
                            onClick = { editGroupType = type },
                            label = { Text(type, style = SplitUpType.Caption) },
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

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Require Owner Approval", style = SplitUpType.BadgeLabel)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (isCreator) "Require owner approval when non-owners edit/delete expenses or remove members" else "Only group creator (${group.createdBy.ifBlank { "Creator" }}) can change this setting",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = editRequireApproval,
                            onCheckedChange = { if (isCreator) editRequireApproval = it },
                            enabled = isCreator
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (editGroupName.isNotBlank()) {
                    val membersList = editMembersInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    viewModel.updateGroup(group.id, editGroupName, editGroupType, membersList, requireApproval = editRequireApproval)
                    onDismiss()
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditGroupExpenseDialog(
    groupId: String,
    expense: GroupExpense,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    var editTitle by remember { mutableStateOf(expense.title) }
    var editAmount by remember { mutableStateOf(formatInputNumber(expense.amount)) }
    var editPaidBy by remember { mutableStateOf(expense.paidBy) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                    val updated = expense.copy(
                        title = editTitle,
                        amount = amt,
                        paidBy = editPaidBy.ifBlank { "You" }
                    )
                    viewModel.updateGroupExpense(groupId, updated)
                    onDismiss()
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteGroupConfirmDialog(
    group: Group,
    viewModel: FinanceViewModel,
    onDeleted: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                    onDismiss()
                    viewModel.deleteGroup(group.id)
                    onDeleted()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text("Delete Group")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SettlementPaymentDialog(
    group: Group,
    viewModel: FinanceViewModel,
    settlement: Settlement,
    currencySymbol: String,
    onResult: (isPendingApproval: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    var customPayer by remember(settlement) { mutableStateOf(settlement.from) }
    var customPayee by remember(settlement) { mutableStateOf(settlement.to) }
    var cashAmountInput by remember(settlement) { mutableStateOf(formatInputNumber(settlement.amount)) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
            Text(text = "Payment: $customPayer → $customPayee", style = SplitUpType.SectionTitle)
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
                                label = { Text(member, style = SplitUpType.Caption) }
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
                                label = { Text(member, style = SplitUpType.Caption) }
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

                val groupCurr = group.expenses.firstOrNull()?.currency ?: viewModel.defaultCurrency
                val upiCurr = if (groupCurr.contains("INR") || groupCurr.contains("₹")) "INR" else "EUR"

                fun handleExecuteSettlement(paidAmt: Double, curr: String) {
                    if (paidAmt <= 0.0) return
                    viewModel.recordSettlementPayment(
                        group = group,
                        payer = customPayer,
                        payee = customPayee,
                        amount = paidAmt,
                        currency = curr,
                        onResult = { isPending ->
                            if (isPending) {
                                UiMessage.showLong("Settlement payment request sent to $customPayee for approval! ✉️")
                            } else {
                                UiMessage.show("Payment of $currencySymbol${String.format(Locale.US, "%.2f", paidAmt)} from $customPayer to $customPayee recorded!")
                            }
                            onResult(isPending)
                            onDismiss()
                        }
                    )
                }

                // Option 1: Confirm Cash Payment / Custom Payment
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val paidAmt = cashAmountInput.parseAmount()
                            handleExecuteSettlement(paidAmt, groupCurr)
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
                // Target the payee member (customPayee / settlement.to), NOT the logged-in debtor's own userProfile
                val registeredUsersList by viewModel.registeredUsers.collectAsState()
                val payeeContact = registeredUsersList.find { it.name.equals(customPayee, ignoreCase = true) }
                val isSelfPayee = customPayee.equals(viewModel.currentUserName, ignoreCase = true) || customPayee == "You"

                val paypalHandle = if (isSelfPayee) userProfile.paypalHandle.trim().removePrefix("@") else payeeContact?.paypalHandle?.trim()?.removePrefix("@") ?: ""
                val venmoHandle = if (isSelfPayee) userProfile.venmoHandle.trim().removePrefix("@") else payeeContact?.venmoHandle?.trim()?.removePrefix("@") ?: ""
                val revolutHandle = if (isSelfPayee) userProfile.revolutHandle.trim().removePrefix("@") else payeeContact?.revolutHandle?.trim()?.removePrefix("@") ?: ""
                val upiVpa = if (isSelfPayee) userProfile.upiVpa.trim() else payeeContact?.upiVpa?.trim() ?: ""

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("2. Direct App Payment to $customPayee", style = SplitUpType.RowTitleMedium)
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
                                handleExecuteSettlement(paidAmt, groupCurr)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Icon(Icons.Rounded.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pay with PayPal", style = SplitUpType.ItemTitle)
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
                                handleExecuteSettlement(paidAmt, groupCurr)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Icon(Icons.Rounded.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pay with Venmo", style = SplitUpType.ItemTitle)
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
                                handleExecuteSettlement(paidAmt, groupCurr)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Icon(Icons.Rounded.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pay with Revolut", style = SplitUpType.ItemTitle)
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
                                handleExecuteSettlement(paidAmt, upiCurr)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    ) {
                        Icon(Icons.Rounded.Smartphone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pay via UPI / Banking App", style = SplitUpType.ItemTitle)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun SettlementCelebrationDialog(
    isSettlementPendingApproval: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                var emojiShown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { emojiShown = true }
                val emojiScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (emojiShown) 1f else 0.3f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                    ),
                    label = "celebrationEmojiScale"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ConfettiBurst(modifier = Modifier.fillMaxSize())
                    Text(
                        if (isSettlementPendingApproval) "✉️" else "🎉",
                        fontSize = 48.sp,
                        modifier = Modifier.graphicsLayer {
                            scaleX = emojiScale
                            scaleY = emojiScale
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(if (isSettlementPendingApproval) "Settlement Request Sent!" else "Settlement Recorded!", style = SplitUpType.ScreenSubtitleStrong)
            }
        },
        text = {
            Text(
                if (isSettlementPendingApproval)
                    "Payment request sent to receiver for approval. Group balances will update automatically once confirmed!"
                else
                    "Debt successfully settled and recorded to group expenses. Outstanding balances have been updated.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(if (isSettlementPendingApproval) "Got It!" else "Awesome!", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun RemoveMemberConfirmDialog(
    group: Group,
    member: Pair<String, String>,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove Member", fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to remove '${member.first}' from ${group.name}?") },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.removeGroupMember(group.id, member.first, member.second)
                    onDismiss()
                }
            ) {
                Text("Remove", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
