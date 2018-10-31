package uk.co.endofhome.skrooge.unknownmerchant

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.lens.Query
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel
import org.http4k.template.view
import uk.co.endofhome.skrooge.categories.CategoryMappingHandler.Companion.remainingMerchantName
import uk.co.endofhome.skrooge.decisions.Category
import uk.co.endofhome.skrooge.statements.FileMetadata.statementFilePathKey
import uk.co.endofhome.skrooge.statements.FileMetadata.statementName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.monthName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.userName
import uk.co.endofhome.skrooge.statements.StatementMetadata.Companion.yearName

class UnknownMerchantHandler(private val renderer: TemplateRenderer, private val categories: List<Category>) {

    companion object {
        const val currentMerchantName = "currentMerchant"
    }

    operator fun invoke(request: Request): Response {
        val view = Body.view(renderer, ContentType.TEXT_HTML)

        val currentMerchantLens = Query.required(currentMerchantName)
        val remainingMerchantsLens = Query.multi.optional(remainingMerchantName)
        val yearLens = Query.required(yearName)
        val monthLens = Query.required(monthName)
        val usernameLens = Query.required(userName)
        val statementNameLens = Query.required(statementName)
        val statementPathLens = Query.required(statementFilePathKey)

        val currentMerchant = Merchant(currentMerchantLens(request), categories)
        val remainingMerchants: List<String> = remainingMerchantsLens(request)?.flatMap { it.split(",") } ?: emptyList()
        val year = yearLens(request)
        val month = monthLens(request)
        val username = usernameLens(request)
        val statementName = statementNameLens(request)
        val statementPath = statementPathLens(request)
        val unknownMerchants = UnknownMerchants(currentMerchant, remainingMerchants, year, month, username, statementName, statementPath)

        return Response(Status.OK).with(view of unknownMerchants)
    }
}

data class UnknownMerchants(
        val currentMerchant: Merchant,
        val remainingMerchants: List<String>,
        val year: String,
        val month: String,
        val username: String,
        val statementName: String,
        val statementFilePath: String
) : ViewModel
data class Merchant(val name: String, val categories: List<Category>?)
