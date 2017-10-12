package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import org.http4k.core.ContentType
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.body.form
import org.http4k.core.with
import org.http4k.hamkrest.hasStatus
import org.http4k.lens.Header
import org.junit.Before
import org.junit.Test

class ReportCategorisationAcceptanceTest {
    val categoryMappings = listOf("Edgeworld Records,Fun,Tom fun budget")
    val mappingWriter = MockMappingWriter()
    val skrooge = Skrooge(categoryMappings, mappingWriter).routes()

    @Before
    fun `setup`() {
        categoryMappings.forEach {
            mappingWriter.write(it)
        }
    }

    @Test
    fun `POST to reports - categorisations endpoint with no amended mappings returns same mappings`() {
        val request = Request(POST, "/reports/categorisations")
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("decisions", "[Edgeworld Records,Fun,Tom fun budget]")

        skrooge(request) shouldMatch hasStatus(CREATED)
        assertThat(mappingWriter.read(), equalTo(listOf("Edgeworld Records,Fun,Tom fun budget")))
    }
}