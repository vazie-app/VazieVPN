// The tunnel: one at a time, owned here, described truthfully. This is `VazieVpnConnectionController` for a
// browser: the "TUN" is the helper's loopback proxy plus the PAC script that sends the browser to it.

import type { StoredProfile } from "../shared/model.ts";
import { PROTOCOL } from "../shared/model.ts";
import { supports } from "../shared/support.ts";
import { type Diagnostics, type Failure, type HelperStatus, isActive, type Snapshot, type Traffic } from "../shared/state.ts";
import type { SplitTunnel } from "../shared/splitTunnel.ts";
import { xrayOutbounds } from "../shared/xrayConfig.ts";
import { type Hello, NativeHost } from "./host.ts";
import { applyProxy, clearProxy, control, type Control } from "./proxy.ts";
import type { Repository } from "./repository.ts";

const IDLE: Snapshot = { state: { kind: "idle" }, subject: null, diagnostics: null };

function diagnosticsOf(profile: StoredProfile): Diagnostics {
  const { outbound } = profile;
  return {
    engine: "xray",
    protocol: PROTOCOL.id,
    security: outbound.security.kind,
    transport: outbound.transport.kind === "tcp" ? "raw" : outbound.transport.kind,
    flow: outbound.flow,
    endpointHost: outbound.endpoint.host,
    endpointPort: outbound.endpoint.port,
    latencyMs: null,
    dnsMode: "tunnel",
    ipv4: true,
    // Names are resolved by the server, and the server decides what it can reach.
    ipv6: true,
  };
}

export interface ControllerDeps {
  repository: Repository;
  host: NativeHost;
  split: () => SplitTunnel;
}

export class Controller {
  snapshot: Snapshot = IDLE;
  traffic: Traffic | null = null;
  helper: HelperStatus = { kind: "unknown" };
  proxyControl: Control = "unknown";

  readonly #deps: ControllerDeps;
  #generation = 0;
  #changed: () => void = () => undefined;
  #trafficTimer: ReturnType<typeof setInterval> | null = null;
  #watchers = 0;
  #connectedSince: number | null = null;
  #port: number | null = null;
  #subject: Snapshot["subject"] = null;
  #diagnostics: Diagnostics | null = null;

  constructor(deps: ControllerDeps) {
    this.#deps = deps;
    deps.host.onEvent((event) => {
      if (event.event === "exited") void this.#engineExited(event.helper === true);
    });
  }

  onChange(listener: () => void): void {
    this.#changed = listener;
  }

