package com.finance.splitup.data

import com.finance.splitup.ui.screens.detectCategoryFromTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class PaymentDetectionTest {

    @Test
    fun testMonetaryAmountRegexExtraction() {
        val googleWalletNotification = "You spent $45.20 at Whole Foods Market using Google Wallet"
        val amexNotification = "Amex Alert: Spent USD 125.50 at Apple Store"
        val gpayInrNotification = "Paid INR 450.00 to Starbucks via GPay"

        val pattern = Pattern.compile("(?:[$₹€£]|INR|USD|EUR|GBP)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE)

        val matcher1 = pattern.matcher(googleWalletNotification)
        assertTrue(matcher1.find())
        assertEquals(45.20, matcher1.group(1)?.toDouble() ?: 0.0, 0.01)

        val matcher2 = pattern.matcher(amexNotification)
        assertTrue(matcher2.find())
        assertEquals(125.50, matcher2.group(1)?.toDouble() ?: 0.0, 0.01)

        val matcher3 = pattern.matcher(gpayInrNotification)
        assertTrue(matcher3.find())
        assertEquals(450.00, matcher3.group(1)?.toDouble() ?: 0.0, 0.01)
    }

    @Test
    fun testMerchantAndCategoryDetection() {
        val title1 = "Whole Foods Market"
        val category1 = detectCategoryFromTitle(title1)
        assertEquals(ExpenseCategories.FOOD, category1)

        val title2 = "Uber Ride"
        val category2 = detectCategoryFromTitle(title2)
        assertEquals(ExpenseCategories.TRANSPORT, category2)

        val title3 = "Electric Utility"
        val category3 = detectCategoryFromTitle(title3)
        assertEquals(ExpenseCategories.BILLS, category3)
    }
}
