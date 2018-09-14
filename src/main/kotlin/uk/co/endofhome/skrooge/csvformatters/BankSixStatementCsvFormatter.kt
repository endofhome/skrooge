package uk.co.endofhome.skrooge.csvformatters

import java.io.File
import java.nio.file.Path

object BankSixStatementCsvFormatter : StatementCsvFormatter {

    override operator fun invoke(inputFileName: Path): List<String> {
        val file = File(baseInputPath().toString() + File.separator + inputFileName.toString())

        return file.readLines()
                .drop(1)
                .map {
            val unquoted = it.removeQuotedCommas()
            val split = unquoted.split(",")
            val date = split[1].split("/").reversed().joinToString("-")
            val merchant = sanitise(split[3])
            val value = split[4]

            "$date,$merchant,$value"
        }
    }

    private fun sanitise(merchant: String): String {
        val santisedMerchant = merchant.trim().toLowerCase().capitalizeMerchant()
        return santisedMerchant.modifyIfSpecialMerchant()
    }

    private fun String.removeQuotedCommas(): String {
        val split = this.split("\"")
        return if (split.size == 3) {
            val quotedMerchant = this.substringAfter('"').substringBefore('"').trim().removeSurrounding("\"")
            val unquotedMerchant = quotedMerchant.substringBefore(",")
            "${split[0]}$unquotedMerchant${split[2]}"
        } else {
            this
        }
    }
}