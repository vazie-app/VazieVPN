package app.vazie.vpn.core.network

import app.vazie.vpn.core.model.Secret
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable

/** The inspector observes; it must never participate. */
class InspectorIsolationTest {

    @Serializable
    private data class Profile(val credential: String, val protocol: String)

    @Test
    fun `the application receives the original body, not the redacted one`() = runTest {
        val body = """{"credential":"$CREDENTIAL","protocol":"VLESS"}"""
        val client = VazieApiClient.create(
            engine = MockEngine {
                respond(body, HttpStatusCode.OK, headersOf(VazieApiClient.REQUEST_ID_HEADER, "r"))
            },
            baseUrl = ApiBaseUrl("https://vazie.example/api/v1"),
            tokens = { Secret.of("a-synthetic-session-token") },
        )

        val result = client.get("/vpn/access", Profile.serializer())

        // What the app got: intact. Without this the app would connect with a blanked credential.
        val value = assertIs<ApiResult.Success<Profile>>(result).value
        assertEquals(CREDENTIAL, value.credential)
        assertEquals("VLESS", value.protocol)

        // What the inspector would store from the very same bytes: redacted.
        val inspected = VazieTrafficRedactor.redact(body, body.length)
        assertFalse(inspected.contains(CREDENTIAL))
        assertTrue(inspected.contains("VLESS"))
    }

    @Test
    fun `the request path never calls the redactor`() {
        // Structural, because the property is about absence. The redactor is reachable only from the
        // debug seam; if it ever appeared in the client itself, it would be rewriting real traffic.
        val client = File("src/main/kotlin/app/vazie/vpn/core/network/VazieApiClient.kt").readText()

        assertFalse(
            client.contains("VazieTrafficRedactor"),
            "the API client references the redactor - it must only ever run inside the inspector",
        )
    }

    @Test
    fun `the release seam installs nothing`() {
        val release = File("src/release/kotlin/app/vazie/vpn/core/network/NetworkInspector.kt")
        assertTrue(release.exists(), "the release no-op seam is missing entirely")
        val text = release.readText()

        // The package, not the word: the KDoc explains what is deliberately absent, and a check
        // that cannot tell an explanation from an import is a check somebody deletes.
        assertFalse(text.contains(INSPECTOR_PACKAGE), "the release seam imports the inspector")
        assertTrue(
            Regex("""fun interceptor\([^)]*\): Interceptor\? = null""").containsMatchIn(text),
            "the release seam no longer returns null, so a release build would gain an interceptor",
        )
    }

    @Test
    fun `only the debug seam knows Chucker exists`() {
        val offenders = listOf("src/main", "src/release", "src/test")
            .map(::File)
            .filter { it.exists() }
            .flatMap { root ->
                root.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .filterNot { it.name == "InspectorIsolationTest.kt" }
                    .filter { it.readText().contains(INSPECTOR_PACKAGE) }
                    .map { it.path }
            }

        assertTrue(offenders.isEmpty(), "Chucker is referenced outside src/debug:\n$offenders")
    }

    private companion object {
        const val CREDENTIAL = "00000000-0000-4000-8000-0000000000c1"

        /** Split so this file does not match its own scan. */
        val INSPECTOR_PACKAGE = "com.chuckerteam" + ".chucker"
    }
}
