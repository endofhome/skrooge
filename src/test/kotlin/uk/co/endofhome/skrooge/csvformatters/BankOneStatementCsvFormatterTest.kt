package uk.co.endofhome.skrooge.csvformatters

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.oneeyedmen.okeydoke.junit.ApprovalsRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Paths

class BankOneStatementCsvFormatterTest : CsvFormatterTest() {

    @Rule @JvmField
    val approver: ApprovalsRule = ApprovalsRule.fileSystemRule("src/test/kotlin/approvals")

    private val bankName = System.getenv("BANK_ONE")
    private val merchantOne = System.getenv("MERCHANT_ONE")!!
    private val merchantTwo = System.getenv("MERCHANT_TWO")
    private val merchantThree = System.getenv("MERCHANT_THREE")
    private val merchantSix = System.getenv("MERCHANT_SIX")

    // You will need csv files in your 'input' directory. as well
    // as environment variables for bankName and merchants set up
    // in order to run these tests.

    @Test
    fun `can format one-line statement`() {
        val formattedStatement = BankOneStatementCsvFormatter(Paths.get("${bankName}_test_one_line.csv"))
        val expectedFormat = listOf("2017-10-15,$merchantOne,18.17")
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can format three-line statement`() {
        val formattedStatement = BankOneStatementCsvFormatter(Paths.get("${bankName}_test_three_lines.csv"))
        val expectedFormat =
                listOf(
                        "2017-10-15,$merchantOne,18.17",
                        "2017-10-14,$merchantTwo,3.00",
                        "2017-10-14,$merchantThree,100.00"
                )
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can handle negative values`() {
        val formattedStatement = BankOneStatementCsvFormatter(Paths.get("${bankName}_test_three_lines_negative.csv"))
        val expectedFormat =
                listOf(
                        "2017-10-15,$merchantOne,-11.17",
                        "2017-10-14,$merchantTwo,-2.00",
                        "2017-10-14,$merchantThree,-99.00"
                )
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can handle trailing commas`() {
        val formattedStatement = BankOneStatementCsvFormatter(Paths.get("${bankName}_test_trailing_comma.csv"))
        val expectedFormat =
                listOf(
                        "2017-10-15,$merchantOne,9.97"
                )
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can handle quoted strings with commas in merchant names and line totals`() {
        val formattedStatement = BankOneStatementCsvFormatter(Paths.get("${bankName}_test_quoted_amounts_with_commas.csv"))
        val expectedFormat =
            listOf(
                "2018-06-04,$merchantTwo,1933.21"
            )
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can handle additional templated text in merchant field`() {
        val formattedStatement = BankOneStatementCsvFormatter(Paths.get("${bankName}_test_with_additional_text_in_merchant_field.csv"))
        val expectedFormat =
            listOf(
                "2018-06-04,$merchantSix,33.21"
            )
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can format full statement`() {
        val formattedStatement = BankOneStatementCsvFormatter(Paths.get("${bankName}_test_full.csv"))

        approver.assertApproved(formattedStatement.joinToString(System.lineSeparator()))
    }
}