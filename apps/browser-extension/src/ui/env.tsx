import { createContext, type ComponentChildren } from "preact";
import { useCallback, useContext, useEffect, useMemo, useRef, useState } from "preact/hooks";
import { languageOf, translator, type Translator } from "../shared/i18n.ts";
import type { AppState } from "../shared/state.ts";

export interface Env {
  app: AppState;
  t: Translator;
  /** A line at the bottom of the screen, for a while. */
  toast(message: string): void;
  /** The surface this page is: the toolbar popup, a full tab, or the side panel. */
  surface: "popup" | "tab" | "panel";
}

const Context = createContext<Env | null>(null);

export function useEnv(): Env {
  const env = useContext(Context);
  if (!env) throw new Error("no environment");
  return env;
}

export const surface = (document.documentElement.dataset.surface ?? "popup") as Env["surface"];

export function EnvProvider({ app, children }: { app: AppState; children: ComponentChildren }) {
  const [message, setMessage] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const toast = useCallback((text: string) => {
    setMessage(text);
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => setMessage(null), 3200);
  }, []);
  const t = useMemo(() => translator(languageOf(chrome.i18n.getUILanguage())), []);

  useEffect(() => {
    document.documentElement.dataset.theme = app.prefs.appearance;
    document.documentElement.lang = t.language;
  }, [app.prefs.appearance, t]);

  const env = useMemo<Env>(() => ({ app, t, toast, surface }), [app, t, toast]);
  return (
    <Context.Provider value={env}>
      {children}
      {message && (
        <div class="toast" role="status">
          {message}
        </div>
      )}
    </Context.Provider>
  );
}
