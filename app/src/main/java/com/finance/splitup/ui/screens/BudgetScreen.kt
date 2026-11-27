package com.finance.splitup.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.data.Currencies
import com.finance.splitup.data.Expense
import com.finance.splitup.data.ExpenseCategories
import com.finance.splitup.data.SampleData
import com.finance.splitup.ui.theme.*
import com.finance.splitup.viewmodel.FinanceViewModel
import java.util.Locale

@Composable
fun BudgetScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit
) {
    val expenses by viewModel.expenses.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    BudgetScreen(
        expenses = expenses,
        budgets = budgets,
        defaultCurrency = viewModel.defaultCurrency,
        onUpdateBudget = { category, newLimit -> viewModel.updateBudget(category, newLimit) },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    expenses: List<Expense>,
    budgets: Map<String, Double>,
    defaultCurrency: String,
    onUpdateBudget: (String, Double) -> Unit,
    onBack: () -> Unit
) {
    val currencySymbol = Currencies.getSymbol(defaultCurrency)

    var editingCategory by remember { mutableStateOf<String?>(null) }
    var budgetInput by remember { mutableStateOf("") }

    val categories = remember {
        listOf(
            ExpenseCategories.FOOD,
            ExpenseCategories.TRANSPORT,
            ExpenseCategories.SHOPPING,
            ExpenseCategories.BILLS,
            ExpenseCategories.ENTERTAINMENT,
            ExpenseCategories.OTHER
        )
    }

    val spendingByCategory = remember(expenses, defaultCurrency) {
        expenses.filter { it.amount > 0 && it.category != ExpenseCategories.INCOME }
            .groupBy { it.category }
            .mapValues { entry ->
                entry.value.sumOf { Currencies.convert(it.amount, it.currency, defaultCurrency) }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monthly Category Budgets", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Budget Management", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        Text(
                            "Set monthly spending limits per category. SplitUp will notify you when you approach or exceed your budget limits.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            items(categories, key = { it }) { category ->
                val spent = spendingByCategory[category] ?: 0.0
                // No limit set for this category yet: show "no budget" instead of
                // inventing a phantom 200.00 default.
                val hasBudget = budgets.containsKey(category) && (budgets[category] ?: 0.0) > 0.0
                val limit = budgets[category] ?: 0.0
                val ratio = if (limit > 0.0) (spent / limit).toFloat() else 0f
                val isExceeded = hasBudget && spent > limit

                Surface(
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .semantics { contentDescription = if (hasBudget) "Budget for $category: Spent $currencySymbol$spent of $currencySymbol$limit" else "Budget for $category: Spent $currencySymbol$spent, no limit set" },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isExceeded) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val catColor = getCategoryColor(category)
                                Surface(
                                    shape = CircleShape,
                                    color = catColor.copy(alpha = 0.22f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(imageVector = getCategoryIcon(category), contentDescription = null, tint = catColor, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Column {
                                    Text(ExpenseCategories.getDisplayName(category), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(
                                        if (hasBudget) "$currencySymbol${String.format(Locale.US, "%.2f", spent)} spent of $currencySymbol${String.format(Locale.US, "%.2f", limit)} limit" else "$currencySymbol${String.format(Locale.US, "%.2f", spent)} spent · no budget set",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    editingCategory = category
                                    budgetInput = limit.toString()
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Edit Limit", fontSize = 12.sp, maxLines = 1, softWrap = false)
                            }
                        }

                        val animatedRatio by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = ratio.coerceIn(0f, 1f),
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 900,
                                delayMillis = 120,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                            ),
                            label = "budgetFill"
                        )
                        val barColor by androidx.compose.animation.animateColorAsState(
                            targetValue = if (isExceeded) MaterialTheme.colorScheme.error else getCategoryColor(category),
                            animationSpec = androidx.compose.animation.core.tween(400),
                            label = "budgetBarColor"
                        )
                        LinearProgressIndicator(
                            progress = { animatedRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = barColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        val cal = java.util.Calendar.getInstance()
                        val currentDay = cal.get(java.util.Calendar.DAY_OF_MONTH)
                        val maxDays = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                        val projectedSpent = (spent / currentDay) * maxDays
                        val isProjectedExceeded = !isExceeded && limit > 0 && projectedSpent > limit && currentDay >= 3

                        if (isProjectedExceeded) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "⚡ Pacing Alert: On track to reach $currencySymbol${String.format(Locale.US, "%.2f", projectedSpent)} by month end",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingCategory != null) {
        val cat = editingCategory!!
        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text("Edit Budget for $cat") },
            text = {
                OutlinedTextField(
                    value = budgetInput,
                    onValueChange = { budgetInput = it },
                    label = { Text("Monthly Limit ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newLimit = budgetInput.toDoubleOrNull() ?: 200.0
                        onUpdateBudget(cat, newLimit)
                        editingCategory = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BudgetScreenPreview() {
    SplitUpTheme {
        BudgetScreen(
            expenses = SampleData.initialExpenses,
            budgets = SampleData.initialBudgets,
            defaultCurrency = Currencies.EUR,
            onUpdateBudget = { _, _ -> },
            onBack = {}
        )
    }
}
