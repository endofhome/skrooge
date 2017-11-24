package uk.co.endofhome.skrooge

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

object BankThreeStatementCsvFormatter: StatementCsvFormatter {
    val baseInputPath = Paths.get("input")

    operator fun invoke(inputFilePath: Path): List<String> {
        val file = File(baseInputPath.toString() + File.separator + inputFilePath.toString())

        return file.readLines()
                .drop(1)
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
