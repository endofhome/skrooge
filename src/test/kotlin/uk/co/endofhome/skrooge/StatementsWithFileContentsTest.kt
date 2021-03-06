package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.oneeyedmen.okeydoke.junit.ApprovalsRule
import org.http4k.core.ContentType
import org.http4k.core.FormFile
import org.http4k.core.Method.POST
import org.http4k.core.MultipartFormBody
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.junit.Rule
import org.junit.Test
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.statementsWithFileContents
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.categories.StubbedMappingWriter
import uk.co.endofhome.skrooge.decisions.FileSystemDecisionReaderReaderWriter
import uk.co.endofhome.skrooge.decisions.StubbedDecisionReaderWriter
import uk.co.endofhome.skrooge.statements.CategoryMapping
import java.io.File
import java.nio.file.Paths

class StatementsWithFileContentsTest {

    @Rule @JvmField
    val approver: ApprovalsRule = ApprovalsRule.fileSystemRule("src/test/kotlin/approvals")

    private val categories = Categories("src/test/resources/test-schema.json")
    private val mappingWriter = StubbedMappingWriter()
    private val decisionReaderWriter = StubbedDecisionReaderWriter()
    private val testBudgetDirectory = Paths.get("src/test/resources/budgets/")
    private val skrooge = Skrooge(categories, mappingWriter, decisionReaderWriter, testBudgetDirectory).routes

