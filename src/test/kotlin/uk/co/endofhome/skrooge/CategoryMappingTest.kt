package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.oneeyedmen.okeydoke.junit.ApprovalsRule
import org.http4k.core.ContentType
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
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
import uk.co.endofhome.skrooge.categories.StubbedMappingWriter
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.monthName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.statement
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.userName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.yearName
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
    private val statementName = "Empty Statement"
    private val statementFilePath = "src/test/resources/2017-02_Test_EmptyStatement.csv"

    private val skrooge = Skrooge(categories, mappingWriter, budgetDirectory = Paths.get("src/test/resources/budgets/")).routes

    @Test
    fun `POST to category-mapping endpoint with empty new-mapping field returns HTTP Bad Request`() {
        val request = Request(POST, categoryMapping)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("new-mapping", "")
                .form("remaining-vendors", "")
                .form(yearName, statementYear)
                .form(monthName, statementMonth)
                .form(userName, statementUser)
                .form(statement, statementName)
                .form("statement-file-path", statementFilePath)

        assertThat(skrooge(request).status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `POST to category-mapping endpoint with non-CSV mapping returns HTTP Bad Request`() {
        val request = Request(POST, categoryMapping)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("new-mapping", "Casbah Records;Established 1967 in our minds")
                .form("remaining-vendors", "")
                .form(yearName, statementYear)
                .form(monthName, statementMonth)
                .form(userName, statementUser)
                .form(statement, statementName)
                .form("statement-file-path", statementFilePath)

        assertThat(skrooge(request).status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `POST to category-mapping endpoint with good CSV mapping returns HTTP OK and writes new mapping`() {
        val request = Request(POST, categoryMapping)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("new-mapping", "Casbah Records,Fun,Tom fun budget")
                .form("remaining-vendors", "")
                .form(yearName, statementYear)
                .form(monthName, statementMonth)
                .form(userName, statementUser)
                .form(statement, statementName)
                .form("statement-file-path", statementFilePath)

        assertThat(skrooge(request).status, equalTo(TEMPORARY_REDIRECT))
        assertThat(mappingWriter.read().last(), equalTo("Casbah Records,Fun,Tom fun budget"))
    }

    @Test
    fun `succesful POST to category-mapping redirects back to continue categorisation if necessary`() {
        val request = Request(POST, categoryMapping)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("new-mapping", "DIY Space for London,Fun,Tom fun budget")
                .form("remaining-vendors", "Another vendor")
                .form(yearName, statementYear)
                .form(monthName, statementMonth)
                .form(userName, statementUser)
                .form(statement, statementName)
                .form("statement-file-path", statementFilePath)

        val followedResponse = with(RedirectHelper(skrooge)) { request.handleAndFollowRedirect() }

        assertThat(mappingWriter.read().last(), equalTo("DIY Space for London,Fun,Tom fun budget"))
        assertThat(followedResponse.status, equalTo(OK))
        assertThat(followedResponse.bodyString(), containsSubstring("You need to categorise some merchants."))
        assertThat(followedResponse.bodyString(), containsSubstring("<h3>Another vendor</h3>"))
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
                .form("new-mapping", "Last new mapping,Fun,Tom fun budget")
                .form("remaining-vendors", "")
                .form(yearName, statementYear)
                .form(monthName, statementMonth)
                .form(userName, statementUser)
                .form(statement, statementName)
                .form("statement-file-path", statementFilePath)

        val response = skrooge(request)

        assertThat(response.status, equalTo(TEMPORARY_REDIRECT))
        assertThat(response.header("Method")!!, equalTo("POST"))
        assertTrue(response.header("Location")!!.endsWith(statementsWithFilePath))

        val followedResponse = with(RedirectHelper(skrooge)) { response.followRedirect(request) }
        approver.assertApproved(followedResponse.bodyString())
    }
}