package com.finance.splitup.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.data.Currencies
import com.finance.splitup.data.ExpenseCategories
import com.finance.splitup.data.db.RecurringExpenseEntity
import com.finance.splitup.viewmodel.FinanceViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val subscriptions by viewModel.recurringExpenses.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSubscription by remember { mutableStateOf<RecurringExpenseEntity?>(null) }

    val totalMonthlyCommitment = remember(subscriptions) {
        subscriptions.sumOf { item ->
            when (item.frequency.lowercase()) {
                "yearly", "annually" -> item.amount / 12.0
                "weekly" -> item.amount * 4.33
                else -> item.amount
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subscriptions & Bills", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add Subscription", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp)
        ) {
            // Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Monthly Commitment",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    "${Currencies.getSymbol(viewModel.defaultCurrency)}${String.format(Locale.US, "%.2f", totalMonthlyCommitment)}/mo",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Repeat,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${subscriptions.size} Active Subscriptions",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Auto-renewal tracking active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Your Subscriptions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (subscriptions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Subscriptions, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("No Subscriptions Tracked Yet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Tap '+ Add Subscription' to track recurring bills like Netflix, Spotify, Rent, or Wi-Fi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(subscriptions, key = { it.id }) { item ->
                    SubscriptionCard(
                        item = item,
                        defaultCurrency = viewModel.defaultCurrency,
                        onClick = { editingSubscription = item },
                        onLogExpense = {
                            viewModel.addExpense(
                                title = item.title,
                                amount = item.amount,
                                category = item.category.ifBlank { ExpenseCategories.BILLS },
                                date = "Today",
                                description = "Recurring Subscription (${item.frequency})",
                                paidBy = "You",
                                currency = item.currency,
                                paymentMethod = "Debit Card"
                            )
                            Toast.makeText(context, "Logged '${item.title}' as personal expense!", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = {
                            viewModel.deleteRecurringExpense(item.id)
                            Toast.makeText(context, "Subscription removed.", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog || editingSubscription != null) {
        AddSubscriptionDialog(
            existingItem = editingSubscription,
            defaultCurrency = viewModel.defaultCurrency,
            onDismiss = { showAddDialog = false; editingSubscription = null },
            onConfirm = { title, amount, category, frequency, nextDueDate, _ ->
                if (editingSubscription != null) {
                    viewModel.upsertRecurringExpense(
                        id = editingSubscription!!.id,
                        title = title,
                        amount = amount,
                        category = category,
                        frequency = frequency,
                        nextDueDate = nextDueDate
                    )
                    Toast.makeText(context, "Updated '$title'!", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addRecurringExpense(
                        title = title,
                        amount = amount,
                        category = category,
                        frequency = frequency,
                        nextDueDate = nextDueDate
                    )
                    Toast.makeText(context, "Added '$title' to subscriptions!", Toast.LENGTH_SHORT).show()
                }
                showAddDialog = false
                editingSubscription = null
            }
        )
    }
}

@Composable
fun SubscriptionCard(
    item: RecurringExpenseEntity,
    defaultCurrency: String,
    onClick: () -> Unit,
    onLogExpense: () -> Unit,
    onDelete: () -> Unit
) {
    val symbol = Currencies.getSymbol(item.currency.ifBlank { defaultCurrency })
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Subscriptions,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column {
                    Text(item.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = item.frequency,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text("• Next: ${item.nextDueDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$symbol${String.format(Locale.US, "%.2f", item.amount)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onLogExpense, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.AddCard, contentDescription = "Log Expense", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSubscriptionDialog(
    existingItem: RecurringExpenseEntity? = null,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, amount: Double, category: String, frequency: String, nextDueDate: String, autoAdd: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(existingItem?.title ?: "") }
    var amountText by remember { mutableStateOf(existingItem?.amount?.let { formatInputNumber(it) } ?: "") }
    var category by remember { mutableStateOf(existingItem?.category?.ifBlank { ExpenseCategories.BILLS } ?: ExpenseCategories.BILLS) }
    var frequency by remember { mutableStateOf(existingItem?.frequency ?: "Monthly") }
    var expandedCategoryDropdown by remember { mutableStateOf(false) }

    val todayMillis = remember {
        java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
    }
    var nextDueDateMillis by remember { mutableStateOf<Long?>(todayMillis) }
    var showDatePicker by remember { mutableStateOf(false) }

    val formattedDueDate = remember(nextDueDateMillis) {
        nextDueDateMillis?.let { millis ->
            java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneId.of("UTC"))
                .format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US))
        } ?: existingItem?.nextDueDate ?: "Select Due Date"
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = nextDueDateMillis ?: todayMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        nextDueDateMillis = datePickerState.selectedDateMillis ?: nextDueDateMillis
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingItem != null) "Edit Subscription" else "Add Subscription", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Subscription Name (e.g. Netflix)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (${Currencies.getSymbol(defaultCurrency)})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Category Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedCategoryDropdown,
                    onExpandedChange = { expandedCategoryDropdown = it }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategoryDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCategoryDropdown,
                        onDismissRequest = { expandedCategoryDropdown = false }
                    ) {
                        ExpenseCategories.all.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    expandedCategoryDropdown = false
                                }
                            )
                        }
                    }
                }

                Text("Frequency", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Weekly", "Monthly", "Yearly").forEach { freq ->
                        FilterChip(
                            selected = frequency == freq,
                            onClick = { frequency = freq },
                            label = { Text(freq) }
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = formattedDueDate,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Next Due Date") },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Rounded.CalendarToday, contentDescription = "Select Date")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showDatePicker = true }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.parseAmount()
                    if (title.isNotBlank() && amt > 0.0) {
                        onConfirm(title, amt, category, frequency, formattedDueDate, true)
                    }
                },
                enabled = title.isNotBlank() && amountText.parseAmount() > 0.0,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (existingItem != null) "Save Changes" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
