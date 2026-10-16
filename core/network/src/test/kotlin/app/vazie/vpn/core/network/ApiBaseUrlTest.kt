package app.vazie.vpn.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApiBaseUrlTest {

    @Test
    fun `a plaintext origin cannot be constructed at all`() {
        // The property under test is the absence of an escape hatch. Every request carries a session token in
        // a header, so "just for local development" is how a token reaches the network in the clear.
        assertFailsWith<IllegalArgumentException> { ApiBaseUrl("http://vazie.example/api/v1") }
        assertFailsWith<IllegalArgumentException> { ApiBaseUrl("http://127.0.0.1:8090/api/v1") }
    }

    @Test
    fun `a scheme without a host is refused`() {
        assertFailsWith<IllegalArgumentException> { ApiBaseUrl("https://") }
    }

    @Test
    fun `a trailing slash is refused rather than trimmed`() {
        // Trimming would make two spellings of the same origin, and the one nobody tested is the one
        // that produces a double slash in a path.
        assertFailsWith<IllegalArgumentException> { ApiBaseUrl("https://vazie.example/api/v1/") }
    }

    @Test
    fun `a path is joined to the origin exactly once`() {
        assertEquals(
            "https://vazie.example/api/v1/servers",
            ApiBaseUrl("https://vazie.example/api/v1").resolve("/servers"),
        )
    }

    @Test
    fun `a path without a leading slash is a failure, not a guess`() {
        assertFailsWith<IllegalArgumentException> {
            ApiBaseUrl("https://vazie.example/api/v1").resolve("servers")
        }
    }
}
