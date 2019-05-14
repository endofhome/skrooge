package uk.co.endofhome.skrooge.statements

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.query
import org.http4k.core.with
import org.http4k.lens.BiDiLens
import org.http4k.lens.FormField
import org.http4k.lens.MultipartForm
import org.http4k.lens.MultipartFormField
import org.http4k.lens.Query
import org.http4k.lens.WebForm
import org.http4k.template.ViewModel
import org.http4k.template.view
import uk.co.endofhome.skrooge.Skrooge.Companion.renderer
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.unknownMerchant
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.categories.CategoriesWithSelection
import uk.co.endofhome.skrooge.categories.CategoryMappingHandler.Companion.remainingMerchantKey
import uk.co.endofhome.skrooge.decisions.DecisionState.Decision
import uk.co.endofhome.skrooge.decisions.DecisionState.DecisionRequired
import uk.co.endofhome.skrooge.decisions.Line
import uk.co.endofhome.skrooge.decisions.SubCategory
import uk.co.endofhome.skrooge.statements.FileMetadata.statementFilePathKey
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.FieldNames.MONTH
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.FieldNames.STATEMENT
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.FieldNames.USER
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.FieldNames.YEAR
import uk.co.endofhome.skrooge.unknownmerchant.UnknownMerchantHandler.Companion.currentMerchantKey
import java.io.File
import java.nio.file.Path
import java.time.Month
import java.time.Year
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class StatementsHandler(val categories: Categories, private val normalisedStatements: Path) {

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
        val decisionStates = StatementDecider(categories.categoryMappings).process(statementFile.readLines())
        val unknownMerchants: Set<String> = decisionStates.filterIsInstance<DecisionRequired>()
                                                     .map { it.line.merchant }
                                                     .toSet()
        return when {
            unknownMerchants.isEmpty() -> pleaseReviewYourCategorisations(statementMetadata, statementFile, decisionStates.filterIsInstance<Decision>())
            else                       -> redirectToUnknownMerchant(statementMetadata, statementFile, unknownMerchants)
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
                            decision.subCategory,
                            categories.withSelection(decision.subCategory)
                    )
                }
        )

        val view = Body.view(renderer, ContentType.TEXT_HTML)
        val reviewCategorisationsViewModel = PleaseReviewYourCategorisations(formattedBankStatement)
        return Response(Status.OK).with(view of reviewCategorisationsViewModel)
    }

    private fun redirectToUnknownMerchant(statementMetadata: StatementMetadata, statementFile: File, unknownMerchants: Set<String>): Response {
        val (year, month, user, statement) = statementMetadata
        val (currentMerchant, remainingMerchants) = unknownMerchants.partition { it == unknownMerchants.first() }
        val baseUri = Uri.of(unknownMerchant)
                .query(currentMerchantKey, currentMerchant.single())
                .query(YEAR.key, year.toString())
                .query(MONTH.key, month.getDisplayName(TextStyle.FULL, Locale.UK))
                .query(USER.key, user)
                .query(STATEMENT.key, statement)
                .query(statementFilePathKey, statementFile.path)
        val uri = remainingMerchants.fold(baseUri) { acc, merchant -> acc.query(remainingMerchantKey, merchant) }
        return Response(Status.SEE_OTHER).header("Location", uri.toString())
    }
}

object LineFormatter {
    fun format(line: Line) = FormattedLine(
        line.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
        line.merchant,
        line.amount.toPlainString()
    )
}

data class StatementMetadata(val year: Year, val month: Month, val user: String, val statement: String) {
    companion object {
        enum class FieldNames(val key: String) {
            YEAR("year"),
            MONTH("month"),
            USER("user"),
            STATEMENT("statement-name")
        }

        fun from(webForm: WebForm): StatementMetadata {
            val (year, month, user, statement) = webFormFields().map { it.extract(webForm) }

            return StatementMetadata(
                year = Year.parse(year),
                month = Month.valueOf(month.toUpperCase()),
                user = user,
                statement = statement
            )
        }

        fun webFormFields(): List<BiDiLens<WebForm, String>> =
            FieldNames.values().map { FormField.required(it.key) }

        fun multipartFormFields(): List<BiDiLens<MultipartForm, String>> =
            FieldNames.values().map { MultipartFormField.required(it.key) }

        fun queryParameters(): List<BiDiLens<Request, String>> =
            FieldNames.values().map { Query.required(it.key) }
    }
}

object FileMetadata {
    const val statementFile = "statement-file"
    const val statementFilePathKey = "statement-file-path"
}

data class FormattedBankStatement(val year: String, val month: String, val user: String, val statement: String, val filePath: String, val formattedDecisions: List<FormattedDecision>)
data class FormattedLine(val date: String, val merchant: String, val amount: String)
data class FormattedDecision(val line: FormattedLine, val subCategory: SubCategory, val categoriesWithSelection: CategoriesWithSelection)
data class CategoryMapping(val merchant: String, val mainCategory: String, val subCategory: String)

data class PleaseReviewYourCategorisations(val bankStatement: FormattedBankStatement) : ViewModel
