package app.vazie.vpn.engine.xray

import app.vazie.vpn.api.EngineHost

/** The native core, behind an interface Vazie owns. */
internal interface XrayRuntime {

    /** Keeps the core's sockets out of the tunnel and sets a protected resolver; call before [run].
     * `false` means no protected resolver, a failure: the server's name could not resolve. */
    fun attach(host: EngineHost): Boolean

    /** Starts the core on [configJson]. */
    fun run(configJson: String): XrayInvocation

    /** Whether the core reports itself running. */
    fun isRunning(): Boolean

    /** Stops the core and undoes [attach]. Safe when nothing started. */
    fun stop()
}

/** What the core answered. */
internal sealed interface XrayInvocation {

    data object Succeeded : XrayInvocation

    data class Failed(val detail: String?) : XrayInvocation
}
