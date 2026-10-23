import { useEffect, useMemo, useRef, useState } from "preact/hooks";
import { homeView, present, type Presentation, type RouteState } from "../shared/homeView.ts";
import { formatSessionDuration, trafficReading } from "../shared/format.ts";
import type { AppState, ProfileSummary } from "../shared/state.ts";
import { siteOfUrl } from "../shared/splitTunnel.ts";
import { routesThroughTunnel } from "../shared/splitTunnel.ts";
import { Banner, Button, Card, Dialog, Section } from "./components.tsx";
import { type Env, useEnv } from "./env.tsx";
import { Icon } from "./Icon.tsx";
import { navigate } from "./router.ts";
import { call } from "./rpc.ts";
import { Tour } from "./Tour.tsx";

/** One tick a second while a session is live, so the clock moves. */
function useNow(live: boolean): number {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    setNow(Date.now());
    if (!live) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [live]);
  return now;
}

export function Home() {
  const env = useEnv();
  const { app, t } = env;
  const view = useMemo(() => homeView(app), [app]);
  const presentation = present(view);
  const connected = app.snapshot.state.kind === "connected";
  const now = useNow(connected);
  const [askNotifications, setAskNotifications] = useState(false);

  const start = async () => {
    const needsPrompt = !app.prefs.notificationPromptShown && chrome.notifications === undefined;
    if (needsPrompt) {
      setAskNotifications(true);
      return;
    }
    await call("connect");
  };

  const act = async (action: Presentation["action"]) => {
    switch (action) {
      case "add":
        return navigate("/add");
      case "connect":
        return start();
      case "retry":
      case "check":
        return call("connect");
      case "cancel":
      case "disconnect":
        return call("disconnect");
    }
  };

  const failure = app.snapshot.state.kind === "failed" ? app.snapshot.state.failure : null;
  const needsHelper = app.helper.kind === "missing" || failure === "helper";
  const needsEngine = failure === "engine" || (app.helper.kind === "ready" && app.helper.engineVersion === null);

  return (
    <div class="frame">
      <header class="topbar">
        <span class="brand">
          Vazie <b>VPN</b>
        </span>
        <span class="spacer" />
        <button class="icon-button" data-tour="settings" onClick={() => navigate("/settings")} aria-label={t.t("home_a11y_settings")}>
          <Icon name="gear" />
        </button>
      </header>

      <main class="scroll">
        <Headline presentation={presentation} connected={connected} name={view.kind === "ready" ? view.name : undefined} now={now} app={app} />
        {presentation.route && <Route presentation={presentation} app={app} now={now} />}

        {(needsHelper || needsEngine) && view.kind !== "empty" && (
          <Banner tone="warning" title={t.t(needsHelper ? "x_helper_needed_title" : "x_engine_needed_title")}>
            {t.t(needsHelper ? "x_helper_needed_body" : "x_engine_needed_body")}{" "}
            <a href="#/settings/helper" style="color:inherit;font-weight:600">
              {t.t("x_helper_setup_link")}
            </a>
          </Banner>
        )}
        {app.proxyControl === "other" && app.snapshot.state.kind !== "connected" && (
          <Banner tone="warning" title={t.t("x_proxy_other_title")}>
            {t.t("x_proxy_other_body")}
          </Banner>
        )}

        <div style="margin-top:14px" data-tour="connect">
          <Button
            variant={presentation.actionVariant}
            icon={presentation.action === "add" ? "plus" : "power"}
            disabled={!presentation.actionEnabled}
            onClick={() => void act(presentation.action)}
          >
            {t.t(presentation.actionKey)}
          </Button>
        </div>

        <SiteRow app={app} />
        <Configurations env={env} />
      </main>

      <Tour />

      {askNotifications && (
        <Dialog
          title={t.t("notification_prompt_title")}
          body={t.t("x_notification_prompt_body")}
          confirm={t.t("notification_prompt_allow")}
          cancel={t.t("notification_prompt_not_now")}
          onConfirm={async () => {
            setAskNotifications(false);
            try {
              await chrome.permissions.request({ permissions: ["notifications"] });
            } catch {
              // Declined, or not available here: the tunnel connects either way.
            }
            await call("notificationPermissionChanged");
            await call("connect");
          }}
          onCancel={async () => {
            setAskNotifications(false);
            await call("setPrefs", { notificationPromptShown: true });
            await call("connect");
          }}
        />
      )}
    </div>
  );
}

