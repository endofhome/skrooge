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
import java.io.File
import java.time.LocalDate
import java.time.Month
import java.time.Year

class ReportCategorisationAcceptanceTest {
    val decisionWriter = MockDecisionWriter()
    val skrooge = Skrooge(decisionWriter = decisionWriter).routes()

    val originalDecision = Decision(Line(LocalDate.of(2017, 10, 18), "Edgeworld Records", 14.99), Category("Fun", Categories.categories().find { it.title == "Fun" }?.subCategories!!), SubCategory("Tom fun budget"))

    @Before
    fun `setup`() {
        val statementData = StatementData(Year.of(2017), Month.OCTOBER, "Milford", listOf(File("doesn't matter")))
        val decisions = listOf(originalDecision)
        decisionWriter.write(statementData, decisions)
    }

    @Test
    fun `POST to reports - categorisations endpoint with no amended decisions writes same decisions`() {
        val request = Request(POST, "/reports/categorisations")
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("statement-data", "2017;October;Tom;[blah]")
                .form("decisions", "[2017/10/18,Edgeworld Records,14.99,Fun,Tom fun budget]")

        skrooge(request) shouldMatch hasStatus(CREATED)
        assertThat(decisionWriter.read(), equalTo(listOf(originalDecision)))
    }

    @Test
    fun `POST to reports - categorisations endpoint with amended mappings writes amended mappings`() {
        val request = Request(POST, "/reports/categorisations")
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("statement-data", "2017;October;Tom;[blah]")
                .form("decisions", "[2017/10/18,Edgeworld Records,14.99,Eats and drinks,Food]")

        val expectedCategory = "Eats and drinks"
        val expectedSubCategories = Categories.categories().find { it.title == expectedCategory }!!.subCategories
        val expectedDecision = originalDecision.copy(
                category = originalDecision.category?.copy(expectedCategory, expectedSubCategories),
                subCategory = SubCategory("Food")
        )

        skrooge(request) shouldMatch hasStatus(CREATED)
        assertThat(decisionWriter.read(), equalTo(listOf(expectedDecision)))
    }
}