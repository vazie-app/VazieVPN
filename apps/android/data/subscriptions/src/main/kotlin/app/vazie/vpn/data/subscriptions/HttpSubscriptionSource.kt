package app.vazie.vpn.data.subscriptions

import app.vazie.vpn.config.subscription.SubscriptionFailure
import app.vazie.vpn.config.subscription.SubscriptionFetch
import app.vazie.vpn.config.subscription.SubscriptionLink
import app.vazie.vpn.config.subscription.SubscriptionSource
import app.vazie.vpn.core.model.Secret
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException

/** One `GET` to the subscription's own address: no session, no cookies, nothing of Vazie's in it. */
class HttpSubscriptionSource internal constructor(private val http: HttpClient) : SubscriptionSource {

    override suspend fun fetch(link: SubscriptionLink): SubscriptionFetch = try {
        http.prepareGet(link.url.expose()) {
            // Panels pick the format by what asks; plain text is what gets a list of share links.
            header(HttpHeaders.Accept, ACCEPT)
        }.execute { response ->
            when {
                !response.status.isSuccess() -> SubscriptionFetch.Failed(SubscriptionFailure.REFUSED)
                else -> response.boundedText()?.let { SubscriptionFetch.Fetched(Secret.of(it)) }
                    ?: SubscriptionFetch.Failed(SubscriptionFailure.TOO_LARGE)
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        // Engines report an unreachable host each in their own way; the user is told one thing.
        SubscriptionFetch.Failed(SubscriptionFailure.UNREACHABLE)
    }

    /** The body, or `null` once it is larger than a list could be - without reading the rest of it. */
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
        return String(buffer.toByteArray(), Charsets.UTF_8)
    }

    companion object {

        fun create(): SubscriptionSource = HttpSubscriptionSource(
            HttpClient(OkHttp) {
                defaults()
                engine {
                    config {
                        // Ktor follows redirects itself (see `defaults`); this keeps OkHttp from doing it a second
                        // way, and from ever following one that leaves https.
                        followRedirects(false)
                        followSslRedirects(false)
                    }
                }
            },
        )

        /** The seam a test drives: the production configuration with a substituted transport. */
        internal fun create(engine: HttpClientEngine): SubscriptionSource =
            HttpSubscriptionSource(HttpClient(engine) { defaults() })

        private fun io.ktor.client.HttpClientConfig<*>.defaults() {
            // A refusal is a result, not an exception.
            expectSuccess = false
            // The link was checked to be https; a redirect must not quietly undo that. Said here rather than left to
            // the plugin's default, and pinned by a test.
            install(HttpRedirect) {
                allowHttpsDowngrade = false
                checkHttpMethod = true
            }
            install(UserAgent) { agent = USER_AGENT }
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
            }
        }

        /** The app's name and nothing about the device or the build. */
        private const val USER_AGENT = "VazieVPN"
        private const val ACCEPT = "text/plain, */*;q=0.5"

        /** A hundred long share links fit many times over; a web page or a download does not get read. */
        private const val BODY_LIMIT_BYTES = 1024 * 1024
        private const val READ_CHUNK_BYTES = 8 * 1024

        private const val CONNECT_TIMEOUT_MILLIS = 10_000L
        private const val REQUEST_TIMEOUT_MILLIS = 20_000L
        private const val SOCKET_TIMEOUT_MILLIS = 15_000L
    }
}
