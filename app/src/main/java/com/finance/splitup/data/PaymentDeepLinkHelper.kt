package com.finance.splitup.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale

object PaymentDeepLinkHelper {

    fun launchUpiPayment(
        context: Context,
        vpa: String,
        payeeName: String,
        amount: Double,
        currency: String = "INR",
        note: String = "SplitUp Settlement"
    ): Boolean {
        val cleanVpa = vpa.trim()
        if (cleanVpa.isBlank()) {
            val displayName = payeeName.ifBlank { "Recipient" }
            Toast.makeText(context, "$displayName has not set up a UPI VPA.", Toast.LENGTH_LONG).show()
            return false
        }
        return try {
            val formattedAmount = String.format(Locale.US, "%.2f", amount)
            val currStr = if (currency.contains("INR") || currency.contains("₹")) "INR" else "EUR"
            val uri = Uri.parse("upi://pay?pa=$cleanVpa&pn=${Uri.encode(payeeName)}&am=$formattedAmount&cu=$currStr&tn=${Uri.encode(note)}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            val chooser = Intent.createChooser(intent, "Pay via UPI App")
            com.finance.splitup.MainActivity.isExternalActionInProgress = true
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "No UPI payment app found: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun launchPaypalPayment(
        context: Context,
        paypalHandle: String,
        amount: Double,
        payeeName: String = "",
        currencySymbol: String = "$"
    ): Boolean {
        val cleanHandle = paypalHandle.trim().removePrefix("@")
        if (cleanHandle.isBlank()) {
            val displayName = payeeName.ifBlank { "Recipient" }
            Toast.makeText(context, "$displayName has not set up their PayPal username.", Toast.LENGTH_LONG).show()
            return false
        }
        return try {
            val formattedAmount = String.format(Locale.US, "%.2f", amount)
            val paypalUrl = if (amount > 0) {
                "https://paypal.me/$cleanHandle/$formattedAmount"
            } else {
                "https://paypal.me/$cleanHandle"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paypalUrl))
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Could not launch PayPal: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun launchVenmoPayment(
        context: Context,
        venmoHandle: String,
        amount: Double,
        payeeName: String = "",
        note: String = "SplitUp"
    ): Boolean {
        val cleanHandle = venmoHandle.trim().removePrefix("@")
        if (cleanHandle.isBlank()) {
            val displayName = payeeName.ifBlank { "Recipient" }
            Toast.makeText(context, "$displayName has not set up their Venmo username.", Toast.LENGTH_LONG).show()
            return false
        }
        return try {
            val formattedAmount = String.format(Locale.US, "%.2f", amount)
            val uri = Uri.parse("https://venmo.com/$cleanHandle?txn=pay&amount=$formattedAmount&note=${Uri.encode(note)}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Could not launch Venmo: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun launchRevolutPayment(
        context: Context,
        revolutTag: String,
        payeeName: String = ""
    ): Boolean {
        val cleanTag = revolutTag.trim().removePrefix("@")
        if (cleanTag.isBlank()) {
            val displayName = payeeName.ifBlank { "Recipient" }
            Toast.makeText(context, "$displayName has not set up their Revolut tag.", Toast.LENGTH_LONG).show()
            return false
        }
        return try {
            val uri = Uri.parse("https://revolut.me/$cleanTag")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Could not launch Revolut: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
