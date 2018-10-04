package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.ContentType
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.body.form
import org.http4k.core.with
import org.http4k.lens.Header
import org.junit.Test
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.categories.StubbedMappingWriter
import uk.co.endofhome.skrooge.decisions.FileSystemDecisionReaderReaderWriter
import java.io.File
import java.nio.file.Paths

class DecisionsHandlerTest {

    private val categoryMappings = mutableListOf<String>()
    private val categories = Categories("src/test/resources/test-schema.json", categoryMappings)
    private val mappingWriter = StubbedMappingWriter()
    private val outputPath = Paths.get("src/test/resources/decisions")
    private val decisionReaderWriter = FileSystemDecisionReaderReaderWriter(categories, outputPath)
    private val testBudgetDirectory = Paths.get("src/test/resources/budgets/")
    private val skrooge = Skrooge(categories, mappingWriter, decisionReaderWriter, testBudgetDirectory).routes()

    @Test
    fun `POST with valid form data results in HTTP CREATED and new decision file on file system`() {

        val request = Request(Method.POST, "/reports/categorisations")
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("decisions", "[29/12/2016,National Lottery,10,Fun,Test fun budget]")
                .form("statement-data", "2016;December;Test;SomeBank")

        assertThat(skrooge(request).status, equalTo(CREATED))

        val decisionFile = File("$outputPath/2016-12-Test-decisions-SomeBank.csv")
        val decisionFileContents = decisionFile.readLines()

        assertThat(decisionFileContents.size, equalTo(1))
        assertThat(decisionFileContents[0], equalTo("2016-12-29,National Lottery,10.0,Fun,Test fun budget"))
    }
}