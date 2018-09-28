package uk.co.endofhome.skrooge

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.Uri
import org.http4k.core.query
import org.http4k.core.with
import org.http4k.filter.DebuggingFilters
import org.http4k.format.Gson
import org.http4k.lens.BiDiBodyLens
import org.http4k.lens.BiDiLens
import org.http4k.lens.FormField
import org.http4k.lens.Query
import org.http4k.lens.Validator
import org.http4k.lens.WebForm
import org.http4k.lens.webForm
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel
import org.http4k.template.view
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.Month

fun main(args: Array<String>) {
    val port = if (args.isNotEmpty()) args[0].toInt() else 5000
    val app = Skrooge()
            .routes()
            .withFilter(DebuggingFilters.PrintRequestAndResponse())
    app.asServer(Jetty(port)).start()
    println("Skrooge has started on http://localhost:$port")
}

class Skrooge(private val categories: Categories = Categories(),
              private val mappingWriter: MappingWriter = FileSystemMappingWriter(),
              private val decisionReaderWriter: DecisionReaderWriter = FileSystemDecisionReaderReaderWriter(categories),
              budgetDirectory: Path = Paths.get("input/budgets/")) {

    private val categoryMappings = categories.categoryMappings
    private val gson = Gson
    private val renderer = HandlebarsTemplates().HotReload("src/main/resources")
    private val publicDirectory = static(ResourceLoader.Directory("public"))
    private val annualBudgets = AnnualBudgets.from(budgetDirectory)
    private val categoryReporter = CategoryReporter(categories.all(), annualBudgets)
    private val annualReporter = AnnualReporter(gson, decisionReaderWriter, categoryReporter)
    private val monthlyReporter = MonthlyReporter(gson, decisionReaderWriter, categoryReporter)

    fun routes() = routes(
            "/public" bind publicDirectory,
            "/" bind GET to { _ -> Statements(categories).index(renderer) },
            "/statements" bind POST to { request -> Statements(categories).uploadStatements(request, renderer, decisionReaderWriter) },
            "/statements-js-hack" bind POST to { request -> Statements(categories).uploadStatementsJsHack(request.body, renderer, decisionReaderWriter) },
            "/unknown-merchant" bind GET to { request -> UnknownMerchantHandler(renderer, categories.all()).handle(request) },
            "category-mapping" bind POST to { request -> CategoryMappings(categoryMappings, mappingWriter).addCategoryMapping(request) },
            "reports/categorisations" bind POST to { request -> ReportCategorisations(decisionReaderWriter, categories.all()).confirm(request) },
            "annual-report/json" bind GET to { request -> annualReporter(request) },
            "monthly-report/json" bind GET to { request -> monthlyReporter(request) },
            "web" bind GET to { request -> Charts(request, renderer) }
    )
}

class ReportCategorisations(private val decisionReaderWriter: DecisionReaderWriter, val categories: List<Category>) {
    fun confirm(request: Request): Response {
        val webForm = Body.webForm(Validator.Strict)
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
                Decision(Line(date, merchant, amount), Category(category, categories.find { it.title == category }!!.subcategories), SubCategory(subCategory))
            }
        }

        val statementDataString: List<String> = form.fields["statement-data"]!![0].split(";")
        val hackStatementData = JsHackStatementData.fromFormParts(statementDataString)
        decisionReaderWriter.write(hackStatementData.statementData, decisions)
        return Response(Status.CREATED)
    }
}

class UnknownMerchantHandler(private val renderer: TemplateRenderer, private val categories: List<Category>) {
    fun handle(request: Request): Response {
        val currentMerchantLens: BiDiLens<Request, String> = Query.required("currentMerchant")
        val outstandingMerchantsLens: BiDiLens<Request, List<String>> = Query.multi.required("outstandingMerchants")
        val originalRequestBodyLens: BiDiLens<Request, String> = Query.required("originalRequestBody")
        val view = Body.view(renderer, ContentType.TEXT_HTML)
        val currentMerchant = Merchant(currentMerchantLens(request), categories)
        val outstandingMerchants: List<String> = outstandingMerchantsLens(request).flatMap { it.split(",") }
        val originalRequestBody = originalRequestBodyLens(request)
        val unknownMerchants = UnknownMerchants(currentMerchant, outstandingMerchants.joinToString(","), originalRequestBody)

        return Response(OK).with(view of unknownMerchants)
    }
}

class StatementDecider(categoryMappings: List<String>) {
    private val mappings = categoryMappings.map {
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

class CategoryMappings(private val categoryMappings: MutableList<String>, private val mappingWriter: MappingWriter) {
    fun addCategoryMapping(request: Request): Response {
        val newMappingLens = FormField.required("new-mapping")
        val remainingVendorsLens = FormField.required("remaining-vendors")
        val originalRequestBodyLens = FormField.required("originalRequestBody")
        val webForm: BiDiBodyLens<WebForm> = Body.webForm(Validator.Strict, newMappingLens, remainingVendorsLens).toLens()
        val newMapping = newMappingLens.extract(webForm(request)).split(",")
        val remainingVendors: List<String> = remainingVendorsLens.extract(webForm(request)).split(",").filter { it.isNotBlank() }
        val originalRequestBody = Body(originalRequestBodyLens.extract(webForm(request)))

        return newMapping.size.let {
            when {
                it < 3 -> Response(BAD_REQUEST)
                else -> {
                    val newMappingString = newMapping.joinToString(",")
                    mappingWriter.write(newMappingString)
                    categoryMappings.add(newMappingString)
                    when (remainingVendors.isEmpty()) {
                        true -> Response(Status.TEMPORARY_REDIRECT)
                                .header("Location", "/statements")
                                .header("Method", POST.name)
                                .body(originalRequestBody)
                        false -> {
                            val nextVendor = remainingVendors.first()
                            val carriedForwardVendors = remainingVendors.filterIndexed { index, _ -> index != 0 }
                            val uri = Uri.of("/unknown-merchant")
                                    .query("currentMerchant", nextVendor)
                                    .query("outstandingMerchants", carriedForwardVendors.joinToString(","))
                                    .query("originalRequestBody", originalRequestBody.toString())
                            Response(SEE_OTHER).header("Location", uri.toString())
                        }
                    }
                }
            }
        }
    }
}


data class CategoryMapping(val purchase: String, val mainCatgeory: String, val subCategory: String)
data class Line(val date: LocalDate, val merchant: String, val amount: Double)
data class UnknownMerchants(val currentMerchant: Merchant, val outstandingMerchants: String, val originalRequestBody: String) : ViewModel
data class Merchant(val name: String, val categories: List<Category>?)
data class Category(val title: String, val subcategories: List<SubCategory>)
data class CategoryWithSelection(val title: String, val subCategories: List<SubCategoryWithSelection>)
data class CategoriesWithSelection(val categories: List<CategoryWithSelection>)
data class SubCategory(val name: String)
data class SubCategoryWithSelection(val subCategory: SubCategory, val selector: String)
data class Decision(val line: Line, val category: Category?, val subCategory: SubCategory?)

data class Main(val unnecessary: String) : ViewModel
