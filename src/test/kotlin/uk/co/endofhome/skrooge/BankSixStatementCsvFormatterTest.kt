package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test
import java.io.File
import java.nio.file.Paths

class BankSixStatementCsvFormatterTest {
    val bankName = System.getenv("BANK_SIX").toLowerCase()
    val merchantThirteen = System.getenv("MERCHANT_THIRTEEN")
    val merchantFourteen = System.getenv("MERCHANT_FOURTEEN")
    val merchantFifteen = System.getenv("MERCHANT_FIFTEEN")

    // You will need csv files in your 'input' directory. as well
    // as environment variables for bankName and merchants set up
    // in order to run these tests.

    @Test
    fun `can format one-line statement`() {
        val formattedStatement = BankSixStatementCsvFormatter(Paths.get("${bankName}_test_one_line.csv"))
        val expectedFormat =
                listOf(
                        "2017-05-22,${merchantFifteen},2.35"
                )
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can format three-line statement`() {
        val formattedStatement = BankSixStatementCsvFormatter(Paths.get("${bankName}_test_three_lines.csv"))
        val expectedFormat =
                listOf(
                        "2017-05-22,${merchantFifteen},2.35",
                        "2017-05-19,${merchantThirteen},-645.50",
                        "2017-05-17,${merchantFourteen},75.68"
                )
        assertThat(formattedStatement, equalTo(expectedFormat))
    }

    @Test
    fun `can format full statement`() {
        val formattedStatement = BankSixStatementCsvFormatter(Paths.get("${bankName}_test_full.csv"))
        val expectedFile = File(BankSixStatementCsvFormatter.baseInputPath.toString() + File.separator + "processed" + File.separator + "2017-05_Test_${bankName.capitalize()}.csv")
        val expected = expectedFile.readLines()

        assertThat(formattedStatement, equalTo(expected))
    }
}