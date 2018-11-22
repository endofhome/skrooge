package uk.co.endofhome.skrooge.csvformatters

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.io.FileReader
import java.nio.file.Path

object BankSixStatementCsvFormatter : StatementCsvFormatter {
    private const val dateEnteredField = "Date entered"
    private const val descriptionField = "Description"
    private const val amountField = "Amount"
    private val header = arrayOf("Date","Date entered","Reference", descriptionField, amountField,"")

    override operator fun invoke(inputFileName: Path): List<String> {
        val file = File(baseInputPath().toString() + File.separator + inputFileName.toString())
        val reader = FileReader(file)
        val lines: List<CSVRecord> = CSVFormat.DEFAULT.withHeader(*header).withFirstRecordAsHeader().parse(reader).records.toList()

        return lines.map {
            val date = it.get(dateEnteredField).split("/").reversed().joinToString("-")
            val merchant = it.get(descriptionField).sanitise().modifyIfSpecialMerchant()
            val value = it.get(amountField)
            "$date,$merchant,$value"
        }
    }

    private fun String.sanitise(): String = trim().toLowerCase().replace(',', ' ').capitalizeMerchant()
}