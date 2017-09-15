package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.*
import org.http4k.core.Method.POST
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.SEE_OTHER
import org.junit.Before
import org.junit.Test
import java.io.File

class SkroogeTest {
    val skrooge = Skrooge().routes()

    @Before
    fun `setup`() {
        DecisionWriter().cleanDecisions()
    }

    @Test
    fun `POST to statements endpoint with empty body returns HTTP Not Found`() {
        val request = Request(POST, "/statements").body(Body.EMPTY)
        assertThat(skrooge(request), equalTo(Response(NOT_FOUND)))
    }

    @Test
    fun `POST to statements with incorrect dummy form data returns HTTP Bad Request`() {
        // Can't receive form data, so will read files from FS instead. Will use a string to represent form.
        val request = Request(POST, "/statements").body("nonsense")
        assertThat(skrooge(request), equalTo(Response(BAD_REQUEST)))
    }

    @Test
    fun `POST to statements with a dummy string instead of form data returns HTTP OK`() {
        val request = Request(POST, "/statements").body("2017,September,Tom,src/test/resources/empty-file.csv")
        assertThat(skrooge(request), equalTo(Response(OK)))
    }

    @Test
    fun `POST with empty csv produces empty output file`() {
        val request = Request(POST, "/statements").body("2017,September,Tom,src/test/resources/empty-file.csv")
        skrooge(request)

        val decisionFile = File("output/decisions/2017-9-Tom-decisions-empty-file.csv")
        val fileContents = decisionFile.readLines()
        assertThat(fileContents.size, equalTo(0))
    }

    @Test
    fun `POST with one entry produces output file with one entry when recognised transaction`() {
        val request = Request(POST, "/statements").body("2017,September,Tom,src/test/resources/one-known-transaction.csv")
        skrooge(request)

        val decisionFile = File("output/decisions/2017-9-Tom-decisions-one-known-transaction.csv")
        val fileContents = decisionFile.readLines()
        assertThat(fileContents.size, equalTo(1))
        assertThat(fileContents[0], equalTo("2017-09-17,Pizza Union,5.50,Eats and drinks,Meals at work"))
    }

    @Test
    fun `POST with one entry returns HTTP See Other when unrecognised transaction`() {
        val request = Request(POST, "/statements").body("2017,September,Tom,src/test/resources/unknown-transaction.csv")
        assertThat(skrooge(request), equalTo(Response(SEE_OTHER).header("Location", "/unknown-transactions")))
    }
}