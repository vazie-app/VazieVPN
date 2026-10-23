// Builds the extension into dist/chromium.
//   node scripts/build.mjs                  minified, with the development key: load it unpacked, and the extension id
//                                           is always apmoogcaoomcoaomkacmlngnknmgieeg (dist/chromium)
//   node scripts/build.mjs --development    the same, not minified, with source maps
//   node scripts/build.mjs --watch          rebuild on change (development)
//   node scripts/build.mjs --store          for the Chrome Web Store: no key (the store gives its own id) → dist/store
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { build } from "vite";
import { manifest } from "../src/manifest.ts";

const root = fileURLToPath(new URL("..", import.meta.url));
const args = new Set(process.argv.slice(2));
const watch = args.has("--watch");
const development = args.has("--development") || watch;
const store = args.has("--store");
const out = `${root}dist/${store ? "store" : "chromium"}`;
const { version } = JSON.parse(readFileSync(`${root}package.json`, "utf8"));
// The official addresses are not in the repository: a store build must be given them, a development build uses
// placeholders (the same ones CI uses).
const apiUrl = process.env.VAZIE_API_URL || (store ? "" : "https://api.example.com/v1");
const siteUrl = process.env.VAZIE_SITE_URL || (store ? "" : "https://example.com");
// The installers are files of the release with the same version as the extension (`v<version>`, put there by
// `release-extension.yml`), so a build always links to the helper that was made with it.
const helperUrl = process.env.VAZIE_HELPER_URL ?? `https://github.com/vazie-app/VazieVPN/releases/download/v${version}`;

if (!/^https:\/\/[^/]+(\/.*)?$/.test(apiUrl)) throw new Error("VAZIE_API_URL must be an https address");
if (!/^https:\/\/[^/]+(\/.*)?$/.test(siteUrl)) throw new Error("VAZIE_SITE_URL must be an https address");

await build({
  root: `${root}src/ui`,
  configFile: false,
  base: "./",
  publicDir: `${root}public`,
  logLevel: "info",
  define: { __VAZIE_API_URL__: JSON.stringify(apiUrl), __VAZIE_SITE_URL__: JSON.stringify(siteUrl), __VAZIE_HELPER_URL__: JSON.stringify(helperUrl) },
  oxc: { jsx: { runtime: "automatic", importSource: "preact" } },
  build: {
    outDir: out,
    emptyOutDir: true,
    target: "chrome116",
    minify: !development,
    sourcemap: development,
    modulePreload: false,
    cssCodeSplit: false,
    watch: watch ? {} : null,
    rollupOptions: {
      input: {
        popup: `${root}src/ui/popup.html`,
        app: `${root}src/ui/app.html`,
        sidepanel: `${root}src/ui/sidepanel.html`,
        background: `${root}src/background/index.ts`,
      },
      output: {
        entryFileNames: "[name].js",
        chunkFileNames: "chunks/[name]-[hash].js",
        assetFileNames: "assets/[name]-[hash].[ext]",
      },
    },
  },
});

mkdirSync(out, { recursive: true });
writeFileSync(`${out}/manifest.json`, JSON.stringify(manifest({ version, apiUrl, withKey: !store }), null, 2) + "\n");
console.log(`built ${store ? "store" : development ? "development" : "unpacked"} extension ${version} → ${out}`);
