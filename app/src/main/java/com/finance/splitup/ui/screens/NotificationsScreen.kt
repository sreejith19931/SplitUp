package com.finance.splitup.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.ui.theme.CrimsonDanger
import com.finance.splitup.ui.theme.EmeraldSuccess
import com.finance.splitup.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch

private sealed class AlertItem {
    abstract val key: String
    abstract val categoryName: String
    abstract val message: String

    data class GroupBalance(
        override val key: String,
        val groupId: String,
        val groupName: String,
        val amount: Double,
        val isOwed: Boolean,
        override val message: String
    ) : AlertItem() {
        override val categoryName = "Balances"
    }

    data class Budget(
        override val key: String,
        val category: String,
        val isExceeded: Boolean,
        override val message: String
    ) : AlertItem() {
        override val categoryName = "Budgets"
    }

    data class Subscription(
        override val key: String,
        val subscriptionId: String,
        val title: String,
        val isIncome: Boolean = false,
        override val message: String
    ) : AlertItem() {
        override val categoryName = if (isIncome) "Income" else "Subscriptions"
    }
}

private enum class NotificationFilter(val label: String) {
    ALL("All"),
    APPROVALS("Approvals"),
    BALANCES("Balances"),
    BUDGETS("Budgets"),
    SUBSCRIPTIONS("Subscriptions")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit,
    onNavigateToGroupDetail: (String, Int) -> Unit = { _, _ -> },
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
    onNavigateToBudgets: () -> Unit = { onNavigateToInsights() },
    onNavigateToPersonalExpenses: (String?) -> Unit = {}
) {
    val groups by viewModel.groups.collectAsState()
    val recurringExpenses by viewModel.recurringExpenses.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val dismissedNotifications by viewModel.dismissedNotifications.collectAsState()
    val pendingApprovalRequests by viewModel.pendingApprovalRequests.collectAsState()

    var isClearingAll by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(NotificationFilter.ALL) }
    val coroutineScope = rememberCoroutineScope()

    val parsedAlertItems = remember(groups, recurringExpenses, budgets, expenses, viewModel, dismissedNotifications) {
        val list = mutableListOf<AlertItem>()
        groups.forEach { g ->
            val myBal = viewModel.getUserNetBalance(g)
            val key = "group_bal_${g.id}_${String.format(java.util.Locale.US, "%.2f", myBal)}"
            val sym = com.finance.splitup.data.Currencies.getSymbol(viewModel.defaultCurrency)
            if (myBal < -0.01) {
                val msg = "You owe $sym${String.format(java.util.Locale.US, "%.2f", -myBal)} in '${g.name}'."
                if (!dismissedNotifications.contains(key)) {
                    list.add(
                        AlertItem.GroupBalance(
                            key = key,
                            groupId = g.id,
                            groupName = g.name,
                            amount = -myBal,
                            isOwed = false,
                            message = msg
                        )
                    )
                }
            } else if (myBal > 0.01) {
                val msg = "You are owed $sym${String.format(java.util.Locale.US, "%.2f", myBal)} in '${g.name}'."
                if (!dismissedNotifications.contains(key)) {
                    list.add(
                        AlertItem.GroupBalance(
                            key = key,
                            groupId = g.id,
                            groupName = g.name,
                            amount = myBal,
                            isOwed = true,
                            message = msg
                        )
                    )
                }
            }
        }
        recurringExpenses.forEach { r ->
            val isInc = r.isIncome || r.category == com.finance.splitup.data.ExpenseCategories.INCOME
            val key = "recurring_${r.id}"
            val actionText = if (isInc) "expected on" else "due on"
            val msg = "'${r.title}' (${com.finance.splitup.data.Currencies.getSymbol(r.currency)}${r.amount}) $actionText ${r.nextDueDate}."
            if (!dismissedNotifications.contains(key)) {
                list.add(
                    AlertItem.Subscription(
                        key = key,
                        subscriptionId = r.id,
                        title = r.title,
                        isIncome = isInc,
                        message = msg
                    )
                )
            }
        }
        budgets.forEach { (cat, limit) ->
            val spent = viewModel.getSpendingForCategory(cat)
            val spentRounded = String.format(java.util.Locale.US, "%.0f", spent)
            val key = "budget_${cat}_${limit.toInt()}_${spentRounded}"
            val sym = com.finance.splitup.data.Currencies.getSymbol(viewModel.defaultCurrency)
            if (spent >= limit && limit > 0) {
                val msg = "Limit $sym${limit.toInt()} reached ($sym${String.format(java.util.Locale.US, "%.2f", spent)} spent)."
                if (!dismissedNotifications.contains(key)) {
                    list.add(
                        AlertItem.Budget(
                            key = key,
                            category = cat,
                            isExceeded = true,
                            message = msg
                        )
                    )
                }
            } else if (spent >= limit * 0.8 && limit > 0) {
                val msg = "At ${(spent / limit * 100).toInt()}% of $sym${limit.toInt()} limit."
                if (!dismissedNotifications.contains(key)) {
                    list.add(
                        AlertItem.Budget(
                            key = key,
                            category = cat,
                            isExceeded = false,
                            message = msg
                        )
                    )
                }
            }
        }
        list
    }

    val filteredAlertItems = remember(parsedAlertItems, selectedFilter) {
        when (selectedFilter) {
            NotificationFilter.ALL -> parsedAlertItems
            NotificationFilter.BALANCES -> parsedAlertItems.filterIsInstance<AlertItem.GroupBalance>()
            NotificationFilter.BUDGETS -> parsedAlertItems.filterIsInstance<AlertItem.Budget>()
            NotificationFilter.SUBSCRIPTIONS -> parsedAlertItems.filterIsInstance<AlertItem.Subscription>()
            NotificationFilter.APPROVALS -> emptyList()
        }
    }

    val filteredApprovalRequests = remember(pendingApprovalRequests, selectedFilter, viewModel.currentUserName, viewModel.currentUserEmail) {
        val nonSelf = pendingApprovalRequests.filter { req ->
            !viewModel.isUserMatch(req.requestedBy, viewModel.currentUserName, viewModel.currentUserEmail)
        }
        if (selectedFilter == NotificationFilter.ALL || selectedFilter == NotificationFilter.APPROVALS) {
            nonSelf
        } else {
            emptyList()
        }
    }

    val totalActiveAlertsCount = parsedAlertItems.size + pendingApprovalRequests.size
    val hasAnyAlerts = totalActiveAlertsCount > 0

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Notifications Hub", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        if (hasAnyAlerts) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "$totalActiveAlertsCount",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                    }
                },
                actions = {
                    if (hasAnyAlerts) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    isClearingAll = true
                                    kotlinx.coroutines.delay(300L)
                                    if (parsedAlertItems.isNotEmpty()) {
                                        viewModel.dismissNotifications(parsedAlertItems.map { it.key })
                                    }
                                    pendingApprovalRequests.forEach { req ->
                                        viewModel.rejectGroupRequest(req)
                                    }
                                    isClearingAll = false
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    } else if (dismissedNotifications.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearDismissedNotifications() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Cleared", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        ) {
            // Hero Status Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.NotificationsActive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Financial Activity Hub 🔔",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    if (hasAnyAlerts)
                                        "$totalActiveAlertsCount pending items requiring your attention"
                                    else
                                        "All caught up! No active financial alerts.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
                                )
                            }
                        }
                    }
                }
            }



            // Category Filter Chips
            if (hasAnyAlerts) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(NotificationFilter.entries.toTypedArray()) { filter ->
                            val count = when (filter) {
                                NotificationFilter.ALL -> totalActiveAlertsCount
                                NotificationFilter.APPROVALS -> pendingApprovalRequests.size
                                NotificationFilter.BALANCES -> parsedAlertItems.filterIsInstance<AlertItem.GroupBalance>().size
                                NotificationFilter.BUDGETS -> parsedAlertItems.filterIsInstance<AlertItem.Budget>().size
                                NotificationFilter.SUBSCRIPTIONS -> parsedAlertItems.filterIsInstance<AlertItem.Subscription>().size
                            }
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = {
                                    Text(
                                        "${filter.label} ($count)",
                                        fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                }
            }

            // Section: Pending Group Approval Requests
            if (filteredApprovalRequests.isNotEmpty()) {
                item {
                    Text(
                        "Pending Group Requests (${filteredApprovalRequests.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(filteredApprovalRequests, key = { "req_${it.id}" }) { req ->
                    AnimatedVisibility(
                        modifier = Modifier.animateItem(),
                        visible = !isClearingAll,
                        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToGroupDetail(req.groupId, 1) },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    req.requestedBy.take(1).uppercase(),
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                req.groupName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "From ${req.requestedBy}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            req.requestType.replace("_", " "),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text(
                                    "${req.requestedBy} requested ${req.requestType.lowercase().replace("_", " ")} in '${req.groupName}'. Tap to review and approve.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.95f)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { viewModel.approveGroupRequest(req) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = Color.White)
                                    ) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Approve", fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.rejectGroupRequest(req) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonDanger)
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Decline", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section: Active Alerts
            if (filteredAlertItems.isNotEmpty()) {
                item {
                    Text(
                        "Active Alerts (${filteredAlertItems.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                items(filteredAlertItems, key = { it.key }) { item ->
                    AnimatedVisibility(
                        modifier = Modifier.animateItem(),
                        visible = !isClearingAll,
                        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                    ) {
                        val onClickCard: () -> Unit = {
                            when (item) {
                                is AlertItem.GroupBalance -> onNavigateToGroupDetail(item.groupId, 0)
                                is AlertItem.Budget -> onNavigateToBudgets()
                                is AlertItem.Subscription -> if (item.isIncome) onNavigateToPersonalExpenses(null) else onNavigateToSubscriptions()
                            }
                        }

                        val icon = when (item) {
                            is AlertItem.GroupBalance -> if (item.isOwed) Icons.AutoMirrored.Rounded.CallReceived else Icons.AutoMirrored.Rounded.CallMade
                            is AlertItem.Budget -> if (item.isExceeded) Icons.Rounded.Warning else Icons.Rounded.PieChart
                            is AlertItem.Subscription -> if (item.isIncome) Icons.AutoMirrored.Rounded.CallReceived else Icons.Rounded.CalendarToday
                        }

                        val iconContainerColor = when (item) {
                            is AlertItem.GroupBalance -> if (item.isOwed) EmeraldSuccess.copy(alpha = 0.15f) else CrimsonDanger.copy(alpha = 0.15f)
                            is AlertItem.Budget -> if (item.isExceeded) CrimsonDanger.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer
                            is AlertItem.Subscription -> if (item.isIncome) EmeraldSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.tertiaryContainer
                        }

                        val iconColor = when (item) {
                            is AlertItem.GroupBalance -> if (item.isOwed) EmeraldSuccess else CrimsonDanger
                            is AlertItem.Budget -> if (item.isExceeded) CrimsonDanger else MaterialTheme.colorScheme.primary
                            is AlertItem.Subscription -> if (item.isIncome) EmeraldSuccess else MaterialTheme.colorScheme.tertiary
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onClickCard),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = iconContainerColor,
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            when (item) {
                                                is AlertItem.GroupBalance -> if (item.isOwed) "Owed Money • ${item.groupName}" else "Balance Owed • ${item.groupName}"
                                                is AlertItem.Budget -> if (item.isExceeded) "Budget Exceeded • ${item.category}" else "Budget Warning • ${item.category}"
                                                is AlertItem.Subscription -> if (item.isIncome) "Recurring Income • ${item.title}" else "Subscription Due • ${item.title}"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        item.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.dismissNotifications(listOf(item.key)) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "Dismiss",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Empty State Card
            if (filteredAlertItems.isEmpty() && filteredApprovalRequests.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = EmeraldSuccess.copy(alpha = 0.15f),
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                            Text(
                                "All Caught Up! ✨",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (selectedFilter == NotificationFilter.ALL)
                                    "No pending group approval requests, budget alerts, or upcoming subscription reminders."
                                else
                                    "No active alerts found in category '${selectedFilter.label}'.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            if (dismissedNotifications.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedButton(
                                    onClick = { viewModel.clearDismissedNotifications() },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Reset Cleared Alerts", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
