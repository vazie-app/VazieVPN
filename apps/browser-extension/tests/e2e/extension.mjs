// The extension in a real browser, with a real helper and a real engine, end to end:
//   - a local Xray plays "the user's server";
//   - Chrome for Testing loads the built extension and starts the built helper over native messaging;
//   - the interface adds a configuration, connects, and a request from the browser reaches the internet through
//     the tunnel; then it disconnects and the browser's own proxy settings come back.
//
//   npm i --no-save playwright-core
//   node scripts/build.mjs --development
//   (cd ../native-host && cargo build)
//   HOME=<a directory whose engine is installed: vazie-vpn-host engine> node tests/e2e/extension.mjs [--screenshots <dir>]
import { spawn } from "node:child_process";
import assert from "node:assert/strict";
import { existsSync, mkdirSync, mkdtempSync, readdirSync, writeFileSync } from "node:fs";
import { createServer } from "node:net";
import { tmpdir, userInfo } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright-core";

const ROOT = fileURLToPath(new URL("../..", import.meta.url));
const EXT = join(ROOT, "dist/chromium");
const HOST_BIN = process.env.HOST_BIN ?? join(ROOT, "../native-host/target/debug/vazie-vpn-host");
const DEV_ID = "apmoogcaoomcoaomkacmlngnknmgieeg";
const HOME = process.env.HOME;
const shots = process.argv.includes("--screenshots") ? process.argv[process.argv.indexOf("--screenshots") + 1] : null;
if (shots) mkdirSync(shots, { recursive: true });

function chromePath() {
  if (process.env.CHROME) return process.env.CHROME;
  // The helper runs under a scratch HOME; the browser is found under the real one.
  const cache = join(userInfo().homedir, "Library/Caches/ms-playwright");
  for (const dir of readdirSync(cache).filter((d) => d.startsWith("chromium-")).sort().reverse()) {
    for (const arch of ["chrome-mac-arm64", "chrome-mac-x64"]) {
      const p = join(cache, dir, arch, "Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing");
      if (existsSync(p)) return p;
    }
  }
  throw new Error("no Chrome for Testing; set CHROME");
}

const engineDir = process.platform === "darwin"
  ? join(HOME, "Library/Application Support/Vazie VPN/engine/v26.7.28")
  : join(process.env.XDG_DATA_HOME ?? join(HOME, ".local/share"), "vazie-vpn/engine/v26.7.28");
const XRAY = join(engineDir, "xray");
assert.ok(existsSync(XRAY), `no engine at ${XRAY}`);
assert.ok(existsSync(join(EXT, "manifest.json")), "build the extension first");
assert.ok(existsSync(HOST_BIN), "build the helper first");

const freePort = () => new Promise((resolve) => {
  const s = createServer().listen(0, "127.0.0.1", () => { const p = s.address().port; s.close(() => resolve(p)); });
});

// the "server"
const UUID = "5783a3e7-e373-51cd-8642-c83782b807c5";
const serverPort = await freePort();
const server = spawn(XRAY, ["run", "-config", "stdin:"], { stdio: ["pipe", "ignore", "inherit"] });
server.stdin.end(JSON.stringify({
  log: { loglevel: "none" },
  dns: { servers: ["1.1.1.1", "8.8.8.8"] },
  inbounds: [{ listen: "127.0.0.1", port: serverPort, protocol: "vless", settings: { clients: [{ id: UUID }], decryption: "none" } }],
  outbounds: [{ protocol: "freedom", settings: { domainStrategy: "UseIPv4" } }],
}));
await new Promise((r) => setTimeout(r, 800));

// the browser, with the helper registered in its profile the way `vazie-vpn-host install` registers it
const profile = mkdtempSync(join(tmpdir(), "vazie-e2e-"));
mkdirSync(join(profile, "NativeMessagingHosts"), { recursive: true });
writeFileSync(join(profile, "NativeMessagingHosts/app.vazie.vpn.json"), JSON.stringify({
  name: "app.vazie.vpn", description: "test", path: HOST_BIN, type: "stdio",
  allowed_origins: [`chrome-extension://${DEV_ID}/`],
}));

