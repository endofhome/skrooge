package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.oneeyedmen.okeydoke.junit.ApprovalsRule
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.junit.Rule
import org.junit.Test
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.monthlyBarChartReport
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.categories.StubbedMappingWriter
import uk.co.endofhome.skrooge.decisions.StubbedDecisionReaderWriter
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.FieldNames.MONTH
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.FieldNames.YEAR
import java.nio.file.Paths


class ChartAcceptanceTest {

    @Rule @JvmField
    val approver: ApprovalsRule = ApprovalsRule.fileSystemRule("src/test/kotlin/approvals")

    private val categories = Categories("src/test/resources/test-schema.json")
    private val mappingWriter = StubbedMappingWriter()
    private val decisionReaderWriter = StubbedDecisionReaderWriter()
    private val skrooge = Skrooge(categories, mappingWriter, decisionReaderWriter, Paths.get("src/test/resources/budgets/")).routes

    @Test
    fun `GET to monthly chart endpoint with valid query parameters`() {
        val response = assertExpectedBarChartResponse("2017", "4", OK)
        approver.assertApproved(response.bodyString())
    }

    @Test
    fun `GET to monthly chart endpoint with invalid query parameters`() {
        assertExpectedBarChartResponse("2017", "13", BAD_REQUEST)
    }

    @Test
    fun `GET to monthly chart endpoint with missing year parameter`() {
        assertExpectedBarChartResponse(
            year = null,
            monthNumber = "1",
            status = BAD_REQUEST
        )
    }

    @Test
    fun `GET to monthly chart endpoint with missing month parameter`() {
        assertExpectedBarChartResponse(
            year = "2017",
            monthNumber = null,
            status = BAD_REQUEST
        )
    }

    private fun assertExpectedBarChartResponse(year: String?, monthNumber: String?, status: Status): Response {
        val baseRequest = Request(GET, monthlyBarChartReport)
        val queryParams = listOf(YEAR.key to year, MONTH.key to monthNumber)
        val request = queryParams.fold(baseRequest) { acc, query ->
            query.second?.let {
                acc.query(query.first, it)
            } ?: acc
        }

        val response = skrooge(request)

        assertThat(response.status, equalTo(status))
        return response
    }
}
