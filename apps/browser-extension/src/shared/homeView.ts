// How Home's state is drawn (`HomePresentation.kt`, `HomeRoutePresentation.kt`, `ConnectionPresentation.kt`) —
// pure, so the whole vocabulary is one test away.

import type { AppState, ConnectionState, Failure } from "./state.ts";

export type HomeView =
  | { kind: "empty" }
  | { kind: "unselected" }
  | {
      kind: "ready";
      name: string;
      mark: string;
      protocolLabel: string;
      connection: ConnectionState;
      runnable: boolean;
    };

/** The tunnel's profile outranks the selection: the screen describes what exists before it describes what was
 * chosen. */
export function homeView(app: AppState): HomeView {
  const { snapshot, profiles, prefs } = app;
  const subject =
    snapshot.subject ?? profiles.find((p) => p.id === prefs.selectedId) ?? null;
  if (!subject) return profiles.length === 0 ? { kind: "empty" } : { kind: "unselected" };
  const stored = profiles.find((p) => p.id === subject.id);
  return {
    kind: "ready",
    name: stored?.name ?? subject.name,
    mark: subject.mark,
    protocolLabel: subject.protocolLabel,
    connection: snapshot.state,
    runnable: stored?.runnable ?? true,
  };
}

export type Tone = "neutral" | "info" | "warning" | "success" | "error";
export type HomeAction = "connect" | "cancel" | "disconnect" | "retry" | "check" | "add";
export type RouteState = "disconnected" | "connecting" | "connected" | "failed";

export interface Presentation {
  /** The one-word status. */
  statusKey: string;
  /** The headline, or `null` where the screen supplies it itself. */
  titleKey: string | null;
  tone: Tone;
  action: HomeAction;
  actionKey: string;
  actionVariant: "primary" | "secondary" | "destructive";
  actionEnabled: boolean;
  route: RouteState | null;
  line:
    | { kind: "words"; key: string; name?: string }
    | { kind: "session"; protocol: string };
}

export function failureKey(failure: Failure): string {
  switch (failure) {
    case "helper":
      return "x_failure_helper";
    case "engine":
      return "x_failure_engine";
    case "proxyControlled":
      return "x_failure_proxy";
    case "noInternet":
      return "home_failure_no_internet";
    case "profile":
      return "home_failure_profile";
    case "unsupported":
      return "home_failure_unsupported";
    case "tunnel":
      return "home_failure_tunnel";
    case "handshake":
      return "home_failure_handshake";
    case "tunnelUnusable":
      return "home_failure_tunnel_unusable";
    default:
      return "home_failure_unknown";
  }
}

export function present(view: HomeView): Presentation {
  if (view.kind === "empty") {
    return {
      statusKey: "home_status_no_configuration",
      titleKey: "home_no_config_title",
      tone: "neutral",
      action: "add",
      actionKey: "home_add_config",
      actionVariant: "primary",
      actionEnabled: true,
      route: null,
      line: { kind: "words", key: "home_no_config_body" },
    };
  }
  if (view.kind === "unselected") {
    return {
      statusKey: "home_status_unselected",
      titleKey: "home_unselected_title",
      tone: "neutral",
      action: "connect",
      actionKey: "home_action_connect",
      actionVariant: "primary",
      actionEnabled: false,
      route: null,
      line: { kind: "words", key: "home_unselected_body" },
    };
  }
  const { connection } = view;
  switch (connection.kind) {
    case "idle":
      return {
        statusKey: "home_status_disconnected",
        titleKey: "home_route_title_disconnected",
        tone: "neutral",
        action: "connect",
        actionKey: "home_action_connect",
        actionVariant: "primary",
        // A configuration this build cannot run is still the person's configuration and stays visible and named;
        // what it does not get is a button that would fail.
        actionEnabled: view.runnable,
        route: "disconnected",
        line: { kind: "words", key: view.runnable ? "home_route_subtitle_direct" : "home_subtitle_unsupported" },
      };
    case "preparing":
      return {
        statusKey: "home_status_preparing",
        titleKey: "home_route_title_connecting",
        tone: "info",
        action: "cancel",
        actionKey: "home_action_cancel",
        actionVariant: "secondary",
        actionEnabled: true,
        route: "connecting",
        line: { kind: "words", key: "x_subtitle_preparing" },
      };
    case "connecting":
      return {
        statusKey: "home_status_connecting",
        titleKey: "home_route_title_connecting",
        tone: "warning",
        action: "cancel",
        actionKey: "home_action_cancel",
        actionVariant: "secondary",
        actionEnabled: true,
        route: "connecting",
        line: { kind: "words", key: "home_route_subtitle_connecting", name: view.name },
      };
    case "connected":
      return {
        statusKey: "home_status_protected",
        titleKey: "home_route_title_connected",
        tone: "success",
        action: "disconnect",
        actionKey: "home_action_disconnect",
        actionVariant: "destructive",
        actionEnabled: true,
        route: "connected",
        line: { kind: "session", protocol: view.protocolLabel },
      };
    case "disconnecting":
      return {
        statusKey: "home_status_disconnecting",
        titleKey: "home_route_title_disconnecting",
        tone: "warning",
        action: "cancel",
        actionKey: "home_action_disconnecting",
        actionVariant: "secondary",
        actionEnabled: false,
        route: "disconnected",
        line: { kind: "words", key: "home_title_disconnecting" },
      };
    case "failed":
      return {
        statusKey: "home_status_failed",
        titleKey: "home_title_failed",
        tone: "error",
        action: "retry",
        actionKey: "home_action_retry",
        actionVariant: "primary",
        actionEnabled: view.runnable,
        route: "failed",
        line: { kind: "words", key: failureKey(connection.failure) },
      };
    case "noInternet":
      return {
        statusKey: "home_status_no_internet",
        titleKey: "home_status_no_internet",
        tone: "neutral",
        action: "check",
        actionKey: "home_action_check_connection",
        actionVariant: "secondary",
        actionEnabled: true,
        route: "failed",
        line: { kind: "words", key: "home_subtitle_no_internet" },
      };
  }
}
