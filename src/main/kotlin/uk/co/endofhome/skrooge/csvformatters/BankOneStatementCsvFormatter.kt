package uk.co.endofhome.skrooge.csvformatters

import java.io.File
import java.nio.file.Path
import java.time.Month

object BankOneStatementCsvFormatter: StatementCsvFormatter {

    override operator fun invoke(inputFileName: Path): List<String> {
        val file = File(baseInputPath().toString() + File.separator + inputFileName.toString())

        return file.readLines()
                .filterNot { it.contains("Payment") }
                .map {
            val sanitisedLine = removeTrailingCommasFrom(it)
            val split = sanitisedLine.split(",")
            val date = dateFrom(split[0])
            val sanitisedMerchant = sanitise(split[1])
            val value = split.last()

            "$date,$sanitisedMerchant,$value"
        }
    }

    private fun removeTrailingCommasFrom(line: String): String {
        return line.let { when {
                it.endsWith(",") -> line.removeSuffix(",")
                else -> line
            }
        }
    }

    private fun sanitise(merchant: String): String {
        val prefixRemoved = merchant.replace("\" ", "")
        return prefixRemoved.modifyIfSpecialMerchant()
    }

    private fun dateFrom(bankDate: String): String {
        val dateElements = bankDate.split(" ")
        val day = dateElements[0]
        val month = monthFrom(dateElements[1])?.value
        val year = "20" + dateElements[2]
        return "$year-$month-$day"
    }

    private fun monthFrom(shortName: String): Month? {
        val monthMap = mapOf(
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

        return monthMap[shortName]
    }
}