import { type AppLink, linksIn, type LinkSection, localize } from "../shared/backend.ts";
import { Card, Row, Section, TopBar } from "./components.tsx";
import { useEnv } from "./env.tsx";
import { navigate } from "./router.ts";
import { extensionVersion } from "./Settings.tsx";

function LinkRows({ links }: { links: AppLink[] }) {
  const { t } = useEnv();
  return (
    <Card class="list">
      {links.map((link) => (
        <Row key={link.key} title={localize(link.title, t.language)} subtitle={link.subtitle ? localize(link.subtitle, t.language) : undefined} href={link.url} chevron />
      ))}
    </Card>
  );
}

/** Who made this, how to reach them, and how to help pay for it. Every link is the backend's. */
export function About() {
  const { app, t } = useEnv();
  const links = app.links;
  const section = (name: LinkSection) => (links ? linksIn(links, name) : []);
  const author = section("AUTHOR");
  const project = section("PROJECT");
  const security = section("SECURITY");
  const help = section("HELP");
  const support = section("SUPPORT");
  const feedback = section("FEEDBACK");

  return (
    <div class="frame">
      <TopBar title={t.t("settings_about_title")} />
      <main class="scroll">
        <Section title={t.t("settings_made_by")} />
        {/* The author's card stays even with no links: it says who makes the app. */}
        <Card padded>
          <img class="sticker" src="brand/vazovsky_sticker.webp" alt={t.t("vazie_a11y_vazovsky_sticker")} />
          <h3 style="margin:12px 0 4px;text-align:center;font-size:17px">{t.t("settings_made_by_title")}</h3>
          <p class="note center" style="margin:0 0 8px">
            {t.t("settings_made_by_body")}
          </p>
        </Card>
        {author.length > 0 && (
          <div style="margin-top:10px">
            <LinkRows links={author} />
          </div>
        )}

        {/* The app's own page, not a link: it is here whatever the backend publishes. */}
        <Section title={t.t("settings_privacy_section")} />
        <Card>
          <Row icon="lock" title={t.t("settings_privacy_row")} subtitle={t.t("settings_privacy_row_body")} chevron onClick={() => navigate("/settings/privacy")} />
        </Card>

        {project.length > 0 && (
          <>
            <Section title={t.t("settings_project_section")} />
            <LinkRows links={project} />
          </>
        )}
        {security.length > 0 && (
          <>
            <Section title={t.t("settings_security_section")} />
            <LinkRows links={security} />
          </>
        )}
        {help.length > 0 && (
          <>
            <Section title={t.t("settings_about_help_section")} />
            <LinkRows links={help} />
          </>
        )}
        {support.length > 0 && (
          <>
            <Section title={t.t("settings_support_section")} />
            {support.map((link) => (
              <div style="margin-top:8px" key={link.key}>
                <Card padded>
                  <h3 style="margin:0 0 4px;font-size:16px">{localize(link.title, t.language)}</h3>
                  {link.subtitle && <p class="note" style="margin:0 0 12px">{localize(link.subtitle, t.language)}</p>}
                  <a class="button secondary" href={link.url} target="_blank" rel="noopener noreferrer">
                    {link.action ? localize(link.action, t.language) : localize(link.title, t.language)}
                  </a>
                </Card>
              </div>
            ))}
          </>
        )}
        {feedback.length > 0 && (
          <>
            <Section title={t.t("settings_feedback_section")} />
            <LinkRows links={feedback} />
          </>
        )}

        <p class="footer-note">{t.t("x_build_signature", extensionVersion, app.helper.kind === "ready" ? app.helper.version : "—")}</p>
      </main>
    </div>
  );
}

const SECTIONS: { title: string; items: string[] }[] = [
  { title: "privacy_stores_title", items: ["x_privacy_stores_configs", "privacy_stores_choices", "privacy_stores_usage"] },
  { title: "privacy_where_title", items: ["x_privacy_where_encrypted", "x_privacy_where_plain", "x_privacy_where_sync"] },
  { title: "privacy_never_title", items: ["privacy_never_analytics", "privacy_never_account", "privacy_never_browsing"] },
  { title: "privacy_logs_title", items: ["x_privacy_logs_body"] },
  { title: "privacy_vpn_title", items: ["x_privacy_vpn_body", "x_privacy_vpn_dns"] },
  { title: "x_privacy_helper_title", items: ["x_privacy_helper_local", "x_privacy_helper_engine"] },
  { title: "privacy_own_title", items: ["privacy_own_body"] },
  { title: "x_privacy_surfaces_title", items: ["x_privacy_surfaces_menu", "x_privacy_surfaces_notification", "x_privacy_surfaces_permissions"] },
  { title: "privacy_export_title", items: ["x_privacy_export_body"] },
];

/** What Vazie VPN stores, where, and what it never collects. */
export function Privacy() {
  const { t } = useEnv();
  return (
    <div class="frame">
      <TopBar title={t.t("settings_privacy_title")} />
      <main class="scroll">
        <p class="body">{t.t("settings_privacy_body")}</p>
        {SECTIONS.map((section) => (
          <div key={section.title}>
            <h3 class="heading">{t.t(section.title)}</h3>
            <Card padded>
              <ul style="margin:0;padding-left:18px;display:grid;gap:8px;color:var(--text-2)">
                {section.items.map((item) => (
                  <li key={item}>{t.t(item)}</li>
                ))}
              </ul>
            </Card>
          </div>
        ))}
      </main>
    </div>
  );
}

