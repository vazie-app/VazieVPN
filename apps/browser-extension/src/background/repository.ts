// `EncryptedVpnProfileRepository.kt`: the stored profiles, decrypted once into memory and written back whole on
// every change, with every write serialized.

import type { ProfileDraft, StoredProfile } from "../shared/model.ts";
import { PROTOCOL } from "../shared/model.ts";
import { supports } from "../shared/support.ts";
import type { ProfileDetails, ProfileSummary } from "../shared/state.ts";
import { readProfiles, writeProfiles } from "./vault.ts";

export class Repository {
  #cache: StoredProfile[] | null = null;
  #chain: Promise<unknown> = Promise.resolve();
  #listeners = new Set<() => void>();

  onChange(listener: () => void): void {
    this.#listeners.add(listener);
  }

  /** Runs `task` after every earlier task, so two edits never read the same list. */
  #serial<T>(task: () => Promise<T>): Promise<T> {
    const next = this.#chain.then(task, task);
    this.#chain = next.catch(() => undefined);
    return next;
  }

  async #load(): Promise<StoredProfile[]> {
    if (this.#cache === null) this.#cache = (await readProfiles()) ?? [];
    return this.#cache;
  }

  async #commit(list: StoredProfile[]): Promise<void> {
    await writeProfiles(list);
    this.#cache = list;
    for (const listener of this.#listeners) listener();
  }

  /** Newest first. */
  async summaries(): Promise<ProfileSummary[]> {
    const list = await this.#serial(() => this.#load());
    return [...list]
      .sort((a, b) => b.createdAt - a.createdAt)
      .map((p) => ({
        id: p.id,
        name: p.name,
        protocolLabel: PROTOCOL.label,
        mark: PROTOCOL.mark,
        createdAt: p.createdAt,
        lastUsedAt: p.lastUsedAt,
        runnable: supports(p.outbound).ok,
      }));
  }

  async profile(id: string): Promise<StoredProfile | null> {
    const list = await this.#serial(() => this.#load());
    return list.find((p) => p.id === id) ?? null;
  }

  create(draft: ProfileDraft, name: string): Promise<string> {
    const trimmed = name.trim();
    if (trimmed === "") return Promise.reject(new Error("a profile name must not be blank"));
    return this.#serial(async () => {
      const list = await this.#load();
      const profile: StoredProfile = {
        id: crypto.randomUUID(),
        name: trimmed,
        origin: "imported_link",
        createdAt: Date.now(),
        lastUsedAt: null,
        outbound: draft.outbound,
      };
      await this.#commit([...list, profile]);
      return profile.id;
    });
  }

  /** Several at once, in the order the list gave them, each created a millisecond apart so the order survives. */
  createMany(drafts: { draft: ProfileDraft; name: string }[]): Promise<string[]> {
    return this.#serial(async () => {
      const list = await this.#load();
      const base = Date.now();
      const created = drafts.map(({ draft, name }, index): StoredProfile => ({
        id: crypto.randomUUID(),
        name: name.trim() || draft.outbound.endpoint.host,
        origin: "imported_link",
        createdAt: base + (drafts.length - index),
        lastUsedAt: null,
        outbound: draft.outbound,
      }));
      await this.#commit([...list, ...created]);
      return created.map((p) => p.id);
    });
  }

  #edit(id: string, transform: (profile: StoredProfile) => StoredProfile): Promise<void> {
    return this.#serial(async () => {
      const list = await this.#load();
      const existing = list.find((p) => p.id === id);
      if (!existing) return;
      const updated = transform(existing);
      if (JSON.stringify(updated) === JSON.stringify(existing)) return;
      await this.#commit(list.map((p) => (p.id === id ? updated : p)));
    });
  }

  /** Changes what a configuration is called, and nothing else. */
  rename(id: string, name: string): Promise<void> {
    const trimmed = name.trim();
    if (trimmed === "") return Promise.reject(new Error("a profile name must not be blank"));
    return this.#edit(id, (p) => ({ ...p, name: trimmed }));
  }

  markUsed(id: string): Promise<void> {
    const now = Date.now();
    return this.#edit(id, (p) => ({ ...p, lastUsedAt: now }));
  }

  duplicate(id: string, name: string): Promise<string | null> {
    const trimmed = name.trim();
    if (trimmed === "") return Promise.reject(new Error("a profile name must not be blank"));
    return this.#serial(async () => {
      const list = await this.#load();
      const source = list.find((p) => p.id === id);
      if (!source) return null;
      const copy: StoredProfile = {
        ...structuredClone(source),
        id: crypto.randomUUID(),
        name: trimmed,
        createdAt: Date.now(),
        // Deliberately dropped: it describes what happened to the original.
        lastUsedAt: null,
      };
      await this.#commit([...list, copy]);
      return copy.id;
    });
  }

  remove(id: string): Promise<void> {
    return this.#serial(async () => {
      const list = await this.#load();
      const updated = list.filter((p) => p.id !== id);
      if (updated.length === list.length) return;
      await this.#commit(updated);
    });
  }

  async details(id: string): Promise<ProfileDetails | null> {
    const profile = await this.profile(id);
    if (!profile) return null;
    const { outbound } = profile;
    const security = outbound.security;
    const transport = outbound.transport;
    return {
      id: profile.id,
      name: profile.name,
      engineLabel: "xray",
      protocolLabel: PROTOCOL.label,
      security: security.kind,
      transport: transport.kind,
      endpointHost: outbound.endpoint.host,
      endpointPort: outbound.endpoint.port,
      serverName: security.kind === "none" ? null : security.serverName,
      fingerprint: security.kind === "none" ? null : security.fingerprint,
      flow: outbound.flow,
      transportDetail:
        transport.kind === "tcp"
          ? transport.headerType
          : transport.kind === "ws"
            ? transport.path
            : transport.serviceName || null,
      unknownParameterNames: outbound.unknownParameters.map((p) => p.name),
      createdAt: profile.createdAt,
      lastUsedAt: profile.lastUsedAt,
    };
  }
}
