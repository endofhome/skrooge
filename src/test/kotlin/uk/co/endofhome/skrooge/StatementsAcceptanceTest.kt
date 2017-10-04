package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import org.http4k.core.*
import org.http4k.core.Method.POST
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.hamkrest.*
import org.http4k.routing.RoutingHttpHandler
import org.junit.Test
import java.io.File

class StatementsAcceptanceTest {
    val skrooge = Skrooge().routes()
    val helpers = TestHelpers(skrooge)

    @Test
    fun `POST to statements endpoint with empty body returns HTTP Bad Request`() {
        val request = Request(POST, "/statements").body(Body.EMPTY)
        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }

    @Test
    fun `POST to statements with incorrect dummy form data returns HTTP Bad Request`() {
        // Can't receive form data, so will read files from FS instead. Will use a string to represent form.
        val request = Request(POST, "/statements").body("nonsense")
        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }

    @Test
    fun `POST to statements with a dummy string instead of form data returns HTTP OK`() {
        val request = Request(POST, "/statements").body("2017;September;Tom;[src/test/resources/2017-01_Someone_empty-file.csv]")
        skrooge(request) shouldMatch hasStatus(SEE_OTHER)
    }

    @Test
    fun `POST to statements with multiple dummy files returns HTTP SEE OTHER`() {
        val request = Request(POST, "/statements").body("2017;September;Tom;[src/test/resources/2017-01_Someone_empty-file.csv,src/test/resources/2017-01_Someone_empty-file.csv]")
        skrooge(request) shouldMatch hasStatus(SEE_OTHER)
    }

    @Test
    fun `POST with empty csv produces empty output file`() {
        val request = Request(POST, "/statements").body("2017;September;Tom;src/test/resources/[2017-01_Someone_empty-file.csv]")
        skrooge(request)

        val decisionFile = File("output/decisions/2017-9-Tom-decisions-empty-file.csv")
        val fileContents = decisionFile.readLines()
        assertThat(fileContents.size, equalTo(0))
    }

    @Test
    fun `POST with one entry produces output file with one entry when recognised transaction`() {
        val request = Request(POST, "/statements").body("2017;September;Tom;[src/test/resources/2017-02_Someone_one-known-transaction.csv]")
        skrooge(request)

        val decisionFile = File("output/decisions/2017-9-Tom-decisions-one-known-transaction.csv")
        val fileContents = decisionFile.readLines()
        assertThat(fileContents.size, equalTo(1))
        assertThat(fileContents[0], equalTo("2017-09-17,Pizza Union,5.50,Eats and drinks,Meals at work"))
    }

    @Test
    fun `POST with one entry redirects to monthly report when recognised transaction`() {
        val requestWithPizzaUnion = Request(POST, "/statements").body("2017;September;Tom;[src/test/resources/2017-02_Someone_one-known-transaction.csv]")
        val response = skrooge(requestWithPizzaUnion)
        response shouldMatch hasStatus(SEE_OTHER)

        val followedResponse = helpers.followRedirectResponse(response)
        followedResponse shouldMatch hasStatus(OK)
        followedResponse shouldMatch hasBody(containsSubstring("<h1>Please review your monthly categorisations for one-known-transaction</h1>"))
        followedResponse shouldMatch hasBody(containsSubstring("<h3>2017-09-17, Pizza Union: £5.5</h3>"))
    }

    @Test
    fun `POST with one entry returns HTTP See Other when unrecognised transaction`() {
        val requestWithMcDonalds = Request(POST, "/statements").body("2017;September;Tom;[src/test/resources/2017-04_Someone_unknown-transaction.csv]")
        val response = skrooge(requestWithMcDonalds)
        response shouldMatch hasStatus(SEE_OTHER)
        response shouldMatch hasHeader("Location", "/unknown-transaction?currentTransaction=McDonalds&outstandingTransactions=")

        val followedResponse = helpers.followRedirectResponse(response)
        followedResponse shouldMatch hasBody(containsSubstring("You need to categorise some transactions."))
    }

    @Test
    fun `redirect when unrecognised transaction shows correct unrecognised transaction`() {
        val requestWithMcDonalds = Request(POST, "/statements").body("2017;September;Tom;[src/test/resources/2017-04_Someone_unknown-transaction.csv]")
        val followedResponse = helpers.followRedirectResponse(skrooge(requestWithMcDonalds))

        followedResponse shouldMatch hasBody(containsSubstring("<h3>McDonalds</h3>"))
    }

    @Test
    fun `redirect when multiple unrecognised transactions shows correct unrecognised transactions`() {
        val requestWithTwoRecordShops = Request(POST, "/statements").body("2017;September;Tom;[src/test/resources/2017-03_Someone_two-unknown-transactions.csv]")
        val followedResponse = helpers.followRedirectResponse(skrooge(requestWithTwoRecordShops))

        followedResponse shouldMatch hasBody(containsSubstring("<h3>Rounder Records</h3>"))
        followedResponse shouldMatch hasBody(containsSubstring("<input type=\"hidden\" name=\"remaining-vendors\" value=\"Edgeworld Records\">"))
    }

    @Test
    fun `redirect when multiple unrecognised transactions and multiple input files`() {
        val requestWithTwoFilesOfUnknownTransactions = Request(POST, "/statements").body("2017;September;Tom;[src/test/resources/2017-03_Someone_two-unknown-transactions.csv,src/test/resources/2017-04_Someone_unknown-transaction.csv]")
        val followedResponse = helpers.followRedirectResponse(skrooge(requestWithTwoFilesOfUnknownTransactions))

        followedResponse shouldMatch hasBody(containsSubstring("<h3>Rounder Records</h3>"))
        followedResponse shouldMatch hasBody(containsSubstring("<input type=\"hidden\" name=\"remaining-vendors\" value=\"Edgeworld Records,McDonalds\">"))
    }
}

class TestHelpers(val skrooge: RoutingHttpHandler) {
    fun followRedirectResponse(response: Response): Response {
        val location = response.headerValues("location").first()
        return skrooge(Request(Method.GET, location!!).body(response.body))
    }
}