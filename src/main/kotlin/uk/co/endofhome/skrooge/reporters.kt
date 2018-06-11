package uk.co.endofhome.skrooge

import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.Gson
import org.http4k.template.ViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class MonthlyReporter(private val gson: Gson,
                      private val decisionWriter: DecisionWriter,
                      private val decisionsToCategoryReports: List<Decision>.() -> List<CategoryReport>)
{
    fun handle(request: Request): Response {
        val year = request.query("year")!!.toInt()
        val month = Month.of(request.query("month")!!.toInt())
        val decisions = decisionWriter.read(year, month)

        return decisions.let { when {
                it.isNotEmpty() -> {
                    val catReports = decisionsToCategoryReports(decisions)

                    val jsonReport = MonthlyReport(year, month.getDisplayName(TextStyle.FULL, Locale.UK), month.value, catReports)
                    val jsonReportJson = gson.asJsonObject(jsonReport)

                    Response(Status.OK).body(jsonReportJson.toString())
                }
                else -> Response(Status.BAD_REQUEST)
            }
        }
    }
}


class AnnualReporter(private val gson: Gson,
                     private val decisionWriter: DecisionWriter,
                     private val decisionsToCategoryReports: List<Decision>.() -> List<CategoryReport>)
{

    fun handle(request: Request): Response {
        val startDateString = request.query("startDate")!!
        val startDate = LocalDate.parse(startDateString, DateTimeFormatter.ISO_DATE)
        val decisions = decisionWriter.readForYearStarting(startDate)

        return decisions.let { when {
            it.isNotEmpty() -> {
                val catReports = decisionsToCategoryReports(decisions)
                val jsonReport = AnnualReport(startDate, catReports)
                val jsonReportJson = gson.asJsonObject(jsonReport)

                Response(Status.OK).body(jsonReportJson.toString())
            }
            else -> Response(Status.BAD_REQUEST)
        }
        }
    }
}

data class CategoryReportDataItem(val name: String, val actual: Double)
data class CategoryReport(val title: String, val data: List<CategoryReportDataItem>)
data class MonthlyReport(val year: Int, val month: String, val monthNumber: Int, val categories: List<CategoryReport>) : ViewModel
data class AnnualReport(val startDate: LocalDate, val categories: List<CategoryReport>) : ViewModel

val toCategoryReports: List<Decision>.() -> List<CategoryReport> = {
    val catReportDataItems: List<CategoryReportDataItem> = this.map {
        CategoryReportDataItem(it.subCategory!!.name, it.line.amount)
    }.groupBy { it.name }.map {
        it.value.reduce { acc, categoryReportDataItem -> CategoryReportDataItem(it.key, acc.actual + categoryReportDataItem.actual) }
    }.map { it.copy(actual = BigDecimal.valueOf(it.actual).setScale(2, RoundingMode.HALF_UP).toDouble()) }

    CategoryHelpers.categories().map { category ->
        CategoryReport(category.title, catReportDataItems.filter { category.subcategories.map { it.name }.contains(it.name) })
    }.filter { it.data.isNotEmpty() }
}
