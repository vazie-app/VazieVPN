import type { ConfigPreview } from "./api.ts";
import { PROTOCOL, type ProfileDraft, type SecurityKind, type TransportKind } from "./model.ts";

export function securityLabel(kind: SecurityKind): string | null {
  return kind === "none" ? null : kind === "tls" ? "TLS" : "REALITY";
}

export function transportLabel(kind: TransportKind): string {
  return kind === "tcp" ? "TCP" : kind === "ws" ? "WebSocket" : "gRPC";
}

/** Safe fields only: there is no field here that could hold a user id, a key or a short id, so a preview card
 * cannot draw one however it is written — the type is the guarantee. */
export function toPreview(draft: ProfileDraft): ConfigPreview {
  const { outbound } = draft;
  const security = outbound.security;
  const endpoint = outbound.endpoint;
  return {
    protocolLabel: PROTOCOL.label,
    securityLabel: securityLabel(security.kind),
    transportLabel: transportLabel(outbound.transport.kind),
    server: `${endpoint.host}:${endpoint.port}`,
    endpointHost: endpoint.host,
    endpointPort: endpoint.port,
    serverName: security.kind === "none" ? null : security.serverName,
    fingerprint: security.kind === "none" ? null : security.fingerprint,
    flow: outbound.flow,
    // A link with no fragment still has to be called something, and the host is what the user would call it.
    suggestedName: draft.suggestedName ?? endpoint.host,
    mark: PROTOCOL.mark,
    keptParameters: outbound.unknownParameters.map((p) => p.name),
  };
}
