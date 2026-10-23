package app.vazie.vpn.engine.xray

import app.vazie.vpn.api.CidrAddress
import app.vazie.vpn.api.EngineHost
import app.vazie.vpn.api.EngineStartFailure
import app.vazie.vpn.api.EngineStartResult
import app.vazie.vpn.api.EngineSupport
import app.vazie.vpn.api.TunOptions
import app.vazie.vpn.api.TunSpec
import app.vazie.vpn.api.TunnelAttachment
import app.vazie.vpn.api.TunnelStrategy
import app.vazie.vpn.api.VpnEngine
import app.vazie.vpn.api.VpnEngineId
import app.vazie.vpn.api.VpnProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Xray-core, as a Vazie engine. */
class XrayEngine internal constructor(
    private val runtime: XrayRuntime,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : VpnEngine {

    constructor() : this(runtime = LibXrayRuntime())

    override val id: VpnEngineId get() = VpnEngineId.XRAY

    override fun supports(profile: VpnProfile): EngineSupport = XraySupport.supports(profile)

    override fun tunnelStrategy(profile: VpnProfile): TunnelStrategy = TunnelStrategy.EngineOwnsTun

    /** The interface Xray needs. */
    override fun tunSpec(profile: VpnProfile, options: TunOptions): TunSpec = TunSpec(
        sessionName = options.sessionName,
        mtu = MTU,
        addresses = listOf(
            CidrAddress(IPV4_ADDRESS, IPV4_PREFIX),
            CidrAddress(IPV6_ADDRESS, IPV6_PREFIX),
        ),
        routes = listOf(
            CidrAddress(IPV4_ANY, 0),
            CidrAddress(IPV6_ANY, 0),
        ),
        dnsServers = listOf(XrayConfigFactory.DNS_SERVER),
    )

    override suspend fun start(
        profile: VpnProfile,
        attachment: TunnelAttachment,
        host: EngineHost,
    ): EngineStartResult = withContext(dispatcher) {
        val xray = profile as? VpnProfile.Xray
            ?: return@withContext EngineStartResult.Failed(EngineStartFailure.CONFIGURATION)
        if (supports(profile) !is EngineSupport.Supported) {
            return@withContext EngineStartResult.Failed(EngineStartFailure.CONFIGURATION)
        }
        val descriptor = when (attachment) {
            is TunnelAttachment.Tun -> attachment.descriptor
        }
        val originalHost = xray.outbound.endpoint.host
        val dialAddress = if (originalHost.isIpv4Literal()) {
            originalHost
        } else {
            host.resolveIpv4(originalHost)
                ?: return@withContext EngineStartResult.Failed(EngineStartFailure.RUNTIME)
        }

        val config = runCatching {
            XrayConfigFactory.create(
                profile = xray,
                mtu = MTU,
                tunDescriptor = descriptor,
                dialAddress = dialAddress,
            )
        }.getOrElse {
            return@withContext EngineStartResult.Failed(EngineStartFailure.CONFIGURATION)
        }

        try {
            if (!runtime.attach(host)) {
                runtime.stop()
                return@withContext EngineStartResult.Failed(EngineStartFailure.RUNTIME)
            }
            when (runtime.run(config)) {
                // The message is deliberately dropped rather than passed on: the core builds it out
                // of the configuration it rejected.
                is XrayInvocation.Failed -> {
                    runtime.stop()
                    return@withContext EngineStartResult.Failed(EngineStartFailure.RUNTIME)
                }

                XrayInvocation.Succeeded -> Unit
            }
            if (!runtime.isRunning()) {
                runtime.stop()
                return@withContext EngineStartResult.Failed(EngineStartFailure.RUNTIME)
            }
            EngineStartResult.Started
        } catch (error: LinkageError) {
            // The AAR is missing, or built for another ABI. Distinguishable from every other
            // failure and the only one a user can do nothing about, so it gets its own reason.
            runCatching { runtime.stop() }
            EngineStartResult.Failed(EngineStartFailure.NATIVE_LIBRARY)
        }
    }

    override suspend fun stop() {
        withContext(dispatcher) { runCatching { runtime.stop() } }
    }

    override fun isRunning(): Boolean = runCatching { runtime.isRunning() }.getOrDefault(false)

    private fun String.isIpv4Literal(): Boolean {
        val octets = split('.')
        return octets.size == 4 && octets.all { octet ->
            octet.isNotEmpty() && octet.all(Char::isDigit) &&
                octet.toIntOrNull()?.let { it in 0..255 } == true &&
                (octet == "0" || !octet.startsWith('0'))
        }
    }

    private companion object {
        /** 1500 matches what the gVisor endpoint is told in the Xray config; the two must agree, or the stack
         * segments to a size the interface will not carry. */
        const val MTU = 1500

        /** Addresses inside the tunnel, only to satisfy `VpnService.Builder`; gVisor answers whatever
         * arrives. */
        const val IPV4_ADDRESS = "10.10.10.1"
        const val IPV4_PREFIX = 32
        const val IPV6_ADDRESS = "fdfe:dcba:9876::1"
        const val IPV6_PREFIX = 128

        const val IPV4_ANY = "0.0.0.0"
        const val IPV6_ANY = "::"
    }
}
