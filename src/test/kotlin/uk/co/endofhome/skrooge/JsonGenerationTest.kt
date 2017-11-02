package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.should.shouldMatch
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.CREATED
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasStatus
import org.junit.Before
import org.junit.Test
import uk.co.endofhome.skrooge.Categories.subcategoriesFor
import java.io.File
import java.time.LocalDate
import java.time.Month.OCTOBER
import java.time.Year

class JsonGenerationTest {
    val decisionWriter = StubbedDecisionWriter()
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
        val request = Request(GET, "/generate/json").query("year", "2006").query("month", "10")

        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }

    @Test
    fun `POST to generate - json endpoint with one decision in one monthly decisions file returns correct JSON`() {
        val categoryTitle = "In your home"
        val subCategories = subcategoriesFor(categoryTitle)
        val decision = Decision(Line(LocalDate.of(2017, 10, 24), "B Dradley Painter and Decorator", 250.00), Category(categoryTitle, subCategories), subCategories.find { it.name == "Building insurance" })
        val statementData = StatementData(Year.of(2017), OCTOBER, "Tom", emptyList())
        decisionWriter.write(statementData, listOf(decision))
        val request = Request(GET, "/generate/json").query("year", "2017").query("month", "10")

        val response = skrooge(request)
        response shouldMatch hasStatus(CREATED)
        response shouldMatch hasBody("{\"year\":2017,\"month\":\"October\",\"monthNumber\":10,\"categories\":[{\"title\":\"In your home\",\"data\":[{\"name\":\"Building insurance\",\"actual\":250.0}]}]}")
    }

    @Test
    fun `POST to generate - json endpoint with two decisions of same category in one monthly decisions file returns correct JSON`() {
        val categoryTitle = "In your home"
        val subCategories = subcategoriesFor(categoryTitle)
        val decision1 = Decision(Line(LocalDate.of(2017, 10, 24), "B Dradley Painter and Decorator", 250.00), Category(categoryTitle, subCategories), subCategories.find { it.name == "Building insurance" })
        val decision2 = Decision(Line(LocalDate.of(2017, 10, 14), "OIS Removals", 500.00), Category(categoryTitle, subCategories), subCategories.find { it.name == "Building insurance" })
        val statementData = StatementData(Year.of(2017), OCTOBER, "Tom", emptyList())
        decisionWriter.write(statementData, listOf(decision1, decision2))
        val request = Request(GET, "/generate/json").query("year", "2017").query("month", "10")

        val response = skrooge(request)
        response shouldMatch hasStatus(CREATED)
        response shouldMatch hasBody("{\"year\":2017,\"month\":\"October\",\"monthNumber\":10,\"categories\":[{\"title\":\"In your home\",\"data\":[{\"name\":\"Building insurance\",\"actual\":750.0}]}]}")
    }
}