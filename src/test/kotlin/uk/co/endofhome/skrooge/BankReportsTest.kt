package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.should.shouldMatch
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasStatus
import org.junit.Test

class BankReportsTest {

    private val categories = CategoryHelpers.categories("src/test/resources/test-schema.json")
    private val categoryMappings = mutableListOf("Pizza Union,Some category,Some subcategory")
    private val skrooge = Skrooge(categories, categoryMappings).routes()

    @Test
    fun `Statement categorisation report has required hidden fields`() {
        val request = Request(Method.POST, "/statements").body("2016;January;Test;[input/normalised/2016-01_Test_Santander.csv]")

        val response = skrooge(request)

        response shouldMatch hasStatus(Status.OK)
        response shouldMatch hasBody(containsSubstring("<input type=\"hidden\" name=\"statement-data\" value=\"2016;January;Test;[Santander.csv]\">"))
    }
}