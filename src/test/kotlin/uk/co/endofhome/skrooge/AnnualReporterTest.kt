package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.format.Gson
import org.http4k.format.Gson.auto
import org.junit.Test
import java.time.LocalDate
import java.time.Month
import java.time.Year
import java.time.format.DateTimeFormatter

class AnnualReporterTest {

    @Test
    fun `valid request returns OK`() {
        val annualReport = setUpWithOneDecision()
        val request = Request(Method.GET, "any-url")
                .query("startDate", "1978-11-10")

        val response = annualReport.handle(request)

        assertThat(response.status, equalTo(Status.OK))
    }

    @Test
    fun `response has expected properties`() {
        val annualReport = setUpWithOneDecision()
        val request = Request(Method.GET, "any-url")
                .query("startDate", "1978-11-10")

        val response = annualReport.handle(request)

        val reportLens = Body.auto<AnnualReport>().toLens()
        val json = reportLens.extract(response)
        val categoryReport = CategoryReport("Eats and drinks", listOf(CategoryReportDataItem("Food", 4.99)))
        assertThat(json.startDate, equalTo(LocalDate.of(1978, 11, 10)))
        assertThat(json.categories, equalTo(listOf(categoryReport)))
    }


    private fun setUpWithOneDecision(): AnnualReporter {
        val statementData = StatementData(Year.of(2018), Month.APRIL, "username", emptyList()) // not sure if this makes much sense
        val line = Line(
                LocalDate.parse("1978-11-10", DateTimeFormatter.ISO_DATE),
                "Woolworths",
                4.99)
        val subcategoryConcerned = SubCategory("Food")
        val subcategories = listOf(
                subcategoryConcerned,
                SubCategory("Eating out"),
                SubCategory("Meals at work")
        )
        val decisions = listOf(Decision(line, Category("Eats and drinks", subcategories), subcategoryConcerned))
        val decisionWriter = StubbedDecisionReaderWriter()
        decisionWriter.write(statementData, decisions)
        val categories = Categories("src/test/resources/test-schema.json").all()
        return AnnualReporter(Gson, categories, decisionWriter, toCategoryReports)
    }
}