import { useEffect, useState } from "preact/hooks";
import { addSite, MAX_SITES, type SplitMode, removeSite } from "../shared/splitTunnel.ts";
import { Banner, Button, Card, Section, TextField, TopBar } from "./components.tsx";
import { useEnv } from "./env.tsx";
import { Icon } from "./Icon.tsx";
import { call } from "./rpc.ts";

const MODES: SplitMode[] = ["off", "exclude", "include"];

/** Split tunneling for a browser: which sites use the tunnel (`SplitTunnelScreen.kt`, with sites for apps). */
export function Sites() {
  const { app, t } = useEnv();
  const split = app.prefs.split;
  const [input, setInput] = useState("");
  const [error, setError] = useState(false);
  const [site, setSite] = useState<string | null>(null);

  useEffect(() => {
    void call("activeSite").then(setSite).catch(() => undefined);
  }, []);

  const add = async (value: string) => {
    const next = addSite(split, value);
    if (next === null) {
      setError(true);
      return;
    }
    setError(false);
    setInput("");
    await call("setSplit", next);
  };

  return (
    <div class="frame">
      <TopBar title={t.t("split_tunnel_title")} />
      <main class="scroll">
        <Banner>{t.t("x_split_note")}</Banner>

        <div style="margin-top:14px">
          <Card class="list">
            {MODES.map((mode) => (
              <button class="row" key={mode} onClick={() => void call("setSplit", { ...split, mode })} aria-pressed={split.mode === mode}>
                <span class="grow">
                  <div class="title">{t.t(`x_split_mode_${mode}`)}</div>
                  <div class="sub wrap">{t.t(`x_split_mode_${mode}_body`)}</div>
                </span>
                {split.mode === mode && (
                  <span class="check">
                    <Icon name="check" />
                  </span>
                )}
              </button>
            ))}
          </Card>
        </div>

        {split.mode !== "off" && (
          <>
            <Section title={t.n("x_split_selected", split.sites.length)} />
            <div class="input-row" style="align-items:flex-start">
              <TextField value={input} onInput={(v) => (setInput(v), setError(false))} placeholder={t.t("x_split_add_placeholder")} mono onEnter={() => void add(input)} error={error ? t.t("x_split_invalid") : null} />
              <div style="margin-top:14px">
                <Button compact variant="secondary" onClick={() => void add(input)} disabled={input.trim() === ""}>
                  {t.t("x_split_add")}
                </Button>
              </div>
            </div>
            {site && !split.sites.includes(site) && (
              <div style="margin-top:10px">
                <Button variant="outline" compact icon="plus" onClick={() => void add(site)}>
                  {t.t("x_split_add_current", site)}
                </Button>
              </div>
            )}
            {split.sites.length > 0 ? (
              <div class="chips" style="margin-top:14px">
                {split.sites.map((s) => (
                  <span class="chip removable" key={s}>
                    <span style="font-family:var(--mono);font-size:12px">{s}</span>
                    <button onClick={() => void call("setSplit", removeSite(split, s))} aria-label={t.t("x_split_remove", s)}>
                      <Icon name="close" width={14} height={14} />
                    </button>
                  </span>
                ))}
              </div>
            ) : (
              <p class="note">{t.t("x_split_empty")}</p>
            )}
            {split.sites.length >= MAX_SITES && <p class="note">{t.t("x_split_full")}</p>}
          </>
        )}
      </main>
    </div>
  );
}
