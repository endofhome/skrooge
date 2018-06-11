package uk.co.endofhome.skrooge.csvformatters

import java.io.File
import java.nio.file.Path

object BankThreeStatementCsvFormatter: StatementCsvFormatter {

    override operator fun invoke(inputFileName: Path): List<String> {
        val file = File(baseInputPath().toString() + File.separator + inputFileName.toString())

        return file.readLines()
                .drop(1)
                .filterNot { it.toLowerCase().contains("eft payment") }
                .map {
            val split = it.split(",")
            val date = split[1].split("/").reversed().joinToString("-")
            val merchant = sanitise(split[3])
            val value = split[2].replace("£", "")

            "$date,$merchant,$value"
        }
    }

    private fun sanitise(merchant: String): String {
        val santisedMerchant = merchant.removeSurrounding("\"").toLowerCase().capitalizeMerchant()
        return santisedMerchant.modifyIfSpecialMerchant()
    }
}
