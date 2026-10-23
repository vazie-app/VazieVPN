import type { ProfileDraft } from "./model.ts";

export type UnsupportedFeature =
  | { feature: "security"; value: string }
  | { feature: "transport"; value: string }
  | { feature: "flow"; value: string }
  | { feature: "encryption"; value: string };

export type InvalidReason =
  | { reason: "empty" }
  | { reason: "unknownScheme"; scheme: string | null }
  | { reason: "malformedURI" }
  | { reason: "missingUserID" }
  | { reason: "malformedUserID" }
  | { reason: "missingHost" }
  | { reason: "malformedHost" }
  | { reason: "missingPort" }
  | { reason: "invalidPort" }
  | { reason: "malformedEncoding" }
  | { reason: "missingParameter"; parameter: string }
  | { reason: "conflictingParameters"; parameters: string[] };

export type ParseResult =
  | { kind: "recognized"; draft: ProfileDraft }
  | { kind: "unsupported"; feature: UnsupportedFeature }
  | { kind: "invalid"; reason: InvalidReason };
