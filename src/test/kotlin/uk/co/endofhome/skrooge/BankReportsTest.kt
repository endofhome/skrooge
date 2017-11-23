package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.should.shouldMatch
import org.http4k.core.*
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasStatus
import org.junit.Test

class BankReportsTest {

    val skrooge = Skrooge().routes()

    @Test
    fun `Statement categorisation report has required hidden fields`() {
        val request = Request(Method.POST, "/statements").body("2016;January;Fred;[input/2016-01_Fred_Santander.csv]")
        skrooge(request) shouldMatch hasStatus(Status.OK)
        skrooge(request) shouldMatch hasBody(containsSubstring("<input type=\"hidden\" name=\"statement-data\" value=\"2016;January;Fred;[Santander.csv]\">"))
    }
}