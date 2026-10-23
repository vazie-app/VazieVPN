package app.vazie.vpn.data.links

import app.vazie.vpn.core.model.VazieLink
import app.vazie.vpn.core.model.VazieLinkSection
import app.vazie.vpn.core.model.VazieLocalizedText
import app.vazie.vpn.core.network.ApiBaseUrl
import app.vazie.vpn.core.network.SessionTokenProvider
import app.vazie.vpn.core.network.VazieApiClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

/** The links endpoint on the wire: what is believed, link by link, and every failure that keeps what is shown. */
class VazieLinksSourceTest {

    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun `published links are read in order from the public endpoint with no session`() = runBlocking<Unit> {
        val links = source { ok(BODY) }.fetch()!!.links

        assertEquals(
            listOf(
                VazieLink("behance", VazieLinkSection.PROJECT, "https://www.behance.net/gallery/1", VazieLocalizedText("Поддержать", "Support"), VazieLocalizedText("Кейс", "The case")),
                VazieLink("boosty", VazieLinkSection.SUPPORT, "https://boosty.to/vazie", VazieLocalizedText("Кофе", "Coffee"), action = VazieLocalizedText("Открыть", "Open")),
                VazieLink("mail", VazieLinkSection.PROJECT, "mailto:support@vazie.app", VazieLocalizedText("Почта", "Email")),
            ),
            links,
        )
        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/apps/vpn/links", request.url.encodedPath)
        assertNull(request.headers[HttpHeaders.Authorization], "the links are never an account's")
    }

    @Test
    fun `a link the app cannot show is dropped, and the rest stay`() = runBlocking<Unit> {
        val body = """{"links":[
            {"key":"ok","section":"AUTHOR","url":"https://t.me/vazovsky17","title":{"ru":"Т","en":"T"}},
            {"key":"http","section":"AUTHOR","url":"http://t.me/x","title":{"ru":"Т","en":"T"}},
            {"key":"intent","section":"AUTHOR","url":"intent://x#Intent;end","title":{"ru":"Т","en":"T"}},
            {"key":"section","section":"SIDEBAR","url":"https://t.me/x","title":{"ru":"Т","en":"T"}},
            {"key":"half","section":"AUTHOR","url":"https://t.me/x","title":{"ru":"Т"}},
            {"key":"blank","section":"AUTHOR","url":"https://t.me/x","title":{"ru":" ","en":"T"}},
            {"section":"AUTHOR","url":"https://t.me/x","title":{"ru":"Т","en":"T"}},
            {"key":"halfsub","section":"AUTHOR","url":"https://t.me/y","title":{"ru":"Т","en":"T"},"subtitle":{"en":"only"}}
        ]}"""

        val links = source { ok(body) }.fetch()!!.links

        assertEquals(listOf("ok", "halfsub"), links.map { it.key })
        assertNull(links.last().subtitle, "half a subtitle is no subtitle")
    }

    @Test
    fun `any failure, or nothing usable, is nothing new`() = runBlocking<Unit> {
        for (status in listOf(HttpStatusCode.NotFound, HttpStatusCode.InternalServerError, HttpStatusCode.TooManyRequests)) {
            assertNull(source { respond("""{"error":{"code":"NOT_ENABLED"}}""", status) }.fetch(), "$status")
        }
        for (body in listOf("not json", "{}", """{"links":[]}""", """{"links":[{"key":"x","section":"AUTHOR","url":"http://x","title":{"ru":"a","en":"b"}}]}""")) {
            assertNull(source { ok(body) }.fetch(), body)
        }
        assertNull(source { throw java.io.IOException("offline") }.fetch())
    }

    @Test
    fun `a slow backend is given up on`() = runBlocking<Unit> {
        val never = CompletableDeferred<Unit>()
        assertNull(source(timeoutMillis = 1) { never.await(); ok(BODY) }.fetch())
        never.complete(Unit)
    }

    private fun source(
        timeoutMillis: Long = 8_000,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = VazieLinksSource(
        api = VazieApiClient.create(
            engine = MockEngine { request ->
                requests += request
                handler(request)
            },
            baseUrl = ApiBaseUrl("https://vazie.example/api/v1"),
            tokens = SessionTokenProvider { null },
        ),
        timeoutMillis = timeoutMillis,
    )

    private fun MockRequestHandleScope.ok(body: String) = respond(body, HttpStatusCode.OK)

    private companion object {
        const val BODY = """{"links":[
            {"key":"behance","section":"PROJECT","url":"https://www.behance.net/gallery/1","title":{"ru":"Поддержать","en":"Support"},"subtitle":{"ru":"Кейс","en":"The case"}},
            {"key":"boosty","section":"SUPPORT","url":"https://boosty.to/vazie","title":{"ru":"Кофе","en":"Coffee"},"action":{"ru":"Открыть","en":"Open"},"future":"ignored"},
            {"key":"mail","section":"PROJECT","url":"mailto:support@vazie.app","title":{"ru":"Почта","en":"Email"}}
        ]}"""
    }
}
