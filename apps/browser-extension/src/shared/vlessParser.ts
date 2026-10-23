import { LinkQuery } from "./linkQuery.ts";
import type { Endpoint, Security, Transport, XrayFlow } from "./model.ts";
import type { InvalidReason, ParseResult, UnsupportedFeature } from "./parseResult.ts";
import { decodeLenient, decodeStrict } from "./percent.ts";

const SCHEME = "vless://";
const SECURITY_KINDS = new Set(["none", "tls", "reality"]);
const TRANSPORT_KINDS: Record<string, "tcp" | "ws" | "grpc"> = {
  tcp: "tcp",
  ws: "ws",
  websocket: "ws",
  grpc: "grpc",
};
const UUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;
/** A domain name or an IPv4 literal, permissively: hosts are the user's, not ours to judge. */
const HOST = /^[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?$/;
/** Enough to tell an IPv6 literal from a typo; the OS resolves it, not this parser. */
const IPV6 = /^[0-9A-Fa-f:]*:[0-9A-Fa-f:.]*(?:%[A-Za-z0-9._-]+)?$/;
const MAX_NAME_LENGTH = 80;
const DEFAULT_WEBSOCKET_PATH = "/";

export function accepts(raw: string): boolean {
  return raw.slice(0, SCHEME.length).toLowerCase() === SCHEME;
}

const invalid = (reason: InvalidReason): ParseResult => ({ kind: "invalid", reason });
const unsupported = (feature: UnsupportedFeature): ParseResult => ({ kind: "unsupported", feature });

const trimOrNull = (text: string | null | undefined): string | null => {
  const trimmed = text?.trim();
  return trimmed ? trimmed : null;
};

const isTruthy = (text: string | null): boolean => text !== null && (text === "1" || text.toLowerCase() === "true");

type HostPort = { ok: true; endpoint: Endpoint } | { ok: false; reason: InvalidReason };

function parseHostPort(text: string): HostPort {
  if (text === "") return { ok: false, reason: { reason: "missingHost" } };
  let host: string;
  let rawPort: string;
  if (text.startsWith("[")) {
    const close = text.indexOf("]");
    if (close < 0) return { ok: false, reason: { reason: "malformedURI" } };
    host = text.slice(1, close);
    const after = text.slice(close + 1);
    if (after === "") return { ok: false, reason: { reason: "missingPort" } };
    if (!after.startsWith(":")) return { ok: false, reason: { reason: "malformedURI" } };
    rawPort = after.slice(1);
    if (host === "") return { ok: false, reason: { reason: "missingHost" } };
    if (!IPV6.test(host)) return { ok: false, reason: { reason: "malformedHost" } };
  } else {
    const colon = text.lastIndexOf(":");
    if (colon < 0) return { ok: false, reason: { reason: "missingPort" } };
    host = text.slice(0, colon);
    rawPort = text.slice(colon + 1);
    if (host === "") return { ok: false, reason: { reason: "missingHost" } };
    if (!HOST.test(host)) return { ok: false, reason: { reason: "malformedHost" } };
  }
  if (rawPort === "") return { ok: false, reason: { reason: "missingPort" } };
  const port = /^[0-9]+$/.test(rawPort) ? Number(rawPort) : NaN;
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    return { ok: false, reason: { reason: "invalidPort" } };
  }
  return { ok: true, endpoint: { host, port } };
}

