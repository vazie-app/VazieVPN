package app.vazie.vpn.tour

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.vazie.vpn.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Connections step says where configurations live and where they are added — and stops there. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TourCopyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the configurations step says they are chosen and added right here`() {
        val body = context.getString(R.string.tour_configuration_body)

        assertFalse(
            body.contains("Vazie servers", ignoreCase = true),
            "the step mentions Vazie servers, which the app does not have: $body",
        )
        assertFalse(
            body.contains("open Connections", ignoreCase = true),
            "the step still sends people to a Connections screen that no longer exists: $body",
        )
        assertTrue(
            body.contains("add", ignoreCase = true),
            "the step does not say that is where configurations are added: $body",
        )
    }

    @Test
    fun `no tour step shows a link, a protocol or a procedure`() {
        val steps = listOf(
            R.string.tour_connect_body,
            R.string.tour_configuration_body,
            R.string.tour_settings_body,
            R.string.tour_done_body,
        ).map(context::getString)

        steps.forEach { body ->
            assertFalse(
                body.contains("://"),
                "a tour caption is showing a link, which belongs in the guide: $body",
            )
            listOf("VLESS", "WireGuard", "Xray").forEach { protocol ->
                assertFalse(
                    body.contains(protocol, ignoreCase = true),
                    "a tour caption named a protocol: $body",
                )
            }
            assertFalse(
                body.contains("long-press", ignoreCase = true) ||
                    body.contains("tap edit", ignoreCase = true),
                "a tour caption grew a procedure, which belongs in a guide: $body",
            )
        }
    }

    /** The first step names the button by the label it actually carries. */
    @Test
    @Config(qualifiers = "+ru")
    fun `the connect step names the disconnect label the button really shows`() {
        val label = context.getString(app.vazie.vpn.feature.home.R.string.home_action_disconnect)
        val body = context.getString(R.string.tour_connect_body)
        assertTrue(body.contains("«$label»"), "the step names a label the button does not show: $body")
    }
}
