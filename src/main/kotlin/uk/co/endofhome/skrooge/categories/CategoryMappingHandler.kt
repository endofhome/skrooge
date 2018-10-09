package uk.co.endofhome.skrooge.categories

import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.query
import org.http4k.lens.BiDiBodyLens
import org.http4k.lens.FormField
import org.http4k.lens.Validator
import org.http4k.lens.WebForm
import org.http4k.lens.webForm
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.statements
import uk.co.endofhome.skrooge.Skrooge.RouteDefinitions.unknownMerchant

class CategoryMappingHandler(private val categoryMappings: MutableList<String>, private val mappingWriter: MappingWriter) {
    fun addCategoryMapping(request: Request): Response {
        val newMappingLens = FormField.required("new-mapping")
        val remainingVendorsLens = FormField.required("remaining-vendors")
        val originalRequestBodyLens = FormField.required("originalRequestBody")
        val webForm: BiDiBodyLens<WebForm> = Body.webForm(Validator.Strict, newMappingLens, remainingVendorsLens).toLens()
        val newMapping = newMappingLens.extract(webForm(request)).split(",")
        val remainingVendors: List<String> = remainingVendorsLens.extract(webForm(request)).split(",").filter { it.isNotBlank() }
        val originalRequestBody = Body(originalRequestBodyLens.extract(webForm(request)))

        return newMapping.size.let {
            when {
                it < 3 -> Response(Status.BAD_REQUEST)
                else -> {
                    val newMappingString = newMapping.joinToString(",")
                    mappingWriter.write(newMappingString)
                    categoryMappings.add(newMappingString)
                    when (remainingVendors.isEmpty()) {
                        true -> Response(Status.TEMPORARY_REDIRECT)
                                .header("Location", statements)
                                .header("Method", Method.POST.name)
                                .body(originalRequestBody)
                        false -> {
                            val nextVendor = remainingVendors.first()
                            val carriedForwardVendors = remainingVendors.filterIndexed { index, _ -> index != 0 }
                            val uri = Uri.of(unknownMerchant)
                                    .query("currentMerchant", nextVendor)
                                    .query("outstandingMerchants", carriedForwardVendors.joinToString(","))
                                    .query("originalRequestBody", originalRequestBody.toString())
                            Response(Status.SEE_OTHER).header("Location", uri.toString())
                        }
                    }
                }
            }
        }
    }
}