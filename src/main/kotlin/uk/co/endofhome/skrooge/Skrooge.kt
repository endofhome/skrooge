package uk.co.endofhome.skrooge

import org.http4k.asString
import org.http4k.core.*
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.filter.DebuggingFilters
import org.http4k.format.Gson
import org.http4k.lens.*
import org.http4k.routing.*
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.template.*
import uk.co.endofhome.skrooge.Categories.categories
import uk.co.endofhome.skrooge.Categories.subcategoriesFor
import java.io.File
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

fun main(args: Array<String>) {
    val port = if (args.isNotEmpty()) args[0].toInt() else 5000
    val app = Skrooge()
                .routes()
                .withFilter(DebuggingFilters.PrintRequestAndResponse())
    app.asServer(Jetty(port)).startAndBlock()
}

class Skrooge(val categoryMappings: List<String> = File("category-mappings/category-mappings.csv").readLines(),
              val mappingWriter: MappingWriter = FileSystemMappingWriter(),
              val decisionWriter: DecisionWriter = FileSystemDecisionWriter()) {

    private val gson = Gson
    private val renderer = HandlebarsTemplates().HotReload("src/main/resources")
    private val publicDirectory = static(ResourceLoader.Directory("public"))

    fun routes() = routes(
            "/public" bind publicDirectory,
            "/" bind GET to { _ -> Statements(categoryMappings).index(renderer) },
            "/statements" bind POST to { request -> Statements(categoryMappings).uploadStatements(request.body, renderer, decisionWriter) },
            "/unknown-transaction" bind GET to { request -> UnknownTransactionHandler(renderer).handle(request) },
            "category-mapping" bind POST to { request -> CategoryMappings(mappingWriter).addCategoryMapping(request) },
            "reports/categorisations" bind POST to { request -> ReportCategorisations(decisionWriter).confirm(request) },
            "generate/json" bind GET to { request -> GenerateJson(gson, decisionWriter).handle(request) }
    )
}

class GenerateJson(val gson: Gson, val decisionWriter: DecisionWriter) {
    fun handle(request: Request): Response {
        val year = request.query("year")!!.toInt()
        val month = Month.of(request.query("month")!!.toInt())
        val decisions = decisionWriter.read(year, month)

        return decisions.let { when {
                it.isNotEmpty() -> {
                    val catReportDataItems: List<CategoryReportDataItem> = decisions.map {
                        CategoryReportDataItem(it.subCategory!!.name, it.line.amount)
                    }.groupBy { it.name }.map {
                        it.value.reduce { acc, categoryReportDataItem -> CategoryReportDataItem(it.key, acc.actual + categoryReportDataItem.actual) }
                    }

                    val catReport = CategoryReport(decisions.first().category!!.title, catReportDataItems)
                    val jsonReport = JsonReport(year, month.getDisplayName(TextStyle.FULL, Locale.UK), month.value, listOf(catReport))
                    val jsonReportJson = gson.asJsonObject(jsonReport)

                    Response(CREATED).body(jsonReportJson.toString())
                }
                else -> Response(BAD_REQUEST)
            }
        }
    }
}

class ReportCategorisations(val decisionWriter: DecisionWriter) {
    fun confirm(request: Request): Response {
        val webForm = Body.webForm(FormValidator.Strict)
        val form = webForm.toLens().extract(request)
        val decisionsStrings: List<String>? = form.fields["decisions"]
        val decisionsSplit: List<List<String>>? = decisionsStrings?.map { it.substring(1, it.lastIndex).split(", ") }
        val decisions: List<Decision> = decisionsSplit!!.flatMap { decisionLine ->
            decisionLine.map { it.split(",") }.map {
                val dateParts = it[0].split("/")
                val day = Integer.valueOf(dateParts[0])
                val month = Month.of(Integer.valueOf(dateParts[1]))
                val year = Integer.valueOf(dateParts[2])
                val date = LocalDate.of(year, month, day)
                val merchant = it[1]
                val amount = it[2].toDouble()
                val category = it[3]
                val subCategory = it[4]
                Decision(Line(date, merchant, amount), Category(category, categories().find { it.title == category }!!.subCategories), SubCategory(subCategory))
            }
        }

        val statementDataString: List<String> = form.fields["statement-data"]!![0].split(";")
        val statementData = StatementData.fromFormParts(statementDataString)
        decisionWriter.write(statementData, decisions)
        return Response(Status.CREATED)
    }
}

