package com.finance.splitup.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.data.Currencies
import com.finance.splitup.data.Expense
import com.finance.splitup.data.ExpenseCategories
import com.finance.splitup.ui.theme.*
import java.util.Locale

@Composable
fun SpendingDonutChart(
    expenses: List<Expense>,
    modifier: Modifier = Modifier,
    selectedCategory: String? = null,
    onCategorySelected: ((String?) -> Unit)? = null,
    defaultCurrency: String = Currencies.EUR
) {
    val spendingExpenses = remember(expenses) { expenses.filter { it.category != ExpenseCategories.INCOME } }
    val totalSpent = remember(spendingExpenses, defaultCurrency) {
        spendingExpenses.sumOf { Currencies.convert(it.amount, it.currency, defaultCurrency) }
    }
    val currencySymbol = Currencies.getSymbol(defaultCurrency)
    val spendingCategories = remember { ExpenseCategories.all.filter { it != ExpenseCategories.INCOME } }
    val categoryAmounts = remember(spendingExpenses, defaultCurrency) {
        spendingCategories.associateWith { category ->
            spendingExpenses.filter { it.category == category }
                .sumOf { Currencies.convert(it.amount, it.currency, defaultCurrency) }
        }
    }

    val categoryColors = remember { spendingCategories.associateWith { getCategoryColor(it) } }

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(spendingExpenses) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = modifier
            .size(180.dp)
            .semantics { contentDescription = "Spending donut chart showing total spent $currencySymbol$totalSpent" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 26f
            val selectedStrokeWidth = 34f
            val baseDiameter = size.minDimension - selectedStrokeWidth
            val topLeft = Offset(selectedStrokeWidth / 2f, selectedStrokeWidth / 2f)
            val canvasSize = Size(baseDiameter, baseDiameter)

            var startAngle = -90f
            val total = if (totalSpent <= 0.0) 1.0 else totalSpent
            val currentProgress = animationProgress.value

            for (category in spendingCategories) {
                val amount = categoryAmounts[category] ?: 0.0
                if (amount <= 0.0) continue
                val fullSweep = ((amount / total) * 360f).toFloat()
                val sweepAngle = fullSweep * currentProgress
                val color = categoryColors[category] ?: Color.Gray
                val isSelected = selectedCategory == category

                val actualStroke = if (isSelected) selectedStrokeWidth else strokeWidth
                drawArc(
                    color = color.copy(alpha = if (selectedCategory == null || isSelected) 1f else 0.4f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = canvasSize,
                    style = Stroke(width = actualStroke, cap = StrokeCap.Round)
                )
                startAngle += fullSweep
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { onCategorySelected?.invoke(null) }
        ) {
            Text(
                text = selectedCategory ?: "Total Spent",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            val displayedAmount = remember(selectedCategory, categoryAmounts, totalSpent) {
                if (selectedCategory != null) categoryAmounts[selectedCategory] ?: 0.0 else totalSpent
            }
            Text(
                text = "$currencySymbol${String.format(Locale.US, "%.2f", displayedAmount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (selectedCategory != null) {
                val selectedAmt = categoryAmounts[selectedCategory] ?: 0.0
                val percent = if (totalSpent > 0) (selectedAmt / totalSpent * 100).toInt() else 0
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "$percent% of total",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SpendingVelocityInsightCard(expenses: List<Expense>, defaultCurrency: String = Currencies.EUR) {
    val spendingExpenses = remember(expenses) { expenses.filter { it.category != ExpenseCategories.INCOME } }
    val totalSpent = remember(spendingExpenses, defaultCurrency) {
        spendingExpenses.sumOf { Currencies.convert(it.amount, it.currency, defaultCurrency) }
    }
    val insightText = remember(totalSpent) {
        when {
            totalSpent < 100.0 -> "Financial Health: Excellent! Very low spending velocity this month."
            totalSpent < 300.0 -> "Financial Health: Good pacing. Spending matches historical averages."
            totalSpent < 600.0 -> "Financial Health: Moderate velocity. Keep an eye on dining & shopping."
            else -> "Financial Health: High spending velocity. Consider reviewing budget limits."
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = insightText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Unified Component combining Monthly Budget Status & Donut Category Analytics into a single card.
 */
@Composable
fun BudgetAndCategoryAnalyticsCard(
    expenses: List<Expense>,
    budgets: Map<String, Double>,
    defaultCurrency: String = Currencies.EUR,
    onCategoryClick: (String) -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(true) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var expandedCategoryAccordion by remember { mutableStateOf<String?>(null) }

    val spendingExpenses = remember(expenses) { expenses.filter { it.category != ExpenseCategories.INCOME } }
    val totalSpent = remember(spendingExpenses, defaultCurrency) {
        spendingExpenses.sumOf { Currencies.convert(it.amount, it.currency, defaultCurrency) }
    }
    val totalLimit = remember(budgets) { budgets.values.sum() }
    val currencySymbol = Currencies.getSymbol(defaultCurrency)
    val spendingCategories = remember { ExpenseCategories.all.filter { it != ExpenseCategories.INCOME } }
    val categoryColors = remember { spendingCategories.associateWith { getCategoryColor(it) } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.PieChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text("Budget & Category Analytics", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = "$currencySymbol${String.format(Locale.US, "%.2f", totalSpent)} spent of $currencySymbol${String.format(Locale.US, "%.2f", totalLimit)} limit",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Donut Chart
                    SpendingDonutChart(
                        expenses = expenses,
                        defaultCurrency = defaultCurrency,
                        selectedCategory = selectedCategoryFilter,
                        onCategorySelected = { cat ->
                            selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat
                        }
                    )

                    // Financial Velocity Insight Banner
                    SpendingVelocityInsightCard(expenses = expenses, defaultCurrency = defaultCurrency)

                    // Unified Category List with Budget Progress & Expenses Accordion
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        spendingCategories.forEach { category ->
                            val categoryExpenses = remember(spendingExpenses, category) {
                                spendingExpenses.filter { it.category == category }
                            }
                            val spent = remember(categoryExpenses, defaultCurrency) {
                                categoryExpenses.sumOf { Currencies.convert(it.amount, it.currency, defaultCurrency) }
                            }
                            val limit = budgets[category] ?: 200.0
                            val rawProgress = if (limit > 0) (spent / limit).toFloat() else 0f
                            val animatedProgress by animateFloatAsState(
                                targetValue = rawProgress.coerceIn(0f, 1f),
                                animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                                label = "progressAnimation"
                            )
                            val percentOfTotal = if (totalSpent > 0) (spent / totalSpent * 100).toInt() else 0
                            val color = categoryColors[category] ?: Color.Gray

                            val isCategoryAccordionExpanded = expandedCategoryAccordion == category
                            val isSelectedInChart = selectedCategoryFilter == category

                            val progressColor = when {
                                rawProgress >= 1.0f -> CrimsonDanger
                                rawProgress >= 0.8f -> MaterialTheme.colorScheme.primary
                                else -> EmeraldSuccess
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedCategoryFilter = if (selectedCategoryFilter == category) null else category
                                        onCategoryClick(category)
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelectedInChart || isCategoryAccordionExpanded) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                            )
                                            Text(
                                                text = category,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (rawProgress >= 1.0f) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = CrimsonDanger.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "Exceeded",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = CrimsonDanger,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "$currencySymbol${String.format(Locale.US, "%.2f", spent)} / $currencySymbol${String.format(Locale.US, "%.0f", limit)} ($percentOfTotal%)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (rawProgress >= 1.0f) CrimsonDanger else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = progressColor,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )

                                    AnimatedVisibility(
                                        visible = isCategoryAccordionExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                            if (categoryExpenses.isEmpty()) {
                                                Text(
                                                    text = "No expenses recorded in $category yet.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                )
                                            } else {
                                                categoryExpenses.forEach { exp ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp, horizontal = 2.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = exp.title,
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontSize = 13.sp,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text(
                                                                text = "${com.finance.splitup.data.DateUtils.formatDisplayDate(exp.date, exp.updatedAt)} • Paid by ${exp.paidBy}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                        Text(
                                                            text = "$currencySymbol${String.format(Locale.US, "%.2f", exp.amount)}",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            color = MaterialTheme.colorScheme.primary
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
                }
            }
        }
    }
}
