package uk.co.endofhome.skrooge

import com.oneeyedmen.okeydoke.junit.ApprovalsRule
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.index
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.categories.StubbedMappingWriter
import uk.co.endofhome.skrooge.decisions.StubbedDecisionReaderWriter
import java.nio.file.Paths

class IndexTest {

    @Rule @JvmField
    val approver: ApprovalsRule = ApprovalsRule.fileSystemRule("src/test/kotlin/approvals")

    private val categoryMappings = mutableListOf<String>()
    private val categories = Categories("src/test/resources/test-schema.json", categoryMappings)
    private val mappingWriter = StubbedMappingWriter()
    private val decisionReaderWriter = StubbedDecisionReaderWriter()
    private val testBudgetDirectory = Paths.get("src/test/resources/budgets/")
    private val skrooge = Skrooge(categories, mappingWriter, decisionReaderWriter, testBudgetDirectory).routes

    @Ignore("Ignoring as some values are sensitive and provided by env vars")
    @Test
    fun `renders index page correctly`() {
        val indexRequest = Request(GET, index)
        val response = skrooge(indexRequest)

        approver.assertApproved(response)
    }
}