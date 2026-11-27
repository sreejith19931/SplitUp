package com.finance.splitup.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.data.Currencies
import com.finance.splitup.data.ExpenseCategories
import com.finance.splitup.ui.theme.SplitUpTheme
import com.finance.splitup.viewmodel.FinanceViewModel

import androidx.compose.runtime.saveable.rememberSaveable
import com.finance.splitup.ui.theme.SplitUpType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    expenseId: String? = null,
    investmentId: String? = null,
    initialAmount: Double? = null,
    initialTitle: String? = null,
    initialCategory: String? = null,
    initialIsIncome: Boolean = false,
    initialCurrency: String? = null,
    viewModel: FinanceViewModel,
    onBack: () -> Unit,
    onNavigateToScanner: () -> Unit = {}
) {
    val expenses by viewModel.expenses.collectAsState()
    val existingExpense = remember(expenses, expenseId) { expenses.find { it.id == expenseId } }
    val investments by viewModel.investments.collectAsState()
    val existingInvestment = remember(investments, investmentId) { investments.find { it.id == investmentId } }

    var title by rememberSaveable { mutableStateOf(existingExpense?.title ?: existingInvestment?.title ?: initialTitle ?: viewModel.currentExtractedReceipt?.merchantName ?: "") }
    var amount by rememberSaveable { mutableStateOf(existingExpense?.amount?.let { formatInputNumber(it) } ?: existingInvestment?.amount?.let { formatInputNumber(it) } ?: initialAmount?.let { formatInputNumber(it) } ?: viewModel.currentExtractedReceipt?.totalAmount?.let { formatInputNumber(it) } ?: "") }
    var selectedCategory by rememberSaveable { mutableStateOf(existingExpense?.category ?: existingInvestment?.category ?: initialCategory ?: ExpenseCategories.FOOD) }
    var selectedCurrency by rememberSaveable { mutableStateOf(existingExpense?.currency ?: existingInvestment?.currency ?: initialCurrency ?: viewModel.currentExtractedReceipt?.currency ?: viewModel.defaultCurrency) }
    val currencySymbol = remember(selectedCurrency) { Currencies.getSymbol(selectedCurrency) }
    var description by rememberSaveable { mutableStateOf(existingExpense?.description ?: existingInvestment?.description ?: "") }
    var paidBy by rememberSaveable { mutableStateOf(existingExpense?.paidBy ?: "You") }
    var expenseDate by rememberSaveable { mutableStateOf(existingExpense?.date ?: existingInvestment?.date ?: "Today") }
    var selectedPaymentMethod by rememberSaveable { mutableStateOf(existingExpense?.paymentMethod ?: com.finance.splitup.data.PaymentMethods.CASH) }
    var expandedCategoryDropdown by remember { mutableStateOf(false) }
    var expandedCurrencyDropdown by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    var isCategoryManuallySelected by rememberSaveable { mutableStateOf(existingExpense != null || initialCategory != null) }
    var entryType by rememberSaveable {
        mutableStateOf(
            when {
                existingInvestment != null -> "Investment"
                initialCategory?.equals("INVESTMENT", ignoreCase = true) == true || initialCategory?.equals("Investments", ignoreCase = true) == true -> "Investment"
                initialIsIncome -> "Income"
                else -> "Expense"
            }
        )
    }
    val isIncome = entryType == "Income"
    val isInvestment = entryType == "Investment"
    var brokerOrPlatform by rememberSaveable { mutableStateOf(existingInvestment?.brokerOrPlatform ?: "") }
    var investmentType by rememberSaveable { mutableStateOf(existingInvestment?.transactionType ?: "BUY") }
    var selectedSubCategory by rememberSaveable { mutableStateOf("") }
    var isRecurring by rememberSaveable { mutableStateOf(false) }
    var recurringFrequency by rememberSaveable { mutableStateOf("Monthly") }

    var editableLineItems by remember(existingExpense, viewModel.currentExtractedReceipt) {
        mutableStateOf(existingExpense?.lineItems?.ifEmpty { null } ?: viewModel.currentExtractedReceipt?.lineItems ?: emptyList())
    }
    var lineItemPriceStrings by remember(editableLineItems) {
        mutableStateOf(editableLineItems.associate { it.id to formatInputNumber(it.price) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isInvestment) (if (existingInvestment != null) "Edit Investment" else "Add Investment") else if (isIncome) (if (existingExpense != null) "Edit Income" else "Add Income") else (if (existingExpense != null) "Edit Personal Expense" else "Add Personal Expense"), style = SplitUpType.ScreenSubtitle) },
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
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Entry Type", style = SplitUpType.RowTitle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val entryOptions = listOf("Expense", "Income", "Investment")
                    entryOptions.forEach { option ->
                        val isSelected = entryType == option
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                entryType = option
                                selectedCategory = when (option) {
                                    "Expense" -> ExpenseCategories.FOOD
                                    "Income" -> ExpenseCategories.INCOME
                                    else -> com.finance.splitup.data.InvestmentCategories.STOCKS
                                }
                            },
                            label = {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = option, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, style = SplitUpType.Caption, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
            Surface(
                onClick = onNavigateToScanner,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
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
                            Text(text = "Scan Receipt", style = SplitUpType.RowTitle, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ) {
                            }
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
                    isCategoryManuallySelected = false
                    if (it.isNotBlank() && !isIncome) {
                        selectedCategory = detectCategoryFromTitle(it)
                    }
                },
                label = { Text(if (isInvestment) "Asset Title (e.g. Apple Stock, Vanguard ETF)" else if (isIncome) "Income Title (e.g. Salary)" else "Expense Title (e.g. Grocery)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                isError = errorMessage != null && title.isBlank()
            )

            // Auto Suggestions
            val defaultMerchants = listOf("Starbucks", "Whole Foods", "Trader Joe's", "Uber", "Lyft", "Amazon", "Target", "Netflix", "Spotify", "Rent", "Electricity", "Internet", "Dinner", "Groceries", "Taxi", "Movie", "Gas")
            val historicalTitles = expenses.map { it.title }.filter { it.isNotBlank() }
            val suggestionPool = remember(expenses) { (defaultMerchants + historicalTitles).distinct() }
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
                                isCategoryManuallySelected = false
                                if (!isIncome) {
                                    selectedCategory = detectCategoryFromTitle(suggestion)
                                }
                            },
                            label = { Text(suggestion, style = SplitUpType.Caption) }
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = title.isNotBlank() && !isCategoryManuallySelected && !isIncome,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(text = "Auto-detected category: $selectedCategory", style = SplitUpType.ChipLabelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        errorMessage = null
                    },
                    label = { Text("Amount") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = errorMessage != null && amount.parseAmount() <= 0
                )

                // Currency Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedCurrencyDropdown,
                    onExpandedChange = { expandedCurrencyDropdown = it },
                    modifier = Modifier.widthIn(min = 135.dp, max = 175.dp)
                ) {
                    OutlinedTextField(
                        value = "${Currencies.getFlag(selectedCurrency)} $selectedCurrency",
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Currency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCurrencyDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                        shape = RoundedCornerShape(12.dp)
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
                                        Text(Currencies.getFlag(currency), style = SplitUpType.BodyRegular)
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

            val defaultCurr = viewModel.defaultCurrency
            if (selectedCurrency != defaultCurr && amount.parseAmount() > 0) {
                val converted = Currencies.convert(amount.parseAmount(), selectedCurrency, defaultCurr)
                val targetSymbol = Currencies.getSymbol(defaultCurr)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Travel Mode: ~ $targetSymbol${String.format(java.util.Locale.US, "%.2f", converted)} ($defaultCurr)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Category dropdown menu
            val budgets by viewModel.budgets.collectAsState()
            if (isInvestment) {
                ExposedDropdownMenuBox(
                    expanded = expandedCategoryDropdown,
                    onExpandedChange = { expandedCategoryDropdown = it }
                ) {
                    OutlinedTextField(
                        value = com.finance.splitup.data.InvestmentCategories.getDisplayName(selectedCategory),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Asset Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategoryDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCategoryDropdown,
                        onDismissRequest = { expandedCategoryDropdown = false }
                    ) {
                        com.finance.splitup.data.InvestmentCategories.all.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(com.finance.splitup.data.InvestmentCategories.getDisplayName(category)) },
                                onClick = {
                                    selectedCategory = category
                                    expandedCategoryDropdown = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = brokerOrPlatform,
                    onValueChange = { brokerOrPlatform = it },
                    label = { Text("Broker / Platform (e.g. Robinhood, Fidelity, Binance)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Transaction Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = investmentType == "BUY",
                            onClick = { investmentType = "BUY" },
                            label = { Text("📈 Buy / Contribution", style = SplitUpType.Caption) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        FilterChip(
                            selected = investmentType == "SELL",
                            onClick = { investmentType = "SELL" },
                            label = { Text("📉 Sell / Withdrawal", style = SplitUpType.Caption) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        FilterChip(
                            selected = investmentType == "DIVIDEND_RETURN",
                            onClick = { investmentType = "DIVIDEND_RETURN" },
                            label = { Text("💰 Dividend / Return", style = SplitUpType.Caption) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            } else if (!isIncome) {
                ExposedDropdownMenuBox(
                    expanded = expandedCategoryDropdown,
                    onExpandedChange = { expandedCategoryDropdown = it }
                ) {
                    OutlinedTextField(
                        value = ExpenseCategories.getDisplayName(selectedCategory),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategoryDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCategoryDropdown,
                        onDismissRequest = { expandedCategoryDropdown = false }
                    ) {
                        ExpenseCategories.expenseCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(ExpenseCategories.getDisplayName(category)) },
                                onClick = {
                                    selectedCategory = category
                                    isCategoryManuallySelected = true
                                    expandedCategoryDropdown = false
                                    if (title.isNotBlank()) {
                                        viewModel.learnCategory(title, category)
                                    }
                                }
                            )
                        }
                    }
                }

                val subCategories = remember(selectedCategory) {
                    ExpenseCategories.subCategoriesMap[selectedCategory] ?: emptyList()
                }

                LaunchedEffect(selectedCategory) {
                    if (!subCategories.contains(selectedSubCategory)) {
                        selectedSubCategory = ""
                    }
                }

                if (subCategories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Sub-category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(subCategories) { subCat ->
                            FilterChip(
                                selected = selectedSubCategory == subCat,
                                onClick = {
                                    selectedSubCategory = if (selectedSubCategory == subCat) "" else subCat
                                },
                                label = { Text(subCat, style = SplitUpType.Caption) }
                            )
                        }
                    }
                }

                val currentCategorySpent = remember(expenses, selectedCategory, viewModel.defaultCurrency) {
                    expenses.filter { it.category == selectedCategory }
                        .sumOf { Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency) }
                }
                val categoryLimit = budgets[selectedCategory] ?: 0.0
                val enteredAmt = amount.parseAmount()
                val convertedEnteredAmt = Currencies.convert(enteredAmt, selectedCurrency, viewModel.defaultCurrency)
                val projectedSpent = currentCategorySpent + convertedEnteredAmt
                val isBudgetExceeded = categoryLimit > 0.0 && projectedSpent >= categoryLimit
                val isBudgetWarning = categoryLimit > 0.0 && projectedSpent >= categoryLimit * 0.8 && !isBudgetExceeded

                if (!isIncome && selectedCategory != ExpenseCategories.INCOME && categoryLimit > 0.0 && (isBudgetExceeded || isBudgetWarning)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isBudgetExceeded) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = if (isBudgetExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = if (isBudgetExceeded) "⚠️ Budget Exceeded for $selectedCategory!" else "⚠️ Budget Warning: Approaching limit",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBudgetExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Projected: $currencySymbol${String.format(java.util.Locale.US, "%.2f", projectedSpent)} / Limit: $currencySymbol${String.format(java.util.Locale.US, "%.2f", categoryLimit)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description / Notes") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                minLines = 3
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerFieldToModal(
                    selectedDateText = com.finance.splitup.data.DateUtils.formatDisplayDate(expenseDate, existingExpense?.updatedAt ?: 0L),
                    onDateSelected = { expenseDate = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = if (isIncome) "Income Date" else "Expense Date"
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Today", "Yesterday").forEach { quickDate ->
                        val isSelected = expenseDate == quickDate || com.finance.splitup.data.DateUtils.formatDisplayDate(expenseDate, existingExpense?.updatedAt ?: 0L).startsWith(quickDate, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { expenseDate = quickDate },
                            label = { Text(quickDate, style = SplitUpType.Caption) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = paidBy,
                onValueChange = { paidBy = it },
                label = { Text(if (isIncome) "Received By" else "Paid By") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            // Scanned Receipt Line Items Editor
            if (!isIncome && (editableLineItems.isNotEmpty() || existingExpense?.receiptImagePath != null || viewModel.currentExtractedReceipt != null)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Receipt Line Items (${editableLineItems.size})", style = SplitUpType.RowTitle)
                            TextButton(
                                onClick = {
                                    val newId = java.util.UUID.randomUUID().toString()
                                    val newItem = com.finance.splitup.data.ReceiptLineItem(
                                        id = newId,
                                        name = "New Item",
                                        price = 0.0
                                    )
                                    editableLineItems = editableLineItems + newItem
                                    lineItemPriceStrings = lineItemPriceStrings + (newId to "")
                                }
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Item", style = SplitUpType.Caption)
                            }
                        }

                        editableLineItems.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = item.name,
                                    onValueChange = { newName ->
                                        editableLineItems = editableLineItems.map { if (it.id == item.id) it.copy(name = newName) else it }
                                    },
                                    label = { Text("Item Name") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = lineItemPriceStrings[item.id] ?: formatInputNumber(item.price),
                                    onValueChange = { newPriceStr ->
                                        val filtered = filterPriceInput(newPriceStr)
                                        lineItemPriceStrings = lineItemPriceStrings + (item.id to filtered)
                                        val newPrice = filtered.parseAmount()
                                        editableLineItems = editableLineItems.map { if (it.id == item.id) it.copy(price = newPrice) else it }
                                    },
                                    label = { Text("Price ($currencySymbol)") },
                                    modifier = Modifier.width(110.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                                IconButton(
                                    onClick = {
                                        editableLineItems = editableLineItems.filter { it.id != item.id }
                                        lineItemPriceStrings = lineItemPriceStrings - item.id
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete item",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Payment Method", style = SplitUpType.ItemTitleMedium)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(com.finance.splitup.data.PaymentMethods.all) { method ->
                        FilterChip(
                            selected = selectedPaymentMethod == method,
                            onClick = { selectedPaymentMethod = method },
                            label = { Text(method, style = SplitUpType.Caption) },
                            shape = RoundedCornerShape(10.dp)
                        )
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

            if (isIncome) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Recurring Income", style = SplitUpType.RowTitleMedium)
                        Text("Auto-log this income periodically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it }
                    )
                }

                if (isRecurring) {
                    Text("Frequency", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Weekly", "Monthly", "Yearly").forEach { freq ->
                            FilterChip(
                                selected = recurringFrequency == freq,
                                onClick = { recurringFrequency = freq },
                                label = { Text(freq) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val amt = amount.parseAmount()
            val isFormValid = title.isNotBlank() && amt > 0.0 && !isSubmitting

            Button(
                enabled = isFormValid,
                onClick = {
                    val finalDate = com.finance.splitup.data.DateUtils.normalizeExpenseDate(expenseDate)

                    if (title.isBlank()) {
                        errorMessage = "Please enter a title."
                    } else if (amt <= 0) {
                        errorMessage = "Please enter a valid amount greater than 0."
                    } else {
                        isSubmitting = true
                        val nextDueStr = if (isRecurring && isIncome) {
                            val now = java.time.LocalDate.now()
                            val futureDate = when (recurringFrequency.lowercase(java.util.Locale.ROOT)) {
                                "weekly" -> now.plusWeeks(1)
                                "yearly" -> now.plusYears(1)
                                else -> now.plusMonths(1)
                            }
                            futureDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy", java.util.Locale.US))
                        } else {
                            finalDate
                        }

                        val finalDescription = if (selectedSubCategory.isNotBlank()) {
                            "[$selectedSubCategory] ${description.trim()}".trim()
                        } else {
                            description
                        }

                        if (isInvestment) {
                            val existingInv = existingInvestment
                            if (existingInv != null) {
                                viewModel.updateInvestment(
                                    existingInv.copy(
                                        title = title,
                                        amount = amt,
                                        category = selectedCategory,
                                        brokerOrPlatform = brokerOrPlatform,
                                        transactionType = investmentType,
                                        currency = selectedCurrency,
                                        date = finalDate,
                                        description = finalDescription.ifBlank { "Investment Asset" }
                                    )
                                )
                            } else {
                                viewModel.addInvestment(
                                    title = title,
                                    amount = amt,
                                    category = selectedCategory,
                                    brokerOrPlatform = brokerOrPlatform,
                                    transactionType = investmentType,
                                    currency = selectedCurrency,
                                    date = finalDate,
                                    description = finalDescription.ifBlank { "Investment Asset" }
                                )
                            }
                        } else if (isIncome) {
                            viewModel.addExpense(
                                title = title,
                                amount = amt,
                                category = ExpenseCategories.INCOME,
                                date = finalDate,
                                description = finalDescription.ifBlank { "Income" },
                                paidBy = paidBy,
                                currency = selectedCurrency,
                                paymentMethod = selectedPaymentMethod
                            )
                            if (isRecurring) {
                                viewModel.addRecurringExpense(
                                    title = title,
                                    amount = amt,
                                    category = ExpenseCategories.INCOME,
                                    frequency = recurringFrequency,
                                    nextDueDate = nextDueStr,
                                    currency = selectedCurrency,
                                    isIncome = true
                                )
                            }
                        } else {
                            if (existingExpense != null) {
                                val updated = existingExpense.copy(
                                    title = title,
                                    amount = amt,
                                    category = selectedCategory,
                                    date = finalDate,
                                    description = finalDescription,
                                    paidBy = paidBy,
                                    currency = selectedCurrency,
                                    paymentMethod = selectedPaymentMethod,
                                    lineItems = editableLineItems
                                )
                                viewModel.updateExpense(updated)
                            } else {
                                viewModel.addExpense(
                                    title = title,
                                    amount = amt,
                                    category = selectedCategory,
                                    date = finalDate,
                                    description = finalDescription,
                                    paidBy = paidBy,
                                    currency = selectedCurrency,
                                    paymentMethod = selectedPaymentMethod,
                                    lineItems = editableLineItems
                                )
                            }
                        }
                        viewModel.currentExtractedReceipt = null
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFormValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isFormValid) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isInvestment) (if (existingInvestment != null) "Save Investment" else "Add Investment") else if (isIncome) (if (existingExpense != null) "Save Income" else "Add Income") else (if (existingExpense != null) "Save Changes" else "Save Expense"), style = SplitUpType.CardTitle)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AddExpenseScreenPreview() {
    SplitUpTheme {
        val viewModel = remember { FinanceViewModel() }
        AddExpenseScreen(viewModel = viewModel, onBack = {})
    }
}
