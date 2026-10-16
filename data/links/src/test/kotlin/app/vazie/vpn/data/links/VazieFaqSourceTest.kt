package app.vazie.vpn.data.links

import app.vazie.vpn.core.model.VazieFaqLink
import app.vazie.vpn.core.model.VazieFaqText
import app.vazie.vpn.core.network.ApiBaseUrl
import app.vazie.vpn.core.network.SessionTokenProvider
import app.vazie.vpn.core.network.VazieApiClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** The FAQ endpoint on the wire: what is believed, question by question, and every failure that keeps what is shown. */
class VazieFaqSourceTest {

    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun `published questions are read in order from the public endpoint with no session`() = runBlocking<Unit> {
        val body = """{"items":[
            {"key":"what","question":{"ru":"Что?","en":"What?"},"answer":{"ru":"Первая\nвторая","en":"A"},"future":"ignored"},
            {"key":"refund","question":{"ru":"Возврат?"},"answer":{"ru":"Пишите."},"link":{"url":"/legal/refunds","label":{"ru":"Возвраты"}}}
        ]}"""
        val items = source { ok(body) }.fetch()!!.items

        assertEquals(listOf("what", "refund"), items.map { it.key })
        assertEquals(VazieFaqText("Первая\nвторая", "A"), items[0].answer)
        assertEquals(VazieFaqLink("/legal/refunds", VazieFaqText("Возвраты")), items[1].link)
        assertEquals("/api/v1/faq/vpn", requests.single().url.encodedPath)
        assertNull(requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `a question without Russian is dropped, a bad link is dropped and its question stays`() = runBlocking<Unit> {
        val body = """{"items":[
            {"key":"en_only","question":{"en":"Q"},"answer":{"ru":"A"}},
            {"key":"two_lines","question":{"ru":"Q\nQ"},"answer":{"ru":"A"}},
            {"key":"ok","question":{"ru":"Q"},"answer":{"ru":"A"},"link":{"url":"javascript:alert(1)","label":{"ru":"L"}}},
            {"key":"host","question":{"ru":"Q"},"answer":{"ru":"A"},"link":{"url":"//evil.example","label":{"ru":"L"}}}
        ]}"""
        val items = source { ok(body) }.fetch()!!.items

        assertEquals(listOf("ok", "host"), items.map { it.key })
        assertTrue(items.all { it.link == null })
    }

    @Test
    fun `an empty list is believed, a failure or nothing usable is nothing new`() = runBlocking<Unit> {
        assertEquals(emptyList(), source { ok("""{"items":[]}""") }.fetch()!!.items)
        assertNull(source { respond("""{"error":{"code":"NOT_ENABLED"}}""", HttpStatusCode.NotFound) }.fetch())
        for (body in listOf("not json", "{}", """{"items":[{"key":"x","question":{"en":"Q"},"answer":{"ru":"A"}}]}""")) {
            assertNull(source { ok(body) }.fetch(), body)
        }
        assertNull(source { throw java.io.IOException("offline") }.fetch())
    }

    private fun source(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = VazieFaqSource(
        api = VazieApiClient.create(
            engine = MockEngine { request ->
                requests += request
                handler(request)
            },
            baseUrl = ApiBaseUrl("https://vazie.example/api/v1"),
            tokens = SessionTokenProvider { null },
        ),
    )

    private fun MockRequestHandleScope.ok(body: String) = respond(body, HttpStatusCode.OK)
}
