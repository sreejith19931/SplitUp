package com.finance.splitup.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.data.Currencies
import com.finance.splitup.ui.theme.*
import com.finance.splitup.viewmodel.FinanceViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit
) {
    val expenses by viewModel.expenses.collectAsState()
    val currencySymbol = Currencies.getSymbol(viewModel.defaultCurrency)

    val investments by viewModel.investments.collectAsState()

    val totalSpent = remember(expenses, viewModel.defaultCurrency) {
        expenses.filter { it.amount > 0 && it.category != com.finance.splitup.data.ExpenseCategories.INCOME }
            .sumOf { Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency) }
    }

    val totalIncome = remember(expenses, viewModel.defaultCurrency) {
        expenses.filter { it.amount > 0 && it.category == com.finance.splitup.data.ExpenseCategories.INCOME }
            .sumOf { Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency) }
    }

    val totalPortfolio = remember(investments, viewModel.defaultCurrency) {
        investments.sumOf { Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency) }
    }

    val categoryBreakdown = remember(expenses, viewModel.defaultCurrency) {
        expenses.filter { it.amount > 0 && it.category != com.finance.splitup.data.ExpenseCategories.INCOME }
            .groupBy { it.category }
            .mapValues { entry ->
                entry.value.sumOf { Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency) }
            }
            .entries.sortedByDescending { it.value }
    }

    val investmentBreakdown = remember(investments, viewModel.defaultCurrency) {
        investments
            .groupBy { it.category }
            .mapValues { entry ->
                entry.value.sumOf { Currencies.convert(it.amount, it.currency, viewModel.defaultCurrency) }
            }
            .entries.sortedByDescending { it.value }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spending & Asset Insights", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Financial Summary", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Total Spending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                AnimatedAmountText(
                                    targetAmount = totalSpent,
                                    currencySymbol = currencySymbol,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Column {
                                Text("Total Income", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                AnimatedAmountText(
                                    targetAmount = totalIncome,
                                    currencySymbol = currencySymbol,
                                    fontSize = 18.sp,
                                    color = EmeraldSuccess
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Investments", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                AnimatedAmountText(
                                    targetAmount = totalPortfolio,
                                    currencySymbol = currencySymbol,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            if (investmentBreakdown.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Portfolio Asset Allocation", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                items(investmentBreakdown) { (category, amount) ->
                    val percentage = if (totalPortfolio > 0.0) (amount / totalPortfolio).toFloat() else 0f
                    Surface(
                        modifier = Modifier.animateItem().fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    com.finance.splitup.data.InvestmentCategories.getEmoji(category),
                                    fontSize = 22.sp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(category, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("$currencySymbol${String.format(Locale.US, "%.2f", amount)} (${String.format(Locale.US, "%.1f", percentage * 100)}%)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { percentage },
                                        modifier = Modifier.fillMaxWidth().height(6.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Expense Category Breakdown", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            if (categoryBreakdown.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No spending records found to generate insights.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(categoryBreakdown) { (category, amount) ->
                    val percentage = if (totalSpent > 0.0) (amount / totalSpent).toFloat() else 0f
                    Surface(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .semantics { contentDescription = "Category $category: $currencySymbol$amount" },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                val catColor = getCategoryColor(category)
                                Surface(
                                    shape = CircleShape,
                                    color = catColor.copy(alpha = 0.22f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(imageVector = getCategoryIcon(category), contentDescription = null, tint = catColor, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(category, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("$currencySymbol${String.format(Locale.US, "%.2f", amount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { percentage },
                                        modifier = Modifier.fillMaxWidth().height(5.dp),
                                        color = catColor,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
