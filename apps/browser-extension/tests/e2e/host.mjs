// End-to-end check of the native helper against a real Xray, with no network service of Vazie's involved:
//   1. a local Xray plays "the user's server" (VLESS in, freedom out);
//   2. the helper is started the way Chrome starts it and speaks native messaging;
//   3. the extension's own config code builds the outbound, the helper starts the tunnel, and a request through the
//      helper's authenticated loopback proxy reaches the internet.
//
//   HOME=<dir with the engine installed> HOST_BIN=../native-host/target/debug/vazie-vpn-host node tests/e2e/host.mjs
//
// It needs the engine installed for that HOME (`vazie-vpn-host engine`) and a working internet connection.
import { spawn } from "node:child_process";
import { createServer } from "node:net";
import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { parseConfig } from "../../src/shared/parsers.ts";
import { xrayOutbounds } from "../../src/shared/xrayConfig.ts";

const HOST_BIN = process.env.HOST_BIN ?? fileURLToPath(new URL("../../../native-host/target/debug/vazie-vpn-host", import.meta.url));
const HOME = process.env.HOME;
const engineDir = process.platform === "darwin"
  ? join(HOME, "Library/Application Support/Vazie VPN/engine/v26.7.28")
  : join(process.env.XDG_DATA_HOME ?? join(HOME, ".local/share"), "vazie-vpn/engine/v26.7.28");
const XRAY = join(engineDir, "xray");
assert.ok(existsSync(XRAY), `no engine at ${XRAY}; run the helper's \`engine\` command with this HOME first`);

const freePort = () => new Promise((resolve) => {
  const s = createServer().listen(0, "127.0.0.1", () => { const p = s.address().port; s.close(() => resolve(p)); });
});

// 1. the "server"
const UUID = "5783a3e7-e373-51cd-8642-c83782b807c5";
const serverPort = await freePort();
const server = spawn(XRAY, ["run", "-config", "stdin:"], { stdio: ["pipe", "ignore", "inherit"] });
server.stdin.end(JSON.stringify({
  log: { loglevel: "none" },
  // The "server" resolves names itself, the way a real one does, rather than through whatever this machine has.
  dns: { servers: ["1.1.1.1", "8.8.8.8"] },
  inbounds: [{ listen: "127.0.0.1", port: serverPort, protocol: "vless", settings: { clients: [{ id: UUID }], decryption: "none" } }],
  outbounds: [{ protocol: "freedom", settings: { domainStrategy: "UseIPv4" } }],
}));
await new Promise((r) => setTimeout(r, 800));

// 2. the helper, started as Chrome starts it
const host = spawn(HOST_BIN, ["chrome-extension://aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/"], { env: { ...process.env, HOME }, stdio: ["pipe", "pipe", "inherit"] });
const waiting = new Map();
const events = [];
let buffer = Buffer.alloc(0);
host.stdout.on("data", (chunk) => {
  buffer = Buffer.concat([buffer, chunk]);
  while (buffer.length >= 4) {
    const length = buffer.readUInt32LE(0);
    if (buffer.length < 4 + length) break;
    const message = JSON.parse(buffer.subarray(4, 4 + length).toString());
    buffer = buffer.subarray(4 + length);
    if (message.event) events.push(message);
    else waiting.get(message.id)?.(message);
  }
});
let nextId = 1;
const call = (message) => new Promise((resolve) => {
  const id = nextId++;
  waiting.set(id, resolve);
  const body = Buffer.from(JSON.stringify({ id, ...message }));
  const head = Buffer.alloc(4);
  head.writeUInt32LE(body.length);
  host.stdin.write(Buffer.concat([head, body]));
});

let failed = false;
try {
  const hello = await call({ type: "hello" });
  assert.equal(hello.ok, true);
  assert.equal(hello.engine.installed, true, "engine installed");
  console.log("hello:", hello.version, hello.platform, "engine", hello.engine.version);

  // a configuration that cannot run is refused by the helper, not the engine
  assert.equal((await call({ type: "start", outbounds: [{ tag: "x", protocol: "freedom" }] })).code, "request");

  const parsed = parseConfig(`vless://${UUID}@127.0.0.1:${serverPort}?security=none&type=tcp#test`);
  assert.equal(parsed.kind, "recognized");
  const outbounds = xrayOutbounds(parsed.draft.outbound);
  const started = await call({ type: "start", outbounds });
  assert.equal(started.ok, true, JSON.stringify(started));
  console.log("started on", started.port);

  if (process.env.DEBUG_CURL) {
    const c = spawn("curl", ["--noproxy", "", "-sS", "-m", "12", "-o", "/dev/null", "-x", `http://127.0.0.1:${started.port}`, process.env.DEBUG_URL ?? "https://cp.cloudflare.com/generate_204"], { stdio: ["ignore", "ignore", "inherit"] });
    await new Promise((r) => c.on("close", r));
  }
  const ready = await call({ type: "ready" });
  assert.equal(ready.ok, true, JSON.stringify(ready));
  console.log("readiness: passed (TLS to an address, then a name, through the tunnel)");

  await call({ type: "traffic" });
  const traffic = await call({ type: "traffic" });
  assert.equal(traffic.ok, true, JSON.stringify(traffic));
  assert.ok(traffic.rx > 0 && traffic.tx > 0, `bytes moved: ${JSON.stringify(traffic)}`);
  console.log("traffic:", traffic.rx, "down /", traffic.tx, "up");

  const probe = await call({ type: "probe", host: "127.0.0.1", port: serverPort });
  assert.equal(probe.ok, true);
  assert.ok(typeof probe.ms === "number", "latency answered");
  assert.equal((await call({ type: "probe", host: "127.0.0.1", port: 1 })).ms, null);
  console.log("latency:", probe.ms, "ms");

  assert.equal((await call({ type: "stop" })).ok, true);
  assert.equal((await call({ type: "ready" })).code, "not_running");

  console.log("e2e: ok");
} catch (error) {
  failed = true;
  console.error(error);
} finally {
  host.stdin.end();
  server.kill();
  await new Promise((r) => setTimeout(r, 300));
  process.exit(failed ? 1 : 0);
}
