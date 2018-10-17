package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.oneeyedmen.okeydoke.junit.ApprovalsRule
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.with
import org.http4k.lens.Header
import org.junit.Rule
import org.junit.Test
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.statementsWithFilePath
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.categories.StubbedMappingWriter
import uk.co.endofhome.skrooge.decisions.StubbedDecisionReaderWriter
import java.io.File
import java.io.PrintWriter
import java.nio.file.Paths

class StatementsWithFilePathTest {

    @Rule
    @JvmField val approver: ApprovalsRule = ApprovalsRule.fileSystemRule("src/test/kotlin/approvals")

    private val categoryMappings = mutableListOf<String>()
    private val categories = Categories("src/test/resources/test-schema.json", categoryMappings)
    private val mappingWriter = StubbedMappingWriter()
    private val decisionReaderWriter = StubbedDecisionReaderWriter()
    private val testBudgetDirectory = Paths.get("src/test/resources/budgets/")
    private val testNormalisedStatementsDirectory = Paths.get("src/test/resources/normalised-statements")
    private val skrooge = Skrooge(categories, mappingWriter, decisionReaderWriter, testBudgetDirectory).routes

    @Test
    fun `POST to statements endpoint with empty body returns HTTP Bad Request`() {
        val request = Request(Method.POST, statementsWithFilePath).body(Body.EMPTY)
            .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)

        assertThat(skrooge(request).status, equalTo(Status.BAD_REQUEST))
    }

    @Test
    fun `POST to statements with missing year in body returns HTTP Bad Request`() {
        val request = Request(Method.POST, statementsWithFilePath)
                        .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                        .form("month", "September")
                        .form("user", "Tom")
                        .form("statement-name", "EmptyStatement")
                        .form("statement-file-path", "$testNormalisedStatementsDirectory/empty-statement.csv")
        assertThat(skrooge(request).status, equalTo(Status.BAD_REQUEST))
    }

    @Test
    fun `POST to statements with missing month in form data returns HTTP Bad Request`() {
        val request = Request(Method.POST, statementsWithFilePath)
                        .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                        .form("year", "2017")
                        .form("user", "Tom")
                        .form("statement-name", "EmptyStatement")
                        .form("statement-file-path", "$testNormalisedStatementsDirectory/empty-statement.csv")
        assertThat(skrooge(request).status, equalTo(Status.BAD_REQUEST))
    }

    @Test
    fun `POST to statements with missing user in form data returns HTTP Bad Request`() {
        val request = Request(Method.POST, statementsWithFilePath)
                        .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                        .form("year", "2017")
                        .form("month", "September")
                        .form("statement-name", "EmptyStatement")
                        .form("statement-file-path", "$testNormalisedStatementsDirectory/empty-statement.csv")
        assertThat(skrooge(request).status, equalTo(Status.BAD_REQUEST))
    }

    @Test
    fun `POST to statements with missing statement name in form data returns HTTP Bad Request`() {
        val request = Request(Method.POST, statementsWithFilePath)
                        .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                        .form("year", "2017")
                        .form("month", "September")
                        .form("user", "Tom")
                        .form("statement-file-path", "$testNormalisedStatementsDirectory/empty-statement.csv")
        assertThat(skrooge(request).status, equalTo(Status.BAD_REQUEST))
    }

    @Test
    fun `POST to statements with missing statement file path in form data returns HTTP Bad Request`() {
        val request = Request(Method.POST, statementsWithFilePath)
                        .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                        .form("year", "2017")
                        .form("month", "September")
                        .form("user", "Tom")
                        .form("statement-name", "EmptyStatement")
        assertThat(skrooge(request).status, equalTo(Status.BAD_REQUEST))
    }

    @Test
    fun `POST to statements with correct form data returns HTTP OK`() {
        val filePath = "$testNormalisedStatementsDirectory/empty-statement.csv"
        val testFile = File(filePath)
        val printWriter = PrintWriter(testFile)
        printWriter.use {
            it.write("")
        }
        val request = Request(Method.POST, statementsWithFilePath)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("year", "2017")
                .form("month", "September")
                .form("user", "Tom")
                .form("statement-name", "EmptyStatement")
                .form("statement-file-path", filePath)

        val response = skrooge(request)

        assertThat(response.status, equalTo(Status.OK))
        approver.assertApproved(response.bodyString())
    }
}