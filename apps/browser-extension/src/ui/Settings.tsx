import { useState } from "preact/hooks";
import { Card, Dialog, Row, Section } from "./components.tsx";
import { useEnv } from "./env.tsx";
import { Icon } from "./Icon.tsx";
import { navigate } from "./router.ts";
import { Tip } from "./Tip.tsx";
import { linksIn } from "../shared/backend.ts";

export const extensionVersion = chrome.runtime.getManifest().version;

export async function openInTab(path = ""): Promise<void> {
  await chrome.tabs.create({ url: chrome.runtime.getURL(`app.html${path ? `#${path}` : ""}`) });
  if (document.documentElement.dataset.surface === "popup") window.close();
}

export async function openSidePanel(): Promise<void> {
  const window = await chrome.windows.getCurrent();
  if (window.id !== undefined) await chrome.sidePanel.open({ windowId: window.id });
  if (document.documentElement.dataset.surface === "popup") globalThis.close();
}

export function Settings() {
  const { app, t, surface } = useEnv();
  const links = app.links;
  const split = app.prefs.split;
  const splitStatus =
    split.mode === "off" || split.sites.length === 0
      ? t.t("x_split_status_off")
      : split.mode === "exclude"
        ? t.n("x_split_status_exclude", split.sites.length)
        : t.n("x_split_status_include", split.sites.length);
  const feedback = links ? linksIn(links, "FEEDBACK")[0] : undefined;
  const support = links?.links.find((l) => l.url.startsWith("mailto:"));
  const language = t.language;
  const helperSubtitle =
    app.helper.kind === "ready"
      ? app.helper.engineVersion
        ? t.t("x_helper_row_ready", app.helper.version, app.helper.engineVersion)
        : t.t("x_helper_row_engine_missing")
      : app.helper.kind === "missing"
        ? t.t("x_helper_row_missing")
        : t.t("x_helper_row_checking");

  return (
    <div class="frame">
      <header class="topbar">
        <button class="icon-button" onClick={() => navigate("/", { replace: true })} aria-label={t.t("settings_back")}>
          <Icon name="back" />
        </button>
        <h1>{t.t("settings_title")}</h1>
      </header>
      <main class="scroll">
        <Section title={t.t("settings_look_and_feel")} />
        <Card>
          <Row
            icon="palette"
            title={t.t("settings_appearance")}
            subtitle={`${t.t(`settings_appearance_${app.prefs.appearance}`)} · ${t.t(`settings_app_icon_${app.prefs.appIcon}`)}`}
            chevron
            onClick={() => navigate("/settings/appearance")}
          />
        </Card>
        <Tip id="appearance" title="guidance_appearance_title" body="x_guidance_appearance_body" />

        <Section title={t.t("settings_vpn_behavior")} />
        <Card class="list">
          <Row icon="split" title={t.t("settings_split_tunneling")} subtitle={splitStatus} chevron onClick={() => navigate("/settings/split")} />
          <Row icon="plug" title={t.t("x_helper_row")} subtitle={helperSubtitle} chevron onClick={() => navigate("/settings/helper")} />
        </Card>
        <Tip id="split" title="guidance_split_title" body="x_guidance_split_body" />

        {surface !== "tab" && surface !== "panel" ? (
          <>
            <Section title={t.t("x_open_section")} />
            <Card class="list">
              <Row icon="panel" title={t.t("x_open_side_panel")} chevron onClick={() => void openSidePanel()} />
              <Row icon="tab" title={t.t("x_open_in_tab")} chevron onClick={() => void openInTab()} />
            </Card>
          </>
        ) : surface === "tab" ? (
          <>
            <Section title={t.t("x_open_section")} />
            <Card>
              <Row icon="panel" title={t.t("x_open_side_panel")} chevron onClick={() => void openSidePanel()} />
            </Card>
          </>
        ) : null}

        <Section title={t.t("settings_help_section")} />
        <Card class="list">
          <Row icon="book" title={t.t("settings_guides_row")} subtitle={t.t("settings_guides_row_body")} chevron onClick={() => navigate("/settings/guides")} />
          <Row icon="help" title={t.t("settings_faq_row")} subtitle={t.t("settings_faq_row_body")} chevron onClick={() => navigate("/settings/faq")} />
          {feedback && <Row icon="bug" title={t.t("settings_feedback_row")} subtitle={t.t("settings_feedback_row_body")} chevron href={feedback.url} />}
          {support && <Row icon="mail" title={t.t("settings_contact_support")} subtitle={t.t("settings_contact_support_body")} chevron href={support.url} />}
        </Card>
        <Tip id="guides" title="guidance_guides_title" body="x_guidance_guides_body" />
        {feedback && <Tip id="bug" title="guidance_bug_title" body="guidance_bug_body" />}

        <Section title={t.t("settings_about_section")} />
        <Card class="list">
          <Row icon="info" title={t.t("settings_about_row")} subtitle={t.t("settings_about_row_body")} chevron onClick={() => navigate("/settings/about")} />
          <UpdateRow />
        </Card>

        <p class="footer-note">
          {t.t("settings_footer_name")} · {extensionVersion}
          <br />
          {t.t("settings_footer_made_by")}
        </p>
        <span hidden>{language}</span>
      </main>
    </div>
  );
}

/** "Check for Updates": the browser's own check against the store the extension came from. */
function UpdateRow() {
  const { t } = useEnv();
  const [checking, setChecking] = useState(false);
  const [result, setResult] = useState<null | { kind: "latest" | "available" | "failed"; version?: string }>(null);

  const check = async () => {
    if (checking) return;
    setChecking(true);
    try {
      const { status, version } = await chrome.runtime.requestUpdateCheck();
      if (status === "update_available") setResult({ kind: "available", version });
      else if (status === "no_update") setResult({ kind: "latest" });
      else setResult({ kind: "failed" });
    } catch {
      // An unpacked or sideloaded build has no store to ask.
      setResult({ kind: "failed" });
    }
    setChecking(false);
  };

  return (
    <>
      <Row title={t.t("settings_check_updates")} subtitle={checking ? t.t("settings_check_updates_checking") : undefined} onClick={() => void check()} />
      {result?.kind === "latest" && (
        <Dialog title={t.t("update_up_to_date_title")} body={t.t("update_up_to_date_body")} confirm={t.t("update_ok")} cancel={t.t("update_close")} onConfirm={() => setResult(null)} onCancel={() => setResult(null)} />
      )}
      {result?.kind === "available" && (
        <Dialog
          title={t.t("update_available_title", result.version ?? "")}
          body={t.t("update_available_body")}
          confirm={t.t("update_action")}
          cancel={t.t("update_later")}
          onConfirm={() => chrome.runtime.reload()}
          onCancel={() => setResult(null)}
        />
      )}
      {result?.kind === "failed" && (
        <Dialog title={t.t("x_update_unavailable_title")} body={t.t("x_update_unavailable_body")} confirm={t.t("update_ok")} cancel={t.t("update_close")} onConfirm={() => setResult(null)} onCancel={() => setResult(null)} />
      )}
    </>
  );
}

