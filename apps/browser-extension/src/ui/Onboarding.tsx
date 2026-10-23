import { useState } from "preact/hooks";
import { IconPreview } from "./Appearance.tsx";
import { Button } from "./components.tsx";
import { useEnv } from "./env.tsx";
import { navigate } from "./router.ts";
import { call } from "./rpc.ts";

const PAGES = [
  { title: "onboarding_welcome_title", body: "x_onboarding_welcome_body" },
  { title: "onboarding_what_title", body: "x_onboarding_what_body" },
  { title: "onboarding_own_title", body: "x_onboarding_own_body" },
  { title: "onboarding_reach_title", body: "x_onboarding_reach_body" },
] as const;

/** First run, in four pages, narrated by Vazi. */
export function Onboarding() {
  const { t, app } = useEnv();
  const [page, setPage] = useState(0);
  const last = page === PAGES.length - 1;
  const current = PAGES[page] ?? PAGES[0];

  const finish = async (destination: string) => {
    await call("setPrefs", { onboardingDone: true });
    navigate(destination, { replace: true });
  };

  return (
    <div class="frame onboarding">
      <div class="page" aria-live="polite">
        <div class="art">
          <IconPreview icon={app.prefs.appIcon} plate={app.prefs.appIconPlate} size={120} />
        </div>
        <h1>{t.t(current.title)}</h1>
        <p>{t.t(current.body)}</p>
      </div>
      <div class="footer">
        <div class="pager" role="img" aria-label={t.t("onboarding_page_indicator", page + 1, PAGES.length)}>
          {PAGES.map((_, index) => (
            <i class={index === page ? "on" : ""} key={index} />
          ))}
        </div>
        {last ? (
          <>
            <Button onClick={() => void finish("/add")}>{t.t("onboarding_add_config")}</Button>
            <Button variant="text" onClick={() => void finish("/")}>
              {t.t("onboarding_start")}
            </Button>
          </>
        ) : (
          <>
            <Button onClick={() => setPage(page + 1)}>{t.t("onboarding_next")}</Button>
            <Button variant="text" onClick={() => void finish("/")}>
              {t.t("onboarding_skip")}
            </Button>
          </>
        )}
      </div>
    </div>
  );
}
