package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.Body
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.junit.Test

class SkroogeTest {
    val skrooge = App().routes()

    @Test
    fun `POST to statements endpoint with empty body returns 200`() {
        val request = Request(POST, "/statements").body(Body.EMPTY)
        assertThat(skrooge(request), equalTo(Response(OK)))
    }
}