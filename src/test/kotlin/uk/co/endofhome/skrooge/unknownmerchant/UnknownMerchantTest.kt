package uk.co.endofhome.skrooge.unknownmerchant

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.oneeyedmen.okeydoke.junit.ApprovalsRule
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.Rule
import org.junit.Test
import uk.co.endofhome.skrooge.Skrooge
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.unknownMerchant
import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.categories.CategoryMappingHandler.Companion.remainingMerchantName
import uk.co.endofhome.skrooge.decisions.StubbedDecisionReaderWriter
import uk.co.endofhome.skrooge.statements.FileMetadata.statementFilePathKey
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.FieldNames.MONTH
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.FieldNames.STATEMENT
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.FieldNames.USER
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.FieldNames.YEAR
import uk.co.endofhome.skrooge.unknownmerchant.UnknownMerchantHandler.Companion.currentMerchantName
import java.nio.file.Paths

class UnknownMerchantTest {


    @Rule
    @JvmField
    val approver: ApprovalsRule = ApprovalsRule.fileSystemRule("src/test/kotlin/approvals")

    private val categoryMappings = mutableListOf<String>()
    private val categories = Categories("src/test/resources/test-schema.json", categoryMappings)
    private val decisionReaderWriter = StubbedDecisionReaderWriter()
    private val skrooge = Skrooge(categories, decisionReaderWriter = decisionReaderWriter, budgetDirectory = Paths.get("src/test/resources/budgets/")).routes

    @Test
    fun `missing required query parameters returns HTTP Bad Request`() {
        val request = Request(Method.GET, unknownMerchant)
        assertThat(skrooge(request).status, equalTo(Status.BAD_REQUEST))
    }

    @Test
    fun `with required query parameters returns OK with correct html`() {
        val request = Request(Method.GET, unknownMerchant)
            .query(currentMerchantName, "Bob's Hardware")
            .query(remainingMerchantName, "Bert's Audio Gear")
            .query(remainingMerchantName, "Hubert's bots")
            .query(YEAR.key, "2017")
            .query(MONTH.key, "November")
            .query(USER.key, "Milford")
            .query(STATEMENT.key, "Hipster Bank")
            .query(statementFilePathKey, "path")

        val response = skrooge(request)
        assertThat(response.status, equalTo(Status.OK))
        approver.assertApproved(response.bodyString())
    }
}