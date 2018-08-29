package uk.co.endofhome.skrooge

import java.io.File
import java.time.LocalDate
import java.time.Month
import java.time.Year

interface DecisionReaderWriter {
    fun write(statementData: StatementData, decisions: List<Decision>)
    fun read(year: Int, month: Month): List<Decision>
    fun readForYearStarting(startDate: LocalDate): List<Decision>
}

class FileSystemDecisionReaderReaderWriter : DecisionReaderWriter {
    private val decisionFilePath = "output/decisions"

    override fun write(statementData: StatementData, decisions: List<Decision>) {
        val year = statementData.year.toString()
        val month = statementData.month.value
        val username = statementData.username
        val bank = statementData.files[0].toString().split("_").last().substringBefore(".csv")
        File("$decisionFilePath/$year-$month-$username-decisions-$bank.csv").printWriter().use { out ->
            decisions.forEach {
                out.print("${it.line.date},${it.line.merchant},${it.line.amount},${it.category?.title},${it.subCategory?.name}\n")
            }
        }
    }

    override fun read(year: Int, month: Month): List<Decision> {
        val monthFiles = File(decisionFilePath).listFiles().filter { it.name.startsWith("$year-${month.value}") }

        return monthFiles.toDecisions()
    }

    override fun readForYearStarting(startDate: LocalDate): List<Decision> {
        // so far this is weird because the day of the month is completely ignored.
        // probably easier to start this way, but query param should be simply yyyy-MM

        val yearFiles = File(decisionFilePath)
                .listFiles()
                .filter {
                    val filenameSplit = it.name.split("-")
                    val year = Year.parse(filenameSplit[0])
                    val month = Month.of(filenameSplit[1].toInt())

                    (year == Year.of(startDate.year)  && month >= startDate.month)
                            || (year == Year.of(startDate.year + 1)  && month < startDate.month)
                }

        return yearFiles.toDecisions()
    }

    private fun List<File>.toDecisions(): List<Decision> {
        println("files being counted for this period: ")
        this.forEach {
            println(it.name)
        }

        return this.flatMap {
            it.readLines().map {
                val split = it.split(",")
                val dateValues = split[0].split("-").map { it.toInt() }
                val line = Line(LocalDate.of(dateValues[0], dateValues[1], dateValues[2]), split[1], split[2].toDouble())

                val category = CategoryHelpers.categories().find { it.title == split[3] }!!
                Decision(line, category, CategoryHelpers.subcategoriesFor(category.title).find { it.name == split[4] })
            }
        }
    }
}

class StubbedDecisionReaderWriter : DecisionReaderWriter {
    private val file: MutableList<Decision> = mutableListOf()

    override fun readForYearStarting(startDate: LocalDate): List<Decision> =
            file.filter { it.line.date >= startDate && it.line.date < startDate.plusYears(1L) }

    override fun write(statementData: StatementData, decisions: List<Decision>) {
        file.clear()
        decisions.forEach {
            file.add(it)
        }
    }

    override fun read(year: Int, month: Month) = file.toList()
}