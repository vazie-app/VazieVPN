package app.vazie.vpn.feature.onboarding

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The last page hands off to the tour rather than deferring to a screen. */
class OnboardingCopyTest {

    @Test
    fun `neither locale defers to a screen that will not remind anybody`() {
        mapOf(
            "src/main/res/values/strings.xml" to "Settings will remind you",
            "src/main/res/values-ru/strings.xml" to "Настройки напомнят",
        ).forEach { (path, forbidden) ->
            val body = reachBody(path)
            assertFalse(
                body.contains(forbidden, ignoreCase = true),
                "$path promises a reminder on another screen again:\n$body",
            )
            assertTrue(body.isNotBlank(), "$path has no last-page body at all")
        }
    }

    /** It still teaches none of the three integrations. */
    @Test
    fun `the last page still teaches no procedure`() {
        val body = reachBody("src/main/res/values/strings.xml")

        listOf("long-press", "long press", "drag it", "widget list", "tap edit").forEach { step ->
            assertFalse(
                body.contains(step, ignoreCase = true),
                "onboarding started teaching a procedure that belongs in a guide: $step",
            )
        }
    }

    private fun reachBody(path: String): String {
        val text = File(path).readText()
        val match = BODY.find(text)
        return match?.groupValues?.get(1).orEmpty()
    }

    private companion object {
        val BODY = Regex("""<string name="onboarding_reach_body">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    }
}
