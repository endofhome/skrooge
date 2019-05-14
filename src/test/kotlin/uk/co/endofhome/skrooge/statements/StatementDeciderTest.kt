package uk.co.endofhome.skrooge.statements

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isA
import org.junit.Test
import uk.co.endofhome.skrooge.decisions.DecisionState

class StatementDeciderTest {
    @Test
    fun `purchases are categorised if a known mapping exists as a substring of the merchant name`() {
        val categoryMappings = listOf(
            CategoryMapping("Amzn Mktp Uk", "some category", "some subcategory"),
            CategoryMapping("Amazon", "some category", "some subcategory")
        )

        val testStatement = listOf(
            "2019-1-1,Amzn Mktp Uk*Mt9Vu7P54m,1.99",
            "2019-1-1,Amzn Mktp Uk*Mt8Vy1Pa3,1.99",
            "2019-1-1,Amazon.co.uk*M850K8Q62,1.99"
        )
        val result = StatementDecider(categoryMappings).process(testStatement)

        result.forEach {
            assertThat(it, isA<DecisionState.Decision>())
        }
    }
}