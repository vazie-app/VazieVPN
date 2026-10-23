declare const __VAZIE_API_URL__: string;

/** Where the Vazie backend answers: the public links, questions and nothing else. Set at build time. */
export const API_URL: string = typeof __VAZIE_API_URL__ === "string" ? __VAZIE_API_URL__ : "https://api.example.com/v1";

declare const __VAZIE_SITE_URL__: string;

/** The Vazie site, for the questions that point at a page on it. Set at build time. */
export const SITE_URL: string = typeof __VAZIE_SITE_URL__ === "string" ? __VAZIE_SITE_URL__ : "https://example.com";

declare const __VAZIE_HELPER_URL__: string;

/** Where the helper's installers are published: a directory that holds the files named in `helperInstaller`. */
export const HELPER_URL: string =
  typeof __VAZIE_HELPER_URL__ === "string" ? __VAZIE_HELPER_URL__ : "https://github.com/vazie-app/VazieVPN/releases";

export type HelperOs = "macos" | "windows" | "linux";

/** The installer file for a system: one universal package on macOS, one executable elsewhere. */
export function helperInstaller(os: HelperOs, arm: boolean): string {
  if (os === "macos") return "VazieVPNHelper.pkg";
  return `vazie-vpn-helper-${os}-${arm ? "arm64" : "x64"}${os === "windows" ? ".exe" : ""}`;
}
