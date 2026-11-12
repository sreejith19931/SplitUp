package com.finance.splitup.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.finance.splitup.data.Currencies
import com.finance.splitup.data.Expense
import com.finance.splitup.data.ExpenseCategories
import com.finance.splitup.ui.components.LogoStyle
import com.finance.splitup.ui.components.SplitUpLogo
import com.finance.splitup.ui.theme.*
import com.finance.splitup.viewmodel.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: FinanceViewModel,
    onNavigateToAddExpense: () -> Unit = {},
    onNavigateToEditExpense: (String) -> Unit = {},
    onNavigateToPersonalExpenses: (String?) -> Unit = {},
    onNavigateToGroups: () -> Unit = {},
    onNavigateToGroupDetail: (String) -> Unit = {},
    onNavigateToScanner: () -> Unit = {},
    onNavigateToCreateGroup: () -> Unit = {},
    onNavigateToGlobalSearch: () -> Unit = {},
    onNavigateToAddGroupExpense: (String) -> Unit = {}
) {
    val expenses by viewModel.expenses.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val recurringExpenses by viewModel.recurringExpenses.collectAsState()

    val sortedGroups = remember(groups) {
        groups.sortedByDescending { group ->
            group.expenses.maxOfOrNull { it.id } ?: group.id
        }
    }

    var isBalanceHidden by rememberSaveable { mutableStateOf(false) }

    val totalPersonalSpent by remember(expenses, viewModel.defaultCurrency) {
        derivedStateOf {
            expenses.filter { it.category != ExpenseCategories.INCOME }
                .sumOf { Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency) }
        }
    }
    val totalGroupSpent by remember(groups, viewModel.defaultCurrency) {
        derivedStateOf {
            groups.sumOf { g ->
                g.expenses
                    .filter { it.category != ExpenseCategories.SETTLEMENT && !it.title.contains("Settlement", ignoreCase = true) }
                    .sumOf { Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency) }
            }
        }
    }

    val totalNetOwed by remember(groups, viewModel.defaultCurrency) {
        derivedStateOf {
            groups.sumOf { group ->
                viewModel.getUserNetBalance(group, viewModel.defaultCurrency)
            }
        }
    }

    var selectedDashboardCategory by remember { mutableStateOf("All") }
    val groupedExpenseEntries = remember(expenses, selectedDashboardCategory) {
        val filtered = if (selectedDashboardCategory == "All") expenses else expenses.filter { it.category.equals(selectedDashboardCategory, ignoreCase = true) }
        filtered.groupBy { com.finance.splitup.data.DateUtils.formatDisplayDate(it.date, it.updatedAt) }.entries.toList()
    }

    val currencySymbol = Currencies.getSymbol(viewModel.defaultCurrency)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }
    var showGroupPickerForExpense by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

    val dismissedNotifications by viewModel.dismissedNotifications.collectAsState()

    val activeNotificationItems = remember(groups, recurringExpenses, budgets, viewModel, dismissedNotifications) {
        val list = mutableListOf<Pair<String, String>>()
        groups.forEach { g ->
            val myBal = viewModel.getUserNetBalance(g)
            val key = "group_bal_${g.id}_${String.format(Locale.US, "%.2f", myBal)}"
            val sym = Currencies.getSymbol(viewModel.defaultCurrency)
            if (myBal < -0.01) {
                val msg = "You owe $sym${String.format(Locale.US, "%.2f", -myBal)} in group '${g.name}'."
                if (!dismissedNotifications.contains(key)) list.add(key to msg)
            } else if (myBal > 0.01) {
                val msg = "You are owed $sym${String.format(Locale.US, "%.2f", myBal)} in group '${g.name}'."
                if (!dismissedNotifications.contains(key)) list.add(key to msg)
            }
        }
        recurringExpenses.forEach { r ->
            val key = "recurring_${r.id}"
            val msg = "Upcoming Subscription: '${r.title}' (${Currencies.getSymbol(r.currency)}${r.amount}) due on ${r.nextDueDate}."
            if (!dismissedNotifications.contains(key)) list.add(key to msg)
        }
        budgets.forEach { (cat, limit) ->
            val spent = viewModel.getSpendingForCategory(cat)
            val spentRounded = String.format(Locale.US, "%.0f", spent)
            val key = "budget_${cat}_${limit.toInt()}_${spentRounded}"
            val sym = Currencies.getSymbol(viewModel.defaultCurrency)
            if (spent >= limit) {
                val msg = "Budget Exceeded: $cat limit ($sym${limit.toInt()}) reached ($sym${String.format(Locale.US, "%.2f", spent)})!"
                if (!dismissedNotifications.contains(key)) list.add(key to msg)
            } else if (spent >= limit * 0.8) {
                val msg = "Budget Alert: $cat is at ${(spent / limit * 100).toInt()}% of limit ($sym${String.format(Locale.US, "%.0f", limit)})."
                if (!dismissedNotifications.contains(key)) list.add(key to msg)
            }
        }
        list
    }

    val notificationCount = activeNotificationItems.size

    val currentUser = remember {
        try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
    }
    val userEmail = currentUser?.email
    val userName = currentUser?.displayName?.ifBlank { null } ?: userEmail?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "Friend"
    val firstName = remember(userName) { userName.substringBefore(" ") }

    val greetings = remember(firstName) {
        listOf(
            "Welcome back, $firstName! 👋",
            "Hi, $firstName! ✨",
            "Great to see you, $firstName! 🚀",
            "Good day, $firstName! ☀️",
            "Hello, $firstName! 👋"
        )
    }
    val selectedGreeting = remember(firstName) { greetings.random() }

    var displayedGreeting by remember { mutableStateOf("") }
    LaunchedEffect(selectedGreeting) {
        displayedGreeting = ""
        for (i in selectedGreeting.indices) {
            displayedGreeting = selectedGreeting.substring(0, i + 1)
            kotlinx.coroutines.delay(40L)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            SplitUpLogo(size = 24.dp, style = LogoStyle.REFINED_CARD_SPLITUP)
                            Text(
                                text = displayedGreeting,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        BadgedBox(
                            badge = {
                                if (notificationCount > 0) {
                                    Badge {
                                        Text("$notificationCount")
                                    }
                                }
                            }
                        ) {
                            IconButton(onClick = { showNotificationsSheet = true }) {
                                Icon(Icons.Rounded.Notifications, contentDescription = "Notifications", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        IconButton(onClick = onNavigateToGlobalSearch) {
                            Icon(Icons.Rounded.Search, contentDescription = "Global Search", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { innerPadding ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        viewModel.refreshData()
                        kotlinx.coroutines.delay(600)
                        isRefreshing = false
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val syncStatus by com.finance.splitup.data.CloudSyncManager.syncStatus.collectAsState()
                val displayedExpenses = remember(expenses, selectedDashboardCategory) {
                    val filtered = if (selectedDashboardCategory == "All") expenses else expenses.filter { it.category.equals(selectedDashboardCategory, ignoreCase = true) }
                    filtered.take(3)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    if (syncStatus == com.finance.splitup.data.SyncStatus.ERROR || currentUser == null) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.CloudOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = "Offline Mode — Local data is saved securely",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }

                    item {
                        val pagerState = rememberPagerState(pageCount = { 3 })
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth()
                        ) { page ->
                            when (page) {
                                0 -> {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Spending & Balances", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            IconButton(onClick = { isBalanceHidden = !isBalanceHidden }, modifier = Modifier.size(28.dp)) {
                                                Icon(
                                                    imageVector = if (isBalanceHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                    contentDescription = "Toggle Balance Privacy",
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(
                                                modifier = Modifier.clickable { onNavigateToPersonalExpenses(null) }
                                            ) {
                                                Text("Personal Spent", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                                                Spacer(modifier = Modifier.height(2.dp))
                                                if (isBalanceHidden) {
                                                    Text("••••••", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                                } else {
                                                    AnimatedAmountText(
                                                        targetAmount = totalPersonalSpent,
                                                        currencySymbol = currencySymbol,
                                                        fontSize = 22.sp,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                            Column(
                                                modifier = Modifier.clickable { onNavigateToGroups() }
                                            ) {
                                                Text("Group Spent", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                                                Spacer(modifier = Modifier.height(2.dp))
                                                if (isBalanceHidden) {
                                                    Text("••••••", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                                } else {
                                                    AnimatedAmountText(
                                                        targetAmount = totalGroupSpent,
                                                        currencySymbol = currencySymbol,
                                                        fontSize = 22.sp,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onNavigateToGroups() },
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Net Group Balance", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                                                Spacer(modifier = Modifier.height(2.dp))
                                                if (isBalanceHidden) {
                                                    Text("••••••", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                                } else if (totalNetOwed >= 0.01 || totalNetOwed < -0.01) {
                                                    AnimatedAmountText(
                                                        targetAmount = if (totalNetOwed >= 0) totalNetOwed else -totalNetOwed,
                                                        currencySymbol = currencySymbol,
                                                        prefix = if (totalNetOwed >= 0.01) "Owed: +" else "Owe: -",
                                                        fontSize = 18.sp,
                                                        color = if (totalNetOwed >= 0.01) EmeraldSuccess else CrimsonDanger
                                                    )
                                                } else {
                                                    Text(
                                                        text = "Settled Up",
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                1 -> {
                                    val totalInc = remember(viewModel.totalIncome, viewModel.defaultCurrency) { Currencies.convert(viewModel.totalIncome, Currencies.EUR, viewModel.defaultCurrency) }
                                    val netFlow = remember(viewModel.netCashFlow, viewModel.defaultCurrency) { Currencies.convert(viewModel.netCashFlow, Currencies.EUR, viewModel.defaultCurrency) }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNavigateToPersonalExpenses(null) }
                                    ) {
                                        Text("Income & Cash Flow", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("Total Monthly Income", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                                                Spacer(modifier = Modifier.height(2.dp))
                                                AnimatedAmountText(
                                                    targetAmount = totalInc,
                                                    currencySymbol = currencySymbol,
                                                    fontSize = 22.sp,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Net Cash Flow", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                                                Spacer(modifier = Modifier.height(2.dp))
                                                AnimatedAmountText(
                                                    targetAmount = if (netFlow >= 0) netFlow else -netFlow,
                                                    currencySymbol = currencySymbol,
                                                    prefix = if (netFlow >= 0) "+" else "-",
                                                    fontSize = 22.sp,
                                                    color = if (netFlow >= 0) EmeraldSuccess else CrimsonDanger
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }
                                }
                                2 -> {
                                    val totalSpent = remember(expenses, viewModel.defaultCurrency) {
                                        expenses.filter { it.category != ExpenseCategories.INCOME }
                                            .sumOf { Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency) }
                                    }
                                    val totalLimit = budgets.values.sum()
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNavigateToGroups() }
                                    ) {
                                        Text("Financial Health & Groups", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("Total Budget Spent", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    AnimatedAmountText(
                                                        targetAmount = totalSpent,
                                                        currencySymbol = currencySymbol,
                                                        fontSize = 20.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Text(" / $currencySymbol${String.format(Locale.US, "%.2f", totalLimit)}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                                }
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Active Groups", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("${groups.size} Groups", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(3) { index ->
                                val isSelected = pagerState.currentPage == index
                                val dotWidth by animateDpAsState(
                                    targetValue = if (isSelected) 18.dp else 6.dp,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "dotWidth"
                                )
                                val dotColor by animateColorAsState(
                                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f),
                                    label = "dotColor"
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .height(6.dp)
                                        .width(dotWidth)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }
                        }
                    }
                }
            }

            item {
                val exceededBudgetsCount = remember(expenses, budgets, viewModel.defaultCurrency) {
                    budgets.count { (cat, limit) ->
                        val spent = expenses.filter { it.category == cat }.sumOf { Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency) }
                        spent >= limit
                    }
                }
                val recurringCount = remember(recurringExpenses) { recurringExpenses.size }
                val isPositiveCashFlow = remember(viewModel.netCashFlow) { viewModel.netCashFlow >= 0 }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    if (exceededBudgetsCount > 0) {
                        item {
                            SuggestionChip(
                                onClick = { showNotificationsSheet = true },
                                label = { Text("⚠️ $exceededBudgetsCount budget exceeded", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, labelColor = CrimsonDanger),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            )
                        }
                    }
                    item {
                        SuggestionChip(
                            onClick = { showNotificationsSheet = true },
                            label = { Text(if (isPositiveCashFlow) "💡 Positive cash flow" else "⚠️ Negative cash flow", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (isPositiveCashFlow) EmeraldSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = if (isPositiveCashFlow) EmeraldSuccess else CrimsonDanger
                            ),
                            border = BorderStroke(1.dp, if (isPositiveCashFlow) EmeraldSuccess.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline)
                        )
                    }
                    if (recurringCount > 0) {
                        item {
                            SuggestionChip(
                                onClick = { showNotificationsSheet = true },
                                label = { Text("🔔 $recurringCount subscriptions active", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer, labelColor = MaterialTheme.colorScheme.primary),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            )
                        }
                    }
                }
            }

            item {
                BudgetAndCategoryAnalyticsCard(
                    expenses = expenses,
                    budgets = budgets,
                    defaultCurrency = viewModel.defaultCurrency,
                    onCategoryClick = { category ->
                        selectedDashboardCategory = if (selectedDashboardCategory == category) "All" else category
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Personal Expenses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    TextButton(onClick = { onNavigateToPersonalExpenses(null) }) {
                        Text("View All")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    val cats = listOf("All", ExpenseCategories.FOOD, ExpenseCategories.TRANSPORT, ExpenseCategories.SHOPPING, ExpenseCategories.BILLS, ExpenseCategories.ENTERTAINMENT)
                    items(cats) { cat ->
                        FilterChip(
                            selected = selectedDashboardCategory == cat,
                            onClick = { selectedDashboardCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            if (groupedExpenseEntries.isEmpty() || groupedExpenseEntries.all { it.value.isEmpty() }) {
                item {
                    val emptyTitle = if (selectedDashboardCategory == "All") "No Personal Expenses Yet" else "No $selectedDashboardCategory Expenses"
                    val emptyDesc = if (selectedDashboardCategory == "All") "Track your daily spending easily by logging your first expense or scanning a receipt!" else "You haven't logged any expenses in '$selectedDashboardCategory' yet."
                    com.finance.splitup.ui.components.EmptyStateComponent(
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        title = emptyTitle,
                        description = emptyDesc,
                        primaryActionText = "Add Expense",
                        onPrimaryAction = onNavigateToAddExpense,
                        secondaryActionText = "Scan Receipt",
                        onSecondaryAction = onNavigateToScanner
                    )
                }
            } else {
                groupedExpenseEntries.forEach { (date, dateExpenses) ->
                    stickyHeader(key = date) {
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
                                    text = "${dateExpenses.size} items",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    items(dateExpenses, key = { it.id }) { expense ->
                        ExpenseItemCard(
                            expense = expense,
                            viewModel = viewModel,
                            onDelete = { viewModel.deleteExpense(expense.id) },
                            onEdit = { onNavigateToEditExpense(expense.id) }
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
                    Text("Active Groups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    TextButton(onClick = onNavigateToGroups) {
                        Text("View All")
                    }
                }
            }

            if (sortedGroups.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No active groups yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(sortedGroups.take(3), key = { it.id }) { group ->
                    GroupCard(
                        group = group,
                        viewModel = viewModel,
                        onClick = { onNavigateToGroupDetail(group.id) }
                    )
                }
            }
        }
      }

      // Full-screen dimming scrim when FAB menu is expanded
      AnimatedVisibility(
          visible = isFabExpanded,
          enter = fadeIn(animationSpec = tween(200)),
          exit = fadeOut(animationSpec = tween(200)),
          modifier = Modifier.fillMaxSize()
      ) {
          Box(
              modifier = Modifier
                  .fillMaxSize()
                  .background(Color.Black.copy(alpha = 0.5f))
                  .clickable(
                      indication = null,
                      interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                  ) {
                      isFabExpanded = false
                  }
          )
      }

      // Speed Dial Fab Menu rendered on top of dimming scrim at bottom end
      Box(
          modifier = Modifier
              .fillMaxSize()
              .padding(16.dp),
          contentAlignment = Alignment.BottomEnd
      ) {
          SpeedDialFabMenu(
              isExpanded = isFabExpanded,
              onToggle = { isFabExpanded = !isFabExpanded },
              onNavigateToAddExpense = onNavigateToAddExpense,
              onNavigateToGroupExpense = {
                  if (groups.isEmpty()) {
                      onNavigateToCreateGroup()
                  } else {
                      showGroupPickerForExpense = true
                  }
              },
              onNavigateToScanner = onNavigateToScanner,
              onNavigateToCreateGroup = onNavigateToCreateGroup
          )
      }
    }
}

    if (showGroupPickerForExpense) {
        AlertDialog(
            onDismissRequest = { showGroupPickerForExpense = false },
            title = { Text("Select Group for Expense", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 240.dp)) {
                        items(groups, key = { it.id }) { group ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showGroupPickerForExpense = false
                                        onNavigateToAddGroupExpense(group.id)
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(group.name, fontWeight = FontWeight.SemiBold)
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    TextButton(
                        onClick = {
                            showGroupPickerForExpense = false
                            onNavigateToCreateGroup()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.GroupAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("+ Add a New Group", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGroupPickerForExpense = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showNotificationsSheet) {
        NotificationTraySheet(
            viewModel = viewModel,
            activeNotifications = activeNotificationItems,
            onDismiss = { showNotificationsSheet = false }
        )
    }
}

@Composable
fun SpeedDialFabMenu(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onNavigateToAddExpense: () -> Unit,
    onNavigateToGroupExpense: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToCreateGroup: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "fabRotation"
    )

    Box(contentAlignment = Alignment.BottomEnd) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    SpeedDialActionItem(
                        icon = Icons.Rounded.GroupAdd,
                        label = "Create New Group",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle()
                            onNavigateToCreateGroup()
                        }
                    )
                    SpeedDialActionItem(
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        label = "Scan Receipt",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle()
                            onNavigateToScanner()
                        }
                    )
                    SpeedDialActionItem(
                        icon = Icons.Rounded.Groups,
                        label = "Group Expense",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle()
                            onNavigateToGroupExpense()
                        }
                    )
                    SpeedDialActionItem(
                        icon = Icons.Rounded.Person,
                        label = "Personal Expense",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle()
                            onNavigateToAddExpense()
                        }
                    )
                }
            }

            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.semantics { contentDescription = "Quick Actions Menu" }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add Expense",
                    modifier = Modifier.graphicsLayer(rotationZ = rotation)
                )
            }
        }
    }
}

@Composable
fun SpeedDialActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 4.dp,
            tonalElevation = 2.dp
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = CircleShape
        ) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun ExpenseItemCard(expense: Expense, viewModel: FinanceViewModel, onDelete: () -> Unit, onEdit: () -> Unit) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val categoryIcon = getCategoryIcon(expense.category)

    SwipeableItemContainer(
        onDelete = { showDeleteConfirmDialog = true },
        onEdit = onEdit
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val catColor = getCategoryColor(expense.category)
                Surface(
                    shape = CircleShape,
                    color = catColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = null,
                            tint = catColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = expense.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (expense.description.isNotBlank()) {
                        Text(text = expense.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val formattedDate = com.finance.splitup.data.DateUtils.formatDisplayDate(expense.date, expense.updatedAt)
                    Text(
                        text = "$formattedDate • ${expense.paidBy} • ${expense.paymentMethod}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    val expCurrencySymbol = Currencies.getSymbol(expense.currency)
                    val isIncome = expense.category == ExpenseCategories.INCOME
                    val amountColor = if (isIncome) EmeraldSuccess else CrimsonDanger
                    Text(
                        text = if (isIncome) "+$expCurrencySymbol${String.format(Locale.US, "%.2f", expense.amount)}" else "-$expCurrencySymbol${String.format(Locale.US, "%.2f", expense.amount)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = amountColor
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { showDeleteConfirmDialog = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Expense", fontWeight = FontWeight.SemiBold) },
            text = { Text("Are you sure you want to delete '${expense.title}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationTraySheet(
    viewModel: FinanceViewModel,
    activeNotifications: List<Pair<String, String>>,
    onDismiss: () -> Unit
) {
    val dismissedNotifications by viewModel.dismissedNotifications.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .heightIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Notifications", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                if (activeNotifications.isNotEmpty()) {
                    TextButton(onClick = {
                        viewModel.dismissNotifications(activeNotifications.map { it.first })
                    }) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            HorizontalDivider()

            if (activeNotifications.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("All caught up! No active notifications.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (dismissedNotifications.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { viewModel.clearDismissedNotifications() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Cleared Alerts", fontSize = 12.sp)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(activeNotifications, key = { it.first }) { (key, note) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text(note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.dismissNotifications(listOf(key)) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Dismiss Notification", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

