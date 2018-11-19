package uk.co.endofhome.skrooge

import org.http4k.core.Body
import org.http4k.core.Headers
import org.http4k.core.Method
import org.http4k.core.MultipartFormBody
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.routing.RoutingHttpHandler

class RedirectHelper(val skrooge: RoutingHttpHandler) {
    fun Request.handleAndFollowRedirect(): Response {
        val initialResponse = skrooge(this)
        return initialResponse.followRedirect(this)
    }

    fun Response.followRedirect(request: Request? = null): Response {
        val (redirectMethod, body, headers) = when (status.code) {
            307 -> request?.let { Triple(it.method, it.body, deriveHeaders(this, it)) }
                ?: throw RuntimeException("No request provided.")
            303 -> Triple(Method.GET, Body.EMPTY, emptyList())
            else -> throw RuntimeException("Don't care about any other status codes at the moment.")
        }
        val location = headerValues("Location").first()!!
        val redirectRequest = Request(redirectMethod, location)
            .body(body)
            .headers(headers)
        return skrooge(redirectRequest)
    }

    private fun deriveHeaders(response: Response, request: Request): Headers =
        if (response.body is MultipartFormBody) {
            val multipartFormBody = response.body as MultipartFormBody
            listOf("content-type" to "multipart/form-data; boundary=${multipartFormBody.boundary}")
        } else {
            request.headers
        }
}