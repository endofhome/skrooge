package uk.co.endofhome.skrooge

import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.Gson
import org.http4k.format.Gson.asPrettyJsonString
import org.http4k.template.ViewModel
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class MonthlyReporter(private val gson: Gson,
                      private val decisionReaderWriter: DecisionReaderWriter,
                      private val categoryReporter: CategoryReporter) {

    operator fun invoke(request: Request): Response {
        val year = request.query("year")!!.toInt()
        val month = Month.of(request.query("month")!!.toInt())
        val decisions = decisionReaderWriter.read(year, month)

        return decisions.let {
            when {
                it.isNotEmpty() -> {
                    val catReports = categoryReporter.categoryReportsFrom(decisions)
                    val overview = categoryReporter.overviewFrom(catReports)

                    val sortedDecisions = decisions.sortedBy { it.line.date }
                    val dateOfFirstTransaction = sortedDecisions.first().line.date
                    val endDate = sortedDecisions.last().line.date
                    val budgetStartDate = categoryReporter.currentBudgetStartDateFor(dateOfFirstTransaction)?.startDateInclusive
                    val historicalCategoryReports: List<List<CategoryReport>> = when {
                        budgetStartDate != null -> {
                            val startOfPreviousPeriod = dateOfFirstTransaction.previousBudgetDate(budgetStartDate.dayOfMonth).minusMonths(1)

                            catReportsForPreviousMonths(startOfPreviousPeriod, budgetStartDate)
                        }
                        else -> emptyList()
                    }
                    val aggregatedOverview = categoryReporter.aggregatedOverviewFrom(overview, dateOfFirstTransaction, endDate, historicalCategoryReports)
                    val report = MonthlyReport(year, month.getDisplayName(TextStyle.FULL, Locale.UK), month.value, aggregatedOverview, overview, catReports)
                    val reportJson = gson.asJsonObject(report)
                    Response(Status.OK).body(reportJson.asPrettyJsonString())
                }
                else -> Response(Status.BAD_REQUEST)
            }
        }
    }

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

    private fun LocalDate.previousBudgetDate(budgetDayOfMonth: Int): LocalDate = when {
        this.dayOfMonth >= budgetDayOfMonth -> LocalDate.of(year, month, budgetDayOfMonth)
        else                                -> {
            when {
                this.month == Month.JANUARY -> LocalDate.of(year.minus(1), Month.DECEMBER, budgetDayOfMonth)
                else                        -> LocalDate.of(year, month.minus(1), budgetDayOfMonth)
            }
        }
    }
}

class AnnualReporter(private val gson: Gson,
                     private val decisionReaderWriter: DecisionReaderWriter,
                     private val categoryReporter: CategoryReporter) {

    operator fun invoke(request: Request): Response {
        val startDateString = request.query("startDate")!!
        val startDate = LocalDate.parse(startDateString, DateTimeFormatter.ISO_DATE)
        val decisions = decisionReaderWriter.readForYearStarting(startDate)

        return decisions.let {
            when {
                it.isNotEmpty() -> {
                    val catReports = categoryReporter.annualCategoryReportsFrom(decisions)
                    val report = AnnualReport(startDate, catReports)
                    val reportJson = gson.asJsonObject(report)
                    Response(Status.OK).body(reportJson.asPrettyJsonString())
                }
                else -> Response(Status.BAD_REQUEST)
            }
        }
    }
}

data class MonthlyReport(val year: Int, val month: String, val monthNumber: Int, val aggregateOverview: AggregateOverviewReport?, val overview: CategoryReport?, val categories: List<CategoryReport>) : ViewModel
data class AnnualReport(val startDate: LocalDate, val categories: List<AnnualCategoryReport>) : ViewModel
data class AnnualCategoryReport(val title: String, val data: List<AnnualCategoryReportDataItem>) : ViewModel
data class AnnualCategoryReportDataItem(val name: String, val actual: Double, val budget: Double, val annualBudget: Double)
