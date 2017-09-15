package uk.co.endofhome.skrooge

import org.http4k.core.Body
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer

fun main(args: Array<String>) {
    val port = if (args.isNotEmpty()) args[0].toInt() else 5000
    val app = App().routes()
    app.asServer(Jetty(port)).startAndBlock()
}

class App {
    fun routes() = routes("/statements" bind POST to { request -> Statements().uploadStatements(request.body) } )
}

class Statements {
    fun uploadStatements(body: Body) = Response(OK)
}
