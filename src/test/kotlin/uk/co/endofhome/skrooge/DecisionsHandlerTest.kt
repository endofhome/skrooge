package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.ContentType
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.with
import org.http4k.lens.Header
import org.junit.Before
import org.junit.Test
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.index
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.statementDecisions
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.decisions.Decision
import uk.co.endofhome.skrooge.decisions.DecisionsHandler.Companion.decision
import uk.co.endofhome.skrooge.decisions.Line
import uk.co.endofhome.skrooge.decisions.StubbedDecisionReaderWriter
import uk.co.endofhome.skrooge.decisions.SubCategory
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.FieldNames.MONTH
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.FieldNames.STATEMENT
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.FieldNames.USER
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.FieldNames.YEAR
import java.nio.file.Paths
import java.time.LocalDate
import java.time.Month

class DecisionsHandlerTest {
    private val categoryMappings = mutableListOf<String>()
    private val categories = Categories("src/test/resources/test-schema.json", categoryMappings)
    private val decisionReaderWriter = StubbedDecisionReaderWriter()
    private val skrooge = Skrooge(categories, decisionReaderWriter = decisionReaderWriter, budgetDirectory = Paths.get("src/test/resources/budgets/")).routes

    private val originalDecision =
            Decision(
                Line(LocalDate.of(2017, 10, 18), "Edgeworld Records", 14.99),
                Category("Fun", categories.get("Fun").subcategories),
                SubCategory("Tom fun budget")
            )

    @Before
    fun setup() {
        decisionReaderWriter.files.clear()
    }

    @Test
    fun `POST to statementDecisions endpoint with no amended decisions writes same decisions`() {
        val request = Request(Method.POST, statementDecisions)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form(decision, "18/10/2017,Edgeworld Records,14.99,Fun,Tom fun budget")
                .form(YEAR.key, "2017")
                .form(MONTH.key, "October")
                .form(USER.key, "Tom")
                .form(STATEMENT.key, "SomeBank")

        val response = skrooge(request)
        assertThat(response.status, equalTo(Status.SEE_OTHER))
        assertThat(response.header("Location")!!, equalTo(index))

        assertThat(decisionReaderWriter.read(2017, Month.of(10)), equalTo(listOf(originalDecision)))
    }

    @Test
    fun `POST to statementDecisions endpoint with amended mappings writes amended mappings`() {
        val request = Request(Method.POST, statementDecisions)
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form(decision, "18/10/2017,Edgeworld Records,14.99,Eats and drinks,Food")
                .form(YEAR.key, "2017")
                .form(MONTH.key, "October")
                .form(USER.key, "Tom")
                .form(STATEMENT.key, "SomeBank")

        val expectedCategory = "Eats and drinks"
        val expectedSubCategories = categories.get(expectedCategory).subcategories
        val expectedDecision = originalDecision.copy(
                category = originalDecision.category?.copy(expectedCategory, expectedSubCategories),
                subCategory = SubCategory("Food")
        )

        val response = skrooge(request)
        assertThat(response.status, equalTo(Status.SEE_OTHER))
        assertThat(response.header("Location")!!, equalTo(index))
        assertThat(decisionReaderWriter.read(2017, Month.of(10)), equalTo(listOf(expectedDecision)))
    }

    @Test
    fun `POST to statementDecisions endpoint with multiple decisions writes same decisions`() {
        val additionalDecision =
            Decision(
                Line(LocalDate.of(2017, 10, 3), "Pizza Union", 5.5),
                Category("Eats and drinks", categories.get("Eats and drinks").subcategories),
                SubCategory("Meals at work")
            )

        val request = Request(Method.POST, statementDecisions)
            .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
            .form(decision, "18/10/2017,Edgeworld Records,14.99,Fun,Tom fun budget")
            .form(decision, "3/10/2017,Pizza Union,5.50,Eats and drinks,Meals at work")
            .form(YEAR.key, "2017")
            .form(MONTH.key, "October")
            .form(USER.key, "Tom")
            .form(STATEMENT.key, "SomeBank")

        val response = skrooge(request)
        assertThat(response.status, equalTo(Status.SEE_OTHER))
        assertThat(response.header("Location")!!, equalTo(index))

        val expectedDecisions = listOf(originalDecision, additionalDecision)
        assertThat(decisionReaderWriter.read(2017, Month.of(10)), equalTo(expectedDecisions))
    }

    @Test
    fun `POST to statementDecisions endpoint with missing fields returns HTTP Bad Request`() {
        val request = Request(Method.POST, statementDecisions)
            .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
            .form(decision, "18/10/2017,Edgeworld Records,14.99,Fun,Tom fun budget")

        val response = skrooge(request)
        assertThat(response.status, equalTo(Status.BAD_REQUEST))
        assertThat(response.bodyString(), equalTo("""
            Form fields were missing:
            year
            month
            user
            statement-name
        """.trimIndent()))
    }
}
