package com.finance.splitup.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.data.Currencies
import com.finance.splitup.data.Expense
import com.finance.splitup.data.ExpenseCategories
import com.finance.splitup.ui.theme.*
import com.finance.splitup.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalExpensesScreen(
    viewModel: FinanceViewModel,
    initialCategory: String? = null,
    onNavigateToAddExpense: () -> Unit = {},
    onNavigateToEditExpense: (String) -> Unit = {},
    onNavigateToGlobalSearch: () -> Unit = {}
) {
    val expenses by viewModel.expenses.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val incomes by viewModel.incomes.collectAsState()
    val recurringExpenses by viewModel.recurringExpenses.collectAsState()

    var selectedCategoryFilter by remember { mutableStateOf<String?>(initialCategory) }
    var selectedDateRange by remember { mutableStateOf("All") }
    var selectedPersonalTab by remember { mutableIntStateOf(0) } // 0 = Expenses, 1 = Budgets, 2 = Income & Subs

    var showAddIncomeDialog by remember { mutableStateOf(false) }
    var showAddRecurringDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val filteredExpenses = remember(expenses, selectedCategoryFilter, selectedDateRange) {
        expenses.filter { exp ->
            val matchesCategory = selectedCategoryFilter == null || exp.category == selectedCategoryFilter
            val matchesDate = com.finance.splitup.data.DateUtils.matchesDateRange(exp.date, exp.updatedAt, selectedDateRange)
            matchesCategory && matchesDate
        }
    }

    val totalPersonalSpent = remember(filteredExpenses, viewModel.defaultCurrency) {
        filteredExpenses.filter { it.category != ExpenseCategories.INCOME }
            .sumOf { Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency) }
    }
    val currencySymbol = Currencies.getSymbol(viewModel.defaultCurrency)
    val context = LocalContext.current
    val groups by viewModel.groups.collectAsState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Personal Expenses", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                actions = {
                    IconButton(onClick = {
                        try {
                            val pdfFile = com.finance.splitup.data.PdfGenerator.generateFullAppSummaryPdf(context, expenses, groups)
                            if (pdfFile != null) {
                                viewModel.postSystemNotification(
                                    "Statement PDF Generated 📄",
                                    "Your summary PDF report is ready to share.",
                                    FinanceViewModel.CHANNEL_EXPORTS_INSIGHTS
                                )
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_SUBJECT, "Personal Expenses Report")
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Export Expense Report PDF"))
                            } else {
                                Toast.makeText(context, "Failed to generate report", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Export PDF Report", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onNavigateToGlobalSearch) {
                        Icon(Icons.Rounded.Search, contentDescription = "Global Search", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddExpense,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Personal Expense")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text("Total Personal Spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        AnimatedAmountText(
                            targetAmount = totalPersonalSpent,
                            currencySymbol = currencySymbol,
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val subtitleText = if (selectedCategoryFilter != null) "Filtered by $selectedCategoryFilter" else "Logged individual expenses & receipts"
                        Text(subtitleText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                }
            }

            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    SegmentedButton(
                        selected = selectedPersonalTab == 0,
                        onClick = { selectedPersonalTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Expenses", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    SegmentedButton(
                        selected = selectedPersonalTab == 1,
                        onClick = { selectedPersonalTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Budgets", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    SegmentedButton(
                        selected = selectedPersonalTab == 2,
                        onClick = { selectedPersonalTab = 2 },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Income & Subs", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (selectedPersonalTab == 1) {
                // BUDGETS SECTION
                item {
                    var totalMonthlyInput by remember(budgets) { mutableStateOf(formatInputNumber(budgets.values.sum())) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Monthly Budget Allocation", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Set total monthly budget and distribute evenly or adjust category limits below", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            OutlinedTextField(
                                value = totalMonthlyInput,
                                onValueChange = { totalMonthlyInput = it },
                                label = { Text("Total Monthly Budget ($currencySymbol)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )

                            Button(
                                onClick = {
                                    val total = totalMonthlyInput.parseAmount()
                                    if (total > 0.0) {
                                        val perCat = total / ExpenseCategories.all.size
                                        ExpenseCategories.all.forEach { cat ->
                                            viewModel.updateBudget(cat, kotlin.math.round(perCat * 100) / 100.0)
                                        }
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Budgets auto-distributed successfully!")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Auto-Distribute Evenly Across Categories")
                            }
                        }
                    }
                }

                item {
                    Text("Category Monthly Limits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                items(ExpenseCategories.all) { category ->
                    val currentLimit = budgets[category] ?: 200.0
                    var limitText by remember { mutableStateOf(formatInputNumber(currentLimit)) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = category, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(text = "Monthly Spending Limit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedTextField(
                                value = limitText,
                                onValueChange = { newVal ->
                                    limitText = newVal
                                    newVal.parseAmount().let { limit ->
                                        viewModel.updateBudget(category, limit)
                                    }
                                },
                                modifier = Modifier.width(110.dp),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                    }
                }
            } else if (selectedPersonalTab == 2) {
                // INCOME & SUBSCRIPTIONS SECTION
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Incomes (${incomes.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        TextButton(onClick = { showAddIncomeDialog = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Income")
                        }
                    }
                }

                if (incomes.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Box(modifier = Modifier.padding(20.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("No direct income sources recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(incomes, key = { it.id }) { income ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                                        color = EmeraldSuccess.copy(alpha = 0.15f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.AttachMoney, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Column {
                                        Text(income.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text("${income.source} • ${com.finance.splitup.data.DateUtils.formatDisplayDate(income.date, income.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "+${Currencies.getSymbol(income.currency)}${String.format(Locale.US, "%.2f", income.amount)}",
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldSuccess,
                                        fontSize = 16.sp
                                    )
                                    IconButton(onClick = { viewModel.deleteIncome(income.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete Income", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Subscriptions (${recurringExpenses.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        TextButton(onClick = { showAddRecurringDialog = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Subscription")
                        }
                    }
                }

                if (recurringExpenses.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Box(modifier = Modifier.padding(20.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("No recurring subscriptions active.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(recurringExpenses, key = { it.id }) { recurring ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.Autorenew, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Column {
                                        Text(recurring.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text("${recurring.frequency} • Next: ${recurring.nextDueDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "${Currencies.getSymbol(recurring.currency)}${String.format(Locale.US, "%.2f", recurring.amount)}",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 16.sp
                                    )
                                    IconButton(onClick = { viewModel.deleteRecurringExpense(recurring.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete Subscription", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // EXPENSES LIST SECTION
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val ranges = listOf("All", "This Month", "Last 30 Days", "This Year")
                        items(ranges) { range ->
                            FilterChip(
                                selected = selectedDateRange == range,
                                onClick = { selectedDateRange = range },
                                label = { Text(range, fontSize = 12.sp) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategoryFilter == null,
                                onClick = { selectedCategoryFilter = null },
                                label = { Text("All") },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                        items(ExpenseCategories.all) { category ->
                            FilterChip(
                                selected = selectedCategoryFilter == category,
                                onClick = {
                                    selectedCategoryFilter = if (selectedCategoryFilter == category) null else category
                                },
                                label = { Text(category) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                if (filteredExpenses.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No personal expenses logged yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filteredExpenses, key = { it.id }) { expense ->
                        ExpenseItemCard(
                            expense = expense,
                            viewModel = viewModel,
                            onDelete = {
                                viewModel.deleteExpense(expense.id)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Deleted '${expense.title}'",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.updateExpense(expense)
                                    }
                                }
                            },
                            onEdit = { onNavigateToEditExpense(expense.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddIncomeDialog) {
        var incTitle by remember { mutableStateOf("") }
        var incAmount by remember { mutableStateOf("") }
        var incSource by remember { mutableStateOf("Salary") }

        AlertDialog(
            onDismissRequest = { showAddIncomeDialog = false },
            title = { Text("Record Direct Income", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().imePadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = incTitle,
                        onValueChange = { incTitle = it },
                        label = { Text("Income Title (e.g. Monthly Salary)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = incAmount,
                        onValueChange = { incAmount = it },
                        label = { Text("Amount ($currencySymbol)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = incSource,
                        onValueChange = { incSource = it },
                        label = { Text("Source (e.g. Employer, Client)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = incAmount.parseAmount()
                    if (incTitle.isNotBlank() && amt > 0) {
                        viewModel.addIncome(incTitle, amt, incSource, viewModel.defaultCurrency)
                        showAddIncomeDialog = false
                    }
                }) {
                    Text("Add Income")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddIncomeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddRecurringDialog) {
        AddSubscriptionDialog(
            defaultCurrency = viewModel.defaultCurrency,
            onDismiss = { showAddRecurringDialog = false },
            onConfirm = { title, amt, cat, freq, dueDate, _ ->
                viewModel.addRecurringExpense(title, amt, cat, freq, dueDate, viewModel.defaultCurrency)
                showAddRecurringDialog = false
            }
        )
    }
}
