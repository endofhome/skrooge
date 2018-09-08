package uk.co.endofhome.skrooge

import java.math.BigDecimal
import java.math.RoundingMode

class CategoryReporter(val categories: List<Category>, private val annualBudgets: AnnualBudgets) {

    fun categoryReportsFrom(decisions: List<Decision>, numberOfMonths: Int = 1): List<CategoryReport> {
        val catReportDataItems: List<CategoryReportDataItem> =
            decisions.map {
                        val budgetAmount = annualBudgets.valueFor(it.category!!, it.subCategory!!, it.line.date)
                        CategoryReportDataItem(it.subCategory.name, it.line.amount, budgetAmount * numberOfMonths) }
                     .groupBy { it.name }
                     .map {
                        it.value.reduce {
                            acc, categoryReportDataItem ->
                            CategoryReportDataItem(it.key, acc.actual + categoryReportDataItem.actual, categoryReportDataItem.budget)
                        }}
                     .map { it.copy(actual = BigDecimal.valueOf(it.actual).setScale(2, RoundingMode.HALF_UP).toDouble()) }

        return categories.map { category ->
            CategoryReport(
                    category.title,
                    catReportDataItems.filter { dataItem -> category.subcategories.map { it.name }.contains(dataItem.name) }
            )
        }.filter { it.data.isNotEmpty() }
    }

    fun overviewFrom(categoryReports: List<CategoryReport>): CategoryReport {
        val overviewCategoryReport: List<CategoryReportDataItem> =  categoryReports.map { categoryReport ->
            categoryReport.data.reduce { acc, categoryReportDataItem ->
                CategoryReportDataItem(categoryReport.title, acc.actual + categoryReportDataItem.actual, acc.budget + categoryReportDataItem.budget)
            }
        }
        return CategoryReport("Overview", overviewCategoryReport)
    }
}

data class CategoryReport(val title: String, val data: List<CategoryReportDataItem>)
data class CategoryReportDataItem(val name: String, val actual: Double, val budget: Double)
