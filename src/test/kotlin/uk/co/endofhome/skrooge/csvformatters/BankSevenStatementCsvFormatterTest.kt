package uk.co.endofhome.skrooge.csvformatters

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.oneeyedmen.okeydoke.junit.ApprovalsRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Paths

class BankSevenStatementCsvFormatterTest : CsvFormatterTest() {

    @Rule @JvmField
    val approver: ApprovalsRule = ApprovalsRule.fileSystemRule("src/test/kotlin/approvals")

    private val bankName = System.getenv("BANK_SEVEN")
    private val merchantOne = System.getenv("MERCHANT_ONE")
    private val merchantTwelve = System.getenv("MERCHANT_TWELVE")

    // You will need csv files in your 'input' directory. as well
    // as environment variables for bankName and merchants set up
    // in order to run these tests.

    @Test
    fun `can format two-line statement`() {
        val formattedStatement = BankSevenStatementCsvFormatter(Paths.get("${bankName}_test_two_lines.csv"))
        val expectedFormat =
                listOf(
                        "2018-11-04,$merchantOne,11.44",
                        "2018-11-07,$merchantTwelve,177.51"
                )
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can format full statement`() {
        val formattedStatement = BankSevenStatementCsvFormatter(Paths.get("${bankName}_test_full.csv"))

        approver.assertApproved(formattedStatement.joinToString(System.lineSeparator()))
    }
}