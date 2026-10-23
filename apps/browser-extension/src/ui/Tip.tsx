import { Card } from "./components.tsx";
import { useEnv } from "./env.tsx";
import { Icon } from "./Icon.tsx";
import { call } from "./rpc.ts";

/** A one-sentence hint about a gesture or a place, shown until it is dismissed (the app's coach marks). */
export function Tip({ id, title, body }: { id: string; title: string; body: string }) {
  const { app, t } = useEnv();
  if (!app.prefs.tourDone || app.prefs.dismissedTips.includes(id)) return null;
  return (
    <div style="margin-top:8px">
      <Card>
        <div class="row">
          <span class="grow">
            <div class="title">{t.t(title)}</div>
            <div class="sub wrap">{t.t(body)}</div>
          </span>
          <button class="icon-button small" onClick={() => void call("setPrefs", { dismissedTips: [...app.prefs.dismissedTips, id] })} aria-label={t.t("config_close")}>
            <Icon name="close" width={18} height={18} />
          </button>
        </div>
      </Card>
    </div>
  );
}
