package com.finance.splitup.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.data.Currencies
import com.finance.splitup.data.ExpenseCategories
import com.finance.splitup.viewmodel.FinanceViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptAllocationScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit,
    onExpenseAdded: () -> Unit
) {
    val extracted = viewModel.currentExtractedReceipt ?: com.finance.splitup.data.SampleReceipts.sampleWholeFoods
    val groups = viewModel.groups.collectAsState().value
    val currencySymbol = Currencies.getSymbol(extracted.currency)

    var isPersonalExpense by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(ExpenseCategories.FOOD) }

    var selectedGroup by remember {
        mutableStateOf(
            groups.firstOrNull { it.id == viewModel.targetGroupIdForReceipt } ?: groups.firstOrNull()
        )
    }

    var selectedPayer by remember { mutableStateOf("You") }
    var expenseDate by remember(extracted) { mutableStateOf(extracted.date.ifBlank { "Today" }) }
    var splitModeEqual by remember { mutableStateOf(true) }
    var tipPercent by remember { mutableStateOf(0) }
    var extraTaxAmount by remember { mutableStateOf("") }

    val baseAmount = extracted.totalAmount
    val taxValue = extraTaxAmount.parseAmount()
    val calculatedTotal = kotlin.math.round((baseAmount * (1.0 + tipPercent / 100.0) + taxValue) * 100) / 100.0

    var itemAssignments by remember {
        mutableStateOf(
            extracted.lineItems.associate { item ->
                item.id to (selectedGroup?.members ?: listOf("You"))
            }
        )
    }

    LaunchedEffect(selectedGroup) {
        selectedGroup?.let { group ->
            itemAssignments = extracted.lineItems.associate { item ->
                item.id to group.members
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receipt Cost Allocation", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
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
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = extracted.merchantName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(text = "Base Total: $currencySymbol${String.format(Locale.US, "%.2f", extracted.totalAmount)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    Text(text = "Final Total: $currencySymbol${String.format(Locale.US, "%.2f", calculatedTotal)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            // Expense Type Selector (Personal vs Group)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Expense Type", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = isPersonalExpense,
                        onClick = { isPersonalExpense = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Personal")
                    }
                    SegmentedButton(
                        selected = !isPersonalExpense,
                        onClick = { isPersonalExpense = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Group")
                    }
                }
            }

            // Tip & Tax Calculator Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tip & Tax Calculator", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0, 10, 15, 20).forEach { pct ->
                            FilterChip(
                                selected = tipPercent == pct,
                                onClick = { tipPercent = pct },
                                label = { Text("$pct% Tip") },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = extraTaxAmount,
                        onValueChange = { extraTaxAmount = it },
                        label = { Text("Extra Tax / Fees ($currencySymbol)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            if (isPersonalExpense) {
                Text("Category", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ExpenseCategories.all) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Expense Date", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    DatePickerFieldToModal(
                        selectedDateText = expenseDate,
                        onDateSelected = { expenseDate = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Expense Date"
                    )
                }

                Text("Extracted Line Items", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            } else {
                Text("Select Group", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groups) { group ->
                        val isSelected = selectedGroup?.id == group.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedGroup = group },
                            label = { Text(group.name) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Expense Date", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    DatePickerFieldToModal(
                        selectedDateText = expenseDate,
                        onDateSelected = { expenseDate = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Expense Date"
                    )
                }

                Text("Paid By", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                selectedGroup?.let { group ->
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(group.members) { member ->
                            val isSelected = selectedPayer == member
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedPayer = member },
                                label = { Text(member) },
                                leadingIcon = { MemberAvatar(name = member, size = 20.dp) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Split Mode", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = splitModeEqual,
                            onClick = { splitModeEqual = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Equal Split")
                        }
                        SegmentedButton(
                            selected = !splitModeEqual,
                            onClick = { splitModeEqual = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("Line Items")
                        }
                    }
                }

                if (!splitModeEqual) {
                    Text("Assign items to members:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedGroup?.let { group ->
                                    itemAssignments = extracted.lineItems.associate { it.id to group.members }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Split Evenly", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                selectedGroup?.let { group ->
                                    val members = group.members
                                    if (members.isNotEmpty()) {
                                        var idx = 0
                                        itemAssignments = extracted.lineItems.associate { item ->
                                            val assignedMember = members[idx % members.size]
                                            idx++
                                            item.id to listOf(assignedMember)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Smart Match AI", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                extracted.lineItems.forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.name, fontWeight = FontWeight.Medium)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "$currencySymbol${String.format(Locale.US, "%.2f", item.price)}",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (!isPersonalExpense && !splitModeEqual && selectedGroup != null) {
                                val assigned = itemAssignments[item.id] ?: selectedGroup!!.members
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(selectedGroup!!.members) { member ->
                                        val isAssigned = assigned.contains(member)
                                        FilterChip(
                                            selected = isAssigned,
                                            onClick = {
                                                val newAssigned = if (isAssigned) assigned - member else assigned + member
                                                itemAssignments = itemAssignments + (item.id to if (newAssigned.isEmpty()) listOf(member) else newAssigned)
                                            },
                                            label = { Text(member, fontSize = 11.sp) },
                                            leadingIcon = { MemberAvatar(name = member, size = 18.dp) },
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (isPersonalExpense) {
                        viewModel.addExpense(
                            title = extracted.merchantName,
                            amount = calculatedTotal,
                            category = selectedCategory,
                            date = expenseDate.ifBlank { "Today" },
                            description = "Scanned receipt from ${extracted.merchantName}",
                            paidBy = "You",
                            currency = extracted.currency
                        )
                        viewModel.currentExtractedReceipt = null
                        viewModel.targetGroupIdForReceipt = null
                        onExpenseAdded()
                    } else {
                        selectedGroup?.let { group ->
                            val (splitModeStr, memberSplitsMap, splitMembers) = if (splitModeEqual) {
                                Triple("EQUAL", emptyMap<String, Double>(), group.members)
                            } else {
                                val rawSubtotals = mutableMapOf<String, Double>()
                                extracted.lineItems.forEach { item ->
                                    val assigned = itemAssignments[item.id] ?: group.members
                                    if (assigned.isNotEmpty()) {
                                        val share = item.price / assigned.size
                                        assigned.forEach { member ->
                                            rawSubtotals[member] = (rawSubtotals[member] ?: 0.0) + share
                                        }
                                    }
                                }
                                val totalBaseItems = extracted.lineItems.sumOf { it.price }
                                val multiplier = if (totalBaseItems > 0.0) calculatedTotal / totalBaseItems else 1.0
                                val memberTotals = rawSubtotals.mapValues { kotlin.math.round(it.value * multiplier * 100) / 100.0 }.toMutableMap()

                                val totalsSum = kotlin.math.round(memberTotals.values.sum() * 100) / 100.0
                                val diff = kotlin.math.round((calculatedTotal - totalsSum) * 100) / 100.0
                                if (kotlin.math.abs(diff) in 0.009..0.05 && memberTotals.isNotEmpty()) {
                                    val maxKey = memberTotals.maxByOrNull { it.value }?.key ?: memberTotals.keys.first()
                                    memberTotals[maxKey] = kotlin.math.round(((memberTotals[maxKey] ?: 0.0) + diff) * 100) / 100.0
                                }

                                Triple("CUSTOM", memberTotals.toMap(), memberTotals.keys.toList().ifEmpty { group.members })
                            }

                            viewModel.addGroupExpense(
                                groupId = group.id,
                                title = extracted.merchantName,
                                amount = calculatedTotal,
                                paidBy = selectedPayer,
                                splitAmong = splitMembers,
                                date = expenseDate.ifBlank { "Today" },
                                currency = extracted.currency,
                                splitMode = splitModeStr,
                                memberSplits = memberSplitsMap,
                                category = selectedCategory
                            )
                            viewModel.currentExtractedReceipt = null
                            viewModel.targetGroupIdForReceipt = null
                            onExpenseAdded()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPersonalExpense) "Add as Personal Expense" else "Add as Group Expense",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

