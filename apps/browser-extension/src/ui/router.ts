// A hash router, small on purpose: the interface has a stack of screens and a back button, as the app does.

import { useEffect, useState } from "preact/hooks";

export type Route = { path: string; params: Record<string, string>; query: URLSearchParams };

function read(): Route {
  const raw = location.hash.replace(/^#/, "") || "/";
  const [path = "/", search = ""] = raw.split("?");
  return { path, params: {}, query: new URLSearchParams(search) };
}

export function navigate(path: string, options: { replace?: boolean } = {}): void {
  if (options.replace) location.replace(`#${path}`);
  else location.hash = path;
}

export function back(fallback = "/"): void {
  // `history.length` counts the page that opened this one; a screen opened directly has nowhere to go back to.
  if (history.length > 1 && (history.state as { depth?: number } | null)?.depth !== 0) history.back();
  else navigate(fallback, { replace: true });
}

export function useRoute(): Route {
  const [route, setRoute] = useState<Route>(read);
  useEffect(() => {
    const listener = () => setRoute(read());
    addEventListener("hashchange", listener);
    return () => removeEventListener("hashchange", listener);
  }, []);
  return route;
}

/** `/config/:id` against `/config/abc` → `{ id: "abc" }`, or `null`. */
export function match(pattern: string, path: string): Record<string, string> | null {
  const a = pattern.split("/");
  const b = path.split("/");
  if (a.length !== b.length) return null;
  const params: Record<string, string> = {};
  for (let i = 0; i < a.length; i++) {
    const p = a[i] as string;
    const s = b[i] as string;
    if (p.startsWith(":")) params[p.slice(1)] = decodeURIComponent(s);
    else if (p !== s) return null;
  }
  return params;
}
