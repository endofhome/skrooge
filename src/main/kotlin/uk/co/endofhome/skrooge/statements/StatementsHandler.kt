package uk.co.endofhome.skrooge.statements

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.query
import org.http4k.core.with
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel
import org.http4k.template.view
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.unknownMerchant
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.categories.CategoriesWithSelection
import uk.co.endofhome.skrooge.categories.CategoryMappingHandler.Companion.remainingMerchantName
import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.decisions.Decision
import uk.co.endofhome.skrooge.decisions.Line
import uk.co.endofhome.skrooge.decisions.SubCategory
import uk.co.endofhome.skrooge.statements.FileMetadata.statementFilePathKey
import uk.co.endofhome.skrooge.statements.FileMetadata.statementName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.monthName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.userName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.yearName
import uk.co.endofhome.skrooge.unknownmerchant.UnknownMerchantHandler.Companion.currentMerchantName
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

    private fun redirectToUnknownMerchant(statementMetadata: StatementMetadata, statementFile: File, unknownMerchants: Set<String>): Response {
        val (year, month, user, statement) = statementMetadata
        val (currentMerchant, remainingMerchants) = unknownMerchants.partition { it == unknownMerchants.first() }
        val baseUri = Uri.of(unknownMerchant)
                .query(currentMerchantName, currentMerchant.single())
                .query(yearName, year.toString())
                .query(monthName, month.getDisplayName(TextStyle.FULL, Locale.UK))
                .query(userName, user)
                .query(statementName, statement)
                .query(statementFilePathKey, statementFile.path)
        val uri = when {
            remainingMerchants.isNotEmpty() -> baseUri.query(remainingMerchantName, remainingMerchants.joinToString(","))
            else                            -> baseUri
        }
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

data class StatementMetadata(val year: Year, val month: Month, val user: String, val statement: String) {
    companion object {
        const val yearName = "year"
        const val monthName = "month"
        const val userName = "user"
        const val statement = "statement-name"
    }
}

object FileMetadata {
    const val statementName = "statement-name"
    const val statementFile = "statement-file"
    const val statementFilePathKey = "statement-file-path"
}

data class FormattedBankStatement(val year: String, val month: String, val user: String, val bankName: String, val filePath: String, val formattedDecisions: List<FormattedDecision>)
data class FormattedLine(val date: String, val merchant: String, val amount: String)
data class FormattedDecision(val line: FormattedLine, val category: Category?, val subCategory: SubCategory?, val categoriesWithSelection: CategoriesWithSelection)
data class CategoryMapping(val purchase: String, val mainCategory: String, val subCategory: String)

data class PleaseReviewYourCategorisations(val bankStatement: FormattedBankStatement) : ViewModel
