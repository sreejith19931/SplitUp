package com.finance.splitup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptProcessorTest {

    @Test
    fun testSubtotalVsTotalDiscrimination() {
        val ocrText = """
            Trader Joe's
            05/12/2024
            Organic Eggs 4.99
            Almond Milk 3.50
            SUBTOTAL 8.49
            TAX 0.85
            TOTAL 9.34
        """.trimIndent()

        val extracted = ReceiptProcessor.parseReceiptText(ocrText)

        assertEquals("Trader Joe's", extracted.merchantName)
        assertEquals(9.34, extracted.totalAmount, 0.01)
        assertTrue(extracted.lineItems.any { it.name.contains("Organic Eggs", ignoreCase = true) })
        assertTrue(extracted.lineItems.any { it.name.contains("Almond Milk", ignoreCase = true) })
        // Subtotal and Total lines should not be listed as purchase items
        assertTrue(extracted.lineItems.none { it.name.contains("SUBTOTAL", ignoreCase = true) })
        assertTrue(extracted.lineItems.none { it.name.contains("TOTAL", ignoreCase = true) })
    }

    @Test
    fun testCurrencyDetectionUSD() {
        val ocrText = """
            Starbucks Coffee
            Caffe Latte $5.50
            TOTAL $5.50 USD
        """.trimIndent()

        val extracted = ReceiptProcessor.parseReceiptText(ocrText)

        assertEquals(Currencies.USD, extracted.currency)
        assertEquals(5.50, extracted.totalAmount, 0.01)
    }

    @Test
    fun testCurrencyDetectionEUR() {
        val ocrText = """
            Boulangerie Paris
            Croissant 2.50 €
            Espresso 2.00 €
            TOTAL 4.50 €
        """.trimIndent()

        val extracted = ReceiptProcessor.parseReceiptText(ocrText)

        assertEquals(Currencies.EUR, extracted.currency)
        assertEquals(4.50, extracted.totalAmount, 0.01)
    }

    @Test
    fun testSingleItemBillWithCodePaymentAndTaxBreakdown() {
        val ocrText = """
            Electronics Store
            01/11/2024
            Wireless Mouse 29.99 €
            Code 987654321 29.99 €
            TOTAL 29.99 €
            CARD / VISA 29.99 €
            NET 25.20 €
            VAT 19% 4.79 €
        """.trimIndent()

        val extracted = ReceiptProcessor.parseReceiptText(ocrText)

        assertEquals("Electronics Store", extracted.merchantName)
        assertEquals(29.99, extracted.totalAmount, 0.01)
        assertEquals(1, extracted.lineItems.size)
        assertEquals("Wireless Mouse", extracted.lineItems.first().name)
        assertEquals(29.99, extracted.lineItems.first().price, 0.01)
    }

    @Test
    fun testItemCostReconciliationAgainstTotal() {
        val ocrText = """
            Supermarket
            Apples 3.00
            Bread 2.00
            SUBTOTAL 5.00
            TAX 0.50
            TOTAL 5.50
        """.trimIndent()

        val extracted = ReceiptProcessor.parseReceiptText(ocrText)

        assertEquals(5.50, extracted.totalAmount, 0.01)
        val sumOfItems = extracted.lineItems.sumOf { it.price }
        assertEquals(5.50, sumOfItems, 0.01)
    }
}
