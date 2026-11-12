package com.finance.splitup.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.animation.core.*
import com.finance.splitup.data.Currencies
import com.finance.splitup.data.ExpenseCategories
import com.finance.splitup.data.Group
import com.finance.splitup.ui.theme.*
import com.finance.splitup.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun GroupsScreen(
    viewModel: FinanceViewModel,
    onNavigateToGroupDetail: (String) -> Unit,
    onNavigateToCreateGroup: () -> Unit,
    onNavigateToGlobalSearch: () -> Unit = {},
    onNavigateToFindFriends: () -> Unit = {}
) {
    val groups by viewModel.groups.collectAsState()
    var selectedGroupId by remember { mutableStateOf<String?>(groups.firstOrNull()?.id) }
    var selectedGroupTypeFilter by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    val filteredGroups = remember(groups, selectedGroupTypeFilter) {
        groups.filter { group ->
            if (selectedGroupTypeFilter == "Archived") {
                group.isArchived
            } else {
                !group.isArchived && (selectedGroupTypeFilter == null || group.type.equals(selectedGroupTypeFilter, ignoreCase = true))
            }
        }
    }

    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val coroutineScope = rememberCoroutineScope()
    var showJoinDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Groups", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                            actions = {
                                IconButton(onClick = onNavigateToFindFriends) {
                                    Icon(Icons.Rounded.People, contentDescription = "Find Friends", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { showJoinDialog = true }) {
                                    Icon(Icons.Rounded.Link, contentDescription = "Join Group by ID", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = onNavigateToGlobalSearch) {
                                    Icon(Icons.Rounded.Search, contentDescription = "Global Search", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = onNavigateToCreateGroup) {
                                    Icon(Icons.Rounded.GroupAdd, contentDescription = "Create Group", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = onNavigateToCreateGroup,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "New Group")
                        }
                    }
                ) { innerPadding ->
                    if (showJoinDialog) {
                        JoinGroupDialog(
                            onDismiss = { showJoinDialog = false },
                            onJoin = { groupId ->
                                showJoinDialog = false
                                viewModel.joinGroupViaInvite(
                                    groupId = groupId,
                                    onJoined = {
                                        onNavigateToGroupDetail(groupId)
                                    },
                                    onError = { err ->
                                        android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        )
                    }
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            viewModel.refreshData()
                            isRefreshing = false
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            item {
                                val currencySymbol = com.finance.splitup.data.Currencies.getSymbol(viewModel.defaultCurrency)
                                val totalNetOwed = groups.sumOf { group ->
                                    viewModel.getUserNetBalance(group, viewModel.defaultCurrency)
                                }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            modifier = Modifier.size(52.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.PieChart,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(28.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text("Total Net Balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                            AnimatedAmountText(
                                                targetAmount = if (totalNetOwed >= 0) totalNetOwed else -totalNetOwed,
                                                currencySymbol = currencySymbol,
                                                prefix = if (totalNetOwed >= 0) "+" else "-",
                                                fontSize = 24.sp,
                                                color = if (totalNetOwed >= 0.01) EmeraldSuccess else if (totalNetOwed < -0.01) CrimsonDanger else MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text("Across all active shared groups", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                                        }
                                    }
                                }
                            }

                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item {
                                        FilterChip(
                                            selected = selectedGroupTypeFilter == null,
                                            onClick = { selectedGroupTypeFilter = null },
                                            label = { Text("All") },
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                    val types = listOf("Trip", "Roommates", "Dinner", "Other", "Archived")
                                    items(types) { type ->
                                        FilterChip(
                                            selected = selectedGroupTypeFilter == type,
                                            onClick = {
                                                selectedGroupTypeFilter = if (selectedGroupTypeFilter == type) null else type
                                            },
                                            label = { Text(type) },
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                            }

                            item {
                                Text(if (selectedGroupTypeFilter == "Archived") "Archived Groups" else "Your Active Groups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }

                            if (filteredGroups.isEmpty()) {
                                if (isRefreshing) {
                                    items(3) {
                                        ShimmerGroupCard()
                                    }
                                } else {
                                    item {
                                        com.finance.splitup.ui.components.EmptyStateComponent(
                                            icon = Icons.Rounded.Groups,
                                            title = "No Groups Found",
                                            description = "Create a group or join via invite link to split expenses with friends and family!",
                                            primaryActionText = "Create Group",
                                            onPrimaryAction = onNavigateToCreateGroup
                                        )
                                    }
                                }
                            } else {
                                items(filteredGroups) { group ->
                                    GroupCard(
                                        group = group,
                                        viewModel = viewModel,
                                        onClick = {
                                            selectedGroupId = group.id
                                            if (navigator.scaffoldDirective.maxHorizontalPartitions > 1) {
                                                coroutineScope.launch {
                                                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, group.id)
                                                }
                                            } else {
                                                onNavigateToGroupDetail(group.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                val selectedGroup = groups.find { it.id == selectedGroupId } ?: groups.firstOrNull()
                if (selectedGroup != null) {
                    GroupDetailContent(
                        group = selectedGroup,
                        viewModel = viewModel,
                        onAddExpense = { onNavigateToGroupDetail(selectedGroup.id) },
                        onBack = if (navigator.scaffoldDirective.maxHorizontalPartitions == 1) {
                            {
                                coroutineScope.launch {
                                    navigator.navigateBack()
                                }
                            }
                        } else null
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select a group to view details", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    )
}

@Composable
fun GroupCard(
    group: Group,
    viewModel: FinanceViewModel,
    onClick: () -> Unit
) {
    val userBalance = viewModel.getUserNetBalance(group, viewModel.defaultCurrency)
    val totalBalance = group.expenses
        .filter { it.category != ExpenseCategories.SETTLEMENT && !it.title.contains("Settlement", ignoreCase = true) }
        .sumOf { 
            val amt = Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency)
            if (it.category == ExpenseCategories.INCOME || it.amount < 0) -kotlin.math.abs(amt) else kotlin.math.abs(amt)
        }
    val currencySymbol = Currencies.getSymbol(viewModel.defaultCurrency)

    val statusText = when {
        userBalance > 0.01 -> "You are owed $currencySymbol${String.format(Locale.US, "%.2f", userBalance)}"
        userBalance < -0.01 -> "You owe $currencySymbol${String.format(Locale.US, "%.2f", -userBalance)}"
        else -> "Settled up"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (group.type) {
                            "Trip" -> Icons.Rounded.Flight
                            "Roommates" -> Icons.Rounded.Home
                            "Dinner" -> Icons.Rounded.Restaurant
                            else -> Icons.Rounded.Groups
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = group.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Person, contentDescription = "Members", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "${group.members.size} • Total: $currencySymbol${String.format(Locale.US, "%.2f", totalBalance)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            userBalance > 0.01 -> EmeraldSuccess.copy(alpha = 0.15f)
                            userBalance < -0.01 -> CrimsonDanger.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                userBalance > 0.01 -> EmeraldSuccess
                                userBalance < -0.01 -> CrimsonDanger
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = "View", tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun JoinGroupDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var groupIdInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Group by ID", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter or paste the Group ID shared by your friend:")
                OutlinedTextField(
                    value = groupIdInput,
                    onValueChange = { groupIdInput = it },
                    label = { Text("Group ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (groupIdInput.isNotBlank()) {
                        onJoin(groupIdInput.trim())
                    }
                }
            ) {
                Text("Join Group")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun ShimmerGroupCard() {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.5f),
                modifier = Modifier.size(44.dp)
            ) {}
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.5f),
                    modifier = Modifier.width(140.dp).height(16.dp)
                ) {}
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.3f),
                    modifier = Modifier.width(90.dp).height(12.dp)
                ) {}
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GroupsScreenPreview() {
    SplitUpTheme {
        val viewModel = remember { FinanceViewModel() }
        GroupsScreen(viewModel = viewModel, onNavigateToGroupDetail = {}, onNavigateToCreateGroup = {})
    }
}
