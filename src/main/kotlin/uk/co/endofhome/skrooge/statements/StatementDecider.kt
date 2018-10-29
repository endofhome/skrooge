package uk.co.endofhome.skrooge.statements

import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.decisions.Decision
import uk.co.endofhome.skrooge.decisions.Line
import uk.co.endofhome.skrooge.decisions.SubCategory
import java.time.LocalDate

class StatementDecider(categoryMappings: List<String>) {
    private val mappings = categoryMappings.map {
        val (purchase, mainCategory, subCategory) = it.split(",")
        CategoryMapping(purchase, mainCategory, subCategory)
    }

    fun process(statementData: List<String>) = statementData.map { decide(it) }

    private fun decide(lineString: String): Decision {
        val (date, merchant, amount) = lineString.split(",")
        val (year, month, day) = date.split("-").map { it.toInt() }
        val line = Line(
            LocalDate.of(year, month, day),
            merchant,
            amount.toDouble()
        )

        val match = mappings.find { it.purchase.contains(line.merchant) }
        return when (match) {
            null -> Decision(line, null, null)
            else -> Decision(line, Category(match.mainCategory, emptyList()), SubCategory(match.subCategory))
        }
    }
}