/** Reads a `vless://` share link. */
export function parseVless(raw: string): ParseResult {
  if (!accepts(raw)) return invalid({ reason: "unknownScheme", scheme: null });

  let rest = raw.slice(SCHEME.length);
  const hash = rest.indexOf("#");
  const rawFragment = hash >= 0 ? rest.slice(hash + 1) : null;
  if (hash >= 0) rest = rest.slice(0, hash);

  const question = rest.indexOf("?");
  const rawQuery = question >= 0 ? rest.slice(question + 1) : "";
  const authority = question >= 0 ? rest.slice(0, question) : rest;

  const at = authority.lastIndexOf("@");
  if (at < 0) return invalid({ reason: "missingUserID" });
  const rawUserId = authority.slice(0, at);
  if (rawUserId === "") return invalid({ reason: "missingUserID" });

  const userId = decodeStrict(rawUserId);
  if (userId === null) return invalid({ reason: "malformedEncoding" });
  if (!UUID.test(userId)) return invalid({ reason: "malformedUserID" });

  const hostPort = parseHostPort(authority.slice(at + 1));
  if (!hostPort.ok) return invalid(hostPort.reason);
  const endpoint = hostPort.endpoint;

  const query = LinkQuery.parse(rawQuery);
  if (query === null) return invalid({ reason: "malformedEncoding" });

  const encryption = query.take("encryption")?.trim() ?? "";
  if (encryption !== "" && encryption.toLowerCase() !== "none") {
    return unsupported({ feature: "encryption", value: encryption });
  }

  const rawSecurity = (query.take("security")?.trim() ?? "") || "none";
  if (!SECURITY_KINDS.has(rawSecurity.toLowerCase())) {
    return unsupported({ feature: "security", value: rawSecurity });
  }
  const securityKind = rawSecurity.toLowerCase() as "none" | "tls" | "reality";

  const rawTransport = (query.take("type")?.trim() ?? "") || "tcp";
  const transportKind = TRANSPORT_KINDS[rawTransport.toLowerCase()];
  if (transportKind === undefined) return unsupported({ feature: "transport", value: rawTransport });

  const rawFlow = query.take("flow")?.trim() ?? "";
  let flow: XrayFlow | null = null;
  if (rawFlow !== "") {
    if (rawFlow.toLowerCase() === "xtls-rprx-vision") flow = "xtls-rprx-vision";
    else return unsupported({ feature: "flow", value: rawFlow });
  }

  // REALITY runs over TCP, HTTP/2 and gRPC; there is no WebSocket variant of it.
  if (securityKind === "reality" && transportKind === "ws") {
    return invalid({ reason: "conflictingParameters", parameters: ["security", "type"] });
  }
  if (flow !== null && securityKind === "none") {
    return invalid({ reason: "conflictingParameters", parameters: ["flow", "security"] });
  }
  if (flow !== null && transportKind !== "tcp") {
    return invalid({ reason: "conflictingParameters", parameters: ["flow", "type"] });
  }

  // `sni` is the parameter every current client writes; `peer` is what older ones wrote for the same field.
  const takeServerName = () => trimOrNull(query.take("sni", "peer"));

  let security: Security;
  if (securityKind === "none") {
    security = { kind: "none" };
  } else if (securityKind === "tls") {
    security = {
      kind: "tls",
      serverName: takeServerName(),
      fingerprint: trimOrNull(query.take("fp", "fingerprint")),
      alpn: (query.take("alpn") ?? "")
        .split(",")
        .map((item) => trimOrNull(item))
        .filter((item): item is string => item !== null),
      allowInsecure: isTruthy(query.take("allowInsecure", "insecure")),
    };
  } else {
    const publicKey = trimOrNull(query.take("pbk", "publicKey"));
    if (publicKey === null) return invalid({ reason: "missingParameter", parameter: "pbk" });
    security = {
      kind: "reality",
      serverName: takeServerName(),
      fingerprint: trimOrNull(query.take("fp", "fingerprint")),
      publicKey,
      shortId: trimOrNull(query.take("sid", "shortId")),
      spiderX: trimOrNull(query.take("spx", "spiderX")),
    };
  }

  let transport: Transport;
  if (transportKind === "tcp") {
    transport = { kind: "tcp", headerType: trimOrNull(query.take("headerType")) };
  } else if (transportKind === "ws") {
    transport = {
      kind: "ws",
      path: trimOrNull(query.take("path")) ?? DEFAULT_WEBSOCKET_PATH,
      host: trimOrNull(query.take("host")),
    };
  } else {
    transport = {
      kind: "grpc",
      serviceName: query.take("serviceName")?.trim() ?? "",
      multiMode: (query.take("mode")?.trim() ?? "").toLowerCase() === "multi",
    };
  }

  const suggestedName = (() => {
    if (rawFragment === null) return null;
    const decoded = decodeLenient(rawFragment).trim();
    return decoded === "" ? null : Array.from(decoded).slice(0, MAX_NAME_LENGTH).join("");
  })();

  return {
    kind: "recognized",
    draft: {
      suggestedName,
      outbound: {
        userId,
        endpoint,
        security,
        transport,
        flow,
        unknownParameters: query.remaining().map(([name, value]) => ({ name, value })),
      },
    },
  };
}
