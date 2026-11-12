package com.finance.splitup.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.data.Currencies
import com.finance.splitup.ui.theme.*
import com.finance.splitup.viewmodel.FinanceViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit,
    onNavigateToEditExpense: (String) -> Unit,
    onNavigateToGroupDetail: (String) -> Unit
) {
    val expenses by viewModel.expenses.collectAsState()
    val groups by viewModel.groups.collectAsState()

    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val currencySymbol = Currencies.getSymbol(viewModel.defaultCurrency)

    val matchedPersonalExpenses = remember(expenses, query) {
        if (query.isBlank()) emptyList()
        else expenses.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.category.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true) ||
            it.paymentMethod.contains(query, ignoreCase = true)
        }
    }

    val matchedGroups = remember(groups, query) {
        if (query.isBlank()) emptyList()
        else groups.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.type.contains(query, ignoreCase = true) ||
            it.members.any { m -> m.contains(query, ignoreCase = true) }
        }
    }

    val matchedGroupExpenses = remember(groups, query) {
        if (query.isBlank()) emptyList()
        else {
            val results = mutableListOf<Pair<com.finance.splitup.data.Group, com.finance.splitup.data.GroupExpense>>()
            groups.forEach { group ->
                group.expenses.forEach { ge ->
                    if (ge.title.contains(query, ignoreCase = true) ||
                        ge.category.contains(query, ignoreCase = true) ||
                        ge.paidBy.contains(query, ignoreCase = true)) {
                        results.add(group to ge)
                    }
                }
            }
            results
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search personal & group expenses...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        trailingIcon = if (query.isNotBlank()) {
                            {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                }
                            }
                        } else null
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (query.isBlank()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("Type something to search across all personal expenses, groups, and shared bills.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                if (matchedPersonalExpenses.isEmpty() && matchedGroups.isEmpty() && matchedGroupExpenses.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            Text("No results found for '$query'.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (matchedPersonalExpenses.isNotEmpty()) {
                    item {
                        Text("Personal Expenses (${matchedPersonalExpenses.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    items(matchedPersonalExpenses, key = { "per_${it.id}" }) { expense ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onNavigateToEditExpense(expense.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val catColor = getCategoryColor(expense.category)
                                    Surface(
                                        shape = CircleShape,
                                        color = catColor.copy(alpha = 0.12f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(imageVector = getCategoryIcon(expense.category), contentDescription = null, tint = catColor, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Column {
                                        Text(expense.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text("Personal • ${expense.category} • ${com.finance.splitup.data.DateUtils.formatDisplayDate(expense.date, expense.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                }
                                Text(
                                    text = "$currencySymbol${String.format(Locale.US, "%.2f", expense.amount)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (expense.amount < 0) CrimsonDanger else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (matchedGroups.isNotEmpty()) {
                    item {
                        Text("Groups (${matchedGroups.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    items(matchedGroups, key = { "grp_${it.id}" }) { group ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onNavigateToGroupDetail(group.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(imageVector = Icons.Rounded.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Column {
                                        Text(group.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text("Group • Type: ${group.type} • ${group.members.size} members", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                }
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                if (matchedGroupExpenses.isNotEmpty()) {
                    item {
                        Text("Group Expenses (${matchedGroupExpenses.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    items(matchedGroupExpenses, key = { "gexp_${it.second.id}" }) { (group, ge) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onNavigateToGroupDetail(group.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val catColor = getCategoryColor(ge.category)
                                    Surface(
                                        shape = CircleShape,
                                        color = catColor.copy(alpha = 0.12f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(imageVector = getCategoryIcon(ge.category), contentDescription = null, tint = catColor, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Column {
                                        Text(ge.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("Group: ${group.name} • Paid by ${ge.paidBy} • ${com.finance.splitup.data.DateUtils.formatDisplayDate(ge.date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                val gSym = Currencies.getSymbol(ge.currency)
                                Text(
                                    text = "$gSym${String.format(Locale.US, "%.2f", ge.amount)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (ge.amount < 0) CrimsonDanger else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
