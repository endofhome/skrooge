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
import java.nio.file.Paths

class ReportCategorisationsTest {

    private val categoryMappings = mutableListOf<String>()
    private val categories = Categories("src/test/resources/test-schema.json", categoryMappings)
    private val mappingWriter = StubbedMappingWriter()
    private val decisionReaderWriter = StubbedDecisionReaderWriter()
    private val testBudgetDirectory = Paths.get("src/test/resources/budgets/")
    private val skrooge = Skrooge(categories, mappingWriter, decisionReaderWriter, testBudgetDirectory).routes()

    @Test
    fun `POST with valid form data results in HTTP CREATED`() {
        val request = Request(Method.POST, "/reports/categorisations")
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("decisions", "[29/12/2016,National Lottery,10,Fun,Bert fun budget]")
                .form("statement-data", "2016;DECEMBER;Bert;Nationwide")

        assertThat(skrooge(request).status, equalTo(CREATED))
    }
}