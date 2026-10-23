package app.vazie.vpn.core.network

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Every call Vazie makes to the Vazie backend, and the only place one can be made from. */
class VazieApiClient internal constructor(
    private val http: HttpClient,
    private val baseUrl: ApiBaseUrl,
    private val tokens: SessionTokenProvider,
    private val newRequestId: () -> String = { UUID.randomUUID().toString() },
) {

    suspend fun <T> get(path: String, response: DeserializationStrategy<T>): ApiResult<T> =
        exchange(HttpMethod.Get, path, body = null).decode(response)

    suspend fun <T> post(
        path: String,
        body: JsonElement?,
        response: DeserializationStrategy<T>,
        headers: Map<String, String> = emptyMap(),
    ): ApiResult<T> = exchange(HttpMethod.Post, path, body, headers).decode(response)

    /** A call answered by its status (`204`); any body is discarded. [body] is sent as JSON when given. */
    suspend fun delete(path: String, body: JsonElement? = null): ApiResult<Unit> =
        when (val result = exchange(HttpMethod.Delete, path, body = body)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> ApiResult.Success(Unit, result.requestId)
        }

    fun close() {
        http.close()
    }

    private suspend fun exchange(
        method: HttpMethod,
        path: String,
        body: JsonElement?,
        headers: Map<String, String> = emptyMap(),
    ): ApiResult<String> {
        val url = baseUrl.resolve(path)
        val token = tokens.token()
        val correlation = newRequestId()
        val response = try {
            http.request(url) { prepare(method, correlation, token?.expose(), body, headers) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Engines report unreachable hosts differently per platform; callers treat them all alike.
            return ApiResult.Failure(ApiFailure.Unreachable())
        }
        val requestId = response.headers[REQUEST_ID_HEADER] ?: correlation
        val text = response.boundedText()
            ?: return ApiResult.Failure(ApiFailure.Unreadable(requestId))
        if (response.status.isSuccess()) return ApiResult.Success(text, requestId)
        return ApiResult.Failure(
            ApiFailure.Http(
                status = response.status.value,
                code = text.errorCode(),
                requestId = requestId,
                retryAfterSeconds = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull(),
            ),
        )
    }

    private fun HttpRequestBuilder.prepare(
        method: HttpMethod,
        correlation: String,
        token: String?,
        body: JsonElement?,
        headers: Map<String, String>,
    ) {
        this.method = method
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        // Per-request headers such as `Idempotency-Key`. They describe the request, never a secret.
        headers.forEach { (name, value) -> header(name, value) }
        // Sent, not only read back. The backend echoes an id it accepts, so a device-side failure
        // and a server-side log line carry the same value without anybody correlating timestamps.
        header(REQUEST_ID_HEADER, correlation)
        if (token != null) header(HttpHeaders.Authorization, "$BEARER $token")
        if (body != null) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonElement.serializer(), body))
        }
    }

    private suspend fun HttpResponse.boundedText(): String? {
        val channel = bodyAsChannel()
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(READ_CHUNK_BYTES)
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(chunk, 0, chunk.size)
            if (read <= 0) break
            if (buffer.size() + read > BODY_LIMIT_BYTES) return null
            buffer.write(chunk, 0, read)
        }
        // Not `buffer.toString(Charsets.UTF_8)`: that overload is API 33 and the app runs from 28.
        return String(buffer.toByteArray(), Charsets.UTF_8)
    }

    private fun String.errorCode(): String =
        runCatching { json.decodeFromString(ApiErrorEnvelope.serializer(), this).error.code }
            .getOrDefault(ApiFailure.UNKNOWN_CODE)

    private fun <T> ApiResult<String>.decode(serializer: DeserializationStrategy<T>): ApiResult<T> =
        when (this) {
            is ApiResult.Failure -> this
            is ApiResult.Success -> runCatching { json.decodeFromString(serializer, value) }.fold(
                onSuccess = { ApiResult.Success(it, requestId) },
                onFailure = { ApiResult.Failure(ApiFailure.Unreadable(requestId)) },
            )
        }

    companion object {

        /** The one `Json` for the API: unknown keys ignored, absent values omitted rather than null. */
        val json: Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        const val REQUEST_ID_HEADER: String = "X-Request-Id"

        private const val BEARER = "Bearer"

        /** Generous for an API response, trivial for memory, and far below an error page worth reading. */
        private const val BODY_LIMIT_BYTES = 256 * 1024

        private const val READ_CHUNK_BYTES = 8 * 1024

        private const val CONNECT_TIMEOUT_MILLIS = 10_000L

        /** Longer on purpose: `POST /access` waits on the agent, whose own timeout is ten seconds. */
        private const val REQUEST_TIMEOUT_MILLIS = 30_000L

        private const val SOCKET_TIMEOUT_MILLIS = 20_000L

        /** [context] is used only by the debug HTTP inspector. */
        fun create(
            context: Context,
            baseUrl: ApiBaseUrl,
            tokens: SessionTokenProvider,
        ): VazieApiClient = VazieApiClient(
            http = HttpClient(OkHttp) {
                defaults()
                engine {
                    NetworkInspector.interceptor(context)?.let { addInterceptor(it) }
                }
            },
            baseUrl = baseUrl,
            tokens = tokens,
        )

        /** The seam a test drives: the production configuration with a substituted transport. */
        fun create(
            engine: HttpClientEngine,
            baseUrl: ApiBaseUrl,
            tokens: SessionTokenProvider,
            newRequestId: () -> String = { UUID.randomUUID().toString() },
        ): VazieApiClient =
            VazieApiClient(HttpClient(engine) { defaults() }, baseUrl, tokens, newRequestId)

        private fun io.ktor.client.HttpClientConfig<*>.defaults() {
            // A refusal is a result, not an exception: every non-2xx becomes a typed `ApiFailure`
            // through one path instead of an exception through another.
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
            }
        }
    }
}
