package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.oneeyedmen.okeydoke.junit.ApprovalsRule
import org.http4k.core.ContentType
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.Status.Companion.TEMPORARY_REDIRECT
import org.http4k.core.body.form
import org.http4k.core.with
import org.http4k.lens.Header
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.categoryMapping
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.statementsWithFilePath
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.categories.CategoryMappingHandler.Companion.newMappingKey
import uk.co.endofhome.skrooge.categories.CategoryMappingHandler.Companion.remainingMerchantKey
import uk.co.endofhome.skrooge.categories.StubbedMappingWriter
import uk.co.endofhome.skrooge.statements.FileMetadata.statementFilePathKey
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.FieldNames.MONTH
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.FieldNames.STATEMENT
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.FieldNames.USER
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.FieldNames.YEAR
import java.io.File
import java.io.PrintWriter
import java.nio.file.Paths

class CategoryMappingTest {

    @Rule
    @JvmField val approver: ApprovalsRule = ApprovalsRule.fileSystemRule("src/test/kotlin/approvals")

    private val categoryMappings = mutableListOf("Edgeworld Records,Fun,Tom fun budget")
    private val categories = Categories("src/test/resources/test-schema.json", categoryMappings)
    private val mappingWriter = StubbedMappingWriter()
    private val statementYear = "2017"
    private val statementMonth = "February"
    private val statementUser = "Test"
    private val statement = "Empty Statement"
    private val statementFilePath = "src/test/resources/2017-02_Test_EmptyStatement.csv"

    private val skrooge = Skrooge(categories, mappingWriter, budgetDirectory = Paths.get("src/test/resources/budgets/")).routes

    @Test
    fun `POST to category-mapping endpoint with empty new-mapping field returns HTTP Bad Request`() {
        val request = Request(POST, categoryMapping)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form(newMappingKey, "")
                .form(YEAR.key, statementYear)
                .form(MONTH.key, statementMonth)
                .form(USER.key, statementUser)
                .form(STATEMENT.key, statement)
                .form(statementFilePathKey, statementFilePath)

        assertThat(skrooge(request).status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `POST to category-mapping endpoint with non-CSV mapping returns HTTP Bad Request`() {
        val request = Request(POST, categoryMapping)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form(newMappingKey, "Casbah Records;Established 1967 in our minds")
                .form(YEAR.key, statementYear)
                .form(MONTH.key, statementMonth)
                .form(USER.key, statementUser)
                .form(STATEMENT.key, statement)
                .form(statementFilePathKey, statementFilePath)

        assertThat(skrooge(request).status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `POST to category-mapping endpoint with good CSV mapping returns HTTP OK and writes new mapping`() {
        val request = Request(POST, categoryMapping)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form(newMappingKey, "Casbah Records,Fun,Tom fun budget")
                .form(YEAR.key, statementYear)
                .form(MONTH.key, statementMonth)
                .form(USER.key, statementUser)
                .form(STATEMENT.key, statement)
                .form(statementFilePathKey, statementFilePath)

        assertThat(skrooge(request).status, equalTo(TEMPORARY_REDIRECT))
        assertThat(mappingWriter.read().last(), equalTo("Casbah Records,Fun,Tom fun budget"))
    }

    @Test
    fun `successful POST to category-mapping redirects back to continue categorisation if necessary`() {
        val request = Request(POST, categoryMapping)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form(newMappingKey, "DIY Space for London,Fun,Tom fun budget")
                .form(remainingMerchantKey, "Another vendor")
                .form(YEAR.key, statementYear)
                .form(MONTH.key, statementMonth)
                .form(USER.key, statementUser)
                .form(STATEMENT.key, statement)
                .form(statementFilePathKey, statementFilePath)

        val followedResponse = with(RedirectHelper(skrooge)) { request.handleAndFollowRedirect() }

        assertThat(mappingWriter.read().last(), equalTo("DIY Space for London,Fun,Tom fun budget"))
        assertThat(followedResponse.status, equalTo(OK))
        approver.assertApproved(followedResponse.bodyString())
    }

    @Test
    fun `can accept multiple remaining merchants`() {
        val testFile = File(statementFilePath)
        val printWriter = PrintWriter(testFile)
        printWriter.use {
            it.write("")
        }

        val request = Request(POST, categoryMapping)
            .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
            .form(newMappingKey, "Last new mapping,Fun,Tom fun budget")
            .form(remainingMerchantKey, "Bob's Laundrette")
            .form(remainingMerchantKey, "Bert's Hardware")
            .form(remainingMerchantKey, "Han's Solo")
            .form(YEAR.key, statementYear)
            .form(MONTH.key, statementMonth)
            .form(USER.key, statementUser)
            .form(STATEMENT.key, statement)
            .form(statementFilePathKey, statementFilePath)

        val response = skrooge(request)

        assertThat(response.status, equalTo(SEE_OTHER))
        assertThat(response.header("Location")!!, equalTo("/unknown-merchant?currentMerchant=Bob%27s+Laundrette&year=2017&month=February&user=Test&statement-name=Empty+Statement&statement-file-path=src%2Ftest%2Fresources%2F2017-02_Test_EmptyStatement.csv&remaining-merchant=Bert%27s+Hardware&remaining-merchant=Han%27s+Solo"))

        val followedResponse = with(RedirectHelper(skrooge)) { response.followRedirect(request) }
        approver.assertApproved(followedResponse.bodyString())
    }

    @Test
    fun `when all categories have been mapped a monthly report is available for review`() {
        val testFile = File(statementFilePath)
        val printWriter = PrintWriter(testFile)
        printWriter.use {
            it.write("")
        }

        val request = Request(POST, categoryMapping)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form(newMappingKey, "Last new mapping,Fun,Tom fun budget")
                .form(YEAR.key, statementYear)
                .form(MONTH.key, statementMonth)
                .form(USER.key, statementUser)
                .form(STATEMENT.key, statement)
                .form(statementFilePathKey, statementFilePath)

        val response = skrooge(request)

        assertThat(response.status, equalTo(TEMPORARY_REDIRECT))
        assertThat(response.header("Method")!!, equalTo("POST"))
        assertTrue(response.header("Location")!!.endsWith(statementsWithFilePath))

        val followedResponse = with(RedirectHelper(skrooge)) { response.followRedirect(request) }
        approver.assertApproved(followedResponse.bodyString())
    }
}