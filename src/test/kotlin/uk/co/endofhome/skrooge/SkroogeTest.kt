package uk.co.endofhome.skrooge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.Body
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.NOT_FOUND
import org.junit.Test
import java.io.InputStream

class SkroogeTest {
    val skrooge = App().routes()

    @Test
    fun `POST to statements endpoint with empty body returns 404`() {
        val request = Request(POST, "/statements").body(Body.EMPTY)
        assertThat(skrooge(request), equalTo(Response(NOT_FOUND)))
    }

    @Test
    fun `POST to statements with a dummy string instead of form data returns 200`() {
        // Can't receive form data, so will read files from FS instead. Will use a string to represent form.
        val request = Request(POST, "/statements").body("year=2017, month=September, user=Tom, files=[empty-file.csv]")
        assertThat(skrooge(request), equalTo(Response(OK)))
    }
}