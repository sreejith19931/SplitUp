package com.finance.splitup.data

import com.finance.splitup.ui.screens.detectCategoryFromTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertEquals(45.20, matcher1.group(1)?.replace(",", "")?.toDouble() ?: 0.0, 0.01)

        val matcher2 = pattern.matcher(amexNotification)
        assertTrue(matcher2.find())
        assertEquals(125.50, matcher2.group(1)?.replace(",", "")?.toDouble() ?: 0.0, 0.01)

        val matcher3 = pattern.matcher(gpayInrNotification)
        assertTrue(matcher3.find())
        assertEquals(450.00, matcher3.group(1)?.replace(",", "")?.toDouble() ?: 0.0, 0.01)
    }

    @Test
    fun testMerchantAndCategoryDetection() {
        val title1 = "Whole Foods Market"
        val category1 = detectCategoryFromTitle(title1)
        assertEquals(ExpenseCategories.GROCERIES, category1)

        val title2 = "Uber Ride"
        val category2 = detectCategoryFromTitle(title2)
        assertEquals(ExpenseCategories.TRANSPORT, category2)

        val title3 = "Electric Utility"
        val category3 = detectCategoryFromTitle(title3)
        assertEquals(ExpenseCategories.BILLS, category3)
    }

    @Test
    fun testParseGoogleWalletExpenseNotification() {
        val detected = PaymentNotificationListenerService.parseNotification(
            title = "Google Wallet",
            text = "You spent $45.20 at Whole Foods Market",
            packageName = "com.google.android.apps.walletnf"
        )
        assertNotNull(detected)
        assertEquals(45.20, detected!!.amount, 0.01)
        assertFalse(detected.isIncome)
        assertEquals(ExpenseCategories.GROCERIES, detected.category)
    }

    @Test
    fun testParseAmexExpenseNotification() {
        val detected = PaymentNotificationListenerService.parseNotification(
            title = "Amex Alert",
            text = "Spent USD 125.50 at Apple Store",
            packageName = "com.americanexpress.android.acctsvc"
        )
        assertNotNull(detected)
        assertEquals(125.50, detected!!.amount, 0.01)
        assertFalse(detected.isIncome)
    }

    @Test
    fun testParseGPayInrExpenseNotification() {
        val detected = PaymentNotificationListenerService.parseNotification(
            title = "GPay",
            text = "Paid INR 450.00 to Starbucks via GPay",
            packageName = "com.google.android.apps.nfc"
        )
        assertNotNull(detected)
        assertEquals(450.00, detected!!.amount, 0.01)
        assertFalse(detected.isIncome)
    }

    @Test
    fun testParseIncomeRefundNotification() {
        val detected = PaymentNotificationListenerService.parseNotification(
            title = "Paytm Alert",
            text = "Received INR 1200.00 refund from Amazon",
            packageName = "net.one97.paytm"
        )
        assertNotNull(detected)
        assertEquals(1200.00, detected!!.amount, 0.01)
        assertTrue(detected.isIncome)
    }

    @Test
    fun testParseFormattedCurrencyWithCommas() {
        val detected = PaymentNotificationListenerService.parseNotification(
            title = "Bank Alert",
            text = "Debited $1,250.50 for Laptop Store",
            packageName = "com.chase.sig.android"
        )
        assertNotNull(detected)
        assertEquals(1250.50, detected!!.amount, 0.01)
        assertFalse(detected.isIncome)
    }

    @Test
    fun testParseCurrencySymbolAfterAmount() {
        val detected = PaymentNotificationListenerService.parseNotification(
            title = "Card Notification",
            text = "Charged 89.99 EUR at Hotel Berlin",
            packageName = "com.citi.citimobile"
        )
        assertNotNull(detected)
        assertEquals(89.99, detected!!.amount, 0.01)
        assertEquals(Currencies.EUR, detected.currency)
        assertFalse(detected.isIncome)
    }

    @Test
    fun testParseEuroSymbolNotification() {
        val detected = PaymentNotificationListenerService.parseNotification(
            title = "Bank Notification",
            text = "Paid €42.50 to Bistro Paris",
            packageName = "com.citi.citimobile"
        )
        assertNotNull(detected)
        assertEquals(42.50, detected!!.amount, 0.01)
        assertEquals(Currencies.EUR, detected.currency)
    }

    @Test
    fun testCurrenciesGetSymbol() {
        assertEquals("€", Currencies.getSymbol("EUR"))
        assertEquals("€", Currencies.getSymbol("€"))
        assertEquals("$", Currencies.getSymbol("USD"))
        assertEquals("$", Currencies.getSymbol("$"))
        assertEquals("£", Currencies.getSymbol("GBP"))
        assertEquals("₹", Currencies.getSymbol("INR"))
    }

    @Test
    fun testFilterOwnAppNotifications() {
        val detected = PaymentNotificationListenerService.parseNotification(
            title = "SplitUp Alert",
            text = "Payment of $50.00 recorded",
            packageName = "com.finance.splitup",
            ownPackageName = "com.finance.splitup"
        )
        assertNull(detected)
    }

    @Test
    fun testFilterNonPaymentNotifications() {
        val detected = PaymentNotificationListenerService.parseNotification(
            title = "Chat App",
            text = "Hey! Are we still meeting for lunch at 5pm?",
            packageName = "com.example.chat"
        )
        assertNull(detected)
    }

    @Test
    fun testMerchantNameExtraction() {
        val merchant = PaymentNotificationListenerService.extractMerchantName("Payment to Whole Foods", "Paid $25.00")
        assertEquals("Whole Foods", merchant)

        val merchant2 = PaymentNotificationListenerService.extractMerchantName("Uber Ride", "Spent at Uber Ride")
        assertEquals("Uber Ride", merchant2)
    }

    @Test
    fun testKnownPaymentAppPackages() {
        assertTrue(PaymentNotificationListenerService.isPaymentApp("com.google.android.apps.walletnf"))
        assertTrue(PaymentNotificationListenerService.isPaymentApp("net.one97.paytm"))
        assertTrue(PaymentNotificationListenerService.isPaymentApp("com.phonepe.app"))
        assertFalse(PaymentNotificationListenerService.isPaymentApp("com.example.randomapp"))
    }
}
