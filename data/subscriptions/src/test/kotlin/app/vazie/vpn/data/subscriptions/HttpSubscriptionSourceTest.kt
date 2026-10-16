package app.vazie.vpn.data.subscriptions

import app.vazie.vpn.config.subscription.SubscriptionFailure
import app.vazie.vpn.config.subscription.SubscriptionFetch
import app.vazie.vpn.config.subscription.SubscriptionLink
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

/** The subscription request on the wire: what is asked, of whom, and every way it can come to nothing. */
class HttpSubscriptionSourceTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val link = SubscriptionLink.parse("https://sub.example.net:8443/list/not-a-real-token?fmt=1")!!

    @Test
    fun `the list is asked for at the address given, with nothing of Vazie's attached`() = runBlocking<Unit> {
        val fetch = source { respond("the list") }.fetch(link)

        assertEquals("the list", assertIs<SubscriptionFetch.Fetched>(fetch).body.expose())
        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("https://sub.example.net:8443/list/not-a-real-token?fmt=1", request.url.toString())
        assertNull(request.headers[HttpHeaders.Authorization], "a session reached someone else's server")
        assertNull(request.headers[HttpHeaders.Cookie])
        assertEquals("VazieVPN", request.headers[HttpHeaders.UserAgent])
    }

    @Test
    fun `an answer that is not a success is a refusal`() = runBlocking<Unit> {
        for (status in listOf(HttpStatusCode.NotFound, HttpStatusCode.Forbidden, HttpStatusCode.InternalServerError)) {
            assertEquals(SubscriptionFailure.REFUSED, failure(source { respond("no", status) }.fetch(link)), "$status")
        }
    }

    @Test
    fun `no answer at all is unreachable`() = runBlocking<Unit> {
        assertEquals(SubscriptionFailure.UNREACHABLE, failure(source { throw IOException("no route") }.fetch(link)))
    }

    @Test
    fun `an answer too large to be a list is not read`() = runBlocking<Unit> {
        val huge = "x".repeat(1024 * 1024 + 1)

        assertEquals(SubscriptionFailure.TOO_LARGE, failure(source { respond(huge) }.fetch(link)))
    }

    private fun failure(fetch: SubscriptionFetch) = assertIs<SubscriptionFetch.Failed>(fetch).reason

    private fun source(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpSubscriptionSource.create(
            MockEngine { request ->
                requests += request
                handler(request)
            },
        )
}
