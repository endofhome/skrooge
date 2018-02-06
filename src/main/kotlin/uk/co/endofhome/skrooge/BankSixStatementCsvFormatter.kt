package uk.co.endofhome.skrooge

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

object BankSixStatementCsvFormatter : StatementCsvFormatter {
    val baseInputPath = Paths.get("input")

    override operator fun invoke(inputFileName: Path): List<String> {
        val file = File(baseInputPath.toString() + File.separator + inputFileName.toString())

        return file.readLines()
                .drop(1)
                .map {
            val unquoted = it.removeQuotedCommas()
            val split = unquoted.split(",")
            val date = split[0].split("/").reversed().joinToString("-")
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