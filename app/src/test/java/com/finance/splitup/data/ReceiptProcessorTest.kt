package com.finance.splitup.data

import com.finance.splitup.ui.screens.parseAmount
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
        assertEquals(5.00, sumOfItems, 0.01)
    }

    @Test
    fun testEuropeanCommaPriceParsing() {
        val ocrText = """
            SIMMEL
            Pringles H.Cheese 1,89
            Herz.Gouda mittel 2,15
            SUMME € 25,12
        """.trimIndent()

        val extracted = ReceiptProcessor.parseReceiptText(ocrText)

        assertEquals("SIMMEL", extracted.merchantName)
        assertEquals(25.12, extracted.totalAmount, 0.01)
        assertTrue(extracted.lineItems.any { it.name.contains("Pringles", ignoreCase = true) && kotlin.math.abs(it.price - 1.89) < 0.01 })
        assertTrue(extracted.lineItems.any { it.name.contains("Gouda", ignoreCase = true) && kotlin.math.abs(it.price - 2.15) < 0.01 })
    }

    @Test
    fun testCommaAndDotParseAmount() {
        assertEquals(25.12, "25,12".parseAmount(), 0.01)
        assertEquals(25.12, "25.12".parseAmount(), 0.01)
        assertEquals(1.89, "€ 1,89".parseAmount(), 0.01)
        assertEquals(2.0, "2,".parseAmount(), 0.01)
    }

    @Test
    fun testMerchantHeaderExtractionWithAddressAndDecorativeChars() {
        val ocrText = """
            *** WHOLE FOODS MARKET ***
            123 MAIN STREET
            TEL: (555) 019-2834
            STORE #10294
            05/12/2024 14:45
            Organic Avocados 4.99
            TOTAL 4.99
        """.trimIndent()

        val extracted = ReceiptProcessor.parseReceiptText(ocrText)
        assertEquals("WHOLE FOODS MARKET", extracted.merchantName)
        assertEquals(1, extracted.lineItems.size)
        assertEquals("Organic Avocados", extracted.lineItems.first().name)
    }

    @Test
    fun testItemCleaningWithQuantitiesWeightsAndTrailingTax() {
        val ocrText = """
            WELCOME TO TARGET
            Store #402
            0.50 lb @ 4.00 /lb Organic Avocados 2.00 T
            2 x Almond Milk 1L @ 3.49 6.98 T
            SKU# 98234 Artisan Sourdough Bread 5.25
            SUBTOTAL 14.23
            TAX 1.20
            TOTAL 15.43
        """.trimIndent()

        val extracted = ReceiptProcessor.parseReceiptText(ocrText)
        assertEquals("TARGET", extracted.merchantName)
        assertEquals(3, extracted.lineItems.size)
        assertEquals("Organic Avocados", extracted.lineItems[0].name)
        assertEquals(2.00, extracted.lineItems[0].price, 0.01)
        assertEquals("Almond Milk 1L", extracted.lineItems[1].name)
        assertEquals(6.98, extracted.lineItems[1].price, 0.01)
        assertEquals("Artisan Sourdough Bread", extracted.lineItems[2].name)
        assertEquals(5.25, extracted.lineItems[2].price, 0.01)
    }

    @Test
    fun testSimmelGermanReceiptParsing() {
        val ocrText = """
            Peter Simmel Handels GmbH
            Parkackerstr. 1
            82008 Unterhaching
            Tel. 089 / 449991380
            www.simmel.de

            Pringles H.Cheese 1,89 AW
            App Preis / Rabatt -0,20
            Herz.Gouda mittel. 2,15 AW
            Tomaten 1,32 AW
            0,330 kg x 3,99 €/kg
            Ananas Gesch 4,95 A
            G&G Milchbrötch. 1,69 A
            EGEN Clementinen 1,99 AW
            Käs.Güntensberg 8,10 A
            Herz.High Pr.Wraps 1,49 A
            G&G Vollkorntoast 0,89 A
            G&G Fettarme Milch 0,85 A

            Posten: 10
            SUMME € 25,12
            Mastercard € 25,12
        """.trimIndent()

        val extracted = ReceiptProcessor.parseReceiptText(ocrText)
        assertEquals("Peter Simmel Handels GmbH", extracted.merchantName)
        assertEquals(25.12, extracted.totalAmount, 0.01)
        assertEquals(10, extracted.lineItems.size)
        assertTrue(extracted.lineItems.any { it.name.contains("Pringles", ignoreCase = true) && kotlin.math.abs(it.price - 1.89) < 0.01 })
        assertTrue(extracted.lineItems.any { it.name.contains("Gouda", ignoreCase = true) && kotlin.math.abs(it.price - 2.15) < 0.01 })
        assertTrue(extracted.lineItems.any { it.name.contains("Tomaten", ignoreCase = true) && kotlin.math.abs(it.price - 1.32) < 0.01 })
        assertTrue(extracted.lineItems.any { it.name.contains("Ananas", ignoreCase = true) && kotlin.math.abs(it.price - 4.95) < 0.01 })
        assertTrue(extracted.lineItems.any { it.name.contains("Milch", ignoreCase = true) && kotlin.math.abs(it.price - 0.85) < 0.01 })
    }
}
