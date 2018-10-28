package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.ContentType
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.body.form
import org.http4k.core.with
import org.http4k.lens.Header
import org.junit.Before
import org.junit.Test
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.statementDecisions
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.decisions.Decision
import uk.co.endofhome.skrooge.decisions.Line
import uk.co.endofhome.skrooge.decisions.StubbedDecisionReaderWriter
import uk.co.endofhome.skrooge.decisions.SubCategory
import uk.co.endofhome.skrooge.statements.FileMetadata.statementName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.monthName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.userName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.yearName
import java.nio.file.Paths
import java.time.LocalDate
import java.time.Month

class ReportCategorisationAcceptanceTest {
    private val categoryMappings = mutableListOf<String>()
    private val categories = Categories("src/test/resources/test-schema.json", categoryMappings)
    private val decisionReaderWriter = StubbedDecisionReaderWriter()
    private val skrooge = Skrooge(categories, decisionReaderWriter = decisionReaderWriter, budgetDirectory = Paths.get("src/test/resources/budgets/")).routes

    private val originalDecision =
            Decision(
                    Line(LocalDate.of(2017, 10, 18), "Edgeworld Records", 14.99),
                    Category("Fun", categories.all().find { it.title == "Fun" }?.subcategories!!),
                    SubCategory("Tom fun budget")
            )

    @Before
    fun setup() {
        decisionReaderWriter.files.clear()
    }

    @Test
    fun `POST to reports - categorisations endpoint with no amended decisions writes same decisions`() {
        val request = Request(POST, statementDecisions)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("decisions", "[18/10/2017,Edgeworld Records,14.99,Fun,Tom fun budget]")
                .form(yearName, "2017")
                .form(monthName, "October")
                .form(userName, "Tom")
                .form(statementName, "SomeBank")

        assertThat(skrooge(request).status, equalTo(CREATED))
        assertThat(decisionReaderWriter.read(2017, Month.of(10)), equalTo(listOf(originalDecision)))
    }

    @Test
    fun `POST to reports - categorisations endpoint with amended mappings writes amended mappings`() {
        val request = Request(POST, statementDecisions)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("decisions", "[18/10/2017,Edgeworld Records,14.99,Eats and drinks,Food]")
                .form(yearName, "2017")
                .form(monthName, "October")
                .form(userName, "Tom")
                .form(statementName, "SomeBank")

        val expectedCategory = "Eats and drinks"
        val expectedSubCategories = categories.all().find { it.title == expectedCategory }!!.subcategories
        val expectedDecision = originalDecision.copy(
                category = originalDecision.category?.copy(expectedCategory, expectedSubCategories),
                subCategory = SubCategory("Food")
        )

        assertThat(skrooge(request).status, equalTo(CREATED))
        assertThat(decisionReaderWriter.read(2017, Month.of(10)), equalTo(listOf(expectedDecision)))
    }
}