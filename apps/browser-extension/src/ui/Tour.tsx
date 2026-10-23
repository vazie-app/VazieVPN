import { useEffect, useState } from "preact/hooks";
import { Button } from "./components.tsx";
import { useEnv } from "./env.tsx";
import { navigate } from "./router.ts";
import { call } from "./rpc.ts";

const STEPS = [
  { target: "connect", title: "tour_connect_title", body: "tour_connect_body" },
  { target: "configs", title: "tour_configuration_title", body: "tour_configuration_body" },
  { target: "settings", title: "tour_settings_title", body: "tour_settings_body" },
] as const;

/** The first-run tour: three steps over the real interface, then a card that is not a step. */
export function Tour() {
  const { app, t } = useEnv();
  const [step, setStep] = useState(0);
  const active = app.loaded && app.prefs.onboardingDone && !app.prefs.tourDone;
  const done = step >= STEPS.length;
  const current = STEPS[step];

  useEffect(() => {
    if (!active || !current) return;
    const element = document.querySelector<HTMLElement>(`[data-tour="${current.target}"]`);
    element?.classList.add("highlight");
    element?.scrollIntoView({ block: "center", behavior: "smooth" });
    return () => element?.classList.remove("highlight");
  }, [active, current]);

  if (!active) return null;
  const finish = () => void call("setPrefs", { tourDone: true });

  if (done) {
    return (
      <aside class="callout" role="dialog" aria-labelledby="tour-title">
        <h3 id="tour-title">{t.t("tour_done_title")}</h3>
        <p>{t.t("x_tour_done_body")}</p>
        <div class="actions">
          <span class="grow" />
          <Button
            variant="text"
            compact
            onClick={() => {
              finish();
              navigate("/settings/guides");
            }}
          >
            {t.t("tour_done_guides")}
          </Button>
          <Button variant="primary" compact onClick={finish}>
            {t.t("tour_done_dismiss")}
          </Button>
        </div>
      </aside>
    );
  }
  if (!current) return null;
  return (
    <aside class="callout" role="dialog" aria-labelledby="tour-title">
      <h3 id="tour-title">{t.t(current.title)}</h3>
      <p>{t.t(current.body)}</p>
      <div class="actions">
        <span class="grow" aria-label={t.t("tour_progress_a11y", step + 1, STEPS.length)}>
          {t.t("tour_progress", step + 1, STEPS.length)}
        </span>
        <Button variant="text" compact onClick={finish}>
          {t.t("tour_skip")}
        </Button>
        {step > 0 && (
          <Button variant="secondary" compact onClick={() => setStep(step - 1)}>
            {t.t("tour_back")}
          </Button>
        )}
        <Button variant="primary" compact onClick={() => setStep(step + 1)}>
          {t.t("tour_next")}
        </Button>
      </div>
    </aside>
  );
}
