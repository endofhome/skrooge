package uk.co.endofhome.skrooge.csvformatters

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test
import java.io.File
import java.io.File.separator
import java.nio.file.Paths

class BankOneStatementCsvFormatterTest : CsvFormatterTest() {
    private val bankName = System.getenv("BANK_ONE")
    private val merchantOne = System.getenv("MERCHANT_ONE")!!
    private val merchantTwo = System.getenv("MERCHANT_TWO")
    private val merchantThree = System.getenv("MERCHANT_THREE")

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
    fun `can format full statement`() {
        val formattedStatement = BankOneStatementCsvFormatter(Paths.get("${bankName}_test_full.csv"))
        val expectedFile = File(BankOneStatementCsvFormatter.normalisedInputsPath().toString() + separator + "2017-05_Test_${bankName.capitalize()}.csv")
        val expected = expectedFile.readLines()

        assertThat(formattedStatement, equalTo(expected))
    }
}