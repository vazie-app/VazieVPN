import type { Security, Transport, VlessOutbound } from "./model.ts";

// The outbound half of what Xray runs, ported from `XrayConfigFactory.kt`. The inbounds (a loopback HTTP proxy
// with a per-session login, the stats API) are the helper's own: it is the component that binds sockets, so it
// is the one that decides they are loopback-only and authenticated.

export const TAG_PROXY = "proxy";
export const TAG_BLOCK = "block";

const DEFAULT_FINGERPRINT = "chrome";
const DEFAULT_SPIDER_X = "/";

/** Post-quantum ClientHellos (>1700 bytes) can hang REALITY on lossy paths, so the last pre-quantum build of the
 * requested browser is used. */
const PRE_QUANTUM_BUILDS: Record<string, string> = {
  chrome: "hellochrome_120",
  firefox: "hellofirefox_120",
  edge: "helloedge_106",
};

function preQuantumFingerprint(requested: string | null): string {
  const name = requested?.toLowerCase() ?? DEFAULT_FINGERPRINT;
  return PRE_QUANTUM_BUILDS[name] ?? name;
}

type Json = Record<string, unknown>;

function tlsSettings(security: Extract<Security, { kind: "tls" }>): Json {
  const out: Json = {};
  if (security.serverName) out.serverName = security.serverName;
  if (security.fingerprint) out.fingerprint = preQuantumFingerprint(security.fingerprint);
  if (security.alpn.length > 0) out.alpn = security.alpn;
  // No `allowInsecure`: `supports()` refuses profiles that ask for it.
  return out;
}

function realitySettings(security: Extract<Security, { kind: "reality" }>): Json {
  const out: Json = {};
  if (security.serverName) out.serverName = security.serverName;
  out.fingerprint = preQuantumFingerprint(security.fingerprint);
  out.publicKey = security.publicKey;
  out.shortId = security.shortId ?? "";
  out.spiderX = security.spiderX ?? DEFAULT_SPIDER_X;
  return out;
}

/** Xray renamed the TCP stream to `raw` and kept `tcp` as an alias; the current name is written. */
function networkName(transport: Transport): string {
  return transport.kind === "tcp" ? "raw" : transport.kind;
}

function streamSettings(outbound: VlessOutbound): Json {
  const { security, transport } = outbound;
  const out: Json = { network: networkName(transport), security: security.kind };
  if (security.kind === "tls") out.tlsSettings = tlsSettings(security);
  if (security.kind === "reality") out.realitySettings = realitySettings(security);
  if (transport.kind === "tcp") {
    out.rawSettings = { header: { type: transport.headerType ?? "none" } };
  } else if (transport.kind === "ws") {
    const ws: Json = { path: transport.path === "" ? "/" : transport.path };
    if (transport.host) ws.host = transport.host;
    out.wsSettings = ws;
  } else {
    out.grpcSettings = { serviceName: transport.serviceName, multiMode: transport.multiMode };
  }
  return out;
}

/** The outbounds Xray is started with: the user's server, and a blackhole for the helper's routing. */
export function xrayOutbounds(outbound: VlessOutbound): Json[] {
  const user: Json = { id: outbound.userId, encryption: "none", level: 0 };
  if (outbound.flow) user.flow = outbound.flow;
  return [
    {
      tag: TAG_PROXY,
      protocol: "vless",
      settings: {
        vnext: [{ address: outbound.endpoint.host, port: outbound.endpoint.port, users: [user] }],
      },
      streamSettings: streamSettings(outbound),
    },
    { tag: TAG_BLOCK, protocol: "blackhole" },
  ];
}
