package app.vazie.vpn.engine.xray

import app.vazie.vpn.api.EngineHost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import libXray.DialerController
import libXray.LibXray

/** libXray, spoken to in the one way it speaks: a JSON envelope in, a JSON envelope out. */
internal class LibXrayRuntime : XrayRuntime {

    private val json = Json { ignoreUnknownKeys = true }

    private var controller: DialerController? = null

    override fun attach(host: EngineHost): Boolean {
        val controller = DialerController { fd -> host.protect(fd.toInt()) }
        this.controller = controller
        LibXray.registerDialerController(controller)
        LibXray.registerListenerController(controller)
        return true
    }

    override fun run(configJson: String): XrayInvocation = invoke(
        method = METHOD_RUN,
        payload = buildJsonObject { put(PAYLOAD_CONFIG, configJson) },
    )

    override fun isRunning(): Boolean {
        val response = call(METHOD_STATE, null) ?: return false
        if (response[FIELD_SUCCESS]?.jsonPrimitive?.boolean != true) return false
        return response[FIELD_DATA]
            ?.let { it as? JsonObject }
            ?.get(FIELD_RUNNING)
            ?.jsonPrimitive
            ?.boolean == true
    }

    override fun stop() {
        invoke(method = METHOD_STOP, payload = null)
        controller = null
    }

    private fun invoke(method: String, payload: JsonObject?): XrayInvocation {
        val response = call(method, payload)
            ?: return XrayInvocation.Failed(detail = "unreadable response")
        val success = response[FIELD_SUCCESS]?.jsonPrimitive?.boolean == true
        return if (success) {
            XrayInvocation.Succeeded
        } else {
            XrayInvocation.Failed(response[FIELD_ERROR]?.jsonPrimitive?.contentOrNull)
        }
    }

    private fun call(method: String, payload: JsonObject?): JsonObject? {
        val request = buildJsonObject {
            put("apiVersion", API_VERSION)
            put("method", method)
            if (payload != null) putJsonObject("payload") { payload.forEach { (k, v) -> put(k, v) } }
        }
        val raw = runCatching {
            LibXray.invoke(json.encodeToString(JsonObject.serializer(), request))
        }.getOrNull() ?: return null
        return runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
    }

    private companion object {
        /** libXray v26.7.28 accepts `0` and `1`. Pinned rather than omitted so a version bump that changes
         * the envelope fails here, loudly, instead of at the first connect. */
        const val API_VERSION = 1

        const val METHOD_RUN = "runXrayFromJson"
        const val METHOD_STOP = "stopXray"
        const val METHOD_STATE = "getXrayState"
        const val PAYLOAD_CONFIG = "configJSON"

        const val FIELD_SUCCESS = "success"
        const val FIELD_DATA = "data"
        const val FIELD_ERROR = "error"
        const val FIELD_RUNNING = "running"

    }
}
