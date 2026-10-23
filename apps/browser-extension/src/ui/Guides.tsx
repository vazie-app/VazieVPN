import { useEffect } from "preact/hooks";
import type { GuideId } from "../shared/state.ts";
import { Button, Card, Row, Section, TopBar } from "./components.tsx";
import { useEnv } from "./env.tsx";
import { navigate } from "./router.ts";
import { call } from "./rpc.ts";
import { openInTab, openSidePanel } from "./Settings.tsx";

const EXAMPLE = "vless://00000000-0000-0000-0000-000000000001@vpn.example.com:443?...";

interface GuideSpec {
  id: GuideId;
  icon: "plug" | "tab" | "panel" | "info" | "book";
  title: string;
  summary: string;
  body: (on: boolean) => string;
  steps: string[];
  action?: { key: string; run: () => Promise<void> };
}

function specs(notificationsOn: boolean): GuideSpec[] {
  return [
    {
      id: "toolbar",
      icon: "tab",
      title: "x_guide_toolbar_title",
      summary: "x_guide_toolbar_summary",
      body: () => "x_guide_toolbar_body",
      steps: ["x_guide_toolbar_step_1", "x_guide_toolbar_step_2", "x_guide_toolbar_step_3"],
    },
    {
      id: "context_menu",
      icon: "book",
      title: "x_guide_menu_title",
      summary: "x_guide_menu_summary",
      body: () => "x_guide_menu_body",
      steps: ["x_guide_menu_step_1", "x_guide_menu_step_2"],
    },
    {
      id: "side_panel",
      icon: "panel",
      title: "x_guide_panel_title",
      summary: "x_guide_panel_summary",
      body: () => "x_guide_panel_body",
      steps: ["x_guide_panel_step_1", "x_guide_panel_step_2", "x_guide_panel_step_3"],
      action: { key: "x_guide_panel_action", run: openSidePanel },
    },
    {
      id: "notification",
      icon: "info",
      title: "guide_notification_title",
      summary: "x_guide_notification_summary",
      body: (on) => (on ? "x_guide_notification_body_on" : "x_guide_notification_body_off"),
      steps: ["x_guide_notification_step_1", "x_guide_notification_step_2"],
      action: notificationsOn
        ? undefined
        : {
            key: "x_guide_notification_action",
            run: async () => {
              try {
                await chrome.permissions.request({ permissions: ["notifications"] });
              } finally {
                await call("notificationPermissionChanged");
              }
            },
          },
    },
  ];
}

export function Guides() {
  const { app, t } = useEnv();
  const viewed = new Set(app.prefs.viewedGuides);
  return (
    <div class="frame">
      <TopBar title={t.t("settings_guides_title")} />
      <main class="scroll">
        <p class="body">{t.t("settings_guides_body")}</p>

        <Section title={t.t("getting_started_section")} />
        <Card class="list">
          <Row icon="book" title={t.t("getting_started_first_config_short")} subtitle={t.t("getting_started_first_config_summary")} chevron onClick={() => navigate("/settings/guide/first-config")} />
          <Row icon="book" title={t.t("getting_started_manage_short")} subtitle={t.t("getting_started_manage_summary")} chevron onClick={() => navigate("/settings/guide/manage")} />
          <Row icon="plug" title={t.t("x_guide_helper_short")} subtitle={t.t("x_guide_helper_summary")} chevron onClick={() => navigate("/settings/helper")} />
        </Card>

        <Section title={t.t("settings_guides_system_section")} />
        <Card class="list">
          {specs(app.prefs.notificationsEnabled).map((guide) => (
            <Row
              key={guide.id}
              icon={guide.icon}
              title={t.t(guide.title)}
              subtitle={t.t(guide.summary)}
              trailing={viewed.has(guide.id) ? t.t("settings_guides_viewed") : undefined}
              chevron
              onClick={() => navigate(`/settings/guide/${guide.id}`)}
            />
          ))}
        </Card>

        <div style="margin-top:20px">
          <Card>
            <Row
              icon="refresh"
              title={t.t("settings_guides_replay_tour")}
              subtitle={t.t("settings_guides_replay_tour_body")}
              onClick={async () => {
                await call("setPrefs", { tourDone: false });
                navigate("/", { replace: true });
              }}
            />
          </Card>
        </div>
      </main>
    </div>
  );
}

export function GuideDetail({ id }: { id: string }) {
  const { app, t } = useEnv();

  useEffect(() => {
    const guide = id as GuideId;
    if (["toolbar", "context_menu", "side_panel", "notification"].includes(id) && !app.prefs.viewedGuides.includes(guide)) {
      void call("setPrefs", { viewedGuides: [...app.prefs.viewedGuides, guide] });
    }
    // Once, on arrival.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  if (id === "first-config") return <FirstConfig />;
  if (id === "manage") return <Manage />;
  const guide = specs(app.prefs.notificationsEnabled).find((g) => g.id === id);
  if (!guide) return <div class="frame"><TopBar title={t.t("settings_guides_title")} /></div>;
  return (
    <div class="frame">
      <TopBar title={t.t(guide.title)} />
      <main class="scroll">
        <p class="body">{t.t(guide.body(app.prefs.notificationsEnabled))}</p>
        {guide.action && (
          <div style="margin-top:16px">
            <Button onClick={() => void guide.action?.run()}>{t.t(guide.action.key)}</Button>
          </div>
        )}
        {guide.id === "side_panel" && (
          <div style="margin-top:8px">
            <Button variant="outline" icon="tab" onClick={() => void openInTab()}>
              {t.t("x_open_in_tab")}
            </Button>
          </div>
        )}
        <Section title={t.t("x_guide_how")} />
        <Card padded>
          <ol class="steps" style="margin:0">
            {guide.steps.map((step) => (
              <li key={step}>{t.t(step)}</li>
            ))}
          </ol>
        </Card>
      </main>
    </div>
  );
}

function FirstConfig() {
  const { t } = useEnv();
  return (
    <div class="frame">
      <TopBar title={t.t("getting_started_first_config_title")} />
      <main class="scroll">
        <p class="body">{t.t("getting_started_first_config_intro")}</p>
        <h3 class="heading">{t.t("getting_started_shape_heading")}</h3>
        <p class="body">{t.t("getting_started_shape_body")}</p>
        <div class="example" role="img" aria-label={t.t("getting_started_example_a11y")}>
          {EXAMPLE}
        </div>
        <h3 class="heading">{t.t("getting_started_reads_heading")}</h3>
        <p class="body">{t.t("getting_started_reads_body", "VLESS")}</p>
        <div style="margin-top:20px">
          <Button onClick={() => navigate("/add")}>{t.t("getting_started_add_action")}</Button>
        </div>
      </main>
    </div>
  );
}

function Manage() {
  const { t } = useEnv();
  return (
    <div class="frame">
      <TopBar title={t.t("getting_started_manage_title")} />
      <main class="scroll">
        <p class="body">{t.t("getting_started_manage_intro")}</p>
        <h3 class="heading">{t.t("getting_started_manage_list_heading")}</h3>
        <p class="body">{t.t("x_manage_list_body")}</p>
        <h3 class="heading">{t.t("getting_started_manage_details_heading")}</h3>
        <p class="body">{t.t("getting_started_manage_details_body")}</p>
        <h3 class="heading">{t.t("getting_started_manage_export_heading")}</h3>
        <p class="body">{t.t("getting_started_manage_export_body")}</p>
      </main>
    </div>
  );
}

