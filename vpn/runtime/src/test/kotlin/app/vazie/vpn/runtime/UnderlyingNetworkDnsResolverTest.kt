package app.vazie.vpn.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric, because `parseIpv4Response` parses with `org.json`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UnderlyingNetworkDnsResolverTest {

    @Test
    fun `reads an A record after a CNAME`() {
        val response = """
            {
              "Status": 0,
              "Answer": [
                {"name":"vpn.example","type":5,"data":"edge.example."},
                {"name":"edge.example","type":1,"data":"203.0.113.17"}
              ]
            }
        """.trimIndent()

        assertEquals(
            "203.0.113.17",
            UnderlyingNetworkDnsResolver.parseIpv4Response(response),
        )
    }

    @Test
    fun `fails closed for DNS errors malformed JSON and non-IPv4 data`() {
        listOf(
            """{"Status":3}""",
            "not json",
            """{"Status":0,"Answer":[{"type":28,"data":"2001:db8::1"}]}""",
            """{"Status":0,"Answer":[{"type":1,"data":"999.0.0.1"}]}""",
        ).forEach { response ->
            assertNull(UnderlyingNetworkDnsResolver.parseIpv4Response(response))
        }
    }
}
