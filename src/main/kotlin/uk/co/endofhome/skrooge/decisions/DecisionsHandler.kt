package uk.co.endofhome.skrooge.decisions

import org.http4k.core.Body
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.Validator
import org.http4k.lens.webForm
import uk.co.endofhome.skrooge.statements.StatementData
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.monthName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.statement
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.userName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.yearName
import java.time.LocalDate
import java.time.Month
import java.time.Year

class DecisionsHandler(private val decisionReaderWriter: DecisionReaderWriter, val categories: List<Category>) {
    operator fun invoke(request: Request): Response {
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

        val year = form.fields[yearName]?.firstOrNull()
        val month = form.fields[monthName]?.firstOrNull()
        val user = form.fields[userName]?.firstOrNull()
        val statement = form.fields[statement]?.firstOrNull()

        if (year != null && month != null && user != null && statement != null) {
            val statementData = StatementData(Year.parse(year), Month.valueOf(month.toUpperCase()), user, statement)
            decisionReaderWriter.write(statementData, decisions)

            return Response(Status.CREATED)
        } else {
            throw IllegalStateException("""Form fields cannot be null, but were:
                            |year: $year
                            |month: $month
                            |user: $user
                            |statement: $statement
            """.trimIndent())
        }
    }
}
