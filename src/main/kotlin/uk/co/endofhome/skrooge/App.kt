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
import java.time.Month
import java.time.Year

fun main(args: Array<String>) {
    val port = if (args.isNotEmpty()) args[0].toInt() else 5000
    val app = App().routes()
    app.asServer(Jetty(port)).startAndBlock()
}

class App {
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
                  val fileData = it.readLines()
                  println(fileData)
              }
          } catch (e: Exception) {
              return Response(BAD_REQUEST)
          }
          Response(OK)
      }
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
