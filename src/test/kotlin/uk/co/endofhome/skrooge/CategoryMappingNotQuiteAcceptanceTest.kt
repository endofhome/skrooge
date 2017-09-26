package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import org.http4k.core.Request
import org.http4k.core.ContentType
import org.http4k.core.with
import org.http4k.core.Method.POST
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.body.form
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasStatus
import org.http4k.lens.Header
import org.junit.Ignore
import org.junit.Test

class CategoryMappingNotQuiteAcceptanceTest {
    val categoryMappings = listOf("Edgeworld Records,Fun,Tom fun budget")
    val mappingWriter = MockMappingWriter()
    val skrooge = Skrooge(categoryMappings, mappingWriter).routes()
    val helpers = TestHelpers(skrooge)

    @Test
    fun `POST to category-mapping endpoint with empty new-mapping field returns HTTP Bad Request`() {
        val request = Request(POST, "/category-mapping")
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("new-mapping", "")
                .form("remaining-vendors", "")
        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }

    @Test
    fun `POST to category-mapping endpoint with non-CSV content returns HTTP Bad Request`() {
        val request = Request(POST, "/category-mapping")
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("new-mapping", "Casbah Records;Established 1967 in our minds")
                .form("remaining-vendors", "")
        skrooge(request) shouldMatch hasStatus(BAD_REQUEST)
    }

    @Test
    fun `POST to category-mapping endpoint with good CSV content returns HTTP OK and writes new mapping`() {
        val request = Request(POST, "/category-mapping")
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("new-mapping", "Casbah Records,Fun,Tom fun budget")
                .form("remaining-vendors", "")
        skrooge(request) shouldMatch hasStatus(OK)
        assertThat(mappingWriter.read().last(), equalTo("Casbah Records,Fun,Tom fun budget"))
    }

    @Test
    fun `succesful POST to category-mapping redirects back to continue categorisation if necessary`() {
        val request = Request(POST, "/category-mapping")
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("new-mapping", "DIY Space for London,Fun,Tom fun budget")
                .form("remaining-vendors", "Another vendor")

        val followedResponse = helpers.followRedirectResponse(skrooge(request))

        assertThat(mappingWriter.read().last(), equalTo("DIY Space for London,Fun,Tom fun budget"))
        followedResponse shouldMatch hasStatus(OK)
        followedResponse shouldMatch hasBody(containsSubstring("You need to categorise some transactions."))
        followedResponse shouldMatch hasBody(containsSubstring("<h3>Another vendor</h3>"))
    }

    @Ignore("TODO but not important enough yet")
    @Test
    fun `when all categories have been mapped a monthly report is available for review`() {
        val request = Request(POST, "/category-mapping")
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("new-mapping", "Last new mapping,Fun,Tom fun budget")
                .form("remaining-vendors", "")

        val followedResponse = helpers.followRedirectResponse(skrooge(request))

        followedResponse shouldMatch hasStatus(OK)
        followedResponse shouldMatch hasBody(containsSubstring("Please review the categories chosen."))
        followedResponse shouldMatch hasBody(containsSubstring("<h3>Last new mapping</h3>"))
    }

    @Test
    fun `temporarily, when all categories have been mapped a confirmation page is shown`() {
        val request = Request(POST, "/category-mapping")
                .with(Header.Common.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("new-mapping", "Last new mapping,Fun,Tom fun budget")
                .form("remaining-vendors", "")

        val response = skrooge(request)

        response shouldMatch hasStatus(OK)
        response shouldMatch hasBody(containsSubstring("All new categories mapped. Please POST your data once again."))
    }
}