class Statements(val categoryMappings: List<String>) {
    private val parser = PretendFormParser()

    fun uploadStatements(body: Body, renderer: TemplateRenderer, decisionWriter: DecisionWriter): Response {
        try {
            val statementData: StatementData = parser.parse(body)
            val processedLines: List<BankStatement> = statementData.files.map {
                val filenameParts = it.name.split("_")
                val splitName = filenameParts[2]
                val splitYear = Integer.valueOf(filenameParts[0].split("-")[0])
                val splitMonth = Integer.valueOf(filenameParts[0].split("-")[1])
                BankStatement(
                        splitName.substringBefore(".csv"),
                        YearMonth.of(splitYear, splitMonth),
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
                    processedLines.forEach { decisionWriter.write(statementData, it.decisions) }
                    val bankStatements = BankStatements(processedLines.map { bankStatement ->
                        FormattedBankStatement(bankStatement.bankName, bankStatement.decisions.map { decision ->
                            FormattedDecision(
                                    LineFormatter.format(decision.line),
                                    decision.category,
                                    decision.subCategory,
                                    Categories.categoriesWithSelection(decision.subCategory)
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
            return Response(BAD_REQUEST)
        }
    }

    fun index(renderer: TemplateRenderer): Response {
        val main = Main("unncessary")
        val view = Body.view(renderer, ContentType.TEXT_HTML)
        return Response(OK).with(view of main)
    }
}

object LineFormatter {
    fun format(line: Line) = FormattedLine(
                line.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                line.purchase,
                line.amount.roundTo2DecimalPlaces()
        )

    private fun Double.roundTo2DecimalPlaces() =
            BigDecimal(this).setScale(2, BigDecimal.ROUND_HALF_UP).toString()
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
            Category("In your home", listOf(SubCategory("Mortgage"), SubCategory("Building insurance"))),
            Category("Insurance", listOf(SubCategory("Travel insurance"), SubCategory("Income protection"))),
            Category("Eats and drinks", listOf(SubCategory("Food"), SubCategory("Meals at work"))),
            Category("Fun", listOf(SubCategory("Tom fun budget"), SubCategory("Someone else's fun budget")))
    )

    fun categoriesWithSelection(subCategory: SubCategory?): CategoriesWithSelection {
        val titles = categories().map { it.title }
        val subCategories: List<List<SubCategoryWithSelection>> = categories().map { cat ->
            cat.subCategories.map { subCat ->
                SubCategoryWithSelection(subCat, selectedString(subCat, subCategory))
            }
        }
        val catsWithSelection = titles.zip(subCategories).map { CategoryWithSelection(it.first, it.second) }
        return CategoriesWithSelection(catsWithSelection)
    }

    fun subcategoriesFor(category: String): List<SubCategory> {
        return Categories.categories().filter { it.title == category }.flatMap { it.subCategories }
    }

    private fun selectedString(subCategory: SubCategory, anotherSubCategory: SubCategory?): String {
        return when (subCategory == anotherSubCategory) {
            true -> " selected"
            false -> ""
        }
    }
}

interface DecisionWriter {
    fun write(statementData: StatementData, decisions: List<Decision>)
    fun read(year: Int, month: Month): List<Decision>
}

class FileSystemDecisionWriter : DecisionWriter {
    private val decisionFilePath = "output/decisions"

    override fun write(statementData: StatementData, decisions: List<Decision>) {
        val year = statementData.year.toString()
        val month = statementData.month.value
        val username = statementData.username
        val bank = statementData.files[0].toString().split("_").last().substringBefore(".csv")
        File("$decisionFilePath/$year-$month-$username-decisions-$bank.csv").printWriter().use { out ->
            decisions.forEach {
                out.print("${it.line.date},${it.line.purchase},${it.line.amount},${it.category?.title},${it.subCategory?.name}\n")
            }
        }
    }

    override fun read(year: Int, month: Month): List<Decision> {
        val monthFiles = File(decisionFilePath).listFiles().filter { it.name.startsWith("$year-${month.value}") }
        return monthFiles.flatMap {
            it.readLines().map {
                val split = it.split(",")
                val dateValues = split[0].split("-").map { it.toInt() }
                val line = Line(LocalDate.of(dateValues[0], dateValues[1], dateValues[2]), split[1], split[2].toDouble())

                val category = Categories.categories().find { it.title == split[3] }!!
                Decision(line, category, subcategoriesFor(category.title).find { it.name == split[4] })
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
            else -> { Decision(line, Category(match.mainCatgeory, emptyList()), SubCategory(match.subCategory)) }
        }
    }
}

class StubbedDecisionWriter : DecisionWriter {
    private val file: MutableList<Decision> = mutableListOf()

    override fun write(statementData: StatementData, decisions: List<Decision>) {
        file.clear()
        decisions.forEach {
            file.add(it)
        }
    }

    override fun read(year: Int, month: Month) = file.toList()
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
        return StatementData.fromFormParts(params)
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

data class StatementData(val year: Year, val month: Month, val username: String, val files: List<File>) {
    companion object {
        fun fromFormParts(formParts: List<String>): StatementData {
            val year = Year.parse(formParts[0])
            val month = Month.valueOf(formParts[1].toUpperCase())
            val username = formParts[2]
            val fileStrings: List<String> = formParts[3].substring(1, formParts[3].lastIndex).split(",")
            val files: List<File> = fileStrings.map { File(it) }
            return StatementData(year, month, username, files)
        }
    }
}

data class CategoryMapping(val purchase: String, val mainCatgeory: String, val subCategory: String)
data class Line(val date: LocalDate, val purchase: String, val amount: Double)
data class FormattedLine(val date: String, val purchase: String, val amount: String)
data class UnknownTransactions(val currentTransaction: Transaction, val outstandingTransactions: String) : ViewModel
data class Transaction (val vendorName: String, val categories: List<Category>?)
data class Category(val title: String, val subCategories: List<SubCategory>)
data class CategoryWithSelection(val title: String, val subCategories: List<SubCategoryWithSelection>)
data class CategoriesWithSelection(val categories: List<CategoryWithSelection>)
data class SubCategory(val name: String)
data class SubCategoryWithSelection(val subCategory: SubCategory, val selector: String)
data class BankStatement(val bankName: String, val month: YearMonth, val decisions: List<Decision>)
data class FormattedBankStatement(val bankName: String, val formattedDecisions: List<FormattedDecision>)
data class BankStatements(val statements: List<FormattedBankStatement>)
data class Decision(val line: Line, val category: Category?, val subCategory: SubCategory?)
data class FormattedDecision(val line: FormattedLine, val category: Category?, val subCategory: SubCategory?, val categoriesWithSelection: CategoriesWithSelection)
data class BankReport(val bankStatement: FormattedBankStatement, val outstandingStatements: List<FormattedBankStatement>) : ViewModel

data class CategoryReportDataItem(val name: String, val actual: Double)
data class CategoryReport(val title: String, val data: List<CategoryReportDataItem>)
data class JsonReport(val year: Int, val month: String, val monthNumber: Int, val categories: List<CategoryReport>)

data class Main(val unnecessary: String) : ViewModel