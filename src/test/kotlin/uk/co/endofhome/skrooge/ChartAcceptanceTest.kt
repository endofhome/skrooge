package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.oneeyedmen.okeydoke.junit.ApprovalsRule
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.junit.Rule
import org.junit.Test
import java.nio.file.Paths


class ChartAcceptanceTest {

    @Rule @JvmField val approver: ApprovalsRule = ApprovalsRule.fileSystemRule("src/test/kotlin")

    private val categoryMappings = mutableListOf<String>()
    private val categories = Categories("src/test/resources/test-schema.json", categoryMappings)
    private val mappingWriter = StubbedMappingWriter()
    private val decisionReaderWriter = StubbedDecisionReaderWriter()
    private val skrooge = Skrooge(categories, mappingWriter, decisionReaderWriter, Paths.get("src/test/resources/budgets/")).routes()

    @Test
    fun `GET to monthly chart endpoint with valid query parameters`() {
        val request = Request(GET, "/web").query("year", "2017").query("month", "4")
        val response = skrooge(request)

        assertThat(response.status, equalTo(OK))
        approver.assertApproved(response.bodyString())
    }

    @Test
    fun `GET to monthly chart endpoint with invalid query parameters`() {
        val request = Request(GET, "/web").query("year", "2017").query("month", "13")
        val response = skrooge(request)

        assertThat(response.status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `GET to monthly chart endpoint with missing year parameter`() {
        val request = Request(GET, "/web").query("month", "1")
        val response = skrooge(request)

        assertThat(response.status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `GET to monthly chart endpoint with missing month parameter`() {
        val request = Request(GET, "/web").query("year", "2017")
        val response = skrooge(request)

        assertThat(response.status, equalTo(BAD_REQUEST))
    }
}
