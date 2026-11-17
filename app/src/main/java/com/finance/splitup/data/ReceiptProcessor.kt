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
                    val rawText = result.text
                    if (rawText.isBlank()) {
                        continuation.resume(emptyResult)
                        return@addOnSuccessListener
                    }
                    val reconstructedText = reconstructTextByLines(result)
                    val textToParse = if (reconstructedText.isNotBlank()) reconstructedText else rawText
                    val extracted = parseReceiptText(textToParse)
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
                        val padX = (maxX - minX) * 0.06f
                        val padY = (maxY - minY) * 0.04f
                        val normLeft = ((minX - padX) / w).coerceIn(0.0f, 0.9f)
                        val normTop = ((minY - padY) / h).coerceIn(0.0f, 0.9f)
                        val normRight = ((maxX + padX) / w).coerceIn(normLeft + 0.1f, 1.0f)
                        val normBottom = ((maxY + padY) / h).coerceIn(normTop + 0.1f, 1.0f)
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

    private data class PhysicalLine(
        val text: String,
        val top: Int,
        val bottom: Int,
        val left: Int,
        val right: Int
    )

    private fun reconstructTextByLines(visionText: com.google.mlkit.vision.text.Text): String {
        val allLines = mutableListOf<PhysicalLine>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox
                if (box != null && line.text.isNotBlank()) {
                    allLines.add(PhysicalLine(line.text.trim(), box.top, box.bottom, box.left, box.right))
                }
            }
        }

        if (allLines.isEmpty()) return visionText.text

        val sortedLines = allLines.sortedBy { it.top }
        val rows = mutableListOf<MutableList<PhysicalLine>>()
        for (line in sortedLines) {
            val matchingRow = rows.firstOrNull { row ->
                val avgTop = row.map { it.top }.average()
                val avgHeight = row.map { it.bottom - it.top }.average().coerceAtLeast(10.0)
                kotlin.math.abs(line.top - avgTop) < avgHeight * 0.70
            }
            if (matchingRow != null) {
                matchingRow.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }

        return rows.joinToString("\n") { row ->
            row.sortedBy { it.left }.joinToString("   ") { it.text }
        }
    }

    private fun parseReceiptPrice(priceStr: String): Double {
        if (priceStr.isBlank()) return 0.0
        val cleaned = priceStr.trim()
        val isNegative = cleaned.startsWith("-")
        val absCleaned = if (isNegative) cleaned.substring(1).trim() else cleaned
        val normalized = if (absCleaned.contains(",") && absCleaned.contains(".")) {
            val lastComma = absCleaned.lastIndexOf(',')
            val lastDot = absCleaned.lastIndexOf('.')
            if (lastComma > lastDot) {
                absCleaned.replace(".", "").replace(",", ".")
            } else {
                absCleaned.replace(",", "")
            }
        } else if (absCleaned.contains(",")) {
            absCleaned.replace(",", ".")
        } else {
            absCleaned
        }
        val value = normalized.toDoubleOrNull() ?: 0.0
        return if (isNegative) -value else value
    }

    fun parseReceiptText(text: String): ExtractedReceipt {
        val lines = text.split("\n").asSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) {
            return ExtractedReceipt(
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
        }

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
        val discountKeywords = setOf("RABATT", "DISCOUNT", "RABAT", "REDUKTION", "SAVINGS", "GUTSCHEIN", "COUPON", "APP PREIS", "OFF", "NACHLASS", "PROMO", "PROMOTIONAL", "SPECIAL", "OFFER", "REBATE", "CREDIT", "REDUCTION", "SCONTO", "DESCUENTO", "KORTING", "SPAREN", "AKTION", "ERMÄSSIGUNG", "MEMBER PRICE")
        val companySuffixes = setOf("GMBH", "AG", "E.K.", "INC", "LLC", "LTD", "CORP", "CO", "HANDELS", "SUPERMARKET", "MARKET")
        val addressTerms = setOf("STREET", "ST", "AVENUE", "AVE", "ROAD", "RD", "BOULEVARD", "BLVD", "DRIVE", "DR", "LANE", "LN", "WAY", "SUITE", "STE", "UNIT", "FLOOR", "FL", "HIGHWAY", "HWY", "PARKWAY", "PKWY", "BOX", "ZIP", "POSTAL", "CITY", "STATE")
        val nonMerchantTerms = setOf("TABLE", "GUEST", "SERVER", "RECEIPT", "INVOICE", "CHECK", "DATE", "TIME", "REGISTER", "TERMINAL", "CASHIER", "WELCOME", "TICKET", "ORDER", "CASH", "CARD", "STORE", "BRANCH", "SHOP", "LOCATION", "MANAGER", "TEL", "PHONE", "FAX", "MOBILE", "WWW", "HTTP", "HTTPS", "COM", "ORG", "NET", "THANK", "THANKS", "VISIT", "CUSTOMER", "DUPLICATE", "ORIGINAL")

        val priceRegex = Regex("""(-?\s*\d+[.,]\d{1,2})""")

        fun cleanMerchantLine(line: String): String {
            var s = line.trim()
            s = s.replace(Regex("""^[*\s\-_=:#~!]+"""), "").replace(Regex("""[*\s\-_=:#~!]+$"""), "").trim()
            val prefixes = listOf("WELCOME TO ", "WELCOME ", "THANK YOU FOR SHOPPING AT ", "THANK YOU FOR VISITING ", "THANKS FOR VISITING ")
            for (p in prefixes) {
                if (s.uppercase().startsWith(p)) {
                    s = s.substring(p.length).trim()
                    break
                }
            }
            return s
        }

        var merchant = ""
        // Priority 1: Check for company name or store suffix in top lines
        for (line in lines.take(8)) {
            val cleanedLine = cleanMerchantLine(line)
            val upper = cleanedLine.uppercase()
            val wordsInLine = upper.split(Regex("""\s+""")).map { it.replace(Regex("""[^A-Z0-9]"""), "") }.filter { it.isNotBlank() }
            val hasPrice = priceRegex.containsMatchIn(cleanedLine)
            val hasDate = upper.contains(Regex("""\d{1,2}[/-]\d{1,2}[/-]\d{2,4}"""))
            val hasAddress = addressTerms.any { wordsInLine.contains(it) }

            if (cleanedLine.length > 2 && !hasPrice && !hasDate && !hasAddress) {
                if (companySuffixes.any { wordsInLine.contains(it) }) {
                    merchant = cleanedLine
                    break
                }
            }
        }

        // Priority 2: General merchant line matching
        if (merchant.isBlank()) {
            for (line in lines.take(8)) {
                val cleanedLine = cleanMerchantLine(line)
                val upper = cleanedLine.uppercase()
                val wordsInLine = upper.split(Regex("""\s+""")).map { it.replace(Regex("""[^A-Z0-9]"""), "") }.filter { it.isNotBlank() }

                val hasAddressTerm = addressTerms.any { term -> wordsInLine.contains(term) || upper.contains(" $term") }
                val hasPhoneOrWeb = upper.contains("TEL") || upper.contains("PHONE") || upper.contains("FAX") || upper.contains("WWW.") || upper.contains(".COM") || upper.contains("HTTP") || Regex("""\(\d{3}\)|\d{3}-\d{3}""").containsMatchIn(upper)
                val hasStoreOrOrderMarker = upper.contains("STORE #") || upper.contains("STORE#") || upper.contains("RECEIPT #") || upper.contains("INVOICE #") || upper.contains("CHECK #") || upper.contains("ORDER #") || upper.contains("TAX ID") || upper.contains("VAT ID") || upper.contains("GSTIN")
                val hasDate = upper.contains(Regex("""\d{1,2}[/-]\d{1,2}[/-]\d{2,4}""")) || upper.contains(Regex("""\d{4}[/-]\d{2}[/-]\d{2}"""))
                val hasPrice = priceRegex.containsMatchIn(cleanedLine)

                val isNonMerchantLine = totalKeywords.any { upper.contains(it) } ||
                        paymentKeywords.any { upper.contains(it) } ||
                        taxBreakdownKeywords.any { upper.contains(it) } ||
                        subtotalKeywords.any { upper.contains(it) } ||
                        tipKeywords.any { upper.contains(it) } ||
                        nonMerchantTerms.any { term -> wordsInLine.contains(term) || upper == term }

                if (cleanedLine.length > 2 && !hasAddressTerm && !hasPhoneOrWeb && !hasStoreOrOrderMarker && !hasDate && !hasPrice && !isNonMerchantLine) {
                    merchant = cleanedLine
                    break
                }
            }
        }

        if (merchant.isBlank()) {
            merchant = lines.take(6).map { cleanMerchantLine(it) }.firstOrNull { it.length > 2 && !priceRegex.containsMatchIn(it) } ?: "Scanned Merchant"
        }

        // Pass 1: Find authoritative total, subtotal, tax, tip
        var extractedTotal = 0.0
        var extractedSubtotal = 0.0
        var extractedTax = 0.0
        var extractedTip = 0.0
        var bestTotalScore = -1

        for (i in lines.indices) {
            val line = lines[i]
            val upper = line.uppercase()
            val matches = priceRegex.findAll(line).toList()
            if (matches.isNotEmpty()) {
                val lastPriceStr = matches.last().groupValues[1]
                val price = parseReceiptPrice(lastPriceStr)
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
            val nextMatches = priceRegex.findAll(lines[i + 1]).toList()
            if (nextMatches.isNotEmpty()) {
                val price = parseReceiptPrice(nextMatches.last().groupValues[1])
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

        fun cleanItemName(raw: String): String {
            var name = raw
                .replace(Regex("""[$€£₹]"""), "")
                .replace(Regex("""\b(USD|EUR|GBP|INR|RS)\b""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^[*\s\-_=:#~!.+]+"""), "")
                .replace(Regex("""[*\s\-_=:#~!.+]+$"""), "")
                .trim()

            name = name.replace(Regex("""^\d+([.,]\d+)?\s*(lb|kg|g|oz|pk|ea|pcs|pc|ct)?\s*(@|at|\*|[xX]|/(lb|kg|g|oz|pk|ea|pcs|pc|ct))\s*(\$?\d+([.,]\d{1,2})?)?\s*(/(lb|kg|g|oz|pk|ea|pcs|pc|ct))?\s*""", RegexOption.IGNORE_CASE), "")
            name = name.replace(Regex("""^\d+\s*[xX*]\s*"""), "")
            name = name.replace(Regex("""@\s*\$?\d+([.,]\d{1,2})?(/.*)?"""), "")
            name = name.replace(Regex("""/\s*(lb|kg|g|oz|pk|ea|pcs|pc|ct)\b""", RegexOption.IGNORE_CASE), "")
            name = name.replace(Regex("""^(SKU|PLU|ITEM)?\s*#?\s*\d{5,}\s+""", RegexOption.IGNORE_CASE), "")
            name = name.replace(Regex("""^\d{1,3}\s+(?=[A-Za-z])"""), "")

            return name
                .replace(Regex("""^[*\s\-_=:#~!.+]+"""), "")
                .replace(Regex("""[*\s\-_=:#~!.+]+$"""), "")
                .trim()
        }

        // Pass 2: Extract item and discount lines
        val items = mutableListOf<ReceiptLineItem>()
        var skipNext = false
        for (i in lines.indices) {
            if (skipNext) {
                skipNext = false
                continue
            }
            val line = lines[i]
            val upperLine = line.uppercase()
            val matches = priceRegex.findAll(line).toList()

            var itemName = ""
            var itemPrice = 0.0

            if (matches.isNotEmpty()) {
                val lastMatch = matches.last()
                val price = parseReceiptPrice(lastMatch.groupValues[1])
                if (price != 0.0) {
                    val textBeforePrice = line.substring(0, lastMatch.range.first)
                    itemName = cleanItemName(textBeforePrice)
                    itemPrice = price
                    val isDiscount = discountKeywords.any { upperLine.contains(it) } || line.startsWith("-") || line.contains(" -") || price < 0.0
                    if (isDiscount && itemPrice > 0.0) {
                        itemPrice = -itemPrice
                    }
                }
            } else if (i < lines.size - 1) {
                val nextMatches = priceRegex.findAll(lines[i + 1]).toList()
                if (nextMatches.isNotEmpty()) {
                    val nextLastMatch = nextMatches.last()
                    val price = parseReceiptPrice(nextLastMatch.groupValues[1])
                    val nextTextBefore = lines[i + 1].substring(0, nextLastMatch.range.first)
                    val cleanedNextBefore = cleanItemName(nextTextBefore)
                    if (price != 0.0 && cleanedNextBefore.isBlank()) {
                        itemName = cleanItemName(line)
                        itemPrice = price
                        val isDiscount = discountKeywords.any { upperLine.contains(it) } || lines[i+1].startsWith("-") || lines[i+1].contains(" -") || price < 0.0
                        if (isDiscount && itemPrice > 0.0) {
                            itemPrice = -itemPrice
                        }
                        skipNext = true
                    }
                }
            }

            if (itemPrice != 0.0 && itemName.isNotBlank()) {
                val wordsInUpperLine = upperLine.split(Regex("""\s+""")).map { it.replace(Regex("""[^A-Z0-9]"""), "") }.filter { it.isNotBlank() }
                val isSubtotal = subtotalKeywords.any { wordsInUpperLine.contains(it) || upperLine.contains("SUB TOTAL") || upperLine.contains("SUB-TOTAL") }
                val isTotalLine = !isSubtotal && totalKeywords.any { wordsInUpperLine.contains(it) || upperLine.contains("GRAND TOTAL") || upperLine.contains("AMOUNT DUE") }
                val isTaxLine = taxBreakdownKeywords.any { wordsInUpperLine.contains(it) }
                val isTipLine = tipKeywords.any { wordsInUpperLine.contains(it) }
                val isPaymentLine = paymentKeywords.any { wordsInUpperLine.contains(it) || upperLine.contains("APPLE PAY") || upperLine.contains("GOOGLE PAY") }

                val nameWithoutCode = codeKeywords.fold(itemName) { acc, kw ->
                    acc.replace(Regex("""\b""" + Regex.escape(kw) + """\b""", RegexOption.IGNORE_CASE), "")
                }.replace(Regex("""[#:\s]"""), "")
                val isCodeLine = codeKeywords.any { wordsInUpperLine.contains(it) || upperLine.startsWith(it) } && nameWithoutCode.matches(Regex("""^\d*$"""))

                val isAddressOrSystem = addressTerms.any { wordsInUpperLine.contains(it) } || (nonMerchantTerms.any { wordsInUpperLine.contains(it) } && !upperLine.contains("ORGANIC"))
                val followsTotalLabel = i > 0 && totalKeywords.any { lines[i - 1].uppercase().contains(it) }

                val isPureNumberOrCode = itemName.matches(Regex("""^[\d\s\-_.:/#]+$"""))
                if (!isSubtotal && !isTotalLine && !isTaxLine && !isTipLine && !isPaymentLine && !isCodeLine && !isAddressOrSystem && !followsTotalLabel && !isPureNumberOrCode) {
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
