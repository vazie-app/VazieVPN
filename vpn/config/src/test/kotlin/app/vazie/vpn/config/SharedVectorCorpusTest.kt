package app.vazie.vpn.config

import app.vazie.vpn.core.model.Secret
import app.vazie.vpn.api.ProfileDraft
import app.vazie.vpn.api.XrayOutbound
import app.vazie.vpn.api.XraySecurity
import app.vazie.vpn.api.XrayTransport
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Runs the corpus in `/product/vless-vectors`, the one both clients read. */
class SharedVectorCorpusTest {

    private val registry = ConfigParserRegistry.default()

    @Test
    fun `every vector in the corpus produces the agreed product meaning`() {
        val failures = GROUPS.flatMap { group -> vectors(group).flatMap { check(group, it) } }
        assertTrue(
            failures.isEmpty(),
            "the shared corpus disagrees with this parser:\n" + failures.joinToString("\n"),
        )
    }

    @Test
    fun `every group contributes vectors`() {
        GROUPS.forEach { group ->
            assertTrue(vectors(group).isNotEmpty(), "$group contributed no vectors")
        }
    }

    private fun check(group: String, vector: JsonObject): List<String> {
        val name = vector.getValue("name").jsonPrimitive.content
        val input = vector.getValue("input").jsonPrimitive.content
        val expect = vector.getValue("expect").jsonObject
        val label = "$group/$name"
        val problems = mutableListOf<String>()

        when (val result = registry.parse(ConfigSource.PlainText(Secret.of(input)))) {
            is ParseResult.Recognized -> {
                if (expect.kind() != "recognized") {
                    return listOf("$label: expected ${expect.kind()}, got recognized")
                }
                problems += expect.string(label, "protocol", result.descriptor.id)
                problems += checkRecognized(label, expect, result.draft)
            }

            is ParseResult.Unsupported -> {
                if (expect.kind() != "unsupported") {
                    return listOf("$label: expected ${expect.kind()}, got unsupported")
                }
                problems += expect.string(label, "protocol", result.descriptor.id)
                problems += expect.string(label, "feature", result.feature.featureName())
                problems += expect.string(label, "value", result.feature.value())
            }

            is ParseResult.Invalid -> {
                if (expect.kind() != "invalid") {
                    return listOf("$label: expected ${expect.kind()}, got invalid")
                }
                problems += expect.string(label, "reason", result.reason.reasonName())
                when (val reason = result.reason) {
                    is InvalidReason.UnknownScheme -> problems += expect.string(label, "scheme", reason.scheme)
                    is InvalidReason.MissingParameter -> problems += expect.string(label, "parameter", reason.name)
                    is InvalidReason.ConflictingParameters ->
                        problems += expect.strings(label, "parameters", reason.names)
                    else -> Unit
                }
            }
        }
        return problems
    }

    private fun checkRecognized(label: String, expect: JsonObject, draft: ProfileDraft): List<String> {
        val xray = draft as ProfileDraft.Xray
        val outbound = xray.outbound as XrayOutbound.Vless
        val problems = mutableListOf<String>()

        problems += expect.string(label, "host", outbound.endpoint.host)
        problems += expect.int(label, "port", outbound.endpoint.port)
        problems += expect.string(label, "security", outbound.security.kind.name.lowercase())
        problems += expect.string(label, "transport", outbound.transport.wireName())
        problems += expect.string(label, "flow", outbound.flow?.wireName)
        problems += expect.string(label, "serverName", outbound.security.serverName)
        problems += expect.string(label, "fingerprint", outbound.security.fingerprint)
        problems += expect.string(label, "suggestedName", xray.suggestedName)
        problems += expect.strings(label, "unknownParameterNames", outbound.unknownParameters.names)

        when (val security = outbound.security) {
            is XraySecurity.Tls -> {
                problems += expect.strings(label, "alpn", security.alpn)
                problems += expect.boolean(label, "allowInsecure", security.allowInsecure)
            }
            is XraySecurity.Reality -> problems += expect.string(label, "spiderX", security.spiderX)
            XraySecurity.None -> Unit
        }

        when (val transport = outbound.transport) {
            is XrayTransport.Tcp -> problems += expect.string(label, "tcpHeaderType", transport.headerType)
            is XrayTransport.WebSocket -> {
                problems += expect.string(label, "wsPath", transport.path)
                problems += expect.string(label, "wsHost", transport.host)
            }
            is XrayTransport.Grpc -> {
                problems += expect.string(label, "grpcServiceName", transport.serviceName)
                problems += expect.boolean(label, "grpcMultiMode", transport.multiMode)
            }
        }
        return problems
    }

