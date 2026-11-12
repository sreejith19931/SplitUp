package com.finance.splitup.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ReceiptLineItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val price: Double,
    val assignedTo: List<String> = emptyList()
)

@Serializable
data class ExtractedReceipt(
    val merchantName: String,
    val date: String,
    val totalAmount: Double,
    val subtotal: Double = 0.0,
    val taxAmount: Double = 0.0,
    val tipAmount: Double = 0.0,
    val lineItems: List<ReceiptLineItem>,
    val currency: String = Currencies.EUR,
    val confidenceScore: Float = 0.98f
)

object SampleReceipts {
    val sampleWholeFoods = ExtractedReceipt(
        merchantName = "Whole Foods Market",
        date = "Today, 2:45 PM",
        totalAmount = 64.25,
        subtotal = 57.72,
        taxAmount = 6.53,
        tipAmount = 0.0,
        lineItems = listOf(
            ReceiptLineItem(name = "Organic Avocados (4pk)", price = 4.99),
            ReceiptLineItem(name = "Almond Milk 1L", price = 3.49),
            ReceiptLineItem(name = "Artisan Sourdough Bread", price = 5.25),
            ReceiptLineItem(name = "Fresh Atlantic Salmon", price = 18.50),
            ReceiptLineItem(name = "Organic Mixed Berries", price = 6.99),
            ReceiptLineItem(name = "Kombucha Ginger 4pk", price = 12.00),
            ReceiptLineItem(name = "Greek Yogurt Organic", price = 6.50),
            ReceiptLineItem(name = "Tax & Deposit", price = 6.53)
        ),
        currency = Currencies.EUR,
        confidenceScore = 0.98f
    )

    val sampleCoffeeShop = ExtractedReceipt(
        merchantName = "Blue Bottle Coffee",
        date = "Today, 9:15 AM",
        totalAmount = 18.75,
        subtotal = 15.00,
        taxAmount = 3.75,
        tipAmount = 0.0,
        lineItems = listOf(
            ReceiptLineItem(name = "Caffe Latte", price = 5.50),
            ReceiptLineItem(name = "Avocado Toast", price = 9.50),
            ReceiptLineItem(name = "Local Tax", price = 3.75)
        ),
        currency = Currencies.EUR,
        confidenceScore = 0.96f
    )
}
