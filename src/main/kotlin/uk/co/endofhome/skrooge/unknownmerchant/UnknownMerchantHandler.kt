package uk.co.endofhome.skrooge.unknownmerchant

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.lens.Query
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel
import org.http4k.template.view
import uk.co.endofhome.skrooge.categories.CategoryMappingHandler.Companion.remainingMerchantKey
import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.statements.FileMetadata.statementFilePathKey
import uk.co.endofhome.skrooge.statements.StatementMetadata

class UnknownMerchantHandler(private val renderer: TemplateRenderer, private val categories: List<Category>) {

    companion object {
        const val currentMerchantKey = "currentMerchant"
    }

    operator fun invoke(request: Request): Response {
        val view = Body.view(renderer, ContentType.TEXT_HTML)

        val currentMerchantLens = Query.required(currentMerchantKey)
        val remainingMerchantsLens = Query.multi.optional(remainingMerchantKey)
        val statementMetadataLenses = StatementMetadata.queryParameters()
        val statementPathLens = Query.required(statementFilePathKey)

        return try {
            val currentMerchant = Merchant(currentMerchantLens(request), categories)
            val remainingMerchants: Set<String> = remainingMerchantsLens(request)?.toSet() ?: emptySet()
            val (year, month, user, statementName) = statementMetadataLenses.map { it.extract(request) }
            val statementPath = statementPathLens(request)
            val unknownMerchants = UnknownMerchants(
                currentMerchant,
                remainingMerchants,
                year,
                month,
                user,
                statementName,
                statementPath
            )
            Response(OK).with(view of unknownMerchants)
        } catch (e: Exception) {
            Response(BAD_REQUEST)
        }
    }
}

data class UnknownMerchants(
        val currentMerchant: Merchant,
        val remainingMerchants: Set<String>,
        val year: String,
        val month: String,
        val user: String,
        val statementName: String,
        val statementFilePath: String
) : ViewModel
data class Merchant(val name: String, val categories: List<Category>?)
