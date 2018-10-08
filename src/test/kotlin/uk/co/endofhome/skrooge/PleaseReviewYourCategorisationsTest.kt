package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.oneeyedmen.okeydoke.junit.ApprovalsRule
import org.http4k.core.ContentType
import org.http4k.core.FormFile
import org.http4k.core.Method
import org.http4k.core.MultipartFormBody
import org.http4k.core.Request
import org.http4k.core.Status.Companion.OK
import org.junit.Rule
import org.junit.Test
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.categories.StubbedMappingWriter
import uk.co.endofhome.skrooge.decisions.StubbedDecisionReaderWriter
import java.nio.file.Paths

class PleaseReviewYourCategorisationsTest {

    @Rule
    @JvmField val approver: ApprovalsRule = ApprovalsRule.fileSystemRule("src/test/kotlin/approvals")

    private val categoryMappings = mutableListOf("Pizza Union,Some category,Some subcategory")
    private val categories = Categories("src/test/resources/test-schema.json", categoryMappings)
    private val mappingWriter = StubbedMappingWriter(categoryMappings)
    private val decisionReaderWriter = StubbedDecisionReaderWriter()
    private val skrooge = Skrooge(categories, mappingWriter, decisionReaderWriter, Paths.get("src/test/resources/budgets/")).routes()

    @Test
    fun `Statement categorisation report has required hidden fields`() {
        val formFile = FormFile(
                "don't care",
                ContentType.OCTET_STREAM,
                "".byteInputStream()
        )
        val body = MultipartFormBody().plus("year" to "2017")
                .plus("month" to "February")
                .plus("user" to "Someone")
                .plus("statement-name" to "one-known-merchant")
                .plus("statement" to formFile)
        val request = Request(Method.POST, "/statements")
                .header("content-type", "multipart/form-data; boundary=${body.boundary}")
                .body(body)

        val response = skrooge(request)

        assertThat(response.status, equalTo(OK))
        approver.assertApproved(response.bodyString())
    }
}