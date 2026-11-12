package com.finance.splitup.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.finance.splitup.MainActivity
import com.finance.splitup.ui.screens.detectCategoryFromTitle
import java.util.Locale
import java.util.regex.Pattern

class PaymentNotificationListenerService : NotificationListenerService() {

    companion object {
        const val ACTION_REVIEW_TRANSACTION = "com.finance.splitup.ACTION_REVIEW_TRANSACTION"
        const val EXTRA_REVIEW_DETECTED_TRANSACTION = "EXTRA_REVIEW_DETECTED_TRANSACTION"
        const val EXTRA_DETECTED_AMOUNT = "EXTRA_DETECTED_AMOUNT"
        const val EXTRA_DETECTED_TITLE = "EXTRA_DETECTED_TITLE"
        const val EXTRA_DETECTED_IS_INCOME = "EXTRA_DETECTED_IS_INCOME"
        const val EXTRA_DETECTED_CATEGORY = "EXTRA_DETECTED_CATEGORY"

        private const val CHANNEL_ID = "splitup_payment_detection_channel"
        private const val NOTIFICATION_ID = 2002

        fun isNotificationListenerEnabled(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return flat != null && flat.contains(packageName)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName ?: return

        // Skip our own notifications to avoid recursion
        if (packageName == applicationContext.packageName) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val fullContent = "$title $text $bigText".trim()

        if (fullContent.isBlank()) return

        // Regex pattern to extract monetary amounts e.g., $45.20, USD 45, INR 500, €12.50, £9.99
        val amountPattern = Pattern.compile("(?:[$₹€£]|INR|USD|EUR|GBP)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE)
        val matcher = amountPattern.matcher(fullContent)

        var detectedAmount: Double? = null
        if (matcher.find()) {
            val rawNum = matcher.group(1)?.replace(",", "")
            detectedAmount = rawNum?.toDoubleOrNull()
        }

        // Alternative regex if currency symbol is after amount e.g. 45.20 USD
        if (detectedAmount == null) {
            val altPattern = Pattern.compile("([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:[$₹€£]|INR|USD|EUR|GBP)", Pattern.CASE_INSENSITIVE)
            val altMatcher = altPattern.matcher(fullContent)
            if (altMatcher.find()) {
                val rawNum = altMatcher.group(1)?.replace(",", "")
                detectedAmount = rawNum?.toDoubleOrNull()
            }
        }

        // Only proceed if a valid transaction amount was parsed
        if (detectedAmount != null && detectedAmount > 0) {
            val lowerContent = fullContent.lowercase()

            val expenseKeywords = listOf("spent", "paid", "debited", "charged", "purchase", "sent", "withdrawn", "payment of")
            val incomeKeywords = listOf("received", "credited", "refund", "deposit", "added", "got")

            val isExpense = expenseKeywords.any { lowerContent.contains(it) }
            val isIncome = incomeKeywords.any { lowerContent.contains(it) }

            if (isExpense || isIncome || isPaymentApp(packageName)) {
                val transactionIsIncome = isIncome && !isExpense

                // Extract potential merchant or title
                val merchantName = extractMerchantName(title, text)
                val category = detectCategoryFromTitle(merchantName)

                postReviewNotification(
                    context = applicationContext,
                    amount = detectedAmount,
                    merchantTitle = merchantName,
                    isIncome = transactionIsIncome,
                    category = category
                )
            }
        }
    }

    private fun isPaymentApp(pkg: String): Boolean {
        val knownPaymentPackages = listOf(
            "com.google.android.apps.walletnf",
            "com.google.android.apps.nfc",
            "com.americanexpress.android.acctsvc",
            "com.chase.sig.android",
            "com.citi.citimobile",
            "com.infonow.bofa",
            "net.one97.paytm",
            "com.phonepe.app",
            "com.whatsapp"
        )
        return knownPaymentPackages.any { pkg.contains(it, ignoreCase = true) }
    }

    private fun extractMerchantName(title: String, text: String): String {
        val combined = if (title.isNotBlank() && title.length < 35) title else text
        // Clean up common prefix words
        val cleaned = combined
            .replace(Regex("(?i)payment (?:to|at|from)|spent at|debited for|paid to"), "")
            .trim()

        return if (cleaned.length in 2..40) cleaned else "Detected Transaction"
    }

    private fun postReviewNotification(
        context: Context,
        amount: Double,
        merchantTitle: String,
        isIncome: Boolean,
        category: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Payment Detection Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for automatically detected payment app transactions"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val targetIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_REVIEW_DETECTED_TRANSACTION, true)
            putExtra(EXTRA_DETECTED_AMOUNT, amount)
            putExtra(EXTRA_DETECTED_TITLE, merchantTitle)
            putExtra(EXTRA_DETECTED_IS_INCOME, isIncome)
            putExtra(EXTRA_DETECTED_CATEGORY, category)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val typeText = if (isIncome) "Income" else "Expense"
        val formattedAmount = String.format(Locale.getDefault(), "%.2f", amount)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Payment Detected: $merchantTitle")
            .setContentText("Detected $typeText of $$formattedAmount. Tap to review and add to SplitUp.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Detected $typeText of $$formattedAmount ($merchantTitle). Tap to review and pre-fill into SplitUp."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID + (amount * 100).toInt() % 1000, builder.build())
    }
}
