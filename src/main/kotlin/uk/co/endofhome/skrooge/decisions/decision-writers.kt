package uk.co.endofhome.skrooge.decisions

import uk.co.endofhome.skrooge.categories.Categories
import uk.co.endofhome.skrooge.decisions.DecisionState.Decision
import uk.co.endofhome.skrooge.statements.StatementMetadata
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.Month
import java.time.Year

interface DecisionReaderWriter {
    fun write(statementMetadata: StatementMetadata, decisions: List<Decision>)
    fun read(year: Int, month: Month): List<Decision>
    fun readForYearStarting(startDate: LocalDate): List<Decision>
}

class FileSystemDecisionReaderReaderWriter(private val categories: Categories, private val outputPath: Path = Paths.get("output/decisions")) : DecisionReaderWriter {
    override fun write(statementMetadata: StatementMetadata, decisions: List<Decision>) {
        val year = statementMetadata.year.toString()
        val month = statementMetadata.month.value
        val user = statementMetadata.user
        val bank = statementMetadata.statement
        File("$outputPath/$year-$month-$user-decisions-$bank.csv").printWriter().use { out ->
            decisions.forEach {
                out.print("${it.line.date},${it.line.merchant},${it.line.amount},${it.subCategory.category.title},${it.subCategory.name}\n")
            }
        }
    }

    override fun read(year: Int, month: Month): List<Decision> =
        outputPath.toFile().listFiles()
            .filterNot { it.name == ".gitkeep" }
            .filter {
                val filenameSplit = it.name.split("-")
                filenameSplit[2] != "Test"
            }
            .filter { it.name.startsWith("$year-${month.value}") }
            .toDecisions()

    override fun readForYearStarting(startDate: LocalDate): List<Decision> {
        // so far this is weird because the day of the month is completely ignored.
        // probably easier to start this way, but query param should be simply yyyy-MM

        return outputPath.toFile()
                .listFiles()
                .filter {
                    val filenameSplit = it.name.split("-")
                    val year = Year.parse(filenameSplit[0])
                    val month = Month.of(filenameSplit[1].toInt())

                    filenameSplit[2] != "Test" &&
                    (year == Year.of(startDate.year)  && month >= startDate.month)
                            || (year == Year.of(startDate.year + 1)  && month < startDate.month)
                }.toDecisions()
    }

    private fun List<File>.toDecisions(): List<Decision> {
        println("files being counted for this period: ")
        this.forEach {
            println(it.name)
        }

        return this.flatMap { file ->
            file.readLines().map { rawLine ->
                val (date, merchant, amount, _, subCategoryName) = rawLine.split(",")
                val (year, month, day) = date.split("-").map { it.toInt() }
                val line = Line(LocalDate.of(year, month, day), merchant, amount.toDouble())

                val subCategory = categories.get(subCategoryName)
                Decision(line, subCategory)
            }
        }
    }
}

class StubbedDecisionReaderWriter : DecisionReaderWriter {
    val files: MutableList<Decision> = mutableListOf()

    override fun write(statementMetadata: StatementMetadata, decisions: List<Decision>) {
        decisions.forEach {
            files.add(it)
        }
    }

    override fun read(year: Int, month: Month) = files.filter { it.line.date.year == year && it.line.date.month == month }.toList()

    override fun readForYearStarting(startDate: LocalDate): List<Decision> =
            files.filter { it.line.date >= startDate && it.line.date < startDate.plusYears(1L) }
}

data class Line(val date: LocalDate, val merchant: String, val amount: Double)
data class Category(val title: String)
data class SubCategory(val name: String, val category: Category)

sealed class DecisionState {
    data class Decision(val line: Line, val subCategory: SubCategory): DecisionState()
    data class DecisionRequired(val line: Line): DecisionState()
}