    @Test
    fun `POST to statements endpoint with empty body returns HTTP Bad Request`() {
        val body = MultipartFormBody()
        val request = Request(POST, statementsWithFileContents)
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)
        assertThat(skrooge(request).status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `POST to statements with missing year in form data returns HTTP Bad Request`() {
        val bankStatement = FormFile("2017-9-Tom-empty-statement-file.csv", ContentType.OCTET_STREAM, "".byteInputStream())
        val body = MultipartFormBody().plus("month" to "September")
                                      .plus("user" to "Tom")
                                      .plus("statement-name" to "EmptyStatement")
                                      .plus("statement-file" to bankStatement)

        val request = Request(POST, statementsWithFileContents)
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)
        assertThat(skrooge(request).status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `POST to statements with missing month in form data returns HTTP Bad Request`() {
        val bankStatement = FormFile("2017-9-Tom-empty-statement-file.csv", ContentType.OCTET_STREAM, "".byteInputStream())
        val body = MultipartFormBody().plus("year" to "2017")
                                      .plus("user" to "Tom")
                                      .plus("statement-name" to "EmptyStatement")
                                      .plus("statement-file" to bankStatement)

        val request = Request(POST, statementsWithFileContents)
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)
        assertThat(skrooge(request).status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `POST to statements with missing user in form data returns HTTP Bad Request`() {
        val bankStatement = FormFile("2017-9-Tom-empty-statement-file.csv", ContentType.OCTET_STREAM, "".byteInputStream())
        val body = MultipartFormBody().plus("year" to "2017")
                                      .plus("month" to "September")
                                      .plus("statement-name" to "EmptyStatement")
                                      .plus("statement-file" to bankStatement)

        val request = Request(POST, statementsWithFileContents)
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)
        assertThat(skrooge(request).status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `POST to statements with missing statement name in form data returns HTTP Bad Request`() {
        val bankStatement = FormFile("2017-9-Tom-empty-statement-file.csv", ContentType.OCTET_STREAM, "".byteInputStream())
        val body = MultipartFormBody().plus("year" to "2017")
                                      .plus("month" to "September")
                                      .plus("user" to "Tom")
                                      .plus("statement-file" to bankStatement)

        val request = Request(POST, statementsWithFileContents)
            .header("content-type", "multipart/form-data; boundary=${body.boundary}")
            .body(body)
        assertThat(skrooge(request).status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `POST to statements with missing file in form data returns HTTP Bad Request`() {
        val body = MultipartFormBody().plus("year" to "2017")
                                      .plus("month" to "September")
                                      .plus("statement-name" to "EmptyStatement")
                                      .plus("user" to "Tom")

        val request = Request(POST, statementsWithFileContents)
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)
        assertThat(skrooge(request).status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `POST to statements with correct form data returns HTTP OK`() {
        val formFile = FormFile("2017-9-Tom-EmptyStatement.csv", ContentType.OCTET_STREAM, "".byteInputStream())
        val body = MultipartFormBody().plus("year" to "2017")
                                      .plus("month" to "September")
                                      .plus("user" to "Tom")
                                      .plus("statement-name" to "EmptyStatement")
                                      .plus("statement-file" to formFile)

        val request = Request(POST, statementsWithFileContents)
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)
        assertThat(skrooge(request).status, equalTo(OK))
    }

    @Test
    fun `POST with empty csv produces empty statement file`() {
        val formFile = FormFile("2017-02_Test_EmptyStatement.csv", ContentType.OCTET_STREAM, "".byteInputStream())
        val body = MultipartFormBody().plus("year" to "2017")
                                      .plus("month" to "February")
                                      .plus("user" to "Test")
                                      .plus("statement-name" to "EmptyStatement")
                                      .plus("statement-file" to formFile)
        val request = Request(POST, statementsWithFileContents)
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)

        skrooge(request)

        val statementFile = File("input/normalised/2017-02_Test_EmptyStatement.csv")
        val statementFileContents = statementFile.readLines()
        assertThat(statementFileContents.size, equalTo(0))
    }

    @Test
    fun `POST with one entry produces output file with one entry when recognised merchant`() {
        mappingWriter.write(CategoryMapping("Pizza Union","Eats and drinks","Meals at work"))
        val categories = Categories("src/test/resources/test-schema.json")
        val outputPath = Paths.get("src/test/resources/decisions")
        val decisionReaderWriter = FileSystemDecisionReaderReaderWriter(categories, outputPath)
        val localSkrooge = Skrooge(categories, mappingWriter, decisionReaderWriter, testBudgetDirectory).routes
        val inputStatementContent = "2017-09-17,Pizza Union,5.50\n"
        val body = MultipartFormBody().plus("year" to "2017")
                                      .plus("month" to "February")
                                      .plus("user" to "Test")
                                      .plus("statement-name" to "one-known-merchant")
                                      .plus("statement-file" to FormFile("2017-02_Test_one-known-merchant.csv", ContentType.OCTET_STREAM, inputStatementContent.byteInputStream()))
        val request = Request(POST, statementsWithFileContents)
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)

        val response = localSkrooge(request)

        val statementFile = File("input/normalised/2017-02_Test_one-known-merchant.csv")
        val statementFileContents = statementFile.readLines()
        assertThat(statementFileContents.size, equalTo(1))
        assertThat(statementFileContents[0], equalTo(inputStatementContent.trim()))

        approver.assertApproved(response.bodyString())
    }

    @Test
    fun `POST with two entries produces output file with two entry when recognised merchants`() {
        listOf(
            CategoryMapping("Pizza Union","Eats and drinks","Meals at work"),
            CategoryMapping("Pizza Hut","Eats and drinks","Meals at work")
        ).forEach { mappingWriter.write(it) }
        val categories = Categories("src/test/resources/test-schema.json")
        val outputPath = Paths.get("src/test/resources/decisions")
        val decisionReaderWriter = FileSystemDecisionReaderReaderWriter(categories, outputPath)
        val localSkrooge = Skrooge(categories, mappingWriter, decisionReaderWriter, testBudgetDirectory).routes
        val firstLineOfContent = "2017-02-17,Pizza Union,5.50"
        val secondLineOfContent = "2017-02-17,Pizza Hut,7.50"
        val inputStatementContent = """
            $firstLineOfContent
            $secondLineOfContent
        """.trimIndent()
        val body = MultipartFormBody().plus("year" to "2017")
            .plus("month" to "February")
            .plus("user" to "Test")
            .plus("statement-name" to "two-known-merchants")
            .plus("statement-file" to FormFile("2017-02_Test_two-known-merchants.csv", ContentType.OCTET_STREAM, inputStatementContent.byteInputStream()))
        val request = Request(POST, statementsWithFileContents)
            .header("content-type", "multipart/form-data; boundary=${body.boundary}")
            .body(body)

        val response = localSkrooge(request)

        val statementFile = File("input/normalised/2017-02_Test_two-known-merchants.csv")
        val statementFileContents = statementFile.readLines()
        assertThat(statementFileContents.size, equalTo(2))
        assertThat(statementFileContents[0], equalTo(firstLineOfContent))
        assertThat(statementFileContents[1], equalTo(secondLineOfContent))

        approver.assertApproved(response.bodyString())
    }

    @Test
    fun `POST with one entry returns HTTP See Other when unknown merchant`() {
        val inputStatementContent = "2017-09-17,McDonalds,0.99\n"
        val body = MultipartFormBody().plus("year" to "2017")
                                      .plus("month" to "April")
                                      .plus("user" to "Test")
                                      .plus("statement-name" to "OneUnknownMerchant")
                                      .plus("statement-file" to FormFile("2017-04_Test_one-unknown-merchant.csv", ContentType.OCTET_STREAM, inputStatementContent.byteInputStream()))
        val requestWithMcDonalds = Request(POST, statementsWithFileContents)
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)

        val response = skrooge(requestWithMcDonalds)
        assertThat(response.status, equalTo(SEE_OTHER))
        assertThat(response.header("Location"), equalTo("/unknown-merchant?currentMerchant=McDonalds&year=2017&month=April&user=Test&statement-name=OneUnknownMerchant&statement-file-path=input%2Fnormalised%2F2017-04_Test_OneUnknownMerchant.csv"))

        val followedResponse = with(RedirectHelper(skrooge)) { response.followRedirect() }
        approver.assertApproved(followedResponse.bodyString())
    }

    @Test
    fun `redirect when two unknown merchants shows correct unrecognised merchants`() {
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
                                      .plus("statement-name" to "TwoUnknownMerchants")
                                      .plus("statement-file" to formFile)
        val requestWithTwoRecordShops = Request(POST, statementsWithFileContents)
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)

        val followedResponse = with(RedirectHelper(skrooge)) { requestWithTwoRecordShops.handleAndFollowRedirect() }
        approver.assertApproved(followedResponse.bodyString())
    }

    @Test
    fun `redirect when more than two unknown merchants shows correct unrecognised merchants`() {
        val inputStatementContent = """
            2017-09-17,Rounder Records,14.99
            2017-09-17,Edgeworld Records,15.99
            2017-09-17,Honest Jon's Records,19.99
        """.trimIndent()
        val formFile = FormFile(
            "2017-04_Test_three-unknown-merchants.csv",
            ContentType.OCTET_STREAM,
            inputStatementContent.byteInputStream()
        )
        val body = MultipartFormBody().plus("year" to "2017")
            .plus("month" to "April")
            .plus("user" to "Test")
            .plus("statement-name" to "ThreeUnknownMerchants")
            .plus("statement-file" to formFile)
        val requestWithThreeRecordShops = Request(POST, statementsWithFileContents)
            .header("content-type", "multipart/form-data; boundary=${body.boundary}")
            .body(body)

        val followedResponse = with(RedirectHelper(skrooge)) { requestWithThreeRecordShops.handleAndFollowRedirect() }
        approver.assertApproved(followedResponse.bodyString())
    }
}

