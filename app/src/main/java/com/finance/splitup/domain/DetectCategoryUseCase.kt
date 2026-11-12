package com.finance.splitup.domain

import android.content.Context
import com.finance.splitup.data.CategoryDetector
import com.finance.splitup.data.CategoryResult
import com.finance.splitup.data.Expense
import com.finance.splitup.data.GroupExpense

class DetectCategoryUseCase {

    fun execute(title: String, context: Context? = null): CategoryResult {
        return CategoryDetector.detectCategory(title, context)
    }

    fun learnCategory(title: String, category: String, context: Context? = null) {
        CategoryDetector.learnCategory(title, category, context)
    }

    fun seedHistory(expenses: List<Expense>, groupExpenses: List<GroupExpense> = emptyList()) {
        CategoryDetector.seedHistory(expenses, groupExpenses)
    }
}
