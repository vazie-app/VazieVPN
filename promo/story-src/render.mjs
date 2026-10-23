import { chromium } from "/Users/vazovsky/Vazi Ecosystem/Apps/vpn-android/apps/browser-extension/node_modules/playwright-core/index.mjs";
import { mkdirSync } from "node:fs";
mkdirSync("frames", { recursive: true });
const FPS = 30;
const browser = await chromium.launch({ executablePath: process.env.EXE });
const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
await page.goto("file://" + process.cwd() + "/story.html");
await page.evaluate(() => document.fonts.ready);
const total = await page.evaluate(() => window.TOTAL);
const n = total * FPS;
for (let i = 0; i < n; i++) {
  await page.evaluate((t) => window.seek(t), i / FPS);
  await page.screenshot({ path: `frames/f_${String(i).padStart(4, "0")}.png` });
}
await browser.close();
console.log("frames", n);
