package com.finance.splitup.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.data.Currencies
import com.finance.splitup.data.ExpenseCategories
import com.finance.splitup.ui.theme.*
import com.finance.splitup.viewmodel.FinanceViewModel
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGroupExpenseScreen(
    groupId: String,
    expenseId: String? = null,
    viewModel: FinanceViewModel,
    onBack: () -> Unit,
    onNavigateToScanner: () -> Unit = {}
) {
    val groups by viewModel.groups.collectAsState()
    val group = groups.find { it.id == groupId }
    val existingExpense = remember(group, expenseId) { group?.expenses?.find { it.id == expenseId } }

    var title by remember { mutableStateOf(existingExpense?.title ?: "") }
    var amount by remember { mutableStateOf(existingExpense?.amount?.let { String.format(Locale.US, "%.2f", it) } ?: "") }
    var selectedCategory by remember { mutableStateOf(existingExpense?.category ?: ExpenseCategories.FOOD) }
    var selectedCurrency by remember { mutableStateOf(existingExpense?.currency ?: Currencies.EUR) }
    var expandedCurrencyDropdown by remember { mutableStateOf(false) }
    var expandedCategoryDropdown by remember { mutableStateOf(false) }
    val currentUserName = viewModel.currentUserName
    var paidBy by remember(group, existingExpense) {
        mutableStateOf(
            existingExpense?.paidBy
                ?: group?.allMembers?.find { it.equals(currentUserName, ignoreCase = true) || it == "You" }
                ?: currentUserName
        )
    }
    var expenseDate by remember { mutableStateOf(existingExpense?.date ?: "Today") }
    var splitMode by remember { mutableStateOf(existingExpense?.splitMode ?: "EQUAL") } // "EQUAL" or "CUSTOM"
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val currencySymbol = Currencies.getSymbol(selectedCurrency)

    val splitAmong = remember(group, existingExpense) {
        mutableStateMapOf<String, Boolean>().apply {
            val defaultMembers = existingExpense?.splitAmong?.ifEmpty { null }
                ?: existingExpense?.memberSplits?.keys?.toList()
                ?: group?.allMembers
                ?: emptyList()
            group?.allMembers?.forEach { member ->
                this[member] = if (existingExpense != null) defaultMembers.contains(member) else true
            }
        }
    }

    val customAmounts = remember(group, existingExpense) {
        mutableStateMapOf<String, String>().apply {
            group?.allMembers?.forEach { member ->
                this[member] = existingExpense?.memberSplits?.get(member)?.let { String.format(Locale.US, "%.2f", it) } ?: ""
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingExpense != null) "Edit Shared Expense" else "Add Shared Expense", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
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
            Surface(
                onClick = onNavigateToScanner,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                                contentDescription = "Scan Receipt",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Scan Receipt",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ) {}
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    errorMessage = null
                    if (it.isNotBlank()) {
                        selectedCategory = detectCategoryFromTitle(it)
                    }
                },
                label = { Text("Expense Title (e.g. Dinner, Hotel)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                isError = errorMessage != null && title.isBlank()
            )

            // Auto Suggestions
            val defaultMerchants = listOf("Starbucks", "Whole Foods", "Trader Joe's", "Uber", "Lyft", "Amazon", "Target", "Netflix", "Spotify", "Rent", "Electricity", "Internet", "Dinner", "Groceries", "Taxi", "Movie", "Gas")
            val historicalTitles = group?.expenses?.map { it.title }?.filter { it.isNotBlank() } ?: emptyList()
            val suggestionPool = remember(group) { (defaultMerchants + historicalTitles).distinct() }
            val filteredSuggestions = remember(title, suggestionPool) {
                if (title.isBlank()) emptyList()
                else suggestionPool.filter { it.contains(title, ignoreCase = true) && !it.equals(title, ignoreCase = true) }.take(5)
            }

            if (filteredSuggestions.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredSuggestions) { suggestion ->
                        SuggestionChip(
                            onClick = {
                                title = suggestion
                                selectedCategory = detectCategoryFromTitle(suggestion)
                            },
                            label = { Text(suggestion, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // Category dropdown menu
            ExposedDropdownMenuBox(
                expanded = expandedCategoryDropdown,
                onExpandedChange = { expandedCategoryDropdown = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategoryDropdown) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                    shape = RoundedCornerShape(16.dp)
                )
                ExposedDropdownMenu(
                    expanded = expandedCategoryDropdown,
                    onDismissRequest = { expandedCategoryDropdown = false }
                ) {
                    ExpenseCategories.all.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                selectedCategory = category
                                expandedCategoryDropdown = false
                                if (title.isNotBlank()) {
                                    viewModel.learnCategory(title, category)
                                }
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it.filter { c -> c.isDigit() || c == '.' }
                        errorMessage = null
                    },
                    label = { Text("Amount ($currencySymbol)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = errorMessage != null && amount.parseAmount() <= 0
                )

                // Currency Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedCurrencyDropdown,
                    onExpandedChange = { expandedCurrencyDropdown = it },
                    modifier = Modifier.widthIn(min = 125.dp, max = 160.dp)
                ) {
                    OutlinedTextField(
                        value = "${Currencies.getFlag(selectedCurrency)} $selectedCurrency",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Currency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCurrencyDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                        shape = RoundedCornerShape(16.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCurrencyDropdown,
                        onDismissRequest = { expandedCurrencyDropdown = false }
                    ) {
                        Currencies.all.forEach { currency ->
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
                                    selectedCurrency = currency
                                    expandedCurrencyDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Expense Date", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                DatePickerFieldToModal(
                    selectedDateText = com.finance.splitup.data.DateUtils.formatDisplayDate(expenseDate),
                    onDateSelected = { expenseDate = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Expense Date"
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Today", "Yesterday").forEach { quickDate ->
                        val isSelected = expenseDate == quickDate || com.finance.splitup.data.DateUtils.formatDisplayDate(expenseDate).startsWith(quickDate, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { expenseDate = quickDate },
                            label = { Text(quickDate, fontSize = 12.sp) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            Text("Paid By", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                group?.allMembers?.forEach { member ->
                    val isSelf = member == "You" || member.equals(currentUserName, ignoreCase = true)
                    val chipLabel = if (isSelf) (if (member == "You") "You" else "$member (You)") else member
                    FilterChip(
                        selected = paidBy == member || (isSelf && (paidBy == "You" || paidBy.equals(currentUserName, ignoreCase = true))),
                        onClick = { paidBy = member },
                        label = { Text(chipLabel, fontWeight = FontWeight.Medium) },
                        leadingIcon = { MemberAvatar(name = member, size = 20.dp) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Split Mode Toggle
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Split Method", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = splitMode == "EQUAL",
                        onClick = { splitMode = "EQUAL" },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { SegmentedButtonDefaults.Icon(active = splitMode == "EQUAL") }
                    ) {
                        Text("Split Equally")
                    }
                    SegmentedButton(
                        selected = splitMode == "CUSTOM",
                        onClick = { splitMode = "CUSTOM" },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { SegmentedButtonDefaults.Icon(active = splitMode == "CUSTOM") }
                    ) {
                        Text("Split by Exact Amounts")
                    }
                }
            }

            if (splitMode == "EQUAL") {
                Text("Split Among Members", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                group?.allMembers?.forEach { member ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MemberAvatar(name = member, size = 32.dp)
                                Text(member, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            Checkbox(
                                checked = splitAmong[member] == true,
                                onCheckedChange = { checked ->
                                    splitAmong[member] = checked
                                }
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter Exact Split Amounts", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val total = amount.parseAmount()
                                val selectedMembers = group?.allMembers?.filter { splitAmong[it] == true } ?: emptyList()
                                if (total > 0.0 && selectedMembers.isNotEmpty()) {
                                    val share = kotlin.math.round((total / selectedMembers.size) * 100) / 100.0
                                    group?.allMembers?.forEach { member ->
                                        if (selectedMembers.contains(member)) {
                                            val index = selectedMembers.indexOf(member)
                                            val amountToAssign = if (index == selectedMembers.size - 1) {
                                                val runningSum = share * (selectedMembers.size - 1)
                                                kotlin.math.round((total - runningSum) * 100) / 100.0
                                            } else {
                                                share
                                            }
                                            customAmounts[member] = String.format(Locale.US, "%.2f", amountToAssign)
                                        } else {
                                            customAmounts[member] = "0.00"
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Split Equally", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val total = amount.parseAmount()
                                if (total > 0.0) {
                                    val otherSum = group?.allMembers?.filter { splitAmong[it] == true && it != paidBy }?.sumOf { customAmounts[it]?.parseAmount() ?: 0.0 } ?: 0.0
                                    val remainingForPayer = maxOf(0.0, total - otherSum)
                                    splitAmong[paidBy] = true
                                    customAmounts[paidBy] = String.format(Locale.US, "%.2f", remainingForPayer)
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Assign Rest to Payer", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                group?.allMembers?.forEach { member ->
                                    customAmounts[member] = "0.00"
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Clear All", fontSize = 12.sp)
                        }
                    }
                }
                group?.allMembers?.forEach { member ->
                    val isChecked = splitAmong[member] == true
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isChecked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        splitAmong[member] = checked
                                        if (!checked) {
                                            customAmounts[member] = "0.00"
                                        }
                                    }
                                )
                                MemberAvatar(name = member, size = 32.dp)
                                Text(member, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            if (isChecked) {
                                OutlinedTextField(
                                    value = customAmounts[member] ?: "",
                                    onValueChange = { newVal ->
                                        customAmounts[member] = newVal.filter { c -> c.isDigit() || c == '.' }
                                        errorMessage = null
                                    },
                                    label = { Text("Amount ($currencySymbol)") },
                                    modifier = Modifier.widthIn(min = 100.dp, max = 130.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                            } else {
                                Text("Excluded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                val totalAmt = amount.parseAmount()
                val customSum = group?.allMembers?.filter { splitAmong[it] == true }?.sumOf { customAmounts[it]?.parseAmount() ?: 0.0 } ?: 0.0
                val diff = totalAmt - customSum
                val isFullyAllocated = abs(diff) < 0.01 && totalAmt > 0.0
                val allocProgress = if (totalAmt > 0) (customSum / totalAmt).coerceIn(0.0, 1.0).toFloat() else 0f

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isFullyAllocated) EmeraldSuccess.copy(alpha = 0.15f) else CrimsonDanger.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isFullyAllocated) "Custom Split Matched ✓" else "Custom Split Incomplete",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isFullyAllocated) EmeraldSuccess else CrimsonDanger
                            )
                            Text(
                                text = "Allocated: $currencySymbol${String.format(Locale.US, "%.2f", customSum)} / $currencySymbol${String.format(Locale.US, "%.2f", totalAmt)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        LinearProgressIndicator(
                            progress = { allocProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = if (isFullyAllocated) EmeraldSuccess else CrimsonDanger,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        if (!isFullyAllocated) {
                            Text(
                                text = if (diff > 0) "Remaining: $currencySymbol${String.format(Locale.US, "%.2f", diff)}" else "Overallocated by $currencySymbol${String.format(Locale.US, "%.2f", abs(diff))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CrimsonDanger
                            )
                        }
                    }
                }
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

            val rawAmount = amount.parseAmount()
            val activeSplit = splitAmong.filter { it.value }.keys.toList()
            val customSum = group?.allMembers?.filter { splitAmong[it] == true }?.sumOf { customAmounts[it]?.parseAmount() ?: 0.0 } ?: 0.0
            val isSplitValid = if (splitMode == "EQUAL") {
                activeSplit.isNotEmpty()
            } else {
                abs(customSum - rawAmount) < 0.01 && rawAmount > 0.0
            }
            val isFormValid = title.isNotBlank() && rawAmount > 0.0 && isSplitValid && !isSubmitting

            Button(
                enabled = isFormValid,
                onClick = {
                    val amountVal = abs(rawAmount)
                    val categoryVal = selectedCategory
                    val finalDate = com.finance.splitup.data.DateUtils.normalizeExpenseDate(expenseDate)

                    if (title.isBlank()) {
                        errorMessage = "Please enter a title."
                    } else if (rawAmount <= 0.0) {
                        errorMessage = "Please enter a valid amount greater than 0."
                    } else if (splitMode == "EQUAL" && activeSplit.isEmpty()) {
                        errorMessage = "Select at least one member to split among."
                    } else if (splitMode == "CUSTOM") {
                        if (abs(customSum - rawAmount) >= 0.01) {
                            errorMessage = "Split total ($currencySymbol${String.format(Locale.US, "%.2f", customSum)}) must match total ($currencySymbol${String.format(Locale.US, "%.2f", rawAmount)})."
                        } else {
                            isSubmitting = true
                            val memberSplitsMap = group?.allMembers?.filter { splitAmong[it] == true }?.associateWith { customAmounts[it]?.parseAmount() ?: 0.0 } ?: emptyMap()
                            if (existingExpense != null) {
                                val updated = existingExpense.copy(
                                    title = title,
                                    amount = amountVal,
                                    paidBy = paidBy,
                                    date = finalDate,
                                    splitAmong = activeSplit,
                                    currency = selectedCurrency,
                                    splitMode = "CUSTOM",
                                    memberSplits = memberSplitsMap,
                                    category = categoryVal
                                )
                                viewModel.updateGroupExpense(groupId, updated)
                            } else {
                                viewModel.addGroupExpense(
                                    groupId = groupId,
                                    title = title,
                                    amount = amountVal,
                                    paidBy = paidBy,
                                    splitAmong = activeSplit,
                                    date = finalDate,
                                    currency = selectedCurrency,
                                    splitMode = "CUSTOM",
                                    memberSplits = memberSplitsMap,
                                    category = categoryVal
                                )
                            }
                            onBack()
                        }
                    } else {
                        if (existingExpense != null) {
                            val updated = existingExpense.copy(
                                title = title,
                                amount = amountVal,
                                paidBy = paidBy,
                                date = finalDate,
                                splitAmong = activeSplit,
                                currency = selectedCurrency,
                                splitMode = "EQUAL",
                                memberSplits = emptyMap(),
                                category = categoryVal
                            )
                            viewModel.updateGroupExpense(groupId, updated)
                        } else {
                            viewModel.addGroupExpense(
                                groupId = groupId,
                                title = title,
                                amount = amountVal,
                                paidBy = paidBy,
                                splitAmong = activeSplit,
                                date = finalDate,
                                currency = selectedCurrency,
                                splitMode = "EQUAL",
                                memberSplits = emptyMap(),
                                category = categoryVal
                            )
                        }
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFormValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isFormValid) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (existingExpense != null) "Save Changes" else "Save Shared Expense", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
