package uk.co.endofhome.skrooge.csvformatters

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.io.FileReader
import java.nio.file.Path
import java.time.Month

object BankTwoStatementCsvFormatter: StatementCsvFormatter {
    private const val dateField = "Date"
    private const val descriptionField = "Description"
    private const val typeFIeld = "Type"
    private const val moneyOutField = " Money Out"
    private const val moneyInField = "Money In"
    private val header = arrayOf(dateField, descriptionField, typeFIeld, moneyInField, moneyOutField, " Balance")

    override operator fun invoke(inputFileName: Path): List<String> {
        val file = File(baseInputPath().toString() + File.separator + inputFileName.toString())
        val reader = FileReader(file)
        val lines: List<CSVRecord> = CSVFormat.DEFAULT.withHeader(*header).withFirstRecordAsHeader().parse(reader).records.toList()

        return lines.map {
            val date = dateFrom(it.get(dateField))
            val rawMerchant = it.get(descriptionField).trim()
            val merchant = rawMerchant.replace(",", "").removeReferenceAndNormaliseCase().modifyIfSpecialMerchant()
            val value = negativeIfCredit(it.get(typeFIeld), it.get(moneyInField), it.get(moneyOutField), rawMerchant)
            "$date,$merchant,$value"
        }
    }

    private fun String.removeReferenceAndNormaliseCase(): String =
        take(18)
            .split(" ")
            .map {
                it.toLowerCase()
                  .capitalize()
                  .capitalizeMerchant()
            }
            .joinToString(" ")
            .trim()

    private fun negativeIfCredit(transactionType: String, creditValue: String, debitValue: String, rawMerchant: String): String =
        when {
            transactionType == "CREDIT"                           -> "-$creditValue"
            transactionType == "OTHER" && rawMerchant == "CREDIT" -> "-$creditValue"
            else                                                  -> debitValue
        }

    private fun dateFrom(bankDate: String): String {
        val dateElements = bankDate.split("-")
        val day = dateElements[2]
        val month = Month.of(dateElements[1].toInt()).value
        val year = dateElements[0]
        return "$year-$month-$day"
    }
}