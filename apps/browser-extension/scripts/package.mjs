// Builds the store variant (no development key) and zips it: dist/vazie-vpn-<version>-chromium.zip.
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, rmSync } from "node:fs";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("..", import.meta.url));
execFileSync("node", [`${root}scripts/build.mjs`, "--store"], { stdio: "inherit" });
const dist = `${root}dist/store`;
const { version } = JSON.parse(readFileSync(`${root}package.json`, "utf8"));
const manifest = JSON.parse(readFileSync(`${dist}/manifest.json`, "utf8"));

if (!existsSync(dist)) throw new Error("the store build was not made");
if ("key" in manifest) throw new Error("the store build must not carry the development key");

const zip = `${root}dist/vazie-vpn-${version}-chromium.zip`;
rmSync(zip, { force: true });
execFileSync("zip", ["-r", "-q", "-X", zip, "."], { cwd: dist });
console.log(zip);
