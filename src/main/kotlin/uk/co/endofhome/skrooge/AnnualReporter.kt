package uk.co.endofhome.skrooge

import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.Gson
import org.http4k.template.ViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

data class AnnualReport(val startDate: LocalDate, val categories: List<CategoryReport>) : ViewModel
