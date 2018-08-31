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

    private val categoryHelpers = CategoryHelpers("src/test/resources/test-schema.json")
    private val categoryMappings = mutableListOf("Pizza Union,Some category,Some subcategory")
    private val mappingWriter = StubbedMappingWriter(categoryMappings)
    private val decisionReaderWriter = StubbedDecisionReaderWriter()
    private val skrooge = Skrooge(categoryHelpers, categoryMappings, mappingWriter, decisionReaderWriter).routes()

    @Test
    fun `Statement categorisation report has required hidden fields`() {
        val request = Request(Method.POST, "/statements").body("2017;February;Someone;[src/test/resources/2017-02_Someone_one-known-merchant.csv]")

        val response = skrooge(request)

        response shouldMatch hasStatus(Status.OK)
        response shouldMatch hasBody(containsSubstring("<input type=\"hidden\" name=\"statement-data\" value=\"2017;February;Someone;[one-known-merchant.csv]\">"))
    }
}