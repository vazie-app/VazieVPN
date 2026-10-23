// Copies the Android app's words into the extension, so one sentence has one author. Reads every `strings.xml` of
// the modules whose screens the extension has too, and writes `src/shared/i18n/android.{en,ru}.json`.
// The extension's own words (the helper, sites, the browser surfaces) live beside them in `ext.{en,ru}.json`.
//
//   node scripts/import-strings.mjs
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const android = fileURLToPath(new URL("../../android/", import.meta.url));
const out = fileURLToPath(new URL("../src/shared/i18n/", import.meta.url));

const MODULES = [
  "feature/home",
  "feature/config",
  "feature/connections",
  "feature/onboarding",
  "feature/settings",
  "app",
  "vpn/runtime",
  "core/designsystem",
];

const ENTITIES = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'" };

function unescape(raw) {
  return raw
    .replace(/&(#x[0-9a-fA-F]+|#\d+|[a-z]+);/g, (whole, name) => {
      if (name.startsWith("#x")) return String.fromCodePoint(parseInt(name.slice(2), 16));
      if (name.startsWith("#")) return String.fromCodePoint(Number(name.slice(1)));
      return ENTITIES[name] ?? whole;
    })
    .replace(/\\(u[0-9a-fA-F]{4}|.)/g, (_, c) => {
      if (c.startsWith("u")) return String.fromCharCode(parseInt(c.slice(1), 16));
      if (c === "n") return "\n";
      return c; // \' \" \@ \? \\
    })
    .replace(/\s*\n\s*/g, " ")
    .trim();
}

function parse(xml) {
  const strings = {};
  const clean = xml.replace(/<!--[\s\S]*?-->/g, "");
  for (const m of clean.matchAll(/<string\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)) {
    strings[m[1]] = unescape(m[2]);
  }
  for (const m of clean.matchAll(/<plurals\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/plurals>/g)) {
    const forms = {};
    for (const item of m[2].matchAll(/<item\s+quantity="([^"]+)"[^>]*>([\s\S]*?)<\/item>/g)) {
      forms[item[1]] = unescape(item[2]);
    }
    strings[m[1]] = forms;
  }
  return strings;
}

for (const [qualifier, file] of [["values", "android.en.json"], ["values-ru", "android.ru.json"]]) {
  const merged = {};
  for (const module of MODULES) {
    let xml;
    try {
      xml = readFileSync(`${android}${module}/src/main/res/${qualifier}/strings.xml`, "utf8");
    } catch {
      continue;
    }
    Object.assign(merged, parse(xml));
  }
  const sorted = Object.fromEntries(Object.entries(merged).sort(([a], [b]) => a.localeCompare(b)));
  writeFileSync(`${out}${file}`, JSON.stringify(sorted, null, 2) + "\n");
  console.log(file, Object.keys(sorted).length, "strings");
}
