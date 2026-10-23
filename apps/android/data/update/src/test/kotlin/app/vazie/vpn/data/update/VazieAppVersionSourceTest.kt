package app.vazie.vpn.data.update

import app.vazie.vpn.core.network.ApiBaseUrl
import app.vazie.vpn.core.network.SessionTokenProvider
import app.vazie.vpn.core.network.VazieApiClient
import app.vazie.vpn.update.api.AppVersionPolicy
import app.vazie.vpn.update.api.VersionAnswer
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

/** The version endpoint on the wire: what is believed, and everything that is not — which blocks nobody. Real time,
 * not `runTest`'s virtual clock, because the source has a real timeout. */
class VazieAppVersionSourceTest {

    private val requests = mutableListOf<HttpRequestData>()

    @Test
    fun `a published version becomes a policy, read from the public endpoint with no session`() = runBlocking<Unit> {
        val answer = source { ok(BODY) }.fetch()

        val policy = assertIs<VersionAnswer.Published>(answer).policy
        assertEquals(AppVersionPolicy(42, "1.4.0", 39, "https://vazie.app/vpn", mapOf("ru" to "Обновите Vazie VPN, чтобы продолжить работу.", "en" to "Update Vazie VPN to continue.")), policy)
        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/apps/vpn/android/version", request.url.encodedPath)
        assertNull(request.headers[HttpHeaders.Authorization], "an update check is never an account's")
    }

    @Test
    fun `the message is optional`() = runBlocking<Unit> {
        val answer = source { ok("""{"latestVersionCode":5,"latestVersionName":"1.0.5","minimumSupportedVersionCode":5,"updateUrl":"https://vazie.app/vpn"}""") }.fetch()

        assertEquals(emptyMap(), assertIs<VersionAnswer.Published>(answer).policy.messages)
    }

    @Test
    fun `nothing published is its own answer`() = runBlocking<Unit> {
        val answer = source { error("NOT_ENABLED", HttpStatusCode.NotFound) }.fetch()

        assertEquals(VersionAnswer.NotPublished, answer)
    }

    @Test
    fun `any other failure says nothing about this build`() = runBlocking<Unit> {
        for (status in listOf(HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable, HttpStatusCode.TooManyRequests, HttpStatusCode.Unauthorized)) {
            assertEquals(VersionAnswer.Failed, source { error("SOMETHING", status) }.fetch(), "$status")
        }
        // A 404 from a proxy or an old backend that knows no such route is not "nothing published".
        assertEquals(VersionAnswer.Failed, source { respondJson("<html>404</html>", HttpStatusCode.NotFound) }.fetch())
        assertEquals(VersionAnswer.Failed, source { error("NOT_FOUND", HttpStatusCode.NotFound) }.fetch())
        assertEquals(VersionAnswer.Failed, source { throw java.io.IOException("offline") }.fetch())
    }

    @Test
    fun `an answer that is not a valid policy is not believed`() = runBlocking<Unit> {
        val invalid = listOf(
            """{}""",
            "not json",
            "[]",
            """{"latestVersionCode":42,"latestVersionName":"1.4.0","minimumSupportedVersionCode":43,"updateUrl":"https://vazie.app/vpn"}""",
            """{"latestVersionCode":0,"latestVersionName":"1.4.0","minimumSupportedVersionCode":0,"updateUrl":"https://vazie.app/vpn"}""",
            """{"latestVersionCode":42.5,"latestVersionName":"1.4.0","minimumSupportedVersionCode":39,"updateUrl":"https://vazie.app/vpn"}""",
            """{"latestVersionCode":42,"latestVersionName":"1.4.0","minimumSupportedVersionCode":39}""",
            """{"latestVersionCode":42,"minimumSupportedVersionCode":39,"updateUrl":"https://vazie.app/vpn"}""",
        )
        for (body in invalid) {
            assertEquals(VersionAnswer.Failed, source { ok(body) }.fetch(), body)
        }
    }

    @Test
    fun `an update address that is not plain https is never handed on`() = runBlocking<Unit> {
        for (url in listOf(
            "http://vazie.app/vpn",
            "intent://update#Intent;scheme=market;end",
            "market://details?id=app.vazie.vpn",
            "vazie-vpn://settings",
            "javascript:alert(1)",
            "https://user@vazie.app/vpn",
        )) {
            val body = """{"latestVersionCode":42,"latestVersionName":"1.4.0","minimumSupportedVersionCode":39,"updateUrl":"$url"}"""
            assertEquals(VersionAnswer.Failed, source { ok(body) }.fetch(), url)
        }
    }

    @Test
    fun `a slow backend is given up on`() = runBlocking<Unit> {
        val never = CompletableDeferred<Unit>()
        val answer = source(timeoutMillis = 1) { never.await(); ok(BODY) }.fetch()

        assertEquals(VersionAnswer.Failed, answer)
        never.complete(Unit)
    }

    private fun source(
        timeoutMillis: Long = 8_000,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = VazieAppVersionSource(
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

    private fun MockRequestHandleScope.ok(body: String) = respondJson(body, HttpStatusCode.OK)

    private fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode) =
        respond(body, status, headersOf(VazieApiClient.REQUEST_ID_HEADER, "a-request-id"))

    private fun MockRequestHandleScope.error(code: String, status: HttpStatusCode) = respond(
        content = """{"error":{"code":"$code","message":"nobody shows this","requestId":"r"}}""",
        status = status,
        headers = headersOf(VazieApiClient.REQUEST_ID_HEADER, "a-request-id"),
    )

    private companion object {
        const val BODY = """{"latestVersionCode":42,"latestVersionName":"1.4.0","minimumSupportedVersionCode":39,"updateUrl":"https://vazie.app/vpn",
            "message":{"ru":"Обновите Vazie VPN, чтобы продолжить работу.","en":"Update Vazie VPN to continue."}}"""
    }
}
