package uk.co.endofhome.skrooge

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.FormFile
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.query
import org.http4k.core.with
import org.http4k.lens.MultipartForm
import org.http4k.lens.MultipartFormField
import org.http4k.lens.MultipartFormFile
import org.http4k.lens.Validator
import org.http4k.lens.multipartForm
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel
import org.http4k.template.view
import java.io.File
import java.math.BigDecimal
import java.time.Month
import java.time.Year
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class Statements(private val categories: Categories) {

    fun uploadStatements(request: Request, renderer: TemplateRenderer): Response {
        val form = try {
            FormForNormalisedStatement.from(request)
        } catch (e: IllegalStateException) {
            return Response(Status.BAD_REQUEST)
        }

        val (year, month, user, statement, file) = form
        val fileBytes = file.content.readBytes()
        val statementFile = File("input/normalised/$year-${format(month)}_${user.capitalize()}_$statement.csv")
        statementFile.writeBytes(fileBytes)

        val decisions = StatementDecider(categories.categoryMappings).process(statementFile.readLines())
        val unknownMerchants: Set<String> = decisions.filter { it.category == null }
                                                     .map { it.line.merchant }
                                                     .toSet()
        return when {
            unknownMerchants.isEmpty() -> pleaseReviewYourCategorisations(form, decisions, renderer)
            else                       -> redirectToUnknownMerchant(form, unknownMerchants)
        }
    }

    private fun pleaseReviewYourCategorisations(form: FormForNormalisedStatement, decisions: List<Decision>, renderer: TemplateRenderer): Response {
        val (year, month, user, statement) = form
        val formattedBankStatement = FormattedBankStatement(
                year.toString(),
                month.name.toLowerCase().capitalize(),
                user,
                statement,
                decisions.sortedBy { it.line.date }.map { decision ->
                    FormattedDecision(
                            LineFormatter.format(decision.line),
                            decision.category,
                            decision.subCategory,
                            categories.withSelection(decision.subCategory)
                    )
                }
        )

        val view = Body.view(renderer, ContentType.TEXT_HTML)
        val reviewCategorisationsViewModel = PleaseReviewYourCategorisations(
                formattedBankStatement,
                emptyList()
        )
        return Response(Status.OK).with(view of reviewCategorisationsViewModel)
    }

    private fun redirectToUnknownMerchant(form: FormForNormalisedStatement, unknownMerchants: Set<String>): Response {
        val (year, month, user, statement) = form
        val currentMerchant = unknownMerchants.first()
        val outstandingMerchants = unknownMerchants.drop(1)
        val uri = Uri.of("/unknown-merchant").query("currentMerchant", currentMerchant)
                .query("outstandingMerchants", outstandingMerchants.joinToString(","))
                .query("originalRequestBody", "$year;${month.getDisplayName(TextStyle.FULL, Locale.UK)};$user;$statement")
        return Response(Status.SEE_OTHER).header("Location", uri.toString())
    }

    private fun format(month: Month) = month.value.toString().padStart(2, '0')
}

data class FormForNormalisedStatement(val year: Year, val month: Month, val user: String, val statement: String, val file: FormFile) {
    companion object {
        fun from(request: Request): FormForNormalisedStatement {
            val yearName = "year"
            val monthName = "month"
            val userName = "user"
            val statementName = "statement"
            val multipartForm = extractFormParts(request, yearName, monthName, userName, statementName)
            val fields = multipartForm.fields
            val files = multipartForm.files

            val year = fields[yearName]?.firstOrNull()
            val month = fields[monthName]?.firstOrNull()
            val user = fields[userName]?.firstOrNull()
            val statement = fields[statementName]?.firstOrNull()
            val formFile = files[statementName]?.firstOrNull()

            if (year != null && month != null && user != null && statement != null && formFile != null) {
                return FormForNormalisedStatement(Year.parse(year), Month.valueOf(month.toUpperCase()), user, statement, formFile)
            } else {
                throw IllegalStateException(
                        """Form fields cannot be null, but were:
                            |year: $year
                            |month: $month
                            |user: $user
                            |statement: $statement
                            |formFile: $formFile
                        """.trimMargin()
                )
            }
        }

        private fun extractFormParts(request: Request, yearName: String, monthName: String, userName: String, statementName: String): MultipartForm {
            val yearLens = MultipartFormField.required(yearName)
            val monthLens = MultipartFormField.required(monthName)
            val userLens = MultipartFormField.required(userName)
            val statementNameLens = MultipartFormField.required(statementName)
            val statementFileLens = MultipartFormFile.required(statementName)
            val multipartFormBody = Body.multipartForm(Validator.Feedback, yearLens, monthLens, userLens, statementNameLens, statementFileLens).toLens()

            return multipartFormBody.extract(request)
        }
    }
}

object LineFormatter {
    fun format(line: Line) = FormattedLine(
            line.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            line.merchant,
            line.amount.roundTo2DecimalPlaces()
    )

    private fun Double.roundTo2DecimalPlaces() =
            BigDecimal(this).setScale(2, BigDecimal.ROUND_HALF_UP).toString()
}

data class PleaseReviewYourCategorisations(val bankStatement: FormattedBankStatement, val outstandingStatements: List<FormattedBankStatement>) : ViewModel

data class FormattedBankStatement(val year: String, val month: String, val username: String, val bankName: String, val formattedDecisions: List<FormattedDecision>)
data class FormattedLine(val date: String, val merchant: String, val amount: String)
data class FormattedDecision(val line: FormattedLine, val category: Category?, val subCategory: SubCategory?, val categoriesWithSelection: CategoriesWithSelection)
data class StatementData(val year: Year, val month: Month, val username: String, val statement: String)
