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

    fun handle(request: Request): Response {
        val year = request.query("year")!!.toInt()
        val month = Month.of(request.query("month")!!.toInt())
        val decisions = decisionReaderWriter.read(year, month)

        return decisions.let { when {
                it.isNotEmpty() -> {
                    val catReports = categoryReporter.categoryReportsFrom(decisions)
                    val overview = categoryReporter.overviewFrom(catReports)
                    val aggregatedOverview: CategoryReport = aggregate(overview)

                    val jsonReport = MonthlyReport(year, month.getDisplayName(TextStyle.FULL, Locale.UK), month.value, aggregatedOverview, overview, catReports)
                    val jsonReportJson = gson.asJsonObject(jsonReport)
                    Response(Status.OK).body(jsonReportJson.asPrettyJsonString())
                }
                else -> Response(Status.BAD_REQUEST)
            }
        }
    }

    private fun aggregate(categoryReport: CategoryReport): CategoryReport {
        val actual = categoryReport.data.map { it.actual }.sum()
        val budget = categoryReport.data.map { it.budget }.sum()
        val data = CategoryReportDataItem("Overview", actual, budget)

        return CategoryReport("Aggregated Overview", listOf(data))
    }
}


class AnnualReporter(private val gson: Gson,
                     private val decisionReaderWriter: DecisionReaderWriter,
                     private val categoryReporter: CategoryReporter) {

    fun handle(request: Request): Response {
        val startDateString = request.query("startDate")!!
        val startDate = LocalDate.parse(startDateString, DateTimeFormatter.ISO_DATE)
        val decisions = decisionReaderWriter.readForYearStarting(startDate)

        return decisions.let { when {
            it.isNotEmpty() -> {
                val catReports = categoryReporter.annualCategoryReportsFrom(decisions)

                val jsonReport = AnnualReport(startDate, catReports)
                val jsonReportJson = gson.asJsonObject(jsonReport)

                Response(Status.OK).body(jsonReportJson.asPrettyJsonString())
            }
            else -> Response(Status.BAD_REQUEST)
        }
        }
    }
}

data class MonthlyReport(val year: Int, val month: String, val monthNumber: Int, val aggregateOverview: CategoryReport?, val overview: CategoryReport?, val categories: List<CategoryReport>) : ViewModel
data class AnnualReport(val startDate: LocalDate, val categories: List<AnnualCategoryReport>) : ViewModel
data class AnnualCategoryReport(val title: String, val data: List<AnnualCategoryReportDataItem>) : ViewModel
data class AnnualCategoryReportDataItem(val name: String, val actual: Double, val budget: Double, val annualBudget: Double)
