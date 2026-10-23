// The extension without its helper: a missing helper is a state of its own, said in words, not a crash.
//   HOME=<any> node tests/e2e/no-helper.mjs
import assert from "node:assert/strict";
import { existsSync, mkdtempSync, readdirSync } from "node:fs";
import { tmpdir, userInfo } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright-core";

const EXT = join(fileURLToPath(new URL("../..", import.meta.url)), "dist/chromium");
const cache = join(userInfo().homedir, "Library/Caches/ms-playwright");
const chrome = process.env.CHROME ?? readdirSync(cache).filter((d) => d.startsWith("chromium-")).sort().reverse()
  .flatMap((d) => ["chrome-mac-arm64", "chrome-mac-x64"].map((a) => join(cache, d, a, "Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing")))
  .find(existsSync);

// no NativeMessagingHosts manifest in this profile: there is no helper
const context = await chromium.launchPersistentContext(mkdtempSync(join(tmpdir(), "vazie-e2e-")), {
  executablePath: chrome, headless: true, viewport: { width: 380, height: 580 },
  args: [`--disable-extensions-except=${EXT}`, `--load-extension=${EXT}`, "--headless=new", "--no-first-run"],
});
let failed = false;
let page;
try {
  const worker = context.serviceWorkers()[0] ?? (await context.waitForEvent("serviceworker", { timeout: 15000 }));
  const id = new URL(worker.url()).host;
  // whatever the worker complains about: the toolbar icon, the menu and the title are drawn there
  const workerErrors = [];
  worker.on("console", (m) => m.type() === "error" && workerErrors.push(m.text()));
  page = await context.newPage();
  await page.goto(`chrome-extension://${id}/popup.html`);
  await page.getByRole("button", { name: "Skip" }).click();

  await page.goto(`chrome-extension://${id}/popup.html#/add`);
  await page.locator("textarea").fill("vless://5783a3e7-e373-51cd-8642-c83782b807c5@relay.example.net:443?security=none#Relay");
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByRole("button", { name: "Save configuration" }).click();
  await page.getByRole("button", { name: "Done" }).click();
  await page.getByRole("button", { name: "Skip tour" }).click();

  await page.getByRole("button", { name: "Connect" }).click();
  const notNow = page.getByRole("button", { name: "Not now" });
  if (await notNow.isVisible().catch(() => false)) await notNow.click();
  await page.getByRole("heading", { name: "Couldn't connect" }).waitFor({ timeout: 20000 });
  await page.getByText("The Vazie VPN helper isn't installed or couldn't start").first().waitFor();
  await page.getByText("Install the helper").first().waitFor();
  assert.equal(await page.evaluate(async () => (await chrome.proxy.settings.get({})).levelOfControl), "controllable_by_this_extension", "the browser's proxy was never touched");
  console.log("no helper: said so, proxy untouched: ok");

  await page.getByRole("link", { name: "Set it up" }).click();
  const download = page.getByRole("link", { name: /Download the installer for (macOS|Windows|Linux)/ });
  await download.waitFor();
  assert.match(await download.getAttribute("href"), /\/(VazieVPNHelper\.pkg|vazie-vpn-helper-(windows|linux)-(x64|arm64)(\.exe)?)$/);
  await page.getByText("Looking for the helper…").waitFor();
  if (process.env.SHOT) await page.screenshot({ path: process.env.SHOT });
  console.log("the Helper screen offers one download for this system and looks for the helper itself: ok");

  // a leftover from a killed worker is cleared at the next start
  await page.evaluate(async () => chrome.proxy.settings.set({ value: { mode: "pac_script", pacScript: { data: "function FindProxyForURL(){return 'PROXY 127.0.0.1:9'}" } }, scope: "regular" }));
  assert.equal(await page.evaluate(async () => (await chrome.proxy.settings.get({})).levelOfControl), "controlled_by_this_extension");
  const cdp = await context.newCDPSession(page);
  await cdp.send("ServiceWorker.enable");
  await cdp.send("ServiceWorker.stopAllWorkers");
  // the page's port wakes the worker again; starting it is what clears a leftover
  const page2 = await context.newPage();
  await page2.goto(`chrome-extension://${id}/popup.html`);
  // any call wakes a stopped worker; starting it is what clears the leftover
  await page2.evaluate(async () => chrome.runtime.sendMessage({ t: "rpc", m: "getState", a: [] }));
  const level = () => page2.evaluate(async () => (await chrome.proxy.settings.get({})).levelOfControl);
  for (let attempt = 0; attempt < 30 && (await level()) !== "controllable_by_this_extension"; attempt++) {
    await new Promise((r) => setTimeout(r, 500));
  }
  const after = await page2.evaluate(async () => (await chrome.proxy.settings.get({})).levelOfControl);
  assert.equal(after, "controllable_by_this_extension", "a proxy left by a dead tunnel is cleared when the worker starts");
  console.log("proxy left behind by a dead tunnel is cleared at start: ok");

  // every icon on every plate it was checked against draws without a complaint, and the title follows the state
  for (const [icon, plate] of [["orbit", "deep"], ["monochrome", "light"], ["monochrome", "ink"], ["gold", "amber"], ["pearl", "light"]]) {
    await page2.evaluate(async ([appIcon, appIconPlate]) => chrome.runtime.sendMessage({ t: "rpc", m: "setPrefs", a: [{ appIcon, appIconPlate }] }), [icon, plate]);
    await new Promise((r) => setTimeout(r, 250));
  }
  const live = (await context.serviceWorkers()).find((w) => w.url().includes(id)) ?? worker;
  assert.match(await live.evaluate(() => chrome.action.getTitle({})), /Vazie VPN — /);
  assert.deepEqual(workerErrors, [], "no errors in the service worker");
  console.log("toolbar icons draw for the icon/plate choices; no worker errors: ok");
  console.log("e2e: ok");
} catch (error) {
  failed = true;
  console.error(error);
  console.error("--- page text ---\n" + (await page?.locator("body").innerText().catch(() => "")));
} finally {
  await context.close();
  process.exit(failed ? 1 : 0);
}
