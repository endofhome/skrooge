package uk.co.endofhome.skrooge

import java.io.File
import java.time.LocalDate
import java.time.Month

interface DecisionWriter {
    fun write(statementData: StatementData, decisions: List<Decision>)
    fun read(year: Int, month: Month): List<Decision>
    fun readForYearStarting(startDate: LocalDate): List<Decision>
}

class FileSystemDecisionWriter : DecisionWriter {
    override fun readForYearStarting(startDate: LocalDate): List<Decision> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

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

        println("files being counted for this month: ")
        monthFiles.forEach {
            println(it.name)
        }

        return monthFiles.flatMap {
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

class StubbedDecisionWriter : DecisionWriter {
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