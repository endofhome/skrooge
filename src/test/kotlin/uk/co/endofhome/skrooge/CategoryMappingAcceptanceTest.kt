package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.should.shouldMatch
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasHeader
import org.http4k.hamkrest.hasStatus
import org.junit.Before
import org.junit.Test
import java.io.File

class CategoryMappingAcceptanceTest {
    val categoryMappings = listOf("Edgeworld Records,Fun,Tom fun budget")
    val skrooge = Skrooge(categoryMappings).routes()

    @Test
    fun `POST to category-mapping endpoint with empty body returns HTTP Bad Request`() {
        val request = Request(POST, "/category-mapping").body(Body.EMPTY)
        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }

    @Test
    fun `POST to category-mapping endpoint with non-CSV content returns HTTP Bad Request`() {
        val request = Request(POST, "/category-mapping").body("Casbah Records;Established 1967 in our minds")
        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }
}