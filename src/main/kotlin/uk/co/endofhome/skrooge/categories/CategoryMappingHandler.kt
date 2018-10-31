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
import uk.co.endofhome.skrooge.unknownmerchant.UnknownMerchantHandler.Companion.currentMerchantName
import java.time.format.TextStyle
import java.util.Locale

class CategoryMappingHandler(private val categoryMappings: MutableList<String>, private val mappingWriter: MappingWriter) {

    companion object {
        const val newMappingName = "new-mapping"
        const val remainingMerchantName = "remaining-merchant"
    }

    operator fun invoke(request: Request): Response {
        val newMappingLens = FormField.required(newMappingName)
        val remainingMerchantsLens = FormField.multi.optional(remainingMerchantName)
        val webForm = Body.webForm(
            Validator.Feedback,
            newMappingLens,
            remainingMerchantsLens
        ).toLens()
        val form = webForm.extract(request)
        val newMapping: List<String> = newMappingLens.extract(form).split(',')

        val remainingMerchants: Set<String> by lazy { remainingMerchantsLens.extract(form)?.toSet() ?: emptySet() }

        val statementForm: FormForNormalisedStatement by lazy {
            FormForNormalisedStatement.fromUrlEncoded(request)
        }

        return when {
            newMapping.isValid() -> {
                newMapping.add()
                when {
                    remainingMerchants.isEmpty() -> redirectToStatementsWIthFilePath()
                    else                         -> redirectToUnknownMerchant(statementForm, remainingMerchants)
                }
            }
            else -> Response(BAD_REQUEST)
        }
    }
    private fun List<String>.isValid() = this.size >= 3

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

    private fun redirectToUnknownMerchant(statementForm: FormForNormalisedStatement, remainingMerchants: Set<String>): Response {
        val (currentMerchant, carriedForwardMerchants) = remainingMerchants.partition { it == remainingMerchants.first() }
        val baseUri = Uri.of(unknownMerchant)
                .query(currentMerchantName, currentMerchant.single())
                .query(yearName, statementForm.statementMetadata.year.toString())
                .query(monthName, statementForm.statementMetadata.month.getDisplayName(TextStyle.FULL, Locale.UK))
                .query(userName, statementForm.statementMetadata.user)
                .query(statementName, statementForm.statementMetadata.statement)
                .query(statementFilePathKey, statementForm.file.path)
        val uri = when {
            carriedForwardMerchants.isNotEmpty() -> baseUri.query(remainingMerchantName, carriedForwardMerchants.joinToString(","))
            else                                 -> baseUri
        }
        return Response(Status.SEE_OTHER).header("Location", uri.toString())
    }
}