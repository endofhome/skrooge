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
import uk.co.endofhome.skrooge.categories.AnnualBudget
import uk.co.endofhome.skrooge.categories.AnnualBudgets
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.categories.CategoryReporter
import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.decisions.Decision
import uk.co.endofhome.skrooge.decisions.Line
import uk.co.endofhome.skrooge.decisions.StubbedDecisionReaderWriter
import uk.co.endofhome.skrooge.decisions.SubCategory
import uk.co.endofhome.skrooge.reports.AnnualCategoryReport
import uk.co.endofhome.skrooge.reports.AnnualCategoryReportDataItem
import uk.co.endofhome.skrooge.reports.AnnualReport
import uk.co.endofhome.skrooge.reports.AnnualReporter
import uk.co.endofhome.skrooge.statements.StatementData
import java.time.LocalDate
import java.time.Month
import java.time.Year
import java.time.format.DateTimeFormatter

class AnnualReporterTest {

    @Test
    fun `valid request returns OK`() {
        val annualReport = setUpWithOneDecision()
        val request = Request(Method.GET, "any-url")
                .query("startDate", "1978-11-01")

        val response = annualReport(request)

        assertThat(response.status, equalTo(Status.OK))
    }

    @Test
    fun `response has expected properties`() {
        val annualReport = setUpWithOneDecision()
        val request = Request(Method.GET, "any-url")
                .query("startDate", "1978-11-01")

        val response = annualReport(request)

        val reportLens = Body.auto<AnnualReport>().toLens()
        val json = reportLens.extract(response)
        val categoryReport = AnnualCategoryReport("Eats and drinks", listOf(AnnualCategoryReportDataItem("Food", 4.99, 5.0, 60.0)))
        assertThat(json.startDate, equalTo(LocalDate.of(1978, 11, 1)))
        assertThat(json.categories, equalTo(listOf(categoryReport)))
    }

    private fun setUpWithOneDecision(): AnnualReporter {
        val statementData = StatementData(Year.of(2018), Month.APRIL, "username", "some-bank") // not sure if this makes much sense
        val line = Line(
                LocalDate.parse("1978-11-01", DateTimeFormatter.ISO_DATE),
                "Woolworths",
                4.99)
        val subcategoryConcerned = SubCategory("Food")
        val subcategories = listOf(
                subcategoryConcerned,
                SubCategory("Eating out"),
                SubCategory("Meals at work")
        )
        val category = Category("Eats and drinks", subcategories)
        val decisions = listOf(Decision(line, category, subcategoryConcerned))
        val decisionWriter = StubbedDecisionReaderWriter()
        decisionWriter.write(statementData, decisions)
        val categoryMappings = emptyList<String>().toMutableList()
        val categories = Categories("src/test/resources/test-schema.json", categoryMappings).all()
        val budget = AnnualBudget(LocalDate.of(1978, 9, 24), listOf(subcategoryConcerned to category to 5.0))
        val budgets = listOf(budget)
        val annualBudgets = AnnualBudgets(budgets)
        val categoryReporter = CategoryReporter(categories, annualBudgets)

        return AnnualReporter(Gson, decisionWriter, categoryReporter)
    }
}