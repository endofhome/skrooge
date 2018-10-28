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

        if (newMapping != null) {
            return newMapping.size.let {
                when {
                    it < 3 -> Response(BAD_REQUEST)
                    else -> {
                        val newMappingString = newMapping.joinToString(",")
                        mappingWriter.write(newMappingString)
                        categoryMappings.add(newMappingString)
                        when (remainingMerchants.isEmpty()) {
                            true -> Response(Status.TEMPORARY_REDIRECT)
                                    .header("Location", statementsWithFilePath)
                                    .header("Method", Method.POST.name)
                            false -> {
                                val nextVendor = remainingMerchants.first()
                                val carriedForwardMerchants = remainingMerchants.filterIndexed { index, _ -> index != 0 }
                                val uri = Uri.of(unknownMerchant)
                                        .query("currentMerchant", nextVendor)
                                        .query("outstandingMerchants", carriedForwardMerchants.joinToString(","))
                                        .query(yearName, statementForm.statementMetadata.year.toString())
                                        .query(monthName, statementForm.statementMetadata.month.getDisplayName(TextStyle.FULL, Locale.UK))
                                        .query(userName, statementForm.statementMetadata.user)
                                        .query(statementName, statementForm.statementMetadata.statement)
                                        .query(statementFilePathKey, statementForm.file.path)
                                Response(Status.SEE_OTHER).header("Location", uri.toString())
                            }
                        }
                    }
                }
            }

        } else {
            return Response(BAD_REQUEST)
        }


    }
}