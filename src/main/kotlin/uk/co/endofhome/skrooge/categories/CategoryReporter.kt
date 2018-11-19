package uk.co.endofhome.skrooge.categories

import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.decisions.DecisionState.Decision
import uk.co.endofhome.skrooge.decisions.SubCategory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Month.DECEMBER
import java.time.Month.JANUARY
import java.time.Period

class CategoryReporter(val categories: Map<Category, List<SubCategory>>, private val annualBudgets: AnnualBudgets) {

    fun categoryReportsFrom(decisions: List<Decision>, numberOfMonths: Int = 1): List<CategoryReport> {
        val catReportDataItems: List<CategoryReportDataItem> =
            decisions.map { decision ->
                val budgetAmount = annualBudgets.valueFor(decision.subCategory, decision.line.date)
                CategoryReportDataItem(decision.subCategory.name, decision.line.amount, budgetAmount * numberOfMonths)
            }.groupBy { reportDataItem ->
                reportDataItem.name
            }.map { mapEntry ->
                mapEntry.value.reduce { acc, categoryReportDataItem ->
                    CategoryReportDataItem(mapEntry.key, acc.actual + categoryReportDataItem.actual, categoryReportDataItem.budget)
                }
            }.map { reportDataItem ->
                reportDataItem.copy(actual = BigDecimal.valueOf(reportDataItem.actual).setScale(2, RoundingMode.HALF_UP).toDouble())
            }

        return categories.map { mapItem ->
            CategoryReport(
                mapItem.key.title,
                catReportDataItems.filter {
                    dataItem -> mapItem.value.map { subcategory ->
                        subcategory.name
                    }.contains(dataItem.name)
                }
            )
        }.filter { categoryReport ->
            categoryReport.data.isNotEmpty()
        }
    }

    fun overviewFrom(categoryReports: List<CategoryReport>): CategoryReport {
        val overviewCategoryReport: List<CategoryReportDataItem> = categoryReports.map { categoryReport ->
            categoryReport.data.reduce { acc, categoryReportDataItem ->
                CategoryReportDataItem(categoryReport.title, acc.actual + categoryReportDataItem.actual, acc.budget + categoryReportDataItem.budget)
            }
        }
        return CategoryReport("Overview", overviewCategoryReport)
    }

    fun aggregatedOverviewFrom(categoryReport: CategoryReport, firstTransactionDate: LocalDate, lastTransactionDate: LocalDate, historicalCategoryReports: List<List<CategoryReport>>): AggregateOverviewReport {
        val firstRelevantBudget = annualBudgets.budgetFor(firstTransactionDate)
        val firstBudgetStartDate = firstRelevantBudget.startDateInclusive
        val budgetDayOfMonth = firstBudgetStartDate.dayOfMonth
        val lastBudgetEndDate = lastTransactionDate.nextBudgetDate(budgetDayOfMonth)
        val numberOfMonthsSoFar = totalMonths(firstBudgetStartDate, lastBudgetEndDate)

        val actualExpenditure = categoryReport.data.map { it.actual }.sum()
        val yearToDateActual: Double = historicalCategoryReports.flatMap { it.flatMap { it.data } }.map { it.actual }.fold(0.0) { acc, actual -> acc + actual } + actualExpenditure
        val budgetedExpenditure = firstRelevantBudget.budgetData.map { it.second }.sum()
        val data = AggregatedOverviewData("Overview", actualExpenditure, yearToDateActual, budgetedExpenditure, budgetedExpenditure * numberOfMonthsSoFar, budgetedExpenditure * 12)

        return AggregateOverviewReport("Aggregated Overview", data)
    }

    fun currentBudgetStartDateFor(date: LocalDate): LocalDate? =
        try {
            annualBudgets.budgetFor(date).startDateInclusive
        } catch (e: IllegalStateException) {
            null
        }

    private fun totalMonths(startDate: LocalDate, endDate: LocalDate): Int {
        val period = Period.between(startDate, endDate)
        return (period.years * 12) + period.months
    }
}

private fun LocalDate.nextBudgetDate(budgetDayOfMonth: Int): LocalDate = when {
    this.dayOfMonth <= budgetDayOfMonth -> LocalDate.of(year, month, budgetDayOfMonth)
    else -> {
        when (this.month) {
            DECEMBER -> LocalDate.of(year.plus(1), JANUARY, budgetDayOfMonth)
            else     -> LocalDate.of(year, month.plus(1), budgetDayOfMonth)
        }

    }
}

data class CategoryReport(val title: String, val data: List<CategoryReportDataItem>)
data class AggregateOverviewReport(val title: String, val data: AggregatedOverviewData)
data class CategoryReportDataItem(val name: String, val actual: Double, val budget: Double)
data class AggregatedOverviewData(val name: String, val actual: Double, val yearToDateActual: Double, val budget: Double, val yearToDateBudget: Double, val annualBudget: Double)
