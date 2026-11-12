package com.finance.splitup.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    expenseId: String? = null,
    initialAmount: Double? = null,
    initialTitle: String? = null,
    initialCategory: String? = null,
    initialIsIncome: Boolean = false,
    viewModel: FinanceViewModel,
    onBack: () -> Unit,
    onNavigateToScanner: () -> Unit = {}
) {
    val expenses by viewModel.expenses.collectAsState()
    val existingExpense = remember(expenses, expenseId) { expenses.find { it.id == expenseId } }

    var title by remember { mutableStateOf(existingExpense?.title ?: initialTitle ?: viewModel.currentExtractedReceipt?.merchantName ?: "") }
    var amount by remember { mutableStateOf(existingExpense?.amount?.let { formatInputNumber(it) } ?: initialAmount?.let { formatInputNumber(it) } ?: viewModel.currentExtractedReceipt?.totalAmount?.let { formatInputNumber(it) } ?: "") }
    var selectedCategory by remember { mutableStateOf(existingExpense?.category ?: initialCategory ?: ExpenseCategories.FOOD) }
    var selectedCurrency by remember { mutableStateOf(existingExpense?.currency ?: viewModel.currentExtractedReceipt?.currency ?: Currencies.EUR) }
    var description by remember { mutableStateOf(existingExpense?.description ?: "") }
    var paidBy by remember { mutableStateOf(existingExpense?.paidBy ?: "You") }
    var expenseDate by remember { mutableStateOf(existingExpense?.date ?: "Today") }
    var selectedPaymentMethod by remember { mutableStateOf(existingExpense?.paymentMethod ?: com.finance.splitup.data.PaymentMethods.CASH) }
    var expandedCategoryDropdown by remember { mutableStateOf(false) }
    var expandedCurrencyDropdown by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    var isCategoryManuallySelected by remember { mutableStateOf(existingExpense != null || initialCategory != null) }
    var isIncome by remember { mutableStateOf(initialIsIncome) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isIncome) (if (existingExpense != null) "Edit Income" else "Add Income") else (if (existingExpense != null) "Edit Personal Expense" else "Add Personal Expense"), fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Entry Type", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = !isIncome,
                        onClick = { isIncome = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Expense")
                    }
                    SegmentedButton(
                        selected = isIncome,
                        onClick = { isIncome = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Income")
                    }
                }
            }
            Button(
                onClick = onNavigateToScanner,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Receipt with OCR", fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    errorMessage = null
                    if (it.isNotBlank() && !isCategoryManuallySelected && !isIncome) {
                        selectedCategory = detectCategoryFromTitle(it)
                    }
                },
                label = { Text(if (isIncome) "Income Title (e.g. Salary)" else "Expense Title (e.g. Grocery)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
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
                                if (!isCategoryManuallySelected && !isIncome) {
                                    selectedCategory = detectCategoryFromTitle(suggestion)
                                } else {
                                    selectedCategory = detectCategoryFromTitle(suggestion)
                                }
                            },
                            label = { Text(suggestion, fontSize = 12.sp) }
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
                        Text(
                            text = "Auto-detected category: $selectedCategory",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
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
                        amount = it
                        errorMessage = null
                    },
                    label = { Text("Amount") },
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

            // Category dropdown menu
            if (!isIncome) {
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
                            label = { Text(quickDate, fontSize = 12.sp) },
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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Payment Method", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(com.finance.splitup.data.PaymentMethods.all) { method ->
                        FilterChip(
                            selected = selectedPaymentMethod == method,
                            onClick = { selectedPaymentMethod = method },
                            label = { Text(method, fontSize = 12.sp) },
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
                        if (isIncome) {
                            viewModel.addExpense(
                                title = title,
                                amount = amt,
                                category = ExpenseCategories.INCOME,
                                date = finalDate,
                                description = description.ifBlank { "Income" },
                                paidBy = paidBy,
                                currency = selectedCurrency,
                                paymentMethod = selectedPaymentMethod
                            )
                        } else {
                            if (existingExpense != null) {
                                val updated = existingExpense.copy(
                                    title = title,
                                    amount = amt,
                                    category = selectedCategory,
                                    date = finalDate,
                                    description = description,
                                    paidBy = paidBy,
                                    currency = selectedCurrency,
                                    paymentMethod = selectedPaymentMethod
                                )
                                viewModel.updateExpense(updated)
                            } else {
                                viewModel.addExpense(
                                    title = title,
                                    amount = amt,
                                    category = selectedCategory,
                                    date = finalDate,
                                    description = description,
                                    paidBy = paidBy,
                                    currency = selectedCurrency,
                                    paymentMethod = selectedPaymentMethod
                                )
                            }
                        }
                        viewModel.currentExtractedReceipt = null
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
                Text(
                    text = if (isIncome) (if (existingExpense != null) "Save Income" else "Add Income") else (if (existingExpense != null) "Save Changes" else "Save Expense"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
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
