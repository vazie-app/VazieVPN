package app.vazie.vpn.api

/** Why a connection did not happen, or stopped happening. */
sealed interface ConnectionFailure {

    /** The user dismissed or denied the system VPN dialog. */
    data object ConsentDenied : ConnectionFailure

    /** No validated network underneath. */
    data object NoInternet : ConnectionFailure

    /** The selected profile is gone — deleted between selection and connect. */
    data object ProfileMissing : ConnectionFailure

    /** The stored profile could not be turned into something an engine can run. */
    data class ProfileInvalid(val field: String?) : ConnectionFailure

    /** The profile is valid and the bundled runtime cannot execute it. Reported *before* the tunnel is built,
     * so a configuration Vazie cannot run never takes the device's traffic with it. */
    data class UnsupportedConfiguration(val feature: UnsupportedRuntimeFeature) : ConnectionFailure

    /** `VpnService.Builder.establish()` returned null or threw. */
    data object TunnelSetupFailed : ConnectionFailure

    /** The engine refused to start, or stopped on its own. */
    data class EngineFailed(val engineId: VpnEngineId) : ConnectionFailure

    /** The interface came up, the engine started, and nothing could reach the internet through it. */
    data object TunnelUnusable : ConnectionFailure

    /** The Android service hosting the tunnel went away underneath the controller. */
    data object ServiceStopped : ConnectionFailure

    data object Unknown : ConnectionFailure
}

enum class UnsupportedRuntimeFeature {
    /** An engine the build does not carry. */
    ENGINE,

    /** A protocol the engine binding does not expose. */
    PROTOCOL,

    /** A stream transport the engine cannot dial. */
    TRANSPORT,

    /** A security mode the engine cannot negotiate. */
    SECURITY,

    /** A flow the engine cannot apply to this transport or security. */
    FLOW,
}
