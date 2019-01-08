package uk.co.endofhome.skrooge.scripts

import uk.co.endofhome.skrooge.csvformatters.BankSixStatementCsvFormatter
import java.io.File
import java.nio.file.Paths
import java.time.Month

fun main(args: Array<String>) {

    val bankStatementFormatter = BankSixStatementCsvFormatter
    val bank = "lloyds"
    val accountType = "credit"
    val monthName = "november"
    val year = "2018"
    val statementFileName = "${bank}_${accountType}_${monthName}_$year.csv"

    val formattedStatement = bankStatementFormatter(Paths.get(statementFileName))
    formattedStatement.forEach {
        println(it)
    }

    approve(formattedStatement, statementFileName, "Sarah")
}

fun normalisedFilenameFor(statementFilename: String, username: String): String {
    val filenameParts = statementFilename.substringBefore('.').split('_')
    val bank = filenameParts[0]
    val accountType = filenameParts[1]
    val monthName = filenameParts[2]
    val year = filenameParts[3]

    val month = Month.valueOf(monthName.toUpperCase()).value.toString().padStart(2, '0')
    val user = username.capitalize()
    val bankdetails = bank.capitalize() + accountType.capitalize()

    val fileExtension = "csv"
    return "$year-${month}_${user}_$bankdetails.$fileExtension"
}

fun approve(normalisedStatement: List<String>, statementFilename: String, username: String) {
    val normalisedFilename = normalisedFilenameFor(statementFilename, username)

    val printWriter = File("input/normalised/$normalisedFilename").printWriter()
    normalisedStatement.forEach {
        printWriter.write(it + System.getProperty("line.separator"))
    }

    printWriter.close()

    println("\nWritten out approved statement: $normalisedFilename")
}
