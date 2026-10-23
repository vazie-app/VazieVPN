import type { Security, Transport, VlessOutbound } from "./model.ts";
import { encode, FRAGMENT, QUERY, USER_INFO } from "./percent.ts";

type Pairs = [string, string][];

function securityParameters(security: Security): Pairs {
  const out: Pairs = [];
  if (security.kind === "none") return out;
  if (security.serverName) out.push(["sni", security.serverName]);
  if (security.fingerprint) out.push(["fp", security.fingerprint]);
  if (security.kind === "tls") {
    if (security.alpn.length > 0) out.push(["alpn", security.alpn.join(",")]);
    if (security.allowInsecure) out.push(["allowInsecure", "1"]);
  } else {
    out.push(["pbk", security.publicKey]);
    if (security.shortId) out.push(["sid", security.shortId]);
    if (security.spiderX) out.push(["spx", security.spiderX]);
  }
  return out;
}

function transportParameters(transport: Transport): Pairs {
  switch (transport.kind) {
    case "tcp":
      return transport.headerType ? [["headerType", transport.headerType]] : [];
    case "ws": {
      const out: Pairs = [["path", transport.path]];
      if (transport.host) out.push(["host", transport.host]);
      return out;
    }
    case "grpc": {
      const out: Pairs = [];
      if (transport.serviceName !== "") out.push(["serviceName", transport.serviceName]);
      if (transport.multiMode) out.push(["mode", "multi"]);
      return out;
    }
  }
}

/** Turns a stored configuration back into the `vless://` link it came from. */
export function serializeVless(outbound: VlessOutbound, name: string | null): string {
  const parameters: Pairs = [
    // Written even though the parser treats an absent value the same way: other clients are less forgiving.
    ["encryption", "none"],
    ["type", outbound.transport.kind],
    ["security", outbound.security.kind],
    ...securityParameters(outbound.security),
    ...transportParameters(outbound.transport),
  ];
  if (outbound.flow) parameters.push(["flow", outbound.flow]);
  // An unknown parameter is somebody else's field: hand it back exactly as it arrived.
  for (const entry of outbound.unknownParameters) parameters.push([entry.name, entry.value]);

  const host = outbound.endpoint.host.includes(":") ? `[${outbound.endpoint.host}]` : outbound.endpoint.host;
  let link = `vless://${encode(outbound.userId, USER_INFO)}@${host}:${outbound.endpoint.port}`;
  parameters.forEach(([key, value], index) => {
    link += `${index === 0 ? "?" : "&"}${encode(key, QUERY)}=${encode(value, QUERY)}`;
  });
  const label = name?.trim();
  if (label) link += `#${encode(label, FRAGMENT)}`;
  return link;
}
