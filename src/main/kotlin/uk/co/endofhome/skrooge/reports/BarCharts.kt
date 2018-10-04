package uk.co.endofhome.skrooge.reports

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.with
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel
import org.http4k.template.view
import uk.co.endofhome.skrooge.categories.AggregateOverviewReport
import uk.co.endofhome.skrooge.categories.CategoryReport
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

object BarCharts {
    operator fun invoke(request: Request, renderer: TemplateRenderer): Response {
        val year: String? = request.query("year")
        val month: String? = request.query("month")
        return when {
            year == null || month == null -> Response(BAD_REQUEST)
            else                          -> (year to month).toYearMonth().toChartResponse(renderer)
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

    private fun YearMonth?.toChartResponse(renderer: TemplateRenderer): Response {
        return when (this) {
            null -> Response(BAD_REQUEST)
            else -> {
                val monthName = Month.of(monthValue).getDisplayName(TextStyle.FULL, Locale.UK)
                val chartView = MonthlyReport(year, monthName, monthValue, null, null, emptyList())
                val view = Body.view(renderer, ContentType.TEXT_HTML)
                Response(Status.OK).with(view of chartView)
            }
        }
    }
}

data class MonthlyReport(
        val year: Int,
        val month: String,
        val monthNumber: Int,
        val aggregateOverview: AggregateOverviewReport?,
        val overview: CategoryReport?,
        val categories: List<CategoryReport>,
        val years: List<Int> = listOf(2017, 2018),
        val months: List<Int> = (1..12).toList()
) : ViewModel
