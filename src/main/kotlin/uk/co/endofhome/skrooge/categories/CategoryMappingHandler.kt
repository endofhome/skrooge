package uk.co.endofhome.skrooge.categories

import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Uri
import org.http4k.core.query
import org.http4k.lens.FormField
import org.http4k.lens.Validator
import org.http4k.lens.webForm
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.statementsWithFilePath
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.unknownMerchant
import uk.co.endofhome.skrooge.statements.FileMetadata.statementFilePathKey
import uk.co.endofhome.skrooge.statements.FileMetadata.statementName
import uk.co.endofhome.skrooge.statements.FormForNormalisedStatement
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.monthName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.userName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.yearName
import java.time.format.TextStyle
import java.util.Locale

class CategoryMappingHandler(private val categoryMappings: MutableList<String>, private val mappingWriter: MappingWriter) {

    operator fun invoke(request: Request): Response {
        val newMappingName = "new-mapping"
        val remainingMerchantsName = "remaining-merchants"
        val newMappingLens = FormField.required(newMappingName)
        val remainingMerchantsLens = FormField.required(remainingMerchantsName)
        val webForm = Body.webForm(
                Validator.Feedback,
                newMappingLens,
                remainingMerchantsLens
        ).toLens()
        val form = webForm.extract(request)
        val fields = form.fields

        val newMapping = fields[newMappingName]?.firstOrNull()
                                               ?.split(",")
        val remainingMerchants = fields[remainingMerchantsName]?.firstOrNull()
                                                               ?.split(",")
                                                               ?.filter { it.isNotBlank() }
                                                               ?: emptyList()

        val statementForm = FormForNormalisedStatement.fromUrlEncoded(request)

        return when {
            newMapping.isValid() -> {
                newMapping!!.add()
                when {
                    remainingMerchants.isEmpty() -> redirectToStatementsWIthFilePath()
                    else                         -> redirectToUnknownMerchant(statementForm, remainingMerchants)
                }
            }
            else -> Response(BAD_REQUEST)
        }
    }

    private fun List<String>?.isValid() = this != null && this.size >= 3

    private fun List<String>.add() {
        val newMappingString = this.joinToString(",")
        mappingWriter.write(newMappingString)
        categoryMappings.add(newMappingString)
    }

    private fun redirectToStatementsWIthFilePath(): Response {
        return Response(Status.TEMPORARY_REDIRECT)
                .header("Location", statementsWithFilePath)
                .header("Method", Method.POST.name)
    }

    private fun redirectToUnknownMerchant(statementForm: FormForNormalisedStatement, remainingMerchants: List<String>): Response {
        val nextVendor = remainingMerchants.first()
        val carriedForwardMerchants = remainingMerchants.drop(1)
        val uri = Uri.of(unknownMerchant)
                .query("currentMerchant", nextVendor)
                .query("remainingMerchants", carriedForwardMerchants.joinToString(","))
                .query(yearName, statementForm.statementMetadata.year.toString())
                .query(monthName, statementForm.statementMetadata.month.getDisplayName(TextStyle.FULL, Locale.UK))
                .query(userName, statementForm.statementMetadata.user)
                .query(statementName, statementForm.statementMetadata.statement)
                .query(statementFilePathKey, statementForm.file.path)
        return Response(Status.SEE_OTHER).header("Location", uri.toString())
    }
}