package uk.co.endofhome.skrooge.csvformatters

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test
import java.io.File
import java.io.File.separator
import java.nio.file.Paths

class BankThreeStatementCsvFormatterTest : CsvFormatterTest() {
    private val bankName = System.getenv("BANK_THREE").toLowerCase()
    private val merchantSeven = System.getenv("MERCHANT_SEVEN")
    private val merchantEight = System.getenv("MERCHANT_EIGHT")
    private val merchantNine = System.getenv("MERCHANT_NINE")

    // You will need csv files in your 'input' directory. as well
    // as environment variables for bankName and merchants set up
    // in order to run these tests.

    @Test
    fun `can format one-line statement`() {
        val formattedStatement = BankThreeStatementCsvFormatter(Paths.get("${bankName}_test_one_line.csv"))
        val expectedFormat =
                listOf(
                        "2017-10-31,$merchantNine,12.00"
                )
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can format three-line statement`() {
        val formattedStatement = BankThreeStatementCsvFormatter(Paths.get("${bankName}_test_three_lines.csv"))
        val expectedFormat =
                listOf(
                        "2017-11-03,$merchantEight,0.17",
                        "2017-10-31,$merchantNine,12.00"
                )
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can format full statement`() {
        val formattedStatement = BankThreeStatementCsvFormatter(Paths.get("${bankName}_test_full.csv"))
        val expectedFile = File(BankTwoStatementCsvFormatter.normalisedInputsPath().toString() + separator + "2017-05_Test_${bankName.capitalize()}.csv")
        val expected = expectedFile.readLines()

        assertThat(formattedStatement, equalTo(expected))
    }
}