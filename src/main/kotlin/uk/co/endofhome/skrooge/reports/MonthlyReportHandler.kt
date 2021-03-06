package uk.co.endofhome.skrooge.reports

import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.Gson
import org.http4k.format.Gson.asPrettyJsonString
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.template.ViewModel
import uk.co.endofhome.skrooge.categories.AggregateOverviewReport
import uk.co.endofhome.skrooge.categories.CategoryReport
import uk.co.endofhome.skrooge.categories.CategoryReporter
import uk.co.endofhome.skrooge.decisions.DecisionReaderWriter
import uk.co.endofhome.skrooge.decisions.DecisionState.Decision
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

class MonthlyReportHandler(
    private val decisionReaderWriter: DecisionReaderWriter,
    private val categoryReporter: CategoryReporter
) {
    operator fun invoke(request: Request): Response {
        val year = Query.int().required("year").extract(request)
        val month = Month.of(Query.int().required("month").extract(request))
        val decisions = decisionReaderWriter.read(year, month)
        val report = when {
            decisions.isNotEmpty() -> monthlyJsonReport(decisions, year, month)
            else                   -> emptyReport()
        }

        return Response(Status.OK).body(report)
    }

    private fun monthlyJsonReport(decisions: List<Decision>, year: Int, month: Month): String {
        val catReports = categoryReporter.categoryReportsFrom(decisions)
        val overview = categoryReporter.overviewFrom(catReports)

        val sortedDecisions = decisions.sortedBy { it.line.date }
        val dateOfFirstTransaction = sortedDecisions.first().line.date
        val endDate = sortedDecisions.last().line.date
        val budgetStartDate = categoryReporter.currentBudgetStartDateFor(dateOfFirstTransaction)
        val historicalCategoryReports: List<List<CategoryReport>> =
            if (budgetStartDate != null) {
                val startOfPreviousPeriod = dateOfFirstTransaction.previousBudgetDate(budgetStartDate.dayOfMonth).minusMonths(1)
                catReportsForPreviousMonths(startOfPreviousPeriod, budgetStartDate)
            }
            else emptyList()
        val aggregatedOverview = categoryReporter.aggregatedOverviewFrom(overview, dateOfFirstTransaction, endDate, historicalCategoryReports)
        val report = MonthlyJsonReport(year, month.getDisplayName(TextStyle.FULL, Locale.UK), month.value, aggregatedOverview, overview, catReports)
        return Gson.asJsonObject(report).asPrettyJsonString()
    }

    private fun emptyReport() = Gson.asJsonObject(emptyMap<Any, Any>()).asPrettyJsonString()

    private fun catReportsForPreviousMonths(startOfThisPeriod: LocalDate, budgetStartDate: LocalDate?, catReports: List<List<CategoryReport>> = emptyList()): List<List<CategoryReport>> {
        if (startOfThisPeriod < budgetStartDate) return catReports

        // Necessary for the time being as decisionReaderWriter.read() method needs to know which file to read.
        // This should happen in some kind of datastore abstraction but the abstractions here are a little poor at the moment.
        val adjustmentValue = when {
            budgetStartDate?.dayOfMonth == 1  -> 0L
            else                              -> 1L
        }
        val adjustedStartOfThisPeriod = startOfThisPeriod.plusMonths(adjustmentValue)
        val lastMonthsDecisions: List<Decision> = decisionReaderWriter.read(adjustedStartOfThisPeriod.year, adjustedStartOfThisPeriod.month)
        val catReportForLastMonth: List<CategoryReport> = categoryReporter.categoryReportsFrom(lastMonthsDecisions)
        return catReportsForPreviousMonths(startOfThisPeriod.minusMonths(1), budgetStartDate, catReports + listOf(catReportForLastMonth))
    }

    private fun LocalDate.previousBudgetDate(budgetDayOfMonth: Int): LocalDate =
        if (this.dayOfMonth >= budgetDayOfMonth) {
            LocalDate.of(year, month, budgetDayOfMonth)
        }
        else {
            when {
                this.month == Month.JANUARY -> LocalDate.of(year.minus(1), Month.DECEMBER, budgetDayOfMonth)
                else                        -> LocalDate.of(year, month.minus(1), budgetDayOfMonth)
            }
        }
}

data class MonthlyJsonReport(
        val year: Int,
        val month: String,
        val monthNumber: Int,
        val aggregateOverview: AggregateOverviewReport?,
        val overview: CategoryReport?,
        val categories: List<CategoryReport>
) : ViewModel