    // A key that is absent is not checked; a key that is `null` must be null in the result too.
    // That distinction lets a vector say "irrelevant here" without weakening what it does assert.
    private fun JsonObject.string(label: String, key: String, actual: String?) =
        compare(label, key, actual) { it.jsonPrimitive.content }

    private fun JsonObject.int(label: String, key: String, actual: Int?) =
        compare(label, key, actual) { it.jsonPrimitive.int }

    private fun JsonObject.boolean(label: String, key: String, actual: Boolean?) =
        compare(label, key, actual) { it.jsonPrimitive.boolean }

    private fun JsonObject.strings(label: String, key: String, actual: List<String>?) =
        compare(label, key, actual) { element -> element.jsonArray.map { it.jsonPrimitive.content } }

    private fun <T> JsonObject.compare(
        label: String,
        key: String,
        actual: T?,
        read: (JsonElement) -> T,
    ): List<String> {
        val element = this[key] ?: return emptyList()
        if (element is JsonNull) {
            return if (actual == null) emptyList() else listOf("$label: $key should be absent, got $actual")
        }
        val expected = read(element)
        return if (expected == actual) emptyList() else listOf("$label: $key expected $expected, got $actual")
    }

    private fun JsonObject.kind(): String? = this["kind"]?.jsonPrimitive?.content

    private fun XrayTransport.wireName(): String = when (this) {
        is XrayTransport.Tcp -> "tcp"
        is XrayTransport.WebSocket -> "ws"
        is XrayTransport.Grpc -> "grpc"
    }

    private fun UnsupportedFeature.featureName(): String = when (this) {
        is UnsupportedFeature.Security -> "security"
        is UnsupportedFeature.Transport -> "transport"
        is UnsupportedFeature.Flow -> "flow"
        is UnsupportedFeature.Encryption -> "encryption"
    }

    private fun UnsupportedFeature.value(): String = when (this) {
        is UnsupportedFeature.Security -> value
        is UnsupportedFeature.Transport -> value
        is UnsupportedFeature.Flow -> value
        is UnsupportedFeature.Encryption -> value
    }

    // The corpus vocabulary is written out rather than derived from Kotlin's own names, so a rename
    // on either side has to be a deliberate change to the shared words.
    private fun InvalidReason.reasonName(): String = when (this) {
        InvalidReason.Empty -> "empty"
        is InvalidReason.UnknownScheme -> "unknownScheme"
        InvalidReason.MalformedUri -> "malformedURI"
        InvalidReason.MissingUserId -> "missingUserID"
        InvalidReason.MalformedUserId -> "malformedUserID"
        InvalidReason.MissingHost -> "missingHost"
        InvalidReason.MalformedHost -> "malformedHost"
        InvalidReason.MissingPort -> "missingPort"
        InvalidReason.InvalidPort -> "invalidPort"
        InvalidReason.MalformedEncoding -> "malformedEncoding"
        is InvalidReason.MissingParameter -> "missingParameter"
        is InvalidReason.ConflictingParameters -> "conflictingParameters"
    }

    private fun vectors(group: String): List<JsonObject> {
        val file = File(corpusDirectory, "$group.json")
        assertTrue(file.isFile, "no corpus file at ${file.path}")
        return JSON.parseToJsonElement(file.readText())
            .jsonObject
            .getValue("vectors")
            .jsonArray
            .map { it.jsonObject }
    }

    private companion object {
        val GROUPS = listOf("recognized", "unknown-parameters", "unsupported", "invalid")
        val JSON = Json { ignoreUnknownKeys = true }

        /** Found by walking up the directories, not by counting `..`. */
        val corpusDirectory: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "product/vless-vectors") }
            .firstOrNull { it.isDirectory }
            ?: error("the shared vector corpus was not found above the module directory")
    }
}
