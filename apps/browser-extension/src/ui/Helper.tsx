import { useEffect, useState } from "preact/hooks";
import { HELPER_URL, type HelperOs, helperInstaller } from "../shared/config.ts";
import { Banner, Button, Card, copySecret, DetailRow, Section, TopBar } from "./components.tsx";
import { useEnv } from "./env.tsx";
import { call } from "./rpc.ts";

/** The person's system, as far as the browser says: which installer to offer. */
async function detectSystem(): Promise<{ os: HelperOs | null; arm: boolean }> {
  const data = (navigator as Navigator & { userAgentData?: { platform?: string; getHighEntropyValues?: (hints: string[]) => Promise<{ architecture?: string }> } }).userAgentData;
  const text = `${data?.platform ?? ""} ${navigator.userAgent}`.toLowerCase();
  const os: HelperOs | null = text.includes("mac") ? "macos" : text.includes("win") ? "windows" : text.includes("linux") || text.includes("cros") ? "linux" : null;
  let arm = /arm|aarch64/.test(text);
  try {
    arm = (await data?.getHighEntropyValues?.(["architecture"]))?.architecture === "arm" || arm;
  } catch {
    // The user agent string's guess stands.
  }
  return { os, arm };
}

export function Helper() {
  const { app, t, toast } = useEnv();
  const [busy, setBusy] = useState<"check" | "engine" | null>(null);
  const helper = app.helper;
  const id = chrome.runtime.id;
  const [system, setSystem] = useState<{ os: HelperOs | null; arm: boolean } | null>(null);
  const missing = helper.kind !== "ready";
  // The terminal route is the installer file itself, by name: the helper is not on the PATH until it is installed.
  const command =
    system?.os === "macos"
      ? "sudo installer -pkg ~/Downloads/VazieVPNHelper.pkg -target /"
      : system?.os === "windows"
        ? ".\\vazie-vpn-helper-windows-x64.exe install"
        : system?.os === "linux"
          ? "chmod +x vazie-vpn-helper-linux-x64 && ./vazie-vpn-helper-linux-x64 install"
          : null;

  useEffect(() => {
    void detectSystem().then(setSystem);
  }, []);

  // Until the helper is found this page looks for it, so nobody has to come back and press anything.
  useEffect(() => {
    if (!missing) return;
    const timer = setInterval(() => void call("recheckHelper"), 2500);
    return () => clearInterval(timer);
  }, [missing]);

  const check = async () => {
    setBusy("check");
    await call("recheckHelper");
    setBusy(null);
  };
  const installEngine = async () => {
    setBusy("engine");
    const ok = await call("installEngine");
    setBusy(null);
    toast(ok ? t.t("x_engine_installed") : t.t("x_engine_failed"));
  };

  return (
    <div class="frame">
      <TopBar title={t.t("x_helper_row")} />
      <main class="scroll">
        <p class="body">{t.t("x_helper_why")}</p>

        {helper.kind === "ready" ? (
          <>
            <Section title={t.t("x_helper_status")} />
            <Card>
              <DetailRow label={t.t("x_helper_version")} value={helper.version} />
              <DetailRow label={t.t("x_helper_platform")} value={helper.platform} />
              <DetailRow label={t.t("home_diagnostics_engine")} value={helper.engineVersion ? `Xray ${helper.engineVersion}` : t.t("x_helper_engine_none")} />
            </Card>
            {helper.engineVersion === null && (
              <>
                <Banner tone="warning" title={t.t("x_engine_needed_title")}>
                  {t.t("x_engine_needed_body")}
                </Banner>
                <div style="margin-top:12px">
                  <Button icon="download" onClick={() => void installEngine()} disabled={busy !== null}>
                    {busy === "engine" ? t.t("x_engine_installing") : t.t("x_engine_install")}
                  </Button>
                </div>
              </>
            )}
          </>
        ) : (
          <>
            <Section title={t.t("x_helper_install")} />
            <Card padded>
              {system?.os ? (
                <>
                  <a class="button primary" href={`${HELPER_URL}/${helperInstaller(system.os, system.arm)}`} download>
                    {t.t("x_helper_download", t.t(`x_helper_os_${system.os}`))}
                  </a>
                  <ol class="steps">
                    <li>{t.t("x_helper_s1")}</li>
                    <li>{t.t(system.os === "macos" ? "x_helper_s2_macos" : "x_helper_s2_other")}</li>
                    <li>{t.t("x_helper_s3")}</li>
                  </ol>
                </>
              ) : (
                system && <p class="body" style="margin:0">{t.t("x_helper_unknown_os")}</p>
              )}
              <p class="note pulse" style="margin:12px 0 0">
                {t.t("x_helper_watching")}
              </p>
              {helper.kind === "missing" && helper.detail && (
                <p class="note" style="margin:8px 0 0">
                  {t.t("x_helper_browser_says")} <code style="font-family:var(--mono);font-size:12px">{helper.detail}</code>
                </p>
              )}
            </Card>
            {command && (
              <details style="margin-top:14px">
                <summary style="cursor:pointer;color:var(--primary);padding:6px 4px">{t.t("x_helper_advanced")}</summary>
                <p class="note">{t.t("x_helper_advanced_body")}</p>
                <code class="code">{command}</code>
                <div style="margin-top:8px">
                  <Button variant="outline" compact icon="copy" onClick={async () => (await copySecret(command), toast(t.t("x_command_copied")))}>
                    {t.t("x_copy_command")}
                  </Button>
                </div>
              </details>
            )}
          </>
        )}

        <div style="margin-top:14px">
          <Button variant="secondary" icon="refresh" onClick={() => void check()} disabled={busy !== null}>
            {t.t("x_helper_check_again")}
          </Button>
        </div>
        <p class="note">{t.t("x_helper_id", id)}</p>
        <p class="note">{t.t("x_helper_remove")}</p>
      </main>
    </div>
  );
}