const context = await chromium.launchPersistentContext(profile, {
  executablePath: chromePath(),
  headless: true,
  env: { ...process.env, HOME },
  args: [`--disable-extensions-except=${EXT}`, `--load-extension=${EXT}`, "--headless=new", "--no-first-run", "--no-default-browser-check"],
  viewport: { width: 380, height: 580 },
});
const logs = [];
let failed = false;
try {
  let worker = context.serviceWorkers()[0];
  worker ??= await context.waitForEvent("serviceworker", { timeout: 15000 });
  const id = new URL(worker.url()).host;
  assert.equal(id, DEV_ID, "the development key gives the extension its known id");
  context.on("page", (p) => {
    p.on("console", (m) => ["error", "warning"].includes(m.type()) && logs.push(`[${m.type()}] ${m.text()}`));
    p.on("pageerror", (e) => logs.push(`[pageerror] ${e.message}`));
  });

  // a fresh install opens the welcome in a tab; use our own page
  const page = await context.newPage();
  await page.goto(`chrome-extension://${id}/popup.html`);
  const shot = async (name) => shots && (await page.screenshot({ path: join(shots, `${name}.png`) }));
  const text = async (re) => page.getByText(re).first();

  // first run: four pages, then the app
  await page.getByText("Hi, I'm Vazi").waitFor();
  await shot("01-onboarding");
  await page.getByRole("button", { name: "Next" }).click();
  await page.getByRole("button", { name: "Next" }).click();
  await page.getByRole("button", { name: "Next" }).click();
  await page.getByText("Vazie VPN without opening the app").waitFor();
  await page.getByRole("button", { name: "Start using Vazie VPN" }).click();
  await page.getByText("Add your first config").waitFor();
  await shot("02-home-empty");
  console.log("onboarding → empty home: ok");

  // the helper is found, with its engine
  await page.goto(`chrome-extension://${id}/popup.html#/settings/helper`);
  await page.getByText("Xray 26.7.28").waitFor({ timeout: 15000 });
  await shot("03-helper");
  console.log("helper found over native messaging, engine Xray 26.7.28: ok");

  // add a configuration
  await page.goto(`chrome-extension://${id}/popup.html#/add`);
  await page.locator("textarea").fill(`vless://${UUID}@127.0.0.1:${serverPort}?security=none&type=tcp#Home%20server`);
  await shot("04-add");
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByText("Home server").first().waitFor();
  await shot("05-preview");
  await page.getByRole("button", { name: "Continue" }).click();
  await shot("06-review");
  await page.getByRole("button", { name: "Save configuration" }).click();
  await page.getByText("Home server added").waitFor();
  await page.getByRole("button", { name: "Done" }).click();
  await page.getByText("Not connected").waitFor();
  console.log("add configuration → home: ok");

  // the tour runs once over the real interface
  await page.getByText("Connect and disconnect").waitFor();
  await shot("07-tour");
  await page.getByRole("button", { name: "Skip tour" }).click();
  await shot("08-home");

  // connect, through the real helper and engine
  await page.getByRole("button", { name: "Connect" }).click();
  // the one-time notification explanation appears at the first Connect
  const notNow = page.getByRole("button", { name: "Not now" });
  if (await notNow.isVisible().catch(() => false)) await notNow.click();
  await page.getByRole("heading", { name: "Connected", exact: true }).waitFor({ timeout: 40000 });
  await shot("09-connected");
  if (process.env.DEBUG_E2E) {
    worker.on("console", (m) => console.log("[sw]", m.text()));
    console.log("proxy now:", JSON.stringify(await page.evaluate(async () => await chrome.proxy.settings.get({}))));
    console.log("state:", JSON.stringify(await page.evaluate(async () => (await chrome.runtime.sendMessage({ t: "rpc", m: "getState", a: [] })).v.snapshot)));
  }
  const control = await page.evaluate(async () => (await chrome.proxy.settings.get({})).levelOfControl);
  assert.equal(control, "controlled_by_this_extension");
  const mode = await page.evaluate(async () => (await chrome.proxy.settings.get({})).value.mode);
  assert.equal(mode, "pac_script");
  console.log("connected; the browser's proxy is ours (PAC): ok");

  // a request from the browser reaches the internet through the tunnel, and the engine counted it
  const reached = await page.evaluate(async () => {
    try {
      await fetch("https://cp.cloudflare.com/generate_204", { mode: "no-cors", cache: "no-store" });
      return true;
    } catch { return false; }
  });
  assert.equal(reached, true, "a browser request through the tunnel");
  await page.locator(".route .details").click();
  await page.getByText("Connection details").waitFor();
  await page.getByText("RX / TX").waitFor();
  await shot("10-details");
  const row = await page.locator(".detail-row", { hasText: "RX / TX" }).innerText();
  assert.doesNotMatch(row, /0\s*B\s*\/\s*0\s*B/, `traffic moved: ${row}`);
  console.log("browser traffic through the tunnel, counters moved:", row.replace(/\s+/g, " "));

  // the tab's site can be split out while connected
  await page.goto(`chrome-extension://${id}/popup.html#/settings/split`);
  await page.getByText("All but the chosen sites").click();
  await page.locator("input").fill("cp.cloudflare.com");
  await page.getByRole("button", { name: "Add", exact: true }).click();
  await page.getByText("cp.cloudflare.com").first().waitFor();
  const pac = await page.evaluate(async () => (await chrome.proxy.settings.get({})).value.pacScript.data);
  assert.match(pac, /"exclude"/);
  assert.match(pac, /cp\.cloudflare\.com/);
  await shot("11-split");
  console.log("split tunneling applied to the live PAC: ok");

  // disconnect: the browser's own settings come back
  await page.goto(`chrome-extension://${id}/popup.html`);
  await page.getByRole("button", { name: "Disconnect" }).click();
  await page.getByRole("heading", { name: "Not connected", exact: true }).waitFor({ timeout: 15000 });
  const after = await page.evaluate(async () => (await chrome.proxy.settings.get({})).levelOfControl);
  assert.equal(after, "controllable_by_this_extension");
  console.log("disconnected; proxy settings restored: ok");

  // the right-click menu and the title follow the state
  const title = await worker.evaluate(() => chrome.action.getTitle({}));
  assert.match(title, /Not protected/);

  // appearance
  await page.goto(`chrome-extension://${id}/popup.html#/settings/appearance`);
  await page.getByText("Milk").first().click();
  await page.waitForFunction(() => document.documentElement.dataset.theme === "milk");
  await shot("12-appearance-milk");
  await page.goto(`chrome-extension://${id}/popup.html#/settings`);
  await shot("13-settings-milk");
  await page.goto(`chrome-extension://${id}/popup.html`);
  await shot("14-home-milk");

  // a missing helper is a state of its own, not a crash
  assert.deepEqual(logs.filter((l) => !/Failed to load resource|favicon/.test(l)), [], "no console errors");
  console.log("e2e: ok");
} catch (error) {
  failed = true;
  console.error(error);
  console.error(logs.join("\n"));
} finally {
  await context.close();
  server.kill();
  process.exit(failed ? 1 : 0);
}
