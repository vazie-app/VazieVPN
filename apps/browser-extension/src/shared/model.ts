// The normalized configuration, as `vpn/api/XrayOutbound.kt` has it. Secrets are plain strings here: this model
// lives only inside the service worker's vault and the parser. Nothing the interface receives contains one.

export interface Endpoint {
  host: string;
  port: number;
}

export type SecurityKind = "none" | "tls" | "reality";
export type TransportKind = "tcp" | "ws" | "grpc";
export type XrayFlow = "xtls-rprx-vision";

export type Security =
  | { kind: "none" }
  | {
      kind: "tls";
      serverName: string | null;
      fingerprint: string | null;
      alpn: string[];
      allowInsecure: boolean;
    }
  | {
      kind: "reality";
      serverName: string | null;
      fingerprint: string | null;
      publicKey: string;
      shortId: string | null;
      spiderX: string | null;
    };

export type Transport =
  | { kind: "tcp"; headerType: string | null }
  | { kind: "ws"; path: string; host: string | null }
  | { kind: "grpc"; serviceName: string; multiMode: boolean };

export interface UnknownParameter {
  name: string;
  value: string;
}

export interface VlessOutbound {
  userId: string;
  endpoint: Endpoint;
  security: Security;
  transport: Transport;
  flow: XrayFlow | null;
  unknownParameters: UnknownParameter[];
}

export type ProfileOrigin = "imported_link";

export interface StoredProfile {
  id: string;
  name: string;
  origin: ProfileOrigin;
  createdAt: number;
  lastUsedAt: number | null;
  outbound: VlessOutbound;
}

/** What a draft is before it has a name and an id. */
export interface ProfileDraft {
  suggestedName: string | null;
  outbound: VlessOutbound;
}

export const PROTOCOL = { id: "vless", label: "VLESS", mark: "VL" } as const;

export function securityOf(outbound: VlessOutbound): Security {
  return outbound.security;
}

export function serverNameOf(security: Security): string | null {
  return security.kind === "none" ? null : security.serverName;
}

export function fingerprintOf(security: Security): string | null {
  return security.kind === "none" ? null : security.fingerprint;
}
