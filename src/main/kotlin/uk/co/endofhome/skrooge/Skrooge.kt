package uk.co.endofhome.skrooge

import org.http4k.asString
import org.http4k.core.Body
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import java.io.File
import java.time.*

fun main(args: Array<String>) {
    val port = if (args.isNotEmpty()) args[0].toInt() else 5000
    val app = Skrooge().routes()
    app.asServer(Jetty(port)).startAndBlock()
}

class Skrooge {
    fun routes() = routes("/statements" bind POST to { request -> Statements().uploadStatements(request.body) } )
}

class Statements {
    private val parser = PretendFormParser()

    fun uploadStatements(body: Body): Response {
      return if (body.payload.asString().isEmpty()) {
          Response(NOT_FOUND)
      } else {
          try {
              val statementData: StatementData = parser.parse(body)
              statementData.files.forEach {
                  val decisions = StatementDecider().process(it.readLines())
                  DecisionWriter().write(statementData, decisions)
              }
          } catch (e: Exception) {
              return Response(BAD_REQUEST)
          }
          Response(OK)
      }
    }
}

class DecisionWriter {
    val decisionFilePath = "output/decisions"
    fun write(statementData: StatementData, decisions: List<String>) {
        val year = statementData.year.toString()
        val month = statementData.month.value
        val username = statementData.username
        val institution = statementData.files[0].toString().split("/").last()
        File("$decisionFilePath/$year-$month-$username-decisions-$institution").printWriter().use { out ->
            decisions.forEach {
                out.print(it)
            }
        }
    }

    fun cleanDecisions() = {
        //TODO
    }
}

class StatementDecider {
    val mappingLines = File("category-mappings/category-mappings.csv").readLines()
    val mappings = mappingLines.map {
        val mappingStrings = it.split(",")
        Mapping(mappingStrings[0], mappingStrings[1], mappingStrings[2])
    }

    fun process(statementData: List<String>): List<String> = statementData.map { decide(it) }

    private fun decide(lineString: String): String {
        val lineEntries = lineString.split(",")
        val dateValues = lineEntries[0].split("-").map { it.toInt() }
        val line = Line(LocalDate.of(dateValues[0], dateValues[1], dateValues[2]), lineEntries[1], lineEntries[2].toDouble())

        val match = mappings.find { it.purchase.contains(line.purchase) }
        return lineString + ",${match?.mainCatgeory},${match?.subCategory}"
    }
}

class PretendFormParser {
    fun parse(body: Body): StatementData {
        val params = body.payload.asString().split(",")
        val year = Year.parse(params[0])
        val month = Month.valueOf(params[1].toUpperCase())
        val username = params[2]
        val files: List<File> = listOf(params[3]).map { File(it) }
        return StatementData(year, month, username, files)
    }
}

data class StatementData(val year: Year, val month: Month, val username: String, val files: List<File>)
data class Mapping(val purchase: String, val mainCatgeory: String, val subCategory: String)
data class Line(val date: LocalDate, val purchase: String, val amount: Double)