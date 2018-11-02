package uk.co.endofhome.skrooge.decisions

import org.http4k.core.Body
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.FormField
import org.http4k.lens.Validator
import org.http4k.lens.webForm
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.index
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.statements.StatementMetadata
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.monthName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.statement
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.userName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.yearName
import java.time.LocalDate
import java.time.Month
import java.time.Year

class DecisionsHandler(private val decisionReaderWriter: DecisionReaderWriter, val categories: Categories) {
    companion object {
        const val decision = "decision"
    }

    operator fun invoke(request: Request): Response {
        val yearLens = FormField.required(yearName)
        val monthLens = FormField.required(monthName)
        val userLens = FormField.required(userName)
        val statementLens = FormField.required(statement)
        val decisionLens = FormField.multi.required(decision)
        val webForm = Body.webForm(
            Validator.Feedback,
            yearLens,
            monthLens,
            userLens,
            statementLens,
            decisionLens
        )
        val form = webForm.toLens().extract(request)

        return if (form.errors.isEmpty()) {
            val decisions: List<Decision> = decisionLens.extract(form).map {
                val (presentationFormattedDate, merchant, amount, categoryName, subCategoryName) = it.split(",")
                Decision(
                    Line(reformat(presentationFormattedDate), merchant, amount.toDouble()),
                    categories.get(categoryName),
                    SubCategory(subCategoryName)
                )
            }

            val statementMetadata = StatementMetadata(
                year = Year.parse(yearLens.extract(form)),
                month = Month.valueOf(monthLens.extract(form).toUpperCase()),
                user = userLens.extract(form),
                statement = statementLens.extract(form)
            )
            decisionReaderWriter.write(statementMetadata, decisions)

            Response(Status.SEE_OTHER).header("Location", index)
        } else {
            val fieldsWithErrors = form.errors.map { it.meta.name }
            val osNewline = System.lineSeparator()

            Response(Status.BAD_REQUEST).body(
                "Form fields were missing:$osNewline${fieldsWithErrors.joinToString(osNewline)}"
            )
        }
    }

    private fun reformat(presentationFormattedDate: String): LocalDate {
        val dateParts = presentationFormattedDate.split("/")
        val day = Integer.valueOf(dateParts[0])
        val month = Month.of(Integer.valueOf(dateParts[1]))
        val year = Integer.valueOf(dateParts[2])
        return LocalDate.of(year, month, day)
    }
}
