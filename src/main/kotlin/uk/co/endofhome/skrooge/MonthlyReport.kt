package uk.co.endofhome.skrooge

import org.http4k.core.*
import org.http4k.format.Gson
import java.time.Month
import java.time.format.TextStyle
import java.util.*

class MonthlyReport(val gson: Gson, val decisionWriter: DecisionWriter) {
    fun handle(request: Request): Response {
        val year = request.query("year")!!.toInt()
        val month = Month.of(request.query("month")!!.toInt())
        val decisions = decisionWriter.read(year, month)

        return decisions.let { when {
                it.isNotEmpty() -> {
                    val catReportDataItems: List<CategoryReportDataItem> = decisions.map {
                        CategoryReportDataItem(it.subCategory!!.name, it.line.amount)
                    }.groupBy { it.name }.map {
                        it.value.reduce { acc, categoryReportDataItem -> CategoryReportDataItem(it.key, acc.actual + categoryReportDataItem.actual) }
                    }

                    val catReports = Categories.categories().map { category ->
                        CategoryReport(category.title, catReportDataItems.filter { category.subCategories.map { it.name }.contains(it.name) })
                    }.filter { it.data.isNotEmpty() }

                    val jsonReport = JsonReport(year, month.getDisplayName(TextStyle.FULL, Locale.UK), month.value, catReports)
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
data class JsonReport(val year: Int, val month: String, val monthNumber: Int, val categories: List<CategoryReport>)