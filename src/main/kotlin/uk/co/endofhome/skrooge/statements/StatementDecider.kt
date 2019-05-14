package uk.co.endofhome.skrooge.statements

import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.decisions.DecisionState
import uk.co.endofhome.skrooge.decisions.DecisionState.Decision
import uk.co.endofhome.skrooge.decisions.DecisionState.DecisionRequired
import uk.co.endofhome.skrooge.decisions.Line
import uk.co.endofhome.skrooge.decisions.SubCategory
import java.math.BigDecimal
import java.time.LocalDate

class StatementDecider(val categoryMappings: List<CategoryMapping>) {

    fun process(statementData: List<String>) = statementData.map { decide(it) }

    private fun decide(lineString: String): DecisionState {
        val (date, merchant, amount) = lineString.split(",")
        val (year, month, day) = date.split("-").map { it.toInt() }
        val line = Line(
            LocalDate.of(year, month, day),
            merchant,
            BigDecimal(amount)
        )

        val match = categoryMappings.find { line.merchant.contains(it.merchant) }
        return when (match) {
            null -> DecisionRequired(line)
            else -> Decision(line, SubCategory(match.subCategory, Category(match.mainCategory)))
        }
    }
}
