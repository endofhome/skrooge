package uk.co.endofhome.skrooge

import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Path

object BankFiveStatementCsvFormatter : StatementCsvFormatter {

    override operator fun invoke(inputFileName: Path): List<String> {
        val file = File(baseInputPath().toString() + File.separator + inputFileName.toString())

        return file.readLines()
                .drop(1)
                .map {
            val split = it.split(",")
            val date = split[0].split("/").reversed().joinToString("-")
            val merchant = split[4].removeSurrounding("\"")
                    .toLowerCase()
                    .capitalizeMerchant()
                    .modifyIfSpecialMerchant()
            val value = calulateValue(split[5], split[6])

            "$date,$merchant,$value"
        }
    }

    private fun calulateValue(creditValue: String, debitValue: String): String {
        val doubleValue = (creditValue.zeroIfEmpty() - debitValue.zeroIfEmpty())
        return BigDecimal(doubleValue).setScale(2, RoundingMode.HALF_UP).toString()
    }

    private fun String.zeroIfEmpty(): Double {
        return this.let {
            when {
                it.isEmpty() -> 0.00
                else -> it.toDouble()
            }
        }
    }


}
