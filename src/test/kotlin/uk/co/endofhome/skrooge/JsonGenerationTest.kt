package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.should.shouldMatch
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.hamkrest.hasStatus
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.Month
import java.time.Year

class JsonGenerationTest {
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
    fun `POST to generate - json endpoint with no monthly data returns BAD REQUEST`() {
        val request = Request(POST, "/generate/json").query("year", "2006").query("month", "10")

        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }
}