function Headline({ presentation, connected, name, now, app }: { presentation: Presentation; connected: boolean; name?: string; now: number; app: AppState }) {
  const { t } = useEnv();
  const since = app.snapshot.state.kind === "connected" ? app.snapshot.state.since : now;
  const seconds = Math.max(0, Math.floor((now - since) / 1000));
  const tone = presentation.tone === "success" ? "on" : presentation.tone === "error" ? "error" : "";
  const line = presentation.line;
  return (
    <div class="headline" aria-live="polite">
      <span class={`tile ${tone}`}>
        <Icon name="power" />
      </span>
      <div>
        <h2>{presentation.titleKey ? t.t(presentation.titleKey) : ""}</h2>
        <p style={line.kind === "session" ? "font-family:var(--mono)" : undefined}>
          {line.kind === "session"
            ? t.t("home_route_session", formatSessionDuration(seconds), line.protocol)
            : t.t(line.key, name ?? "")}
        </p>
      </div>
      {connected && <span class="sr-only">{t.n("home_session_a11y", Math.floor(seconds / 60))}</span>}
    </div>
  );
}

function Route({ presentation, app, now }: { presentation: Presentation; app: AppState; now: number }) {
  const { t } = useEnv();
  const state: RouteState = presentation.route ?? "disconnected";
  const subjectName = app.snapshot.subject?.name ?? app.profiles.find((p) => p.id === app.prefs.selectedId)?.name ?? "";
  const mark = app.snapshot.subject?.mark ?? "VL";
  const live = state === "connected";
  const traffic = app.traffic;
  const rx = trafficReading(traffic?.rx);
  const tx = trafficReading(traffic?.tx);
  const unit = (u: string) => t.t(`home_unit_${{ B: "bytes", KB: "kilobytes", MB: "megabytes", GB: "gigabytes" }[u]}`);
  const text = (r: ReturnType<typeof trafficReading>) => (r ? t.t("home_value_amount", r.amount, unit(r.unit)) : t.t("home_value_unknown"));
  void now;

  const body = (
    <>
      <svg class="line" viewBox="0 0 320 64" role="img" aria-label={t.t("home_route_a11y", t.t(presentation.titleKey ?? "home_route_title_disconnected"), subjectName)}>
        <defs>
          <linearGradient id="routeGradient" x1="0" x2="1">
            <stop offset="0" stop-color="var(--route-start)" />
            <stop offset="1" stop-color="var(--route-end)" />
          </linearGradient>
        </defs>
        <path
          class={`path${live ? " live" : ""}${state === "connecting" ? " moving" : ""}${state === "failed" ? " failed" : ""}`}
          d="M46 32 C 110 6, 210 58, 274 32"
        />
        <circle class={`node${live ? " live" : ""}`} cx="26" cy="32" r="22" />
        <g transform="translate(14 20)" stroke="var(--on-accent-container)" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M3 4.5A1.5 1.5 0 0 1 4.5 3h15A1.5 1.5 0 0 1 21 4.5v10a1.5 1.5 0 0 1-1.500 1.500h-15A1.5 1.5 0 0 1 3 14.500ZM8 21h8M12 17v4" transform="scale(.86)" />
        </g>
        <circle class={`node${live ? " live" : ""}`} cx="294" cy="32" r="22" />
        <text x="294" y="36" text-anchor="middle">
          {mark}
        </text>
      </svg>
      <div class="ends">
        <span>{t.t("home_route_device")}</span>
        <span>{subjectName}</span>
      </div>
    </>
  );

  return (
    <section class="route" aria-label={t.t("home_traffic_a11y")}>
      {body}
      {live && (
        <button class="details" onClick={() => navigate("/details")} aria-label={t.t("home_traffic_a11y")}>
          <div class="session">
            <span>
              ↓ <b style="font-size:13px">{text(rx)}</b> <small>{t.t("home_traffic_downloaded")}</small>
            </span>
            <span>
              ↑ <b style="font-size:13px">{text(tx)}</b> <small>{t.t("home_traffic_uploaded")}</small>
            </span>
          </div>
        </button>
      )}
    </section>
  );
}

/** "Use VPN for this site", while sites are split: the browser's version of choosing which apps use the tunnel. */
function SiteRow({ app }: { app: AppState }) {
  const { t } = useEnv();
  const [site, setSite] = useState<string | null>(null);
  const { split } = app.prefs;
  useEffect(() => {
    void call("activeSite").then(setSite).catch(() => setSite(null));
  }, []);
  if (split.mode === "off" || !site || siteOfUrl(`https://${site}`) === null) return null;
  const through = routesThroughTunnel(split, site);
  const toggle = async () => {
    const has = split.sites.includes(site);
    const sites = has ? split.sites.filter((s) => s !== site) : [...split.sites, site].sort();
    await call("setSplit", { ...split, sites });
  };
  return (
    <div style="margin-top:12px">
      <Card>
        <div class="row">
          <span class="grow">
            <div class="title" style="font-family:var(--mono);font-size:13px">
              {site}
            </div>
            <div class="sub">{t.t(through ? "x_site_through" : "x_site_direct")}</div>
          </span>
          <Button variant="secondary" compact onClick={() => void toggle()}>
            {t.t(through ? "x_site_bypass" : "x_site_use")}
          </Button>
        </div>
      </Card>
    </div>
  );
}

