package uk.co.endofhome.skrooge

import org.http4k.core.Body
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.Validator
import org.http4k.lens.webForm
import java.time.LocalDate
import java.time.Month
import java.time.Year

class ReportCategorisations(private val decisionReaderWriter: DecisionReaderWriter, val categories: List<Category>) {
    fun confirm(request: Request): Response {
        val webForm = Body.webForm(Validator.Strict)
        val form = webForm.toLens().extract(request)
        val decisionsStrings: List<String>? = form.fields["decisions"]
        val decisionsSplit: List<List<String>>? = decisionsStrings?.map { it.substring(1, it.lastIndex).split(", ") }
        val decisions: List<Decision> = decisionsSplit!!.flatMap { decisionLine ->
            decisionLine.map { it.split(",") }.map {
                val dateParts = it[0].split("/")
                val day = Integer.valueOf(dateParts[0])
                val month = Month.of(Integer.valueOf(dateParts[1]))
                val year = Integer.valueOf(dateParts[2])
                val date = LocalDate.of(year, month, day)
                val merchant = it[1]
                val amount = it[2].toDouble()
                val category = it[3]
                val subCategory = it[4]
                Decision(Line(date, merchant, amount), Category(category, categories.find { it.title == category }!!.subcategories), SubCategory(subCategory))
            }
        }

        val statementDataSplit: List<String> = form.fields["statement-data"]!![0].split(";")
        val year = Year.parse(statementDataSplit[0])
        val month = Month.valueOf(statementDataSplit[1].toUpperCase())
        val user = statementDataSplit[2]
        val statement = statementDataSplit[3]
        val statementData = StatementData(year, month, user, statement)
        decisionReaderWriter.write(statementData, decisions)

        return Response(Status.CREATED)
    }
}
