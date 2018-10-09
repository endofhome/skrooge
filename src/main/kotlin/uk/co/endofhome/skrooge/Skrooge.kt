package uk.co.endofhome.skrooge

import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.filter.DebuggingFilters
import org.http4k.format.Gson
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.template.HandlebarsTemplates
import uk.co.endofhome.skrooge.RouteDefinitions.index
import uk.co.endofhome.skrooge.RouteDefinitions.statements
import uk.co.endofhome.skrooge.categories.AnnualBudgets
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.categories.CategoryMappingHandler
import uk.co.endofhome.skrooge.categories.CategoryReporter
import uk.co.endofhome.skrooge.categories.FileSystemMappingWriter
import uk.co.endofhome.skrooge.categories.MappingWriter
import uk.co.endofhome.skrooge.decisions.DecisionReaderWriter
import uk.co.endofhome.skrooge.decisions.DecisionsHandler
import uk.co.endofhome.skrooge.decisions.FileSystemDecisionReaderReaderWriter
import uk.co.endofhome.skrooge.index.IndexHandler
import uk.co.endofhome.skrooge.reports.AnnualReportHandler
import uk.co.endofhome.skrooge.reports.BarChartHandler
import uk.co.endofhome.skrooge.reports.MonthlyReportHandler
import uk.co.endofhome.skrooge.statements.StatementsHandler
import uk.co.endofhome.skrooge.unknownmerchant.UnknownMerchantHandler
import java.nio.file.Path
import java.nio.file.Paths

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

    private val renderer = HandlebarsTemplates().HotReload("src/main/resources")
    private val categoryReporter = CategoryReporter(categories.all(), AnnualBudgets.from(budgetDirectory))

    fun routes() = routes(
            "/public" bind static(ResourceLoader.Directory("public")),
            index bind GET to { IndexHandler(renderer).handle() },
            statements bind POST to { request -> StatementsHandler(categories).upload(request, renderer) },
            "/unknown-merchant" bind GET to { request -> UnknownMerchantHandler(renderer, categories.all()).handle(request) },
            "category-mapping" bind POST to { request -> CategoryMappingHandler(categories.categoryMappings, mappingWriter).addCategoryMapping(request) },
            "reports/categorisations" bind POST to { request -> DecisionsHandler(decisionReaderWriter, categories.all()).confirm(request) },
            "annual-report/json" bind GET to { request -> AnnualReportHandler(Gson, decisionReaderWriter, categoryReporter)(request) },
            "monthly-report/json" bind GET to { request -> MonthlyReportHandler(Gson, decisionReaderWriter, categoryReporter)(request) },
            "web" bind GET to { request -> BarChartHandler(request, renderer) }
    )
}

object RouteDefinitions {
    const val index = "/"
    const val statements = "/statements"
}
