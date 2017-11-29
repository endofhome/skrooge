package uk.co.endofhome.skrooge.scripts

import uk.co.endofhome.skrooge.*
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
