package uk.co.endofhome.skrooge

import org.http4k.core.Body
import org.http4k.core.ContentType
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
import org.http4k.template.view
import java.io.File
import java.time.Month
import java.time.Year
import java.time.YearMonth

class Statements(private val categories: Categories) {

    fun uploadStatements(request: Request, renderer: TemplateRenderer, decisionReaderWriter: DecisionReaderWriter): Response {
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

        val formParts = listOf(year, month, user, statement, formFile)

        return when {
            formParts.contains(null) -> Response(Status.BAD_REQUEST)
            else -> {
                val fileBytes = formFile!!.content.readBytes()
                val statementFile = File("input/normalised/${year!!}-${format(month)}_${user!!.capitalize()}_$statement.csv")
                statementFile.writeBytes(fileBytes)

                val statementData = StatementData(Year.parse(year), Month.valueOf(month!!.toUpperCase()), user, statement!!)
                val decisions = StatementDecider(categories.categoryMappings).process(statementFile.readLines())
                decisionReaderWriter.write(statementData, decisions)
                Response(Status.OK)
            }
        }
    }

    private fun format(month: String?) = Month.valueOf(month!!.toUpperCase()).value.toString().padStart(2, '0')

    private fun extractFormParts(request: Request, yearName: String, monthName: String, userName: String, statementName: String): MultipartForm {
        val yearLens = MultipartFormField.required(yearName)
        val monthLens = MultipartFormField.required(monthName)
        val userLens = MultipartFormField.required(userName)
        val statementNameLens = MultipartFormField.required(statementName)
        val statementFileLens = MultipartFormFile.required(statementName)
        val multipartFormBody = Body.multipartForm(Validator.Feedback, yearLens, monthLens, userLens, statementNameLens, statementFileLens).toLens()

        return multipartFormBody.extract(request)
    }

    fun uploadStatementsJsHack(body: Body, renderer: TemplateRenderer, decisionReaderWriter: DecisionReaderWriter): Response {
        val parser = PretendFormParser()

        try {
            val statementData: JsHackStatementData = parser.parse(body)
            val processedLines: List<BankStatement> = statementData.files.map {
                val filenameParts = it.name.split("_")
                val splitUsername = filenameParts[1]
                val splitFilename = filenameParts[2]
                val splitYear = Integer.valueOf(filenameParts[0].split("-")[0])
                val splitMonth = Integer.valueOf(filenameParts[0].split("-")[1])
                BankStatement(
                        YearMonth.of(splitYear, splitMonth),
                        splitUsername,
                        splitFilename.substringBefore(".csv"),
                        StatementDecider(categories.categoryMappings).process(it.readLines())
                )
            }
            val statementsWithUnknownMerchants = processedLines.filter { it.decisions.map { it.category }.contains(null) }

            return when (statementsWithUnknownMerchants.isNotEmpty()) {
                true -> {
                    val unknownMerchants: Set<String> = statementsWithUnknownMerchants
                            .flatMap { it.decisions }
                            .filter { it.category == null }
                            .map { it.line.merchant }
                            .toSet()
                    val currentMerchant = unknownMerchants.first()
                    val outstandingMerchants = unknownMerchants.filterIndexed { index, _ -> index != 0 }
                    val uri = Uri.of("/unknown-merchant")
                            .query("currentMerchant", currentMerchant)
                            .query("outstandingMerchants", outstandingMerchants.joinToString(","))
                            .query("originalRequestBody", body.toString())
                    Response(Status.SEE_OTHER).header("Location", uri.toString())
                }
                false -> {
                    processedLines.forEach { decisionReaderWriter.write(statementData.statementData, it.decisions) }
                    val bankStatements = BankStatements(processedLines.map { bankStatement ->
                        FormattedBankStatement(
                                bankStatement.yearMonth.year.toString(),
                                bankStatement.yearMonth.month.name.toLowerCase().capitalize(),
                                bankStatement.username,
                                bankStatement.bankName,
                                bankStatement.decisions.sortedBy { it.line.date }.map { decision ->
                                    FormattedDecision(
                                            LineFormatter.format(decision.line),
                                            decision.category,
                                            decision.subCategory,
                                            categories.withSelection(decision.subCategory)
                                    )
                                })
                    })
                    val bankReport = BankReport(
                            bankStatements.statements.first(),
                            bankStatements.statements.filterIndexed { index, _ -> index != 0 }
                    )
                    return BankReports(renderer).report(bankReport)
                }
            }
        } catch (e: Exception) {
            return Response(Status.BAD_REQUEST)
        }
    }

    fun index(renderer: TemplateRenderer): Response {
        val main = Main("unncessary")
        val view = Body.view(renderer, ContentType.TEXT_HTML)
        return Response(Status.OK).with(view of main)
    }
}