package app.vazie.vpn.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The boundary, checked rather than described. */
class WidgetStateCarriesNoSecretsTest {

    @Test
    fun `no field of the published state is named after a secret`() {
        val offenders = File(WIDGET_SOURCES)
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name == "VazieWidgetState.kt" }
            .flatMap { file -> FIELD.findAll(file.readText()).map { it.groupValues[1] } }
            .filter { name -> FORBIDDEN.any { name.contains(it, ignoreCase = true) } }
            .toList()

        assertTrue(offenders.isEmpty(), "widget state declares a field named after a secret: $offenders")
    }

    @Test
    fun `nothing a launcher can read is named after a secret`() {
        WidgetFixtures.allStates.forEach { state ->
            val written = mutablePreferencesOf()
                .apply { VazieWidgetStateStore.write(this, state) }
                .asMap()
            val offenders = written.keys
                .map { it.name }
                .filter { key -> FORBIDDEN.any { key.contains(it, ignoreCase = true) } }

            assertTrue(offenders.isEmpty(), "$state writes a key named after a secret: $offenders")
        }
    }

    private companion object {
        const val WIDGET_SOURCES = "src/main/kotlin/app/vazie/vpn/widget"

        /** The words secret connection material is called in `:vpn:api`. */
        val FORBIDDEN = listOf(
            "credential",
            "token",
            "secret",
            "publicKey",
            "public_key",
            "shortId",
            "short_id",
            "endpoint",
            "password",
            "uuid",
            "privateKey",
            "private_key",
        )

        val FIELD = Regex("""\bva[lr]\s+(\w+)\s*:""")
    }
}
