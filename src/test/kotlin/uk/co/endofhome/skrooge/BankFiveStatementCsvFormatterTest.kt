package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test
import java.io.File
import java.io.File.separator
import java.nio.file.Paths

class BankFiveStatementCsvFormatterTest {
    private val bankName = System.getenv("BANK_FIVE").toLowerCase()
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
    fun `can format full statement`() {
        val formattedStatement = BankFiveStatementCsvFormatter(Paths.get("${bankName}_test_full.csv"))
        val expectedFile = File( BankFiveStatementCsvFormatter.baseInputPath().toString() + separator + "normalised" + separator + separator + "2017-05_Test_${bankName.capitalize()}.csv")
        val expected = expectedFile.readLines()

        assertThat(formattedStatement, equalTo(expected))
    }
}