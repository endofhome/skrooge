package uk.co.endofhome.skrooge

import org.http4k.asString
import org.http4k.core.*
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.filter.DebuggingFilters
import org.http4k.lens.*
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.template.*
import uk.co.endofhome.skrooge.Categories.categories
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter

fun main(args: Array<String>) {
    val port = if (args.isNotEmpty()) args[0].toInt() else 5000
    val app = Skrooge()
                .routes()
                .withFilter(DebuggingFilters.PrintRequestAndResponse())
    app.asServer(Jetty(port)).startAndBlock()
}

class Skrooge(val categoryMappings: List<String> = File("category-mappings/category-mappings.csv").readLines(),
              val mappingWriter: MappingWriter = FileSystemMappingWriter()) {
    private val renderer = HandlebarsTemplates().HotReload("src/main/resources")

    fun routes() = routes(
            "/statements" bind POST to { request -> Statements(categoryMappings).uploadStatements(request.body, renderer) },
            "/unknown-transaction" bind GET to { request -> UnknownTransactionHandler(renderer).handle(request) },
            "category-mapping" bind POST to { request -> CategoryMappings(mappingWriter).addCategoryMapping(request) }
    )
}

class Statements(val categoryMappings: List<String>) {
    private val parser = PretendFormParser()

    fun uploadStatements(body: Body, renderer: TemplateRenderer): Response {
        try {
            val statementData: StatementData = parser.parse(body)
            val processedLines: List<BankStatement> = statementData.files.map {
                BankStatement(
                        it.name.split("_")[2].substringBefore(".csv"),
                        StatementDecider(categoryMappings).process(it.readLines())
                )
            }
            val statementsWithUnknownTransactions = processedLines.filter { it.decisions.map { it.category }.contains(null) }

            return when (statementsWithUnknownTransactions.isNotEmpty()) {
                true -> {
                    val unknownTransactions = statementsWithUnknownTransactions.flatMap { it.decisions }.filter { it.category == null }
                    val currentTransaction: Decision = unknownTransactions.first()
                    val outstandingTransactions = unknownTransactions.filterIndexed { index, _ -> index != 0 }
                    val uri = Uri.of("/unknown-transaction")
                            .query("currentTransaction", currentTransaction.line.purchase)
                            .query("outstandingTransactions", outstandingTransactions.map { it.line.purchase }.joinToString(","))
                    Response(SEE_OTHER).header("Location", uri.toString())
                }
                false -> {
                    val decisions = processedLines.map { it.toString() }
                    DecisionWriter().write(statementData, decisions)
                    val bankStatements = BankStatements(processedLines.map { bankStatement ->
                        FormattedBankStatement(bankStatement.bankName, bankStatement.decisions.map { decision ->
                            FormattedDecision(LineFormatter.format(decision.line), decision.category, decision.dataItem)
                        })
                    })
                    val bankReport = BankReport(
                            bankStatements.statements.first().bankName,
                            bankStatements.statements.first().formattedDecisions,
                            bankStatements.statements.filterIndexed { index, _ -> index != 0 }
                    )
                    return BankReports(renderer).report(bankReport)
                }
            }
        } catch (e: Exception) {
            return Response(BAD_REQUEST)
        }
    }
}

object LineFormatter {
    fun format(line: Line) = FormattedLine(
                line.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                line.purchase,
                line.amount
        )
}

class UnknownTransactionHandler(private val renderer: TemplateRenderer) {
    fun handle(request: Request): Response {
        val vendorLens: BiDiLens<Request, String> = Query.required("currentTransaction")
        val transactionsLens: BiDiLens<Request, List<String>> = Query.multi.required("outstandingTransactions")
        val view = Body.view(renderer, ContentType.TEXT_HTML)
        val currentTransaction = Transaction(vendorLens(request), categories())
        val vendors: List<String> = transactionsLens(request).flatMap { it.split(",") }
        val unknownTransactions = UnknownTransactions(currentTransaction, vendors.joinToString(","))

        return Response(OK).with(view of unknownTransactions)
    }
}

object Categories {
    fun categories() = listOf(
            Category("In your home", listOf(DataItem("Mortgage"), DataItem("Building insurance"))),
            Category("Insurance", listOf(DataItem("Travel insurance"), DataItem("Income protection"))),
            Category("Eats and drinks", listOf(DataItem("Food"), DataItem("Meals at work"))),
            Category("Fun", listOf(DataItem("Tom fun budget"), DataItem("Someone else's fun budget")))
    )
}

class DecisionWriter {
    val decisionFilePath = "output/decisions"
    fun write(statementData: StatementData, decisions: List<String>) {
        val year = statementData.year.toString()
        val month = statementData.month.value
        val username = statementData.username
        val vendor = statementData.files[0].toString().split("/").last()
        File("$decisionFilePath/$year-$month-$username-decisions-$vendor").printWriter().use { out ->
            decisions.forEach {
                out.print(it)
            }
        }
    }
}

