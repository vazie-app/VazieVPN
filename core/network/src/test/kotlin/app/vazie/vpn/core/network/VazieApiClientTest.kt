package app.vazie.vpn.core.network

import app.vazie.vpn.core.model.Secret
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The client is exercised against exact bodies, headers and transport failures. */
class VazieApiClientTest {

    @Serializable
    private data class Sample(val name: String)

    @Test
    fun `a success decodes the body and carries the request id`() = runTest {
        val client = client { respondJson("""{"name":"example"}""") }

        val result = client.get("/servers", Sample.serializer())

        assertIs<ApiResult.Success<Sample>>(result)
        assertEquals("example", result.value.name)
        assertEquals(SERVER_REQUEST_ID, result.requestId)
    }

    @Test
    fun `an unknown field in a response is not an outage`() = runTest {
        // The backend may add a field before Android knows about it, so the decoder must ignore it
        // rather than turn a working deployment into a failing client.
        val client = client { respondJson("""{"name":"example","addedLater":42}""") }

        val result = client.get("/servers", Sample.serializer())

        assertIs<ApiResult.Success<Sample>>(result)
    }

    @Test
    fun `the bearer token is sent when there is one, and no header when there is not`() = runTest {
        var seen: String? = null
        val withToken = client(token = "session-token-value") { request ->
            seen = request.headers["Authorization"]
            respondJson("""{"name":"x"}""")
        }
        withToken.get("/servers", Sample.serializer())
        assertEquals("Bearer session-token-value", seen)

        var absent: String? = "not-read-yet"
        val withoutToken = client(token = null) { request ->
            absent = request.headers["Authorization"]
            respondJson("""{"name":"x"}""")
        }
        withoutToken.get("/servers", Sample.serializer())
        assertNull(absent)
    }

    @Test
    fun `a correlation id is sent, and the server's own id wins when it answers with one`() = runTest {
        var sent: String? = null
        val echoing = client { request ->
            sent = request.headers[VazieApiClient.REQUEST_ID_HEADER]
            respondJson("""{"name":"x"}""")
        }
        val result = echoing.get("/servers", Sample.serializer())
        assertEquals(CLIENT_REQUEST_ID, sent)
        assertEquals(SERVER_REQUEST_ID, result.requestId)
    }

    @Test
    fun `a response without a request id falls back to the one the client sent`() = runTest {
        val silent = client { respond("""{"name":"x"}""", HttpStatusCode.OK) }

        val result = silent.get("/servers", Sample.serializer())

        assertEquals(CLIENT_REQUEST_ID, result.requestId)
    }

    @Test
    fun `a refusal keeps the backend's error code and its request id`() = runTest {
        val client = client {
            respond(
                content = """{"error":{"code":"SUBSCRIPTION_REQUIRED","message":"Entitlement required","requestId":"r"}}""",
                status = HttpStatusCode.Forbidden,
                headers = headersOf(VazieApiClient.REQUEST_ID_HEADER, SERVER_REQUEST_ID),
            )
        }

        val result = client.post("/vpn/access", buildJsonObject { }, Sample.serializer())

        val failure = assertIs<ApiResult.Failure>(result).failure
        assertIs<ApiFailure.Http>(failure)
        assertEquals(403, failure.status)
        assertEquals("SUBSCRIPTION_REQUIRED", failure.code)
        assertEquals(SERVER_REQUEST_ID, failure.requestId)
        assertNull(failure.retryAfterSeconds)
    }

    @Test
    fun `a rate limit carries how long to wait`() = runTest {
        val client = client {
            respond(
                content = """{"error":{"code":"RATE_LIMITED","message":"Too many requests","requestId":"r"}}""",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf("Retry-After", "30"),
            )
        }

        val failure = assertIs<ApiResult.Failure>(client.get("/servers", Sample.serializer())).failure

        assertIs<ApiFailure.Http>(failure)
        assertEquals(30L, failure.retryAfterSeconds)
    }

