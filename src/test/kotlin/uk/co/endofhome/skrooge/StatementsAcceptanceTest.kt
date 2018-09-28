package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import com.oneeyedmen.okeydoke.junit.ApprovalsRule
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.FormFile
import org.http4k.core.Method
import org.http4k.core.Method.POST
import org.http4k.core.MultipartFormBody
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasHeader
import org.http4k.hamkrest.hasStatus
import org.http4k.routing.RoutingHttpHandler
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Paths

class StatementsAcceptanceTest {

    @Rule
    @JvmField val approver: ApprovalsRule = ApprovalsRule.fileSystemRule("src/test/kotlin")

    private val categoryMappings = mutableListOf<String>()
    private val categories = Categories("src/test/resources/test-schema.json", categoryMappings)
    private val mappingWriter = StubbedMappingWriter()
    private val decisionReaderWriter = StubbedDecisionReaderWriter()
    private val testBudgetDirectory = Paths.get("src/test/resources/budgets/")
    private val skrooge = Skrooge(categories, mappingWriter, decisionReaderWriter, testBudgetDirectory).routes()

    @Test
    fun `POST to statements endpoint with empty body returns HTTP Bad Request`() {
        val body = MultipartFormBody()
        val request = Request(POST, "/statements")
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)
        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }

    @Test
    fun `JS-HACK POST to statements endpoint with empty body returns HTTP Bad Request`() {
        val request = Request(POST, "/statements-js-hack").body(Body.EMPTY)
        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }

    @Test
    fun `JS-HACK POST to statements with incorrect dummy form data returns HTTP Bad Request`() {
        // Can't receive form data, so will read files from FS instead. Will use a string to represent form.
        val request = Request(POST, "/statements-js-hack").body("nonsense")
        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }

    @Test
    fun `POST to statements with missing year in form data returns HTTP Bad Request`() {
        val bankStatement = FormFile("2017-9-Tom-empty-statement-file.csv", ContentType.OCTET_STREAM, "".byteInputStream())
        val body = MultipartFormBody().plus("month" to "September")
                                      .plus("user" to "Tom")
                                      .plus("statement" to bankStatement)

        val request = Request(POST, "/statements")
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)
        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }

    @Test
    fun `POST to statements with missing month in form data returns HTTP Bad Request`() {
        val bankStatement = FormFile("2017-9-Tom-empty-statement-file.csv", ContentType.OCTET_STREAM, "".byteInputStream())
        val body = MultipartFormBody().plus("year" to "2017")
                                      .plus("user" to "Tom")
                                      .plus("statement" to bankStatement)

        val request = Request(POST, "/statements")
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)
        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }

    @Test
    fun `POST to statements with missing user in form data returns HTTP Bad Request`() {
        val bankStatement = FormFile("2017-9-Tom-empty-statement-file.csv", ContentType.OCTET_STREAM, "".byteInputStream())
        val body = MultipartFormBody().plus("year" to "2017")
                                      .plus("month" to "September")
                                      .plus("statement" to bankStatement)

        val request = Request(POST, "/statements")
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)
        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }

    @Test
    fun `POST to statements with missing file in form data returns HTTP Bad Request`() {
        val body = MultipartFormBody().plus("year" to "2017")
                                      .plus("month" to "September")
                                      .plus("user" to "Tom")

        val request = Request(POST, "/statements")
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)
        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }

    @Test
    fun `POST to statements with correct form data returns HTTP OK`() {
        val formFile = FormFile("2017-9-Tom-EmptyStatement.csv", ContentType.OCTET_STREAM, "".byteInputStream())
        val body = MultipartFormBody().plus("year" to "2017")
                                      .plus("month" to "September")
                                      .plus("user" to "Tom")
                                      .plus("statement" to "EmptyStatement")
                                      .plus("statement" to formFile)

        val request = Request(POST, "/statements")
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)
        skrooge(request) shouldMatch hasStatus(OK)
    }

    @Test
    fun `JS-HACK POST to statements with a dummy string instead of form data returns HTTP OK`() {
        val request = Request(POST, "/statements-js-hack").body("2017;September;Tom;EmptyFile;[src/test/resources/2017-01_Someone_empty-file.csv]")
        skrooge(request) shouldMatch hasStatus(OK)
    }

    @Ignore("Not sure if I want to support this right now.")
    @Test
    fun `POST to statements with multiple dummy files returns HTTP OK`() {
        val request = Request(POST, "/statements").body("2017;September;Tom;EmptyFile;[src/test/resources/2017-01_Someone_empty-file.csv,src/test/resources/2017-01_Someone_empty-file.csv]")
        skrooge(request) shouldMatch hasStatus(OK)
    }

    @Test
    fun `JS-HACK POST to statements with multiple dummy files returns HTTP OK`() {
        val request = Request(POST, "/statements-js-hack").body("2017;September;Tom;EmptyFile;[src/test/resources/2017-01_Someone_empty-file.csv,src/test/resources/2017-01_Someone_empty-file.csv]")
        skrooge(request) shouldMatch hasStatus(OK)
    }

    @Test
    fun `POST with empty csv produces empty statement and decision files`() {
        val outputPath = Paths.get("src/test/resources/decisions")
        val decisionReaderWriter = FileSystemDecisionReaderReaderWriter(categories, outputPath)
        val localSkrooge = Skrooge(categories, mappingWriter, decisionReaderWriter, testBudgetDirectory).routes()
        val formFile = FormFile("2017-02_Test_EmptyStatement.csv", ContentType.OCTET_STREAM, "".byteInputStream())
        val body = MultipartFormBody().plus("year" to "2017")
                                      .plus("month" to "February")
                                      .plus("user" to "Test")
                                      .plus("statement" to "EmptyStatement")
                                      .plus("statement" to formFile)
        val request = Request(POST, "/statements")
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)

        localSkrooge(request)

        val statementFile = File("input/normalised/2017-02_Test_EmptyStatement.csv")
        val statementFileContents = statementFile.readLines()
        assertThat(statementFileContents.size, equalTo(0))

        val decisionFile = File("$outputPath/2017-2-Test-decisions-EmptyStatement.csv")
        val decisionFileContents = decisionFile.readLines()
        assertThat(decisionFileContents.size, equalTo(0))
    }

    @Test
    fun `JS-HACK POST with empty csv produces empty output file`() {
        val outputPath = Paths.get("src/test/resources/decisions")
        val decisionReaderWriter = FileSystemDecisionReaderReaderWriter(categories, outputPath)
        val localSkrooge = Skrooge(categories, mappingWriter, decisionReaderWriter, testBudgetDirectory).routes()
        val request = Request(POST, "/statements-js-hack").body("2017;January;Test;EmptyFile;[src/test/resources/2017-01_Someone_empty-file.csv]")

        localSkrooge(request)

        val decisionFile = File("$outputPath/2017-1-Test-decisions-empty-file.csv")
        val fileContents = decisionFile.readLines()
        assertThat(fileContents.size, equalTo(0))
    }

    @Test
    fun `POST with one entry produces output file with one entry when recognised merchant`() {
        val categoryMappings = mutableListOf("Pizza Union,Eats and drinks,Meals at work")
        val categories = Categories("src/test/resources/test-schema.json", categoryMappings)
        val outputPath = Paths.get("src/test/resources/decisions")
        val decisionReaderWriter = FileSystemDecisionReaderReaderWriter(categories, outputPath)
        val localSkrooge = Skrooge(categories, mappingWriter, decisionReaderWriter, testBudgetDirectory).routes()
        val inputStatementContent = "2017-09-17,Pizza Union,5.50\n"
        val decisionContent = "2017-09-17,Pizza Union,5.5,Eats and drinks,Meals at work\n"
        val body = MultipartFormBody().plus("year" to "2017")
                .plus("month" to "February")
                .plus("user" to "Test")
                .plus("statement" to "one-known-merchant")
                .plus("statement" to FormFile("2017-02_Test_one-known-merchant.csv", ContentType.OCTET_STREAM, inputStatementContent.byteInputStream()))
        val request = Request(POST, "/statements")
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)

        val response = localSkrooge(request)

        val statementFile = File("input/normalised/2017-02_Test_one-known-merchant.csv")
        val statementFileContents = statementFile.readLines()
        assertThat(statementFileContents.size, equalTo(1))
        assertThat(statementFileContents[0], equalTo(inputStatementContent.trim()))

        val decisionFile = File("$outputPath/2017-2-Test-decisions-one-known-merchant.csv")
        val decisionFileContents = decisionFile.readLines()
        assertThat(decisionFileContents.size, equalTo(1))
        assertThat(decisionFileContents[0], equalTo(decisionContent.trim()))

        approver.assertApproved(response.bodyString())
    }

    @Test
    fun `JS-HACK POST with one entry produces output file with one entry when recognised merchant`() {
        val categoryMappings = mutableListOf("Pizza Union,Eats and drinks,Meals at work")
        val categories = Categories("src/test/resources/test-schema.json", categoryMappings)
        val outputPath = Paths.get("src/test/resources/decisions")
        val decisionReaderWriter = FileSystemDecisionReaderReaderWriter(categories, outputPath)
        val localSkrooge = Skrooge(categories, mappingWriter, decisionReaderWriter, testBudgetDirectory).routes()
        val request = Request(POST, "/statements-js-hack").body("2017;February;Test;OneKnownMerchant;[src/test/resources/2017-02_Test_one-known-merchant.csv]")

        localSkrooge(request)

        val decisionFile = File("$outputPath/2017-2-Test-decisions-one-known-merchant.csv")
        val fileContents = decisionFile.readLines()
        assertThat(fileContents.size, equalTo(1))
        assertThat(fileContents[0], equalTo("2017-09-17,Pizza Union,5.5,Eats and drinks,Meals at work"))
    }

    @Test
    fun `JS-HACK POST with one entry returns a monthly report when recognised merchant`() {
        val categoryMappings = mutableListOf("Pizza Union,Eats and drinks,Meals at work")
        val categories = Categories("src/test/resources/test-schema.json", categoryMappings)
        val outputPath = Paths.get("src/test/resources/decisions")
        val decisionReaderWriter = FileSystemDecisionReaderReaderWriter(categories, outputPath)
        val localSkrooge = Skrooge(categories, mappingWriter, decisionReaderWriter, testBudgetDirectory).routes()
        val requestWithPizzaUnion = Request(POST, "/statements-js-hack").body("2017;February;Test;OneKnownMerchant;[src/test/resources/2017-02_Someone_one-known-merchant.csv]")
        val response = localSkrooge(requestWithPizzaUnion)

        response shouldMatch hasStatus(OK)
        response shouldMatch hasBody(containsSubstring("<h1>Please review your categorisations for one-known-merchant</h1>"))
        response shouldMatch hasBody(containsSubstring("<h3>17/09/2017, Pizza Union: £5.50"))
        response shouldMatch hasBody(containsSubstring("<option value=\"[17/09/2017,Pizza Union,5.50,Eats and drinks,Meals at work]\" selected>Meals at work</option>"))
        response shouldMatch hasBody(containsSubstring("<option value=\"[17/09/2017,Pizza Union,5.50,Eats and drinks,Food]\">Food</option>"))
    }

    @Test
    fun `POST with one entry returns HTTP See Other when unrecognised merchant`() {
        val inputStatementContent = "2017-09-17,McDonalds,0.99\n"
        val body = MultipartFormBody().plus("year" to "2017")
                                      .plus("month" to "April")
                                      .plus("user" to "Test")
                                      .plus("statement" to "OneUnknownMerchant")
                                      .plus("statement" to FormFile("2017-04_Test_one-unknown-merchant.csv", ContentType.OCTET_STREAM, inputStatementContent.byteInputStream()))
        val requestWithMcDonalds = Request(POST, "/statements")
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)

        val response = skrooge(requestWithMcDonalds)
        assertThat(response.status, equalTo(SEE_OTHER))
        assertThat(response.header("Location"), equalTo("/unknown-merchant?currentMerchant=McDonalds&outstandingMerchants=&originalRequestBody=2017%3BApril%3BTest%3BOneUnknownMerchant"))

        val followedResponse = with(RedirectHelper(skrooge)) { response.followRedirect() }
        approver.assertApproved(followedResponse.bodyString())
    }

    @Test
    fun `JS-HACK POST with one entry returns HTTP See Other when unrecognised merchant`() {
        val requestWithMcDonalds = Request(POST, "/statements-js-hack").body("2017;April;Test;UnknownMerchant;[src/test/resources/2017-04_Someone_unknown-merchant.csv]")
        val response = skrooge(requestWithMcDonalds)
        response shouldMatch hasStatus(SEE_OTHER)
        response shouldMatch hasHeader("Location", "/unknown-merchant?currentMerchant=McDonalds&outstandingMerchants=&originalRequestBody=2017%3BApril%3BTest%3BUnknownMerchant%3B%5Bsrc%2Ftest%2Fresources%2F2017-04_Someone_unknown-merchant.csv%5D")

        val followedResponse = with(RedirectHelper(skrooge)) { response.followRedirect() }
        followedResponse shouldMatch hasBody(containsSubstring("You need to categorise some merchants."))
    }

    @Test
    fun `JS-HACK redirect when unrecognised merchant shows correct unrecognised merchant`() {
        val requestWithMcDonalds = Request(POST, "/statements-js-hack").body("2017;April;Test;UnknownMerchant;[src/test/resources/2017-04_Someone_unknown-merchant.csv]")
        val followedResponse = with(RedirectHelper(skrooge)) { requestWithMcDonalds.handleAndfollowRedirect() }

        followedResponse shouldMatch hasBody(containsSubstring("<h3>McDonalds</h3>"))
    }

    @Test
    fun `redirect when multiple unrecognised merchants shows correct unrecognised merchants`() {
        val inputStatementContent = """
            2017-09-17,Rounder Records,14.99
            2017-09-17,Edgeworld Records,15.99
        """.trimIndent()
        val formFile = FormFile(
                "2017-03_Test_two-unknown-merchants.csv",
                ContentType.OCTET_STREAM,
                inputStatementContent.byteInputStream()
        )
        val body = MultipartFormBody().plus("year" to "2017")
                .plus("month" to "March")
                .plus("user" to "Test")
                .plus("statement" to "TwoUnknownMerchants")
                .plus("statement" to formFile)
        val requestWithTwoRecordShops = Request(POST, "/statements")
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)

        val followedResponse = with(RedirectHelper(skrooge)) { requestWithTwoRecordShops.handleAndfollowRedirect() }
        approver.assertApproved(followedResponse.bodyString())
    }

    @Test
    fun `JS-HACK redirect when multiple unrecognised merchants shows correct unrecognised merchants`() {
        val requestWithTwoRecordShops = Request(POST, "/statements-js-hack").body("2017;March;Tom;TwoUnknownMerchants;[src/test/resources/2017-03_Someone_two-unknown-merchants.csv]")
        val followedResponse = with(RedirectHelper(skrooge)) { requestWithTwoRecordShops.handleAndfollowRedirect() }

        followedResponse shouldMatch hasBody(containsSubstring("<h3>Rounder Records</h3>"))
        followedResponse shouldMatch hasBody(containsSubstring("<input type=\"hidden\" name=\"remaining-vendors\" value=\"Edgeworld Records\">"))
    }

    @Test
    fun `JS-HACK redirect when multiple unrecognised merchants and multiple input files`() {
        val requestWithTwoFilesOfUnknownMerchants = Request(POST, "/statements-js-hack").body("2017;March;Test;TwoUnknownMerchants;[src/test/resources/2017-03_Someone_two-unknown-merchants.csv,src/test/resources/2017-04_Someone_unknown-merchant.csv]")
        val followedResponse = with(RedirectHelper(skrooge)) { requestWithTwoFilesOfUnknownMerchants.handleAndfollowRedirect() }

        followedResponse shouldMatch hasBody(containsSubstring("<h3>Rounder Records</h3>"))
        followedResponse shouldMatch hasBody(containsSubstring("<input type=\"hidden\" name=\"remaining-vendors\" value=\"Edgeworld Records,McDonalds\">"))
    }
}

class RedirectHelper(val skrooge: RoutingHttpHandler) {
    fun Request.handleAndfollowRedirect(): Response {
        val initialResponse = skrooge(this)
        return initialResponse.followRedirect()
    }

    fun Response.followRedirect(): Response {
        return skrooge(Request(Method.GET, headerValues("location").first()!!))
    }
}