class StatementDecider(categoryMappings: List<String>) {
    val mappings = categoryMappings.map {
        val mappingStrings = it.split(",")
        CategoryMapping(mappingStrings[0], mappingStrings[1], mappingStrings[2])
    }

    fun process(statementData: List<String>) = statementData.map { decide(it) }

    private fun decide(lineString: String): Decision {
        val lineEntries = lineString.split(",")
        val dateValues = lineEntries[0].split("-").map { it.toInt() }
        val line = Line(LocalDate.of(dateValues[0], dateValues[1], dateValues[2]), lineEntries[1], lineEntries[2].toDouble())

        val match = mappings.find { it.purchase.contains(line.purchase) }
        return when (match) {
            null -> { Decision(line, null, null) }
            else -> { Decision(line, Category(match.mainCatgeory, emptyList()), DataItem(match.subCategory)) }
        }
    }
}

class CategoryMappings(private val mappingWriter: MappingWriter) {
    fun addCategoryMapping(request: Request): Response {
        val newMappingLens = FormField.required("new-mapping")
        val remainingVendorsLens = FormField.required("remaining-vendors")
        val webForm: BiDiBodyLens<WebForm> = Body.webForm(FormValidator.Strict, newMappingLens, remainingVendorsLens).toLens()
        val newMapping = newMappingLens.extract(webForm(request)).split(",")
        val remainingVendors: List<String> = remainingVendorsLens.extract(webForm(request)).split(",").filter { it.isNotBlank() }

        return newMapping.size.let {
            when {
                it < 3 -> Response(BAD_REQUEST)
                else -> {
                    mappingWriter.write(newMapping.joinToString(","))
                    when (remainingVendors.isEmpty()) {
                        true -> Response(OK).body("All new categories mapped. Please POST your data once again.")
                        false -> {
                            val nextVendor = remainingVendors.first()
                            val carriedForwardVendors = remainingVendors.filterIndexed { index, _ -> index != 0 }
                            val uri = Uri.of("/unknown-transaction")
                                    .query("currentTransaction", nextVendor)
                                    .query("outstandingTransactions", carriedForwardVendors.joinToString(","))
                            Response(SEE_OTHER).header("Location", uri.toString())
                        }
                    }
                }
            }
        }
    }
}

class BankReports(private val renderer: TemplateRenderer) {
    fun report(bankReport: BankReport): Response {
        val view = Body.view(renderer, ContentType.TEXT_HTML)
        return Response(OK).with(view of bankReport)
    }
}

class PretendFormParser {
    fun parse(body: Body): StatementData {
        // delimiting with semi-colons for now as I want a list in the last 'field'
        val params = body.payload.asString().split(";")
        val year = Year.parse(params[0])
        val month = Month.valueOf(params[1].toUpperCase())
        val username = params[2]
        val fileStrings: List<String> = params[3].substring(1, params[3].lastIndex).split(",")
        val files: List<File> = fileStrings.map { File(it) }
        return StatementData(year, month, username, files)
    }
}

interface MappingWriter {
    fun write(line: String): Boolean
    fun read(): List<String>
}

class FileSystemMappingWriter : MappingWriter{
    val categoryMappingsFileOutputPath = "category-mappings/category-mappings.csv"
    override fun write(line: String): Boolean {
        try {
            File(categoryMappingsFileOutputPath).appendText(line + "\n")
            return true
        } catch (e: Exception) {
            return false
        }
    }
    override fun read(): List<String> = File(categoryMappingsFileOutputPath).readLines()
}

class MockMappingWriter : MappingWriter {
    private val file: MutableList<String> = mutableListOf()

    override fun write(line: String) = file.add(line)
    override fun read() = file
}

data class StatementData(val year: Year, val month: Month, val username: String, val files: List<File>)
data class CategoryMapping(val purchase: String, val mainCatgeory: String, val subCategory: String)
data class Line(val date: LocalDate, val purchase: String, val amount: Double)
data class FormattedLine(val date: String, val purchase: String, val amount: Double)
data class UnknownTransactions(val currentTransaction: Transaction, val outstandingTransactions: String) : ViewModel
data class Transaction (val vendorName: String, val categories: List<Category>?)
data class Category(val title: String, val data: List<DataItem>)
data class DataItem(val name: String)
data class BankStatement(val bankName: String, val decisions: List<Decision>)
data class FormattedBankStatement(val bankName: String, val formattedDecisions: List<FormattedDecision>)
data class BankStatements(val statements: List<FormattedBankStatement>)
data class Decision(val line: Line, val category: Category?, val dataItem: DataItem?)
data class FormattedDecision(val line: FormattedLine, val category: Category?, val dataItem: DataItem?)
data class BankReport(val currentBank: String, val decisions: List<FormattedDecision>, val outstandingStatements: List<FormattedBankStatement>) : ViewModel