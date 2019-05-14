package uk.co.endofhome.skrooge

import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.filter.DebuggingFilters
import org.http4k.routing.ResourceLoader
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.template.HandlebarsTemplates
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.categoryMapping
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.index
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.monthlyBarChartReport
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.monthlyJsonReport
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.publicResources
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.statementDecisions
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.statementsWithFileContents
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.statementsWithFilePath
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.unknownMerchant
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
import uk.co.endofhome.skrooge.reports.BarChartHandler
import uk.co.endofhome.skrooge.reports.MonthlyReportHandler
import uk.co.endofhome.skrooge.statements.StatementsHandler
import uk.co.endofhome.skrooge.unknownmerchant.UnknownMerchantHandler
import java.nio.file.Path
import java.nio.file.Paths

fun main(args: Array<String>) {
    val port = if (args.isNotEmpty()) args[0].toInt() else 5000
    val app = Skrooge().routes
                       .withFilter(DebuggingFilters.PrintRequestAndResponse())
    app.asServer(Jetty(port)).start()
    println("Skrooge has started on http://localhost:$port")
}

class Skrooge(private val categories: Categories = Categories(),
              private val mappingWriter: MappingWriter = FileSystemMappingWriter(),
              private val decisionReaderWriter: DecisionReaderWriter = FileSystemDecisionReaderReaderWriter(categories),
              budgetDirectory: Path = Paths.get("input/budgets/"),
              normalisedStatementsDirectory: Path = Paths.get("input/normalised/")) {

    companion object {
        val renderer = HandlebarsTemplates().HotReload("src/main/resources")
    }

    private val categoryReporter = CategoryReporter(categories.all(), AnnualBudgets.from(budgetDirectory))
    private val statementsHandler = StatementsHandler(categories, normalisedStatementsDirectory)

    val routes: RoutingHttpHandler
        get() = routes(
            publicResources bind static(ResourceLoader.Directory("public")),

            index bind GET to { IndexHandler() },
            monthlyBarChartReport bind GET to { request -> BarChartHandler(request) },

            statementsWithFileContents bind POST to { request -> statementsHandler.withFileContents(request) },
            statementsWithFilePath bind POST to { request -> statementsHandler.withFilePath(request) },
            unknownMerchant bind GET to { request -> UnknownMerchantHandler(categories.all())(request) },
            categoryMapping bind POST to { request -> CategoryMappingHandler(categories.categoryMappings, mappingWriter)(request) },
            statementDecisions bind POST to { request -> DecisionsHandler(decisionReaderWriter, categories)(request) },
            monthlyJsonReport bind GET to { request -> MonthlyReportHandler(decisionReaderWriter, categoryReporter)(request) }
        )

    object RouteDefinitions {
        const val publicResources = "/public"
        const val index = "/"
        const val statementsWithFileContents = "/statements"
        const val statementsWithFilePath = "/statements-with-path"
        const val unknownMerchant = "/unknown-merchant"
        const val categoryMapping = "/category-mapping"
        const val statementDecisions = "statement-decisions"
        private const val monthlyReport = "monthly-report"
        const val monthlyJsonReport = "$monthlyReport/json"
        const val monthlyBarChartReport = "$monthlyReport/bar-chart"
    }
}