    @Test
    fun `an html error page from in front of the backend is a refusal, not a crash`() = runTest {
        // This is what a reverse proxy answers when the backend is down, and it is the case a client
        // that assumed JSON everywhere gets wrong.
        val client = client {
            respond("<html><body>502 Bad Gateway</body></html>", HttpStatusCode.BadGateway)
        }

        val failure = assertIs<ApiResult.Failure>(client.get("/servers", Sample.serializer())).failure

        assertIs<ApiFailure.Http>(failure)
        assertEquals(502, failure.status)
        assertEquals(ApiFailure.UNKNOWN_CODE, failure.code)
    }

    @Test
    fun `a success that is not the expected shape is unreadable, not unreachable`() = runTest {
        // The distinction is the point: one is fixed by retrying and the other never is.
        val client = client { respondJson("""{"unexpected":true}""") }

        val failure = assertIs<ApiResult.Failure>(client.get("/servers", Sample.serializer())).failure

        assertIs<ApiFailure.Unreadable>(failure)
        assertEquals(SERVER_REQUEST_ID, failure.requestId)
    }

    @Test
    fun `a body larger than the cap is refused instead of allocated`() = runTest {
        val client = client { respondJson("\"" + "a".repeat(512 * 1024) + "\"") }

        val failure = assertIs<ApiResult.Failure>(client.get("/servers", Sample.serializer())).failure

        assertIs<ApiFailure.Unreadable>(failure)
    }

    @Test
    fun `nothing answering is unreachable`() = runTest {
        val client = client { throw IOException("no route to host") }

        val failure = assertIs<ApiResult.Failure>(client.get("/servers", Sample.serializer())).failure

        assertIs<ApiFailure.Unreachable>(failure)
    }

    @Test
    fun `a no content revocation succeeds`() = runTest {
        val client = client {
            respond(
                content = "",
                status = HttpStatusCode.NoContent,
                headers = headersOf(VazieApiClient.REQUEST_ID_HEADER, SERVER_REQUEST_ID),
            )
        }

        val result = client.delete("/vpn/access/an-id")

        assertIs<ApiResult.Success<Unit>>(result)
        assertEquals(SERVER_REQUEST_ID, result.requestId)
    }

    @Test
    fun `a revocation of something that is not there is a refusal the caller can read`() = runTest {
        val client = client {
            respondError(
                status = HttpStatusCode.NotFound,
                content = """{"error":{"code":"VPN_ACCESS_NOT_FOUND","message":"x","requestId":"r"}}""",
            )
        }

        val failure = assertIs<ApiResult.Failure>(client.delete("/vpn/access/an-id")).failure

        assertIs<ApiFailure.Http>(failure)
        assertEquals("VPN_ACCESS_NOT_FOUND", failure.code)
    }

    @Test
    fun `no outcome can print the session token`() = runTest {
        // A failure is the value that reaches a diagnostics screen and a bug report, so its rendered
        // form is part of the contract.
        val secret = "session-token-value"
        val client = client(token = secret) {
            respondError(
                status = HttpStatusCode.Unauthorized,
                content = """{"error":{"code":"AUTH_INVALID","message":"x","requestId":"r"}}""",
            )
        }

        val result = client.get("/servers", Sample.serializer())

        assertFalse(result.toString().contains(secret))
    }

    private fun client(
        token: String? = "session-token-value",
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): VazieApiClient = VazieApiClient.create(
        engine = MockEngine { request -> handler(request) },
        baseUrl = ApiBaseUrl("https://vazie.example/api/v1"),
        tokens = { token?.let { Secret.of(it) } },
        newRequestId = { CLIENT_REQUEST_ID },
    )

    private fun MockRequestHandleScope.respondJson(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(VazieApiClient.REQUEST_ID_HEADER, SERVER_REQUEST_ID),
    )

    private companion object {
        const val CLIENT_REQUEST_ID = "client-side-correlation-id"
        const val SERVER_REQUEST_ID = "server-side-correlation-id"
    }
}
