package uk.co.endofhome.skrooge

import java.math.BigDecimal
import java.math.RoundingMode

class CategoryReporter(val categories: List<Category>, private val annualBudgets: AnnualBudgets) {

    fun categoryReportsFrom(decisions: List<Decision>): List<CategoryReport> {
        val catReportDataItems: List<CategoryReportDataItem> = decisions.map {
            val budgetAmount = annualBudgets.valueFor(it.category!!, it.subCategory!!, it.line.date)
            CategoryReportDataItem(it.subCategory.name, it.line.amount, budgetAmount)
        }.groupBy { it.name }.map {
            it.value.reduce { acc, categoryReportDataItem -> CategoryReportDataItem(it.key, acc.actual + categoryReportDataItem.actual, categoryReportDataItem.budget) }
        }.map { it.copy(actual = BigDecimal.valueOf(it.actual).setScale(2, RoundingMode.HALF_UP).toDouble()) }

        return categories.map { category ->
            CategoryReport(category.title, catReportDataItems.filter { category.subcategories.map { it.name }.contains(it.name) })
        }.filter { it.data.isNotEmpty() }
    }
}

data class CategoryReport(val title: String, val data: List<CategoryReportDataItem>)
data class CategoryReportDataItem(val name: String, val actual: Double, val budget: Double)
