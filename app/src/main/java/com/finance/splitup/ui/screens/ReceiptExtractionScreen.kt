package com.finance.splitup.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.data.Currencies
import com.finance.splitup.viewmodel.FinanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptExtractionScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit,
    onProceedToAllocate: () -> Unit
) {
    val currentReceipt = viewModel.currentExtractedReceipt ?: com.finance.splitup.data.SampleReceipts.sampleWholeFoods
    var merchant by remember(currentReceipt) { mutableStateOf(currentReceipt.merchantName) }
    var totalAmount by remember(currentReceipt) { mutableStateOf(formatInputNumber(currentReceipt.totalAmount)) }
    var taxAmount by remember(currentReceipt) { mutableStateOf(formatInputNumber(currentReceipt.taxAmount)) }
    var tipAmount by remember(currentReceipt) { mutableStateOf(formatInputNumber(currentReceipt.tipAmount)) }
    var lineItems by remember(currentReceipt) { mutableStateOf(currentReceipt.lineItems) }
    var itemPriceStrings by remember(currentReceipt) {
        mutableStateOf(currentReceipt.lineItems.associate { it.id to formatInputNumber(it.price) })
    }

    val currencySymbol = Currencies.getSymbol(currentReceipt.currency)
    val confidencePct = (currentReceipt.confidenceScore * 100).toInt()

    val parsedTotal = totalAmount.parseAmount()
    val parsedTax = taxAmount.parseAmount()
    val parsedTip = tipAmount.parseAmount()
    val itemsSum = lineItems.sumOf { (itemPriceStrings[it.id] ?: formatInputNumber(it.price)).parseAmount() }
    val expectedTotal = itemsSum + parsedTax + parsedTip
    val diff = kotlin.math.abs(parsedTotal - expectedTotal)
    val isTotalMatched = diff <= 0.01

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extracted Receipt Details", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ML Kit OCR Scan Results", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("Confidence $confidencePct%", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }

                    OutlinedTextField(
                        value = merchant,
                        onValueChange = { merchant = it },
                        label = { Text("Merchant Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = totalAmount,
                            onValueChange = { totalAmount = filterPriceInput(it) },
                            label = { Text("Total ($currencySymbol)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            value = taxAmount,
                            onValueChange = { taxAmount = filterPriceInput(it) },
                            label = { Text("Tax ($currencySymbol)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            value = tipAmount,
                            onValueChange = { tipAmount = filterPriceInput(it) },
                            label = { Text("Tip ($currencySymbol)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }

                    // Total Matching & Recalculate helper
                    val parsedTotal = totalAmount.parseAmount()
                    val parsedTax = taxAmount.parseAmount()
                    val parsedTip = tipAmount.parseAmount()
                    val itemsSum = lineItems.sumOf { (itemPriceStrings[it.id] ?: formatInputNumber(it.price)).parseAmount() }
                    val expectedTotal = itemsSum + parsedTax + parsedTip
                    val diff = kotlin.math.abs(parsedTotal - expectedTotal)
                    val isTotalMatched = diff <= 0.01

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isTotalMatched) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isTotalMatched) "✅ Total matches item prices" else "⚠️ Discrepancy: $currencySymbol${String.format(java.util.Locale.US, "%.2f", diff)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isTotalMatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Items Sum: $currencySymbol${String.format(java.util.Locale.US, "%.2f", itemsSum)}" + (if (parsedTax > 0 || parsedTip > 0) " (+ Tax/Tip: $currencySymbol${String.format(java.util.Locale.US, "%.2f", parsedTax + parsedTip)})" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!isTotalMatched || parsedTotal == 0.0) {
                                FilledTonalButton(
                                    onClick = {
                                        totalAmount = formatInputNumber(expectedTotal)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Match Total", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Detected Line Items (${lineItems.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        val newId = java.util.UUID.randomUUID().toString()
                        val newItem = com.finance.splitup.data.ReceiptLineItem(
                            id = newId,
                            name = "New Item",
                            price = 0.0
                        )
                        lineItems = lineItems + newItem
                        itemPriceStrings = itemPriceStrings + (newId to "")
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Item", fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                lineItems.forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = item.name,
                                onValueChange = { newName ->
                                    lineItems = lineItems.map { if (it.id == item.id) it.copy(name = newName) else it }
                                },
                                label = { Text("Item") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = itemPriceStrings[item.id] ?: formatInputNumber(item.price),
                                onValueChange = { newPriceStr ->
                                    val filtered = filterPriceInput(newPriceStr)
                                    itemPriceStrings = itemPriceStrings + (item.id to filtered)
                                    val newPrice = filtered.parseAmount()
                                    lineItems = lineItems.map { if (it.id == item.id) it.copy(price = newPrice) else it }
                                },
                                label = { Text("Price") },
                                modifier = Modifier.width(110.dp),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                            IconButton(
                                onClick = {
                                    lineItems = lineItems.filter { it.id != item.id }
                                    itemPriceStrings = itemPriceStrings - item.id
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete item",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            if (!isTotalMatched) {
                Text(
                    text = "⚠️ Price discrepancy detected ($currencySymbol${String.format(java.util.Locale.US, "%.2f", diff)}). Please match total or adjust items before proceeding.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Button(
                onClick = {
                    val updatedLineItems = lineItems.map { item ->
                        val priceStr = itemPriceStrings[item.id] ?: formatInputNumber(item.price)
                        item.copy(price = priceStr.parseAmount())
                    }
                    val newTotal = if (parsedTotal > 0.0) parsedTotal else itemsSum
                    viewModel.currentExtractedReceipt = currentReceipt.copy(
                        merchantName = merchant,
                        totalAmount = newTotal,
                        taxAmount = parsedTax,
                        tipAmount = parsedTip,
                        lineItems = updatedLineItems
                    )
                    onProceedToAllocate()
                },
                enabled = isTotalMatched,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Group, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Proceed to Cost Allocation", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
