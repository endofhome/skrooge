package uk.co.endofhome.skrooge

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Period

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

    fun annualCategoryReportsFrom(decisions: List<Decision>): List<AnnualCategoryReport> {
        val totalNumberOfMonths = 12
        val sortedDecisions = decisions.sortedBy { it.line.date }
        val dateOfFirstTransaction = sortedDecisions.first().line.date
        val startDate = annualBudgets.budgetFor(dateOfFirstTransaction)!!.startDateInclusive
        val endDate = sortedDecisions.last().line.date
        val numberOfMonthsSoFar = Period.between(startDate, endDate).months

        val catReportDataItems: List<AnnualCategoryReportDataItem> =
                decisions.map {
                    val budgetAmount = annualBudgets.valueFor(it.category!!, it.subCategory!!, it.line.date)
                    AnnualCategoryReportDataItem(it.subCategory.name, it.line.amount, budgetAmount * numberOfMonthsSoFar, budgetAmount * totalNumberOfMonths) }
                        .groupBy { it.name }
                        .map {
                            it.value.reduce {
                                acc, annualCategoryReportDataItem ->
                                AnnualCategoryReportDataItem(it.key, acc.actual + annualCategoryReportDataItem.actual, annualCategoryReportDataItem.budget, annualCategoryReportDataItem.annualBudget)
                            }}
                        .map { it.copy(actual = BigDecimal.valueOf(it.actual).setScale(2, RoundingMode.HALF_UP).toDouble()) }

        return categories.map { category ->
            AnnualCategoryReport(
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
