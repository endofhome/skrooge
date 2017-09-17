package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.should.shouldMatch
import org.http4k.core.*
import org.http4k.core.Method.POST
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.format.Jackson
import org.http4k.hamkrest.*
import org.junit.Before
import org.junit.Test
import java.io.File

class SkroogeTest {
    val skrooge = Skrooge().routes()

    @Before
    fun `setup`() {
        DecisionWriter().cleanDecisions()
    }

    @Test
    fun `POST to statements endpoint with empty body returns HTTP Not Found`() {
        val request = Request(POST, "/statements").body(Body.EMPTY)
        skrooge(request) shouldMatch hasStatus(NOT_FOUND)
    }

    @Test
    fun `POST to statements with incorrect dummy form data returns HTTP Bad Request`() {
        // Can't receive form data, so will read files from FS instead. Will use a string to represent form.
        val request = Request(POST, "/statements").body("nonsense")
        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }

    @Test
    fun `POST to statements with a dummy string instead of form data returns HTTP OK`() {
        val request = Request(POST, "/statements").body("2017,September,Tom,src/test/resources/empty-file.csv")
        skrooge(request) shouldMatch hasStatus(OK)
    }

    @Test
    fun `POST with empty csv produces empty output file`() {
        val request = Request(POST, "/statements").body("2017,September,Tom,src/test/resources/empty-file.csv")
        skrooge(request)

        val decisionFile = File("output/decisions/2017-9-Tom-decisions-empty-file.csv")
        val fileContents = decisionFile.readLines()
        assertThat(fileContents.size, equalTo(0))
    }

    @Test
    fun `POST with one entry produces output file with one entry when recognised transaction`() {
        val request = Request(POST, "/statements").body("2017,September,Tom,src/test/resources/one-known-transaction.csv")
        skrooge(request)

        val decisionFile = File("output/decisions/2017-9-Tom-decisions-one-known-transaction.csv")
        val fileContents = decisionFile.readLines()
        assertThat(fileContents.size, equalTo(1))
        assertThat(fileContents[0], equalTo("2017-09-17,Pizza Union,5.50,Eats and drinks,Meals at work"))
    }

    @Test
    fun `POST with one entry returns HTTP See Other when unrecognised transaction`() {
        val requestWithMcDonalds = Request(POST, "/statements").body("2017,September,Tom,src/test/resources/unknown-transaction.csv")
        val response = skrooge(requestWithMcDonalds)
        val unrecognisedTransactions = Jackson.asJsonObject(UnknownTransaction(listOf(ProcessedLine(true, "McDonalds", ""))))
        response shouldMatch hasStatus(SEE_OTHER)
        response shouldMatch hasHeader("Location", "/unknown-transaction")
        response shouldMatch hasBody(unrecognisedTransactions.toString())

        val followedResponse = followRedirectResponse(response)
        followedResponse shouldMatch hasBody(containsSubstring("You need to categorise some transactions."))
    }

    @Test
    fun `redirect when unrecognised transaction shows correct unrecognised transaction`() {
        val requestWithMcDonalds = Request(POST, "/statements").body("2017,September,Tom,src/test/resources/unknown-transaction.csv")
        val followedResponse = followRedirectResponse(skrooge(requestWithMcDonalds))

        followedResponse shouldMatch hasBody(containsSubstring("McDonalds"))
    }

    private fun followRedirectResponse(response: Response): Response {
        val location = response.headerValues("location").first()
        return skrooge(Request(Method.GET, location!!).body(response.body))
    }

}