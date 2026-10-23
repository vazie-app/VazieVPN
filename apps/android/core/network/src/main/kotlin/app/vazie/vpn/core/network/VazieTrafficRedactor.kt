package app.vazie.vpn.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a debug HTTP inspector is allowed to see of Vazie's traffic. */
internal object VazieTrafficRedactor {

    /** What replaces a withheld value. Fixed text, so a test can assert it and a reader can spot it. */
    const val REDACTED: String = "***REDACTED***"

    /** Headers Chucker is told to mask. */
    val REDACTED_HEADERS: Set<String> = setOf(
        "Authorization",
        "Proxy-Authorization",
        "Cookie",
        "Set-Cookie",
        "X-Api-Key",
        "X-Internal-Token",
    )

    /** JSON keys whose values never reach the inspector. */
    val REDACTED_KEYS: Set<String> = setOf(
        "credential",
        "token",
        "accessToken",
        "refreshToken",
        "publicKey",
        "privateKey",
        "shortId",
        "password",
        "secret",
        "agentCaPem",
        "agentToken",
        "internalApiToken",
        "email",
        "code",
    )

    private val lowercasedKeys = REDACTED_KEYS.map { it.lowercase() }.toSet()

    private val json = Json { prettyPrint = true }

    private val parser = Json { ignoreUnknownKeys = true }

    /** Returns JSON with every sensitive value replaced, or a placeholder; never the original bytes. */
    fun redact(body: String, sizeBytes: Int): String {
        if (body.isBlank()) return ""
        val parsed = runCatching { parser.parseToJsonElement(body) }.getOrNull()
            ?: return "<non-JSON body withheld by the Vazie redactor: $sizeBytes bytes>"
        return runCatching { json.encodeToString(JsonElement.serializer(), parsed.redacted()) }
            .getOrElse { "<body withheld by the Vazie redactor: $sizeBytes bytes>" }
    }

    private fun JsonElement.redacted(): JsonElement = when (this) {
        is JsonObject -> JsonObject(
            mapValues { (key, value) ->
                if (key.lowercase() in lowercasedKeys) JsonPrimitive(REDACTED) else value.redacted()
            },
        )
        // Arrays are walked rather than skipped: `servers` is an array of objects today, and an
        // array of credentials is the shape a batch endpoint would have.
        is JsonArray -> JsonArray(map { it.redacted() })
        else -> this
    }
}
