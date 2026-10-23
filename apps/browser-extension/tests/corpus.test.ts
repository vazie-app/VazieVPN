// Runs the corpus in `/shared/vless-vectors`, the one every client reads. The Android client runs the same files
// (`SharedVectorCorpusTest.kt`); a vector that one parser reads differently from the other fails here or there.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { parseConfig } from "../src/shared/parsers.ts";

const corpus = fileURLToPath(new URL("../../../shared/vless-vectors/", import.meta.url));
const GROUPS = ["recognized", "unknown-parameters", "unsupported", "invalid"];

type Json = Record<string, unknown>;

function vectors(group: string): Json[] {
  const file = JSON.parse(readFileSync(`${corpus}${group}.json`, "utf8")) as { vectors: Json[] };
  return file.vectors;
}

// A key that is absent is not checked; a key that is `null` must be null in the result too.
function compare(label: string, expect: Json, key: string, actual: unknown, problems: string[]) {
  if (!(key in expect)) return;
  const expected = expect[key];
  try {
    assert.deepEqual(actual ?? null, expected);
  } catch {
    problems.push(`${label}: ${key} expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual ?? null)}`);
  }
}

function check(group: string, vector: Json): string[] {
  const label = `${group}/${vector.name as string}`;
  const expect = vector.expect as Json;
  const result = parseConfig(vector.input as string);
  const problems: string[] = [];
  if (result.kind !== expect.kind) return [`${label}: expected ${expect.kind as string}, got ${result.kind}`];

  if (result.kind === "recognized") {
    const { suggestedName, outbound } = result.draft;
    compare(label, expect, "protocol", "vless", problems);
    compare(label, expect, "host", outbound.endpoint.host, problems);
    compare(label, expect, "port", outbound.endpoint.port, problems);
    compare(label, expect, "security", outbound.security.kind, problems);
    compare(label, expect, "transport", outbound.transport.kind, problems);
    compare(label, expect, "flow", outbound.flow, problems);
    compare(label, expect, "serverName", outbound.security.kind === "none" ? null : outbound.security.serverName, problems);
    compare(label, expect, "fingerprint", outbound.security.kind === "none" ? null : outbound.security.fingerprint, problems);
    compare(label, expect, "suggestedName", suggestedName, problems);
    compare(label, expect, "unknownParameterNames", outbound.unknownParameters.map((p) => p.name), problems);
    const security = outbound.security;
    if (security.kind === "tls") {
      compare(label, expect, "alpn", security.alpn, problems);
      compare(label, expect, "allowInsecure", security.allowInsecure, problems);
    } else if (security.kind === "reality") {
      compare(label, expect, "spiderX", security.spiderX, problems);
    }
    const transport = outbound.transport;
    if (transport.kind === "tcp") compare(label, expect, "tcpHeaderType", transport.headerType, problems);
    else if (transport.kind === "ws") {
      compare(label, expect, "wsPath", transport.path, problems);
      compare(label, expect, "wsHost", transport.host, problems);
    } else {
      compare(label, expect, "grpcServiceName", transport.serviceName, problems);
      compare(label, expect, "grpcMultiMode", transport.multiMode, problems);
    }
  } else if (result.kind === "unsupported") {
    compare(label, expect, "protocol", "vless", problems);
    compare(label, expect, "feature", result.feature.feature, problems);
    compare(label, expect, "value", result.feature.value, problems);
  } else {
    const reason = result.reason;
    compare(label, expect, "reason", reason.reason, problems);
    if (reason.reason === "unknownScheme") compare(label, expect, "scheme", reason.scheme, problems);
    if (reason.reason === "missingParameter") compare(label, expect, "parameter", reason.parameter, problems);
    if (reason.reason === "conflictingParameters") compare(label, expect, "parameters", reason.parameters, problems);
  }
  return problems;
}

for (const group of GROUPS) {
  test(`the ${group} vectors read as agreed`, () => {
    const list = vectors(group);
    assert.ok(list.length > 0, `${group} contributed no vectors`);
    const problems = list.flatMap((vector) => check(group, vector));
    assert.deepEqual(problems, [], "the shared corpus disagrees with this parser:\n" + problems.join("\n"));
  });
}
