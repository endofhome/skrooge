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

    fun categoryReportsFrom(decisions: List<Decision>): List<CategoryReport> =
        categories.map { mapItem ->
            CategoryReport(
                mapItem.key.title,
                mapItem.buildJsonData(decisions)
            )
        }

    private fun Map.Entry<Category, List<SubCategory>>.buildJsonData(decisions: List<Decision>): List<DataItemJson> {
        val dataItems = dataItemsFrom(decisions)

        return value.mapNotNull { subCategory ->
            if (dataItems.isNotEmpty()) {
                val dataItemWithNilActual: DataItem by lazy {
                    DataItem(
                        subCategory = subCategory,
                        actual = BigDecimal.ZERO,
                        budget = annualBudgets.valueFor(subCategory, decisions.first().line.date)
                    )
                }
                dataItems.find { dataItem ->
                    subCategory == dataItem.subCategory
                } ?: dataItemWithNilActual
            } else {
                null
            }
        }.map { dataItem ->
            dataItem.toJsonData()
        }
    }

    private fun dataItemsFrom(decisions: List<Decision>): List<DataItem> =
        decisions.map { decision ->
            val budgetAmount = annualBudgets.valueFor(decision.subCategory, decision.line.date)
            DataItem(decision.subCategory, decision.line.amount, budgetAmount)
        }.groupBy { reportDataItem ->
            reportDataItem.subCategory
        }.map { mapEntry ->
            mapEntry.value.reduce { acc, categoryReportDataItem ->
                DataItem(mapEntry.key, acc.actual + categoryReportDataItem.actual, categoryReportDataItem.budget)
            }
        }.map { reportDataItem ->
            reportDataItem.copy(actual = reportDataItem.actual)
        }

    fun overviewFrom(categoryReports: List<CategoryReport>): CategoryReport {
        val overviewCategoryReport: List<DataItemJson> = categoryReports.map { categoryReport ->
            categoryReport.data.fold(DataItemJson(categoryReport.title, BigDecimal.ZERO, BigDecimal.ZERO)) { acc, categoryReportDataItem ->
                DataItemJson(
                    name = categoryReport.title,
                    actual = (acc.actual + categoryReportDataItem.actual).setScale(2, RoundingMode.HALF_UP),
                    budget = acc.budget + categoryReportDataItem.budget.setScale(2, RoundingMode.HALF_UP)
                )
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

        val actualExpenditure: BigDecimal = categoryReport.data.map { it.actual }.reduce { acc, bigDecimal ->  acc + bigDecimal }.setScale(2, RoundingMode.HALF_UP)
        val yearToDateActual: BigDecimal = historicalCategoryReports.toYearToDateActual(actualExpenditure)
        val budgetedExpenditure = firstRelevantBudget.budgetData.map { it.second }.reduce { acc, bigDecimal ->  acc + bigDecimal }.setScale(2, RoundingMode.HALF_UP)
        val data = AggregatedOverviewData("Overview", actualExpenditure, yearToDateActual, budgetedExpenditure, budgetedExpenditure * BigDecimal(numberOfMonthsSoFar), budgetedExpenditure * BigDecimal(12))

        return AggregateOverviewReport("Aggregated Overview", data)
    }

    fun currentBudgetStartDateFor(date: LocalDate): LocalDate? =
        try {
            annualBudgets.budgetFor(date).startDateInclusive
        } catch (e: IllegalStateException) {
            null
        }

    private fun List<List<CategoryReport>>.toYearToDateActual(actualExpenditure: BigDecimal): BigDecimal =
        flatMap { catReports ->
            catReports.flatMap { catReport ->
                catReport.data
            }
        }.map { dateItemJson ->
            dateItemJson.actual
        }.fold(BigDecimal.ZERO) { acc, actual ->
            acc + actual
        } + actualExpenditure
            .setScale(2, RoundingMode.HALF_UP)

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
            else -> LocalDate.of(year, month.plus(1), budgetDayOfMonth)
        }

    }
}

data class CategoryReport(val title: String, val data: List<DataItemJson>)
data class AggregateOverviewReport(val title: String, val data: AggregatedOverviewData)
data class DataItem(val subCategory: SubCategory, val actual: BigDecimal, val budget: BigDecimal) {
    fun toJsonData() = DataItemJson(subCategory.name, actual.setScale(2, RoundingMode.HALF_UP), budget.setScale(2, RoundingMode.HALF_UP))
}
data class DataItemJson(val name: String, val actual: BigDecimal, val budget: BigDecimal)
data class AggregatedOverviewData(val name: String, val actual: BigDecimal, val yearToDateActual: BigDecimal, val budget: BigDecimal, val yearToDateBudget: BigDecimal, val annualBudget: BigDecimal)
