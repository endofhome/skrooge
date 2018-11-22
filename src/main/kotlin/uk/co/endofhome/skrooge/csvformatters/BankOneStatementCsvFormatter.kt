package uk.co.endofhome.skrooge.csvformatters

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.io.FileReader
import java.nio.file.Path
import java.time.Month

object BankOneStatementCsvFormatter: StatementCsvFormatter {
    private val monthMap = mapOf(
        "Jan" to Month.JANUARY,
        "Feb" to Month.FEBRUARY,
        "Mar" to Month.MARCH,
        "Apr" to Month.APRIL,
        "May" to Month.MAY,
        "Jun" to Month.JUNE,
        "Jul" to Month.JULY,
        "Aug" to Month.AUGUST,
        "Sep" to Month.SEPTEMBER,
        "Oct" to Month.OCTOBER,
        "Nov" to Month.NOVEMBER,
        "Dec" to Month.DECEMBER
    )

    override operator fun invoke(inputFileName: Path): List<String> {
        val file = File(baseInputPath().toString() + File.separator + inputFileName.toString())
        val reader = FileReader(file)
        val lines: List<CSVRecord> = CSVFormat.DEFAULT.parse(reader).records.toList()

        return lines.filter {
            it.isNotPayment()
        }.map {
            val date = dateFrom(it.get(0))
            val merchant = it.get(1).trim().replace(",", "").removeAdditionalTemplateText().modifyIfSpecialMerchant()
            val value = it.last().ifBlank { it.get(it.size() - 2) }.replace(",", "")
            "$date,$merchant,$value"
        }
    }

    private fun CSVRecord.isNotPayment(): Boolean = this.get(1).contains("Payment").not()

    private fun String.removeAdditionalTemplateText(): String {
        val templateText = " POUND STERLING GREAT BRITAIN"

        return if (this.endsWith(templateText)) {
            this.replace(templateText, "").split(".").dropLast(1).joinToString(".").dropLastWhile { it.isDigit() }
        } else {
            this
        }
    }

    private fun dateFrom(bankDate: String): String {
        val dateElements = bankDate.split(" ")
        val day = dateElements[0]
        val month = monthMap[dateElements[1]]?.value.toString().padStart(2, '0')
        val year = "20" + dateElements[2]
        return "$year-$month-$day"
    }
}
