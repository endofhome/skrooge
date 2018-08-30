package uk.co.endofhome.skrooge.scripts

import uk.co.endofhome.skrooge.csvformatters.BankFiveStatementCsvFormatter
import uk.co.endofhome.skrooge.csvformatters.BankOneStatementCsvFormatter
import uk.co.endofhome.skrooge.csvformatters.BankSixStatementCsvFormatter
import uk.co.endofhome.skrooge.csvformatters.BankThreeStatementCsvFormatter
import uk.co.endofhome.skrooge.csvformatters.BankTwoStatementCsvFormatter
import java.nio.file.Paths

fun main(args: Array<String>) {

    val bank = ""
    val statementFileName = ""

    val formattedStatement = formatterMap()[bank]!!.invoke(Paths.get(statementFileName))
    formattedStatement.forEach {
        println(it)
    }
}

fun formatterMap() = mapOf(
        "BANK_ONE" to BankOneStatementCsvFormatter,
        "BANK_TWO" to BankTwoStatementCsvFormatter,
        "BANK_THREE" to BankThreeStatementCsvFormatter,
        "BANK_FIVE" to BankFiveStatementCsvFormatter,
        "BANK_SIX" to BankSixStatementCsvFormatter
)