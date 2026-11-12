package com.finance.splitup.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

    val currencySymbol = Currencies.getSymbol(currentReceipt.currency)
    val confidencePct = (currentReceipt.confidenceScore * 100).toInt()

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
                            onValueChange = { totalAmount = it },
                            label = { Text("Total ($currencySymbol)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = taxAmount,
                            onValueChange = { taxAmount = it },
                            label = { Text("Tax ($currencySymbol)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = tipAmount,
                            onValueChange = { tipAmount = it },
                            label = { Text("Tip ($currencySymbol)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )
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
                    fontSize = 18.sp
                )
                OutlinedButton(
                    onClick = {
                        val newItem = com.finance.splitup.data.ReceiptLineItem(
                            id = java.util.UUID.randomUUID().toString(),
                            name = "New Item",
                            price = 0.0
                        )
                        lineItems = lineItems + newItem
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Item", fontSize = 12.sp)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(lineItems, key = { it.id }) { item ->
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
                                value = formatInputNumber(item.price),
                                onValueChange = { newPriceStr ->
                                    val newPrice = newPriceStr.parseAmount()
                                    lineItems = lineItems.map { if (it.id == item.id) it.copy(price = newPrice) else it }
                                },
                                label = { Text("Price") },
                                modifier = Modifier.width(95.dp),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                            IconButton(
                                onClick = {
                                    lineItems = lineItems.filter { it.id != item.id }
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

            Button(
                onClick = {
                    val parsedTotal = totalAmount.parseAmount()
                    val parsedTax = taxAmount.parseAmount()
                    val parsedTip = tipAmount.parseAmount()
                    val newTotal = if (parsedTotal > 0.0) parsedTotal else currentReceipt.totalAmount
                    viewModel.currentExtractedReceipt = currentReceipt.copy(
                        merchantName = merchant,
                        totalAmount = newTotal,
                        taxAmount = parsedTax,
                        tipAmount = parsedTip,
                        lineItems = lineItems
                    )
                    onProceedToAllocate()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Group, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Proceed to Cost Allocation", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
