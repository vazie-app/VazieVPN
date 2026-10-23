import assert from "node:assert/strict";
import { test } from "node:test";
import { homeView, present } from "../src/shared/homeView.ts";
import { sanitize } from "../src/background/prefs.ts";
import type { AppState, ConnectionState, ProfileSummary } from "../src/shared/state.ts";

const profile = (id: string, runnable = true): ProfileSummary => ({
  id, name: id, protocolLabel: "VLESS", mark: "VL", createdAt: 1, lastUsedAt: null, runnable,
});

function app(over: { profiles?: ProfileSummary[]; selected?: string | null; state?: ConnectionState; subject?: string }): AppState {
  const profiles = over.profiles ?? [];
  const subject = over.subject ? { id: over.subject, name: over.subject, protocolLabel: "VLESS", mark: "VL" } : null;
  return {
    snapshot: { state: over.state ?? { kind: "idle" }, subject, diagnostics: null },
    profiles,
    prefs: { ...sanitize(null), selectedId: over.selected ?? null },
    helper: { kind: "unknown" },
    traffic: null,
    proxyControl: "unknown",
    links: null,
    loaded: true,
  };
}

test("nothing stored asks for a first configuration; stored but unchosen asks for a choice", () => {
  assert.equal(present(homeView(app({}))).action, "add");
  const unselected = present(homeView(app({ profiles: [profile("a")] })));
  assert.equal(unselected.action, "connect");
  assert.equal(unselected.actionEnabled, false);
});

test("the tunnel's profile outranks the selection", () => {
  const view = homeView(app({ profiles: [profile("a"), profile("b")], selected: "a", subject: "b", state: { kind: "connecting", profileId: "b" } }));
  assert.equal(view.kind === "ready" && view.name, "b");
});

test("each state has its one action", () => {
  const base = { profiles: [profile("a")], selected: "a" };
  const action = (state: ConnectionState) => present(homeView(app({ ...base, state }))).action;
  assert.equal(action({ kind: "idle" }), "connect");
  assert.equal(action({ kind: "preparing" }), "cancel");
  assert.equal(action({ kind: "connecting", profileId: "a" }), "cancel");
  assert.equal(action({ kind: "connected", profileId: "a", since: 0 }), "disconnect");
  assert.equal(action({ kind: "failed", failure: "helper" }), "retry");
  assert.equal(action({ kind: "noInternet" }), "check");
});

test("a configuration the engine cannot run is named but cannot be connected", () => {
  const view = present(homeView(app({ profiles: [profile("a", false)], selected: "a" })));
  assert.equal(view.actionEnabled, false);
  assert.equal(view.line.kind === "words" && view.line.key, "home_subtitle_unsupported");
});

test("stored preferences are not a trusted enum", () => {
  const prefs = sanitize({ appearance: "neon", appIcon: "nope", appIconPlate: "ink", split: { mode: "weird", sites: ["https://A.com/x", "not a host", 3] }, viewedGuides: ["toolbar", "bogus"] });
  assert.equal(prefs.appearance, "night_indigo");
  assert.equal(prefs.appIcon, "orbit");
  // "ink" is one of the plates the fallback icon (Orbit) was checked against.
  assert.equal(prefs.appIconPlate, "ink");
  assert.deepEqual(prefs.split, { mode: "off", sites: ["a.com"] });
  assert.deepEqual(prefs.viewedGuides, ["toolbar"]);
  // An icon keeps only the plates it was checked against.
  assert.equal(sanitize({ appIcon: "monochrome", appIconPlate: "deep" }).appIconPlate, "ink");
});
