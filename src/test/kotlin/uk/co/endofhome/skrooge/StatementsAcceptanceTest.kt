package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasHeader
import org.http4k.hamkrest.hasStatus
import org.http4k.routing.RoutingHttpHandler
import org.junit.Test
import java.io.File

class StatementsAcceptanceTest {
    private val categoryHelpers = CategoryHelpers("src/test/resources/test-schema.json")
    private val categoryMappings = mutableListOf<String>()
    private val mappingWriter = StubbedMappingWriter()
    private val decisionReaderWriter = StubbedDecisionReaderWriter()
    private val skrooge = Skrooge(categoryHelpers, categoryMappings, mappingWriter, decisionReaderWriter).routes()
    private val helpers = TestHelpers(skrooge)

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
        skrooge(request) shouldMatch hasStatus(OK)
    }

    @Test
    fun `POST to statements with multiple dummy files returns HTTP OK`() {
        val request = Request(POST, "/statements").body("2017;September;Tom;[src/test/resources/2017-01_Someone_empty-file.csv,src/test/resources/2017-01_Someone_empty-file.csv]")
        skrooge(request) shouldMatch hasStatus(OK)
    }

    @Test
    fun `POST with empty csv produces empty output file`() {
        val request = Request(POST, "/statements").body("2017;May;Test;src/test/resources/[2017-01_Someone_empty-file.csv]")
        skrooge(request)

        val decisionFile = File("output/decisions/2017-5-Test-decisions-empty-file.csv")
        val fileContents = decisionFile.readLines()
        assertThat(fileContents.size, equalTo(0))
    }

    @Test
    fun `POST with one entry produces output file with one entry when recognised merchant`() {
        val request = Request(POST, "/statements").body("2017;May;Test;[src/test/resources/2017-02_Someone_one-known-merchant.csv]")
        skrooge(request)

        val decisionFile = File("output/decisions/2017-5-Test-decisions-one-known-merchant.csv")
        val fileContents = decisionFile.readLines()
        assertThat(fileContents.size, equalTo(1))
        assertThat(fileContents[0], equalTo("2017-09-17,Pizza Union,5.5,Eats and drinks,Meals at work"))
    }

    @Test
    fun `POST with one entry returns a monthly report when recognised merchant`() {
        val categoryMappings = mutableListOf("Pizza Union,Eats and drinks,Meals at work")
        val localSkrooge = Skrooge(categoryHelpers, categoryMappings, mappingWriter, decisionReaderWriter).routes()
        val requestWithPizzaUnion = Request(POST, "/statements").body("2017;February;Test;[src/test/resources/2017-02_Someone_one-known-merchant.csv]")
        val response = localSkrooge(requestWithPizzaUnion)

        response shouldMatch hasStatus(OK)
        response shouldMatch hasBody(containsSubstring("<h1>Please review your monthly categorisations for one-known-merchant</h1>"))
        response shouldMatch hasBody(containsSubstring("<h3>17/09/2017, Pizza Union: £5.50"))
        response shouldMatch hasBody(containsSubstring("<option value=\"[17/09/2017,Pizza Union,5.50,Eats and drinks,Meals at work]\" selected>Meals at work</option>"))
        response shouldMatch hasBody(containsSubstring("<option value=\"[17/09/2017,Pizza Union,5.50,Eats and drinks,Food]\">Food</option>"))
    }

    @Test
    fun `POST with one entry returns HTTP See Other when unrecognised merchant`() {
        val requestWithMcDonalds = Request(POST, "/statements").body("2017;April;Test;[src/test/resources/2017-04_Someone_unknown-merchant.csv]")
        val response = skrooge(requestWithMcDonalds)
        response shouldMatch hasStatus(SEE_OTHER)
        response shouldMatch hasHeader("Location", "/unknown-merchant?currentMerchant=McDonalds&outstandingMerchants=&originalRequestBody=2017%3BApril%3BTest%3B%5Bsrc%2Ftest%2Fresources%2F2017-04_Someone_unknown-merchant.csv%5D")

        val followedResponse = helpers.follow302RedirectResponse(response)
        followedResponse shouldMatch hasBody(containsSubstring("You need to categorise some merchants."))
    }

    @Test
    fun `redirect when unrecognised merchant shows correct unrecognised merchant`() {
        val requestWithMcDonalds = Request(POST, "/statements").body("2017;April;Test;[src/test/resources/2017-04_Someone_unknown-merchant.csv]")
        val followedResponse = helpers.follow302RedirectResponse(skrooge(requestWithMcDonalds))

        followedResponse shouldMatch hasBody(containsSubstring("<h3>McDonalds</h3>"))
    }

    @Test
    fun `redirect when multiple unrecognised merchants shows correct unrecognised merchants`() {
        val requestWithTwoRecordShops = Request(POST, "/statements").body("2017;March;Tom;[src/test/resources/2017-03_Someone_two-unknown-merchants.csv]")
        val followedResponse = helpers.follow302RedirectResponse(skrooge(requestWithTwoRecordShops))

        followedResponse shouldMatch hasBody(containsSubstring("<h3>Rounder Records</h3>"))
        followedResponse shouldMatch hasBody(containsSubstring("<input type=\"hidden\" name=\"remaining-vendors\" value=\"Edgeworld Records\">"))
    }

    @Test
    fun `redirect when multiple unrecognised merchants and multiple input files`() {
        val requestWithTwoFilesOfUnknownMerchants = Request(POST, "/statements").body("2017;March;Test;[src/test/resources/2017-03_Someone_two-unknown-merchants.csv,src/test/resources/2017-04_Someone_unknown-merchant.csv]")
        val followedResponse = helpers.follow302RedirectResponse(skrooge(requestWithTwoFilesOfUnknownMerchants))

        followedResponse shouldMatch hasBody(containsSubstring("<h3>Rounder Records</h3>"))
        followedResponse shouldMatch hasBody(containsSubstring("<input type=\"hidden\" name=\"remaining-vendors\" value=\"Edgeworld Records,McDonalds\">"))
    }
}

class TestHelpers(val skrooge: RoutingHttpHandler) {
    fun follow302RedirectResponse(response: Response): Response {
        val location = response.headerValues("location").first()
        return skrooge(Request(Method.GET, location!!))
    }
}