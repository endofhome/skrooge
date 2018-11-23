package uk.co.endofhome.skrooge.csvformatters

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import uk.co.endofhome.skrooge.format
import java.io.File
import java.io.FileReader
import java.math.BigDecimal
import java.math.BigDecimal.ZERO
import java.nio.file.Path

object BankFiveStatementCsvFormatter : StatementCsvFormatter {
    private const val transactionDateField = "Transaction Date"
    private const val transactionDescriptionField = "Transaction Description"
    private const val debitAmountField = "Debit Amount"
    private const val creditAmountField = "Credit Amount"
    private val header = arrayOf(
        transactionDateField,
        "Transaction Type",
        "Sort Code",
        "Account Number",
        transactionDescriptionField,
        debitAmountField,
        creditAmountField,
        "Balance"
    )

    override operator fun invoke(inputFileName: Path): List<String> {
        val file = File(baseInputPath().toString() + File.separator + inputFileName.toString())
        val reader = FileReader(file)
        val lines: List<CSVRecord> = CSVFormat.DEFAULT.withHeader(*header).withFirstRecordAsHeader().parse(reader).records.toList()

        return lines.map {
            val date = it.get(transactionDateField).split("/").reversed().joinToString("-")
            val merchant = it.get(transactionDescriptionField).trim()
                .removeSurrounding("\"")
                .toLowerCase()
                .capitalizeMerchant()
                .modifyIfSpecialMerchant()
            val value = calculateValue(it.get(debitAmountField), it.get(creditAmountField))
            "$date,$merchant,$value"
        }
    }

    private fun calculateValue(debitValue: String, creditValue: String): String =
        (debitValue.toBigDecimalWithDefault(ZERO) - creditValue.toBigDecimalWithDefault(ZERO)).format().toString()

    private fun String.toBigDecimalWithDefault(default: BigDecimal): BigDecimal =
        if (isEmpty()) default else BigDecimal(this)
}
