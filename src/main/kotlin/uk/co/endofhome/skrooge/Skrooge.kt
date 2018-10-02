package uk.co.endofhome.skrooge

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.Uri
import org.http4k.core.query
import org.http4k.core.with
import org.http4k.filter.DebuggingFilters
import org.http4k.format.Gson
import org.http4k.lens.BiDiBodyLens
import org.http4k.lens.FormField
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
import org.http4k.template.ViewModel
import org.http4k.template.view
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale.UK

fun main(args: Array<String>) {
    val port = if (args.isNotEmpty()) args[0].toInt() else 5000
    val app = Skrooge().routes()
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
            "/" bind GET to { _ -> index() },
            "/statements" bind POST to { request -> Statements(categories).uploadStatements(request, renderer) },
            "/statements-js-hack" bind POST to { request -> Statements(categories).uploadStatementsJsHack(request.body, renderer, decisionReaderWriter) },
            "/unknown-merchant" bind GET to { request -> UnknownMerchantHandler(renderer, categories.all()).handle(request) },
            "category-mapping" bind POST to { request -> CategoryMappings(categoryMappings, mappingWriter).addCategoryMapping(request) },
            "reports/categorisations" bind POST to { request -> ReportCategorisations(decisionReaderWriter, categories.all()).confirm(request) },
            "annual-report/json" bind GET to { request -> annualReporter(request) },
            "monthly-report/json" bind GET to { request -> monthlyReporter(request) },
            "web" bind GET to { request -> Charts(request, renderer) }
    )

    private fun index(): Response {
        val years = listOf(DisplayYear.of("2017"), DisplayYear.of("2018"), DisplayYear.of("2019"))
        val months = Month.values().map { DisplayMonth.of(it) }
        val users = listOf(System.getenv("PARTICIPANT_TWO"), System.getenv("PARTICIPANT_ONE"), System.getenv("PARTICIPANT_THREE"))
        val statementTypes = listOf(
            StatementType(System.getenv("BANK_FIVE"), System.getenv("BANK_FIVE_DISPLAY_NAME")),
            StatementType(System.getenv("BANK_SIX"), System.getenv("BANK_SIX_DISPLAY_NAME")),
            StatementType(System.getenv("BANK_ONE"), System.getenv("BANK_ONE_DISPLAY_NAME")),
            StatementType(System.getenv("BANK_THREE"), System.getenv("BANK_THREE_DISPLAY_NAME")),
            StatementType(System.getenv("BANK_FOUR"), System.getenv("BANK_FOUR_DISPLAY_NAME")),
            StatementType(System.getenv("BANK_TWO"), System.getenv("BANK_TWO_DISPLAY_NAME")),
            StatementType(System.getenv("BANK_SEVEN"), System.getenv("BANK_SEVEN_DISPLAY_NAME")),
            StatementType(System.getenv("BANK_EIGHT"), System.getenv("BANK_EIGHT_DISPLAY_NAME"))
        )
        val index = Index(years, months, users, statementTypes)
        val view = Body.view(renderer, ContentType.TEXT_HTML)
        return Response(Status.OK).with(view of index)
    }

    data class StatementType(val id: String, val displayName: String)
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
data class Category(val title: String, val subcategories: List<SubCategory>)
data class CategoryWithSelection(val title: String, val subCategories: List<SubCategoryWithSelection>)
data class CategoriesWithSelection(val categories: List<CategoryWithSelection>)
data class SubCategory(val name: String)
data class SubCategoryWithSelection(val subCategory: SubCategory, val selector: String)
data class Decision(val line: Line, val category: Category?, val subCategory: SubCategory?)

data class DisplayYear(val name: String, val selector: String) {
    companion object {
        fun of(year: String): DisplayYear = DisplayYear(year, selectedString(year))

        private fun selectedString(year: String): String {
            val selectedYear = LocalDate.now().year.toString()
            return when (year) {
                selectedYear -> " selected"
                else        -> ""
            }
        }
    }
}

data class DisplayMonth(val name: String, val selector: String) {
    companion object {
        fun of(month: Month): DisplayMonth =
                DisplayMonth(month.getDisplayName(TextStyle.FULL, UK), selectedString(month))

        private fun selectedString(month: Month): String {
            val selectedMonth = LocalDate.now().month
            return when (month) {
                selectedMonth -> " selected"
                else          -> ""
            }
        }
    }
}

data class Index(val years: List<DisplayYear>, val months: List<DisplayMonth>, val users: List<String>, val statementTypes: List<Skrooge.StatementType>) : ViewModel
