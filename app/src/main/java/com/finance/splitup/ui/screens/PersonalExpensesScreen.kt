package com.finance.splitup.ui.screens

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.finance.splitup.ui.components.EmptyStateComponent
import com.finance.splitup.ui.components.tourTarget
import com.finance.splitup.ui.components.TourHighlightArea
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
import com.finance.splitup.data.UiMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalExpensesScreen(
    viewModel: FinanceViewModel,
    initialCategory: String? = null,
    onNavigateToAddExpense: (expenseId: String?, isIncome: Boolean) -> Unit = { _, _ -> },
    onNavigateToEditInvestment: (String?) -> Unit = {},
    onNavigateToEditExpense: (String) -> Unit = {},
    onNavigateToGlobalSearch: () -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToGroups: () -> Unit = {}
) {
    val context = LocalContext.current
    val expenses by viewModel.expenses.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val incomes by viewModel.incomes.collectAsState()
    val investments by viewModel.investments.collectAsState()
    val recurringExpenses by viewModel.recurringExpenses.collectAsState()

    val isInvestmentRequest = initialCategory?.equals("INVESTMENT", ignoreCase = true) == true || initialCategory?.equals("Investments", ignoreCase = true) == true
    val isIncomeRequest = initialCategory?.equals("INCOME", ignoreCase = true) == true || initialCategory?.equals(ExpenseCategories.INCOME, ignoreCase = true) == true
    var selectedCategoryFilter by remember { mutableStateOf<String?>(if (isIncomeRequest || isInvestmentRequest) null else initialCategory) }
    var selectedDateRange by remember { mutableStateOf("All") }
    var selectedPersonalTab by remember { mutableIntStateOf(if (isInvestmentRequest) 2 else if (isIncomeRequest) 1 else 0) } // 0 = Expenses, 1 = Incomes, 2 = Investments

    LaunchedEffect(initialCategory) {
        if (initialCategory?.equals("INVESTMENT", ignoreCase = true) == true || initialCategory?.equals("Investments", ignoreCase = true) == true) {
            selectedPersonalTab = 2
            selectedCategoryFilter = null
        } else if (initialCategory?.equals("INCOME", ignoreCase = true) == true || initialCategory?.equals(ExpenseCategories.INCOME, ignoreCase = true) == true) {
            selectedPersonalTab = 1
            selectedCategoryFilter = null
        } else if (initialCategory != null) {
            selectedCategoryFilter = initialCategory
        }
    }

    var showBudgetsDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val filteredExpenses = remember(expenses, selectedCategoryFilter, selectedDateRange) {
        expenses.filter { exp ->
            exp.category != ExpenseCategories.INCOME &&
            (selectedCategoryFilter == null || exp.category == selectedCategoryFilter) &&
            com.finance.splitup.data.DateUtils.matchesDateRange(exp.date, exp.updatedAt, selectedDateRange)
        }.sortedByDescending { it.date }
    }

    val totalPersonalSpent = remember(filteredExpenses, viewModel.defaultCurrency) {
        filteredExpenses.filter { exp -> exp.category != ExpenseCategories.INCOME }
            .sumOf { Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency) }
    }
    val currencySymbol = Currencies.getSymbol(viewModel.defaultCurrency)
    val groups by viewModel.groups.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Personal Expenses", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                actions = {
                    // Budgets Button on Top Right
                    IconButton(onClick = { showBudgetsDialog = true }) {
                        Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = "Manage Budgets", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onNavigateToGlobalSearch) {
                        Icon(Icons.Rounded.Search, contentDescription = "Global Search", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More Options", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export Full PDF Statement") },
                                onClick = {
                                    showMenu = false
                                    try {
                                        val pdfFile = com.finance.splitup.data.PdfGenerator.generateFullAppSummaryPdf(context, expenses, groups, investments)
                                        if (pdfFile != null) {
                                            viewModel.postSystemNotification(
                                                "Statement PDF Generated 📄",
                                                "Your summary PDF report is ready to share.",
                                                FinanceViewModel.CHANNEL_EXPORTS_INSIGHTS
                                            )
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_SUBJECT, "Financial Statement PDF")
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Export Financial Statement PDF"))
                                        } else {
                                            UiMessage.show("Failed to generate report")
                                        }
                                    } catch (e: Exception) {
                                        UiMessage.show("Export error: ${e.localizedMessage}")
                                    }
                                },
                                leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Expenses CSV") },
                                onClick = {
                                    showMenu = false
                                    try {
                                        val csvFile = com.finance.splitup.data.CsvExportImporter.exportExpensesToCsv(context, expenses)
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/csv"
                                            putExtra(Intent.EXTRA_SUBJECT, "Personal Expenses CSV")
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Export Personal Expenses CSV"))
                                    } catch (e: Exception) {
                                        UiMessage.show("CSV export error: ${e.localizedMessage}")
                                    }
                                },
                                leadingIcon = { Icon(Icons.Rounded.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Investments CSV") },
                                onClick = {
                                    showMenu = false
                                    try {
                                        val csvFile = com.finance.splitup.data.CsvExportImporter.exportInvestmentsToCsv(context, investments)
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/csv"
                                            putExtra(Intent.EXTRA_SUBJECT, "Investments Portfolio CSV")
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Export Investments Portfolio CSV"))
                                    } catch (e: Exception) {
                                        UiMessage.show("CSV export error: ${e.localizedMessage}")
                                    }
                                },
                                leadingIcon = { Icon(Icons.Rounded.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToAddExpense(null, selectedPersonalTab == 1) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = if (selectedPersonalTab == 1) "Add Income" else "Add Personal Expense")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isWide = maxWidth >= 700.dp
                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
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
                                    val subtitleText = if (selectedCategoryFilter != null) "Filtered by $selectedCategoryFilter" else "Logged individual expenses & recurring bills"
                                    Text(subtitleText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Text("Total Incomes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                                    val totalIncome = incomes.sumOf { Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency) }
                                    AnimatedAmountText(
                                        targetAmount = totalIncome,
                                        currencySymbol = currencySymbol,
                                        fontSize = 28.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Active income sources & inflows", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                                }
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
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
                                val subtitleText = if (selectedCategoryFilter != null) "Filtered by $selectedCategoryFilter" else "Logged individual expenses & recurring bills"
                                Text(subtitleText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            }
                        }
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
                        icon = {},
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(
                            text = "Expenses",
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    SegmentedButton(
                        selected = selectedPersonalTab == 1,
                        onClick = { selectedPersonalTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        icon = {},
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(
                            text = "Incomes",
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    SegmentedButton(
                        selected = selectedPersonalTab == 2,
                        onClick = { selectedPersonalTab = 2 },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        icon = {},
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(
                            text = "Investments",
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (selectedPersonalTab == 1) {
                // INCOMES TAB
                item {
                    val totalIncomeAmt = remember(incomes, recurringExpenses, viewModel.defaultCurrency) {
                        val oneTime = incomes.sumOf { Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency) }
                        val rec = recurringExpenses.filter { it.isIncome || it.category == ExpenseCategories.INCOME }.sumOf { Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency) }
                        oneTime + rec
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Total Recorded Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "+$currencySymbol${String.format(Locale.US, "%.2f", totalIncomeAmt)}",
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess,
                                fontSize = 24.sp
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "One-Time Incomes (${incomes.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { onNavigateToAddExpense(null, true) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Income", maxLines = 1, softWrap = false)
                        }
                    }
                }

                if (incomes.isEmpty()) {
                    item {
                        EmptyStateComponent(
                            icon = Icons.AutoMirrored.Rounded.CallReceived,
                            title = "No income recorded yet",
                            description = "Log salary, freelance, or side income to see your complete financial picture.",
                            primaryActionText = "Add Income",
                            onPrimaryAction = { onNavigateToAddExpense(null, true) }
                        )
                    }
                } else {
                    items(incomes, key = { it.id }) { income ->
                        Card(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth(),
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
                        Text(
                            text = "Recurring Incomes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { onNavigateToAddExpense(null, true) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Recurring Income", maxLines = 1, softWrap = false)
                        }
                    }
                }

                val recurringIncomes = recurringExpenses.filter { it.isIncome || it.category == ExpenseCategories.INCOME }
                if (recurringIncomes.isEmpty()) {
                    item {
                        EmptyStateComponent(
                            icon = Icons.Rounded.Repeat,
                            title = "No recurring incomes configured",
                            description = "Enable 'Recurring' when adding income and SplitUp will track it every month automatically."
                        )
                    }
                } else {
                    items(recurringIncomes, key = { it.id }) { recInc ->
                        Card(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth(),
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
                                            Icon(Icons.Rounded.Repeat, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Column {
                                        Text(recInc.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text("${recInc.frequency} • Next: ${recInc.nextDueDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "+${Currencies.getSymbol(recInc.currency)}${String.format(Locale.US, "%.2f", recInc.amount)}",
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldSuccess,
                                        fontSize = 16.sp
                                    )
                                    IconButton(onClick = { viewModel.deleteRecurringExpense(recInc.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete Recurring Income", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (selectedPersonalTab == 2) {
                // INVESTMENTS TAB
                item {
                    val totalInvestmentAmt = remember(investments, viewModel.defaultCurrency) {
                        investments.sumOf { Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency) }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Total Investments & Assets", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "📈 $currencySymbol${String.format(Locale.US, "%.2f", totalInvestmentAmt)}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 24.sp
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Portfolio Investments (${investments.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { onNavigateToEditInvestment(null) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Investment", maxLines = 1, softWrap = false)
                        }
                    }
                }

                if (investments.isEmpty()) {
                    item {
                        EmptyStateComponent(
                            icon = Icons.Rounded.TrendingUp,
                            title = "No investments logged yet",
                            description = "Track stocks, crypto, mutual funds, or real estate to watch your portfolio grow.",
                            primaryActionText = "Add Investment",
                            onPrimaryAction = { onNavigateToEditInvestment(null) }
                        )
                    }
                } else {
                    items(investments, key = { it.id }) { inv ->
                        Card(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .clickable { onNavigateToEditInvestment(inv.id) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                com.finance.splitup.data.InvestmentCategories.getEmoji(inv.category),
                                                fontSize = 20.sp
                                            )
                                        }
                                    }
                                    Column {
                                        Text(inv.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        val subtitle = inv.category + (if (inv.brokerOrPlatform.isNotBlank()) " • ${inv.brokerOrPlatform}" else "")
                                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "${Currencies.getSymbol(inv.currency)}${String.format(Locale.US, "%.2f", inv.amount)}",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 16.sp
                                    )
                                    IconButton(onClick = { viewModel.deleteInvestment(inv.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete Investment", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (selectedPersonalTab == 0) {
                // EXPENSES & RECURRING (Combined Section)

                // 1. Filters at the top
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.tourTarget(TourHighlightArea.TOP_SEARCH_FILTERS)
                    ) {
                        val ranges = listOf("All", "This Month", "Last 30 Days", "This Year")
                        items(ranges, key = { it }) { range ->
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
                        items(ExpenseCategories.expenseCategories, key = { it }) { category ->
                            FilterChip(
                                selected = selectedCategoryFilter == category,
                                onClick = {
                                    selectedCategoryFilter = if (selectedCategoryFilter == category) null else category
                                },
                                label = { Text(ExpenseCategories.getDisplayName(category)) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                // 2. Recurring Bills & Subscriptions Summary Card (Links to Subscriptions Hub)
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    val recurringSubs = recurringExpenses.filter { !it.isIncome && it.category != ExpenseCategories.INCOME }
                    val totalSubsAmt = recurringSubs.sumOf { Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToSubscriptions),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Subscriptions, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                    }
                                }
                                Column {
                                    Text("Recurring Bills & Subscriptions", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("${recurringSubs.size} active commitments • $currencySymbol${String.format(Locale.US, "%.2f", totalSubsAmt)}/mo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = "Open Subscriptions Hub", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("One-Time Expenses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                if (filteredExpenses.isEmpty()) {
                    item {
                        EmptyStateComponent(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            title = if (selectedCategoryFilter != null) "Nothing in $selectedCategoryFilter" else "No personal expenses yet",
                            description = if (selectedCategoryFilter != null) "Try another category or log a new expense under it." else "Log your first expense to start tracking your spending.",
                            primaryActionText = "Add Expense",
                            onPrimaryAction = { onNavigateToAddExpense(null, false) }
                        )
                    }
                } else {
                    val groupedPersonalExpenses = filteredExpenses.groupBy {
                        com.finance.splitup.data.DateUtils.formatDisplayDate(it.date, it.updatedAt)
                    }.entries.toList()

                    groupedPersonalExpenses.forEach { (date, dateExpenses) ->
                        stickyHeader(key = date) {
                            val dayTotal = dateExpenses.filter { it.category != ExpenseCategories.INCOME }
                                .sumOf { Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency) }
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = date,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Total: $currencySymbol${String.format(Locale.US, "%.2f", dayTotal)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        items(dateExpenses, key = { it.id }) { expense ->
                            ExpenseItemCard(
                                modifier = Modifier.animateItem(),
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
    }

    // Budgets Modal Bottom Sheet / Dialog
    if (showBudgetsDialog) {
        AlertDialog(
            onDismissRequest = { showBudgetsDialog = false },
            title = { Text("Manage Budgets", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        var totalMonthlyInput by remember(budgets) { mutableStateOf(formatInputNumber(budgets.values.sum())) }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Monthly Budget Allocation", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                                        val cats = ExpenseCategories.expenseCategories
                                        val perCat = kotlin.math.round((total / cats.size) * 100) / 100.0
                                        cats.forEachIndexed { index, cat ->
                                            val amountToAssign = if (index == cats.size - 1) {
                                                val runningSum = perCat * (cats.size - 1)
                                                kotlin.math.round((total - runningSum) * 100) / 100.0
                                            } else {
                                                perCat
                                            }
                                            viewModel.updateBudget(cat, amountToAssign)
                                        }
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Budgets auto-distributed successfully!")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Auto-Distribute Evenly")
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Category Monthly Limits", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    items(ExpenseCategories.expenseCategories, key = { it }) { category ->
                        val currentLimit = budgets[category] ?: 200.0
                        var limitText by remember { mutableStateOf(formatInputNumber(currentLimit)) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = ExpenseCategories.getDisplayName(category), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
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
            },
            confirmButton = {
                Button(onClick = { showBudgetsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}
}