  #publish(state: Snapshot["state"], subject: Snapshot["subject"], diagnostics: Diagnostics | null = null): void {
    this.snapshot = { state, subject, diagnostics };
    this.#changed();
  }

  #fail(failure: Failure): void {
    this.#stopTraffic();
    this.traffic = null;
    this.#publish({ kind: "failed", failure }, null);
  }

  // -- the helper ------------------------------------------------------------------------------

  async refreshHelper(): Promise<HelperStatus> {
    const result = await this.#deps.host.hello();
    this.helper = result.ok ? helperStatus(result.hello) : { kind: "missing", detail: result.detail };
    this.proxyControl = await control();
    if (!isActive(this.snapshot.state)) this.#deps.host.releaseWhenIdle();
    this.#changed();
    return this.helper;
  }

  async installEngine(): Promise<boolean> {
    const reply = await this.#deps.host.call({ type: "engine.install" }, 300_000);
    await this.refreshHelper();
    return reply.ok;
  }

  // -- connect / disconnect --------------------------------------------------------------------

  async connect(profileId: string): Promise<void> {
    const generation = ++this.#generation;
    await this.#teardown();
    const alive = () => generation === this.#generation;

    const profile = await this.#deps.repository.profile(profileId);
    if (!profile) return this.#fail("profile");
    const subject = { id: profile.id, name: profile.name, protocolLabel: PROTOCOL.label, mark: PROTOCOL.mark };

    // Reported before anything is started, so a configuration Vazie cannot run never touches the browser.
    if (!supports(profile.outbound).ok) return this.#fail("unsupported");

    if (!navigator.onLine) {
      this.#publish({ kind: "noInternet" }, subject);
      return;
    }

    this.#publish({ kind: "preparing" }, subject);
    const hello = await this.#deps.host.hello();
    if (!alive()) return;
    this.helper = hello.ok ? helperStatus(hello.hello) : { kind: "missing", detail: hello.detail };
    if (!hello.ok) return this.#fail("helper");
    if (!hello.hello.engine.installed) return this.#fail("engine");

    this.proxyControl = await control();
    if (!alive()) return;
    if (this.proxyControl === "other") return this.#fail("proxyControlled");

    this.#publish({ kind: "connecting", profileId }, subject);
    const started = await this.#deps.host.call({ type: "start", outbounds: xrayOutbounds(profile.outbound) }, 20_000);
    if (!alive()) {
      if (started.ok) await this.#deps.host.call({ type: "stop" });
      return;
    }
    if (!started.ok) {
      this.#stopTraffic();
      this.#fail(startFailure(started.code));
      return;
    }
    const port = started.port as number;

    // Proven before the browser is pointed at it: an unusable tunnel must not take the browser's traffic with it.
    const ready = await this.#deps.host.call({ type: "ready" }, 30_000);
    if (!alive()) return;
    if (!ready.ok) {
      await this.#deps.host.call({ type: "stop" });
      return this.#fail(ready.code === "helper" ? "helper" : "tunnelUnusable");
    }

    this.#port = port;
    const applied = await applyProxy(this.#deps.split(), port);
    if (!alive()) return;
    if (!applied) {
      await this.#teardown();
      return this.#fail("proxyControlled");
    }

    this.#connectedSince = Date.now();
    this.#subject = subject;
    this.#diagnostics = diagnosticsOf(profile);
    this.#publish({ kind: "connected", profileId, since: this.#connectedSince }, subject, this.#diagnostics);
    void this.#deps.repository.markUsed(profileId);
    this.#syncTraffic();
    void this.#measureLatency(profile, generation);
  }

  async disconnect(): Promise<void> {
    ++this.#generation;
    const subject = this.snapshot.subject;
    if (this.snapshot.state.kind === "idle" && !subject) return;
    if (this.#connectedSince !== null || this.snapshot.state.kind !== "failed") {
      this.#publish({ kind: "disconnecting" }, subject);
    }
    await this.#teardown();
    this.#publish({ kind: "idle" }, null);
  }

  /** The proxy first, so the browser never points at a tunnel that is going away; then the engine. */
  async #teardown(): Promise<void> {
    this.#stopTraffic();
    this.traffic = null;
    this.#connectedSince = null;
    this.#port = null;
    this.#subject = null;
    this.#diagnostics = null;
    await clearProxy();
    if (this.#deps.host.connected) {
      await this.#deps.host.call({ type: "stop" }, 5_000);
      this.#deps.host.releaseWhenIdle();
    }
  }

  /** The sites list changed while a tunnel is up: the browser is told at once, with no new connection. */
  async reapplySplit(split: SplitTunnel): Promise<void> {
    if (this.#port === null || !isActive(this.snapshot.state)) return;
    await applyProxy(split, this.#port);
  }

  /** The engine ended by itself, or the helper went away under a live tunnel. Before a tunnel is up, the call in
   * flight reports it; only a session that was connected has nothing else to say it. */
  async #engineExited(helperGone: boolean): Promise<void> {
    if (this.#connectedSince === null) return;
    ++this.#generation;
    await this.#teardown();
    this.#fail(helperGone ? "helper" : "handshake");
  }

  /** Called when the browser reports the network came or went; only a live tunnel has anything to say. */
  networkChanged(online: boolean): void {
    const state = this.snapshot.state;
    if (state.kind === "connected" && !online) {
      this.snapshot = { ...this.snapshot, state: { kind: "noInternet" } };
      this.#stopTraffic();
      this.#changed();
    } else if (state.kind === "noInternet" && online && this.#connectedSince !== null && this.#subject) {
      this.snapshot = {
        ...this.snapshot,
        state: { kind: "connected", profileId: this.#subject.id, since: this.#connectedSince },
      };
      this.#syncTraffic();
      this.#changed();
    }
  }

  async #measureLatency(profile: StoredProfile, generation: number): Promise<void> {
    const reply = await this.#deps.host.call({ type: "probe", host: profile.outbound.endpoint.host, port: profile.outbound.endpoint.port }, 10_000);
    if (generation !== this.#generation || !this.#diagnostics) return;
    const ms = reply.ok && typeof reply.ms === "number" ? reply.ms : null;
    this.#diagnostics = { ...this.#diagnostics, latencyMs: ms };
    this.snapshot = { ...this.snapshot, diagnostics: this.#diagnostics };
    this.#changed();
  }

  // -- traffic ---------------------------------------------------------------------------------

  /** How many interface pages are open. The counters are read only while somebody is looking at them. */
  setWatchers(count: number): void {
    this.#watchers = count;
    this.#syncTraffic();
  }

  #syncTraffic(): void {
    const wanted = this.snapshot.state.kind === "connected" && this.#watchers > 0;
    if (wanted && !this.#trafficTimer) {
      void this.#readTraffic();
      this.#trafficTimer = setInterval(() => void this.#readTraffic(), 1_000);
    } else if (!wanted) {
      this.#stopTraffic();
    }
  }

  #stopTraffic(): void {
    if (this.#trafficTimer) clearInterval(this.#trafficTimer);
    this.#trafficTimer = null;
  }

  async #readTraffic(): Promise<void> {
    if (this.snapshot.state.kind !== "connected") return;
    const reply = await this.#deps.host.call({ type: "traffic" }, 4_000);
    if (this.snapshot.state.kind !== "connected") return;
    this.traffic = reply.ok ? { rx: reply.rx as number, tx: reply.tx as number } : null;
    this.#changed();
  }

  get connectedSubjectId(): string | null {
    return this.snapshot.state.kind === "connected" ? this.snapshot.state.profileId : null;
  }
}

function helperStatus(hello: Hello): HelperStatus {
  return {
    kind: "ready",
    version: hello.version,
    engineVersion: hello.engine.installed ? hello.engine.version : null,
    platform: hello.platform,
  };
}

function startFailure(code: string): Failure {
  switch (code) {
    case "helper":
      return "helper";
    case "engine":
      return "engine";
    case "request":
    case "config":
      return "profile";
    case "bind":
      return "tunnel";
    default:
      return "handshake";
  }
}
