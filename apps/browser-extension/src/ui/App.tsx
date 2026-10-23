import { About, Privacy } from "./About.tsx";
import { Add } from "./Add.tsx";
import { Appearance } from "./Appearance.tsx";
import { ConfigDetails } from "./ConfigDetails.tsx";
import { Details } from "./Details.tsx";
import { EnvProvider } from "./env.tsx";
import { Faq } from "./Faq.tsx";
import { GuideDetail, Guides } from "./Guides.tsx";
import { Helper } from "./Helper.tsx";
import { Home } from "./Home.tsx";
import { Onboarding } from "./Onboarding.tsx";
import { match, useRoute } from "./router.ts";
import { useAppState } from "./rpc.ts";
import { Settings } from "./Settings.tsx";
import { Sites } from "./Sites.tsx";

export function App() {
  const app = useAppState();
  const route = useRoute();
  if (!app || !app.loaded) return <div class="frame" />;

  const screen = (() => {
    if (!app.prefs.onboardingDone) return <Onboarding />;
    const { path } = route;
    const config = match("/config/:id", path);
    const guide = match("/settings/guide/:id", path);
    switch (path) {
      case "/add":
        return <Add />;
      case "/details":
        return <Details />;
      case "/settings":
        return <Settings />;
      case "/settings/appearance":
        return <Appearance />;
      case "/settings/split":
        return <Sites />;
      case "/settings/helper":
        return <Helper />;
      case "/settings/guides":
        return <Guides />;
      case "/settings/faq":
        return <Faq />;
      case "/settings/about":
        return <About />;
      case "/settings/privacy":
        return <Privacy />;
    }
    if (config) return <ConfigDetails id={config.id as string} key={config.id} />;
    if (guide) return <GuideDetail id={guide.id as string} key={guide.id} />;
    return <Home />;
  })();

  return <EnvProvider app={app}>{screen}</EnvProvider>;
}
