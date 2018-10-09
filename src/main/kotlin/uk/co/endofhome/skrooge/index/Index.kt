package uk.co.endofhome.skrooge.index

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel
import org.http4k.template.view
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

class IndexHandler(private val renderer: TemplateRenderer) {
    operator fun invoke(): Response {
        val years = listOf(DisplayYear.of("2017"), DisplayYear.of("2018"), DisplayYear.of("2019"))
        val months = Month.values().map { DisplayMonth.of(it) }
        val users = listOf(System.getenv("PARTICIPANT_TWO"), System.getenv("PARTICIPANT_ONE"), System.getenv("PARTICIPANT_THREE"))
        val statementTypes = listOf(
                StatementType(System.getenv("BANK_FIVE"), System.getenv("BANK_FIVE_DISPLAY_NAME")),
                StatementType(System.getenv("BANK_SIX"), System.getenv("BANK_SIX_DISPLAY_NAME")),
                StatementType(System.getenv("BANK_ONE"), System.getenv("BANK_ONE_DISPLAY_NAME")),
                StatementType(System.getenv("BANK_THREE"), System.getenv("BANK_THREE_DISPLAY_NAME")),
                StatementType(System.getenv("BANK_FOUR"), System.getenv("BANK_FOUR_DISPLAY_NAME")),
                StatementType(System.getenv("BANK_TWO"), System.getenv("BANK_TWO_DISPLAY_NAME")),
                StatementType(System.getenv("BANK_SEVEN"), System.getenv("BANK_SEVEN_DISPLAY_NAME")),
                StatementType(System.getenv("BANK_EIGHT"), System.getenv("BANK_EIGHT_DISPLAY_NAME"))
        )
        val index = Index(years, months, users, statementTypes)
        val view = Body.view(renderer, ContentType.TEXT_HTML)
        return Response(Status.OK).with(view of index)
    }
}

data class StatementType(val id: String, val displayName: String)

data class DisplayYear(val name: String, val selector: String) {
    companion object {
        fun of(year: String): DisplayYear = DisplayYear(year, selectedString(year))

        private fun selectedString(year: String): String {
            val selectedYear = LocalDate.now().year.toString()
            return when (year) {
                selectedYear -> " selected"
                else        -> ""
            }
        }
    }
}

data class DisplayMonth(val name: String, val selector: String) {
    companion object {
        fun of(month: Month): DisplayMonth =
                DisplayMonth(month.getDisplayName(TextStyle.FULL, Locale.UK), selectedString(month))

        private fun selectedString(month: Month): String {
            val selectedMonth = LocalDate.now().month
            return when (month) {
                selectedMonth -> " selected"
                else          -> ""
            }
        }
    }
}

data class Index(val years: List<DisplayYear>, val months: List<DisplayMonth>, val users: List<String>, val statementTypes: List<StatementType>) : ViewModel
