package uk.co.endofhome.skrooge.csvformatters

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test
import java.io.File
import java.io.File.separator
import java.nio.file.Paths

class BankSixStatementCsvFormatterTest : CsvFormatterTest() {
    private val bankName = System.getenv("BANK_SIX")
    private val merchantThirteen = System.getenv("MERCHANT_THIRTEEN")!!
    private val merchantFourteen = System.getenv("MERCHANT_FOURTEEN")
    private val merchantFifteen = System.getenv("MERCHANT_FIFTEEN")

    // You will need csv files in your 'input' directory. as well
    // as environment variables for bankName and merchants set up
    // in order to run these tests.

    @Test
    fun `can format one-line statement`() {
        val formattedStatement = BankSixStatementCsvFormatter(Paths.get("${bankName}_test_one_line.csv"))
        val expectedFormat =
                listOf(
                        "2017-05-23,$merchantFifteen,2.35"
                )
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can format three-line statement`() {
        val formattedStatement = BankSixStatementCsvFormatter(Paths.get("${bankName}_test_three_lines.csv"))
        val expectedFormat =
                listOf(
                        "2017-05-23,$merchantFifteen,2.35",
                        "2017-05-19,$merchantThirteen,-645.50",
                        "2017-05-18,$merchantFourteen,75.68"
                )
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can format statement with quoted lines`() {
        val formattedStatement = BankSixStatementCsvFormatter(Paths.get("${bankName}_quoted_line.csv"))
        val expectedFormat =
                listOf(
                        "2017-12-13,Bob,23.20"
                )
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can format full statement`() {
        val formattedStatement = BankSixStatementCsvFormatter(Paths.get("${bankName}_test_full.csv"))
        val expectedFile = File(BankSixStatementCsvFormatter.normalisedInputsPath().toString() + separator + "2017-05_Test_${bankName.capitalize()}.csv")
        val expected = expectedFile.readLines()

        formattedStatement.forEach { println(it) }

        assertThat(formattedStatement, equalTo(expected))
    }
}