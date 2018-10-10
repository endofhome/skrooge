package uk.co.endofhome.skrooge.unknownmerchant

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.lens.BiDiLens
import org.http4k.lens.Query
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel
import org.http4k.template.view
import uk.co.endofhome.skrooge.decisions.Category

class UnknownMerchantHandler(private val renderer: TemplateRenderer, private val categories: List<Category>) {
    operator fun invoke(request: Request): Response {
        val currentMerchantLens: BiDiLens<Request, String> = Query.required("currentMerchant")
        val outstandingMerchantsLens: BiDiLens<Request, List<String>> = Query.multi.required("outstandingMerchants")
        val originalRequestBodyLens: BiDiLens<Request, String> = Query.required("originalRequestBody")
        val view = Body.view(renderer, ContentType.TEXT_HTML)
        val currentMerchant = Merchant(currentMerchantLens(request), categories)
        val outstandingMerchants: List<String> = outstandingMerchantsLens(request).flatMap { it.split(",") }
        val originalRequestBody = originalRequestBodyLens(request)
        val unknownMerchants = UnknownMerchants(currentMerchant, outstandingMerchants.joinToString(","), originalRequestBody)

        return Response(Status.OK).with(view of unknownMerchants)
    }
}

data class UnknownMerchants(val currentMerchant: Merchant, val outstandingMerchants: String, val originalRequestBody: String) : ViewModel
data class Merchant(val name: String, val categories: List<Category>?)