function statusOf(profile: ProfileSummary, app: AppState): "connected" | "selected" | "idle" | "unavailable" {
  if (app.snapshot.state.kind === "connected" && app.snapshot.state.profileId === profile.id) return "connected";
  if (!profile.runnable) return "unavailable";
  return app.prefs.selectedId === profile.id ? "selected" : "idle";
}

export function Configurations({ env }: { env: Env }) {
  const { app, t } = env;
  const [latency, setLatency] = useState<Record<string, number | null>>({});
  const [deleting, setDeleting] = useState<ProfileSummary | null>(null);
  const helperReady = app.helper.kind === "ready";
  const ids = app.profiles.map((p) => p.id).join(",");
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  // Each row's latency, re-measured every thirty seconds while this list is on screen.
  useEffect(() => {
    if (!helperReady || ids === "") return;
    let alive = true;
    const measure = () => void call("latencies").then((l) => alive && setLatency(l)).catch(() => undefined);
    measure();
    timer.current = setInterval(measure, 30_000);
    return () => {
      alive = false;
      if (timer.current) clearInterval(timer.current);
    };
  }, [helperReady, ids]);

  return (
    <>
      <Section
        title={t.t("connections_my_configs")}
        trailing={
          <button class="button text compact" style="width:auto;min-height:28px;padding:0 4px" onClick={() => navigate("/add")}>
            {t.t("connections_add")}
          </button>
        }
      />
      <div data-tour="configs">
        {app.profiles.length === 0 ? (
          <Card padded>
            <strong>{t.t("connections_empty_title")}</strong>
            <p class="note" style="margin:6px 0 0">
              {t.t("connections_empty_body")}
            </p>
          </Card>
        ) : (
          <Card class="list">
            {app.profiles.map((profile) => {
              const status = statusOf(profile, app);
              const ms = latency[profile.id];
              return (
                <div class="config-row" key={profile.id} onKeyDown={(e: KeyboardEvent) => e.key === "Delete" && setDeleting(profile)}>
                  <button
                    class="select"
                    onClick={() => void call("select", profile.id)}
                    aria-pressed={status === "selected" || status === "connected"}
                  >
                    <span class="mark" aria-hidden="true">
                      {profile.mark}
                    </span>
                    <span class="grow">
                      <div class="title">{profile.name}</div>
                      <div class="sub">{profile.protocolLabel}</div>
                    </span>
                    <span class="trail">
                      {ms !== undefined && (
                        <span aria-label={ms === null ? t.t("connections_latency_no_answer_a11y") : t.t("connections_latency_a11y", ms)} style="font-family:var(--mono);font-size:11px">
                          {ms === null ? t.t("connections_latency_no_answer") : t.t("connections_latency_ms", ms)}
                        </span>
                      )}
                      <i class={`dot ${status === "connected" ? "connected" : status === "selected" ? "selected" : status === "unavailable" ? "error" : ""}`} />
                      <span class={`status-text ${status}`}>{t.t(`connections_status_${status === "unavailable" ? "unavailable" : status}`)}</span>
                    </span>
                  </button>
                  <button class="icon-button small delete-hover" onClick={() => setDeleting(profile)} aria-label={t.t("connections_delete")}>
                    <Icon name="trash" width={18} height={18} />
                  </button>
                  <button class="nav-chevron" onClick={() => navigate(`/config/${encodeURIComponent(profile.id)}`)} aria-label={t.t("connections_details")}>
                    <Icon name="chevron" width={18} height={18} />
                  </button>
                </div>
              );
            })}
          </Card>
        )}
      </div>

      {deleting && (
        <Dialog
          title={t.t("connections_delete_title")}
          body={t.t("connections_delete_body", deleting.name)}
          confirm={t.t("connections_delete_confirm")}
          cancel={t.t("connections_delete_cancel")}
          destructive
          onConfirm={async () => {
            const id = deleting.id;
            setDeleting(null);
            await call("remove", id);
          }}
          onCancel={() => setDeleting(null)}
        />
      )}
    </>
  );
}
