package uk.co.endofhome.skrooge.statements

import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.decisions.Decision
import uk.co.endofhome.skrooge.decisions.Line
import uk.co.endofhome.skrooge.decisions.SubCategory
import java.time.LocalDate

class StatementDecider(categoryMappings: List<String>) {
    private val mappings = categoryMappings.map {
        val mappingStrings = it.split(",")
        CategoryMapping(mappingStrings[0], mappingStrings[1], mappingStrings[2])
    }

    fun process(statementData: List<String>) = statementData.map { decide(it) }

    private fun decide(lineString: String): Decision {
        val lineEntries = lineString.split(",")
        val dateValues = lineEntries[0].split("-").map { it.toInt() }
        val line = Line(LocalDate.of(dateValues[0], dateValues[1], dateValues[2]), lineEntries[1], lineEntries[2].toDouble())

        val match = mappings.find { it.purchase.contains(line.merchant) }
        return when (match) {
            null -> {
                Decision(line, null, null)
            }
            else -> {
                Decision(line, Category(match.mainCategory, emptyList()), SubCategory(match.subCategory))
            }
        }
    }
}