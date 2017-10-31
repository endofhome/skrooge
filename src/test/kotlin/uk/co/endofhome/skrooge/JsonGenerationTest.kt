package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CREATED
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasStatus
import org.junit.Before
import org.junit.Test
import uk.co.endofhome.skrooge.Categories.subcategoriesFor
import uk.co.endofhome.skrooge.JsonReport.Companion.jsonReport
import java.io.File
import java.time.LocalDate
import java.time.Month.OCTOBER
import java.time.Year

class JsonGenerationTest {
    val decisionWriter = MockDecisionWriter()
    val jsonReportReader = FileSystemJsonReportReader()
    val skrooge = Skrooge(decisionWriter = decisionWriter).routes()

//    val originalDecision = Decision(Line(LocalDate.of(2017, 10, 18), "Edgeworld Records", 14.99), Category("Fun", Categories.categories().find { it.title == "Fun" }?.subCategories!!), SubCategory("Tom fun budget"))

    @Before
    fun `setup`() {
        val statementData = StatementData(Year.of(2017), OCTOBER, "Milford", listOf(File("doesn't matter")))
        val decisions: List<Decision> = emptyList()
        decisionWriter.write(statementData, decisions)
    }

    @Test
    fun `POST to generate - json endpoint with no monthly data returns BAD REQUEST`() {
        val request = Request(POST, "/generate/json").query("year", "2006").query("month", "10")

        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }

    @Test
    fun `POST to generate - json endpoint with one decision in one monthly decisions file returns correct JSON file`() {
        val categoryTitle = "In your home"
        val subCategories = subcategoriesFor(categoryTitle)
        val decision = Decision(Line(LocalDate.of(2017, 10, 24), "B Dradley Painter and Decorator", 250.00), Category(categoryTitle, subCategories), subCategories.first())
        val statementData = StatementData(Year.of(2017), OCTOBER, "Tom", emptyList())
        decisionWriter.write(statementData, listOf(decision))
        val request = Request(POST, "/generate/json").query("year", "2017").query("month", "10")

        val response = skrooge(request)
        response shouldMatch hasStatus(CREATED)

        val jsonReport = jsonReportReader.read(2017, 10)
        val categoryReportDataItem = CategoryReportDataItem(subCategories.first().name, 250.toDouble())
        val categoryReport = CategoryReport(categoryTitle, listOf(categoryReportDataItem))
        val expectedJsonReport = JsonReport.jsonReport(2017, 10, listOf(categoryReport))
        jsonReport shouldMatch equalTo(expectedJsonReport)
    }
}