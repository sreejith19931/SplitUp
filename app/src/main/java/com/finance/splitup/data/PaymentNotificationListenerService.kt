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

data class DetectedPaymentTransaction(
    val amount: Double,
    val merchantTitle: String,
    val isIncome: Boolean,
    val category: String,
    val currency: String = Currencies.EUR
)

class PaymentNotificationListenerService : NotificationListenerService() {

    companion object {
        const val ACTION_REVIEW_TRANSACTION = "com.finance.splitup.ACTION_REVIEW_TRANSACTION"
        const val EXTRA_REVIEW_DETECTED_TRANSACTION = "EXTRA_REVIEW_DETECTED_TRANSACTION"
        const val EXTRA_DETECTED_AMOUNT = "EXTRA_DETECTED_AMOUNT"
        const val EXTRA_DETECTED_TITLE = "EXTRA_DETECTED_TITLE"
        const val EXTRA_DETECTED_IS_INCOME = "EXTRA_DETECTED_IS_INCOME"
        const val EXTRA_DETECTED_CATEGORY = "EXTRA_DETECTED_CATEGORY"
        const val EXTRA_DETECTED_CURRENCY = "EXTRA_DETECTED_CURRENCY"

        private const val CHANNEL_ID = "splitup_payment_detection_channel"
        private const val NOTIFICATION_ID = 2002

        fun isNotificationListenerEnabled(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return flat != null && flat.contains(packageName)
        }

        fun isPaymentApp(pkg: String): Boolean {
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

        fun extractMerchantName(title: String, text: String): String {
            val fullText = "$title $text".trim()

            // Try to extract merchant after "at", "to", "from", "for"
            val regex = Regex("(?i)\\b(?:at|to|from|for)\\s+([A-Za-z0-9&'\\s]{2,30}?)(?:\\s+(?:using|via|on|with|ref|id|card|wallet|gpay|paytm|app|bank)|[.,!]|$)", RegexOption.IGNORE_CASE)
            val match = regex.find(fullText)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.isNotBlank() && !candidate.equals("you", ignoreCase = true) && candidate.length in 2..35) {
                    return candidate
                }
            }

            // Fallback: If title is not a payment app name, use title, otherwise text
            val isGenericTitle = listOf("google wallet", "gpay", "paytm", "phonepe", "amex", "bank", "card", "wallet", "notification", "alert").any {
                title.lowercase(Locale.ROOT).contains(it)
            }

            val raw = if (!isGenericTitle && title.isNotBlank() && title.length < 35) title else text
            val cleaned = raw
                .replace(Regex("(?i)payment (?:to|at|from)|spent at|debited for|paid to|charged at|received from"), "")
                .trim()

            return if (cleaned.length in 2..40) cleaned else "Detected Transaction"
        }

        fun parseCurrencyFromSymbolOrCode(raw: String, defaultCurrency: String = Currencies.EUR): String {
            val upper = raw.uppercase(Locale.ROOT)
            return when {
                upper.contains("€") || upper.contains("EUR") -> Currencies.EUR
                upper.contains("CA$") || upper.contains("CAD") -> "CAD"
                upper.contains("A$") || upper.contains("AUD") -> "AUD"
                upper.contains("S$") || upper.contains("SGD") -> "SGD"
                upper.contains("£") || upper.contains("GBP") -> Currencies.GBP
                upper.contains("₹") || upper.contains("INR") -> Currencies.INR
                upper.contains("¥") || upper.contains("JPY") -> "JPY"
                upper.contains("CNY") -> "CNY"
                upper.contains("CHF") -> "CHF"
                upper.contains("AED") -> "AED"
                upper.contains("$") || upper.contains("USD") -> Currencies.USD
                else -> defaultCurrency
            }
        }

