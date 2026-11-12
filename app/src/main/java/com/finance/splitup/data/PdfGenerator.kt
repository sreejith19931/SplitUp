package com.finance.splitup.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object PdfGenerator {
    fun generateExpenseReportPdf(
        context: Context,
        title: String,
        expenses: List<Expense>
    ): File? {
        return try {
            val pdfDocument = PdfDocument()
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = page.canvas

            val primaryNavy = Color.rgb(30, 64, 175)
            val headerBg = Color.rgb(241, 245, 249)
            val altRowBg = Color.rgb(248, 250, 252)
            val borderGray = Color.rgb(226, 232, 240)

            fun drawHeader(cv: Canvas, pageTitle: String) {
                val bannerPaint = Paint().apply { color = primaryNavy }
                cv.drawRect(0f, 0f, 595f, 75f, bannerPaint)

                val titlePaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 20f
                    isFakeBoldText = true
                }
                cv.drawText("SPLITUP FINANCIAL STATEMENT", 40f, 45f, titlePaint)

                val subtitlePaint = Paint().apply {
                    color = Color.rgb(226, 232, 240)
                    textSize = 10f
                }
                cv.drawText("Generated on ${java.time.LocalDate.now()} • Multi-Page Statement", 40f, 62f, subtitlePaint)
            }

            drawHeader(canvas, title)
            var y = 110f

            val labelPaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 14f
                isFakeBoldText = true
            }
            canvas.drawText(title, 40f, y, labelPaint)
            y += 20f

            fun drawTableHeader(cv: Canvas, currentY: Float): Float {
                val headerRectPaint = Paint().apply { color = headerBg }
                cv.drawRect(40f, currentY, 555f, currentY + 26f, headerRectPaint)

                val headerTextPaint = Paint().apply {
                    color = Color.rgb(51, 65, 85)
                    textSize = 10f
                    isFakeBoldText = true
                }

                cv.drawText("TITLE", 50f, currentY + 17f, headerTextPaint)
                cv.drawText("CATEGORY", 210f, currentY + 17f, headerTextPaint)
                cv.drawText("DATE", 340f, currentY + 17f, headerTextPaint)
                cv.drawText("METHOD", 420f, currentY + 17f, headerTextPaint)
                cv.drawText("AMOUNT", 490f, currentY + 17f, headerTextPaint)

                val borderPaint = Paint().apply {
                    color = borderGray
                    strokeWidth = 1f
                    style = Paint.Style.STROKE
                }
                cv.drawRect(40f, currentY, 555f, currentY + 26f, borderPaint)
                return currentY + 26f
            }

            y = drawTableHeader(canvas, y)

            val bodyPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 10f
            }
            val borderPaint = Paint().apply {
                color = borderGray
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }
            val altBgPaint = Paint().apply { color = altRowBg }

            var total = 0.0
            for ((index, expense) in expenses.withIndex()) {
                if (y > 730f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawHeader(canvas, title)
                    y = 110f
                    y = drawTableHeader(canvas, y)
                }

                if (index % 2 == 1) {
                    canvas.drawRect(40f, y, 555f, y + 24f, altBgPaint)
                }
                canvas.drawRect(40f, y, 555f, y + 24f, borderPaint)

                canvas.drawText(expense.title.take(22), 50f, y + 16f, bodyPaint)
                canvas.drawText(expense.category.take(16), 210f, y + 16f, bodyPaint)
                canvas.drawText(DateUtils.formatDisplayDate(expense.date, expense.updatedAt).take(12), 340f, y + 16f, bodyPaint)
                canvas.drawText(expense.paymentMethod.take(10), 420f, y + 16f, bodyPaint)

                val sym = Currencies.getSymbol(expense.currency)
                val amtStr = "$sym${String.format(Locale.US, "%.2f", expense.amount)}"
                canvas.drawText(amtStr, 490f, y + 16f, bodyPaint)

                total += expense.amount
                y += 24f
            }

            y += 20f
            if (y > 750f) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                drawHeader(canvas, title)
                y = 110f
            }

            val cardRect = RectF(350f, y, 555f, y + 45f)
            canvas.drawRoundRect(cardRect, 8f, 8f, Paint().apply { color = headerBg })
            canvas.drawRoundRect(cardRect, 8f, 8f, borderPaint)

            val totalLabelPaint = Paint().apply {
                color = Color.rgb(71, 85, 105)
                textSize = 10f
                isFakeBoldText = true
            }
            canvas.drawText("TOTAL STATEMENT VALUE", 365f, y + 18f, totalLabelPaint)

            val totalValPaint = Paint().apply {
                color = primaryNavy
                textSize = 14f
                isFakeBoldText = true
            }
            canvas.drawText("€${String.format(Locale.US, "%.2f", total)}", 365f, y + 36f, totalValPaint)

            pdfDocument.finishPage(page)

            val file = File(context.cacheDir, "splitup_report.pdf")
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (_: Exception) {
            null
        }
    }

    fun generateFullAppSummaryPdf(
        context: Context,
        expenses: List<Expense>,
        groups: List<Group>
    ): File? {
        return try {
            val pdfDocument = PdfDocument()
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = page.canvas

            val primaryNavy = Color.rgb(30, 64, 175)
            val altRowBg = Color.rgb(248, 250, 252)
            val borderGray = Color.rgb(226, 232, 240)

            fun drawHeader(cv: Canvas) {
                val bannerPaint = Paint().apply { color = primaryNavy }
                cv.drawRect(0f, 0f, 595f, 75f, bannerPaint)

                val titlePaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 20f
                    isFakeBoldText = true
                }
                cv.drawText("SPLITUP FULL APP SUMMARY REPORT", 40f, 45f, titlePaint)

                val subtitlePaint = Paint().apply {
                    color = Color.rgb(226, 232, 240)
                    textSize = 10f
                }
                cv.drawText("Generated on ${java.time.LocalDate.now()} • Comprehensive Export", 40f, 62f, subtitlePaint)
            }

            drawHeader(canvas)
            var y = 100f

            val sectionPaint = Paint().apply {
                color = primaryNavy
                textSize = 14f
                isFakeBoldText = true
            }

            canvas.drawText("1. PERSONAL EXPENSES (${expenses.size})", 40f, y, sectionPaint)
            y += 20f

            val bodyPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 10f
            }
            val borderPaint = Paint().apply {
                color = borderGray
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }
            val altBgPaint = Paint().apply { color = altRowBg }

            for ((index, exp) in expenses.withIndex()) {
                if (y > 730f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawHeader(canvas)
                    y = 100f
                }
                if (index % 2 == 1) {
                    canvas.drawRect(40f, y, 555f, y + 22f, altBgPaint)
                }
                canvas.drawRect(40f, y, 555f, y + 22f, borderPaint)
                canvas.drawText(exp.title.take(25), 50f, y + 15f, bodyPaint)
                canvas.drawText(exp.category.take(18), 220f, y + 15f, bodyPaint)
                canvas.drawText(DateUtils.formatDisplayDate(exp.date, exp.updatedAt).take(12), 350f, y + 15f, bodyPaint)
                val sym = Currencies.getSymbol(exp.currency)
                canvas.drawText("$sym${String.format(Locale.US, "%.2f", exp.amount)}", 470f, y + 15f, bodyPaint)
                y += 22f
            }

            y += 20f
            for (group in groups) {
                if (y > 700f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawHeader(canvas)
                    y = 100f
                }
                canvas.drawText("GROUP: ${group.name} (${group.type}, ${group.members.size} members)", 40f, y, sectionPaint)
                y += 18f

                for (ge in group.expenses) {
                    if (y > 730f) {
                        pdfDocument.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        drawHeader(canvas)
                        y = 100f
                    }
                    canvas.drawRect(40f, y, 555f, y + 20f, borderPaint)
                    canvas.drawText(ge.title.take(22), 50f, y + 14f, bodyPaint)
                    canvas.drawText("Paid by: ${ge.paidBy}", 220f, y + 14f, bodyPaint)
                    canvas.drawText(DateUtils.formatDisplayDate(ge.date).take(10), 370f, y + 14f, bodyPaint)
                    val gsym = Currencies.getSymbol(ge.currency)
                    canvas.drawText("$gsym${String.format(Locale.US, "%.2f", ge.amount)}", 470f, y + 14f, bodyPaint)
                    y += 20f
                }
                y += 15f
            }

            y += 25f
            if (y > 680f) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                drawHeader(canvas)
                y = 100f
            }
            canvas.drawText("SETTLEMENT SUGGESTIONS (WHO OWES WHOM)", 40f, y, sectionPaint)
            y += 20f

            for (group in groups) {
                val groupCurrency = group.expenses.firstOrNull()?.currency ?: Currencies.EUR
                val netBalances = calculateGroupNetBalances(group, groupCurrency)
                val debtors = netBalances.filter { it.value < -0.01 }.toMutableMap()
                val creditors = netBalances.filter { it.value > 0.01 }.toMutableMap()

                if (y > 720f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawHeader(canvas)
                    y = 100f
                }

                canvas.drawText("Group: ${group.name}", 50f, y, bodyPaint)
                y += 16f
                if (debtors.isEmpty() || creditors.isEmpty()) {
                    canvas.drawText("All members are settled up.", 70f, y, bodyPaint)
                    y += 18f
                } else {
                    for ((debtor, dVal) in debtors) {
                        for ((creditor, cVal) in creditors) {
                            val settleAmt = minOf(-dVal, cVal)
                            if (settleAmt > 0.01) {
                                if (y > 730f) {
                                    pdfDocument.finishPage(page)
                                    pageNumber++
                                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                                    page = pdfDocument.startPage(pageInfo)
                                    canvas = page.canvas
                                    drawHeader(canvas)
                                    y = 100f
                                }
                                canvas.drawText("• $debtor pays $creditor : €${String.format(Locale.US, "%.2f", settleAmt)}", 70f, y, bodyPaint)
                                y += 18f
                            }
                        }
                    }
                }
                y += 10f
            }

            pdfDocument.finishPage(page)

            val file = File(context.cacheDir, "splitup_full_app_report.pdf")
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (_: Exception) {
            null
        }
    }

    fun generateGroupStatementPdf(
        context: Context,
        group: Group,
        netBalances: Map<String, Double>,
        settlements: List<Settlement>
    ): File? {
        return try {
            val pdfDocument = PdfDocument()
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = page.canvas

            val primaryNavy = Color.rgb(30, 64, 175)
            val headerBg = Color.rgb(241, 245, 249)
            val altRowBg = Color.rgb(248, 250, 252)
            val borderGray = Color.rgb(226, 232, 240)

            fun drawHeader(cv: Canvas) {
                val bannerPaint = Paint().apply { color = primaryNavy }
                cv.drawRect(0f, 0f, 595f, 75f, bannerPaint)

                val titlePaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 18f
                    isFakeBoldText = true
                }
                cv.drawText("GROUP STATEMENT: ${group.name.uppercase()}", 40f, 45f, titlePaint)

                val subtitlePaint = Paint().apply {
                    color = Color.rgb(226, 232, 240)
                    textSize = 10f
                }
                cv.drawText("Type: ${group.type} • Members: ${group.members.joinToString(", ")}", 40f, 62f, subtitlePaint)
            }

            drawHeader(canvas)
            var y = 100f

            val sectionPaint = Paint().apply {
                color = primaryNavy
                textSize = 13f
                isFakeBoldText = true
            }
            val bodyPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 10f
            }
            val borderPaint = Paint().apply {
                color = borderGray
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }
            val altBgPaint = Paint().apply { color = altRowBg }

            // 1. Group Expenses List
            canvas.drawText("1. GROUP EXPENSES (${group.expenses.size})", 40f, y, sectionPaint)
            y += 20f

            val headerRectPaint = Paint().apply { color = headerBg }
            canvas.drawRect(40f, y, 555f, y + 24f, headerRectPaint)
            canvas.drawRect(40f, y, 555f, y + 24f, borderPaint)

            val headerTextPaint = Paint().apply {
                color = Color.rgb(51, 65, 85)
                textSize = 10f
                isFakeBoldText = true
            }
            canvas.drawText("EXPENSE", 50f, y + 16f, headerTextPaint)
            canvas.drawText("PAID BY", 230f, y + 16f, headerTextPaint)
            canvas.drawText("DATE", 360f, y + 16f, headerTextPaint)
            canvas.drawText("AMOUNT", 480f, y + 16f, headerTextPaint)
            y += 24f

            var totalExp = 0.0
            for ((idx, ge) in group.expenses.withIndex()) {
                if (y > 730f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawHeader(canvas)
                    y = 100f
                }
                if (idx % 2 == 1) {
                    canvas.drawRect(40f, y, 555f, y + 22f, altBgPaint)
                }
                canvas.drawRect(40f, y, 555f, y + 22f, borderPaint)
                canvas.drawText(ge.title.take(24), 50f, y + 15f, bodyPaint)
                canvas.drawText(ge.paidBy.take(16), 230f, y + 15f, bodyPaint)
                canvas.drawText(DateUtils.formatDisplayDate(ge.date).take(12), 360f, y + 15f, bodyPaint)
                val sym = Currencies.getSymbol(ge.currency)
                canvas.drawText("$sym${String.format(Locale.US, "%.2f", ge.amount)}", 480f, y + 15f, bodyPaint)
                totalExp += ge.amount
                y += 22f
            }

            // Total Summary Card
            y += 15f
            if (y > 720f) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                drawHeader(canvas)
                y = 100f
            }

            // 2. Member Balances Section
            canvas.drawText("2. MEMBER NET BALANCES", 40f, y, sectionPaint)
            y += 20f

            for ((member, balance) in netBalances) {
                if (y > 730f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawHeader(canvas)
                    y = 100f
                }
                val balStr = if (balance > 0.01) "Owed €${String.format(Locale.US, "%.2f", balance)}" else if (balance < -0.01) "Owes €${String.format(Locale.US, "%.2f", -balance)}" else "Settled up"
                canvas.drawText("• $member : $balStr", 50f, y, bodyPaint)
                y += 18f
            }

            // 3. Settlement Instructions
            y += 15f
            if (y > 720f) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                drawHeader(canvas)
                y = 100f
            }

            canvas.drawText("3. SETTLEMENT INSTRUCTIONS (MINIMIZED PAYMENTS)", 40f, y, sectionPaint)
            y += 20f

            if (settlements.isEmpty()) {
                canvas.drawText("All group members are fully settled up!", 50f, y, bodyPaint)
            } else {
                for (s in settlements) {
                    if (y > 730f) {
                        pdfDocument.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        drawHeader(canvas)
                        y = 100f
                    }
                    canvas.drawText("• ${s.from} pays ${s.to} : €${String.format(Locale.US, "%.2f", s.amount)}", 50f, y, bodyPaint)
                    y += 18f
                }
            }

            pdfDocument.finishPage(page)

            val file = File(context.cacheDir, "group_${group.id}_statement.pdf")
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (_: Exception) {
            null
        }
    }

    fun generateTestChecklistPdf(context: Context): File? {
        return try {
            val pdfDocument = PdfDocument()
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = page.canvas

            val primaryNavy = Color.rgb(30, 64, 175)

            fun drawHeader(cv: Canvas) {
                val bannerPaint = Paint().apply { color = primaryNavy }
                cv.drawRect(0f, 0f, 595f, 75f, bannerPaint)

                val titlePaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 18f
                    isFakeBoldText = true
                }
                cv.drawText("SPLITUP APP TESTING CHECKLIST", 40f, 45f, titlePaint)

                val subtitlePaint = Paint().apply {
                    color = Color.rgb(226, 232, 240)
                    textSize = 10f
                }
                cv.drawText("Generated on ${java.time.LocalDate.now()} • QA & Testing Guide", 40f, 62f, subtitlePaint)
            }

            drawHeader(canvas)
            var y = 100f

            val sectionPaint = Paint().apply {
                color = primaryNavy
                textSize = 13f
                isFakeBoldText = true
            }

            val bodyPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 10f
            }

            val checklistSections = listOf(
                Pair("1. Authentication & Security Flow", listOf(
                    "[ ] 1.1 Email/Password Registration (Firebase Auth & Email Verification)",
                    "[ ] 1.2 Demo / Guest Mode (Local offline guest account)",
                    "[ ] 1.3 Biometric Security Lock Activation & 4-digit PIN setup",
                    "[ ] 1.4 App Resume Biometric Overlay & PIN Fallback"
                )),
                Pair("2. Dashboard Screen & Analytics", listOf(
                    "[ ] 2.1 Summary Financial Cards (Total Spent, Owe, Owed)",
                    "[ ] 2.2 Monthly Income & Net Cash Flow widget update",
                    "[ ] 2.3 Interactive Category Chart & filter chips",
                    "[ ] 2.4 Quick Actions & Recent Activity Feed pull-to-refresh"
                )),
                Pair("3. Personal Expenses & Income", listOf(
                    "[ ] 3.1 Entry Type Toggle (Expense vs. Income / Refund)",
                    "[ ] 3.2 Category Auto-Detection from title keywords",
                    "[ ] 3.3 Numeric amount & decimal validation",
                    "[ ] 3.4 Search, filter, and Room database persistence",
                    "[ ] 3.5 Dynamic Date Transitions (Today -> Yesterday -> Date-only)"
                )),
                Pair("4. Groups & Split Management", listOf(
                    "[ ] 4.1 Group Creation, Currency & Member setup",
                    "[ ] 4.2 WhatsApp Invite integration & custom member share",
                    "[ ] 4.3 Group Expense Creation (Equal, Exact, % & Shares splits)",
                    "[ ] 4.4 Debt Simplification algorithm & Multi-Channel Settle Up",
                    "[ ] 4.5 Group Activity Feed, Comments & PDF Report Export",
                    "[ ] 4.6 Subcollection Expense Deletion Sync (`/groups/{id}/expenses`)"
                )),
                Pair("5. Smart AI Receipt Scanner & Cost Allocation", listOf(
                    "[ ] 5.1 Camera capture & Gallery picker permission handling",
                    "[ ] 5.2 Receipt OCR / Regex extraction (Merchant, Tax, Tip, Items)",
                    "[ ] 5.3 Manual Line Item addition on Extracted Receipt screen",
                    "[ ] 5.4 IME Keyboard insets & smooth scrolling (`.imePadding()`)",
                    "[ ] 5.5 Item-by-item cost allocation per group member"
                )),
                Pair("6. Subscriptions Hub & Recurring Automation", listOf(
                    "[ ] 6.1 Subscriptions Hub UI & Monthly Commitment view",
                    "[ ] 6.2 Add / Edit recurring subscriptions (Weekly/Monthly/Yearly)",
                    "[ ] 6.3 Calendar Date Picker (`DatePickerDialog`) for Next Due Date",
                    "[ ] 6.4 One-tap expense logging on upcoming due dates",
                    "[ ] 6.5 Background WorkManager automation worker execution"
                )),
                Pair("7. Data Backup, Import/Export & Cloud Sync", listOf(
                    "[ ] 7.1 RFC 4180 CSV Export & Import engine",
                    "[ ] 7.2 JSON Database backup & restore",
                    "[ ] 7.3 Live currency exchange rates sync (Frankfurter API)",
                    "[ ] 7.4 Cloud Firestore multi-user synchronization & clean restore (`isDirty=false`)"
                )),
                Pair("8. Non-Functional & System Testing", listOf(
                    "[ ] 8.1 Adaptive Multi-Pane Layouts (`ListDetailPaneScaffold`)",
                    "[ ] 8.2 Edge-to-edge system bar padding & transparency",
                    "[ ] 8.3 Screen rotation & configuration change state retention",
                    "[ ] 8.4 Process death & Room database persistence",
                    "[ ] 8.5 Interactive Donut Chart Tooltips & Shimmer Skeleton Loaders"
                ))
            )

            for ((sectionTitle, items) in checklistSections) {
                if (y > 700f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawHeader(canvas)
                    y = 100f
                }

                canvas.drawText(sectionTitle, 40f, y, sectionPaint)
                y += 18f

                for (item in items) {
                    if (y > 730f) {
                        pdfDocument.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        drawHeader(canvas)
                        y = 100f
                    }
                    canvas.drawText(item, 50f, y, bodyPaint)
                    y += 16f
                }
                y += 10f
            }

            pdfDocument.finishPage(page)

            val file = File(context.cacheDir, "splitup_testing_checklist.pdf")
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateGroupNetBalances(group: Group, targetCurrency: String): Map<String, Double> {
        fun matchMember(name: String): String {
            val trimmed = name.trim()
            return group.members.firstOrNull { it.equals(trimmed, ignoreCase = true) } ?: trimmed
        }
        val balances = group.members.associateWith { 0.0 }.toMutableMap()
        for (expense in group.expenses) {
            val matchedPayer = matchMember(expense.paidBy)
            val isIncome = expense.category == ExpenseCategories.INCOME || expense.amount < 0
            val converted = Currencies.convert(kotlin.math.abs(expense.amount), expense.currency, targetCurrency)
            if (isIncome) {
                balances[matchedPayer] = (balances[matchedPayer] ?: 0.0) - converted
                if (expense.splitMode == "CUSTOM" && expense.memberSplits.isNotEmpty()) {
                    for ((member, share) in expense.memberSplits) {
                        val m = matchMember(member)
                        balances[m] = (balances[m] ?: 0.0) + Currencies.convert(kotlin.math.abs(share), expense.currency, targetCurrency)
                    }
                } else {
                    val cleanSplit = expense.splitAmong.map { matchMember(it) }.distinct()
                    if (cleanSplit.isNotEmpty()) {
                        val rawShare = converted / cleanSplit.size
                        val roundedShare = kotlin.math.round(rawShare * 100) / 100.0
                        val rem = kotlin.math.round((converted - (roundedShare * cleanSplit.size)) * 100) / 100.0
                        cleanSplit.forEachIndexed { i, m ->
                            balances[m] = (balances[m] ?: 0.0) + (if (i == 0) roundedShare + rem else roundedShare)
                        }
                    }
                }
            } else {
                balances[matchedPayer] = (balances[matchedPayer] ?: 0.0) + converted
                if (expense.splitMode == "CUSTOM" && expense.memberSplits.isNotEmpty()) {
                    for ((member, share) in expense.memberSplits) {
                        val m = matchMember(member)
                        balances[m] = (balances[m] ?: 0.0) - Currencies.convert(kotlin.math.abs(share), expense.currency, targetCurrency)
                    }
                } else {
                    val cleanSplit = expense.splitAmong.map { matchMember(it) }.distinct()
                    if (cleanSplit.isNotEmpty()) {
                        val rawShare = converted / cleanSplit.size
                        val roundedShare = kotlin.math.round(rawShare * 100) / 100.0
                        val rem = kotlin.math.round((converted - (roundedShare * cleanSplit.size)) * 100) / 100.0
                        cleanSplit.forEachIndexed { i, m ->
                            balances[m] = (balances[m] ?: 0.0) - (if (i == 0) roundedShare + rem else roundedShare)
                        }
                    }
                }
            }
        }
        val rounded = balances.mapValues { kotlin.math.round(it.value * 100) / 100.0 }.toMutableMap()
        val residual = kotlin.math.round(rounded.values.sum() * 100) / 100.0
        if (kotlin.math.abs(residual) in 0.009..0.05 && rounded.isNotEmpty()) {
            val maxKey = rounded.maxByOrNull { kotlin.math.abs(it.value) }?.key
            if (maxKey != null) rounded[maxKey] = kotlin.math.round((rounded[maxKey]!! - residual) * 100) / 100.0
        }
        return rounded
    }
}
