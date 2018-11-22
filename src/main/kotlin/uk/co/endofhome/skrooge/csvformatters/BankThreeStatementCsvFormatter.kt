package uk.co.endofhome.skrooge.csvformatters

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.io.FileReader
import java.nio.file.Path

object BankThreeStatementCsvFormatter: StatementCsvFormatter {
    private const val postingDateField = " Posting Date"
    private const val merchantField = " Merchant"
    private const val billingAmountField = " Billing Amount"
    private val header = arrayOf(" Transaction Date", postingDateField, billingAmountField, merchantField, " Merchant City ", " Merchant State ", " Merchant Zip ", " Reference Number ", " Debit/Credit Flag ", " SICMCC Code")

    override operator fun invoke(inputFileName: Path): List<String> {
        val file = File(baseInputPath().toString() + File.separator + inputFileName.toString())
        val reader = FileReader(file)
        val lines: List<CSVRecord> = CSVFormat.DEFAULT.withHeader(*header).withFirstRecordAsHeader().parse(reader).records.toList()

        return lines.filter {
            it.isNotPayment()
        }.map {
            val date = it.get(postingDateField).split("/").reversed().joinToString("-")
            val merchant = it.get(merchantField).trim().replace(",", "").sanitise().modifyIfSpecialMerchant()
            val value = it.get(billingAmountField).replace("£", "")
            "$date,$merchant,$value"
        }
    }

    private fun CSVRecord.isNotPayment(): Boolean = this.get(merchantField).contains("EFT PAYMENT").not()

    private fun String.sanitise() = removeSurrounding("\"").toLowerCase().capitalizeMerchant()
}
