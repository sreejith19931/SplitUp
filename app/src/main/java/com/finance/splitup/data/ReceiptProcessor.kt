package com.finance.splitup.data

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object ReceiptProcessor {
    suspend fun processReceiptImage(bitmap: Bitmap?): ExtractedReceipt = suspendCancellableCoroutine { continuation ->
        val emptyResult = ExtractedReceipt(
            merchantName = "Unrecognized Receipt",
            date = "Today",
            totalAmount = 0.0,
            subtotal = 0.0,
            taxAmount = 0.0,
            tipAmount = 0.0,
            lineItems = emptyList(),
            currency = Currencies.EUR,
            confidenceScore = 0.0f
        )

        if (bitmap == null) {
            continuation.resume(emptyResult)
            return@suspendCancellableCoroutine
        }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        continuation.invokeOnCancellation {
            try {
                recognizer.close()
            } catch (_: Exception) { }
        }

        try {
            val maxDimension = 1200
            val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = minOf(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else {
                bitmap
            }
            val image = InputImage.fromBitmap(scaledBitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    val text = result.text
                    if (text.isBlank()) {
                        continuation.resume(emptyResult)
                        return@addOnSuccessListener
                    }
                    val extracted = parseReceiptText(text)
                    continuation.resume(extracted)
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume(emptyResult)
                    }
                }
        } catch (_: Exception) {
            if (continuation.isActive) {
                continuation.resume(emptyResult)
            }
        }
    }

    suspend fun detectReceiptBoundsInBitmap(bitmap: Bitmap?): android.graphics.RectF? = suspendCancellableCoroutine { continuation ->
        if (bitmap == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                if (visionText.textBlocks.isEmpty()) {
                    continuation.resume(null)
                } else {
                    var minX = Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxX = Float.MIN_VALUE
                    var maxY = Float.MIN_VALUE

                    val w = bitmap.width.toFloat()
                    val h = bitmap.height.toFloat()

                    for (block in visionText.textBlocks) {
                        block.boundingBox?.let { rect ->
                            if (rect.left < minX) minX = rect.left.toFloat()
                            if (rect.top < minY) minY = rect.top.toFloat()
                            if (rect.right > maxX) maxX = rect.right.toFloat()
                            if (rect.bottom > maxY) maxY = rect.bottom.toFloat()
                        }
                    }

                    if (maxX > minX && maxY > minY && w > 0 && h > 0) {
                        val padX = (maxX - minX) * 0.05f
                        val padY = (maxY - minY) * 0.05f
                        val normLeft = ((minX - padX) / w).coerceIn(0.01f, 0.4f)
                        val normTop = ((minY - padY) / h).coerceIn(0.01f, 0.4f)
                        val normRight = ((maxX + padX) / w).coerceIn(normLeft + 0.1f, 0.99f)
                        val normBottom = ((maxY + padY) / h).coerceIn(normTop + 0.1f, 0.99f)
                        continuation.resume(android.graphics.RectF(normLeft, normTop, normRight, normBottom))
                    } else {
                        continuation.resume(null)
                    }
                }
            }
            .addOnFailureListener {
                continuation.resume(null)
            }
    }

    private fun parseReceiptPrice(priceStr: String): Double {
        if (priceStr.isBlank()) return 0.0
        val cleaned = priceStr.trim()
        val normalized = if (cleaned.contains(",") && cleaned.contains(".")) {
            val lastComma = cleaned.lastIndexOf(',')
            val lastDot = cleaned.lastIndexOf('.')
            if (lastComma > lastDot) {
                cleaned.replace(".", "").replace(",", ".")
            } else {
                cleaned.replace(",", "")
            }
        } else if (cleaned.contains(",")) {
            cleaned.replace(",", ".")
        } else {
            cleaned
        }
        return normalized.toDoubleOrNull() ?: 0.0
    }

    fun parseReceiptText(text: String): ExtractedReceipt {
        val lines = text.split("\n").asSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()

        var detectedCurrency = Currencies.EUR
        val fullTextUpper = text.uppercase()
        val words = fullTextUpper.split(Regex("""\s+"""))

        if (fullTextUpper.contains("$") || words.contains("USD") || words.contains("DOLLAR") || words.contains("DOLLARS")) {
            detectedCurrency = Currencies.USD
        } else if (fullTextUpper.contains("£") || words.contains("GBP") || words.contains("POUND") || words.contains("POUNDS")) {
            detectedCurrency = Currencies.GBP
        } else if (fullTextUpper.contains("₹") || words.contains("INR") || words.contains("RUPEE") || words.contains("RUPEES") || words.contains("RS.") || Regex("""\bRS\b""").containsMatchIn(fullTextUpper)) {
            detectedCurrency = Currencies.INR
        } else if (fullTextUpper.contains("€") || words.contains("EUR") || words.contains("EURO") || words.contains("EUROS")) {
            detectedCurrency = Currencies.EUR
        }

        val subtotalKeywords = setOf("SUBTOTAL", "SUB TOTAL", "SUB-TOTAL", "ZWISCHENSUMME", "SOUS-TOTAL", "PARZIALE", "NET", "NETTO")
        val totalKeywords = setOf("TOTAL", "BALANCE", "AMOUNT DUE", "GRAND TOTAL", "SUMME", "GESAMT", "ENDBETRAG", "PAYABLE", "MONTANT", "TOTALE", "IMPORTO", "PAGO", "TOTAAL")
        val taxBreakdownKeywords = setOf("TAX", "VAT", "MWST", "UST", "STEUER", "CGST", "SGST", "IGST", "TVA", "IVA", "BTW", "GST")
        val tipKeywords = setOf("TIP", "GRATUITY", "SERVICE", "TRINKGELD", "POURBOIRE", "SERVIZIO")
        val paymentKeywords = setOf("CARD", "VISA", "MASTERCARD", "AMEX", "CREDIT", "DEBIT", "CASH", "PAYMENT", "PAID", "CHANGE", "TENDER", "EFTPOS", "APPLE PAY", "GOOGLE PAY", "BARZAHLUNG", "KARTE", "GUTHABEN", "ESPACE", "EFFECTUE", "CARTA", "CONTANTI", "CONTANT")
        val codeKeywords = setOf("CODE", "ARTIKEL", "ART.", "ITEM NO", "SKU", "PLU", "EAN", "BARCODE", "POS", "REF", "ID:")
        val nonMerchantTerms = setOf("TABLE", "GUEST", "SERVER", "RECEIPT", "INVOICE", "CHECK", "DATE", "TIME", "REGISTER", "TERMINAL", "CASHIER", "WELCOME", "TICKET", "ORDER", "CASH", "CARD")

        val priceRegex = Regex("""(\d+[.,]\d{1,2})""")

        val merchant = lines.take(6).firstOrNull { line ->
            val upper = line.uppercase()
            val wordsInLine = upper.split(Regex("""\s+"""))
            line.length > 2 &&
            !line.contains(Regex("""\d{2}/\d{2}""")) &&
            !line.contains(Regex("""^\d+$""")) &&
            !priceRegex.containsMatchIn(line) &&
            totalKeywords.none { upper.contains(it) } &&
            paymentKeywords.none { upper.contains(it) } &&
            taxBreakdownKeywords.none { upper.contains(it) } &&
            nonMerchantTerms.none { term -> wordsInLine.contains(term) || upper == term }
        } ?: lines.firstOrNull { it.length > 2 } ?: "Scanned Merchant"

        // Pass 1: Find authoritative total, subtotal, tax, tip
        var extractedTotal = 0.0
        var extractedSubtotal = 0.0
        var extractedTax = 0.0
        var extractedTip = 0.0
        var bestTotalScore = -1

        for (i in lines.indices) {
            val line = lines[i]
            val upper = line.uppercase()
            val match = priceRegex.find(line)
            if (match != null) {
                val price = parseReceiptPrice(match.groupValues[1])
                if (price > 0.0) {
                    if (subtotalKeywords.any { upper.contains(it) }) {
                        extractedSubtotal = price
                    } else if (taxBreakdownKeywords.any { upper.contains(it) }) {
                        extractedTax = price
                    } else if (tipKeywords.any { upper.contains(it) }) {
                        extractedTip = price
                    } else {
                        var score = 0
                        if (upper.contains("GRAND TOTAL") || upper.contains("ENDBETRAG")) score = 5
                        else if (upper.contains("TOTAL") || upper.contains("GESAMT") || upper.contains("SUMME")) score = 4
                        else if (upper.contains("BALANCE") || upper.contains("AMOUNT DUE")) score = 3
                        else if (i >= lines.size - 3) score = 2

                        if (score > bestTotalScore) {
                            bestTotalScore = score
                            extractedTotal = price
                        } else if (score == bestTotalScore && price > extractedTotal) {
                            extractedTotal = price
                        }
                    }
                }
            }
        }

        // Check if total/tax/subtotal keyword is on line i and price is on line i + 1
        for (i in 0 until lines.size - 1) {
            val upper = lines[i].uppercase()
            val nextMatch = priceRegex.find(lines[i + 1])
            if (nextMatch != null) {
                val price = parseReceiptPrice(nextMatch.groupValues[1])
                if (price > 0.0) {
                    if (totalKeywords.any { upper.contains(it) } && price > extractedTotal) {
                        extractedTotal = price
                    } else if (taxBreakdownKeywords.any { upper.contains(it) } && extractedTax == 0.0) {
                        extractedTax = price
                    } else if (subtotalKeywords.any { upper.contains(it) } && extractedSubtotal == 0.0) {
                        extractedSubtotal = price
                    } else if (tipKeywords.any { upper.contains(it) } && extractedTip == 0.0) {
                        extractedTip = price
                    }
                }
            }
        }

        // Pass 2: Extract item lines
        val items = mutableListOf<ReceiptLineItem>()
        for (i in lines.indices) {
            val line = lines[i]
            val upperLine = line.uppercase()
            val match = priceRegex.find(line)
            
            var itemName = ""
            var itemPrice = 0.0

            if (match != null) {
                val price = parseReceiptPrice(match.groupValues[1])
                if (price > 0.0) {
                    val rawName = line.replace(match.groupValues[1], "")
                        .replace(Regex("""[$€£₹]"""), "")
                        .replace(Regex("""^[*\s\-_=:#]+"""), "")
                        .replace(Regex("""[*\s\-_=:#]+$"""), "")
                        .replace(Regex("""^\d+\s*[xX]\s+"""), "")
                        .trim()
                    itemName = rawName
                    itemPrice = price
                }
            } else if (i < lines.size - 1) {
                val nextMatch = priceRegex.find(lines[i + 1])
                if (nextMatch != null) {
                    val price = parseReceiptPrice(nextMatch.groupValues[1])
                    if (price > 0.0) {
                        itemName = line.trim()
                            .replace(Regex("""^[*\s\-_=:#]+"""), "")
                            .replace(Regex("""[*\s\-_=:#]+$"""), "")
                            .replace(Regex("""^\d+\s*[xX]\s+"""), "")
                        itemPrice = price
                    }
                }
            }

            if (itemPrice > 0.0 && itemName.isNotBlank()) {
                val isSubtotal = subtotalKeywords.any { upperLine.contains(it) }
                val isTotalLine = !isSubtotal && totalKeywords.any { upperLine.contains(it) }
                val isTaxLine = taxBreakdownKeywords.any { upperLine.contains(it) }
                val isTipLine = tipKeywords.any { upperLine.contains(it) }
                val isPaymentLine = paymentKeywords.any { upperLine.contains(it) }
                val isCodeLine = codeKeywords.any { upperLine.contains(it) } || upperLine.startsWith("CODE") || upperLine.startsWith("SKU") || upperLine.startsWith("PLU") || upperLine.startsWith("REF") || upperLine.startsWith("EAN")
                val followsTotalLabel = i > 0 && totalKeywords.any { lines[i - 1].uppercase().contains(it) }

                val isPureNumberOrCode = itemName.matches(Regex("""^[\d\s\-_.:/]+$"""))
                if (!isSubtotal && !isTotalLine && !isTaxLine && !isTipLine && !isPaymentLine && !isCodeLine && !followsTotalLabel && !isPureNumberOrCode) {
                    if (items.none { it.name.equals(itemName, ignoreCase = true) && kotlin.math.abs(it.price - itemPrice) < 0.01 }) {
                        items.add(ReceiptLineItem(name = itemName, price = itemPrice))
                    }
                }
            }
        }

        val dateRegex = Regex("""\b(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\w{3}\s+\d{1,2},?\s+\d{4})\b""")
        val extractedDate = lines.mapNotNull { line ->
            dateRegex.find(line)?.value
        }.firstOrNull() ?: "Today"

        if (extractedTotal == 0.0 && items.isNotEmpty()) {
            extractedTotal = items.sumOf { it.price }
        }
        if (extractedTotal == 0.0 && items.isEmpty()) {
            items.add(ReceiptLineItem(name = "General Purchase", price = 0.0))
        } else if (extractedTotal > 0.0 && items.isEmpty()) {
            items.add(ReceiptLineItem(name = "General Purchase", price = extractedTotal))
        }

        if (extractedSubtotal == 0.0 && items.isNotEmpty() && extractedTax > 0.0) {
            extractedSubtotal = kotlin.math.round((extractedTotal - extractedTax - extractedTip) * 100) / 100.0
        }

        // Reconcile item costs against extractedTotal
        val targetAmountForItems = extractedTotal
        if (targetAmountForItems > 0.0 && items.isNotEmpty()) {
            if (items.size == 1) {
                items[0] = items[0].copy(price = targetAmountForItems)
            } else {
                val currentSum = items.sumOf { it.price }
                if (kotlin.math.abs(currentSum - targetAmountForItems) > 0.01 && currentSum > 0.0) {
                    var runningSum = 0.0
                    val adjustedItems = items.mapIndexed { index, item ->
                        if (index == items.size - 1) {
                            val remaining = kotlin.math.round((targetAmountForItems - runningSum) * 100) / 100.0
                            item.copy(price = maxOf(0.01, remaining))
                        } else {
                            val scaled = kotlin.math.round((item.price / currentSum) * targetAmountForItems * 100) / 100.0
                            runningSum += scaled
                            item.copy(price = maxOf(0.01, scaled))
                        }
                    }
                    items.clear()
                    items.addAll(adjustedItems)
                }
            }
        }

        val confidence = if (lines.size > 5 && extractedTotal > 0.0 && items.isNotEmpty()) 0.98f else 0.88f

        return ExtractedReceipt(
            merchantName = merchant,
            date = extractedDate,
            totalAmount = extractedTotal,
            subtotal = extractedSubtotal,
            taxAmount = extractedTax,
            tipAmount = extractedTip,
            lineItems = items,
            currency = detectedCurrency,
            confidenceScore = confidence
        )
    }
}