        fun parseNotification(
            title: String,
            text: String,
            bigText: String = "",
            packageName: String,
            ownPackageName: String = "com.finance.splitup",
            defaultCurrency: String = Currencies.EUR
        ): DetectedPaymentTransaction? {
            if (packageName == ownPackageName) return null
            val fullContent = "$title $text $bigText".trim()
            if (fullContent.isBlank()) return null

            // Regex pattern to extract monetary amounts e.g., $45.20, USD 45, INR 500, €12.50, £9.99
            val amountPattern = Pattern.compile("([$₹€£]|INR|USD|EUR|GBP|CAD|AUD|JPY|CHF|SGD|AED|CNY)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE)
            val matcher = amountPattern.matcher(fullContent)

            var detectedAmount: Double? = null
            var detectedCurrency: String? = null

            if (matcher.find()) {
                val rawCurr = matcher.group(1)
                val rawNum = matcher.group(2)?.replace(",", "")
                detectedAmount = rawNum?.toDoubleOrNull()
                if (rawCurr != null) {
                    detectedCurrency = parseCurrencyFromSymbolOrCode(rawCurr, defaultCurrency)
                }
            }

            // Alternative regex if currency symbol is after amount e.g. 45.20 USD or 12.50 €
            if (detectedAmount == null) {
                val altPattern = Pattern.compile("([0-9,]+(?:\\.[0-9]{1,2})?)\\s*([$₹€£]|INR|USD|EUR|GBP|CAD|AUD|JPY|CHF|SGD|AED|CNY)", Pattern.CASE_INSENSITIVE)
                val altMatcher = altPattern.matcher(fullContent)
                if (altMatcher.find()) {
                    val rawNum = altMatcher.group(1)?.replace(",", "")
                    val rawCurr = altMatcher.group(2)
                    detectedAmount = rawNum?.toDoubleOrNull()
                    if (rawCurr != null) {
                        detectedCurrency = parseCurrencyFromSymbolOrCode(rawCurr, defaultCurrency)
                    }
                }
            }

            if (detectedCurrency == null) {
                detectedCurrency = parseCurrencyFromSymbolOrCode(fullContent, defaultCurrency)
            }

            // Only proceed if a valid transaction amount was parsed
            if (detectedAmount != null && detectedAmount > 0) {
                val lowerContent = fullContent.lowercase(Locale.ROOT)

                val expenseKeywords = listOf("spent", "paid", "debited", "charged", "purchase", "sent", "withdrawn", "payment of")
                val incomeKeywords = listOf("received", "credited", "refund", "deposit", "added", "got")

                val isExpense = expenseKeywords.any { lowerContent.contains(it) }
                val isIncome = incomeKeywords.any { lowerContent.contains(it) }

                if (isExpense || isIncome || isPaymentApp(packageName)) {
                    val transactionIsIncome = isIncome && !isExpense
                    val merchantName = extractMerchantName(title, text)
                    val category = detectCategoryFromTitle(merchantName)

                    return DetectedPaymentTransaction(
                        amount = detectedAmount,
                        merchantTitle = merchantName,
                        isIncome = transactionIsIncome,
                        category = category,
                        currency = detectedCurrency
                    )
                }
            }
            return null
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName ?: return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

        val userDefaultCurrency = EncryptedPrefs.getPreferences(applicationContext, "splitup_prefs")
            .getString("default_currency", Currencies.EUR) ?: Currencies.EUR

        val detected = parseNotification(
            title = title,
            text = text,
            bigText = bigText,
            packageName = packageName,
            ownPackageName = applicationContext.packageName,
            defaultCurrency = userDefaultCurrency
        )

        if (detected != null) {
            postReviewNotification(
                context = applicationContext,
                amount = detected.amount,
                merchantTitle = detected.merchantTitle,
                isIncome = detected.isIncome,
                category = detected.category,
                currency = detected.currency
            )
        }
    }

    private fun postReviewNotification(
        context: Context,
        amount: Double,
        merchantTitle: String,
        isIncome: Boolean,
        category: String,
        currency: String = Currencies.EUR
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
            putExtra(EXTRA_DETECTED_CURRENCY, currency)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val typeText = if (isIncome) "Income" else "Expense"
        val formattedAmount = String.format(Locale.getDefault(), "%.2f", amount)
        val symbol = Currencies.getSymbol(currency)
        val formattedPrice = "$symbol$formattedAmount"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.finance.splitup.R.mipmap.ic_launcher_round)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(context.resources, com.finance.splitup.R.mipmap.ic_launcher_round))
            .setContentTitle("Payment Detected: $merchantTitle")
            .setContentText("Detected $typeText of $formattedPrice. Tap to review and add to SplitUp.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Detected $typeText of $formattedPrice ($merchantTitle). Tap to review and pre-fill into SplitUp."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID + (amount * 100).toInt() % 1000, builder.build())
    }
}
