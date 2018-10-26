package uk.co.endofhome.skrooge.statements

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.FormFile
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.query
import org.http4k.core.with
import org.http4k.lens.FormField
import org.http4k.lens.MultipartForm
import org.http4k.lens.MultipartFormField
import org.http4k.lens.MultipartFormFile
import org.http4k.lens.Validator
import org.http4k.lens.WebForm
import org.http4k.lens.multipartForm
import org.http4k.lens.webForm
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel
import org.http4k.template.view
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.unknownMerchant
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.categories.CategoriesWithSelection
import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.decisions.Decision
import uk.co.endofhome.skrooge.decisions.Line
import uk.co.endofhome.skrooge.decisions.SubCategory
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.monthName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.statement
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.userName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.yearName
import java.io.File
import java.math.BigDecimal
import java.nio.file.Path
import java.time.Month
import java.time.Year
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class StatementsHandler(private val renderer: TemplateRenderer, val categories: Categories, private val normalisedStatements: Path) {

    fun withFileContents(request: Request): Response {
        val form = try {
            FormForNormalisedStatement.fromMultiPart(request, normalisedStatements)
        } catch (e: IllegalStateException) {
            return Response(Status.BAD_REQUEST)
        }

        return routeForStatement(form.statementMetadata, form.file)
    }

    fun withFilePath(request: Request): Response {
        val form = try {
            FormForNormalisedStatement.fromUrlEncoded(request)
        } catch (e: IllegalStateException) {
            return Response(Status.BAD_REQUEST)
        }

        return routeForStatement(form.statementMetadata, form.file)
    }

    private fun routeForStatement(statementMetadata: StatementMetadata, statementFile: File): Response {
        val decisions = StatementDecider(categories.categoryMappings).process(statementFile.readLines())
        val unknownMerchants: Set<String> = decisions.filter { it.category == null }
                                                     .map { it.line.merchant }
                                                     .toSet()
        return when {
            unknownMerchants.isEmpty() -> pleaseReviewYourCategorisations(statementMetadata, statementFile, decisions)
            else                       -> redirectToUnknownMerchant(statementMetadata, unknownMerchants)
        }
    }

    private fun pleaseReviewYourCategorisations(statementMetadata: StatementMetadata, statementFile: File, decisions: List<Decision>): Response {
        val (year, month, user, statementName) = statementMetadata
        val formattedBankStatement = FormattedBankStatement(
                year.toString(),
                month.name.toLowerCase().capitalize(),
                user,
                statementName,
                statementFile.path,
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
        val reviewCategorisationsViewModel = PleaseReviewYourCategorisations(formattedBankStatement)
        return Response(Status.OK).with(view of reviewCategorisationsViewModel)
    }

    private fun redirectToUnknownMerchant(statementMetadata: StatementMetadata, unknownMerchants: Set<String>): Response {
        val (year, month, user, statement) = statementMetadata
        val currentMerchant = unknownMerchants.first()
        val outstandingMerchants = unknownMerchants.drop(1)
        val uri = Uri.of(unknownMerchant).query("currentMerchant", currentMerchant)
                .query("outstandingMerchants", outstandingMerchants.joinToString(","))
                .query("originalRequestBody", "$year;${month.getDisplayName(TextStyle.FULL, Locale.UK)};$user;$statement")
        return Response(Status.SEE_OTHER).header("Location", uri.toString())
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

data class FormForNormalisedStatement(val statementMetadata: StatementMetadata, val file: File) {
    companion object {
        fun fromMultiPart(request: Request, normalisedStatements: Path): FormForNormalisedStatement {
            val statementName = "statement-name"
            val statementFile = "statement-file"
            val multipartForm = extractMultiPartForm(request, statementName, statementFile)
            val fields = multipartForm.fields
            val files = multipartForm.files

            val year = fields[yearName]?.firstOrNull()
            val monthString = fields[monthName]?.firstOrNull()
            val user = fields[userName]?.firstOrNull()
            val statement = fields[statementName]?.firstOrNull()
            val formFile = files[statementFile]?.firstOrNull()

            if (year != null && monthString != null && user != null && statement != null && formFile != null) {
                val month = Month.valueOf(monthString.toUpperCase())
                val statementMetadata = StatementMetadata(Year.parse(year), month, user, statement)
                val file = writeFileToFileSystem(statementMetadata, formFile, normalisedStatements)

                return FormForNormalisedStatement(statementMetadata, file)
            } else {
                throw IllegalStateException(
                        """Form fields cannot be null, but were:
                            |year: $year
                            |month: $monthString
                            |user: $user
                            |statement: $statement
                            |formFile: $formFile
                        """.trimMargin()
                )
            }
        }

        fun fromUrlEncoded(request: Request): FormForNormalisedStatement {
            val statementFilePathKey = "statement-file-path"
            val form = extractUrlEncodedForm(request, statementFilePathKey)

            val year = form.fields[yearName]?.firstOrNull()
            val month = form.fields[monthName]?.firstOrNull()
            val user = form.fields[userName]?.firstOrNull()
            val statementName = form.fields[statement]?.firstOrNull()
            val statementFilePath = form.fields[statementFilePathKey]?.firstOrNull()

            if (year != null && month != null && user != null && statementName != null && statementFilePath != null) {
                val statementMetadata = StatementMetadata(Year.of(year.toInt()), Month.valueOf(month.toUpperCase()), user, statementName)
                val file = File(statementFilePath)

                return FormForNormalisedStatement(statementMetadata, file)
            } else {
                throw IllegalStateException(
                        """Form fields cannot be null, but were:
                            |year: $year
                            |month: $month
                            |user: $user
                            |statement: $statement
                            |statementFilePath: $statementFilePath
                        """.trimMargin()
                )
            }
        }

        private fun writeFileToFileSystem(statementMetadata: StatementMetadata, formFile: FormFile, normalisedStatements: Path): File {
            val (year, month, user, statement) = statementMetadata
            val fileBytes = formFile.content.use { inputStream ->
                inputStream.readBytes()
            }
            val file = File("$normalisedStatements/$year-${format(month)}_${user.capitalize()}_$statement.csv")
            file.writeBytes(fileBytes)
            return file
        }

        private fun extractUrlEncodedForm(request: Request, statementFilePathKey: String): WebForm {
            val yearLens = FormField.required(yearName)
            val monthLens = FormField.required(monthName)
            val userLens = FormField.required(userName)
            val statementNameLens = FormField.required(statement)
            val statementPathLens = FormField.required(statementFilePathKey)
            val webForm = Body.webForm(
                    Validator.Feedback,
                    yearLens,
                    monthLens,
                    userLens,
                    statementNameLens,
                    statementPathLens
            )
            return webForm.toLens().extract(request)
        }

        private fun extractMultiPartForm(request: Request, statementName: String, statementFile: String): MultipartForm {
            val yearLens = MultipartFormField.required(yearName)
            val monthLens = MultipartFormField.required(monthName)
            val userLens = MultipartFormField.required(userName)
            val statementNameLens = MultipartFormField.required(statementName)
            val statementFileLens = MultipartFormFile.required(statementFile)
            val multipartFormBody = Body.multipartForm(
                    Validator.Feedback,
                    yearLens,
                    monthLens,
                    userLens,
                    statementNameLens,
                    statementFileLens
            ).toLens()

            return multipartFormBody.extract(request)
        }

        private fun format(month: Month) = month.value.toString().padStart(2, '0')
    }
}

data class StatementMetadata(val year: Year, val month: Month, val user: String, val statement: String) {
    companion object {
        const val yearName = "year"
        const val monthName = "month"
        const val userName = "user"
        const val statement = "statement-name"
    }
}
data class FormattedBankStatement(val year: String, val month: String, val username: String, val bankName: String, val filePath: String, val formattedDecisions: List<FormattedDecision>)
data class FormattedLine(val date: String, val merchant: String, val amount: String)
data class FormattedDecision(val line: FormattedLine, val category: Category?, val subCategory: SubCategory?, val categoriesWithSelection: CategoriesWithSelection)
data class StatementData(val year: Year, val month: Month, val username: String, val statement: String)
data class CategoryMapping(val purchase: String, val mainCategory: String, val subCategory: String)

data class PleaseReviewYourCategorisations(val bankStatement: FormattedBankStatement) : ViewModel
