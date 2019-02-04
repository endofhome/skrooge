package uk.co.endofhome.skrooge.csvformatters

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.oneeyedmen.okeydoke.junit.ApprovalsRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Paths

class BankFiveStatementCsvFormatterTest : CsvFormatterTest() {

    @Rule @JvmField
    val approver: ApprovalsRule = ApprovalsRule.fileSystemRule("src/test/kotlin/approvals")

    private val bankName = System.getenv("BANK_FIVE").toLowerCase()
    private val merchantOne = System.getenv("MERCHANT_ONE")
    private val merchantTen = System.getenv("MERCHANT_TEN")
    private val merchantEleven = System.getenv("MERCHANT_ELEVEN")
    private val merchantTwelve = System.getenv("MERCHANT_TWELVE")

    // You will need csv files in your 'input' directory. as well
    // as environment variables for bankName and merchants set up
    // in order to run these tests.

    @Test
    fun `can format one-line statement`() {
        val formattedStatement = BankFiveStatementCsvFormatter(Paths.get("${bankName}_test_one_line.csv"))
        val expectedFormat =
                listOf(
                        "2017-05-15,$merchantTwelve,9.99"
                )
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can format three-line statement`() {
        val formattedStatement = BankFiveStatementCsvFormatter(Paths.get("${bankName}_test_three_lines.csv"))
        val expectedFormat =
                listOf(
                        "2017-05-22,$merchantTen,5.00",
                        "2017-05-22,$merchantEleven,-5.20",
                        "2017-05-15,$merchantTwelve,9.99"
                )
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can format 2018 style three-line statement`() {
        val formattedStatement = BankFiveStatementCsvFormatter(Paths.get("${bankName}_2019_test_three_lines.csv"))
        val expectedFormat =
            listOf(
                "2017-05-22,$merchantTen,5.00",
                "2017-05-22,$merchantEleven,-5.20",
                "2017-05-15,$merchantTwelve,9.99",
                "2017-05-16,$merchantOne,14.57",
                "2017-05-17,$merchantOne,6.55"
            )
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can format full pre-2018 style statement`() {
        val formattedStatement = BankFiveStatementCsvFormatter(Paths.get("${bankName}_test_full.csv"))

        approver.assertApproved(formattedStatement.joinToString(System.lineSeparator()))
    }
}