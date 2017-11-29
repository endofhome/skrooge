package uk.co.endofhome.skrooge

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Month

object BankTwoStatementCsvFormatter: StatementCsvFormatter {
    val baseInputPath = Paths.get("input")

    override operator fun invoke(inputFileName: Path): List<String> {
        val file = File(baseInputPath.toString() + File.separator + inputFileName.toString())

        return file.readLines()
                .drop(1)
                .map {
            val split = it.split(",")
            val date = dateFrom(split[0])
            val merchant = removeReferenceAndNormaliseCase(split[1])
            val value = negativeIfCredit(split[2], split[3], split[4], split[1])

            "$date,$merchant,$value"
        }
    }

    private fun removeReferenceAndNormaliseCase(line: String): String {
        return line
                .take(18)
                .split(" ")
                .map {
                    it.toLowerCase()
                      .capitalize()
                      .capitalizeMerchant()
                }
                .joinToString(" ")
                .trim()
    }

    private fun negativeIfCredit(transactionType: String, creditValue: String, debitValue: String, rawMerchant: String): String {
        return when {
            transactionType == "CREDIT" -> "-$creditValue"
            transactionType == "OTHER" && rawMerchant == "CREDIT" -> "-$creditValue"
            else -> debitValue
        }
    }

    private fun dateFrom(bankDate: String): String {
        val dateElements = bankDate.split("-")
        val day = dateElements[2]
        val month = Month.of(dateElements[1].toInt()).value
        val year = dateElements[0]
        return "$year-$month-$day"
    }
}