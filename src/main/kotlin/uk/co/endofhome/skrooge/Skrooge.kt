package uk.co.endofhome.skrooge

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.http4k.asString
import org.http4k.core.*
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.filter.DebuggingFilters
import org.http4k.format.Gson
import org.http4k.lens.*
import org.http4k.routing.*
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.template.*
import uk.co.endofhome.skrooge.CategoryHelpers.categories
import uk.co.endofhome.skrooge.CategoryHelpers.subcategoriesFor
import java.io.File
import java.math.BigDecimal
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
              val mappingWriter: MappingWriter = FileSystemMappingWriter(),
              val decisionWriter: DecisionWriter = FileSystemDecisionWriter()) {

    private val gson = Gson
    private val renderer = HandlebarsTemplates().HotReload("src/main/resources")
    private val publicDirectory = static(ResourceLoader.Directory("public"))

    fun routes() = routes(
            "/public" bind publicDirectory,
            "/" bind GET to { _ -> Statements(categoryMappings).index(renderer) },
            "/statements" bind POST to { request -> Statements(categoryMappings).uploadStatements(request.body, renderer, decisionWriter) },
            "/unknown-merchant" bind GET to { request -> UnknownMerchantHandler(renderer).handle(request) },
            "category-mapping" bind POST to { request -> CategoryMappings(mappingWriter).addCategoryMapping(request) },
            "reports/categorisations" bind POST to { request -> ReportCategorisations(decisionWriter).confirm(request) },
            "monthly-report/json" bind GET to { request -> MonthlyReport(gson, decisionWriter).handle(request) }
    )
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
                Decision(Line(date, merchant, amount), Category(category, categories().find { it.title == category }!!.subcategories), SubCategory(subCategory))
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
                val splitUsername = filenameParts[1]
                val splitFilename = filenameParts[2]
                val splitYear = Integer.valueOf(filenameParts[0].split("-")[0])
                val splitMonth = Integer.valueOf(filenameParts[0].split("-")[1])
                BankStatement(
                        YearMonth.of(splitYear, splitMonth),
                        splitUsername,
                        splitFilename.substringBefore(".csv"), StatementDecider(categoryMappings).process(it.readLines())
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
                    Response(SEE_OTHER).header("Location", uri.toString())
                }
                false -> {
                    processedLines.forEach { decisionWriter.write(statementData, it.decisions) }
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
                                    CategoryHelpers.categoriesWithSelection(decision.subCategory)
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
                line.merchant,
                line.amount.roundTo2DecimalPlaces()
        )

    private fun Double.roundTo2DecimalPlaces() =
            BigDecimal(this).setScale(2, BigDecimal.ROUND_HALF_UP).toString()
}

class UnknownMerchantHandler(private val renderer: TemplateRenderer) {
    fun handle(request: Request): Response {
        val vendorLens: BiDiLens<Request, String> = Query.required("currentMerchant")
        val merchantsLens: BiDiLens<Request, List<String>> = Query.multi.required("outstandingMerchants")
        val view = Body.view(renderer, ContentType.TEXT_HTML)
        val currentMerchant = Merchant(vendorLens(request), categories())
        val vendors: List<String> = merchantsLens(request).flatMap { it.split(",") }
        val unknownMerchants = UnknownMerchants(currentMerchant, vendors.joinToString(","))

        return Response(OK).with(view of unknownMerchants)
    }
}

object CategoryHelpers {
    fun categories(schemaFilePath: String = "category-schema/category-schema.json"): List<Category> {
        val schemaFile = File(schemaFilePath)
        val contents: String = schemaFile.readText()
        val mapper = ObjectMapper().registerModule(KotlinModule())
        val categories: Categories = mapper.readValue(contents)
        return categories.toList()
    }

    fun categoriesWithSelection(subCategory: SubCategory?): CategoriesWithSelection {
        val titles = categories().map { it.title }
        val subCategories: List<List<SubCategoryWithSelection>> = categories().map { cat ->
            cat.subcategories.map { subCat ->
                SubCategoryWithSelection(subCat, selectedString(subCat, subCategory))
            }
        }
        val catsWithSelection = titles.zip(subCategories).map { CategoryWithSelection(it.first, it.second) }
        return CategoriesWithSelection(catsWithSelection)
    }

    fun subcategoriesFor(category: String): List<SubCategory> {
        return CategoryHelpers.categories().filter { it.title == category }.flatMap { it.subcategories }
    }

    private fun selectedString(subCategory: SubCategory, anotherSubCategory: SubCategory?): String {
        return when (subCategory == anotherSubCategory) {
            true -> " selected"
            false -> ""
        }
    }

    data class Categories(val categories: List<Category>) {
        fun toList() = categories
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
                out.print("${it.line.date},${it.line.merchant},${it.line.amount},${it.category?.title},${it.subCategory?.name}\n")
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

                val category = CategoryHelpers.categories().find { it.title == split[3] }!!
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

        val match = mappings.find { it.purchase.contains(line.merchant) }
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
                            val uri = Uri.of("/unknown-merchant")
                                    .query("currentMerchant", nextVendor)
                                    .query("outstandingMerchants", carriedForwardVendors.joinToString(","))
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

class FileSystemMappingWriter : MappingWriter {
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

class StubbedMappingWriter : MappingWriter {
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
data class Line(val date: LocalDate, val merchant: String, val amount: Double)
data class FormattedLine(val date: String, val merchant: String, val amount: String)
data class UnknownMerchants(val currentMerchant: Merchant, val outstandingMerchants: String) : ViewModel
data class Merchant(val name: String, val categories: List<Category>?)
data class Category(val title: String, val subcategories: List<SubCategory>)
data class CategoryWithSelection(val title: String, val subCategories: List<SubCategoryWithSelection>)
data class CategoriesWithSelection(val categories: List<CategoryWithSelection>)
data class SubCategory(val name: String)
data class SubCategoryWithSelection(val subCategory: SubCategory, val selector: String)
data class BankStatement(val yearMonth: YearMonth, val username: String, val bankName: String, val decisions: List<Decision>)
data class FormattedBankStatement(val year: String, val month: String, val username: String, val bankName: String, val formattedDecisions: List<FormattedDecision>)
data class BankStatements(val statements: List<FormattedBankStatement>)
data class Decision(val line: Line, val category: Category?, val subCategory: SubCategory?)
data class FormattedDecision(val line: FormattedLine, val category: Category?, val subCategory: SubCategory?, val categoriesWithSelection: CategoriesWithSelection)
data class BankReport(val bankStatement: FormattedBankStatement, val outstandingStatements: List<FormattedBankStatement>) : ViewModel

data class Main(val unnecessary: String) : ViewModel