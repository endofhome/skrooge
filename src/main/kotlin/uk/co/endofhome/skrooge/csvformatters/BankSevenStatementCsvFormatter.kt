package uk.co.endofhome.skrooge.csvformatters

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.io.FileReader
import java.math.BigDecimal
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object BankSevenStatementCsvFormatter : StatementCsvFormatter {
    private const val createdField = "created"
    private const val merchantField = "description"
    private const val amountField = "amount"
    private val header = arrayOf(
        "id",
        createdField,
        amountField,
        "currency",
        "local_amount",
        "local_currency",
        "category",
        "emoji",
        merchantField,
        "address",
        "notes",
        "receipt"
    )

    override operator fun invoke(inputFileName: Path): List<String> {
        val file = File(baseInputPath().toString() + File.separator + inputFileName.toString())
        val reader = FileReader(file)
        val lines: List<CSVRecord> = CSVFormat.DEFAULT.withHeader(*header).withFirstRecordAsHeader().parse(reader).records.toList()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault())

        return lines.map {
            val date = formatter.format(Instant.parse(it.get(createdField)))
            val merchant = it.get(merchantField).toLowerCase().capitalizeMerchant().dropLast(17).trim().modifyIfSpecialMerchant()
            val value = BigDecimal(it.get(amountField)).negate().toPlainString()
            "$date,$merchant,$value"
        }
    }
}