package uk.co.endofhome.skrooge.reports

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.with
import org.http4k.template.ViewModel
import org.http4k.template.view
import uk.co.endofhome.skrooge.Skrooge.Companion.renderer
import java.time.Month
import java.time.Year
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

object BarChartHandler {
    operator fun invoke(request: Request): Response {
        val year: String? = request.query("year")
        val month: String? = request.query("month")
        return when {
            year == null || month == null -> Response(BAD_REQUEST)
            else                          -> (year to month).toYearMonth().toChartResponse()
        }
    }

    private fun Pair<String, String>.toYearMonth(): YearMonth? {
        val yearInt = first.toInt()
        val monthInt = second.toInt()
        return try {
            YearMonth.of(yearInt, monthInt)
        } catch (e: Exception) {
            null
        }
    }

    private fun YearMonth?.toChartResponse(): Response =
        when (this) {
            null -> Response(BAD_REQUEST)
            else -> {
                val monthName = Month.of(monthValue).getDisplayName(TextStyle.FULL, Locale.UK)
                val chartView = MonthlyBarChartReport(year, monthName, monthValue)
                val view = Body.view(renderer, ContentType.TEXT_HTML)
                Response(Status.OK).with(view of chartView)
            }
        }
}

data class MonthlyBarChartReport(
    val year: Int,
    val month: String,
    val monthNumber: Int,
    val years: List<Int> = generateSequence(2017) { yearValue -> (yearValue + 1).takeIf { it <= Year.now().value } }.toList(),
    val months: List<Int> = (1..12).toList()
) : ViewModel
