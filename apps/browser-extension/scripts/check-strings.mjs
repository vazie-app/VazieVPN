// Every key the code asks for must exist in both languages, and both languages must have the same keys.
// Used by `npm test` (tests/strings.test.ts) and by hand to list what is missing:  node scripts/check-strings.mjs
import { readdirSync, readFileSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";

const root = fileURLToPath(new URL("../src/", import.meta.url));
const read = (name) => JSON.parse(readFileSync(`${root}shared/i18n/${name}.json`, "utf8"));
const tables = {
  en: { ...read("android.en"), ...read("ext.en") },
  ru: { ...read("android.ru"), ...read("ext.ru") },
};

function* files(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) yield* files(path);
    else if (/\.(ts|tsx)$/.test(name)) yield path;
  }
}

const used = new Set();
for (const file of files(root)) {
  const text = readFileSync(file, "utf8");
  // t.t("key"…), t.n("key"…), and the keys named as string literals that are looked up through variables.
  for (const match of text.matchAll(/\b(?:t\.t|t\.n|translator\([^)]*\)\.t)\(\s*["'`]([a-z0-9_]+)["'`]/g)) used.add(match[1]);
  for (const match of text.matchAll(/["'`]((?:x|home|config|connections|settings|guide|guidance|getting_started|privacy|split_tunnel|onboarding|tour|update|vpn|connection|widget|shortcut|notification|faq|vazie)_[a-z0-9_]+)["'`]/g)) used.add(match[1]);
}
// Keys built from a prefix and a variable part.
const dynamic = {
  home_unit_: ["bytes", "kilobytes", "megabytes", "gigabytes"],
  settings_appearance_: ["night_indigo", "milk"],
  settings_app_icon_: ["orbit", "lime", "emerald", "magenta", "cotton_candy", "crimson", "gold", "pearl", "onyx", "monochrome"],
  settings_app_icon_plate_: ["deep", "light", "ink", "forest", "rose", "amber", "mist"],
  connections_status_: ["connected", "selected", "idle", "unavailable"],
  config_unsupported_: ["security", "transport", "flow", "encryption"],
  x_split_mode_: ["off", "exclude", "include"],
};
for (const [prefix, parts] of Object.entries(dynamic)) for (const part of parts) used.add(prefix + part);
for (const mode of ["off", "exclude", "include"]) used.add(`x_split_mode_${mode}_body`);

// Words that look like keys but are browser API values.
const IGNORED = new Set(["update_available"]);
const problems = [];
for (const language of ["en", "ru"]) {
  for (const key of [...used].filter((k) => !IGNORED.has(k)).sort()) {
    // A literal that merely looks like a key (an element id, a CSS class) is not a problem: only keys that
    // start with a known prefix and are used in a `t` call are checked strictly below.
    if (!(key in tables[language]) && /^(x_|home_|config_|connections_|settings_|guide_|guidance_|getting_started_|privacy_|split_tunnel_|onboarding_|tour_|update_|vpn_|connection_|widget_|shortcut_|notification_|faq_|vazie_)/.test(key)) {
      problems.push(`${language}: missing ${key}`);
    }
  }
}
for (const key of Object.keys(tables.en)) if (!(key in tables.ru)) problems.push(`ru: missing ${key} (present in en)`);
for (const key of Object.keys(tables.ru)) if (!(key in tables.en)) problems.push(`en: missing ${key} (present in ru)`);

if (problems.length > 0) {
  console.log(problems.join("\n"));
  process.exitCode = 1;
} else {
  console.log(`strings ok: ${used.size} keys used, ${Object.keys(tables.en).length} defined`);
}
