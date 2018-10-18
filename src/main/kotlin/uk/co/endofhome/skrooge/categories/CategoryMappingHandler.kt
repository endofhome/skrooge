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
import uk.co.endofhome.skrooge.statements.StatementMetadata
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.yearName

class CategoryMappingHandler(private val categoryMappings: MutableList<String>, private val mappingWriter: MappingWriter) {

    private val thisAintGonnaWork = "ain't gonna work"

    operator fun invoke(request: Request): Response {
        val newMappingName = "new-mapping"
        val newMappingLens = FormField.required(newMappingName)
        val remainingVendorsName = "remaining-vendors"
        val remainingVendorsLens = FormField.required(remainingVendorsName)
        val yearLens = FormField.required(yearName)
        val monthLens = FormField.required(StatementMetadata.monthName)
        val userLens = FormField.required(StatementMetadata.userName)
        val statementNameLens = FormField.required(StatementMetadata.statement)
        val statementFilePathKey = "statement-file-path"
        val statementPathLens = FormField.required(statementFilePathKey)
        val webForm = Body.webForm(
                Validator.Feedback,
                newMappingLens,
                remainingVendorsLens,
                yearLens,
                monthLens,
                userLens,
                statementNameLens,
                statementPathLens
        )
        val form = webForm.toLens().extract(request)
        val year = form.fields[yearName]?.firstOrNull()
        val month = form.fields[StatementMetadata.monthName]?.firstOrNull()
        val user = form.fields[StatementMetadata.userName]?.firstOrNull()
        val statementName = form.fields[StatementMetadata.statement]?.firstOrNull()
        val statementFilePath = form.fields[statementFilePathKey]?.firstOrNull()

        val newMapping = form.fields[newMappingName]?.firstOrNull()?.split(",")
        val remainingVendors = form.fields[remainingVendorsName]?.firstOrNull()?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

        if (newMapping != null && year != null && month != null && user != null && statementName != null && statementFilePath != null) {
            return newMapping.size.let {
                when {
                    it < 3 -> Response(BAD_REQUEST)
                    else -> {
                        val newMappingString = newMapping.joinToString(",")
                        mappingWriter.write(newMappingString)
                        categoryMappings.add(newMappingString)
                        when (remainingVendors.isEmpty()) {
                            true -> Response(Status.TEMPORARY_REDIRECT)
                                    .header("Location", statementsWithFilePath)
                                    .header("Method", Method.POST.name)
                            false -> {
                                val nextVendor = remainingVendors.first()
                                val carriedForwardVendors = remainingVendors.filterIndexed { index, _ -> index != 0 }
                                val uri = Uri.of(unknownMerchant)
                                        .query("currentMerchant", nextVendor)
                                        .query("outstandingMerchants", carriedForwardVendors.joinToString(","))
                                        .query("originalRequestBody", thisAintGonnaWork)
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