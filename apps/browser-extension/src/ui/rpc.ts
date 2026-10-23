// The interface's side of the typed RPC (`shared/api.ts`): a call is one `runtime.sendMessage`, and the state
// arrives on a port the service worker pushes to.

import { useEffect, useState } from "preact/hooks";
import { type Args, type Method, type Push, type Reply, type Result, type Request, UI_PORT } from "../shared/api.ts";
import type { AppState } from "../shared/state.ts";

export async function call<M extends Method>(method: M, ...args: Args<M>): Promise<Result<M>> {
  const request: Request<M> = { t: "rpc", m: method, a: args };
  const reply = (await chrome.runtime.sendMessage(request)) as Reply<M> | undefined;
  if (!reply) throw new Error("no answer from the service worker");
  if (!reply.ok) throw new Error(reply.error);
  return reply.v;
}

/** The app's state, kept current by the service worker's pushes. `null` until the first one. */
export function useAppState(): AppState | null {
  const [state, setState] = useState<AppState | null>(null);
  useEffect(() => {
    let port: chrome.runtime.Port | null = null;
    let closed = false;
    const open = () => {
      if (closed) return;
      port = chrome.runtime.connect({ name: UI_PORT });
      port.onMessage.addListener((message: Push) => {
        if (message.t === "state") setState(message.state);
      });
      // The worker was stopped under us (an update, the browser's own housekeeping): connect again.
      port.onDisconnect.addListener(() => {
        void chrome.runtime.lastError;
        port = null;
        setTimeout(open, 300);
      });
    };
    open();
    return () => {
      closed = true;
      port?.disconnect();
    };
  }, []);
  return state;
}
