import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { format, translator } from "../src/shared/i18n.ts";

test("every key the code asks for exists in both languages, and both languages agree", () => {
  const script = fileURLToPath(new URL("../scripts/check-strings.mjs", import.meta.url));
  const output = execFileSync("node", [script], { encoding: "utf8" });
  assert.match(output, /strings ok/);
});

test("Android's placeholders read the way Android reads them", () => {
  assert.equal(format("%1$s · %2$s", ["04:12", "VLESS"]), "04:12 · VLESS");
  assert.equal(format("%1$d ms", [42]), "42 ms");
  assert.equal(format("%d configurations", [3]), "3 configurations");
  assert.equal(format("100%%", []), "100%");
});

test("plurals follow each language's own rules", () => {
  const en = translator("en");
  const ru = translator("ru");
  assert.equal(en.n("x_split_selected", 1), "1 site chosen");
  assert.equal(en.n("x_split_selected", 2), "2 sites chosen");
  assert.equal(ru.n("x_split_selected", 1), "Выбран 1 сайт");
  assert.equal(ru.n("x_split_selected", 3), "Выбрано 3 сайта");
  assert.equal(ru.n("x_split_selected", 5), "Выбрано 5 сайтов");
  assert.equal(ru.n("x_split_selected", 21), "Выбран 21 сайт");
});

test("a missing key shows the key, never a blank", () => {
  assert.equal(translator("ru").t("no_such_key"), "no_such_key");
});
